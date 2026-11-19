package com.tkofficial.videodownloder

import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.utilities.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FormatUtilsUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `url validation enforces https by default and respects allowHttp`() {
        assertTrue(FormatUtils.isValidDownloadUrl("https://example.com/video.mp4", allowHttp = false))
        assertFalse(FormatUtils.isValidDownloadUrl("http://example.com/video.mp4", allowHttp = false))
        assertTrue(FormatUtils.isValidDownloadUrl("http://example.com/video.mp4", allowHttp = true))
        assertFalse(FormatUtils.isValidDownloadUrl("javascript:alert(1)", allowHttp = true))
        assertFalse(FormatUtils.isValidDownloadUrl("file:///sdcard/test.mp4", allowHttp = true))
        assertFalse(FormatUtils.isValidDownloadUrl("   ", allowHttp = true))
    }

    @Test
    fun `mime and extension classification categorizes media accurately`() {
        assertEquals(MediaCategory.VIDEO, FormatUtils.classifyMimeType("video/mp4", "clip.mp4"))
        assertEquals(MediaCategory.AUDIO, FormatUtils.classifyMimeType("audio/mpeg", "song.mp3"))
        assertEquals(MediaCategory.IMAGE, FormatUtils.classifyMimeType("image/webp", "art.webp"))
        assertEquals(MediaCategory.ARCHIVE, FormatUtils.classifyMimeType("application/zip", "pack.zip"))
        assertEquals(MediaCategory.DOCUMENT, FormatUtils.classifyMimeType("application/pdf", "guide.pdf"))
        assertEquals(MediaCategory.VIDEO, FormatUtils.classifyMimeType("application/octet-stream", "sample.mkv"))
    }

    @Test
    fun `formatting utilities render predictable strings`() {
        assertEquals("0 B", FormatUtils.formatBytes(0))
        assertEquals("1.00 KB", FormatUtils.formatBytes(1024))
        assertEquals("1.00 MB", FormatUtils.formatBytes(1024L * 1024L))
        assertEquals("00:00", FormatUtils.formatDuration(0))
        assertEquals("02:05", FormatUtils.formatDuration(125_000))
        assertEquals("1:01:01", FormatUtils.formatDuration(3661_000))
        assertEquals("2m 5s", FormatUtils.formatEta(125))
    }

    @Test
    fun `sanitizeFileName strips unsafe characters and adds fallback extension`() {
        val sanitized = FormatUtils.sanitizeFileName("../my:unsafe*file?", "mp4")
        assertFalse(sanitized.contains(':'))
        assertFalse(sanitized.contains('*'))
        assertTrue(sanitized.endsWith(".mp4"))
    }

    @Test
    fun `computeFileHash produces consistent SHA-256 and resolveUniqueFile avoids collision`() {
        val dir = tempFolder.newFolder("downloads")
        val f1 = File(dir, "clip.mp4").apply { writeText("downloader-va-sample-payload") }
        val h1 = FormatUtils.computeFileHash(f1)
        val h2 = FormatUtils.computeBytesSha256("downloader-va-sample-payload".toByteArray())
        assertEquals(64, h1.length)
        assertEquals(h2, h1)

        val f2 = FormatUtils.resolveUniqueFile(dir, "clip.mp4")
        assertNotEquals(f1.absolutePath, f2.absolutePath)
        assertEquals("clip (1).mp4", f2.name)
    }
}
