package com.tkofficial.videodownloder.ui.home

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.tkofficial.videodownloder.core.FastMediaThumb
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.core.openAppDetailsSettings
import com.tkofficial.videodownloder.database.DownloadEntity
import com.tkofficial.videodownloder.models.AnalyzedMedia
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.models.MediaFormatOption
import com.tkofficial.videodownloder.models.UiState
import com.tkofficial.videodownloder.ui.components.VaButton
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaIconButton
import com.tkofficial.videodownloder.ui.components.VaLogoEmblem
import com.tkofficial.videodownloder.ui.components.VaOutlinedButton
import com.tkofficial.videodownloder.ui.components.VaPrimaryButton
import com.tkofficial.videodownloder.ui.components.VaSectionTitle
import com.tkofficial.videodownloder.ui.components.VaStatusChip
import com.tkofficial.videodownloder.ui.components.VaTextButton
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.utilities.FormatUtils
import java.io.File
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenMediaStudio: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenStorageAnalyzer: () -> Unit,
    onOpenHistory: () -> Unit,
    onPlayVideo: (uri: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val displayedDownloads by viewModel.displayedDownloads.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showSearchBar by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var detailsTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var scheduleTarget by remember { mutableStateOf<Pair<AnalyzedMedia, MediaFormatOption>?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var missingPermissions by remember { mutableStateOf(LocaleHelper.allMissingRuntimePermissions(context).toList()) }
    var permPromptAttempted by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missingPermissions = LocaleHelper.allMissingRuntimePermissions(context).toList()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val multiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val stillMissing = LocaleHelper.allMissingRuntimePermissions(context).toList()
        missingPermissions = stillMissing
        if (stillMissing.isNotEmpty()) permPromptAttempted = true
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // A. Top Header
        item(key = "home_header", contentType = "header") {
            HomeHeaderRow(
                viewModel = viewModel,
                onToggleSearch = { showSearchBar = !showSearchBar },
                onOpenNotifications = onOpenNotifications,
                onOpenProfile = onOpenProfile,
                onOpenSettings = onOpenSettings
            )
        }

        // Unified runtime permissions prompt (Notifications, Videos, Photos, Music)
        if (missingPermissions.isNotEmpty()) {
            item(key = "home_permissions", contentType = "permissions") {
                VaCard(highlighted = true) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.perm_all_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (permPromptAttempted) stringResource(R.string.perm_denied_hint) else stringResource(R.string.perm_all_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VaButton(onClick = {
                                val toAsk = LocaleHelper.allMissingRuntimePermissions(context)
                                if (toAsk.isNotEmpty()) {
                                    multiPermLauncher.launch(toAsk)
                                } else {
                                    missingPermissions = emptyList()
                                }
                            }) {
                                Text(stringResource(R.string.perm_grant_all_btn))
                            }
                            if (permPromptAttempted) {
                                VaOutlinedButton(onClick = { context.openAppDetailsSettings() }) {
                                    Text(stringResource(R.string.perm_open_settings))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Bar
        item(key = "home_search_bar_holder", contentType = "search_bar") {
            AnimatedVisibility(visible = showSearchBar) {
                HomeSearchBar(viewModel = viewModel)
            }
        }

        // B. Smart Downloader Card
        item(key = "home_smart_downloader", contentType = "downloader") {
            HomeSmartDownloaderSection(
                viewModel = viewModel,
                onScheduleOption = { media, opt -> scheduleTarget = media to opt }
            )
        }

        // F. Quick Access
        item(key = "home_quick_access", contentType = "quick_access_row") {
            HomeQuickAccessSection(
                viewModel = viewModel,
                onOpenFileManager = onOpenFileManager,
                onOpenMediaStudio = onOpenMediaStudio,
                onOpenVault = onOpenVault,
                onOpenStorageAnalyzer = onOpenStorageAnalyzer,
                onOpenBrowser = onOpenBrowser,
                onOpenHistory = onOpenHistory,
                onOpenNotifications = onOpenNotifications
            )
        }

        // G. Real Statistics Card
        item(key = "home_stats_card", contentType = "stats") {
            HomeStatsCard(viewModel = viewModel)
        }

        // D. Advanced Download Manager Tabs
        item(key = "home_dm_tabs", contentType = "tabs") {
            HomeDownloadsTabRow(viewModel = viewModel)
        }

        if (displayedDownloads.isEmpty()) {
            item(key = "home_empty_downloads", contentType = "empty") {
                VaCard {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyText = when (selectedTab) {
                            DownloadManagerTab.ACTIVE -> stringResource(R.string.empty_active_downloads)
                            DownloadManagerTab.QUEUE -> stringResource(R.string.empty_queue_downloads)
                            DownloadManagerTab.COMPLETED -> stringResource(R.string.empty_completed_downloads)
                            DownloadManagerTab.FAILED -> stringResource(R.string.empty_failed_downloads)
                        }
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(displayedDownloads, key = { it.id }, contentType = { it.status }) { item ->
                DownloadItemCard(
                    item = item,
                    onPause = { viewModel.pauseDownload(item.id) },
                    onResume = { viewModel.resumeDownload(item.id) },
                    onCancel = { viewModel.cancelDownload(item.id) },
                    onRetry = { viewModel.retryDownload(item.id) },
                    onRename = { renameTarget = item },
                    onDelete = { deleteTarget = item },
                    onShare = { viewModel.shareDownload(item) },
                    onOpen = {
                        if (item.mediaType == MediaCategory.VIDEO.name && File(item.localPath).exists()) {
                            onPlayVideo(android.net.Uri.fromFile(File(item.localPath)).toString(), item.title)
                        } else {
                            viewModel.openExternal(item)
                        }
                    },
                    onDetails = { detailsTarget = item },
                    onMoveUp = { viewModel.movePriority(item.id, 1) },
                    onMoveDown = { viewModel.movePriority(item.id, -1) }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // Rename Dialog
    renameTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.renameDownload(target.id, newName)
                    renameTarget = null
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                VaTextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.home_delete_file_prompt, target.fileName)) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.deleteDownload(target.id, deleteFile = true)
                    deleteTarget = null
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                VaTextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    // Details Dialog
    detailsTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text(target.fileName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.home_details_source, target.platform), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.home_details_mime, target.mimeType), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.home_details_size, FormatUtils.formatBytes(target.totalBytes.coerceAtLeast(target.downloadedBytes))),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(
                            R.string.home_details_range,
                            stringResource(if (target.supportsRange) R.string.range_yes else R.string.range_no)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.home_details_url, target.url),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    target.sha256Hash?.let {
                        Text(stringResource(R.string.home_details_sha256, it), style = MaterialTheme.typography.labelSmall)
                    }
                    target.errorMessage?.let {
                        Text(stringResource(R.string.home_details_error, it), color = tokens.danger, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                VaButton(onClick = { detailsTarget = null }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }

    // Schedule Dialog
    scheduleTarget?.let { (media, option) ->
        AlertDialog(
            onDismissRequest = { scheduleTarget = null },
            title = { Text(stringResource(R.string.btn_schedule_download)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_schedule_choose_delay, media.title))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5L, 15L, 60L).forEach { mins ->
                            VaOutlinedButton(onClick = {
                                viewModel.enqueueAnalyzedOption(media, option, scheduleMinutes = mins)
                                scheduleTarget = null
                            }) {
                                Text(stringResource(R.string.mins_short, mins.toInt()))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                VaTextButton(onClick = { scheduleTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
private fun HomeHeaderRow(
    viewModel: HomeViewModel,
    onToggleSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val unreadNotifs by viewModel.unreadNotifications.collectAsStateWithLifecycle()
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..16 -> stringResource(R.string.greeting_afternoon)
        else -> stringResource(R.string.greeting_evening)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            VaLogoEmblem(sizeDp = 42)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "$greeting, ${settings.userDisplayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaIconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(48.dp).testTag("home_search_btn")
            ) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.cd_search), tint = tokens.iconTint)
            }
            VaIconButton(
                onClick = onOpenNotifications,
                modifier = Modifier.size(48.dp).testTag("home_notifications_btn")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadNotifs > 0) {
                            Badge { Text(unreadNotifs.coerceAtMost(99).toString()) }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Notifications, contentDescription = stringResource(R.string.cd_notifications), tint = tokens.iconTint)
                }
            }
            VaIconButton(
                onClick = onOpenProfile,
                modifier = Modifier.size(48.dp).testTag("home_profile_btn")
            ) {
                Icon(Icons.Rounded.Person, contentDescription = stringResource(R.string.cd_profile), tint = tokens.iconTint)
            }
            VaIconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp).testTag("home_settings_btn")
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.cd_settings), tint = tokens.iconTint)
            }
        }
    }
}

@Composable
private fun HomeSearchBar(viewModel: HomeViewModel) {
    val tokens = LocalVaTokens.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = searchQuery,
        onValueChange = viewModel::setSearchQuery,
        modifier = Modifier.fillMaxWidth().testTag("home_search_input"),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                VaIconButton(onClick = { viewModel.setSearchQuery("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.btn_clear))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(tokens.smallRadius)
    )
}

@Composable
private fun HomeSmartDownloaderSection(
    viewModel: HomeViewModel,
    onScheduleOption: (AnalyzedMedia, MediaFormatOption) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val batchMode by viewModel.batchMode.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()

    VaCard(highlighted = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.smart_downloader_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.smart_downloader_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !batchMode,
                onClick = { viewModel.setBatchMode(false) },
                label = { Text(stringResource(R.string.mode_single_link)) },
                modifier = Modifier.testTag("single_mode_chip")
            )
            FilterChip(
                selected = batchMode,
                onClick = { viewModel.setBatchMode(true) },
                label = { Text(stringResource(R.string.mode_batch_links)) },
                modifier = Modifier.testTag("batch_mode_chip")
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = urlInput,
            onValueChange = viewModel::onUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (batchMode) it.height(112.dp) else it }
                .testTag("url_input_field"),
            placeholder = {
                Text(
                    if (batchMode) stringResource(R.string.batch_url_hint)
                    else stringResource(R.string.url_hint)
                )
            },
            singleLine = !batchMode,
            maxLines = if (batchMode) 5 else 1,
            shape = RoundedCornerShape(tokens.smallRadius),
            trailingIcon = {
                Row {
                    VaIconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (clip.isNotBlank()) viewModel.onUrlChange(clip)
                        },
                        modifier = Modifier.testTag("paste_url_btn")
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.btn_paste), tint = tokens.iconTint)
                    }
                    if (urlInput.isNotEmpty()) {
                        VaIconButton(
                            onClick = { viewModel.clearAnalysis() },
                            modifier = Modifier.testTag("clear_url_btn")
                        ) {
                            Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.btn_clear))
                        }
                    }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        VaPrimaryButton(
            text = if (batchMode) stringResource(R.string.btn_process_batch) else stringResource(R.string.btn_process_link),
            icon = Icons.Rounded.Download,
            enabled = urlInput.isNotBlank() && analysisState !is UiState.Loading,
            onClick = {
                if (batchMode) viewModel.processBatchUrls() else viewModel.analyzeSingleLink()
            },
            testTag = "process_link_btn",
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(visible = analysisState !is UiState.Idle) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                when (val st = analysisState) {
                    UiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text(stringResource(R.string.analyzing_link), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is UiState.Error -> {
                        VaCard(
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = tokens.danger)
                                Spacer(Modifier.width(8.dp))
                                Text(st.message, color = tokens.danger, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    is UiState.Success -> {
                        AnalyzedMediaPreview(
                            media = st.data,
                            onDownloadOption = { opt -> viewModel.enqueueAnalyzedOption(st.data, opt, 0L) },
                            onScheduleOption = { opt -> onScheduleOption(st.data, opt) }
                        )
                    }
                    UiState.Idle -> {}
                }
            }
        }
    }
}

private data class HomeQuickAccessItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val filterCat: MediaCategory? = null,
    val onClick: () -> Unit
)

@Composable
private fun HomeQuickAccessSection(
    viewModel: HomeViewModel,
    onOpenFileManager: () -> Unit,
    onOpenMediaStudio: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenStorageAnalyzer: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val filterCategory by viewModel.filterCategory.collectAsStateWithLifecycle()

    val videoLabel = stringResource(R.string.qa_video_dl)
    val audioLabel = stringResource(R.string.qa_audio_dl)
    val imageLabel = stringResource(R.string.qa_image_dl)
    val fmLabel = stringResource(R.string.qa_file_manager)
    val studioLabel = stringResource(R.string.qa_media_studio)
    val vaultLabel = stringResource(R.string.qa_private_vault)
    val storageLabel = stringResource(R.string.qa_storage_analyzer)
    val browserLabel = stringResource(R.string.qa_browser)
    val historyLabel = stringResource(R.string.qa_history)
    val notifsLabel = stringResource(R.string.qa_notifications)

    val quickItems = remember(
        videoLabel, audioLabel, imageLabel, fmLabel, studioLabel,
        vaultLabel, storageLabel, browserLabel, historyLabel, notifsLabel
    ) {
        listOf(
            HomeQuickAccessItem("video", videoLabel, Icons.Rounded.VideoFile, MediaCategory.VIDEO) {
                viewModel.setFilterCategory(if (viewModel.filterCategory.value == MediaCategory.VIDEO) null else MediaCategory.VIDEO)
            },
            HomeQuickAccessItem("audio", audioLabel, Icons.Rounded.AudioFile, MediaCategory.AUDIO) {
                viewModel.setFilterCategory(if (viewModel.filterCategory.value == MediaCategory.AUDIO) null else MediaCategory.AUDIO)
            },
            HomeQuickAccessItem("image", imageLabel, Icons.Rounded.Image, MediaCategory.IMAGE) {
                viewModel.setFilterCategory(if (viewModel.filterCategory.value == MediaCategory.IMAGE) null else MediaCategory.IMAGE)
            },
            HomeQuickAccessItem("fm", fmLabel, Icons.Rounded.Folder, onClick = onOpenFileManager),
            HomeQuickAccessItem("studio", studioLabel, Icons.Rounded.AutoFixHigh, onClick = onOpenMediaStudio),
            HomeQuickAccessItem("vault", vaultLabel, Icons.Rounded.Lock, onClick = onOpenVault),
            HomeQuickAccessItem("storage", storageLabel, Icons.Rounded.Storage, onClick = onOpenStorageAnalyzer),
            HomeQuickAccessItem("browser", browserLabel, Icons.Rounded.Language, onClick = onOpenBrowser),
            HomeQuickAccessItem("history", historyLabel, Icons.Rounded.History, onClick = onOpenHistory),
            HomeQuickAccessItem("notifs", notifsLabel, Icons.Rounded.Notifications, onClick = onOpenNotifications)
        )
    }

    Column {
        VaSectionTitle(title = stringResource(R.string.section_quick_access))
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(quickItems, key = { it.key }, contentType = { "quick_access" }) { item ->
                val isSelectedFilter = item.filterCat != null && filterCategory == item.filterCat
                VaCard(
                    onClick = item.onClick,
                    highlighted = isSelectedFilter,
                    lightweight = true,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.width(88.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = tokens.iconTint, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatsCard(viewModel: HomeViewModel) {
    val tokens = LocalVaTokens.current
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
    val queueCount by viewModel.queueCount.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedCount.collectAsStateWithLifecycle()
    val totalCompletedBytes by viewModel.totalCompletedBytes.collectAsStateWithLifecycle()

    VaCard(lightweight = true) {
        VaSectionTitle(title = stringResource(R.string.section_recent_activity))
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatMiniBlock(stringResource(R.string.stat_active), activeCount.toString(), tokens.primaryAccent, Modifier.weight(1f))
            StatMiniBlock(stringResource(R.string.stat_queued), queueCount.toString(), tokens.warning, Modifier.weight(1f))
            StatMiniBlock(stringResource(R.string.stat_completed), completedCount.toString(), tokens.success, Modifier.weight(1f))
            StatMiniBlock(stringResource(R.string.stat_total_data), FormatUtils.formatBytes(totalCompletedBytes), tokens.tertiaryAccent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeDownloadsTabRow(viewModel: HomeViewModel) {
    val tokens = LocalVaTokens.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
    val queueCount by viewModel.queueCount.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedCount.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedCount.collectAsStateWithLifecycle()

    val tabs = listOf(
        DownloadManagerTab.ACTIVE to "${stringResource(R.string.tab_active)} ($activeCount)",
        DownloadManagerTab.QUEUE to "${stringResource(R.string.tab_queue)} ($queueCount)",
        DownloadManagerTab.COMPLETED to "${stringResource(R.string.tab_completed)} ($completedCount)",
        DownloadManagerTab.FAILED to "${stringResource(R.string.tab_failed)} ($failedCount)"
    )
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = tokens.cardSurface,
        contentColor = tokens.primaryAccent,
        modifier = Modifier.clip(RoundedCornerShape(tokens.smallRadius))
    ) {
        tabs.forEach { (tab, title) ->
            Tab(
                selected = selectedTab == tab,
                onClick = { viewModel.selectTab(tab) },
                text = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier.testTag("dm_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun StatMiniBlock(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalyzedMediaPreview(
    media: AnalyzedMedia,
    onDownloadOption: (MediaFormatOption) -> Unit,
    onScheduleOption: (MediaFormatOption) -> Unit
) {
    val tokens = LocalVaTokens.current
    var selectedOption by remember(media.sourceUrl) { mutableStateOf(media.options.first()) }

    VaCard(contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (media.thumbnailUrl != null) {
                val thumbCtx = LocalContext.current
                val thumbReq = remember(media.thumbnailUrl) {
                    ImageRequest.Builder(thumbCtx)
                        .data(media.thumbnailUrl)
                        .size(256)
                        .build()
                }
                AsyncImage(
                    model = thumbReq,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.elevatedSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (media.mediaCategory) {
                            MediaCategory.VIDEO -> Icons.Rounded.VideoFile
                            MediaCategory.AUDIO -> Icons.Rounded.AudioFile
                            MediaCategory.IMAGE -> Icons.Rounded.Image
                            else -> Icons.Rounded.Download
                        },
                        contentDescription = null,
                        tint = tokens.iconTint
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${media.platform} • ${media.mimeType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                VaStatusChip(
                    label = if (media.supportsRange) stringResource(R.string.range_supported) else stringResource(R.string.range_unsupported),
                    color = if (media.supportsRange) tokens.success else tokens.warning
                )
            }
        }

        if (media.options.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                media.options.forEach { opt ->
                    FilterChip(
                        selected = selectedOption.id == opt.id,
                        onClick = { selectedOption = opt },
                        label = { Text("${opt.quality} (${opt.label})") }
                    )
                }
            }
        }

        media.analysisNotes?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(note, style = MaterialTheme.typography.bodyMedium, color = tokens.warning)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            VaPrimaryButton(
                text = stringResource(R.string.btn_download_now),
                icon = Icons.Rounded.Download,
                onClick = { onDownloadOption(selectedOption) },
                modifier = Modifier.weight(1f),
                testTag = "confirm_download_btn"
            )
            VaOutlinedButton(
                onClick = { onScheduleOption(selectedOption) },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(tokens.smallRadius)
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.btn_schedule_download))
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val progress = if (item.totalBytes > 0L) {
        (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else if (item.status == "COMPLETED") 1f else 0f

    val percentInt = (progress * 100).toInt()

    VaCard(lightweight = true, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.elevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                if (item.status == "COMPLETED" && item.mediaType in setOf("IMAGE", "VIDEO")) {
                    val itemContext = LocalContext.current
                    val thumbModel = remember(item.localPath, item.id, item.mediaType) {
                        ImageRequest.Builder(itemContext)
                            .data(
                                FastMediaThumb(
                                    uriString = android.net.Uri.fromFile(File(item.localPath)).toString(),
                                    filePath = item.localPath,
                                    isVideo = item.mediaType == "VIDEO"
                                )
                            )
                            .size(200)
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = thumbModel,
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = when (item.mediaType) {
                            "VIDEO" -> Icons.Rounded.VideoFile
                            "AUDIO" -> Icons.Rounded.AudioFile
                            "IMAGE" -> Icons.Rounded.Image
                            else -> Icons.Rounded.Download
                        },
                        contentDescription = null,
                        tint = tokens.iconTint
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.mediaType} • ${FormatUtils.formatBytes(item.downloadedBytes)} / ${if (item.totalBytes > 0) FormatUtils.formatBytes(item.totalBytes) else "?"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val badgeColor = when (item.status) {
                "COMPLETED" -> tokens.success
                "FAILED", "CANCELLED" -> tokens.danger
                "PAUSED" -> tokens.warning
                else -> tokens.primaryAccent
            }
            val statusLabel = when (item.status) {
                "DOWNLOADING" -> "$percentInt%"
                "QUEUED" -> stringResource(R.string.tab_queue)
                "PAUSED" -> stringResource(R.string.status_paused)
                "COMPLETED" -> stringResource(R.string.tab_completed)
                "FAILED" -> stringResource(R.string.tab_failed)
                else -> item.status
            }
            VaStatusChip(label = statusLabel, color = badgeColor)
        }

        if (item.status in setOf("DOWNLOADING", "PAUSED", "QUEUED")) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = tokens.primaryAccent,
                trackColor = tokens.elevatedSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (item.status == "DOWNLOADING") FormatUtils.formatSpeed(item.speedBytesPerSec) else item.platform,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (item.status == "DOWNLOADING") {
                        stringResource(R.string.eta_prefix, FormatUtils.formatEta(item.etaSeconds))
                    } else {
                        stringResource(R.string.priority_label, item.priority)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item.errorMessage?.let { err ->
            Spacer(Modifier.height(6.dp))
            Text(err, style = MaterialTheme.typography.bodyMedium, color = tokens.danger, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.status == "DOWNLOADING") {
                ActionIconBtn(Icons.Rounded.Pause, stringResource(R.string.action_pause), onPause)
            }
            if (item.status == "PAUSED") {
                ActionIconBtn(Icons.Rounded.PlayArrow, stringResource(R.string.action_resume), onResume)
            }
            if (item.status in setOf("DOWNLOADING", "PAUSED", "QUEUED")) {
                ActionIconBtn(Icons.Rounded.Cancel, stringResource(R.string.action_cancel), onCancel)
            }
            if (item.status == "QUEUED") {
                ActionIconBtn(Icons.Rounded.ArrowUpward, stringResource(R.string.action_move_up), onMoveUp)
                ActionIconBtn(Icons.Rounded.ArrowDownward, stringResource(R.string.action_move_down), onMoveDown)
            }
            if (item.status in setOf("FAILED", "CANCELLED")) {
                ActionIconBtn(Icons.Rounded.Refresh, stringResource(R.string.action_retry), onRetry)
            }
            if (item.status == "COMPLETED") {
                ActionIconBtn(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.action_open), onOpen)
                ActionIconBtn(Icons.Rounded.Share, stringResource(R.string.action_share), onShare)
            }
            ActionIconBtn(Icons.Rounded.DriveFileRenameOutline, stringResource(R.string.action_rename), onRename)
            ActionIconBtn(Icons.Rounded.Info, stringResource(R.string.action_details), onDetails)
            ActionIconBtn(Icons.Rounded.Delete, stringResource(R.string.action_delete), onDelete, tint = tokens.danger)
        }
    }
}

@Composable
private fun ActionIconBtn(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = LocalVaTokens.current.iconTint
) {
    VaIconButton(
        onClick = onClick,
        animatePress = false,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
