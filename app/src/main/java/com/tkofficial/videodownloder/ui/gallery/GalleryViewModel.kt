package com.tkofficial.videodownloder.ui.gallery

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.database.AlbumEntity
import com.tkofficial.videodownloder.database.DownloadedMediaEntity
import com.tkofficial.videodownloder.database.FavoriteEntity
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class GallerySection { ALL, PHOTOS, VIDEOS, ALBUMS, FAVORITES, RECENTLY_ADDED, TRASH }
enum class GallerySortOrder(@StringRes val labelRes: Int) {
    DATE_DESC(R.string.sort_date_desc),
    DATE_ASC(R.string.sort_date_asc),
    NAME_ASC(R.string.sort_name_asc),
    SIZE_DESC(R.string.sort_size_desc)
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DownloaderVaApp
    private val container = app.container
    private val galleryDao = container.database.galleryDao()
    private val playerDao = container.database.playerLibraryDao()
    private val mediaRepo = container.mediaLibraryRepository
    private val vaultRepo = container.vaultRepository
    private val prefsRepo = container.preferencesRepository

    private val _section = MutableStateFlow(GallerySection.ALL)
    val section: StateFlow<GallerySection> = _section.asStateFlow()

    private val _selectedAlbumId = MutableStateFlow<Long?>(null)
    val selectedAlbumId: StateFlow<Long?> = _selectedAlbumId.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(GallerySortOrder.DATE_DESC)
    val sortOrder: StateFlow<GallerySortOrder> = _sortOrder.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _hasImagesPermission = MutableStateFlow(false)
    private val _hasVideoPermission = MutableStateFlow(false)
    private val _refreshTrigger = MutableStateFlow(0L)
    private val _rawDeviceMedia = MutableStateFlow<List<DownloadedMediaEntity>>(emptyList())

    val albums: StateFlow<List<AlbumEntity>> = galleryDao.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val downloadedActive: StateFlow<List<DownloadedMediaEntity>> = galleryDao.observeAllMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedMedia: StateFlow<List<DownloadedMediaEntity>> = galleryDao.observeTrashedMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val favorites = playerDao.observeAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val favoriteKeySet = favorites
        .map { favs -> favs.map { it.itemKey }.toSet() }
        .distinctUntilChanged()

    private val excludeKeysFlow = combine(downloadedActive, trashedMedia) { active, trashed ->
        val all = active + trashed
        val paths = all.mapNotNull { it.filePath.takeIf(String::isNotBlank) }.toSet()
        val uris = all.mapNotNull { it.contentUri.takeIf(String::isNotBlank) }.toSet()
        paths to uris
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()

    private val _deviceMedia: StateFlow<List<DownloadedMediaEntity>> = combine(
        _rawDeviceMedia,
        excludeKeysFlow,
        favoriteKeySet
    ) { raw, excludes, favKeys ->
        val (excludePaths, excludeUris) = excludes
        raw.mapNotNull { item ->
            if (excludeUris.contains(item.contentUri) ||
                (item.filePath.isNotBlank() && excludePaths.contains(item.filePath))
            ) {
                null
            } else {
                val fav = favKeys.contains(item.contentUri)
                if (item.isFavorite == fav) item else item.copy(isFavorite = fav)
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDownloadedMedia: StateFlow<List<DownloadedMediaEntity>> = combine(
        downloadedActive,
        _deviceMedia
    ) { downloaded, device ->
        downloaded + device
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(
                _hasImagesPermission,
                _hasVideoPermission,
                _refreshTrigger
            ) { imgPerm, vidPerm, trigger ->
                Triple(imgPerm, vidPerm, trigger)
            }
                .distinctUntilChanged()
                .map { (imgPerm, vidPerm, _) ->
                    mediaRepo.loadDeviceGalleryMedia(
                        hasImagesPermission = imgPerm,
                        hasVideoPermission = vidPerm,
                        excludeFilePaths = emptySet(),
                        excludeContentUris = emptySet(),
                        favoriteKeys = emptySet()
                    )
                }
                .flowOn(Dispatchers.IO)
                .collect { scanned ->
                    _rawDeviceMedia.value = scanned
                }
        }
    }

    val displayedMedia: StateFlow<List<DownloadedMediaEntity>> = combine(
        allDownloadedMedia,
        trashedMedia,
        _section,
        _selectedAlbumId,
        _searchQuery,
        _sortOrder
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val active = arr[0] as List<DownloadedMediaEntity>
        @Suppress("UNCHECKED_CAST")
        val trashed = arr[1] as List<DownloadedMediaEntity>
        val sec = arr[2] as GallerySection
        val albumId = arr[3] as Long?
        val query = (arr[4] as String).trim().lowercase()
        val sort = arr[5] as GallerySortOrder

        val base = when (sec) {
            GallerySection.ALL -> active.filter { it.mediaType == "PHOTO" || it.mediaType == "VIDEO" }
            GallerySection.PHOTOS -> active.filter { it.mediaType == "PHOTO" }
            GallerySection.VIDEOS -> active.filter { it.mediaType == "VIDEO" }
            GallerySection.ALBUMS -> if (albumId != null) {
                active.filter { it.albumId == albumId && (it.mediaType == "PHOTO" || it.mediaType == "VIDEO") }
            } else {
                active.filter { it.mediaType == "PHOTO" || it.mediaType == "VIDEO" }
            }
            GallerySection.FAVORITES -> active.filter { it.isFavorite && (it.mediaType == "PHOTO" || it.mediaType == "VIDEO") }
            GallerySection.RECENTLY_ADDED -> {
                val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                active.filter { it.addedAt >= cutoff && (it.mediaType == "PHOTO" || it.mediaType == "VIDEO") }
            }
            GallerySection.TRASH -> trashed
        }.filter {
            query.isEmpty() || it.title.lowercase().contains(query) || it.fileName.lowercase().contains(query)
        }

        when (sort) {
            GallerySortOrder.DATE_DESC -> base.sortedByDescending { it.addedAt }
            GallerySortOrder.DATE_ASC -> base.sortedBy { it.addedAt }
            GallerySortOrder.NAME_ASC -> base.sortedBy { it.title.lowercase() }
            GallerySortOrder.SIZE_DESC -> base.sortedByDescending { it.sizeBytes }
        }
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun locString(@StringRes resId: Int, vararg args: Any): String {
        val lang = prefsRepo.settingsFlow.first().language
        return LocaleHelper.getLocalizedString(app, lang, resId, *args)
    }

    fun setMediaPermissions(imagesGranted: Boolean, videoGranted: Boolean) {
        if (_hasImagesPermission.value != imagesGranted) {
            _hasImagesPermission.value = imagesGranted
        }
        if (_hasVideoPermission.value != videoGranted) {
            _hasVideoPermission.value = videoGranted
        }
    }

    fun refreshMedia() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun selectSection(section: GallerySection) {
        _section.value = section
        if (section != GallerySection.ALBUMS) _selectedAlbumId.value = null
        _selectedIds.value = emptySet()
    }

    fun selectAlbum(albumId: Long?) {
        _selectedAlbumId.value = albumId
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setSortOrder(order: GallerySortOrder) {
        _sortOrder.value = order
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(id)) current - id else current + id
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun toggleFavorite(item: DownloadedMediaEntity) {
        viewModelScope.launch {
            if (item.id > 0L) {
                galleryDao.setFavorite(item.id, !item.isFavorite)
            } else {
                if (item.isFavorite) {
                    playerDao.removeFavorite(item.contentUri)
                } else {
                    playerDao.upsertFavorite(
                        FavoriteEntity(
                            itemKey = item.contentUri,
                            itemType = "GALLERY_MEDIA",
                            title = item.title,
                            uriOrPath = item.contentUri
                        )
                    )
                }
            }
        }
    }

    fun createAlbum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            galleryDao.insertAlbum(AlbumEntity(name = trimmed))
            _statusMessage.value = locString(R.string.msg_album_created, trimmed)
        }
    }

    fun updateAlbumCover(albumId: Long, coverUri: String) {
        viewModelScope.launch {
            galleryDao.updateAlbumCover(albumId, coverUri)
            _statusMessage.value = locString(R.string.msg_album_cover_updated)
        }
    }

    fun moveSelectedToAlbum(albumId: Long?) {
        val ids = _selectedIds.value.toList()
        viewModelScope.launch(Dispatchers.IO) {
            var movedCount = 0
            ids.forEach { id ->
                if (id > 0L) {
                    galleryDao.moveToAlbum(id, albumId)
                    movedCount++
                } else {
                    val deviceItem = _deviceMedia.value.firstOrNull { it.id == id }
                    if (deviceItem != null && copyIntoAppAlbum(deviceItem, albumId)) {
                        movedCount++
                    }
                }
            }
            _selectedIds.value = emptySet()
            _statusMessage.value = locString(R.string.msg_items_moved, movedCount)
        }
    }

    fun copyItemToAlbum(item: DownloadedMediaEntity, albumId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (copyIntoAppAlbum(item, albumId)) {
                _statusMessage.value = locString(R.string.msg_item_copied, item.fileName)
            } else {
                _statusMessage.value = locString(R.string.msg_file_missing)
            }
        }
    }

    private suspend fun copyIntoAppAlbum(item: DownloadedMediaEntity, albumId: Long?): Boolean {
        val category = if (item.mediaType == "VIDEO") MediaCategory.VIDEO else MediaCategory.IMAGE
        val destDir = FormatUtils.getAppSubDir(app, category)
        val ext = FormatUtils.extensionFromMime(item.mimeType)
        val baseName = item.fileName.ifBlank { FormatUtils.sanitizeFileName(item.title, ext) }
        val dest = FormatUtils.resolveUniqueFile(destDir, baseName)
        val srcFile = item.filePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
        val copied = if (srcFile != null) {
            runCatching { srcFile.copyTo(dest) }.isSuccess
        } else {
            runCatching {
                app.contentResolver.openInputStream(Uri.parse(item.contentUri))?.use { input ->
                    FileOutputStream(dest).use { out -> input.copyTo(out) }
                    true
                } ?: false
            }.getOrDefault(false)
        }
        if (!copied) return false
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", dest).toString()
        galleryDao.insertMedia(
            item.copy(
                id = 0,
                title = dest.nameWithoutExtension,
                fileName = dest.name,
                filePath = dest.absolutePath,
                contentUri = uri,
                albumId = albumId,
                addedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    fun renameItem(item: DownloadedMediaEntity, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isBlank()) return
        if (item.id < 0L) {
            viewModelScope.launch {
                _statusMessage.value = locString(R.string.gallery_device_item_note)
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(item.filePath)
            val ext = oldFile.extension.ifBlank { FormatUtils.extensionFromMime(item.mimeType) }
            val safeFileName = FormatUtils.sanitizeFileName(clean, ext)
            val newFile = File(oldFile.parentFile ?: FormatUtils.getAppMediaRoot(app), safeFileName)
            if (oldFile.exists()) oldFile.renameTo(newFile)
            val newUri = runCatching {
                FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", newFile).toString()
            }.getOrDefault(item.contentUri)
            galleryDao.renameMedia(item.id, clean, newFile.name, newFile.absolutePath, newUri)
            _statusMessage.value = locString(R.string.msg_renamed_to, newFile.name)
        }
    }

    fun moveToTrash(ids: List<Long>) {
        viewModelScope.launch {
            val roomIds = ids.filter { it > 0L }
            if (roomIds.isEmpty()) {
                _selectedIds.value = emptySet()
                _statusMessage.value = locString(R.string.gallery_device_item_note)
                return@launch
            }
            val now = System.currentTimeMillis()
            roomIds.forEach { galleryDao.setDeleted(it, true, now) }
            _selectedIds.value = emptySet()
            _statusMessage.value = locString(R.string.msg_moved_trash, roomIds.size)
        }
    }

    fun restoreFromTrash(ids: List<Long>) {
        viewModelScope.launch {
            val roomIds = ids.filter { it > 0L }
            roomIds.forEach { galleryDao.setDeleted(it, false, null) }
            _selectedIds.value = emptySet()
            _statusMessage.value = locString(R.string.msg_restored_items, roomIds.size)
        }
    }

    fun deletePermanently(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val roomIds = ids.filter { it > 0L }
            if (roomIds.isEmpty()) {
                _selectedIds.value = emptySet()
                _statusMessage.value = locString(R.string.gallery_device_item_note)
                return@launch
            }
            roomIds.forEach { id ->
                val item = galleryDao.getMediaById(id)
                if (item != null) {
                    if (item.filePath.isNotBlank()) File(item.filePath).delete()
                    galleryDao.deletePermanently(id)
                }
            }
            _selectedIds.value = emptySet()
            _statusMessage.value = locString(R.string.msg_deleted_perm, roomIds.size)
        }
    }

    fun moveToPrivateVault(item: DownloadedMediaEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = item.filePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
            if (file != null && item.id > 0L) {
                val res = vaultRepo.importLocalFileToVault(file, item.mediaType, item.mimeType, deleteSource = true)
                res.onSuccess {
                    galleryDao.deletePermanently(item.id)
                    _statusMessage.value = locString(R.string.msg_moved_vault, item.fileName)
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: locString(R.string.msg_move_vault_failed)
                }
            } else {
                val uri = runCatching { Uri.parse(item.contentUri) }.getOrNull()
                if (uri == null) {
                    _statusMessage.value = locString(R.string.msg_file_missing)
                    return@launch
                }
                val res = vaultRepo.importUriToVault(uri)
                res.onSuccess {
                    _statusMessage.value = locString(R.string.msg_encrypted_vault_item, it.originalName)
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: locString(R.string.msg_move_vault_failed)
                }
            }
        }
    }

    fun shareItem(item: DownloadedMediaEntity) {
        val file = item.filePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
        val shareUri = if (file != null) {
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        } else {
            runCatching { Uri.parse(item.contentUri) }.getOrNull()
        }
        if (shareUri == null) {
            viewModelScope.launch {
                _statusMessage.value = locString(R.string.msg_file_missing)
            }
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(Intent.createChooser(intent, item.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
