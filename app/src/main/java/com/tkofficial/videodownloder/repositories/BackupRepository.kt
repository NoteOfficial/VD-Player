package com.tkofficial.videodownloder.repositories

import android.content.Context
import android.net.Uri
import com.tkofficial.videodownloder.data.PreferencesRepository
import com.tkofficial.videodownloder.database.AlbumEntity
import com.tkofficial.videodownloder.database.BackupRecordEntity
import com.tkofficial.videodownloder.database.DownloadDao
import com.tkofficial.videodownloder.database.DownloadHistoryEntity
import com.tkofficial.videodownloder.database.FavoriteEntity
import com.tkofficial.videodownloder.database.GalleryDao
import com.tkofficial.videodownloder.database.PlayerLibraryDao
import com.tkofficial.videodownloder.database.PlaylistEntity
import com.tkofficial.videodownloder.database.SystemDao
import com.tkofficial.videodownloder.models.ThemeConfig
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupDataPayload(
    val formatVersion: Int = 1,
    val exportedAtMillis: Long,
    val userDisplayName: String,
    val languageTag: String,
    val maxConcurrentDownloads: Int,
    val wifiOnlyDownloads: Boolean,
    val speedLimitKbps: Int,
    val themeConfig: ThemeConfig,
    val historyUrls: List<BackupHistoryDto>,
    val favorites: List<BackupFavoriteDto>,
    val playlists: List<BackupPlaylistDto>,
    val albums: List<String>
)

@Serializable
data class BackupHistoryDto(
    val url: String,
    val title: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val platform: String,
    val status: String,
    val timestamp: Long
)

@Serializable
data class BackupFavoriteDto(
    val itemKey: String,
    val itemType: String,
    val title: String,
    val uriOrPath: String
)

@Serializable
data class BackupPlaylistDto(
    val name: String,
    val mediaCategory: String
)

@Serializable
data class BackupEnvelope(
    val payloadJson: String,
    val sha256Checksum: String
)

class BackupRepository(
    private val context: Context,
    private val prefsRepo: PreferencesRepository,
    private val downloadDao: DownloadDao,
    private val galleryDao: GalleryDao,
    private val playerDao: PlayerLibraryDao,
    private val systemDao: SystemDao
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Exports settings, theme, history, favorites, playlists, and album metadata.
     * Raw vault keys, PIN hashes, and API keys are strictly excluded.
     */
    suspend fun exportBackupToUri(destUri: Uri): Result<BackupRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = prefsRepo.settingsFlow.first()
            val history = downloadDao.observeHistory().first().map {
                BackupHistoryDto(it.url, it.title, it.fileName, it.fileSize, it.mimeType, it.platform, it.status, it.timestamp)
            }
            val favorites = playerDao.observeAllFavorites().first().map {
                BackupFavoriteDto(it.itemKey, it.itemType, it.title, it.uriOrPath)
            }
            val playlists = playerDao.observePlaylists().first().map {
                BackupPlaylistDto(it.name, it.mediaCategory)
            }
            val albums = galleryDao.observeAlbums().first().map { it.name }

            val payload = BackupDataPayload(
                exportedAtMillis = System.currentTimeMillis(),
                userDisplayName = settings.userDisplayName,
                languageTag = settings.language.tag,
                maxConcurrentDownloads = settings.maxConcurrentDownloads,
                wifiOnlyDownloads = settings.wifiOnlyDownloads,
                speedLimitKbps = settings.speedLimitKbps,
                themeConfig = settings.themeConfig,
                historyUrls = history,
                favorites = favorites,
                playlists = playlists,
                albums = albums
            )

            val payloadJson = json.encodeToString(payload)
            val checksum = FormatUtils.computeBytesSha256(payloadJson.toByteArray(Charsets.UTF_8))
            val envelopeJson = json.encodeToString(BackupEnvelope(payloadJson, checksum))

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(envelopeJson.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open backup destination.")

            val fileName = FormatUtils.getFileNameFromUri(context, destUri)
            val record = BackupRecordEntity(
                fileName = fileName,
                filePath = destUri.toString(),
                itemCount = history.size + favorites.size + playlists.size + albums.size,
                sha256Integrity = checksum
            )
            systemDao.insertBackupRecord(record)
            record
        }
    }

    suspend fun validateAndRestoreBackup(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("Unable to read backup file.")

            val envelope = json.decodeFromString<BackupEnvelope>(raw)
            val actualHash = FormatUtils.computeBytesSha256(envelope.payloadJson.toByteArray(Charsets.UTF_8))
            if (actualHash != envelope.sha256Checksum) {
                error("Backup integrity verification failed (SHA-256 mismatch).")
            }

            val payload = json.decodeFromString<BackupDataPayload>(envelope.payloadJson)
            prefsRepo.updateSettings { current ->
                current.copy(
                    userDisplayName = payload.userDisplayName,
                    maxConcurrentDownloads = payload.maxConcurrentDownloads.coerceIn(1, 5),
                    wifiOnlyDownloads = payload.wifiOnlyDownloads,
                    speedLimitKbps = payload.speedLimitKbps,
                    themeConfig = payload.themeConfig
                )
            }

            payload.albums.forEach { albumName ->
                galleryDao.insertAlbum(AlbumEntity(name = albumName))
            }
            payload.favorites.forEach { fav ->
                playerDao.upsertFavorite(
                    FavoriteEntity(
                        itemKey = fav.itemKey,
                        itemType = fav.itemType,
                        title = fav.title,
                        uriOrPath = fav.uriOrPath
                    )
                )
            }
            payload.playlists.forEach { pl ->
                playerDao.insertPlaylist(PlaylistEntity(name = pl.name, mediaCategory = pl.mediaCategory))
            }
            payload.historyUrls.forEach { h ->
                downloadDao.insertHistory(
                    DownloadHistoryEntity(
                        url = h.url,
                        title = h.title,
                        fileName = h.fileName,
                        fileSize = h.fileSize,
                        mimeType = h.mimeType,
                        platform = h.platform,
                        status = h.status,
                        timestamp = h.timestamp
                    )
                )
            }
            payload.historyUrls.size + payload.favorites.size + payload.playlists.size + payload.albums.size
        }
    }
}
