package com.tkofficial.videodownloder.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadEntity::class,
        DownloadHistoryEntity::class,
        DownloadQueueEntity::class,
        AlbumEntity::class,
        DownloadedMediaEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlaybackHistoryEntity::class,
        UserPreferencesEntity::class,
        ThemeSettingsEntity::class,
        AppNotificationEntity::class,
        VaultMetadataEntity::class,
        BackupRecordEntity::class,
        BrowserBookmarkEntity::class,
        BrowserHistoryEntity::class,
        VideoBookmarkEntity::class,
        SongMetadataOverrideEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun galleryDao(): GalleryDao
    abstract fun playerLibraryDao(): PlayerLibraryDao
    abstract fun systemDao(): SystemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for future schema upgrades without destructive fallback
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "downloader_va.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
