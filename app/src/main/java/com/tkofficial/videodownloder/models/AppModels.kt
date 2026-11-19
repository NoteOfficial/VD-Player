package com.tkofficial.videodownloder.models

import androidx.annotation.StringRes
import com.example.R
import kotlinx.serialization.Serializable

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class MediaCategory {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,
    ARCHIVE,
    OTHER
}

enum class ThemePreset(val displayName: String, @StringRes val labelRes: Int) {
    DARK("Dark", R.string.preset_dark),
    LIGHT("Light", R.string.preset_light),
    AMOLED("AMOLED", R.string.preset_amoled),
    NEON_GLOW("Neon Glow", R.string.preset_neon),
    AURORA("Aurora", R.string.preset_aurora),
    CYBERPUNK("Cyberpunk", R.string.preset_cyberpunk),
    OCEAN_BLUE("Ocean Blue", R.string.preset_ocean),
    SUNSET("Sunset", R.string.preset_sunset)
}

enum class NavStyle(val label: String, @StringRes val labelRes: Int) {
    PILL("Modern Pill", R.string.nav_style_pill),
    MINIMAL("Minimal Flat", R.string.nav_style_minimal),
    FLOATING("Floating Dock", R.string.nav_style_floating)
}

enum class IconStyle(val label: String, @StringRes val labelRes: Int) {
    ROUNDED("Rounded", R.string.icon_style_rounded),
    OUTLINED("Outlined", R.string.icon_style_outlined),
    TWO_TONE("Filled + Glow", R.string.icon_style_twotone)
}

enum class AppLanguage(val tag: String, val label: String) {
    ENGLISH("en", "English"),
    BENGALI("bn", "বাংলা"),
    ARABIC("ar", "العربية"),
    HINDI("hi", "हिन्दी")
}

enum class AspectRatioMode(val label: String, @StringRes val labelRes: Int? = null) {
    FIT("Fit", R.string.aspect_fit),
    FILL("Fill", R.string.aspect_fill),
    ZOOM("Zoom", R.string.aspect_zoom),
    RATIO_16_9("16:9", null),
    RATIO_4_3("4:3", null)
}

enum class DecoderMode(val label: String, @StringRes val labelRes: Int) {
    AUTO("Auto (Hardware Preferred)", R.string.decoder_auto),
    PREFER_EXTENSION("Software / Extension Fallback", R.string.decoder_extension)
}

enum class NotificationCategory {
    DOWNLOAD,
    PLAYBACK,
    SECURITY,
    STORAGE,
    UPDATE
}

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class MediaFormatOption(
    val id: String,
    val label: String,
    val downloadUrl: String,
    val mimeType: String,
    val quality: String,
    val estimatedBytes: Long,
    val supportsRange: Boolean
)

data class AnalyzedMedia(
    val sourceUrl: String,
    val title: String,
    val platform: String,
    val thumbnailUrl: String?,
    val durationMs: Long?,
    val totalBytes: Long,
    val mimeType: String,
    val mediaCategory: MediaCategory,
    val supportsRange: Boolean,
    val options: List<MediaFormatOption>,
    val analysisNotes: String? = null
)

data class DeviceMediaItem(
    val id: String,
    val uriString: String,
    val title: String,
    val fileName: String,
    val folderName: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val isVideo: Boolean = true,
    val isDownloadedByApp: Boolean = false,
    val dateAddedSec: Long = 0L,
    val lastPositionMs: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val albumArtUri: String? = null
)

data class BrowserTab(
    val id: String,
    val title: String,
    val url: String,
    val isLoading: Boolean = false,
    val progress: Int = 0
)

data class DetectedWebMedia(
    val url: String,
    val mimeType: String,
    val title: String,
    val category: MediaCategory
)

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isHidden: Boolean,
    val category: MediaCategory,
    val childCount: Int = 0,
    val contentUri: String? = null
)

data class StorageSummary(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val appMediaBytes: Long,
    val downloadsBytes: Long,
    val cacheBytes: Long,
    val vaultBytes: Long,
    val largeFiles: List<FileEntry>,
    val duplicateGroups: List<List<FileEntry>>
)

data class IntegrityReport(
    val packageName: String,
    val expectedPackageName: String,
    val packageValid: Boolean,
    val signingCertSha256: String,
    val isDebuggableBuild: Boolean,
    val isDebuggerAttached: Boolean,
    val suspiciousEnvironmentFlags: List<String>,
    val keystoreAvailable: Boolean,
    val checkedAtMillis: Long
)

data class AudioEqState(
    val sessionId: Int = 0,
    val isSupported: Boolean = false,
    val enabled: Boolean = false,
    val bandLevels: List<Short> = emptyList(),
    val bandCenterFreqsHz: List<Int> = emptyList(),
    val minBandLevel: Short = -1500,
    val maxBandLevel: Short = 1500,
    val presets: List<String> = emptyList(),
    val currentPresetIndex: Int = -1,
    val bassBoostSupported: Boolean = false,
    val bassBoostStrength: Short = 0,
    val virtualizerSupported: Boolean = false,
    val virtualizerStrength: Short = 0
)

@Serializable
data class ThemeConfig(
    val preset: String = ThemePreset.DARK.name,
    val followSystem: Boolean = false,
    val customAccentHex: Long? = null,
    val customBackgroundHex: Long? = null,
    val customCardHex: Long? = null,
    val customIconHex: Long? = null,
    val enableGradients: Boolean = true,
    val glowIntensity: Float = 0.55f,
    val borderRadiusDp: Float = 20f,
    val shadowIntensity: Float = 0.45f,
    val fontScale: Float = 1.0f,
    val animationSpeed: Float = 1.0f,
    val reduceMotion: Boolean = false,
    val navStyle: String = NavStyle.PILL.name,
    val iconStyle: String = IconStyle.ROUNDED.name
)
