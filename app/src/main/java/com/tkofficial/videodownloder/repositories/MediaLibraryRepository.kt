package com.tkofficial.videodownloder.repositories

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.withContext
import java.io.File

class MediaLibraryRepository(
    private val context: Context,
    private val galleryDao: GalleryDao,
    private val playerDao: PlayerLibraryDao
) {
    suspend fun loadRawVideos(
        persistedFolderUris: Set<String>,
        pickedVideoUris: Set<String>
    ): List<DeviceMediaItem> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, DeviceMediaItem>()

        // 1. Videos downloaded by VD Player (zero permission needed)
        val downloaded = galleryDao.getAllActiveOnce().filter { it.mediaType == "VIDEO" }
        downloaded.forEach { entity ->
            val file = File(entity.filePath)
            if (file.exists()) {
                val uriStr = Uri.fromFile(file).toString()
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

        // 2. Videos inside user-granted SAF folders (zero broad permission needed)
        persistedFolderUris.forEach { treeStr ->
            runCatching {
                val treeUri = Uri.parse(treeStr)
                val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching
                val folderName = docTree.name ?: "Local Folder"
                docTree.listFiles().forEach { doc ->
                    val type = doc.type.orEmpty()
                    val name = doc.name.orEmpty()
                    if (doc.isFile && (type.startsWith("video/") || FormatUtils.classifyMimeType(type, name).name == "VIDEO")) {
                        val uriStr = doc.uri.toString()
                        result.putIfAbsent(
                            uriStr,
                            DeviceMediaItem(
                                id = "saf_${uriStr.hashCode()}",
                                uriString = uriStr,
                                title = name.substringBeforeLast('.'),
                                fileName = name,
                                folderName = folderName,
                                durationMs = 0L,
                                sizeBytes = doc.length(),
                                mimeType = type.ifBlank { "video/mp4" },
                                isVideo = true,
                                isDownloadedByApp = false,
                                dateAddedSec = doc.lastModified() / 1000L
                            )
                        )
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
            item.copy(
                lastPositionMs = hist?.lastPositionMs ?: 0L,
                playCount = hist?.playCount ?: 0,
                lastPlayedAt = hist?.lastPlayedAt ?: 0L,
                isFavorite = favoriteKeys.contains(item.uriString) || item.isFavorite
            )
        }
    }

    suspend fun loadVideoLibrary(
        persistedFolderUris: Set<String>,
        pickedVideoUris: Set<String>,
        historyMap: Map<String, PlaybackHistoryEntity>,
        favoriteKeys: Set<String>
    ): List<DeviceMediaItem> {
        val raw = loadRawVideos(persistedFolderUris, pickedVideoUris)
        return applyVideoMetadata(raw, historyMap, favoriteKeys)
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
                        val artUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()

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
