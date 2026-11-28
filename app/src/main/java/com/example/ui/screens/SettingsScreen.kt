package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.LocalizationEngine
import com.example.data.StudioLanguage
import com.example.data.TechnicalLtrText
import com.example.model.AppSettingsState
import com.example.model.BuildProfile
import com.example.model.PreviewViewport
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.SoftSlider
import com.example.ui.components.SoftSwitch
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioBrandMark
import com.example.ui.components.StudioDestructiveButton
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSearchBar
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.formatBytes
import com.example.ui.theme.StudioTheme
import com.example.ui.theme.StudioThemeId
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.studioPaletteFor
import com.example.viewmodel.StudioUiState

private data class SettingsCategorySpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
fun SettingsScreen(
    initialCategory: String?,
    settings: AppSettingsState,
    state: StudioUiState,
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit,
    onSetTheme: (StudioThemeId) -> Unit,
    onSetBrightness: (ThemeBrightnessMode) -> Unit,
    onOpenToolchain: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSigning: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenGitCi: () -> Unit,
    onOpenTestQa: () -> Unit = {},
    onOpenUtilities: () -> Unit = {},
    onOpenApkTool: () -> Unit = {},
    onOpenTutorials: () -> Unit = {},
    onCleanSafeCaches: () -> Unit = {},
    onResetAllSettings: () -> Unit = {},
    onExportSettingsJson: () -> Unit = {},
    onSaveGitHubConfig: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onDisconnectGitHub: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val str = remember(settings.languageCode) { LocalizationEngine.stringsFor(settings.languageCode) }
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember(str) {
        listOf(
            SettingsCategorySpec("General", "General", "Startup route, recent limit & exit prompt", Icons.Rounded.Settings),
            SettingsCategorySpec("Appearance/Themes", "Appearance/Themes", "7 Studio themes, Light/Dark/System & opacity", Icons.Rounded.Palette),
            SettingsCategorySpec("Animation/Motion", "Animation/Motion", "Reduce Motion, Low Perf & speed multiplier", Icons.Rounded.Animation),
            SettingsCategorySpec("Editor", "Editor", "Font size, syntax highlight, indent & autosave", Icons.Rounded.Code),
            SettingsCategorySpec("Live Coding/Preview", "Live Coding/Preview", "JS toggle, console, split ratio & viewport", Icons.Rounded.PlayCircle),
            SettingsCategorySpec("Build/Compiler", "Build/Compiler", "Build profile, workers & output naming", Icons.Rounded.Build),
            SettingsCategorySpec("Toolchain/SDK", str.toolchain, "On-device tools, ABI targets & self-test", Icons.Rounded.Construction),
            SettingsCategorySpec("Git/GitHub", str.gitAndCi, "Identity, pull strategy, OAuth & PAT", Icons.Rounded.AccountTree),
            SettingsCategorySpec("CI/CD", "CI/CD", "Workflow dispatch, safety & remote run monitoring", Icons.Rounded.CloudUpload),
            SettingsCategorySpec("APK/Signing", "APK / ${str.signingKeys}", "Keystore defaults, install confirm & inspector", Icons.Rounded.Key),
            SettingsCategorySpec("Permissions", "Permissions", "Declared Android permissions & SAF compliance", Icons.Rounded.VerifiedUser),
            SettingsCategorySpec("Privacy/Security", str.security, "Secret scan, ZIP-slip guard & backup encryption", Icons.Rounded.Security),
            SettingsCategorySpec("Storage", "Storage", "Workspace disk breakdown & safe cache cleanup", Icons.Rounded.Storage),
            SettingsCategorySpec("Notifications", "Notifications", "Build, toolchain, storage, backup & security alerts", Icons.Rounded.Notifications),
            SettingsCategorySpec("Accessibility", "Accessibility", "High-contrast borders, text scale & 48dp targets", Icons.Rounded.AccessibilityNew),
            SettingsCategorySpec("Language", "Language", "5 languages (EN/BN/AR/ZH/UR) & RTL mirroring", Icons.Rounded.Language),
            SettingsCategorySpec("Advanced", "Advanced", "Diagnostics, settings JSON export, reset & QA", Icons.Rounded.Tune),
            SettingsCategorySpec("About", str.aboutLabel, "TK Studio v0.6.8, TK OFFICIAL & open-source notices", Icons.Rounded.Info)
        )
    }

    BackHandler {
        if (selectedCategory != null) selectedCategory = null else onBack()
    }

    if (selectedCategory != null) {
        SettingsSubpage(
            categoryId = selectedCategory!!,
            categoryTitle = categories.firstOrNull { it.id == selectedCategory }?.title ?: selectedCategory!!,
            settings = settings,
            state = state,
            onUpdateSettings = onUpdateSettings,
            onSetTheme = onSetTheme,
            onSetBrightness = onSetBrightness,
            onOpenToolchain = onOpenToolchain,
            onOpenSecurity = onOpenSecurity,
            onOpenSigning = onOpenSigning,
            onOpenBackup = onOpenBackup,
            onOpenGitCi = onOpenGitCi,
            onOpenTestQa = onOpenTestQa,
            onOpenUtilities = onOpenUtilities,
            onOpenApkTool = onOpenApkTool,
            onOpenTutorials = onOpenTutorials,
            onCleanSafeCaches = onCleanSafeCaches,
            onResetAllSettings = onResetAllSettings,
            onExportSettingsJson = onExportSettingsJson,
            onSaveGitHubConfig = onSaveGitHubConfig,
            onDisconnectGitHub = onDisconnectGitHub,
            onBack = { selectedCategory = null },
            modifier = modifier
        )
        return
    }

    val filtered = remember(searchQuery, categories) {
        if (searchQuery.isBlank()) categories
        else categories.filter {
            it.id.contains(searchQuery, ignoreCase = true) ||
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = str.backLabel,
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                )
                Column {
                    Text(str.settings, style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("${settings.themeId.displayName} • ${settings.brightnessMode.label} • ${StudioLanguage.fromCode(settings.languageCode).displayName}", style = MaterialTheme.typography.bodySmall, color = p.cyan)
                }
            }
        }

        item {
            StudioSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = str.searchPlaceholder
            )
        }

        items(filtered, key = { it.id }) { cat ->
            GlassCard(
                onClick = { selectedCategory = cat.id },
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier.testTag("settings_cat_${cat.id.lowercase().replace("/", "_").replace(" ", "_")}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(cat.icon, contentDescription = null, tint = p.cyan, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cat.title, style = MaterialTheme.typography.titleSmall, color = p.text)
                        Text(cat.subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "${str.openLabel} ${cat.title}", tint = p.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SettingsSubpage(
    categoryId: String,
    categoryTitle: String,
    settings: AppSettingsState,
    state: StudioUiState,
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit,
    onSetTheme: (StudioThemeId) -> Unit,
    onSetBrightness: (ThemeBrightnessMode) -> Unit,
    onOpenToolchain: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSigning: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenGitCi: () -> Unit,
    onOpenTestQa: () -> Unit,
    onOpenUtilities: () -> Unit,
    onOpenApkTool: () -> Unit,
    onOpenTutorials: () -> Unit,
    onCleanSafeCaches: () -> Unit,
    onResetAllSettings: () -> Unit,
    onExportSettingsJson: () -> Unit,
    onSaveGitHubConfig: (String, String, String, String, String) -> Unit,
    onDisconnectGitHub: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val str = remember(settings.languageCode) { LocalizationEngine.stringsFor(settings.languageCode) }
    var showResetAppearanceConfirm by remember { mutableStateOf(false) }
    var showResetAllConfirm by remember { mutableStateOf(false) }
    var showCachePreviewDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_subpage"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str.backLabel, onClick = onBack)
                Text(categoryTitle, style = MaterialTheme.typography.headlineSmall, color = p.text)
            }
        }

        when (categoryId) {
            "Appearance/Themes" -> {
                item {
                    GlassCard(glowAccent = p.primary) {
                        Text("Brightness Mode", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ThemeBrightnessMode.entries.forEach { mode ->
                                SoftChip(
                                    label = mode.label,
                                    selected = settings.brightnessMode == mode,
                                    onClick = { onSetBrightness(mode) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("brightness_${mode.name.lowercase()}")
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Glow Intensity: ${(settings.glowIntensity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = p.text)
                        SoftSlider(
                            value = settings.glowIntensity,
                            onValueChange = { v -> onUpdateSettings { it.copy(glowIntensity = v) } },
                            valueRange = 0.4f..1.5f
                        )
                        Text("Glass Surface Opacity: ${(settings.glassOpacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = p.text)
                        SoftSlider(
                            value = settings.glassOpacity,
                            onValueChange = { v -> onUpdateSettings { it.copy(glassOpacity = v) } },
                            valueRange = 0.55f..1.0f
                        )
                        Text("Text Scale: ${(settings.textScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = p.text)
                        SoftSlider(
                            value = settings.textScale,
                            onValueChange = { v -> onUpdateSettings { it.copy(textScale = v) } },
                            valueRange = 0.85f..1.25f
                        )
                        SettingSwitchRow(
                            title = "Compact Layout Mode",
                            subtitle = "Tighter card padding on small screens",
                            checked = settings.compactMode,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(compactMode = v) } }
                        )
                        Spacer(Modifier.height(8.dp))
                        StudioSecondaryButton(
                            text = "Reset Appearance Defaults",
                            icon = Icons.Rounded.RestartAlt,
                            onClick = { showResetAppearanceConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Text("Studio Themes (7 Palettes)", style = MaterialTheme.typography.titleMedium, color = p.text)
                }
                items(StudioThemeId.entries) { themeId ->
                    val previewDark = settings.brightnessMode != ThemeBrightnessMode.LIGHT
                    val previewPalette = studioPaletteFor(themeId, previewDark)
                    val selected = settings.themeId == themeId
                    GlassCard(
                        onClick = { onSetTheme(themeId) },
                        glowAccent = if (selected) previewPalette.primary else null,
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.testTag("theme_option_${themeId.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(themeId.displayName, style = MaterialTheme.typography.titleMedium, color = p.text)
                                Text(themeId.tagline, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf(previewPalette.background, previewPalette.primary, previewPalette.cyan, previewPalette.violet).forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                            .border(1.dp, p.border, CircleShape)
                                    )
                                }
                                if (selected) StatusPill("Active", p.success)
                            }
                        }
                    }
                }
            }

            "Animation/Motion" -> item {
                val motion = StudioTheme.motion
                val trans = rememberInfiniteTransition(label = "motionPreview")
                val pulseScale by trans.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = (900 / motion.animSpeedMultiplier.coerceIn(0.5f, 1.5f)).toInt(), easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )
                GlassCard {
                    SettingSwitchRow(
                        title = "Reduce Motion",
                        subtitle = "Disables continuous loops and sets transition duration to 0ms",
                        checked = settings.reduceMotion,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(reduceMotion = v) } }
                    )
                    SettingSwitchRow(
                        title = "Low Performance Mode",
                        subtitle = "Simplifies ambient glows and blurs for older GPUs",
                        checked = settings.lowPerformanceMode,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(lowPerformanceMode = v) } }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Animation Speed Multiplier", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0.5f to "0.5x Calm", 1.0f to "1.0x Normal", 1.5f to "1.5x Snappy").forEach { (mult, label) ->
                            SoftChip(
                                label = label,
                                selected = kotlin.math.abs(settings.animSpeedMultiplier - mult) < 0.05f,
                                onClick = { onUpdateSettings { it.copy(animSpeedMultiplier = mult) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.elevated)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Motion Telemetry Preview", style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text("Standard transition: ${motion.standardDurationMs} ms • Pulse: ${if (motion.pulseEnabled) "Active" else "Stopped"}", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    val s = if (motion.pulseEnabled) pulseScale else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                                .clip(CircleShape)
                                .background(p.cyan)
                        )
                    }
                }
            }

            "Editor" -> item {
                GlassCard {
                    Text("Font Size: ${settings.editorFontSizeSp} sp", style = MaterialTheme.typography.titleSmall, color = p.text)
                    SoftSlider(
                        value = settings.editorFontSizeSp.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(editorFontSizeSp = v.toInt()) } },
                        valueRange = 10f..22f
                    )
                    Text("Tab Width: ${settings.editorTabSize} spaces", style = MaterialTheme.typography.titleSmall, color = p.text)
                    SoftSlider(
                        value = settings.editorTabSize.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(editorTabSize = v.toInt()) } },
                        valueRange = 2f..8f
                    )
                    SettingSwitchRow("Syntax Highlighting", "Colorize keywords, HTML tags, strings & comments", settings.editorSyntaxHighlight) { v ->
                        onUpdateSettings { it.copy(editorSyntaxHighlight = v) }
                    }
                    SettingSwitchRow("Line Numbers", "Show gutter line numbers", settings.editorLineNumbers) { v ->
                        onUpdateSettings { it.copy(editorLineNumbers = v) }
                    }
                    SettingSwitchRow("Word Wrap", "Wrap long lines instead of horizontal scroll", settings.editorWordWrap) { v ->
                        onUpdateSettings { it.copy(editorWordWrap = v) }
                    }
                    SettingSwitchRow("Auto-Indent", "Preserve indentation on Enter inside code editor", settings.editorAutoIndent) { v ->
                        onUpdateSettings { it.copy(editorAutoIndent = v) }
                    }
                    SettingSwitchRow("Highlight Current Line", "Subtle focus accent around active editing buffer", settings.editorHighlightCurrentLine) { v ->
                        onUpdateSettings { it.copy(editorHighlightCurrentLine = v) }
                    }
                    SettingSwitchRow("Large-File Optimization", "Skip heavy regex highlighting on large bundles (>80KB)", settings.editorLargeFileMode) { v ->
                        onUpdateSettings { it.copy(editorLargeFileMode = v) }
                    }
                    SettingSwitchRow("Autosave", "Persist edits automatically to disk", settings.editorAutosave) { v ->
                        onUpdateSettings { it.copy(editorAutosave = v) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Autosave Delay", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0 to "Instant", 3 to "3 sec", 10 to "10 sec").forEach { (sec, label) ->
                            SoftChip(
                                label = label,
                                selected = settings.editorAutosaveIntervalSec == sec,
                                onClick = { onUpdateSettings { it.copy(editorAutosaveIntervalSec = sec) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            "Live Coding/Preview" -> item {
                GlassCard {
                    SettingSwitchRow("Auto-Refresh Preview", "Reload WebView when files change", settings.previewAutoRefresh) { v ->
                        onUpdateSettings { it.copy(previewAutoRefresh = v) }
                    }
                    SettingSwitchRow("Enable JavaScript in Preview", "Allow local HTML scripts inside WebView sandbox", settings.previewEnableJs) { v ->
                        onUpdateSettings { it.copy(previewEnableJs = v) }
                    }
                    SettingSwitchRow("Show Console Drawer by Default", "Automatically expand WebView console on Live Coding", settings.previewShowConsoleByDefault) { v ->
                        onUpdateSettings { it.copy(previewShowConsoleByDefault = v) }
                    }
                    SettingSwitchRow("WebView Safe Mode", "Block universal & cross-file URL access", settings.webViewSafeMode) { v ->
                        onUpdateSettings { it.copy(webViewSafeMode = v) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Default Split Ratio: ${(settings.defaultSplitRatio * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = p.text)
                    SoftSlider(
                        value = settings.defaultSplitRatio,
                        onValueChange = { v -> onUpdateSettings { it.copy(defaultSplitRatio = v) } },
                        valueRange = 0.25f..0.75f
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Default Viewport", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewViewport.entries.forEach { vp ->
                            SoftChip(
                                label = vp.label,
                                selected = settings.defaultPreviewViewport == vp,
                                onClick = { onUpdateSettings { it.copy(defaultPreviewViewport = vp) } }
                            )
                        }
                    }
                }
            }

            "Build/Compiler" -> item {
                var prefix by remember(settings.outputNamePrefix) { mutableStateOf(settings.outputNamePrefix) }
                GlassCard {
                    Text("Default Build Profile", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    BuildProfile.entries.forEach { prof ->
                        SoftChip(
                            label = "${prof.label} — ${prof.description}",
                            selected = settings.defaultBuildProfile == prof,
                            onClick = { onUpdateSettings { it.copy(defaultBuildProfile = prof) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Max Build Workers: ${settings.maxBuildWorkers}", style = MaterialTheme.typography.titleSmall, color = p.text)
                    SoftSlider(
                        value = settings.maxBuildWorkers.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(maxBuildWorkers = v.toInt()) } },
                        valueRange = 1f..4f
                    )
                    SettingSwitchRow("Build Verification on Save", "Queue incremental verification check on manual save", settings.buildOnSave) { v ->
                        onUpdateSettings { it.copy(buildOnSave = v) }
                    }
                    SettingSwitchRow("Clean Temp Cache Before Build", "Purge temporary cache before starting clean builds", settings.cleanCacheBeforeBuild) { v ->
                        onUpdateSettings { it.copy(cleanCacheBeforeBuild = v) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Default Output Filename Prefix") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    StudioSecondaryButton(
                        text = "Apply Output Prefix",
                        onClick = { onUpdateSettings { it.copy(outputNamePrefix = prefix) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "Toolchain/SDK" -> item {
                GlassCard {
                    Text("Active On-Device Tools: ${state.toolchainItems.count { it.isAvailable }}/${state.toolchainItems.size}", style = MaterialTheme.typography.titleMedium, color = p.text)
                    state.toolchainEnvReport?.let { env ->
                        Spacer(Modifier.height(4.dp))
                        Text(env.summaryNote, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        Text("ABIs: ${env.supportedAbis.joinToString(", ")} • Heap Ceiling: ${env.maxHeapMb} MB", style = MaterialTheme.typography.labelSmall, color = p.cyan)
                    }
                    Spacer(Modifier.height(10.dp))
                    StudioPrimaryButton(text = "Open Toolchain Inspector", onClick = onOpenToolchain, modifier = Modifier.fillMaxWidth())
                }
            }

            "Git/GitHub", "CI/CD" -> item {
                var owner by remember(settings.githubOwner) { mutableStateOf(settings.githubOwner) }
                var repo by remember(settings.githubRepo) { mutableStateOf(settings.githubRepo) }
                var branch by remember(settings.githubBranch) { mutableStateOf(settings.githubBranch) }
                var clientId by remember(settings.githubClientId) { mutableStateOf(settings.githubClientId) }
                var authorName by remember(settings.gitAuthorName) { mutableStateOf(settings.gitAuthorName) }
                var authorEmail by remember(settings.gitAuthorEmail) { mutableStateOf(settings.gitAuthorEmail) }
                var token by remember { mutableStateOf("") }
                GlassCard {
                    Text(
                        if (settings.hasGitHubToken) "Encrypted GitHub Token Active (AndroidKeyStore)" else "No GitHub Token Stored",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (settings.hasGitHubToken) p.success else p.warning
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "Confirm Destructive Git Operations",
                        subtitle = "Require confirmation before branch delete, reset, or overwrite merge",
                        checked = settings.confirmGitDestructive,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(confirmGitDestructive = v) } }
                    )
                    SettingSwitchRow(
                        title = "Opt-In CI Auto-Build on Push",
                        subtitle = "Include push trigger when generating new GitHub Actions templates",
                        checked = settings.autoBuildOnPush,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(autoBuildOnPush = v) } }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Pull Strategy", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("merge" to "Merge Sync", "ff-only" to "Fast-Forward Only").forEach { (key, label) ->
                            SoftChip(
                                label = label,
                                selected = settings.gitPullStrategy == key,
                                onClick = { onUpdateSettings { it.copy(gitPullStrategy = key) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = authorName, onValueChange = { authorName = it }, label = { Text("Git Author Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = authorEmail, onValueChange = { authorEmail = it }, label = { Text("Git Author Email") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("GitHub Owner / Org") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repository Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Default Branch") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = clientId, onValueChange = { clientId = it }, label = { Text("OAuth App Client ID (Device Flow)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(if (settings.hasGitHubToken) "Replace PAT (Encrypted Vault)" else "Personal Access Token (Encrypted Vault)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StudioPrimaryButton(text = str.saveLabel, onClick = {
                            onUpdateSettings {
                                it.copy(
                                    gitAuthorName = authorName.trim().ifEmpty { "TK OFFICIAL" },
                                    gitAuthorEmail = authorEmail.trim().ifEmpty { "tkofficial68@gmail.com" }
                                )
                            }
                            onSaveGitHubConfig(owner, repo, branch, token, clientId)
                            token = ""
                        }, modifier = Modifier.weight(1f))
                        StudioSecondaryButton(text = "Open Git & CI", onClick = onOpenGitCi, modifier = Modifier.weight(1f))
                    }
                    if (settings.hasGitHubToken) {
                        Spacer(Modifier.height(8.dp))
                        StudioDestructiveButton(text = "Disconnect & Wipe Token", onClick = onDisconnectGitHub, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            "APK/Signing" -> item {
                GlassCard {
                    Text("Stored Signing Keys: ${state.signingKeys.size}", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "Confirm Before System Installer Handoff",
                        subtitle = "Show package & signing continuity check before opening Android Package Installer",
                        checked = settings.apkConfirmInstall,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(apkConfirmInstall = v) } }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StudioPrimaryButton(text = str.signingKeys, onClick = onOpenSigning, modifier = Modifier.weight(1f))
                        StudioSecondaryButton(text = str.navApkTool, onClick = onOpenApkTool, modifier = Modifier.weight(1f))
                    }
                }
            }

            "Permissions" -> item {
                val context = LocalContext.current
                GlassCard {
                    Text("Declared App Permissions", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    state.declaredPermissions.forEach { perm ->
                        val rationale = when {
                            perm.contains("INTERNET") -> "Opt-in GitHub OAuth / REST API & CI artifact downloads"
                            perm.contains("ACCESS_NETWORK_STATE") -> "Accurate offline vs online diagnostics"
                            perm.contains("POST_NOTIFICATIONS") -> "Optional runtime alert for finished builds/CI/security"
                            else -> "Required Android system capability"
                        }
                        Text("• $perm", style = MaterialTheme.typography.labelMedium, color = p.cyan)
                        Text("  $rationale", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Media & files use Android Photo Picker & Storage Access Framework (zero READ_EXTERNAL_STORAGE / MANAGE_EXTERNAL_STORAGE / REQUEST_INSTALL_PACKAGES).", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    StudioSecondaryButton(
                        text = "Open Android App Settings",
                        onClick = {
                            runCatching {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "Privacy/Security" -> item {
                GlassCard {
                    Text(
                        if (settings.pinLockEnabled) "App Lock PIN: Enabled (${settings.autoLockSeconds}s)" else "App Lock PIN: Disabled",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (settings.pinLockEnabled) p.success else p.textSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow("Strict Secret Scan", "Flag API keys, tokens & private keys in builds", settings.strictSecurityScan) { v ->
                        onUpdateSettings { it.copy(strictSecurityScan = v) }
                    }
                    SettingSwitchRow("ZIP-Slip Path Guard", "Block archive traversal outside target folder", settings.zipSlipGuardEnabled) { v ->
                        onUpdateSettings { it.copy(zipSlipGuardEnabled = v) }
                    }
                    SettingSwitchRow("Encrypt Backups by Default", "Use AES-256-GCM when password provided", settings.backupEncryptByDefault) { v ->
                        onUpdateSettings { it.copy(backupEncryptByDefault = v) }
                    }
                    Spacer(Modifier.height(8.dp))
                    StudioPrimaryButton(text = "Open Security & PIN Lock Center", onClick = onOpenSecurity, modifier = Modifier.fillMaxWidth())
                }
            }

            "Storage" -> item {
                val sb = state.storageBreakdown
                GlassCard {
                    Text("Total Studio Storage: ${formatBytes(state.storageUsageBytes)}", style = MaterialTheme.typography.titleMedium, color = p.cyan)
                    Text("${state.projects.size} projects • ${state.backups.size} snapshots • ${state.buildHistory.size} build logs", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    if (sb != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Projects: ${formatBytes(sb.projectsBytes)} • Builds: ${formatBytes(sb.buildsBytes)}", style = MaterialTheme.typography.bodySmall, color = p.text)
                        Text("Backups: ${formatBytes(sb.backupsBytes)} • DB Backups: ${formatBytes(sb.dbBackupsBytes)}", style = MaterialTheme.typography.bodySmall, color = p.text)
                        Text("Cleanable Temp Cache (APK cache + extracted): ${formatBytes(sb.cleanableCacheBytes)}", style = MaterialTheme.typography.bodySmall, color = p.warning)
                    }
                    state.envHealth?.let { env ->
                        if (env.usableStorageBytes < 250L * 1024L * 1024L) {
                            Spacer(Modifier.height(6.dp))
                            StatusPill("Low Device Storage Warning (<250 MB free)", p.error)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StudioPrimaryButton(text = "Backups", onClick = onOpenBackup, modifier = Modifier.weight(1f))
                        StudioSecondaryButton(
                            text = "Preview Cache Cleanup",
                            icon = Icons.Rounded.CleaningServices,
                            onClick = { showCachePreviewDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            "Notifications" -> item {
                val context = LocalContext.current
                var notifGranted by remember {
                    mutableStateOf(
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    notifGranted = granted
                }
                GlassCard {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("System Notification Permission", style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text(if (notifGranted) "Granted for build, CI & security alerts" else "Not granted — tap Allow to enable tray alerts", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                            }
                            if (!notifGranted) {
                                StudioPrimaryButton(text = "Allow", onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                            } else {
                                StatusPill("Granted", p.success)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    SettingSwitchRow("Build & CI Completion Alerts", "Post tray notification & task feed update", settings.notifyBuildComplete) { v ->
                        onUpdateSettings { it.copy(notifyBuildComplete = v) }
                    }
                    SettingSwitchRow("Security Alerts", "Warn immediately on secret/workflow findings", settings.notifySecurityAlerts) { v ->
                        onUpdateSettings { it.copy(notifySecurityAlerts = v) }
                    }
                    SettingSwitchRow("Toolchain Alerts", "Report missing or unhealthy build runtime tools", settings.notifyToolchainAlerts) { v ->
                        onUpdateSettings { it.copy(notifyToolchainAlerts = v) }
                    }
                    SettingSwitchRow("Storage Alerts", "Warn when usable device storage drops below 250 MB", settings.notifyStorageAlerts) { v ->
                        onUpdateSettings { it.copy(notifyStorageAlerts = v) }
                    }
                    SettingSwitchRow("Backup & Recovery Alerts", "Notify after snapshot exports & pre-rollback saves", settings.notifyBackupAlerts) { v ->
                        onUpdateSettings { it.copy(notifyBackupAlerts = v) }
                    }
                }
            }

            "Accessibility" -> item {
                GlassCard {
                    StatusPill("Active Text Scale ${(settings.textScale * 100).toInt()}% • Min Target 48dp", p.cyan)
                    Spacer(Modifier.height(8.dp))
                    SettingSwitchRow("High-Contrast Borders", "Strengthen primary/glass outlines across cards & chips", settings.highContrastBorders) { v ->
                        onUpdateSettings { it.copy(highContrastBorders = v) }
                    }
                    SettingSwitchRow("Large Touch Targets", "Enforce 48dp+ interactive spacing for accessibility", settings.largeTouchTargets) { v ->
                        onUpdateSettings { it.copy(largeTouchTargets = v) }
                    }
                    SettingSwitchRow("Reduce Motion", "Eliminate continuous pulses & motion blur", settings.reduceMotion) { v ->
                        onUpdateSettings { it.copy(reduceMotion = v) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Text Scale: ${(settings.textScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = p.text)
                    SoftSlider(
                        value = settings.textScale,
                        onValueChange = { v -> onUpdateSettings { it.copy(textScale = v) } },
                        valueRange = 0.85f..1.25f
                    )
                }
            }

            "Language" -> item {
                val audit = remember { LocalizationEngine.auditCompleteness() }
                GlassCard {
                    Text("Interface Language (5 Locales • RTL Mirroring)", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "UI controls, status labels, numbers/dates, and 23 tutorial series headings adapt cleanly. Technical identifiers, code, hashes & paths always preserve LTR order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioLanguage.entries.forEach { lang ->
                            val rep = audit[lang]
                            val label = buildString {
                                append(lang.displayName)
                                if (lang.isRtl) append(" • RTL")
                                if (rep != null) append(" (${rep.coveragePercent}%)")
                            }
                            SoftChip(
                                label = label,
                                selected = settings.languageCode == lang.code,
                                onClick = { onUpdateSettings { it.copy(languageCode = lang.code) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lang_${lang.code}")
                            )
                        }
                    }
                }
            }

            "Advanced" -> item {
                GlassCard {
                    SettingSwitchRow("Developer Diagnostics", "Show verbose build and pipeline telemetry", settings.developerDiagnostics) { v ->
                        onUpdateSettings { it.copy(developerDiagnostics = v) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StudioPrimaryButton(text = str.testCenter, onClick = onOpenTestQa, modifier = Modifier.weight(1f))
                        StudioSecondaryButton(text = str.utilities, onClick = onOpenUtilities, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    StudioSecondaryButton(
                        text = "Export Sanitized Settings JSON",
                        icon = Icons.Rounded.Download,
                        onClick = onExportSettingsJson,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.lastSettingsExportPath?.let { path ->
                        Spacer(Modifier.height(6.dp))
                        TechnicalLtrText(text = "Saved: $path", style = MaterialTheme.typography.labelSmall, color = p.cyan)
                    }
                    Spacer(Modifier.height(8.dp))
                    StudioDestructiveButton(
                        text = "Reset All Settings to Defaults",
                        icon = Icons.Rounded.RestartAlt,
                        onClick = { showResetAllConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "About" -> item {
                val context = LocalContext.current
                GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StudioBrandMark(size = 48.dp)
                        Column {
                            Text("TK Studio", style = MaterialTheme.typography.titleLarge, color = p.text)
                            TechnicalLtrText(
                                text = "v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) • ${BuildConfig.APPLICATION_ID}",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.cyan
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Developer: TK OFFICIAL", style = MaterialTheme.typography.bodyMedium, color = p.text)
                    Text("Offline-first Android mobile workspace for coding, live preview, APK inspection, key vault signing, local Git, and GitHub CI.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    StudioSecondaryButton(
                        text = "tkofficial68@gmail.com",
                        icon = Icons.Rounded.Email,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:tkofficial68@gmail.com")))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    StudioSecondaryButton(
                        text = "Telegram @MR_TK_68",
                        icon = Icons.Rounded.Send,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/MR_TK_68")))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    StudioSecondaryButton(
                        text = "${str.tutorials} (266 Labs)",
                        icon = Icons.Rounded.MenuBook,
                        onClick = onOpenTutorials,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Open-Source Notices", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Text(
                        "Built with Kotlin, Jetpack Compose, Material 3, AndroidX Navigation, DataStore, Room, and standard JVM/Android crypto libraries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textSecondary
                    )
                }
            }

            else -> item {
                // General
                val startOptions = listOf(
                    "home" to str.navHome,
                    "project" to str.navProject,
                    "live_coding" to str.navLiveCoding,
                    "apk_tool" to str.navApkTool,
                    "profile" to str.navProfile
                )
                GlassCard {
                    Text("Startup Tab", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        startOptions.forEach { (route, label) ->
                            SoftChip(
                                label = label,
                                selected = settings.startupRoute == route,
                                onClick = { onUpdateSettings { it.copy(startupRoute = route) } }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitchRow("Open Last Project on Startup", "Automatically load recent workspace into Live Coding", settings.openLastProjectOnStartup) { v ->
                        onUpdateSettings { it.copy(openLastProjectOnStartup = v) }
                    }
                    SettingSwitchRow("Confirm Before Exit", "Prompt confirmation dialog when pressing Back on Home", settings.confirmOnExit) { v ->
                        onUpdateSettings { it.copy(confirmOnExit = v) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Recent Projects on Home: ${settings.recentProjectsLimit}", style = MaterialTheme.typography.titleSmall, color = p.text)
                    SoftSlider(
                        value = settings.recentProjectsLimit.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(recentProjectsLimit = v.toInt()) } },
                        valueRange = 3f..15f
                    )
                    SettingSwitchRow("Autosave in Live Coding", "Automatically save edits to workspace", settings.editorAutosave) { v ->
                        onUpdateSettings { it.copy(editorAutosave = v) }
                    }
                    SettingSwitchRow("ZIP-Slip Guard", "Validate archive paths on import", settings.zipSlipGuardEnabled) { v ->
                        onUpdateSettings { it.copy(zipSlipGuardEnabled = v) }
                    }
                    Spacer(Modifier.height(8.dp))
                    StudioDestructiveButton(
                        text = "Reset All Settings",
                        icon = Icons.Rounded.RestartAlt,
                        onClick = { showResetAllConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showResetAppearanceConfirm) {
        StudioDialog(
            title = "Reset Appearance Defaults?",
            onDismiss = { showResetAppearanceConfirm = false },
            confirmText = str.resetLabel,
            isDestructive = true,
            onConfirm = {
                showResetAppearanceConfirm = false
                onUpdateSettings {
                    it.copy(
                        themeId = StudioThemeId.CYBER_BLUE,
                        brightnessMode = ThemeBrightnessMode.DARK,
                        glowIntensity = 1.0f,
                        glassOpacity = 0.88f,
                        compactMode = false,
                        textScale = 1.0f,
                        highContrastBorders = false
                    )
                }
            }
        ) {
            Text(
                "Restores Cyber Blue dark theme, default glass opacity, and standard text scaling.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
        }
    }

    if (showResetAllConfirm) {
        StudioDialog(
            title = "Reset All Settings?",
            onDismiss = { showResetAllConfirm = false },
            confirmText = str.resetLabel,
            isDestructive = true,
            onConfirm = {
                showResetAllConfirm = false
                onResetAllSettings()
            }
        ) {
            Text(
                "Resets UI, editor, preview, build, and notification preferences to defaults. Your encrypted GitHub token, App Lock PIN, projects, and tutorial progress are preserved.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
        }
    }

    if (showCachePreviewDialog) {
        val sb = state.storageBreakdown
        StudioDialog(
            title = "Clean Temporary Cache?",
            onDismiss = { showCachePreviewDialog = false },
            confirmText = "Clean Cache",
            isDestructive = true,
            onConfirm = {
                showCachePreviewDialog = false
                onCleanSafeCaches()
            }
        ) {
            Text(
                text = if (sb != null) {
                    "APK Inspector cache: ${formatBytes(sb.apkCacheBytes)}\nExtracted inspect artifacts: ${formatBytes(sb.extractedBytes)}\nTotal cleanable: ${formatBytes(sb.cleanableCacheBytes)}\n\nWorkspace projects, build outputs, snapshots, and signing keys will NOT be touched."
                } else {
                    "Removes temporary APK inspection and extraction cache only. Projects, snapshots, and signing keys stay untouched."
                },
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = StudioTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }
        SoftSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
