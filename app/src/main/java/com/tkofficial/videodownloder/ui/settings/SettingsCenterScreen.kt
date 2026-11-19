package com.tkofficial.videodownloder.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.tkofficial.videodownloder.core.DeveloperIdentity
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.core.openAppDetailsSettings
import com.tkofficial.videodownloder.models.AppLanguage
import com.tkofficial.videodownloder.models.AspectRatioMode
import com.tkofficial.videodownloder.models.DecoderMode
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaLogoEmblem
import com.tkofficial.videodownloder.ui.components.VaStatusChip
import com.tkofficial.videodownloder.ui.profile.ProfileViewModel
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens

enum class SettingsSection(val id: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    GENERAL("general", R.string.sec_general, Icons.Rounded.Settings),
    DOWNLOADS("downloads", R.string.sec_downloads, Icons.Rounded.Download),
    NOTIFICATIONS("notifications", R.string.sec_notifications, Icons.Rounded.Notifications),
    GALLERY("gallery", R.string.sec_gallery, Icons.Rounded.PhotoLibrary),
    VIDEO_PLAYER("video_player", R.string.sec_video_player, Icons.Rounded.VideoLibrary),
    MUSIC_PLAYER("music_player", R.string.sec_music_player, Icons.Rounded.MusicNote),
    BROWSER("browser", R.string.sec_browser, Icons.Rounded.Public),
    NETWORK("network", R.string.sec_network, Icons.Rounded.Wifi),
    API("api", R.string.sec_api, Icons.Rounded.Api),
    PRIVACY("privacy", R.string.sec_privacy, Icons.Rounded.PrivacyTip),
    STORAGE("storage", R.string.sec_storage, Icons.Rounded.Storage),
    LANGUAGE("language", R.string.sec_language, Icons.Rounded.Language),
    BACKUP("backup", R.string.sec_backup, Icons.Rounded.Backup),
    ACCESSIBILITY("accessibility", R.string.sec_accessibility, Icons.Rounded.AccessibilityNew),
    MAINTENANCE("maintenance", R.string.sec_maintenance, Icons.Rounded.Build),
    ABOUT("about", R.string.sec_about, Icons.Rounded.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCenterScreen(
    initialSection: String,
    viewModel: ProfileViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenStorageAnalyzer: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenLegal: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = LocalVaTokens.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    var activeSection by remember(initialSection) {
        mutableStateOf(SettingsSection.entries.firstOrNull { it.id == initialSection })
    }

    var hasNotifPerm by remember { mutableStateOf(LocaleHelper.hasNotificationPermission(context)) }
    var hasAudioPerm by remember { mutableStateOf(LocaleHelper.hasAudioPermission(context)) }
    var hasCameraPerm by remember { mutableStateOf(LocaleHelper.hasCameraPermission(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = LocaleHelper.hasNotificationPermission(context)
                hasAudioPerm = LocaleHelper.hasAudioPermission(context)
                hasCameraPerm = LocaleHelper.hasCameraPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifPerm = granted
    }
    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPerm = granted
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPerm = granted
    }

    BackHandler(enabled = activeSection != null && initialSection == "hub") {
        activeSection = null
    }

    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(activeSection?.let { stringResource(it.titleRes) } ?: stringResource(R.string.menu_settings))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeSection != null && initialSection == "hub") {
                            activeSection = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        if (activeSection == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(SettingsSection.entries) { sec ->
                    val title = stringResource(sec.titleRes)
                    VaCard(
                        onClick = { activeSection = sec },
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.testTag("settings_sec_${sec.id}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(sec.icon, contentDescription = title, tint = tokens.iconTint)
                            Spacer(Modifier.width(14.dp))
                            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    when (activeSection!!) {
                        SettingsSection.GENERAL -> VaCard {
                            var name by remember(settings.userDisplayName) { mutableStateOf(settings.userDisplayName) }
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(stringResource(R.string.set_user_greeting)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.updateSettings { it.copy(userDisplayName = name.ifBlank { "Media Explorer" }) } }) {
                                Text(stringResource(R.string.set_update_greeting))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.set_start_tab), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val tabLabels = listOf(
                                    "home" to stringResource(R.string.nav_home),
                                    "gallery" to stringResource(R.string.nav_gallery),
                                    "player" to stringResource(R.string.nav_player),
                                    "browser" to stringResource(R.string.nav_browser)
                                )
                                tabLabels.forEach { (tab, label) ->
                                    FilterChip(
                                        selected = settings.startDestination == tab,
                                        onClick = { viewModel.updateSettings { it.copy(startDestination = tab) } },
                                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }

                        SettingsSection.DOWNLOADS -> VaCard {
                            Text(stringResource(R.string.set_max_concurrent, settings.maxConcurrentDownloads), style = MaterialTheme.typography.titleMedium)
                            Slider(
                                value = settings.maxConcurrentDownloads.toFloat(),
                                valueRange = 1f..5f,
                                steps = 3,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(maxConcurrentDownloads = v.toInt()) } }
                            )
                            SettingSwitch(stringResource(R.string.set_wifi_only), settings.wifiOnlyDownloads) { v ->
                                viewModel.updateSettings { it.copy(wifiOnlyDownloads = v) }
                            }
                            val limitLabel = if (settings.speedLimitKbps == 0) {
                                stringResource(R.string.set_unlimited)
                            } else {
                                stringResource(R.string.set_speed_kbps, settings.speedLimitKbps)
                            }
                            Text(stringResource(R.string.set_speed_limit, limitLabel), style = MaterialTheme.typography.titleMedium)
                            Slider(
                                value = settings.speedLimitKbps.toFloat(),
                                valueRange = 0f..10240f,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(speedLimitKbps = (v / 128).toInt() * 128) } }
                            )
                            SettingSwitch(stringResource(R.string.set_auto_retry), settings.autoRetryOnFailure) { v ->
                                viewModel.updateSettings { it.copy(autoRetryOnFailure = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_auto_rename), settings.autoRenameDuplicates) { v ->
                                viewModel.updateSettings { it.copy(autoRenameDuplicates = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_duplicate_check), settings.duplicateCheckEnabled) { v ->
                                viewModel.updateSettings { it.copy(duplicateCheckEnabled = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_verify_sha256), settings.verifySha256AfterDownload) { v ->
                                viewModel.updateSettings { it.copy(verifySha256AfterDownload = v) }
                            }
                        }

                        SettingsSection.NOTIFICATIONS -> VaCard {
                            SettingSwitch(stringResource(R.string.set_notify_active), settings.notifyActiveDownloads) { v ->
                                viewModel.updateSettings { it.copy(notifyActiveDownloads = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_notify_complete), settings.notifyCompletedDownloads) { v ->
                                viewModel.updateSettings { it.copy(notifyCompletedDownloads = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_notify_failed), settings.notifyFailedDownloads) { v ->
                                viewModel.updateSettings { it.copy(notifyFailedDownloads = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_notify_storage), settings.notifyStorageWarnings) { v ->
                                viewModel.updateSettings { it.copy(notifyStorageWarnings = v) }
                            }
                        }

                        SettingsSection.GALLERY -> VaCard {
                            Text(stringResource(R.string.set_grid_cols, settings.galleryGridColumns), style = MaterialTheme.typography.titleMedium)
                            Slider(
                                value = settings.galleryGridColumns.toFloat(),
                                valueRange = 2f..5f,
                                steps = 2,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(galleryGridColumns = v.toInt()) } }
                            )
                            SettingSwitch(stringResource(R.string.set_show_filenames), settings.galleryShowFileNames) { v ->
                                viewModel.updateSettings { it.copy(galleryShowFileNames = v) }
                            }
                            Text(stringResource(R.string.set_slideshow_interval, settings.gallerySlideshowSeconds), style = MaterialTheme.typography.titleMedium)
                            Slider(
                                value = settings.gallerySlideshowSeconds.toFloat(),
                                valueRange = 2f..15f,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(gallerySlideshowSeconds = v.toInt()) } }
                            )
                        }

                        SettingsSection.VIDEO_PLAYER -> VaCard {
                            Text(
                                stringResource(
                                    R.string.set_default_speed,
                                    String.format(java.util.Locale.US, "%.2f", settings.videoDefaultSpeed)
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Slider(
                                value = settings.videoDefaultSpeed,
                                valueRange = 0.5f..2.0f,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(videoDefaultSpeed = v) } }
                            )
                            Text(stringResource(R.string.set_default_aspect), style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AspectRatioMode.entries.take(3).forEach { mode ->
                                    FilterChip(
                                        selected = settings.videoDefaultAspectRatio == mode,
                                        onClick = { viewModel.updateSettings { it.copy(videoDefaultAspectRatio = mode) } },
                                        label = { Text(mode.labelRes?.let { stringResource(it) } ?: mode.label) }
                                    )
                                }
                            }
                            Text(stringResource(R.string.set_decoder_pref), style = MaterialTheme.typography.titleMedium)
                            DecoderMode.entries.forEach { dec ->
                                FilterChip(
                                    selected = settings.videoDecoderMode == dec,
                                    onClick = { viewModel.updateSettings { it.copy(videoDecoderMode = dec) } },
                                    label = { Text(stringResource(dec.labelRes)) }
                                )
                            }
                            SettingSwitch(stringResource(R.string.set_resume_playback), settings.videoResumePlayback) { v ->
                                viewModel.updateSettings { it.copy(videoResumePlayback = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_auto_next), settings.videoAutoNext) { v ->
                                viewModel.updateSettings { it.copy(videoAutoNext = v) }
                            }
                            Text(stringResource(R.string.set_subtitle_size, settings.videoSubtitleFontSp.toInt()), style = MaterialTheme.typography.titleMedium)
                            Slider(
                                value = settings.videoSubtitleFontSp,
                                valueRange = 12f..32f,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(videoSubtitleFontSp = v) } }
                            )
                        }

                        SettingsSection.MUSIC_PLAYER -> VaCard {
                            SettingSwitch(stringResource(R.string.set_gapless), settings.musicGaplessEnabled) { v ->
                                viewModel.updateSettings { it.copy(musicGaplessEnabled = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_visualizer), settings.musicVisualizerEnabled) { v ->
                                viewModel.updateSettings { it.copy(musicVisualizerEnabled = v) }
                            }
                        }

                        SettingsSection.BROWSER -> VaCard {
                            var homeUrl by remember(settings.browserHomeUrl) { mutableStateOf(settings.browserHomeUrl) }
                            OutlinedTextField(
                                value = homeUrl,
                                onValueChange = { homeUrl = it },
                                label = { Text(stringResource(R.string.set_browser_home)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = { viewModel.updateSettings { it.copy(browserHomeUrl = homeUrl.trim()) } }) {
                                Text(stringResource(R.string.set_save_home_url))
                            }
                            SettingSwitch(stringResource(R.string.set_enable_js), settings.browserJavaScriptEnabled) { v ->
                                viewModel.updateSettings { it.copy(browserJavaScriptEnabled = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_media_sniffer), settings.browserMediaSnifferEnabled) { v ->
                                viewModel.updateSettings { it.copy(browserMediaSnifferEnabled = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_save_history), settings.browserSaveHistory) { v ->
                                viewModel.updateSettings { it.copy(browserSaveHistory = v) }
                            }
                        }

                        SettingsSection.NETWORK -> VaCard {
                            SettingSwitch(stringResource(R.string.set_allow_http), settings.allowCleartextHttp) { v ->
                                viewModel.updateSettings { it.copy(allowCleartextHttp = v) }
                            }
                            Text(stringResource(R.string.set_request_timeout, settings.requestTimeoutSeconds), style = MaterialTheme.typography.titleMedium)
                            Slider(
                                value = settings.requestTimeoutSeconds.toFloat(),
                                valueRange = 10f..120f,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(requestTimeoutSeconds = v.toInt()) } }
                            )
                        }

                        SettingsSection.API -> VaCard {
                            var endpoint by remember(settings.customExtractorEndpoint) { mutableStateOf(settings.customExtractorEndpoint) }
                            var apiKey by remember { mutableStateOf("") }
                            Text(
                                stringResource(R.string.set_api_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = endpoint,
                                onValueChange = { endpoint = it },
                                label = { Text(stringResource(R.string.set_api_endpoint)) },
                                placeholder = { Text("https://your-authorized-api.example/resolve") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text(stringResource(R.string.set_api_key)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { viewModel.saveApiConfig(endpoint, apiKey) }) {
                                Text(stringResource(R.string.set_api_save))
                            }
                        }

                        SettingsSection.PRIVACY -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            VaCard {
                                SettingSwitch(stringResource(R.string.set_hide_recents), settings.hideThumbnailsInRecents) { v ->
                                    viewModel.updateSettings { it.copy(hideThumbnailsInRecents = v) }
                                }
                                Text(stringResource(R.string.set_autolock_timeout, settings.autoLockSeconds), style = MaterialTheme.typography.titleMedium)
                                Slider(
                                    value = settings.autoLockSeconds.toFloat(),
                                    valueRange = 15f..300f,
                                    onValueChange = { v -> viewModel.updateSettings { it.copy(autoLockSeconds = v.toInt()) } }
                                )
                            }

                            VaCard(highlighted = true) {
                                Text(stringResource(R.string.perm_section_title), style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.perm_section_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    PermissionStatusRow(
                                        name = stringResource(R.string.perm_notif_name),
                                        granted = hasNotifPerm,
                                        onRequest = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        onOpenSettings = { context.openAppDetailsSettings() }
                                    )
                                }
                                PermissionStatusRow(
                                    name = stringResource(R.string.perm_audio_name),
                                    granted = hasAudioPerm,
                                    onRequest = { audioPermLauncher.launch(LocaleHelper.audioReadPermissionName()) },
                                    onOpenSettings = { context.openAppDetailsSettings() }
                                )
                                PermissionStatusRow(
                                    name = stringResource(R.string.perm_camera_name),
                                    granted = hasCameraPerm,
                                    onRequest = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                                    onOpenSettings = { context.openAppDetailsSettings() }
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.perm_media_picker_name),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    VaStatusChip(
                                        label = stringResource(R.string.perm_status_zero_perm),
                                        color = tokens.success
                                    )
                                }
                            }
                        }

                        SettingsSection.STORAGE -> VaCard {
                            Text(stringResource(R.string.set_storage_desc))
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onOpenStorageAnalyzer) { Text(stringResource(R.string.set_open_storage)) }
                        }

                        SettingsSection.LANGUAGE -> VaCard {
                            Text(stringResource(R.string.set_language_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            AppLanguage.entries.forEach { lang ->
                                OutlinedButton(
                                    onClick = { viewModel.setLanguage(lang) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("lang_btn_${lang.tag}")
                                ) {
                                    Text("${lang.label}${if (settings.language == lang) " ✓" else ""}")
                                }
                            }
                        }

                        SettingsSection.BACKUP -> VaCard {
                            Text(stringResource(R.string.set_backup_desc))
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onOpenBackupRestore) { Text(stringResource(R.string.set_open_backup)) }
                        }

                        SettingsSection.ACCESSIBILITY -> VaCard {
                            SettingSwitch(stringResource(R.string.set_battery_saver), settings.batterySaverMode) { v ->
                                viewModel.updateSettings { it.copy(batterySaverMode = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_low_end), settings.lowEndDeviceMode) { v ->
                                viewModel.updateSettings { it.copy(lowEndDeviceMode = v) }
                            }
                            SettingSwitch(stringResource(R.string.set_high_contrast), settings.highContrastLabels) { v ->
                                viewModel.updateSettings { it.copy(highContrastLabels = v) }
                            }
                        }

                        SettingsSection.MAINTENANCE -> VaCard {
                            Text(stringResource(R.string.set_maint_desc))
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = viewModel::clearAppCache) { Text(stringResource(R.string.set_clear_cache_now)) }
                        }

                        SettingsSection.ABOUT -> VaCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VaLogoEmblem(sizeDp = 52)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(DeveloperIdentity.APP_NAME, style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(R.string.about_version, DeveloperIdentity.VERSION_NAME, DeveloperIdentity.VERSION_CODE))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.about_package, DeveloperIdentity.PACKAGE_NAME))
                            Text(stringResource(R.string.about_developer, DeveloperIdentity.DEVELOPER_NAME))
                            Text(stringResource(R.string.about_email, DeveloperIdentity.DEVELOPER_EMAIL))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.about_official_updates, DeveloperIdentity.DEVELOPER_EMAIL),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.primaryAccent
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        val opened = runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DeveloperIdentity.TELEGRAM_URL)))
                                        }.isSuccess
                                        if (!opened) {
                                            Toast.makeText(context, context.getString(R.string.msg_no_telegram_app), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_open_telegram), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val subject = context.getString(R.string.email_feedback_subject, DeveloperIdentity.APP_NAME)
                                        val mail = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:${DeveloperIdentity.DEVELOPER_EMAIL}")
                                            putExtra(Intent.EXTRA_SUBJECT, subject)
                                        }
                                        val opened = runCatching { context.startActivity(mail) }.isSuccess
                                        if (!opened) {
                                            Toast.makeText(context, context.getString(R.string.msg_no_email_app), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_send_email), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onOpenLegal, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                                Text(stringResource(R.string.menu_legal))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    name: String,
    granted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tokens = LocalVaTokens.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
            VaStatusChip(
                label = stringResource(if (granted) R.string.perm_status_granted else R.string.perm_status_not_granted),
                color = if (granted) tokens.success else tokens.warning
            )
        }
        if (!granted) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequest, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(stringResource(R.string.perm_request_btn))
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(stringResource(R.string.perm_open_settings))
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
