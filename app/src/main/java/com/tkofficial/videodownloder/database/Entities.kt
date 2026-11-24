package com.tkofficial.videodownloder.database

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["url"]),
        Index(value = ["createdAt"])
    ]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val resolvedUrl: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val mediaType: String,
    val platform: String,
    val thumbnailUrl: String? = null,
    val localPath: String = "",
    val contentUri: String = "",
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: String = "QUEUED",
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val supportsRange: Boolean = false,
    val priority: Int = 0,
    val scheduledAt: Long? = null,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val sha256Hash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "download_history",
    indices = [Index(value = ["timestamp"])]
)
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long? = null,
    val url: String,
    val title: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val platform: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "download_queue",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["downloadId"], unique = true), Index(value = ["queueOrder"])]
)
data class DownloadQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long,
    val queueOrder: Int,
    val priority: Int = 0,
    val wifiOnly: Boolean = false,
    val maxSpeedKbps: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "albums",
    indices = [Index(value = ["name"], unique = true)]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "downloaded_media",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["mediaType"]),
        Index(value = ["albumId"]),
        Index(value = ["isDeleted"]),
        Index(value = ["addedAt"])
    ]
)
data class DownloadedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long? = null,
    val title: String,
    val fileName: String,
    val filePath: String,
    val contentUri: String,
    val mediaType: String, // PHOTO, VIDEO, AUDIO
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val albumId: Long? = null,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["itemKey"], unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemKey: String,
    val itemType: String, // GALLERY_MEDIA, VIDEO, AUDIO, BOOKMARK
    val title: String,
    val uriOrPath: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mediaCategory: String, // VIDEO or AUDIO
    val isSmart: Boolean = false,
    val smartRule: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"]), Index(value = ["mediaUri"])]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaUri: String,
    val title: String,
    val artistOrFolder: String = "",
    val durationMs: Long = 0L,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["mediaUri"], unique = true), Index(value = ["lastPlayedAt"])]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val title: String,
    val mediaType: String, // VIDEO or AUDIO
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playCount: Int = 1,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val userDisplayName: String = "Media Explorer",
    val languageTag: String = "en",
    val maxConcurrentDownloads: Int = 2,
    val wifiOnlyDownloads: Boolean = false,
    val speedLimitKbps: Int = 0,
    val autoRetryFailed: Boolean = true,
    val autoRenameDuplicates: Boolean = true,
    val batterySaverMode: Boolean = false,
    val lowEndDeviceMode: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "theme_settings")
data class ThemeSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val configJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["category"]), Index(value = ["createdAt"])]
)
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // DOWNLOAD, PLAYBACK, SECURITY, STORAGE, UPDATE
    val title: String,
    val message: String,
    val actionTarget: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "vault_metadata",
    indices = [Index(value = ["encryptedFileName"], unique = true)]
)
data class VaultMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val encryptedFileName: String,
    val mediaType: String, // PHOTO, VIDEO, DOCUMENT
    val mimeType: String,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val sha256Checksum: String,
    val ivHex: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "backup_records",
    indices = [Index(value = ["createdAt"])]
)
data class BackupRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val itemCount: Int,
    val sha256Integrity: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "browser_bookmarks",
    indices = [Index(value = ["url"], unique = true)]
)
data class BrowserBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "browser_history",
    indices = [Index(value = ["visitedAt"])]
)
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_bookmarks",
    indices = [Index(value = ["mediaUri"])]
)
data class VideoBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val label: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "song_metadata_overrides",
    indices = [Index(value = ["mediaUri"], unique = true)]
)
data class SongMetadataOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val customTitle: String,
    val customArtist: String,
    val customAlbum: String,
    val customArtPath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
