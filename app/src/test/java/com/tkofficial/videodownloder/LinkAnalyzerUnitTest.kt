package com.tkofficial.videodownloder

import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.network.LinkAnalyzer
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAnalyzerUnitTest {

    private val analyzer = LinkAnalyzer(OkHttpClient())

    @Test
    fun `parseExtractorJson handles Cobalt tunnel response and error objects`() {
        val okJson = """
            {
              "status": "tunnel",
              "url": "https://cdn.example.com/stream/video.mp4",
              "filename": "Sample Clip.mp4"
            }
        """.trimIndent()

        val okOutcome = analyzer.parseExtractorJson(
            rawJson = okJson,
            endpoint = "https://api.example.com/",
            targetUrl = "https://social.example.com/watch/123",
            allowHttp = false
        )
        assertNull(okOutcome.errorReason)
        val media = okOutcome.media
        assertNotNull(media)
        assertEquals(1, media!!.options.size)
        assertEquals("https://cdn.example.com/stream/video.mp4", media.options.first().downloadUrl)
        assertEquals("Sample Clip.mp4", media.title)
        assertEquals(MediaCategory.VIDEO, media.mediaCategory)

        val errJson = """
            {
              "status": "error",
              "error": { "code": "error.api.link.unsupported", "message": "Unsupported link" }
            }
        """.trimIndent()
        val errOutcome = analyzer.parseExtractorJson(
            rawJson = errJson,
            endpoint = "https://api.example.com/",
            targetUrl = "https://social.example.com/watch/123",
            allowHttp = false
        )
        assertNull(errOutcome.media)
        assertEquals("Unsupported link", errOutcome.errorReason)
    }

    @Test
    fun `parseExtractorJson handles nested yt-dlp formats and filters mhtml storyboards`() {
        val ytDlpJson = """
            {
              "success": true,
              "data": {
                "title": "High Res Demo",
                "thumbnail": "https://img.example.com/thumb.jpg",
                "duration": 95,
                "formats": [
                  {
                    "ext": "mhtml",
                    "protocol": "mhtml",
                    "url": "https://cdn.example.com/storyboard.mhtml"
                  },
                  {
                    "ext": "mp4",
                    "height": 1080,
                    "filesize": 10485760,
                    "url": "https://cdn.example.com/1080p.mp4"
                  },
                  {
                    "ext": "m4a",
                    "vcodec": "none",
                    "format_note": "Audio Only",
                    "filesize_approx": 2097152,
                    "url": "https://cdn.example.com/audio.m4a"
                  }
                ]
              }
            }
        """.trimIndent()

        val outcome = analyzer.parseExtractorJson(
            rawJson = ytDlpJson,
            endpoint = "https://extractor.example.com/v1/info",
            targetUrl = "https://video.example.org/watch?v=abc",
            allowHttp = false
        )
        val media = outcome.media
        assertNotNull(media)
        assertEquals("High Res Demo", media!!.title)
        assertEquals(95_000L, media.durationMs)
        assertEquals("https://img.example.com/thumb.jpg", media.thumbnailUrl)
        assertEquals(2, media.options.size)
        assertEquals("1080p", media.options[0].quality)
        assertEquals(10485760L, media.options[0].estimatedBytes)
        assertEquals("audio/mp4", media.options[1].mimeType)
    }

    @Test
    fun `parseExtractorJson resolves relative urls and enforces https policy`() {
        val customJson = """
            {
              "title": "Relative Stream",
              "options": [
                {
                  "label": "720p MP4",
                  "download_url": "/files/video_720.mp4",
                  "size_bytes": 5242880
                },
                {
                  "label": "Insecure HTTP",
                  "url": "http://insecure.example.com/video.mp4"
                }
              ]
            }
        """.trimIndent()

        val outcomeStrict = analyzer.parseExtractorJson(
            rawJson = customJson,
            endpoint = "https://dl.example.com/api/extract",
            targetUrl = "https://post.example.com/1",
            allowHttp = false
        )
        val mediaStrict = outcomeStrict.media
        assertNotNull(mediaStrict)
        assertEquals(1, mediaStrict!!.options.size)
        assertEquals("https://dl.example.com/files/video_720.mp4", mediaStrict.options.first().downloadUrl)
    }

    @Test
    fun `analyze falls back to GET query when endpoint rejects POST with 405`() = runTest {
        val requests = mutableListOf<Pair<String, String>>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                requests += req.method to req.url.toString()
                if (req.method == "POST") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(405)
                        .message("Method Not Allowed")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "application/json")
                        .body(
                            """
                            {
                              "title": "GET Fallback Video",
                              "url": "https://cdn.example.com/fallback.mp4",
                              "mimeType": "video/mp4",
                              "sizeBytes": 4096
                            }
                            """.trimIndent().toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
            })
            .build()

        val testAnalyzer = LinkAnalyzer(client)
        val result = testAnalyzer.analyze(
            rawUrl = "https://share.example.com/v/42",
            allowHttp = false,
            timeoutSeconds = 15,
            customEndpoint = "https://api.example.com/extract",
            decryptedApiKey = "my-key",
            retryLimit = 0,
            apiEnabled = true
        )

        assertTrue(result.isSuccess)
        val analyzed = result.getOrThrow()
        assertEquals("GET Fallback Video", analyzed.title)
        assertEquals("https://cdn.example.com/fallback.mp4", analyzed.options.first().downloadUrl)
        assertEquals("POST", requests[0].first)
        assertEquals("GET", requests[1].first)
        assertTrue(requests[1].second.contains("url="))
    }

    @Test
    fun `analyze reports authentication failure without retrying same endpoint`() = runTest {
        var hits = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                hits++
                val req = chain.request()
                if (req.url.host == "api.example.com") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("""{"error":"bad token"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    // Direct webpage HTML with no media -> should surface the API auth error reason
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "text/html")
                        .body("<html><title>Page</title></html>".toResponseBody("text/html".toMediaType()))
                        .build()
                }
            })
            .build()

        val testAnalyzer = LinkAnalyzer(client)
        val result = testAnalyzer.analyze(
            rawUrl = "https://share.example.com/page",
            allowHttp = false,
            timeoutSeconds = 15,
            customEndpoint = "https://api.example.com/extract",
            decryptedApiKey = "wrong",
            retryLimit = 3,
            apiEnabled = true
        )

        assertFalse(result.isSuccess)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("Authentication Failed"))
        // 1 call to api.example.com + probe calls to share.example.com (no 4x retries on api.example.com)
        val apiCalls = hits
        assertTrue(apiCalls <= 4)
    }
}
