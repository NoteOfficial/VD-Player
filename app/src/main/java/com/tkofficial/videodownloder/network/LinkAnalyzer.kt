package com.tkofficial.videodownloder.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tkofficial.videodownloder.models.AnalyzedMedia
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.models.MediaFormatOption
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LinkAnalyzer(
    private val baseClient: OkHttpClient
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    fun buildClient(timeoutSeconds: Int): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun analyze(
        rawUrl: String,
        allowHttp: Boolean,
        timeoutSeconds: Int,
        customEndpoint: String,
        decryptedApiKey: String
    ): Result<AnalyzedMedia> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!FormatUtils.isValidDownloadUrl(url, allowHttp)) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (allowHttp) "Invalid URL. Enter a valid http:// or https:// URL."
                    else "HTTPS-only policy active. Enter a valid https:// URL or enable local HTTP in Network Settings."
                )
            )
        }

        val client = buildClient(timeoutSeconds)

        // 1. If user configured a valid HTTPS extractor API endpoint, query it first
        if (customEndpoint.isNotBlank() && FormatUtils.isValidDownloadUrl(customEndpoint, allowHttp = false)) {
            val apiResult = queryCustomExtractor(client, customEndpoint.trim(), decryptedApiKey, url)
            if (apiResult != null) {
                return@withContext Result.success(apiResult)
            }
        }

        // 2. Direct HTTP HEAD / GET Range probe for authentic server headers
        val headInfo = probeHeaders(client, url)
            ?: return@withContext Result.failure(
                IllegalStateException("Unable to reach host or read HTTP headers from ${FormatUtils.extractHost(url)}.")
            )

        val contentType = headInfo.mimeType.lowercase()
        val host = FormatUtils.extractHost(headInfo.finalUrl)

        // If the server returned an HTML webpage, parse real <video>, <audio>, <meta property="og:..."> tags
        if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
            val htmlMedia = parseHtmlMediaTags(client, headInfo.finalUrl, host)
            if (htmlMedia != null) {
                return@withContext Result.success(htmlMedia)
            }
            return@withContext Result.failure(
                IllegalStateException(
                    "No direct downloadable media stream was found in HTML from $host. " +
                        "Protected/DRM or login-gated platforms require an authorized API in Settings -> API Management."
                )
            )
        }

        // Direct media or binary file
        val fileName = headInfo.suggestedFileName
            ?: deriveFileNameFromUrl(headInfo.finalUrl, headInfo.mimeType)
        val category = FormatUtils.classifyMimeType(headInfo.mimeType, fileName)

        val option = MediaFormatOption(
            id = "direct_source",
            label = "${fileName.substringAfterLast('.', "FILE").uppercase()} (${if (headInfo.contentLength > 0) FormatUtils.formatBytes(headInfo.contentLength) else "Unknown size"})",
            downloadUrl = headInfo.finalUrl,
            mimeType = headInfo.mimeType,
            quality = "Original Source",
            estimatedBytes = headInfo.contentLength,
            supportsRange = headInfo.supportsRange
        )

        Result.success(
            AnalyzedMedia(
                sourceUrl = url,
                title = fileName,
                platform = host,
                thumbnailUrl = if (category == MediaCategory.IMAGE) headInfo.finalUrl else null,
                durationMs = null,
                totalBytes = headInfo.contentLength,
                mimeType = headInfo.mimeType,
                mediaCategory = category,
                supportsRange = headInfo.supportsRange,
                options = listOf(option),
                analysisNotes = if (headInfo.supportsRange) null else "Server did not advertise Accept-Ranges: bytes. Resume after pause may restart."
            )
        )
    }

    private suspend fun queryCustomExtractor(
        client: OkHttpClient,
        endpoint: String,
        apiKey: String,
        targetUrl: String
    ): AnalyzedMedia? {
        return runCatching {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://localhost/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            val service = retrofit.create(CustomExtractorService::class.java)
            val auth = if (apiKey.isNotBlank()) "Bearer $apiKey" else null
            val response = service.resolveMedia(endpoint, auth, request = ExtractorApiRequest(targetUrl))
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            val opts = mutableListOf<MediaFormatOption>()
            body.options?.forEachIndexed { idx, dto ->
                val optUrl = dto.url?.trim().orEmpty()
                if (FormatUtils.isValidDownloadUrl(optUrl, allowHttp = false)) {
                    val mime = dto.mimeType ?: "video/mp4"
                    opts += MediaFormatOption(
                        id = "opt_$idx",
                        label = dto.label ?: dto.quality ?: "Option ${idx + 1}",
                        downloadUrl = optUrl,
                        mimeType = mime,
                        quality = dto.quality ?: "API Stream",
                        estimatedBytes = dto.sizeBytes ?: -1L,
                        supportsRange = true
                    )
                }
            }
            val singleUrl = body.url?.trim().orEmpty()
            if (opts.isEmpty() && FormatUtils.isValidDownloadUrl(singleUrl, allowHttp = false)) {
                val head = probeHeaders(client, singleUrl)
                val mime = head?.mimeType ?: "video/mp4"
                opts += MediaFormatOption(
                    id = "api_primary",
                    label = body.filename ?: "Primary Stream",
                    downloadUrl = singleUrl,
                    mimeType = mime,
                    quality = "API Default",
                    estimatedBytes = head?.contentLength ?: -1L,
                    supportsRange = head?.supportsRange ?: false
                )
            }
            if (opts.isEmpty()) return null
            val first = opts.first()
            AnalyzedMedia(
                sourceUrl = targetUrl,
                title = body.title ?: body.filename ?: deriveFileNameFromUrl(first.downloadUrl, first.mimeType),
                platform = "${FormatUtils.extractHost(targetUrl)} (API)",
                thumbnailUrl = body.thumbnail,
                durationMs = body.durationMs,
                totalBytes = first.estimatedBytes,
                mimeType = first.mimeType,
                mediaCategory = FormatUtils.classifyMimeType(first.mimeType, body.filename),
                supportsRange = first.supportsRange,
                options = opts
            )
        }.getOrNull()
    }

    private data class HeadProbeResult(
        val finalUrl: String,
        val mimeType: String,
        val contentLength: Long,
        val supportsRange: Boolean,
        val suggestedFileName: String?
    )

    private fun probeHeaders(client: OkHttpClient, url: String): HeadProbeResult? {
        val headReq = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "VDPlayer/1.0 (Android)")
            .build()

        runCatching {
            client.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val finalUrl = resp.request.url.toString()
                    val mime = resp.header("Content-Type")?.substringBefore(';')?.trim() ?: "application/octet-stream"
                    val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val acceptRanges = resp.header("Accept-Ranges")?.lowercase().orEmpty()
                    val disp = resp.header("Content-Disposition")
                    return HeadProbeResult(
                        finalUrl = finalUrl,
                        mimeType = mime,
                        contentLength = len,
                        supportsRange = acceptRanges.contains("bytes"),
                        suggestedFileName = parseContentDisposition(disp)
                    )
                }
            }
        }

        // Fallback to 1-byte Range GET for servers that reject HEAD
        val rangeReq = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-0")
            .header("User-Agent", "VDPlayer/1.0 (Android)")
            .build()

        return runCatching {
            client.newCall(rangeReq).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) return null
                val finalUrl = resp.request.url.toString()
                val mime = resp.header("Content-Type")?.substringBefore(';')?.trim() ?: "application/octet-stream"
                val contentRange = resp.header("Content-Range")
                val totalFromRange = contentRange?.substringAfterLast('/')?.toLongOrNull()
                val len = totalFromRange ?: resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val supportsRange = resp.code == 206 || (resp.header("Accept-Ranges")?.contains("bytes", true) == true)
                HeadProbeResult(
                    finalUrl = finalUrl,
                    mimeType = mime,
                    contentLength = len,
                    supportsRange = supportsRange,
                    suggestedFileName = parseContentDisposition(resp.header("Content-Disposition"))
                )
            }
        }.getOrNull()
    }

    private fun parseHtmlMediaTags(client: OkHttpClient, pageUrl: String, host: String): AnalyzedMedia? {
        val req = Request.Builder()
            .url(pageUrl)
            .get()
            .header("User-Agent", "VDPlayer/1.0 (Android)")
            .build()

        val html = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                // Limit read to 512 KB to prevent memory spikes on huge HTML pages
                val source = resp.body?.source() ?: return null
                source.request(512 * 1024L)
                source.buffer.clone().readUtf8()
            }
        }.getOrNull() ?: return null

        val title = extractMetaContent(html, "og:title")
            ?: extractHtmlTitle(html)
            ?: "Web Media"
        val thumb = extractMetaContent(html, "og:image")?.let { resolveRelativeUrl(pageUrl, it) }

        val candidates = linkedMapOf<String, String>()
        extractMetaContent(html, "og:video:secure_url")?.let { candidates[resolveRelativeUrl(pageUrl, it)] = "video/mp4" }
        extractMetaContent(html, "og:video")?.let { candidates[resolveRelativeUrl(pageUrl, it)] = "video/mp4" }
        extractMetaContent(html, "og:audio")?.let { candidates[resolveRelativeUrl(pageUrl, it)] = "audio/mpeg" }

        val srcMatcher = Pattern.compile(
            """<(?:video|audio|source)[^>]+src\s*=\s*["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        while (srcMatcher.find()) {
            val found = srcMatcher.group(1)?.trim() ?: continue
            if (!found.startsWith("blob:", true) && !found.startsWith("data:", true)) {
                val abs = resolveRelativeUrl(pageUrl, found)
                val extMime = when {
                    abs.contains(".mp3", true) -> "audio/mpeg"
                    abs.contains(".m4a", true) -> "audio/mp4"
                    abs.contains(".webm", true) -> "video/webm"
                    else -> "video/mp4"
                }
                candidates.putIfAbsent(abs, extMime)
            }
        }

        if (candidates.isEmpty() && thumb != null) {
            candidates[thumb] = "image/jpeg"
        }

        val options = mutableListOf<MediaFormatOption>()
        candidates.entries.take(6).forEachIndexed { idx, (mediaUrl, fallbackMime) ->
            if (FormatUtils.isValidDownloadUrl(mediaUrl, allowHttp = false)) {
                val probe = probeHeaders(client, mediaUrl)
                val mime = probe?.mimeType?.takeIf { !it.contains("text/html") } ?: fallbackMime
                val size = probe?.contentLength ?: -1L
                val sizeLabel = if (size > 0) FormatUtils.formatBytes(size) else "Stream"
                options += MediaFormatOption(
                    id = "html_$idx",
                    label = "${FormatUtils.extensionFromMime(mime).uppercase()} • $sizeLabel",
                    downloadUrl = probe?.finalUrl ?: mediaUrl,
                    mimeType = mime,
                    quality = "Detected Source ${idx + 1}",
                    estimatedBytes = size,
                    supportsRange = probe?.supportsRange ?: false
                )
            }
        }

        if (options.isEmpty()) return null
        val primary = options.first()
        return AnalyzedMedia(
            sourceUrl = pageUrl,
            title = title,
            platform = host,
            thumbnailUrl = thumb,
            durationMs = null,
            totalBytes = primary.estimatedBytes,
            mimeType = primary.mimeType,
            mediaCategory = FormatUtils.classifyMimeType(primary.mimeType, title),
            supportsRange = primary.supportsRange,
            options = options
        )
    }

    private fun extractMetaContent(html: String, propertyName: String): String? {
        val p1 = Pattern.compile(
            """<meta[^>]+(?:property|name)\s*=\s*["']${Pattern.quote(propertyName)}["'][^>]+content\s*=\s*["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        if (p1.find()) return p1.group(1)?.trim()

        val p2 = Pattern.compile(
            """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["']${Pattern.quote(propertyName)}["']""",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        if (p2.find()) return p2.group(1)?.trim()
        return null
    }

    private fun extractHtmlTitle(html: String): String? {
        val m = Pattern.compile("""<title[^>]*>(.*?)</title>""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
        return if (m.find()) m.group(1)?.replace(Regex("\\s+"), " ")?.trim() else null
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String {
        return runCatching { URI(baseUrl).resolve(relative).toString() }.getOrDefault(relative)
    }

    private fun parseContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val matcher = Pattern.compile("""filename\*?=(?:UTF-8''|")?([^";]+)""", Pattern.CASE_INSENSITIVE).matcher(header)
        return if (matcher.find()) {
            runCatching { URLDecoder.decode(matcher.group(1)?.trim() ?: "", "UTF-8") }.getOrNull()
        } else null
    }

    private fun deriveFileNameFromUrl(url: String, mimeType: String): String {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty()
        val last = path.substringAfterLast('/', "").substringBefore('?')
        val ext = FormatUtils.extensionFromMime(mimeType)
        return FormatUtils.sanitizeFileName(last.ifBlank { "media_${System.currentTimeMillis()}.$ext" }, ext)
    }
}
