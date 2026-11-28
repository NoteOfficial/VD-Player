package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val directoryPath: String,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sizeBytes: Long,
    val fileCount: Int,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val lastBuildStatus: String,
    val entryFile: String,
    val packageId: String
)

@Entity(tableName = "build_queue")
data class BuildQueueEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectName: String,
    val profile: String,
    val priority: Int,
    val status: String, // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED
    val createdAt: Long,
    val updatedAt: Long,
    val retryCount: Int = 0
)

@Entity(tableName = "incremental_cache")
data class IncrementalCacheEntity(
    @PrimaryKey val cacheKey: String, // projectId + ":" + relativePath
    val projectId: String,
    val relativePath: String,
    val sha256: String,
    val lastSizeBytes: Long,
    val lastVerifiedAt: Long
)

@Entity(tableName = "crash_recovery")
data class CrashRecoveryEntity(
    @PrimaryKey val id: String, // e.g. "editor_draft:<absPath>" or "task:<taskId>"
    val kind: String, // EDITOR_DRAFT, BUILD_TASK, IMPORT_TASK
    val projectId: String,
    val targetPath: String,
    val payload: String,
    val updatedAt: Long
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(entity: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    @Query("SELECT * FROM build_queue ORDER BY priority DESC, createdAt ASC")
    fun observeBuildQueue(): Flow<List<BuildQueueEntity>>

    @Query("SELECT * FROM build_queue WHERE status IN ('QUEUED', 'RUNNING', 'INTERRUPTED')")
    suspend fun getPendingOrInterruptedBuilds(): List<BuildQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBuildQueue(entity: BuildQueueEntity)

    @Query("DELETE FROM build_queue WHERE id = :id")
    suspend fun deleteBuildQueue(id: String)

    @Query("SELECT * FROM incremental_cache WHERE projectId = :projectId")
    suspend fun getProjectCache(projectId: String): List<IncrementalCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCacheEntries(entries: List<IncrementalCacheEntity>)

    @Query("DELETE FROM incremental_cache WHERE projectId = :projectId")
    suspend fun clearProjectCache(projectId: String)

    @Query("DELETE FROM incremental_cache")
    suspend fun clearAllCache()

    @Query("SELECT COUNT(*) FROM incremental_cache")
    suspend fun countCacheEntries(): Int

    @Query("SELECT * FROM crash_recovery ORDER BY updatedAt DESC")
    suspend fun getAllRecoveryRecords(): List<CrashRecoveryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecovery(record: CrashRecoveryEntity)

    @Query("DELETE FROM crash_recovery WHERE id = :id")
    suspend fun deleteRecovery(id: String)

    @Query("DELETE FROM crash_recovery")
    suspend fun clearAllRecovery()
}

@Database(
    entities = [
        ProjectEntity::class,
        BuildQueueEntity::class,
        IncrementalCacheEntity::class,
        CrashRecoveryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao

    companion object {
        const val DB_NAME = "tk_studio_core.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `crash_recovery` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `kind` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `targetPath` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // Safe pre-migration backup before opening DB if an older DB file exists
                    val dbFile = context.getDatabasePath(DB_NAME)
                    if (dbFile.exists()) {
                        val backupDir = File(context.filesDir, "tkstudio/db_backups").apply { mkdirs() }
                        val snap = File(backupDir, "tk_studio_core_pre_open.db.bak")
                        runCatching { dbFile.copyTo(snap, overwrite = true) }
                    }
                    // No destructive fallback migration without backup
                    Room.databaseBuilder(context.applicationContext, StudioDatabase::class.java, DB_NAME)
                        .addMigrations(MIGRATION_1_2)
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}

class DatabaseIntegrityManager(private val context: Context) {
    data class DbHealthReport(
        val schemaVersion: Int,
        val integrityOk: Boolean,
        val integrityMessage: String,
        val dbSizeBytes: Long,
        val backupCount: Int
    )

    suspend fun inspectAndVerify(db: StudioDatabase): DbHealthReport = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(StudioDatabase.DB_NAME)
        val backupDir = File(context.filesDir, "tkstudio/db_backups").apply { mkdirs() }
        var ok = false
        var msg = "Not checked"
        runCatching {
            db.openHelper.readableDatabase.query("PRAGMA integrity_check").use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    ok = result.equals("ok", ignoreCase = true)
                    msg = "PRAGMA integrity_check: $result"
                }
            }
        }.onFailure {
            ok = false
            msg = "Integrity error: ${it.message}"
        }
        DbHealthReport(
            schemaVersion = db.openHelper.readableDatabase.version,
            integrityOk = ok,
            integrityMessage = msg,
            dbSizeBytes = if (dbFile.exists()) dbFile.length() else 0L,
            backupCount = backupDir.listFiles()?.size ?: 0
        )
    }

    suspend fun backupDatabaseFile(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = context.getDatabasePath(StudioDatabase.DB_NAME)
            val backupDir = File(context.filesDir, "tkstudio/db_backups").apply { mkdirs() }
            val out = File(backupDir, "db_backup_${System.currentTimeMillis()}.db")
            if (dbFile.exists()) dbFile.copyTo(out, overwrite = true) else out.writeText("")
            out
        }
    }
}
