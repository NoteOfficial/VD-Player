package com.tkofficial.videodownloder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tkofficial.videodownloder.models.AppLanguage
import com.tkofficial.videodownloder.models.AspectRatioMode
import com.tkofficial.videodownloder.models.DecoderMode
import com.tkofficial.videodownloder.models.ThemeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "downloader_va_prefs")

data class AppSettings(
    // General & User Display (strictly separate from fixed DeveloperIdentity)
    val userDisplayName: String = "Media Explorer",
    val language: AppLanguage = AppLanguage.ENGLISH,
    val startDestination: String = "home",

    // Downloads
    val maxConcurrentDownloads: Int = 2,
    val wifiOnlyDownloads: Boolean = false,
    val speedLimitKbps: Int = 0, // 0 = unlimited
    val autoRetryOnFailure: Boolean = true,
    val autoRenameDuplicates: Boolean = true,
    val duplicateCheckEnabled: Boolean = true,
    val verifySha256AfterDownload: Boolean = true,

    // Notifications
    val notifyActiveDownloads: Boolean = true,
    val notifyCompletedDownloads: Boolean = true,
    val notifyFailedDownloads: Boolean = true,
    val notifyStorageWarnings: Boolean = true,

    // Gallery
    val galleryGridColumns: Int = 3,
    val galleryShowFileNames: Boolean = true,
    val gallerySlideshowSeconds: Int = 4,

    // Video Player
    val videoDefaultSpeed: Float = 1.0f,
    val videoDefaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val videoAutoHideControlsMs: Int = 3500,
    val videoGestureSensitivity: Float = 1.0f,
    val videoResumePlayback: Boolean = true,
    val videoAutoNext: Boolean = true,
    val videoDecoderMode: DecoderMode = DecoderMode.AUTO,
    val videoProgressBarAccent: Boolean = true,
    val videoSubtitleFontSp: Float = 18f,
    val videoSubtitleColorHex: Long = 0xFFFFFFFF,
    val videoSubtitleBgAlpha: Float = 0.65f,

    // Music Player
    val musicGaplessEnabled: Boolean = true,
    val musicCrossfadeMs: Int = 0,
    val musicVisualizerEnabled: Boolean = true,

    // Browser
    val browserHomeUrl: String = "https://duckduckgo.com",
    val browserJavaScriptEnabled: Boolean = true,
    val browserMediaSnifferEnabled: Boolean = true,
    val browserSaveHistory: Boolean = true,

    // Network & API Management
    val allowCleartextHttp: Boolean = false,
    val requestTimeoutSeconds: Int = 30,
    val connectTimeoutSeconds: Int = 15,
    val retryLimit: Int = 2,
    val apiEnabled: Boolean = false,
    val apiVersion: String = "v1",
    val customExtractorEndpoint: String = "",
    val secondaryExtractorEndpoint: String = "",
    val backupExtractorEndpoint: String = "",
    val encryptedExtractorApiKey: String = "",

    // Privacy & Security
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockSeconds: Int = 60,
    val hideThumbnailsInRecents: Boolean = false,

    // Performance & Accessibility
    val batterySaverMode: Boolean = false,
    val lowEndDeviceMode: Boolean = false,
    val highContrastLabels: Boolean = false,

    // Persisted SAF Tree URIs for local Video folders
    val persistedVideoFolderUris: Set<String> = emptySet(),

    // Theme Config
    val themeConfig: ThemeConfig = ThemeConfig()
)

class PreferencesRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_display_name")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val START_DEST = stringPreferencesKey("start_dest")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent_downloads")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val SPEED_LIMIT = intPreferencesKey("speed_limit_kbps")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry")
        val AUTO_RENAME = booleanPreferencesKey("auto_rename")
        val DUPLICATE_CHECK = booleanPreferencesKey("duplicate_check")
        val VERIFY_HASH = booleanPreferencesKey("verify_sha256")
        val NOTIFY_ACTIVE = booleanPreferencesKey("notify_active")
        val NOTIFY_COMPLETE = booleanPreferencesKey("notify_complete")
        val NOTIFY_FAILED = booleanPreferencesKey("notify_failed")
        val NOTIFY_STORAGE = booleanPreferencesKey("notify_storage")
        val GALLERY_COLS = intPreferencesKey("gallery_cols")
        val GALLERY_NAMES = booleanPreferencesKey("gallery_names")
        val GALLERY_SLIDESHOW = intPreferencesKey("gallery_slideshow_sec")
        val VIDEO_SPEED = floatPreferencesKey("video_speed")
        val VIDEO_ASPECT = stringPreferencesKey("video_aspect")
        val VIDEO_AUTOHIDE = intPreferencesKey("video_autohide_ms")
        val VIDEO_GESTURE = floatPreferencesKey("video_gesture_sens")
        val VIDEO_RESUME = booleanPreferencesKey("video_resume")
        val VIDEO_AUTONEXT = booleanPreferencesKey("video_autonext")
        val VIDEO_DECODER = stringPreferencesKey("video_decoder")
        val VIDEO_SUB_SIZE = floatPreferencesKey("video_sub_sp")
        val VIDEO_SUB_COLOR = longPreferencesKey("video_sub_color")
        val VIDEO_SUB_BG_ALPHA = floatPreferencesKey("video_sub_bg_alpha")
        val MUSIC_GAPLESS = booleanPreferencesKey("music_gapless")
        val MUSIC_CROSSFADE = intPreferencesKey("music_crossfade_ms")
        val MUSIC_VISUALIZER = booleanPreferencesKey("music_visualizer")
        val BROWSER_HOME = stringPreferencesKey("browser_home")
        val BROWSER_JS = booleanPreferencesKey("browser_js")
        val BROWSER_SNIFFER = booleanPreferencesKey("browser_sniffer")
        val BROWSER_HISTORY = booleanPreferencesKey("browser_history")
        val ALLOW_HTTP = booleanPreferencesKey("allow_cleartext_http")
        val TIMEOUT_SEC = intPreferencesKey("request_timeout_sec")
        val CONNECT_TIMEOUT_SEC = intPreferencesKey("connect_timeout_sec")
        val RETRY_LIMIT = intPreferencesKey("api_retry_limit")
        val API_ENABLED = booleanPreferencesKey("api_enabled")
        val API_VERSION = stringPreferencesKey("api_version")
        val EXTRACTOR_URL = stringPreferencesKey("custom_extractor_url")
        val SECONDARY_EXTRACTOR_URL = stringPreferencesKey("secondary_extractor_url")
        val BACKUP_EXTRACTOR_URL = stringPreferencesKey("backup_extractor_url")
        val EXTRACTOR_KEY_ENC = stringPreferencesKey("encrypted_extractor_key")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_SEC = intPreferencesKey("auto_lock_seconds")
        val HIDE_RECENTS = booleanPreferencesKey("hide_recents")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver_mode")
        val LOW_END = booleanPreferencesKey("low_end_mode")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val SAF_VIDEO_FOLDERS = stringSetPreferencesKey("saf_video_folders")
        val THEME_CONFIG = stringPreferencesKey("theme_config_json")

        // Security internal keys (never exported in backups)
        val PIN_HASH = stringPreferencesKey("sec_pin_hash")
        val PIN_SALT = stringPreferencesKey("sec_pin_salt")
        val FAILED_ATTEMPTS = intPreferencesKey("sec_failed_attempts")
        val LOCKOUT_UNTIL = longPreferencesKey("sec_lockout_until")
    }

    @Volatile
    private var cachedThemeRaw: String? = null

    @Volatile
    private var cachedThemeConfig: ThemeConfig = ThemeConfig()

    private fun decodeThemeConfig(rawJson: String?): ThemeConfig {
        if (rawJson.isNullOrBlank()) return ThemeConfig()
        if (rawJson == cachedThemeRaw) return cachedThemeConfig
        val decoded = runCatching { json.decodeFromString<ThemeConfig>(rawJson) }.getOrNull() ?: ThemeConfig()
        cachedThemeRaw = rawJson
        cachedThemeConfig = decoded
        return decoded
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val langTag = prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.tag
        val lang = AppLanguage.entries.firstOrNull { it.tag == langTag } ?: AppLanguage.ENGLISH
        val aspect = runCatching {
            AspectRatioMode.valueOf(prefs[Keys.VIDEO_ASPECT] ?: AspectRatioMode.FIT.name)
        }.getOrDefault(AspectRatioMode.FIT)
        val decoder = runCatching {
            DecoderMode.valueOf(prefs[Keys.VIDEO_DECODER] ?: DecoderMode.AUTO.name)
        }.getOrDefault(DecoderMode.AUTO)
        val theme = decodeThemeConfig(prefs[Keys.THEME_CONFIG])

        AppSettings(
            userDisplayName = prefs[Keys.USER_NAME] ?: "Media Explorer",
            language = lang,
            startDestination = prefs[Keys.START_DEST] ?: "home",
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT] ?: 2).coerceIn(1, 5),
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY] ?: false,
            speedLimitKbps = prefs[Keys.SPEED_LIMIT] ?: 0,
            autoRetryOnFailure = prefs[Keys.AUTO_RETRY] ?: true,
            autoRenameDuplicates = prefs[Keys.AUTO_RENAME] ?: true,
            duplicateCheckEnabled = prefs[Keys.DUPLICATE_CHECK] ?: true,
            verifySha256AfterDownload = prefs[Keys.VERIFY_HASH] ?: true,
            notifyActiveDownloads = prefs[Keys.NOTIFY_ACTIVE] ?: true,
            notifyCompletedDownloads = prefs[Keys.NOTIFY_COMPLETE] ?: true,
            notifyFailedDownloads = prefs[Keys.NOTIFY_FAILED] ?: true,
            notifyStorageWarnings = prefs[Keys.NOTIFY_STORAGE] ?: true,
            galleryGridColumns = (prefs[Keys.GALLERY_COLS] ?: 3).coerceIn(2, 5),
            galleryShowFileNames = prefs[Keys.GALLERY_NAMES] ?: true,
            gallerySlideshowSeconds = (prefs[Keys.GALLERY_SLIDESHOW] ?: 4).coerceIn(2, 15),
            videoDefaultSpeed = prefs[Keys.VIDEO_SPEED] ?: 1.0f,
            videoDefaultAspectRatio = aspect,
            videoAutoHideControlsMs = prefs[Keys.VIDEO_AUTOHIDE] ?: 3500,
            videoGestureSensitivity = prefs[Keys.VIDEO_GESTURE] ?: 1.0f,
            videoResumePlayback = prefs[Keys.VIDEO_RESUME] ?: true,
            videoAutoNext = prefs[Keys.VIDEO_AUTONEXT] ?: true,
            videoDecoderMode = decoder,
            videoSubtitleFontSp = prefs[Keys.VIDEO_SUB_SIZE] ?: 18f,
            videoSubtitleColorHex = prefs[Keys.VIDEO_SUB_COLOR] ?: 0xFFFFFFFF,
            videoSubtitleBgAlpha = prefs[Keys.VIDEO_SUB_BG_ALPHA] ?: 0.65f,
            musicGaplessEnabled = prefs[Keys.MUSIC_GAPLESS] ?: true,
            musicCrossfadeMs = prefs[Keys.MUSIC_CROSSFADE] ?: 0,
            musicVisualizerEnabled = prefs[Keys.MUSIC_VISUALIZER] ?: true,
            browserHomeUrl = prefs[Keys.BROWSER_HOME] ?: "https://duckduckgo.com",
            browserJavaScriptEnabled = prefs[Keys.BROWSER_JS] ?: true,
            browserMediaSnifferEnabled = prefs[Keys.BROWSER_SNIFFER] ?: true,
            browserSaveHistory = prefs[Keys.BROWSER_HISTORY] ?: true,
            allowCleartextHttp = prefs[Keys.ALLOW_HTTP] ?: false,
            requestTimeoutSeconds = (prefs[Keys.TIMEOUT_SEC] ?: 30).coerceIn(10, 120),
            connectTimeoutSeconds = (prefs[Keys.CONNECT_TIMEOUT_SEC] ?: 15).coerceIn(5, 60),
            retryLimit = (prefs[Keys.RETRY_LIMIT] ?: 2).coerceIn(0, 5),
            apiEnabled = prefs[Keys.API_ENABLED] ?: false,
            apiVersion = prefs[Keys.API_VERSION] ?: "v1",
            customExtractorEndpoint = prefs[Keys.EXTRACTOR_URL] ?: "",
            secondaryExtractorEndpoint = prefs[Keys.SECONDARY_EXTRACTOR_URL] ?: "",
            backupExtractorEndpoint = prefs[Keys.BACKUP_EXTRACTOR_URL] ?: "",
            encryptedExtractorApiKey = prefs[Keys.EXTRACTOR_KEY_ENC] ?: "",
            appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC] ?: false,
            autoLockSeconds = prefs[Keys.AUTO_LOCK_SEC] ?: 60,
            hideThumbnailsInRecents = prefs[Keys.HIDE_RECENTS] ?: false,
            batterySaverMode = prefs[Keys.BATTERY_SAVER] ?: false,
            lowEndDeviceMode = prefs[Keys.LOW_END] ?: false,
            highContrastLabels = prefs[Keys.HIGH_CONTRAST] ?: false,
            persistedVideoFolderUris = prefs[Keys.SAF_VIDEO_FOLDERS] ?: emptySet(),
            themeConfig = theme
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                userDisplayName = prefs[Keys.USER_NAME] ?: "Media Explorer",
                language = AppLanguage.entries.firstOrNull { it.tag == prefs[Keys.LANGUAGE] } ?: AppLanguage.ENGLISH,
                startDestination = prefs[Keys.START_DEST] ?: "home",
                maxConcurrentDownloads = prefs[Keys.MAX_CONCURRENT] ?: 2,
                wifiOnlyDownloads = prefs[Keys.WIFI_ONLY] ?: false,
                speedLimitKbps = prefs[Keys.SPEED_LIMIT] ?: 0,
                autoRetryOnFailure = prefs[Keys.AUTO_RETRY] ?: true,
                autoRenameDuplicates = prefs[Keys.AUTO_RENAME] ?: true,
                duplicateCheckEnabled = prefs[Keys.DUPLICATE_CHECK] ?: true,
                verifySha256AfterDownload = prefs[Keys.VERIFY_HASH] ?: true,
                notifyActiveDownloads = prefs[Keys.NOTIFY_ACTIVE] ?: true,
                notifyCompletedDownloads = prefs[Keys.NOTIFY_COMPLETE] ?: true,
                notifyFailedDownloads = prefs[Keys.NOTIFY_FAILED] ?: true,
                notifyStorageWarnings = prefs[Keys.NOTIFY_STORAGE] ?: true,
                galleryGridColumns = prefs[Keys.GALLERY_COLS] ?: 3,
                galleryShowFileNames = prefs[Keys.GALLERY_NAMES] ?: true,
                gallerySlideshowSeconds = prefs[Keys.GALLERY_SLIDESHOW] ?: 4,
                videoDefaultSpeed = prefs[Keys.VIDEO_SPEED] ?: 1.0f,
                videoDefaultAspectRatio = runCatching { AspectRatioMode.valueOf(prefs[Keys.VIDEO_ASPECT] ?: "FIT") }.getOrDefault(AspectRatioMode.FIT),
                videoAutoHideControlsMs = prefs[Keys.VIDEO_AUTOHIDE] ?: 3500,
                videoGestureSensitivity = prefs[Keys.VIDEO_GESTURE] ?: 1.0f,
                videoResumePlayback = prefs[Keys.VIDEO_RESUME] ?: true,
                videoAutoNext = prefs[Keys.VIDEO_AUTONEXT] ?: true,
                videoDecoderMode = runCatching { DecoderMode.valueOf(prefs[Keys.VIDEO_DECODER] ?: "AUTO") }.getOrDefault(DecoderMode.AUTO),
                videoSubtitleFontSp = prefs[Keys.VIDEO_SUB_SIZE] ?: 18f,
                videoSubtitleColorHex = prefs[Keys.VIDEO_SUB_COLOR] ?: 0xFFFFFFFF,
                videoSubtitleBgAlpha = prefs[Keys.VIDEO_SUB_BG_ALPHA] ?: 0.65f,
                musicGaplessEnabled = prefs[Keys.MUSIC_GAPLESS] ?: true,
                musicCrossfadeMs = prefs[Keys.MUSIC_CROSSFADE] ?: 0,
                musicVisualizerEnabled = prefs[Keys.MUSIC_VISUALIZER] ?: true,
                browserHomeUrl = prefs[Keys.BROWSER_HOME] ?: "https://duckduckgo.com",
                browserJavaScriptEnabled = prefs[Keys.BROWSER_JS] ?: true,
                browserMediaSnifferEnabled = prefs[Keys.BROWSER_SNIFFER] ?: true,
                browserSaveHistory = prefs[Keys.BROWSER_HISTORY] ?: true,
                allowCleartextHttp = prefs[Keys.ALLOW_HTTP] ?: false,
                requestTimeoutSeconds = prefs[Keys.TIMEOUT_SEC] ?: 30,
                connectTimeoutSeconds = prefs[Keys.CONNECT_TIMEOUT_SEC] ?: 15,
                retryLimit = prefs[Keys.RETRY_LIMIT] ?: 2,
                apiEnabled = prefs[Keys.API_ENABLED] ?: false,
                apiVersion = prefs[Keys.API_VERSION] ?: "v1",
                customExtractorEndpoint = prefs[Keys.EXTRACTOR_URL] ?: "",
                secondaryExtractorEndpoint = prefs[Keys.SECONDARY_EXTRACTOR_URL] ?: "",
                backupExtractorEndpoint = prefs[Keys.BACKUP_EXTRACTOR_URL] ?: "",
                encryptedExtractorApiKey = prefs[Keys.EXTRACTOR_KEY_ENC] ?: "",
                appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
                biometricEnabled = prefs[Keys.BIOMETRIC] ?: false,
                autoLockSeconds = prefs[Keys.AUTO_LOCK_SEC] ?: 60,
                hideThumbnailsInRecents = prefs[Keys.HIDE_RECENTS] ?: false,
                batterySaverMode = prefs[Keys.BATTERY_SAVER] ?: false,
                lowEndDeviceMode = prefs[Keys.LOW_END] ?: false,
                highContrastLabels = prefs[Keys.HIGH_CONTRAST] ?: false,
                persistedVideoFolderUris = prefs[Keys.SAF_VIDEO_FOLDERS] ?: emptySet(),
                themeConfig = runCatching { prefs[Keys.THEME_CONFIG]?.let { json.decodeFromString<ThemeConfig>(it) } }.getOrNull() ?: ThemeConfig()
            )
            val updated = transform(current)
            prefs[Keys.USER_NAME] = updated.userDisplayName
            prefs[Keys.LANGUAGE] = updated.language.tag
            prefs[Keys.START_DEST] = updated.startDestination
            prefs[Keys.MAX_CONCURRENT] = updated.maxConcurrentDownloads.coerceIn(1, 5)
            prefs[Keys.WIFI_ONLY] = updated.wifiOnlyDownloads
            prefs[Keys.SPEED_LIMIT] = updated.speedLimitKbps
            prefs[Keys.AUTO_RETRY] = updated.autoRetryOnFailure
            prefs[Keys.AUTO_RENAME] = updated.autoRenameDuplicates
            prefs[Keys.DUPLICATE_CHECK] = updated.duplicateCheckEnabled
            prefs[Keys.VERIFY_HASH] = updated.verifySha256AfterDownload
            prefs[Keys.NOTIFY_ACTIVE] = updated.notifyActiveDownloads
            prefs[Keys.NOTIFY_COMPLETE] = updated.notifyCompletedDownloads
            prefs[Keys.NOTIFY_FAILED] = updated.notifyFailedDownloads
            prefs[Keys.NOTIFY_STORAGE] = updated.notifyStorageWarnings
            prefs[Keys.GALLERY_COLS] = updated.galleryGridColumns.coerceIn(2, 5)
            prefs[Keys.GALLERY_NAMES] = updated.galleryShowFileNames
            prefs[Keys.GALLERY_SLIDESHOW] = updated.gallerySlideshowSeconds
            prefs[Keys.VIDEO_SPEED] = updated.videoDefaultSpeed
            prefs[Keys.VIDEO_ASPECT] = updated.videoDefaultAspectRatio.name
            prefs[Keys.VIDEO_AUTOHIDE] = updated.videoAutoHideControlsMs
            prefs[Keys.VIDEO_GESTURE] = updated.videoGestureSensitivity
            prefs[Keys.VIDEO_RESUME] = updated.videoResumePlayback
            prefs[Keys.VIDEO_AUTONEXT] = updated.videoAutoNext
            prefs[Keys.VIDEO_DECODER] = updated.videoDecoderMode.name
            prefs[Keys.VIDEO_SUB_SIZE] = updated.videoSubtitleFontSp
            prefs[Keys.VIDEO_SUB_COLOR] = updated.videoSubtitleColorHex
            prefs[Keys.VIDEO_SUB_BG_ALPHA] = updated.videoSubtitleBgAlpha
            prefs[Keys.MUSIC_GAPLESS] = updated.musicGaplessEnabled
            prefs[Keys.MUSIC_CROSSFADE] = updated.musicCrossfadeMs
            prefs[Keys.MUSIC_VISUALIZER] = updated.musicVisualizerEnabled
            prefs[Keys.BROWSER_HOME] = updated.browserHomeUrl
            prefs[Keys.BROWSER_JS] = updated.browserJavaScriptEnabled
            prefs[Keys.BROWSER_SNIFFER] = updated.browserMediaSnifferEnabled
            prefs[Keys.BROWSER_HISTORY] = updated.browserSaveHistory
            prefs[Keys.ALLOW_HTTP] = updated.allowCleartextHttp
            prefs[Keys.TIMEOUT_SEC] = updated.requestTimeoutSeconds.coerceIn(10, 120)
            prefs[Keys.CONNECT_TIMEOUT_SEC] = updated.connectTimeoutSeconds.coerceIn(5, 60)
            prefs[Keys.RETRY_LIMIT] = updated.retryLimit.coerceIn(0, 5)
            prefs[Keys.API_ENABLED] = updated.apiEnabled
            prefs[Keys.API_VERSION] = updated.apiVersion.ifBlank { "v1" }
            prefs[Keys.EXTRACTOR_URL] = updated.customExtractorEndpoint
            prefs[Keys.SECONDARY_EXTRACTOR_URL] = updated.secondaryExtractorEndpoint
            prefs[Keys.BACKUP_EXTRACTOR_URL] = updated.backupExtractorEndpoint
            prefs[Keys.EXTRACTOR_KEY_ENC] = updated.encryptedExtractorApiKey
            prefs[Keys.APP_LOCK] = updated.appLockEnabled
            prefs[Keys.BIOMETRIC] = updated.biometricEnabled
            prefs[Keys.AUTO_LOCK_SEC] = updated.autoLockSeconds
            prefs[Keys.HIDE_RECENTS] = updated.hideThumbnailsInRecents
            prefs[Keys.BATTERY_SAVER] = updated.batterySaverMode
            prefs[Keys.LOW_END] = updated.lowEndDeviceMode
            prefs[Keys.HIGH_CONTRAST] = updated.highContrastLabels
            prefs[Keys.SAF_VIDEO_FOLDERS] = updated.persistedVideoFolderUris
            prefs[Keys.THEME_CONFIG] = json.encodeToString(updated.themeConfig)
        }
    }

    val pinStateFlow: Flow<Triple<String?, String?, Pair<Int, Long>>> = context.dataStore.data.map { prefs ->
        Triple(
            prefs[Keys.PIN_HASH],
            prefs[Keys.PIN_SALT],
            (prefs[Keys.FAILED_ATTEMPTS] ?: 0) to (prefs[Keys.LOCKOUT_UNTIL] ?: 0L)
        )
    }

    suspend fun savePinHash(hashBase64: String, saltBase64: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hashBase64
            prefs[Keys.PIN_SALT] = saltBase64
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun clearPin() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL] = 0L
            prefs[Keys.APP_LOCK] = false
        }
    }

    suspend fun recordUnlockAttempt(success: Boolean, lockoutUntilMs: Long = 0L) {
        context.dataStore.edit { prefs ->
            if (success) {
                prefs[Keys.FAILED_ATTEMPTS] = 0
                prefs[Keys.LOCKOUT_UNTIL] = 0L
            } else {
                val count = (prefs[Keys.FAILED_ATTEMPTS] ?: 0) + 1
                prefs[Keys.FAILED_ATTEMPTS] = count
                if (lockoutUntilMs > 0L) {
                    prefs[Keys.LOCKOUT_UNTIL] = lockoutUntilMs
                }
            }
        }
    }

    suspend fun addPersistedVideoFolder(uriString: String) {
        updateSettings { it.copy(persistedVideoFolderUris = it.persistedVideoFolderUris + uriString) }
    }
}
