package com.tkofficial.videodownloder.network

import android.content.Context
import android.net.Uri
import com.tkofficial.videodownloder.data.AppSettings
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class MediaServiceFileConfig(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("status") val status: String = "not_configured",
    @SerialName("primary_endpoint") val primaryEndpoint: String = "",
    @SerialName("secondary_endpoint") val secondaryEndpoint: String = "",
    @SerialName("backup_endpoint") val backupEndpoint: String = "",
    @SerialName("api_version") val apiVersion: String = "v1",
    @SerialName("request_timeout_seconds") val requestTimeoutSeconds: Int = 30,
    @SerialName("connect_timeout_seconds") val connectTimeoutSeconds: Int = 15,
    @SerialName("retry_limit") val retryLimit: Int = 2
)

@Serializable
data class AppSettingsFileRoot(
    @SerialName("media_service") val mediaService: MediaServiceFileConfig = MediaServiceFileConfig()
)

enum class ApiConfigStatus(val code: String) {
    NOT_CONFIGURED("API Not Configured"),
    CONFIGURED_READY("Configured"),
    CONNECTED("Connected / Ready"),
    CONNECTION_FAILED("API Connection Failed"),
    INVALID_CONFIG("Invalid Configuration"),
    AUTH_FAILED("Authentication Failed"),
    DOWNLOAD_BLOCKED("Download Blocked")
}

data class ApiTestResult(
    val status: ApiConfigStatus,
    val message: String,
    val activeEndpoint: String? = null,
    val httpCode: Int? = null
)

class ApiConfigManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val ASSET_CONFIG_PATH = "configuration/app_settings.json"
        internal const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun parseConfigJson(rawJson: String): Result<MediaServiceFileConfig> = runCatching {
            val root = json.decodeFromString<AppSettingsFileRoot>(rawJson)
            root.mediaService.copy(
                requestTimeoutSeconds = root.mediaService.requestTimeoutSeconds.coerceIn(5, 120),
                connectTimeoutSeconds = root.mediaService.connectTimeoutSeconds.coerceIn(5, 60),
                retryLimit = root.mediaService.retryLimit.coerceIn(0, 5),
                apiVersion = root.mediaService.apiVersion.ifBlank { "v1" }
            )
        }

        fun serializeConfigJson(config: MediaServiceFileConfig): String {
            return json.encodeToString(AppSettingsFileRoot(config))
        }

        fun isValidEndpointUrl(endpoint: String, allowHttp: Boolean = false): Boolean {
            val trimmed = endpoint.trim()
            if (trimmed.isBlank()) return false
            return try {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
                val okScheme = if (allowHttp) (scheme == "https" || scheme == "http") else (scheme == "https")
                okScheme && !uri.host.isNullOrBlank()
            } catch (_: Exception) {
                false
            }
        }

        fun resolveOrderedEndpoints(settings: AppSettings): List<String> {
            return listOf(
                settings.customExtractorEndpoint,
                settings.secondaryExtractorEndpoint,
                settings.backupExtractorEndpoint
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        }

        fun computeStaticStatus(settings: AppSettings): ApiConfigStatus {
            val endpoints = resolveOrderedEndpoints(settings)
            if (endpoints.isEmpty()) return ApiConfigStatus.NOT_CONFIGURED
            if (endpoints.any { !isValidEndpointUrl(it, allowHttp = settings.allowCleartextHttp) }) {
                return ApiConfigStatus.INVALID_CONFIG
            }
            return if (settings.apiEnabled) ApiConfigStatus.CONFIGURED_READY else ApiConfigStatus.NOT_CONFIGURED
        }
    }

    fun loadDefaultAssetConfig(): MediaServiceFileConfig {
        return runCatching {
            context.assets.open(ASSET_CONFIG_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
                parseConfigJson(reader.readText()).getOrDefault(MediaServiceFileConfig())
            }
        }.getOrDefault(MediaServiceFileConfig())
    }

    suspend fun importConfigFromUri(uri: Uri): Result<MediaServiceFileConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("Unable to read selected JSON file.")
            parseConfigJson(text).getOrThrow()
        }
    }

    suspend fun exportConfigToUri(uri: Uri, settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = MediaServiceFileConfig(
                enabled = settings.apiEnabled,
                status = if (resolveOrderedEndpoints(settings).isEmpty()) "not_configured" else "configured",
                primaryEndpoint = settings.customExtractorEndpoint,
                secondaryEndpoint = settings.secondaryExtractorEndpoint,
                backupEndpoint = settings.backupExtractorEndpoint,
                apiVersion = settings.apiVersion,
                requestTimeoutSeconds = settings.requestTimeoutSeconds,
                connectTimeoutSeconds = settings.connectTimeoutSeconds,
                retryLimit = settings.retryLimit
            )
            val payload = serializeConfigJson(cfg)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(payload)
            } ?: error("Unable to write JSON file.")
        }
    }

    suspend fun testConnection(
        settings: AppSettings,
        decryptedApiKey: String
    ): ApiTestResult = withContext(Dispatchers.IO) {
        val endpoints = resolveOrderedEndpoints(settings)
        if (endpoints.isEmpty()) {
            return@withContext ApiTestResult(
                status = ApiConfigStatus.NOT_CONFIGURED,
                message = ApiConfigStatus.NOT_CONFIGURED.code
            )
        }

        val invalid = endpoints.firstOrNull { !isValidEndpointUrl(it, allowHttp = settings.allowCleartextHttp) }
        if (invalid != null) {
            return@withContext ApiTestResult(
                status = ApiConfigStatus.INVALID_CONFIG,
                message = "Invalid Configuration: $invalid"
            )
        }

        val client = okHttpClient.newBuilder()
            .connectTimeout(settings.connectTimeoutSeconds.coerceIn(5, 60).toLong(), TimeUnit.SECONDS)
            .readTimeout(settings.requestTimeoutSeconds.coerceIn(10, 120).toLong(), TimeUnit.SECONDS)
            .build()

        var lastError = ApiConfigStatus.CONNECTION_FAILED.code
        var lastStatus = ApiConfigStatus.CONNECTION_FAILED
        var lastCode: Int? = null
        val versionHeader = settings.apiVersion.ifBlank { "v1" }

        for (endpoint in endpoints) {
            val host = FormatUtils.extractHost(endpoint)
            val attempts = (settings.retryLimit.coerceIn(0, 3)) + 1
            for (attempt in 0 until attempts) {
                var stopEndpointRetries = false
                val res = runCatching {
                    fun baseReq(): Request.Builder = Request.Builder()
                        .url(endpoint)
                        .header("User-Agent", BROWSER_USER_AGENT)
                        .header("Accept", "application/json")
                        .header("X-Api-Version", versionHeader)
                        .apply {
                            if (decryptedApiKey.isNotBlank()) {
                                header("Authorization", "Bearer $decryptedApiKey")
                                header("X-API-Key", decryptedApiKey)
                            }
                        }

                    // 1. Fast HEAD probe
                    client.newCall(baseReq().head().build()).execute().use { headResp ->
                        lastCode = headResp.code
                        when {
                            headResp.isSuccessful || headResp.code == 405 -> {
                                return@withContext ApiTestResult(
                                    status = ApiConfigStatus.CONNECTED,
                                    message = "Connected / Ready ($host • HTTP ${headResp.code})",
                                    activeEndpoint = endpoint,
                                    httpCode = headResp.code
                                )
                            }
                            headResp.code == 401 || headResp.code == 403 -> {
                                lastStatus = ApiConfigStatus.AUTH_FAILED
                                lastError = "Authentication Failed (HTTP ${headResp.code} at $host)"
                                stopEndpointRetries = true
                            }
                            headResp.code in listOf(400, 404, 415, 422, 501) -> {
                                // Many POST-only extractor APIs (Cobalt, FastAPI, Express, CF Workers) reject HEAD.
                                // Probe with a lightweight POST JSON validation payload before concluding failure.
                                val postReq = baseReq()
                                    .post("""{"url":""}""".toRequestBody(JSON_MEDIA_TYPE))
                                    .build()
                                client.newCall(postReq).execute().use { postResp ->
                                    lastCode = postResp.code
                                    val isJsonReply = postResp.header("Content-Type")
                                        ?.contains("json", ignoreCase = true) == true
                                    when {
                                        postResp.isSuccessful ||
                                            postResp.code == 405 ||
                                            ((postResp.code == 400 || postResp.code == 422) && isJsonReply) -> {
                                            return@withContext ApiTestResult(
                                                status = ApiConfigStatus.CONNECTED,
                                                message = "Connected / Ready ($host • HTTP ${postResp.code})",
                                                activeEndpoint = endpoint,
                                                httpCode = postResp.code
                                            )
                                        }
                                        postResp.code == 401 || postResp.code == 403 -> {
                                            lastStatus = ApiConfigStatus.AUTH_FAILED
                                            lastError = "Authentication Failed (HTTP ${postResp.code} at $host)"
                                            stopEndpointRetries = true
                                        }
                                        else -> {
                                            lastStatus = ApiConfigStatus.CONNECTION_FAILED
                                            lastError = "API Connection Failed (HTTP ${postResp.code} at $host)"
                                        }
                                    }
                                }
                            }
                            else -> {
                                lastStatus = ApiConfigStatus.CONNECTION_FAILED
                                lastError = "API Connection Failed (HTTP ${headResp.code} at $host)"
                            }
                        }
                    }
                }
                res.onFailure { err ->
                    lastStatus = ApiConfigStatus.CONNECTION_FAILED
                    lastError = "API Connection Failed: ${err.localizedMessage ?: "Host unreachable"}"
                }
                if (stopEndpointRetries) break
            }
        }

        ApiTestResult(
            status = lastStatus,
            message = lastError,
            httpCode = lastCode
        )
    }
}
