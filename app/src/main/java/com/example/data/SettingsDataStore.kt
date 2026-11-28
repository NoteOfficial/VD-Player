package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppSettingsState
import com.example.model.BuildProfile
import com.example.model.PreviewViewport
import com.example.ui.theme.StudioThemeId
import com.example.ui.theme.ThemeBrightnessMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "tk_studio_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val BRIGHTNESS_MODE = stringPreferencesKey("brightness_mode")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val LOW_PERF = booleanPreferencesKey("low_perf_mode")
        val ANIM_SPEED = floatPreferencesKey("anim_speed_multiplier")
        val STARTUP_ROUTE = stringPreferencesKey("startup_route")
        val OPEN_LAST_PROJECT = booleanPreferencesKey("open_last_project_startup")
        val CONFIRM_ON_EXIT = booleanPreferencesKey("confirm_on_exit")
        val RECENT_PROJECTS_LIMIT = intPreferencesKey("recent_projects_limit")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val EDITOR_TAB_SIZE = intPreferencesKey("editor_tab_size")
        val EDITOR_WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
        val EDITOR_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
        val EDITOR_SYNTAX_HIGHLIGHT = booleanPreferencesKey("editor_syntax_highlight")
        val EDITOR_AUTO_INDENT = booleanPreferencesKey("editor_auto_indent")
        val EDITOR_HIGHLIGHT_LINE = booleanPreferencesKey("editor_highlight_line")
        val EDITOR_LARGE_FILE = booleanPreferencesKey("editor_large_file_mode")
        val EDITOR_AUTOSAVE = booleanPreferencesKey("editor_autosave")
        val EDITOR_AUTOSAVE_SEC = intPreferencesKey("editor_autosave_sec")
        val PREVIEW_AUTO_REFRESH = booleanPreferencesKey("preview_auto_refresh")
        val PREVIEW_ENABLE_JS = booleanPreferencesKey("preview_enable_js")
        val PREVIEW_SHOW_CONSOLE = booleanPreferencesKey("preview_show_console")
        val DEFAULT_SPLIT_RATIO = floatPreferencesKey("default_split_ratio")
        val DEFAULT_VIEWPORT = stringPreferencesKey("default_viewport")
        val DEFAULT_BUILD_PROFILE = stringPreferencesKey("default_build_profile")
        val MAX_BUILD_WORKERS = intPreferencesKey("max_build_workers")
        val BUILD_ON_SAVE = booleanPreferencesKey("build_on_save")
        val CLEAN_CACHE_BEFORE_BUILD = booleanPreferencesKey("clean_cache_before_build")
        val OUTPUT_NAME_PREFIX = stringPreferencesKey("output_name_prefix")
        val STRICT_SECURITY_SCAN = booleanPreferencesKey("strict_security_scan")
        val ZIP_SLIP_GUARD = booleanPreferencesKey("zip_slip_guard")
        val WEBVIEW_SAFE_MODE = booleanPreferencesKey("webview_safe_mode")
        val BACKUP_ENCRYPT = booleanPreferencesKey("backup_encrypt")
        val NOTIFY_BUILD = booleanPreferencesKey("notify_build")
        val NOTIFY_SECURITY = booleanPreferencesKey("notify_security")
        val NOTIFY_TOOLCHAIN = booleanPreferencesKey("notify_toolchain")
        val NOTIFY_STORAGE = booleanPreferencesKey("notify_storage")
        val NOTIFY_BACKUP = booleanPreferencesKey("notify_backup")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val LARGE_TARGETS = booleanPreferencesKey("large_targets")
        val LANGUAGE = stringPreferencesKey("language_code")
        val DEV_DIAGNOSTICS = booleanPreferencesKey("dev_diagnostics")
        val GITHUB_OWNER = stringPreferencesKey("github_owner")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val GITHUB_BRANCH = stringPreferencesKey("github_branch")
        val GITHUB_CLIENT_ID = stringPreferencesKey("github_client_id")
        val HAS_GITHUB_TOKEN = booleanPreferencesKey("has_github_token")
        val LEGACY_GITHUB_TOKEN = stringPreferencesKey("github_token")
        val GIT_AUTHOR_NAME = stringPreferencesKey("git_author_name")
        val GIT_AUTHOR_EMAIL = stringPreferencesKey("git_author_email")
        val CONFIRM_GIT_DESTRUCTIVE = booleanPreferencesKey("confirm_git_destructive")
        val GIT_PULL_STRATEGY = stringPreferencesKey("git_pull_strategy")
        val APK_CONFIRM_INSTALL = booleanPreferencesKey("apk_confirm_install")
        val AUTO_BUILD_ON_PUSH = booleanPreferencesKey("auto_build_on_push")
        val PIN_LOCK_ENABLED = booleanPreferencesKey("pin_lock_enabled")
        val AUTO_LOCK_SECONDS = intPreferencesKey("auto_lock_seconds")
        val BOOKMARKED_TUTORIALS = stringSetPreferencesKey("bookmarked_tutorials")
        val COMPLETED_STEPS = stringSetPreferencesKey("completed_tutorial_steps")
        val GLOW_INTENSITY = floatPreferencesKey("glow_intensity")
        val GLASS_OPACITY = floatPreferencesKey("glass_opacity")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
    }

    private fun decodePrefs(prefs: Preferences): AppSettingsState = AppSettingsState(
        themeId = prefs[Keys.THEME_ID]?.let { runCatching { StudioThemeId.valueOf(it) }.getOrNull() }
            ?: StudioThemeId.CYBER_BLUE,
        brightnessMode = prefs[Keys.BRIGHTNESS_MODE]?.let { runCatching { ThemeBrightnessMode.valueOf(it) }.getOrNull() }
            ?: ThemeBrightnessMode.DARK,
        reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
        lowPerformanceMode = prefs[Keys.LOW_PERF] ?: false,
        animSpeedMultiplier = (prefs[Keys.ANIM_SPEED] ?: 1.0f).coerceIn(0.5f, 1.5f),
        startupRoute = prefs[Keys.STARTUP_ROUTE] ?: "home",
        openLastProjectOnStartup = prefs[Keys.OPEN_LAST_PROJECT] ?: true,
        confirmOnExit = prefs[Keys.CONFIRM_ON_EXIT] ?: true,
        recentProjectsLimit = (prefs[Keys.RECENT_PROJECTS_LIMIT] ?: 5).coerceIn(3, 15),
        editorFontSizeSp = (prefs[Keys.EDITOR_FONT_SIZE] ?: 13).coerceIn(10, 22),
        editorTabSize = (prefs[Keys.EDITOR_TAB_SIZE] ?: 2).coerceIn(2, 8),
        editorWordWrap = prefs[Keys.EDITOR_WORD_WRAP] ?: false,
        editorLineNumbers = prefs[Keys.EDITOR_LINE_NUMBERS] ?: true,
        editorSyntaxHighlight = prefs[Keys.EDITOR_SYNTAX_HIGHLIGHT] ?: true,
        editorAutoIndent = prefs[Keys.EDITOR_AUTO_INDENT] ?: true,
        editorHighlightCurrentLine = prefs[Keys.EDITOR_HIGHLIGHT_LINE] ?: true,
        editorLargeFileMode = prefs[Keys.EDITOR_LARGE_FILE] ?: false,
        editorAutosave = prefs[Keys.EDITOR_AUTOSAVE] ?: true,
        editorAutosaveIntervalSec = prefs[Keys.EDITOR_AUTOSAVE_SEC] ?: 0,
        previewAutoRefresh = prefs[Keys.PREVIEW_AUTO_REFRESH] ?: true,
        previewEnableJs = prefs[Keys.PREVIEW_ENABLE_JS] ?: true,
        previewShowConsoleByDefault = prefs[Keys.PREVIEW_SHOW_CONSOLE] ?: false,
        defaultSplitRatio = (prefs[Keys.DEFAULT_SPLIT_RATIO] ?: 0.5f).coerceIn(0.25f, 0.75f),
        defaultPreviewViewport = prefs[Keys.DEFAULT_VIEWPORT]?.let { runCatching { PreviewViewport.valueOf(it) }.getOrNull() }
            ?: PreviewViewport.MOBILE,
        defaultBuildProfile = prefs[Keys.DEFAULT_BUILD_PROFILE]?.let { runCatching { BuildProfile.valueOf(it) }.getOrNull() }
            ?: BuildProfile.AUTO,
        maxBuildWorkers = (prefs[Keys.MAX_BUILD_WORKERS] ?: 2).coerceIn(1, 4),
        buildOnSave = prefs[Keys.BUILD_ON_SAVE] ?: false,
        cleanCacheBeforeBuild = prefs[Keys.CLEAN_CACHE_BEFORE_BUILD] ?: false,
        outputNamePrefix = prefs[Keys.OUTPUT_NAME_PREFIX] ?: "tk_build",
        strictSecurityScan = prefs[Keys.STRICT_SECURITY_SCAN] ?: true,
        zipSlipGuardEnabled = prefs[Keys.ZIP_SLIP_GUARD] ?: true,
        webViewSafeMode = prefs[Keys.WEBVIEW_SAFE_MODE] ?: true,
        backupEncryptByDefault = prefs[Keys.BACKUP_ENCRYPT] ?: false,
        notifyBuildComplete = prefs[Keys.NOTIFY_BUILD] ?: true,
        notifySecurityAlerts = prefs[Keys.NOTIFY_SECURITY] ?: true,
        notifyToolchainAlerts = prefs[Keys.NOTIFY_TOOLCHAIN] ?: true,
        notifyStorageAlerts = prefs[Keys.NOTIFY_STORAGE] ?: true,
        notifyBackupAlerts = prefs[Keys.NOTIFY_BACKUP] ?: true,
        highContrastBorders = prefs[Keys.HIGH_CONTRAST] ?: false,
        largeTouchTargets = prefs[Keys.LARGE_TARGETS] ?: true,
        languageCode = prefs[Keys.LANGUAGE] ?: "en",
        developerDiagnostics = prefs[Keys.DEV_DIAGNOSTICS] ?: false,
        githubOwner = prefs[Keys.GITHUB_OWNER] ?: "",
        githubRepo = prefs[Keys.GITHUB_REPO] ?: "",
        githubBranch = prefs[Keys.GITHUB_BRANCH] ?: "main",
        githubClientId = prefs[Keys.GITHUB_CLIENT_ID] ?: "",
        hasGitHubToken = prefs[Keys.HAS_GITHUB_TOKEN] ?: false,
        gitAuthorName = prefs[Keys.GIT_AUTHOR_NAME] ?: "TK OFFICIAL",
        gitAuthorEmail = prefs[Keys.GIT_AUTHOR_EMAIL] ?: "tkofficial68@gmail.com",
        confirmGitDestructive = prefs[Keys.CONFIRM_GIT_DESTRUCTIVE] ?: true,
        gitPullStrategy = prefs[Keys.GIT_PULL_STRATEGY] ?: "merge",
        apkConfirmInstall = prefs[Keys.APK_CONFIRM_INSTALL] ?: true,
        autoBuildOnPush = prefs[Keys.AUTO_BUILD_ON_PUSH] ?: false,
        pinLockEnabled = prefs[Keys.PIN_LOCK_ENABLED] ?: false,
        autoLockSeconds = prefs[Keys.AUTO_LOCK_SECONDS] ?: 300,
        bookmarkedTutorials = prefs[Keys.BOOKMARKED_TUTORIALS] ?: emptySet(),
        completedTutorialSteps = prefs[Keys.COMPLETED_STEPS] ?: emptySet(),
        glowIntensity = (prefs[Keys.GLOW_INTENSITY] ?: 1.0f).coerceIn(0.4f, 1.5f),
        glassOpacity = (prefs[Keys.GLASS_OPACITY] ?: 0.88f).coerceIn(0.55f, 1.0f),
        compactMode = prefs[Keys.COMPACT_MODE] ?: false,
        textScale = (prefs[Keys.TEXT_SCALE] ?: 1.0f).coerceIn(0.85f, 1.25f)
    )

    val settingsFlow: Flow<AppSettingsState> = context.studioDataStore.data.map(::decodePrefs)

    /**
     * Migrates any legacy plaintext token from DataStore into the encrypted SecureCredentialStore and removes it.
     */
    suspend fun migrateLegacyTokenIfNeeded(credentialStore: SecureCredentialStore) {
        val prefs = context.studioDataStore.data.first()
        val legacy = prefs[Keys.LEGACY_GITHUB_TOKEN].orEmpty()
        if (legacy.isNotBlank()) {
            credentialStore.saveGitHubToken(legacy)
        }
        val encryptedExists = credentialStore.hasGitHubToken()
        context.studioDataStore.edit { mutable ->
            mutable.remove(Keys.LEGACY_GITHUB_TOKEN)
            mutable[Keys.HAS_GITHUB_TOKEN] = encryptedExists
        }
    }

    suspend fun update(transform: (AppSettingsState) -> AppSettingsState) {
        context.studioDataStore.edit { prefs ->
            val current = decodePrefs(prefs)
            val next = transform(current)
            prefs.remove(Keys.LEGACY_GITHUB_TOKEN)
            prefs[Keys.THEME_ID] = next.themeId.name
            prefs[Keys.BRIGHTNESS_MODE] = next.brightnessMode.name
            prefs[Keys.REDUCE_MOTION] = next.reduceMotion
            prefs[Keys.LOW_PERF] = next.lowPerformanceMode
            prefs[Keys.ANIM_SPEED] = next.animSpeedMultiplier.coerceIn(0.5f, 1.5f)
            prefs[Keys.STARTUP_ROUTE] = next.startupRoute
            prefs[Keys.OPEN_LAST_PROJECT] = next.openLastProjectOnStartup
            prefs[Keys.CONFIRM_ON_EXIT] = next.confirmOnExit
            prefs[Keys.RECENT_PROJECTS_LIMIT] = next.recentProjectsLimit.coerceIn(3, 15)
            prefs[Keys.EDITOR_FONT_SIZE] = next.editorFontSizeSp.coerceIn(10, 22)
            prefs[Keys.EDITOR_TAB_SIZE] = next.editorTabSize.coerceIn(2, 8)
            prefs[Keys.EDITOR_WORD_WRAP] = next.editorWordWrap
            prefs[Keys.EDITOR_LINE_NUMBERS] = next.editorLineNumbers
            prefs[Keys.EDITOR_SYNTAX_HIGHLIGHT] = next.editorSyntaxHighlight
            prefs[Keys.EDITOR_AUTO_INDENT] = next.editorAutoIndent
            prefs[Keys.EDITOR_HIGHLIGHT_LINE] = next.editorHighlightCurrentLine
            prefs[Keys.EDITOR_LARGE_FILE] = next.editorLargeFileMode
            prefs[Keys.EDITOR_AUTOSAVE] = next.editorAutosave
            prefs[Keys.EDITOR_AUTOSAVE_SEC] = next.editorAutosaveIntervalSec
            prefs[Keys.PREVIEW_AUTO_REFRESH] = next.previewAutoRefresh
            prefs[Keys.PREVIEW_ENABLE_JS] = next.previewEnableJs
            prefs[Keys.PREVIEW_SHOW_CONSOLE] = next.previewShowConsoleByDefault
            prefs[Keys.DEFAULT_SPLIT_RATIO] = next.defaultSplitRatio.coerceIn(0.25f, 0.75f)
            prefs[Keys.DEFAULT_VIEWPORT] = next.defaultPreviewViewport.name
            prefs[Keys.DEFAULT_BUILD_PROFILE] = next.defaultBuildProfile.name
            prefs[Keys.MAX_BUILD_WORKERS] = next.maxBuildWorkers.coerceIn(1, 4)
            prefs[Keys.BUILD_ON_SAVE] = next.buildOnSave
            prefs[Keys.CLEAN_CACHE_BEFORE_BUILD] = next.cleanCacheBeforeBuild
            prefs[Keys.OUTPUT_NAME_PREFIX] = next.outputNamePrefix.ifBlank { "tk_build" }
            prefs[Keys.STRICT_SECURITY_SCAN] = next.strictSecurityScan
            prefs[Keys.ZIP_SLIP_GUARD] = next.zipSlipGuardEnabled
            prefs[Keys.WEBVIEW_SAFE_MODE] = next.webViewSafeMode
            prefs[Keys.BACKUP_ENCRYPT] = next.backupEncryptByDefault
            prefs[Keys.NOTIFY_BUILD] = next.notifyBuildComplete
            prefs[Keys.NOTIFY_SECURITY] = next.notifySecurityAlerts
            prefs[Keys.NOTIFY_TOOLCHAIN] = next.notifyToolchainAlerts
            prefs[Keys.NOTIFY_STORAGE] = next.notifyStorageAlerts
            prefs[Keys.NOTIFY_BACKUP] = next.notifyBackupAlerts
            prefs[Keys.HIGH_CONTRAST] = next.highContrastBorders
            prefs[Keys.LARGE_TARGETS] = next.largeTouchTargets
            prefs[Keys.LANGUAGE] = next.languageCode
            prefs[Keys.DEV_DIAGNOSTICS] = next.developerDiagnostics
            prefs[Keys.GITHUB_OWNER] = next.githubOwner
            prefs[Keys.GITHUB_REPO] = next.githubRepo
            prefs[Keys.GITHUB_BRANCH] = next.githubBranch
            prefs[Keys.GITHUB_CLIENT_ID] = next.githubClientId
            prefs[Keys.HAS_GITHUB_TOKEN] = next.hasGitHubToken
            prefs[Keys.GIT_AUTHOR_NAME] = next.gitAuthorName
            prefs[Keys.GIT_AUTHOR_EMAIL] = next.gitAuthorEmail
            prefs[Keys.CONFIRM_GIT_DESTRUCTIVE] = next.confirmGitDestructive
            prefs[Keys.GIT_PULL_STRATEGY] = next.gitPullStrategy
            prefs[Keys.APK_CONFIRM_INSTALL] = next.apkConfirmInstall
            prefs[Keys.AUTO_BUILD_ON_PUSH] = next.autoBuildOnPush
            prefs[Keys.PIN_LOCK_ENABLED] = next.pinLockEnabled
            prefs[Keys.AUTO_LOCK_SECONDS] = next.autoLockSeconds
            prefs[Keys.BOOKMARKED_TUTORIALS] = next.bookmarkedTutorials
            prefs[Keys.COMPLETED_STEPS] = next.completedTutorialSteps
            prefs[Keys.GLOW_INTENSITY] = next.glowIntensity.coerceIn(0.4f, 1.5f)
            prefs[Keys.GLASS_OPACITY] = next.glassOpacity.coerceIn(0.55f, 1.0f)
            prefs[Keys.COMPACT_MODE] = next.compactMode
            prefs[Keys.TEXT_SCALE] = next.textScale.coerceIn(0.85f, 1.25f)
        }
    }

    suspend fun resetAllToDefaultsPreserveAuth() {
        update { cur ->
            AppSettingsState(
                githubOwner = cur.githubOwner,
                githubRepo = cur.githubRepo,
                githubBranch = cur.githubBranch,
                githubClientId = cur.githubClientId,
                hasGitHubToken = cur.hasGitHubToken,
                pinLockEnabled = cur.pinLockEnabled,
                autoLockSeconds = cur.autoLockSeconds,
                bookmarkedTutorials = cur.bookmarkedTutorials,
                completedTutorialSteps = cur.completedTutorialSteps
            )
        }
    }

    companion object {
        private fun escapeJson(value: String): String = buildString(value.length + 4) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }

        fun exportSanitizedSettingsJson(s: AppSettingsState): String {
            val entries = listOf(
                "exportSchemaVersion" to "1",
                "appVersion" to "\"0.6.8\"",
                "excludesSecretsAndSigningKeys" to "true",
                "themeId" to "\"${escapeJson(s.themeId.name)}\"",
                "brightnessMode" to "\"${escapeJson(s.brightnessMode.name)}\"",
                "reduceMotion" to s.reduceMotion.toString(),
                "lowPerformanceMode" to s.lowPerformanceMode.toString(),
                "animSpeedMultiplier" to s.animSpeedMultiplier.toDouble().toString(),
                "startupRoute" to "\"${escapeJson(s.startupRoute)}\"",
                "openLastProjectOnStartup" to s.openLastProjectOnStartup.toString(),
                "confirmOnExit" to s.confirmOnExit.toString(),
                "recentProjectsLimit" to s.recentProjectsLimit.toString(),
                "editorFontSizeSp" to s.editorFontSizeSp.toString(),
                "editorTabSize" to s.editorTabSize.toString(),
                "editorWordWrap" to s.editorWordWrap.toString(),
                "editorLineNumbers" to s.editorLineNumbers.toString(),
                "editorSyntaxHighlight" to s.editorSyntaxHighlight.toString(),
                "editorAutoIndent" to s.editorAutoIndent.toString(),
                "editorHighlightCurrentLine" to s.editorHighlightCurrentLine.toString(),
                "editorLargeFileMode" to s.editorLargeFileMode.toString(),
                "editorAutosave" to s.editorAutosave.toString(),
                "editorAutosaveIntervalSec" to s.editorAutosaveIntervalSec.toString(),
                "previewAutoRefresh" to s.previewAutoRefresh.toString(),
                "previewEnableJs" to s.previewEnableJs.toString(),
                "previewShowConsoleByDefault" to s.previewShowConsoleByDefault.toString(),
                "defaultSplitRatio" to s.defaultSplitRatio.toDouble().toString(),
                "defaultPreviewViewport" to "\"${escapeJson(s.defaultPreviewViewport.name)}\"",
                "defaultBuildProfile" to "\"${escapeJson(s.defaultBuildProfile.name)}\"",
                "maxBuildWorkers" to s.maxBuildWorkers.toString(),
                "buildOnSave" to s.buildOnSave.toString(),
                "cleanCacheBeforeBuild" to s.cleanCacheBeforeBuild.toString(),
                "outputNamePrefix" to "\"${escapeJson(s.outputNamePrefix)}\"",
                "strictSecurityScan" to s.strictSecurityScan.toString(),
                "zipSlipGuardEnabled" to s.zipSlipGuardEnabled.toString(),
                "webViewSafeMode" to s.webViewSafeMode.toString(),
                "backupEncryptByDefault" to s.backupEncryptByDefault.toString(),
                "notifyBuildComplete" to s.notifyBuildComplete.toString(),
                "notifySecurityAlerts" to s.notifySecurityAlerts.toString(),
                "notifyToolchainAlerts" to s.notifyToolchainAlerts.toString(),
                "notifyStorageAlerts" to s.notifyStorageAlerts.toString(),
                "notifyBackupAlerts" to s.notifyBackupAlerts.toString(),
                "highContrastBorders" to s.highContrastBorders.toString(),
                "largeTouchTargets" to s.largeTouchTargets.toString(),
                "languageCode" to "\"${escapeJson(s.languageCode)}\"",
                "developerDiagnostics" to s.developerDiagnostics.toString(),
                "gitAuthorName" to "\"${escapeJson(s.gitAuthorName)}\"",
                "confirmGitDestructive" to s.confirmGitDestructive.toString(),
                "gitPullStrategy" to "\"${escapeJson(s.gitPullStrategy)}\"",
                "apkConfirmInstall" to s.apkConfirmInstall.toString(),
                "glowIntensity" to s.glowIntensity.toDouble().toString(),
                "glassOpacity" to s.glassOpacity.toDouble().toString(),
                "compactMode" to s.compactMode.toString(),
                "textScale" to s.textScale.toDouble().toString()
            )
            return entries.joinToString(
                separator = ",\n",
                prefix = "{\n",
                postfix = "\n}"
            ) { (k, v) -> "  \"$k\": $v" }
        }
    }
}
