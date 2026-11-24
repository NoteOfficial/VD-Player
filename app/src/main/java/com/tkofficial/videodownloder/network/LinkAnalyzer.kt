package com.tkofficial.videodownloder.network

import com.tkofficial.videodownloder.models.AnalyzedMedia
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.models.MediaFormatOption
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LinkAnalyzer(
    private val baseClient: OkHttpClient
) {
    internal data class ExtractorOutcome(
        val media: AnalyzedMedia? = null,
        val errorReason: String? = null,
        val isAuthFailure: Boolean = false
    )

    companion object {
        internal const val BROWSER_USER_AGENT = ApiConfigManager.BROWSER_USER_AGENT
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val tolerantJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    fun buildClient(timeoutSeconds: Int, connectTimeoutSeconds: Int = 15): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(connectTimeoutSeconds.coerceIn(5, 60).toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.coerceIn(10, 120).toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.coerceIn(10, 120).toLong(), TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun analyze(
        rawUrl: String,
        allowHttp: Boolean,
        timeoutSeconds: Int,
        customEndpoint: String,
        decryptedApiKey: String,
        secondaryEndpoint: String = "",
        backupEndpoint: String = "",
        apiVersion: String = "v1",
        connectTimeoutSeconds: Int = 15,
        retryLimit: Int = 2,
        apiEnabled: Boolean = true
    ): Result<AnalyzedMedia> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!FormatUtils.isValidDownloadUrl(url, allowHttp)) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (allowHttp) "Invalid URL. Enter a valid http:// or https:// URL."
                    else "HTTPS-only policy active. Enter a valid https:// URL or enable HTTP in Network Settings."
                )
            )
        }

        val client = buildClient(timeoutSeconds, connectTimeoutSeconds)

        // 1. If API service is enabled and endpoints are configured, query Primary -> Secondary -> Backup
        val orderedEndpoints = listOf(customEndpoint, secondaryEndpoint, backupEndpoint)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        var apiErrorNote: String? = null
        if (apiEnabled && orderedEndpoints.isNotEmpty()) {
            for (endpoint in orderedEndpoints) {
                if (!FormatUtils.isValidDownloadUrl(endpoint, allowHttp = allowHttp)) {
                    apiErrorNote = "Invalid Configuration: $endpoint"
                    continue
                }
                val attempts = retryLimit.coerceIn(0, 5) + 1
                for (attempt in 0 until attempts) {
                    val outcome = queryCustomExtractor(
                        client = client,
                        endpoint = endpoint,
                        apiKey = decryptedApiKey,
                        apiVersion = apiVersion,
                        targetUrl = url,
                        allowHttp = allowHttp
                    )
                    if (outcome.media != null) {
                        return@withContext Result.success(outcome.media)
                    }
                    apiErrorNote = outcome.errorReason
                    if (outcome.isAuthFailure) break // Do not retry 401/403 on same endpoint
                }
            }
        }

        // 2. Direct HTTP HEAD / GET Range probe for authentic server headers
        val headInfo = probeHeaders(client, url)
            ?: return@withContext Result.failure(
                IllegalStateException(
                    apiErrorNote ?: "Unable to reach host or read HTTP headers from ${FormatUtils.extractHost(url)}."
                )
            )

        val contentType = headInfo.mimeType.lowercase()
        val host = FormatUtils.extractHost(headInfo.finalUrl)

        // If the server returned an HTML webpage, parse real <video>, <audio>, <meta property="og:..."> tags
        if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
            val htmlMedia = parseHtmlMediaTags(client, headInfo.finalUrl, host, allowHttp)
            if (htmlMedia != null) {
                return@withContext Result.success(htmlMedia)
            }
            val apiHint = when {
                apiErrorNote != null -> apiErrorNote
                !apiEnabled || orderedEndpoints.isEmpty() -> "API Not Configured. Configure an authorized API in Settings -> API Management for protected platforms."
                else -> "Unsupported platform or no downloadable media stream found on $host."
            }
            return@withContext Result.failure(
                IllegalStateException(apiHint)
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

    private fun queryCustomExtractor(
        client: OkHttpClient,
        endpoint: String,
        apiKey: String,
        apiVersion: String,
        targetUrl: String,
        allowHttp: Boolean
    ): ExtractorOutcome {
        return runCatching {
            val ver = apiVersion.ifBlank { "v1" }
            val payloadJson = tolerantJson.encodeToString(
                JsonObject.serializer(),
                JsonObject(mapOf("url" to JsonPrimitive(targetUrl)))
            )

            fun Request.Builder.withApiHeaders(): Request.Builder = apply {
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "application/json")
                header("X-Api-Version", ver)
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer $apiKey")
                    header("X-API-Key", apiKey)
                }
            }

            val postReq = Request.Builder()
                .url(endpoint)
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                .withApiHeaders()
                .build()

            var statusCode: Int
            var rawBody: String
            client.newCall(postReq).execute().use { resp ->
                statusCode = resp.code
                rawBody = resp.body?.string().orEmpty()
            }

            // Fallback to GET ?url=... if endpoint rejects POST (405 / 404 / 501)
            if (statusCode in listOf(404, 405, 501)) {
                val sep = if (endpoint.contains('?')) "&" else "?"
                val encodedTarget = URLEncoder.encode(targetUrl, "UTF-8")
                val getReq = Request.Builder()
                    .url("$endpoint${sep}url=$encodedTarget")
                    .get()
                    .withApiHeaders()
                    .build()
                client.newCall(getReq).execute().use { getResp ->
                    statusCode = getResp.code
                    rawBody = getResp.body?.string().orEmpty()
                }
            }

            if (statusCode == 401 || statusCode == 403) {
                return ExtractorOutcome(
                    errorReason = "Authentication Failed (HTTP $statusCode)",
                    isAuthFailure = true
                )
            }
            if (statusCode == 451) {
                return ExtractorOutcome(errorReason = "Download Blocked (HTTP 451)")
            }
            if (statusCode !in 200..299) {
                val apiMsg = runCatching {
                    val obj = tolerantJson.parseToJsonElement(rawBody) as? JsonObject
                    extractErrorMessage(obj)
                }.getOrNull()
                return ExtractorOutcome(
                    errorReason = apiMsg ?: "API Connection Failed (HTTP $statusCode)"
                )
            }

            if (rawBody.isBlank()) {
                return ExtractorOutcome(errorReason = "API Connection Failed: Empty JSON body")
            }

            val parsed = parseExtractorJson(
                rawJson = rawBody,
                endpoint = endpoint,
                targetUrl = targetUrl,
                allowHttp = allowHttp
            )
            if (parsed.errorReason != null || parsed.media == null) {
                return parsed
            }

            // If API returned a single stream with unknown mime/size, do one fast optional probe without failing extraction
            val media = parsed.media
            if (media.options.size == 1 && media.options.first().mimeType == "application/octet-stream") {
                val first = media.options.first()
                val head = probeHeaders(client, first.downloadUrl)
                if (head != null) {
                    val refinedMime = head.mimeType.takeIf { !it.contains("text/html") } ?: first.mimeType
                    val refinedOpt = first.copy(
                        mimeType = refinedMime,
                        estimatedBytes = if (first.estimatedBytes > 0) first.estimatedBytes else head.contentLength,
                        supportsRange = head.supportsRange
                    )
                    return ExtractorOutcome(
                        media = media.copy(
                            totalBytes = refinedOpt.estimatedBytes,
                            mimeType = refinedMime,
                            mediaCategory = FormatUtils.classifyMimeType(refinedMime, media.title),
                            supportsRange = refinedOpt.supportsRange,
                            options = listOf(refinedOpt)
                        )
                    )
                }
            }
            parsed
        }.getOrElse { err ->
            ExtractorOutcome(errorReason = "API Connection Failed: ${err.localizedMessage ?: "Network error"}")
        }
    }

    internal fun parseExtractorJson(
        rawJson: String,
        endpoint: String,
        targetUrl: String,
        allowHttp: Boolean
    ): ExtractorOutcome {
        val rootElem = runCatching { tolerantJson.parseToJsonElement(rawJson) }.getOrNull()
            ?: return ExtractorOutcome(errorReason = "API returned invalid JSON response")

        val unwrapped = unwrapContainerObject(rootElem)
        val rootObj = rootElem as? JsonObject

        // Explicit failure payloads (e.g., {"error": "..."} or {"status": "error", "error": {"code": "..."}})
        val rootErr = extractErrorMessage(unwrapped) ?: extractErrorMessage(rootObj)
        val statusFlag = unwrapped?.str("status")?.lowercase() ?: rootObj?.str("status")?.lowercase()
        val successFlag = unwrapped?.bool("success") ?: rootObj?.bool("success")

        val collectedOptions = mutableListOf<MediaFormatOption>()
        val seenUrls = linkedSetOf<String>()

        fun addOptionIfValid(
            rawCandidateUrl: String?,
            label: String?,
            quality: String?,
            mimeHint: String?,
            sizeBytes: Long
        ) {
            val trimmed = rawCandidateUrl?.trim().orEmpty()
            if (trimmed.isBlank()) return
            val resolved = resolveRelativeUrl(endpoint, trimmed)
            if (!FormatUtils.isValidDownloadUrl(resolved, allowHttp = allowHttp)) return
            if (!seenUrls.add(resolved)) return

            val inferredMime = inferMimeType(mimeHint, resolved, label, quality)
            val resolvedQuality = quality?.takeIf { it.isNotBlank() } ?: "API Stream"
            val sizeText = if (sizeBytes > 0L) FormatUtils.formatBytes(sizeBytes) else null
            val resolvedLabel = when {
                !label.isNullOrBlank() && sizeText != null && !label.contains(sizeText) -> "$label ($sizeText)"
                !label.isNullOrBlank() -> label
                sizeText != null -> "$resolvedQuality • $sizeText"
                else -> resolvedQuality
            }

            collectedOptions += MediaFormatOption(
                id = "api_opt_${collectedOptions.size}",
                label = resolvedLabel,
                downloadUrl = resolved,
                mimeType = inferredMime,
                quality = resolvedQuality,
                estimatedBytes = sizeBytes,
                supportsRange = true
            )
        }

        // Array keys commonly used by yt-dlp wrappers, Cobalt picker, and custom REST downloaders
        val arrayKeys = listOf(
            "options", "formats", "streams", "medias", "links",
            "videos", "audio", "picker", "qualities", "downloads"
        )
        val searchObjects = listOfNotNull(unwrapped, rootObj).distinct()

        for (obj in searchObjects) {
            for (key in arrayKeys) {
                val arr = obj[key] as? JsonArray ?: continue
                arr.forEachIndexed { idx, item ->
                    when (item) {
                        is JsonPrimitive -> {
                            addOptionIfValid(
                                rawCandidateUrl = item.contentOrNull,
                                label = "Stream ${idx + 1}",
                                quality = if (key == "audio") "Audio" else "API Stream",
                                mimeHint = if (key == "audio") "audio/mpeg" else null,
                                sizeBytes = -1L
                            )
                        }
                        is JsonObject -> {
                            val ext = item.str("ext")?.lowercase()
                            val protocol = item.str("protocol")?.lowercase()
                            if (ext == "mhtml" || protocol == "mhtml") return@forEachIndexed

                            val itemUrl = firstNonBlankStr(
                                item,
                                "url", "downloadUrl", "download_url", "videoUrl", "video_url",
                                "audioUrl", "audio_url", "streamUrl", "stream_url", "link", "file", "src", "href", "uri"
                            )
                            val height = item.longVal("height")
                            val quality = firstNonBlankStr(
                                item,
                                "quality", "resolution", "format_note", "format", "label", "type"
                            ) ?: if (height != null && height > 0) "${height}p" else null
                            val mime = firstNonBlankStr(
                                item,
                                "mimeType", "mime_type", "contentType", "content_type", "mime"
                            ) ?: ext?.let { mimeFromExtension(it) }
                                ?: if (key == "audio" || item.str("vcodec") == "none") "audio/mpeg" else null
                            val label = firstNonBlankStr(item, "label", "format_note", "quality", "resolution", "name")
                                ?: quality
                            val size = firstPositiveLong(
                                item,
                                "sizeBytes", "size_bytes", "filesize", "filesize_approx",
                                "size", "contentLength", "content_length"
                            ) ?: -1L
                            addOptionIfValid(itemUrl, label, quality, mime, size)
                        }
                        else -> Unit
                    }
                }
            }
        }

        // Single-URL keys at unwrapped or root level (Cobalt redirect/tunnel or simple REST extractors)
        val singleUrlKeys = listOf(
            "url", "downloadUrl", "download_url", "videoUrl", "video_url",
            "streamUrl", "stream_url", "link", "file", "href"
        )
        for (obj in searchObjects) {
            val single = firstNonBlankStr(obj, *singleUrlKeys.toTypedArray())
            if (single != null) {
                val filename = firstNonBlankStr(obj, "filename", "fileName", "name")
                val mime = firstNonBlankStr(obj, "mimeType", "mime_type", "contentType", "content_type", "mime")
                    ?: obj.str("ext")?.let { mimeFromExtension(it) }
                val quality = firstNonBlankStr(obj, "quality", "resolution", "status")
                val size = firstPositiveLong(obj, "sizeBytes", "size_bytes", "filesize", "size", "contentLength") ?: -1L
                addOptionIfValid(
                    rawCandidateUrl = single,
                    label = filename ?: quality ?: "Primary Stream",
                    quality = quality ?: "API Default",
                    mimeHint = mime,
                    sizeBytes = size
                )
            }
            // Separate audioUrl/audio string field
            val audioDirect = firstNonBlankStr(obj, "audioUrl", "audio_url", "audio")
            if (audioDirect != null && audioDirect.startsWith("http", ignoreCase = true)) {
                addOptionIfValid(
                    rawCandidateUrl = audioDirect,
                    label = "Audio Track",
                    quality = "Audio",
                    mimeHint = "audio/mpeg",
                    sizeBytes = -1L
                )
            }
        }

        // Also support top-level JsonArray of URL strings or format objects
        if (collectedOptions.isEmpty() && rootElem is JsonArray) {
            rootElem.forEachIndexed { idx, item ->
                when (item) {
                    is JsonPrimitive -> addOptionIfValid(item.contentOrNull, "Stream ${idx + 1}", "API Stream", null, -1L)
                    is JsonObject -> {
                        val u = firstNonBlankStr(item, *singleUrlKeys.toTypedArray())
                        val q = firstNonBlankStr(item, "quality", "resolution", "label")
                        val m = firstNonBlankStr(item, "mimeType", "mime_type", "mime")
                        val sz = firstPositiveLong(item, "sizeBytes", "filesize", "size") ?: -1L
                        addOptionIfValid(u, q, q, m, sz)
                    }
                    else -> Unit
                }
            }
        }

        if (collectedOptions.isEmpty()) {
            val fallbackErr = when {
                !rootErr.isNullOrBlank() -> rootErr
                statusFlag == "error" || successFlag == false ->
                    firstNonBlankStr(unwrapped ?: rootObj, "text", "message", "detail") ?: "API reported extraction error"
                else -> "No downloadable stream returned by API"
            }
            return ExtractorOutcome(errorReason = fallbackErr)
        }

        val title = firstNonBlankStr(unwrapped, "title", "text", "filename", "fileName", "name")
            ?: firstNonBlankStr(rootObj, "title", "text", "filename", "fileName", "name")
        val rawThumb = firstNonBlankStr(unwrapped, "thumbnail", "thumb", "cover", "poster", "image", "pic")
            ?: firstNonBlankStr(rootObj, "thumbnail", "thumb", "cover", "poster", "image", "pic")
        val thumb = rawThumb?.let { resolveRelativeUrl(endpoint, it) }
            ?.takeIf { FormatUtils.isValidDownloadUrl(it, allowHttp = allowHttp) }

        val durationMs = extractDurationMs(unwrapped) ?: extractDurationMs(rootObj)
        val first = collectedOptions.first()
        val resolvedTitle = title ?: deriveFileNameFromUrl(first.downloadUrl, first.mimeType)

        return ExtractorOutcome(
            media = AnalyzedMedia(
                sourceUrl = targetUrl,
                title = resolvedTitle,
                platform = "${FormatUtils.extractHost(targetUrl)} (API)",
                thumbnailUrl = thumb,
                durationMs = durationMs,
                totalBytes = first.estimatedBytes,
                mimeType = first.mimeType,
                mediaCategory = FormatUtils.classifyMimeType(first.mimeType, resolvedTitle),
                supportsRange = first.supportsRange,
                options = collectedOptions
            )
        )
    }

    private fun unwrapContainerObject(element: JsonElement): JsonObject? {
        var current = element as? JsonObject ?: return null
        val wrapperKeys = listOf("data", "result", "response", "payload", "media")
        repeat(4) {
            val next = wrapperKeys.firstNotNullOfOrNull { k -> current[k] as? JsonObject } ?: return current
            current = next
        }
        return current
    }

    private fun extractErrorMessage(obj: JsonObject?): String? {
        if (obj == null) return null
        val errElem = obj["error"]
        when (errElem) {
            is JsonPrimitive -> errElem.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            is JsonObject -> {
                firstNonBlankStr(errElem, "message", "text", "detail", "code")?.let { return it }
            }
            else -> Unit
        }
        firstNonBlankStr(obj, "errorMessage", "error_message", "detail")?.let { return it }
        val status = obj.str("status")?.lowercase()
        val success = obj.bool("success")
        if (status == "error" || success == false) {
            return firstNonBlankStr(obj, "message", "text", "reason")
        }
        return null
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.longVal(key: String): Long? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.longOrNull ?: prim.doubleOrNull?.toLong()
    }

    private fun firstNonBlankStr(obj: JsonObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (k in keys) {
            val v = obj.str(k)
            if (v != null) return v
        }
        return null
    }

    private fun firstPositiveLong(obj: JsonObject?, vararg keys: String): Long? {
        if (obj == null) return null
        for (k in keys) {
            val v = obj.longVal(k)
            if (v != null && v > 0L) return v
        }
        return null
    }

    private fun extractDurationMs(obj: JsonObject?): Long? {
        if (obj == null) return null
        obj.longVal("durationMs")?.takeIf { it > 0L }?.let { return it }
        obj.longVal("duration_ms")?.takeIf { it > 0L }?.let { return it }
        val sec = (obj["duration"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0.0 } ?: return null
        return if (sec < 86_400.0) (sec * 1000.0).toLong() else sec.toLong()
    }

    private fun mimeFromExtension(ext: String): String = when (ext.lowercase().removePrefix(".")) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "video/mp4"
    }

    private fun inferMimeType(mimeHint: String?, url: String, label: String?, quality: String?): String {
        val cleanHint = mimeHint?.substringBefore(';')?.trim()?.lowercase()
        if (!cleanHint.isNullOrBlank() && cleanHint.contains('/')) return cleanHint
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url).lowercase()
        val combined = "$path ${label.orEmpty()} ${quality.orEmpty()}".lowercase()
        return when {
            path.endsWith(".mp3") || combined.contains("mp3") -> "audio/mpeg"
            path.endsWith(".m4a") || combined.contains("m4a") -> "audio/mp4"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".mp4") -> "video/mp4"
            combined.contains("audio") -> "audio/mpeg"
            else -> "video/mp4"
        }
    }

    private data class HeadProbeResult(
        val finalUrl: String,
        val mimeType: String,
        val contentLength: Long,
        val supportsRange: Boolean,
        val suggestedFileName: String?
    )

    private fun probeHeaders(client: OkHttpClient, url: String): HeadProbeResult? {
        val referer = runCatching {
            val uri = URI(url)
            if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) "${uri.scheme}://${uri.host}/" else null
        }.getOrNull()

        val headReq = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .apply { if (referer != null) header("Referer", referer) }
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
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .apply { if (referer != null) header("Referer", referer) }
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

    private fun parseHtmlMediaTags(client: OkHttpClient, pageUrl: String, host: String, allowHttp: Boolean = false): AnalyzedMedia? {
        val req = Request.Builder()
            .url(pageUrl)
            .get()
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", pageUrl)
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
            if (FormatUtils.isValidDownloadUrl(mediaUrl, allowHttp = allowHttp)) {
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
