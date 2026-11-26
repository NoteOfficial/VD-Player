package com.tkofficial.videodownloder.repositories

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.tkofficial.videodownloder.database.DownloadedMediaEntity
import com.tkofficial.videodownloder.database.FavoriteEntity
import com.tkofficial.videodownloder.database.GalleryDao
import com.tkofficial.videodownloder.database.PlaybackHistoryEntity
import com.tkofficial.videodownloder.database.PlayerLibraryDao
import com.tkofficial.videodownloder.database.SongMetadataOverrideEntity
import com.tkofficial.videodownloder.models.DeviceMediaItem
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class MediaLibraryRepository(
    private val context: Context,
    private val galleryDao: GalleryDao,
    private val playerDao: PlayerLibraryDao
) {
    suspend fun loadRawVideos(
        hasVideoPermission: Boolean,
        persistedFolderUris: Set<String>,
        pickedVideoUris: Set<String>
    ): List<DeviceMediaItem> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, DeviceMediaItem>()
        val seenPaths = hashSetOf<String>()

        // 1. Videos downloaded by VD Player (zero permission needed)
        val downloaded = galleryDao.getAllActiveOnce().filter { it.mediaType == "VIDEO" }
        downloaded.forEach { entity ->
            val file = File(entity.filePath)
            if (file.exists()) {
                val uriStr = Uri.fromFile(file).toString()
                seenPaths.add(file.absolutePath)
                result[uriStr] = DeviceMediaItem(
                    id = "dl_${entity.id}",
                    uriString = uriStr,
                    title = entity.title,
                    fileName = entity.fileName,
                    folderName = "VD Player",
                    durationMs = entity.durationMs,
                    sizeBytes = entity.sizeBytes,
                    width = entity.width,
                    height = entity.height,
                    mimeType = entity.mimeType,
                    isVideo = true,
                    isDownloadedByApp = true,
                    dateAddedSec = entity.addedAt / 1000L,
                    isFavorite = entity.isFavorite
                )
            }
        }

        // 2. Device Videos via MediaStore (when runtime video permission granted)
        if (hasVideoPermission) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA
            )
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                    while (cursor.moveToNext()) {
                        val msId = cursor.getLong(idCol)
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        if (dataPath.isNotBlank() && seenPaths.contains(dataPath)) continue

                        val contentUri = ContentUris.withAppendedId(collection, msId)
                        val uriStr = contentUri.toString()
                        val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        val title = (if (titleCol >= 0) cursor.getString(titleCol) else null)
                            ?.takeIf { it.isNotBlank() }
                            ?: displayName.substringBeforeLast('.').ifBlank { "Video $msId" }
                        if (dataPath.isNotBlank()) seenPaths.add(dataPath)
                        result.putIfAbsent(
                            uriStr,
                            DeviceMediaItem(
                                id = "ms_vid_$msId",
                                uriString = uriStr,
                                title = title,
                                fileName = displayName.ifBlank { "$title.mp4" },
                                folderName = if (bucketCol >= 0) cursor.getString(bucketCol)?.ifBlank { "Device" } ?: "Device" else "Device",
                                durationMs = if (durCol >= 0) cursor.getLong(durCol).coerceAtLeast(0L) else 0L,
                                sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                                width = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0,
                                height = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0,
                                mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4",
                                isVideo = true,
                                isDownloadedByApp = false,
                                dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol).coerceAtLeast(0L) else 0L
                            )
                        )
                    }
                }
            }
        }

        // 2. Videos inside user-granted SAF folders (single-cursor IPC query for fast folder loads)
        persistedFolderUris.forEach { treeStr ->
            runCatching {
                val treeUri = Uri.parse(treeStr)
                val docTree = DocumentFile.fromTreeUri(context, treeUri)
                val folderName = docTree?.name ?: "Local Folder"
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
                val safProjection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                context.contentResolver.query(childrenUri, safProjection, null, null, null)?.use { cursor ->
                    val docIdCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext()) {
                        val docId = if (docIdCol >= 0) cursor.getString(docIdCol).orEmpty() else continue
                        val type = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
                        if (type == DocumentsContract.Document.MIME_TYPE_DIR) continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        if (type.startsWith("video/") || FormatUtils.classifyMimeType(type, name).name == "VIDEO") {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val uriStr = docUri.toString()
                            result.putIfAbsent(
                                uriStr,
                                DeviceMediaItem(
                                    id = "saf_${uriStr.hashCode()}",
                                    uriString = uriStr,
                                    title = name.substringBeforeLast('.').ifBlank { "Video" },
                                    fileName = name.ifBlank { "video.mp4" },
                                    folderName = folderName,
                                    durationMs = 0L,
                                    sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                                    mimeType = type.ifBlank { "video/mp4" },
                                    isVideo = true,
                                    isDownloadedByApp = false,
                                    dateAddedSec = if (modCol >= 0) cursor.getLong(modCol).coerceAtLeast(0L) / 1000L else 0L
                                )
                            )
                        }
                    }
                }
            }
        }

        // 3. Videos selected via Photo Picker / Document Picker
        pickedVideoUris.forEach { uriStr ->
            if (!result.containsKey(uriStr)) {
                val uri = Uri.parse(uriStr)
                val name = FormatUtils.getFileNameFromUri(context, uri)
                result[uriStr] = DeviceMediaItem(
                    id = "pick_${uriStr.hashCode()}",
                    uriString = uriStr,
                    title = name.substringBeforeLast('.'),
                    fileName = name,
                    folderName = "Selected Videos",
                    mimeType = context.contentResolver.getType(uri) ?: "video/mp4",
                    isVideo = true,
                    isDownloadedByApp = false
                )
            }
        }

        result.values.toList()
    }

    suspend fun applyVideoMetadata(
        rawVideos: List<DeviceMediaItem>,
        historyMap: Map<String, PlaybackHistoryEntity>,
        favoriteKeys: Set<String>
    ): List<DeviceMediaItem> = withContext(Dispatchers.Default) {
        rawVideos.map { item ->
            val hist = historyMap[item.uriString]
            val nextPos = hist?.lastPositionMs ?: 0L
            val nextCount = hist?.playCount ?: 0
            val nextPlayed = hist?.lastPlayedAt ?: 0L
            val nextFav = favoriteKeys.contains(item.uriString) || item.isFavorite
            if (item.lastPositionMs == nextPos &&
                item.playCount == nextCount &&
                item.lastPlayedAt == nextPlayed &&
                item.isFavorite == nextFav
            ) {
                item
            } else {
                item.copy(
                    lastPositionMs = nextPos,
                    playCount = nextCount,
                    lastPlayedAt = nextPlayed,
                    isFavorite = nextFav
                )
            }
        }
    }

    suspend fun loadVideoLibrary(
        hasVideoPermission: Boolean,
        persistedFolderUris: Set<String>,
        pickedVideoUris: Set<String>,
        historyMap: Map<String, PlaybackHistoryEntity>,
        favoriteKeys: Set<String>
    ): List<DeviceMediaItem> {
        val raw = loadRawVideos(hasVideoPermission, persistedFolderUris, pickedVideoUris)
        return applyVideoMetadata(raw, historyMap, favoriteKeys)
    }

    fun loadDeviceGalleryMediaFlow(
        hasImagesPermission: Boolean,
        hasVideoPermission: Boolean
    ): Flow<List<DownloadedMediaEntity>> = flow {
        if (!hasImagesPermission && !hasVideoPermission) {
            emit(emptyList())
            return@flow
        }
        val items = ArrayList<DownloadedMediaEntity>(128)
        var emittedInitialPreview = false

        if (hasImagesPermission) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.TITLE,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATA
            )
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Images.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                    while (cursor.moveToNext()) {
                        val msId = cursor.getLong(idCol)
                        val uriStr = ContentUris.withAppendedId(collection, msId).toString()
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        val title = (if (titleCol >= 0) cursor.getString(titleCol) else null)
                            ?.takeIf { it.isNotBlank() }
                            ?: displayName.substringBeforeLast('.').ifBlank { "Image $msId" }
                        items.add(
                            DownloadedMediaEntity(
                                id = -msId.coerceAtLeast(1L),
                                title = title,
                                fileName = displayName.ifBlank { "$title.jpg" },
                                filePath = dataPath,
                                contentUri = uriStr,
                                mediaType = "PHOTO",
                                mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/*" else "image/*",
                                sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                                width = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0,
                                height = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0,
                                isFavorite = false,
                                addedAt = (if (dateCol >= 0) cursor.getLong(dateCol) else 0L) * 1000L
                            )
                        )
                        if (!emittedInitialPreview && items.size == 90) {
                            emittedInitialPreview = true
                            emit(items.toList())
                        }
                    }
                }
            }
        }

        if (hasVideoPermission) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA
            )
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                    var videoCount = 0
                    while (cursor.moveToNext()) {
                        val msId = cursor.getLong(idCol)
                        val uriStr = ContentUris.withAppendedId(collection, msId).toString()
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        val title = (if (titleCol >= 0) cursor.getString(titleCol) else null)
                            ?.takeIf { it.isNotBlank() }
                            ?: displayName.substringBeforeLast('.').ifBlank { "Video $msId" }
                        items.add(
                            DownloadedMediaEntity(
                                id = -1_000_000_000L - msId.coerceAtLeast(1L),
                                title = title,
                                fileName = displayName.ifBlank { "$title.mp4" },
                                filePath = dataPath,
                                contentUri = uriStr,
                                mediaType = "VIDEO",
                                mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4",
                                sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                                durationMs = if (durCol >= 0) cursor.getLong(durCol).coerceAtLeast(0L) else 0L,
                                width = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0,
                                height = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0,
                                isFavorite = false,
                                addedAt = (if (dateCol >= 0) cursor.getLong(dateCol) else 0L) * 1000L
                            )
                        )
                        videoCount++
                        if (!emittedInitialPreview && videoCount == 60) {
                            emittedInitialPreview = true
                            emit(items.toList())
                        }
                    }
                }
            }
        }

        emit(items.sortedByDescending { it.addedAt })
    }.flowOn(Dispatchers.IO)

    suspend fun loadDeviceGalleryMedia(
        hasImagesPermission: Boolean,
        hasVideoPermission: Boolean,
        excludeFilePaths: Set<String>,
        excludeContentUris: Set<String>,
        favoriteKeys: Set<String>
    ): List<DownloadedMediaEntity> = withContext(Dispatchers.IO) {
        if (!hasImagesPermission && !hasVideoPermission) return@withContext emptyList()
        val items = mutableListOf<DownloadedMediaEntity>()

        if (hasImagesPermission) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.TITLE,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATA
            )
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Images.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                    while (cursor.moveToNext()) {
                        val msId = cursor.getLong(idCol)
                        val uriStr = ContentUris.withAppendedId(collection, msId).toString()
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        if (excludeContentUris.contains(uriStr) || (dataPath.isNotBlank() && excludeFilePaths.contains(dataPath))) {
                            continue
                        }
                        val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        val title = (if (titleCol >= 0) cursor.getString(titleCol) else null)
                            ?.takeIf { it.isNotBlank() }
                            ?: displayName.substringBeforeLast('.').ifBlank { "Image $msId" }
                        items.add(
                            DownloadedMediaEntity(
                                id = -msId.coerceAtLeast(1L),
                                title = title,
                                fileName = displayName.ifBlank { "$title.jpg" },
                                filePath = dataPath,
                                contentUri = uriStr,
                                mediaType = "PHOTO",
                                mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/*" else "image/*",
                                sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                                width = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0,
                                height = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0,
                                isFavorite = favoriteKeys.contains(uriStr),
                                addedAt = (if (dateCol >= 0) cursor.getLong(dateCol) else 0L) * 1000L
                            )
                        )
                    }
                }
            }
        }

        if (hasVideoPermission) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA
            )
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                    while (cursor.moveToNext()) {
                        val msId = cursor.getLong(idCol)
                        val uriStr = ContentUris.withAppendedId(collection, msId).toString()
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        if (excludeContentUris.contains(uriStr) || (dataPath.isNotBlank() && excludeFilePaths.contains(dataPath))) {
                            continue
                        }
                        val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        val title = (if (titleCol >= 0) cursor.getString(titleCol) else null)
                            ?.takeIf { it.isNotBlank() }
                            ?: displayName.substringBeforeLast('.').ifBlank { "Video $msId" }
                        items.add(
                            DownloadedMediaEntity(
                                id = -1_000_000_000L - msId.coerceAtLeast(1L),
                                title = title,
                                fileName = displayName.ifBlank { "$title.mp4" },
                                filePath = dataPath,
                                contentUri = uriStr,
                                mediaType = "VIDEO",
                                mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4",
                                sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                                durationMs = if (durCol >= 0) cursor.getLong(durCol).coerceAtLeast(0L) else 0L,
                                width = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0,
                                height = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0,
                                isFavorite = favoriteKeys.contains(uriStr),
                                addedAt = (if (dateCol >= 0) cursor.getLong(dateCol) else 0L) * 1000L
                            )
                        )
                    }
                }
            }
        }

        items.sortedByDescending { it.addedAt }
    }

    suspend fun loadRawAudio(
        hasAudioPermission: Boolean,
        pickedAudioUris: Set<String>
    ): List<DeviceMediaItem> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, DeviceMediaItem>()

        // 1. Audio downloaded by VD Player
        val downloaded = galleryDao.getAllActiveOnce().filter { it.mediaType == "AUDIO" }
        downloaded.forEach { entity ->
            val file = File(entity.filePath)
            if (file.exists()) {
                val uriStr = Uri.fromFile(file).toString()
                result[uriStr] = DeviceMediaItem(
                    id = "dl_aud_${entity.id}",
                    uriString = uriStr,
                    title = entity.title,
                    fileName = entity.fileName,
                    folderName = "VD Player",
                    artist = "Downloaded Audio",
                    album = "VD Player",
                    durationMs = entity.durationMs,
                    sizeBytes = entity.sizeBytes,
                    mimeType = entity.mimeType,
                    isVideo = false,
                    isDownloadedByApp = true,
                    dateAddedSec = entity.addedAt / 1000L,
                    isFavorite = entity.isFavorite
                )
            }
        }

        // 2. Device Audio via MediaStore (if audio permission granted)
        if (hasAudioPermission) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
            )
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val bucketCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(collection, id)
                        val uriStr = contentUri.toString()
                        val albumId = cursor.getLong(albumIdCol)
                        val artUri = if (albumId > 0L) {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()
                        } else {
                            null
                        }

                        result.putIfAbsent(
                            uriStr,
                            DeviceMediaItem(
                                id = "ms_aud_$id",
                                uriString = uriStr,
                                title = cursor.getString(titleCol) ?: "Unknown Track",
                                fileName = cursor.getString(nameCol) ?: "audio",
                                folderName = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "Music" else "Music",
                                artist = cursor.getString(artistCol) ?: "Unknown Artist",
                                album = cursor.getString(albumCol) ?: "Unknown Album",
                                durationMs = cursor.getLong(durCol),
                                sizeBytes = cursor.getLong(sizeCol),
                                mimeType = cursor.getString(mimeCol) ?: "audio/*",
                                isVideo = false,
                                isDownloadedByApp = false,
                                dateAddedSec = cursor.getLong(dateCol),
                                albumArtUri = artUri
                            )
                        )
                    }
                }
            }
        }

        // 3. SAF Picked audio files
        pickedAudioUris.forEach { uriStr ->
            if (!result.containsKey(uriStr)) {
                val uri = Uri.parse(uriStr)
                val name = FormatUtils.getFileNameFromUri(context, uri)
                result[uriStr] = DeviceMediaItem(
                    id = "pick_aud_${uriStr.hashCode()}",
                    uriString = uriStr,
                    title = name.substringBeforeLast('.'),
                    fileName = name,
                    folderName = "Picked Audio",
                    artist = "Local File",
                    album = "Picked Audio",
                    mimeType = context.contentResolver.getType(uri) ?: "audio/*",
                    isVideo = false
                )
            }
        }

        result.values.toList()
    }

    suspend fun applyAudioMetadata(
        rawAudio: List<DeviceMediaItem>,
        historyMap: Map<String, PlaybackHistoryEntity>,
        favoriteKeys: Set<String>,
        overrides: Map<String, SongMetadataOverrideEntity>
    ): List<DeviceMediaItem> = withContext(Dispatchers.Default) {
        rawAudio.map { item ->
            val ov = overrides[item.uriString]
            val hist = historyMap[item.uriString]
            item.copy(
                title = ov?.customTitle?.takeIf { it.isNotBlank() } ?: item.title,
                artist = ov?.customArtist?.takeIf { it.isNotBlank() } ?: item.artist,
                album = ov?.customAlbum?.takeIf { it.isNotBlank() } ?: item.album,
                lastPositionMs = hist?.lastPositionMs ?: 0L,
                playCount = hist?.playCount ?: 0,
                lastPlayedAt = hist?.lastPlayedAt ?: 0L,
                isFavorite = favoriteKeys.contains(item.uriString) || item.isFavorite,
                albumArtUri = ov?.customArtPath ?: item.albumArtUri
            )
        }
    }

    suspend fun loadAudioLibrary(
        hasAudioPermission: Boolean,
        pickedAudioUris: Set<String>,
        historyMap: Map<String, PlaybackHistoryEntity>,
        favoriteKeys: Set<String>,
        overrides: Map<String, SongMetadataOverrideEntity>
    ): List<DeviceMediaItem> {
        val raw = loadRawAudio(hasAudioPermission, pickedAudioUris)
        return applyAudioMetadata(raw, historyMap, favoriteKeys, overrides)
    }

    suspend fun toggleFavorite(item: DeviceMediaItem) {
        val currentlyFav = item.isFavorite
        if (currentlyFav) {
            playerDao.removeFavorite(item.uriString)
        } else {
            playerDao.upsertFavorite(
                FavoriteEntity(
                    itemKey = item.uriString,
                    itemType = if (item.isVideo) "VIDEO" else "AUDIO",
                    title = item.title,
                    uriOrPath = item.uriString
                )
            )
        }
    }

    suspend fun recordPlayback(item: DeviceMediaItem, positionMs: Long, durationMs: Long, incrementPlay: Boolean) {
        val existing = playerDao.getPlaybackRecord(item.uriString)
        val newCount = if (incrementPlay) (existing?.playCount ?: 0) + 1 else (existing?.playCount ?: 1)
        playerDao.upsertPlaybackHistory(
            PlaybackHistoryEntity(
                id = existing?.id ?: 0,
                mediaUri = item.uriString,
                title = item.title,
                mediaType = if (item.isVideo) "VIDEO" else "AUDIO",
                lastPositionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(existing?.durationMs ?: 0L),
                playCount = newCount,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }
}
