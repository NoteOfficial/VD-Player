package com.tkofficial.videodownloder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY priority DESC, createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('DOWNLOADING', 'PAUSED') ORDER BY updatedAt DESC")
    fun observeActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY priority DESC, createdAt ASC")
    fun observeQueuedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY updatedAt DESC")
    fun observeCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('FAILED', 'CANCELLED') ORDER BY updatedAt DESC")
    fun observeFailedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE url = :url AND status != 'CANCELLED' LIMIT 1")
    suspend fun findByUrl(url: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE (resolvedUrl = :targetUrl OR url = :targetUrl) AND status != 'CANCELLED' LIMIT 1")
    suspend fun findByResolvedOrSourceUrl(targetUrl: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY priority DESC, createdAt ASC")
    suspend fun getQueuedOnce(): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun getRunningCount(): Int

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    fun observeTotalCompletedBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, speedBytesPerSec = :speed, etaSeconds = :eta, supportsRange = :supportsRange, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(
        id: Long,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long,
        supportsRange: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET priority = :priority, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET fileName = :newName, title = :newName, localPath = :newPath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameDownload(id: Long, newName: String, newPath: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'PAUSED', speedBytesPerSec = 0, etaSeconds = -1 WHERE status = 'DOWNLOADING'")
    suspend fun pauseInterruptedOnBoot()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertHistory(history: DownloadHistoryEntity)

    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    fun observeHistory(): Flow<List<DownloadHistoryEntity>>

    @Query("DELETE FROM download_history")
    suspend fun clearHistory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: DownloadQueueEntity)

    @Query("DELETE FROM download_queue WHERE downloadId = :downloadId")
    suspend fun removeFromQueue(downloadId: Long)

    @Transaction
    suspend fun enqueueDownload(entity: DownloadEntity, wifiOnly: Boolean, maxSpeedKbps: Int): Long {
        val id = insert(entity)
        insertQueueItem(
            DownloadQueueEntity(
                downloadId = id,
                queueOrder = id.toInt(),
                priority = entity.priority,
                wifiOnly = wifiOnly,
                maxSpeedKbps = maxSpeedKbps
            )
        )
        insertHistory(
            DownloadHistoryEntity(
                downloadId = id,
                url = entity.url,
                title = entity.title,
                fileName = entity.fileName,
                fileSize = entity.totalBytes.coerceAtLeast(0L),
                mimeType = entity.mimeType,
                platform = entity.platform,
                status = entity.status
            )
        )
        return id
    }
}

@Dao
interface GalleryDao {
    @Query("SELECT * FROM downloaded_media WHERE isDeleted = 0 ORDER BY addedAt DESC")
    fun observeAllMedia(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeTrashedMedia(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): DownloadedMediaEntity?

    @Query("SELECT * FROM downloaded_media WHERE isDeleted = 0")
    suspend fun getAllActiveOnce(): List<DownloadedMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: DownloadedMediaEntity): Long

    @Update
    suspend fun updateMedia(media: DownloadedMediaEntity)

    @Query("UPDATE downloaded_media SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE downloaded_media SET isDeleted = :deleted, deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean, deletedAt: Long?)

    @Query("UPDATE downloaded_media SET albumId = :albumId WHERE id = :id")
    suspend fun moveToAlbum(id: Long, albumId: Long?)

    @Query("UPDATE downloaded_media SET title = :newTitle, fileName = :newFileName, filePath = :newPath, contentUri = :newContentUri WHERE id = :id")
    suspend fun renameMedia(id: Long, newTitle: String, newFileName: String, newPath: String, newContentUri: String)

    @Query("UPDATE downloaded_media SET title = :newTitle, fileName = :newFileName, filePath = :newPath, contentUri = :newContentUri WHERE downloadId = :downloadId")
    suspend fun renameMediaByDownloadId(downloadId: Long, newTitle: String, newFileName: String, newPath: String, newContentUri: String)

    @Query("DELETE FROM downloaded_media WHERE downloadId = :downloadId")
    suspend fun deleteMediaByDownloadId(downloadId: Long)

    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Query("UPDATE albums SET coverUri = :coverUri WHERE id = :albumId")
    suspend fun updateAlbumCover(albumId: Long, coverUri: String)

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)
}

@Dao
interface PlayerLibraryDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAllFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE itemKey = :key")
    suspend fun removeFavorite(key: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC, addedAt DESC")
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deletePlaylistItem(id: Long)

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC")
    fun observePlaybackHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE mediaUri = :uri LIMIT 1")
    suspend fun getPlaybackRecord(uri: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackHistory(history: PlaybackHistoryEntity)

    @Query("SELECT * FROM video_bookmarks WHERE mediaUri = :uri ORDER BY positionMs ASC")
    fun observeVideoBookmarks(uri: String): Flow<List<VideoBookmarkEntity>>

    @Insert
    suspend fun insertVideoBookmark(bookmark: VideoBookmarkEntity)

    @Query("DELETE FROM video_bookmarks WHERE id = :id")
    suspend fun deleteVideoBookmark(id: Long)

    @Query("SELECT * FROM song_metadata_overrides")
    fun observeSongOverrides(): Flow<List<SongMetadataOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongOverride(override: SongMetadataOverrideEntity)
}

@Dao
interface SystemDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeNotifications(): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadNotificationCount(): Flow<Int>

    @Insert
    suspend fun insertNotification(notification: AppNotificationEntity): Long

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearNotifications()

    @Query("SELECT * FROM vault_metadata ORDER BY createdAt DESC")
    fun observeVaultItems(): Flow<List<VaultMetadataEntity>>

    @Query("SELECT * FROM vault_metadata")
    suspend fun getVaultItemsOnce(): List<VaultMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultMetadataEntity): Long

    @Query("DELETE FROM vault_metadata WHERE id = :id")
    suspend fun deleteVaultItem(id: Long)

    @Query("SELECT * FROM backup_records ORDER BY createdAt DESC")
    fun observeBackups(): Flow<List<BackupRecordEntity>>

    @Insert
    suspend fun insertBackupRecord(record: BackupRecordEntity)

    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun observeUserPreferences(): Flow<UserPreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserPreferences(prefs: UserPreferencesEntity)

    @Query("SELECT * FROM theme_settings WHERE id = 1")
    fun observeThemeSettings(): Flow<ThemeSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveThemeSettings(settings: ThemeSettingsEntity)

    @Query("SELECT * FROM browser_bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BrowserBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BrowserBookmarkEntity)

    @Query("DELETE FROM browser_bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC LIMIT 200")
    fun observeBrowserHistory(): Flow<List<BrowserHistoryEntity>>

    @Insert
    suspend fun insertBrowserHistory(entry: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history")
    suspend fun clearBrowserHistory()
}
