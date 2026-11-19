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
import com.tkofficial.videodownloder.repositories.BackupEnvelope
import com.tkofficial.videodownloder.utilities.FormatUtils
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
}
