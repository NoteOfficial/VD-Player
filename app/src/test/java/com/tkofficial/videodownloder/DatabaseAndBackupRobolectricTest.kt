package com.tkofficial.videodownloder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tkofficial.videodownloder.database.AlbumEntity
import com.tkofficial.videodownloder.database.AppDatabase
import com.tkofficial.videodownloder.database.DownloadEntity
import com.tkofficial.videodownloder.database.DownloadedMediaEntity
import com.tkofficial.videodownloder.database.FavoriteEntity
import com.tkofficial.videodownloder.database.PlaylistEntity
import com.tkofficial.videodownloder.database.VideoBookmarkEntity
import com.tkofficial.videodownloder.database.VaultMetadataEntity
import com.tkofficial.videodownloder.repositories.BackupEnvelope
import com.tkofficial.videodownloder.repositories.VaultRepository
import com.tkofficial.videodownloder.security.SecurityManager
import com.tkofficial.videodownloder.utilities.FormatUtils
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseAndBackupRobolectricTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `download queue enqueue and crash boot recovery work transactionally`() = runTest {
        val dao = db.downloadDao()
        val id = dao.enqueueDownload(
            entity = DownloadEntity(
                url = "https://example.com/sample.mp4",
                resolvedUrl = "https://example.com/sample.mp4",
                title = "Sample Video",
                fileName = "sample.mp4",
                mimeType = "video/mp4",
                mediaType = "VIDEO",
                platform = "example.com",
                status = "DOWNLOADING",
                totalBytes = 1024L
            ),
            wifiOnly = true,
            maxSpeedKbps = 500
        )

        assertTrue(id > 0L)
        assertEquals(1, dao.observeActiveDownloads().first().size)
        assertEquals(1, dao.observeHistory().first().size)

        dao.pauseInterruptedOnBoot()
        val recovered = dao.getById(id)
        assertEquals("PAUSED", recovered?.status)
    }

    @Test
    fun `gallery strictly isolates downloaded media and supports trash and albums`() = runTest {
        val galleryDao = db.galleryDao()
        assertTrue(galleryDao.observeAllMedia().first().isEmpty())

        val albumId = galleryDao.insertAlbum(AlbumEntity(name = "Tutorials"))
        val mediaId = galleryDao.insertMedia(
            DownloadedMediaEntity(
                title = "Offline Clip",
                fileName = "offline_clip.mp4",
                filePath = "/tmp/offline_clip.mp4",
                contentUri = "file:///tmp/offline_clip.mp4",
                mediaType = "VIDEO",
                mimeType = "video/mp4",
                sizeBytes = 2048L,
                albumId = albumId
            )
        )

        assertEquals(1, galleryDao.observeAllMedia().first().size)
        galleryDao.setDeleted(mediaId, true, System.currentTimeMillis())
        assertEquals(0, galleryDao.observeAllMedia().first().size)
        assertEquals(1, galleryDao.observeTrashedMedia().first().size)
    }

    @Test
    fun `player library favorites playlists and video bookmarks persist properly`() = runTest {
        val playerDao = db.playerLibraryDao()
        val playlistId = playerDao.insertPlaylist(PlaylistEntity(name = "Focus Mix", mediaCategory = "AUDIO"))
        playerDao.upsertFavorite(
            FavoriteEntity(
                itemKey = "track_1",
                itemType = "AUDIO",
                title = "Ambient Loop",
                uriOrPath = "content://media/external/audio/media/1"
            )
        )
        playerDao.insertVideoBookmark(
            VideoBookmarkEntity(
                mediaUri = "content://video/1",
                positionMs = 42_000L,
                label = "Chorus Drop"
            )
        )

        assertTrue(playlistId > 0L)
        assertEquals(1, playerDao.observeAllFavorites().first().size)
        assertEquals(1, playerDao.observeVideoBookmarks("content://video/1").first().size)
    }

    @Test
    fun `backup envelope SHA-256 checksum detects tampering`() {
        val payload = """{"formatVersion":1,"userDisplayName":"Media Explorer"}"""
        val checksum = FormatUtils.computeBytesSha256(payload.toByteArray(Charsets.UTF_8))
        val envelopeJson = Json.encodeToString(BackupEnvelope(payload, checksum))
        val parsed = Json.decodeFromString<BackupEnvelope>(envelopeJson)

        val cleanHash = FormatUtils.computeBytesSha256(parsed.payloadJson.toByteArray(Charsets.UTF_8))
        assertEquals(parsed.sha256Checksum, cleanHash)

        val tamperedHash = FormatUtils.computeBytesSha256(
            parsed.payloadJson.replace("Media Explorer", "Tampered").toByteArray(Charsets.UTF_8)
        )
        assertNotEquals(parsed.sha256Checksum, tamperedHash)
    }

    @Test
    fun `galleryDao renameMediaByDownloadId and deleteMediaByDownloadId stay synchronized`() = runTest {
        val galleryDao = db.galleryDao()
        galleryDao.insertMedia(
            DownloadedMediaEntity(
                downloadId = 77L,
                title = "original",
                fileName = "original.mp4",
                filePath = "/tmp/original.mp4",
                contentUri = "content://auth/original.mp4",
                mediaType = "VIDEO",
                mimeType = "video/mp4",
                sizeBytes = 4096L
            )
        )

        galleryDao.renameMediaByDownloadId(
            downloadId = 77L,
            newTitle = "renamed",
            newFileName = "renamed.mp4",
            newPath = "/tmp/renamed.mp4",
            newContentUri = "content://auth/renamed.mp4"
        )
        val afterRename = galleryDao.observeAllMedia().first()
        assertEquals(1, afterRename.size)
        assertEquals("renamed.mp4", afterRename.first().fileName)
        assertEquals("content://auth/renamed.mp4", afterRename.first().contentUri)

        galleryDao.deleteMediaByDownloadId(77L)
        assertTrue(galleryDao.observeAllMedia().first().isEmpty())
    }

    @Test
    fun `vault integrity check verifies real SHA-256 checksums against DB records`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vaultDir = FormatUtils.getVaultDir(context)
        vaultDir.listFiles()?.forEach { it.delete() }

        val validBlob = File(vaultDir, "valid_test.vlt").apply {
            writeBytes(ByteArray(64) { (it + 1).toByte() })
        }
        val validHash = FormatUtils.computeFileHash(validBlob, "SHA-256")!!

        val tamperedBlob = File(vaultDir, "tampered_test.vlt").apply {
            writeBytes(ByteArray(64) { 0x2A })
        }

        val systemDao = db.systemDao()
        systemDao.insertVaultItem(
            VaultMetadataEntity(
                originalName = "clip.mp4",
                encryptedFileName = validBlob.name,
                mediaType = "VIDEO",
                mimeType = "video/mp4",
                originalSizeBytes = 48L,
                encryptedSizeBytes = validBlob.length(),
                sha256Checksum = validHash,
                ivHex = "00112233445566778899aabb"
            )
        )
        systemDao.insertVaultItem(
            VaultMetadataEntity(
                originalName = "corrupt.mp4",
                encryptedFileName = tamperedBlob.name,
                mediaType = "VIDEO",
                mimeType = "video/mp4",
                originalSizeBytes = 48L,
                encryptedSizeBytes = tamperedBlob.length(),
                sha256Checksum = "0000deadbeef",
                ivHex = "00112233445566778899aabb"
            )
        )

        val repo = VaultRepository(context, systemDao, SecurityManager(context))
        val (validCount, totalCount) = repo.verifyAllVaultIntegrity()
        assertEquals(2, totalCount)
        assertEquals(1, validCount)

        validBlob.delete()
        tamperedBlob.delete()
    }
}
