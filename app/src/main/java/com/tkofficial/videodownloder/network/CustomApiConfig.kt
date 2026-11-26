package com.tkofficial.videodownloder.network

/**
 * Direct In-Code API Configuration for VD Player.
 *
 * You can configure your extractor/downloader API directly in any of these 3 places:
 * 1) Here in Kotlin code: `app/src/main/java/com/tkofficial/videodownloder/network/CustomApiConfig.kt`
 *    (Also visible in MT Manager inside `classes*.dex` -> `com.tkofficial.videodownloder.network.CustomApiConfig`)
 * 2) In APK assets JSON: `app/src/main/assets/configuration/app_settings.json`
 *    (Visible in MT Manager at `assets/configuration/app_settings.json`)
 * 3) Inside the running app: Profile -> Settings Center -> API Management
 *
 * To pre-configure your API in code before building / pushing to GitHub:
 * - Set `API_ENABLED = true`
 * - Set `PRIMARY_ENDPOINT = "https://your-api-domain.com/v1/resolve"`
 * - Optionally set `SECONDARY_ENDPOINT`, `BACKUP_ENDPOINT`, and `API_KEY`
 */
object CustomApiConfig {
    @JvmField val API_ENABLED: Boolean = false
    @JvmField val PRIMARY_ENDPOINT: String = ""
    @JvmField val SECONDARY_ENDPOINT: String = ""
    @JvmField val BACKUP_ENDPOINT: String = ""
    @JvmField val API_KEY: String = ""
    @JvmField val API_VERSION: String = "v1"
    @JvmField val ALLOW_CLEARTEXT_HTTP: Boolean = false
    @JvmField val REQUEST_TIMEOUT_SECONDS: Int = 30
    @JvmField val CONNECT_TIMEOUT_SECONDS: Int = 15
    @JvmField val RETRY_LIMIT: Int = 2

    fun isConfigured(): Boolean = API_ENABLED && PRIMARY_ENDPOINT.isNotBlank()

    fun toFileConfig(): MediaServiceFileConfig = MediaServiceFileConfig(
        enabled = API_ENABLED,
        status = if (isConfigured()) "configured" else "not_configured",
        primaryEndpoint = PRIMARY_ENDPOINT.trim(),
        secondaryEndpoint = SECONDARY_ENDPOINT.trim(),
        backupEndpoint = BACKUP_ENDPOINT.trim(),
        apiVersion = API_VERSION.ifBlank { "v1" },
        requestTimeoutSeconds = REQUEST_TIMEOUT_SECONDS.coerceIn(5, 120),
        connectTimeoutSeconds = CONNECT_TIMEOUT_SECONDS.coerceIn(5, 60),
        retryLimit = RETRY_LIMIT.coerceIn(0, 5)
    )
}
