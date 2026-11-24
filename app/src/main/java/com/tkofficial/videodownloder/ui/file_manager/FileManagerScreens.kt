package com.tkofficial.videodownloder.ui.file_manager

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.R
import com.tkofficial.videodownloder.models.FileEntry
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.ui.components.VaButton
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaFolderCard
import com.tkofficial.videodownloder.ui.components.VaIconButton
import com.tkofficial.videodownloder.ui.components.VaOutlinedButton
import com.tkofficial.videodownloder.ui.components.VaPrimaryButton
import com.tkofficial.videodownloder.ui.components.VaStatusChip
import com.tkofficial.videodownloder.ui.components.VaTextButton
import com.tkofficial.videodownloder.ui.profile.ProfileViewModel
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.utilities.FormatUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: ProfileViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val currentDir by viewModel.currentDir.collectAsStateWithLifecycle()
    val entries by viewModel.fileEntries.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHiddenFiles.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf<MediaCategory?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var hashTarget by remember { mutableStateOf<FileEntry?>(null) }
    var showBatchRename by remember { mutableStateOf(false) }
    var confirmDeleteEntries by remember { mutableStateOf<List<FileEntry>?>(null) }

    LaunchedEffect(Unit) { viewModel.loadDirectory() }
    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val filtered = remember(entries, search) {
        entries.filter { search.isBlank() || it.name.contains(search, true) }
    }
    val canStepUp = activeCategory == null && currentDir.parentFile != null &&
        currentDir.name != "VDPlayer" && currentDir.absolutePath.contains("VDPlayer")

    BackHandler(enabled = activeCategory != null || canStepUp) {
        if (activeCategory != null) {
            activeCategory = null
            viewModel.loadDirectory()
        } else if (canStepUp) {
            currentDir.parentFile?.let { viewModel.loadDirectory(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_file_manager)) },
                navigationIcon = {
                    VaIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    VaIconButton(onClick = { viewModel.toggleHiddenFiles() }) {
                        Icon(if (showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = stringResource(R.string.fm_toggle_hidden))
                    }
                    VaIconButton(onClick = { showCreateFolder = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = stringResource(R.string.fm_new_folder))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(stringResource(R.string.fm_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = activeCategory == null, onClick = {
                    activeCategory = null
                    viewModel.loadDirectory()
                }, label = { Text(stringResource(R.string.fm_root)) })
                listOf(
                    MediaCategory.VIDEO to stringResource(R.string.fm_videos),
                    MediaCategory.IMAGE to stringResource(R.string.fm_images),
                    MediaCategory.AUDIO to stringResource(R.string.fm_music),
                    MediaCategory.DOCUMENT to stringResource(R.string.fm_docs),
                    MediaCategory.ARCHIVE to stringResource(R.string.fm_archives)
                ).forEach { (cat, label) ->
                    FilterChip(
                        selected = activeCategory == cat,
                        onClick = {
                            activeCategory = cat
                            viewModel.loadCategoryFiles(cat)
                        },
                        label = { Text(label) }
                    )
                }
            }

            Text(currentDir.absolutePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

            AnimatedVisibility(visible = selectedPaths.isNotEmpty()) {
                val chosen = filtered.filter { selectedPaths.contains(it.path) }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VaOutlinedButton(onClick = {
                        viewModel.zipFileEntries(chosen, "archive_${System.currentTimeMillis()}.zip")
                        selectedPaths = emptySet()
                    }) {
                        Icon(Icons.Rounded.Archive, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.fm_zip))
                    }
                    VaOutlinedButton(onClick = { showBatchRename = true }) { Text(stringResource(R.string.fm_batch_rename)) }
                    VaOutlinedButton(onClick = { confirmDeleteEntries = chosen }) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = tokens.danger)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.fm_delete_count, chosen.size))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = activeCategory to currentDir.absolutePath,
                transitionSpec = {
                    if (tokens.reduceMotion) {
                        fadeIn() togetherWith fadeOut()
                    } else {
                        (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                    }
                },
                label = "fm_folder_transition"
            ) { (catState, _) ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                if (catState == null && currentDir.parentFile != null && currentDir.absolutePath.contains("VDPlayer")) {
                    item(key = "fm_up_folder", contentType = "up_folder") {
                        VaCard(onClick = {
                            currentDir.parentFile?.let { viewModel.loadDirectory(it) }
                        }, contentPadding = PaddingValues(12.dp)) {
                            Text(stringResource(R.string.fm_up_folder), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                items(filtered, key = { it.path }, contentType = { if (it.isDirectory) "dir" else "file" }) { entry ->
                    val isSel = selectedPaths.contains(entry.path)
                    if (entry.isDirectory && !isSel) {
                        VaFolderCard(
                            title = entry.name,
                            subtitle = stringResource(R.string.fm_items_count, entry.childCount),
                            icon = Icons.Rounded.Folder,
                            onClick = { viewModel.loadDirectory(File(entry.path)) },
                            modifier = Modifier.animateItem()
                        )
                    } else {
                    VaCard(
                        highlighted = isSel,
                        onClick = {
                            if (entry.isDirectory) {
                                viewModel.loadDirectory(File(entry.path))
                            } else {
                                selectedPaths = if (isSel) selectedPaths - entry.path else selectedPaths + entry.path
                            }
                        },
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.animateItem()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = tokens.iconTint
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (entry.isDirectory) stringResource(R.string.fm_items_count, entry.childCount) else FormatUtils.formatBytes(entry.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!entry.isDirectory) {
                                if (entry.name.endsWith(".zip", true)) {
                                    VaIconButton(onClick = { viewModel.extractZipEntry(entry) }) {
                                        Icon(Icons.Rounded.Unarchive, contentDescription = stringResource(R.string.fm_extract_zip))
                                    }
                                }
                                VaIconButton(onClick = { hashTarget = entry }) {
                                    Icon(Icons.Rounded.Tag, contentDescription = stringResource(R.string.fm_file_hash))
                                }
                                VaIconButton(onClick = { viewModel.shareFileEntry(entry) }) {
                                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
                                }
                            }
                            VaIconButton(onClick = { renameTarget = entry }) {
                                Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = stringResource(R.string.action_rename))
                            }
                            VaIconButton(onClick = {
                                selectedPaths = if (isSel) selectedPaths - entry.path else selectedPaths + entry.path
                            }) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.gallery_select), tint = if (isSel) tokens.primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    }
                }
            }
            }
        }
    }

    if (showCreateFolder) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text(stringResource(R.string.fm_create_folder)) },
            text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it }, singleLine = true) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.createFolder(folderName)
                    showCreateFolder = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = { VaTextButton(onClick = { showCreateFolder = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    renameTarget?.let { entry ->
        var newName by remember(entry.path) { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.renameFileEntry(entry, newName)
                    renameTarget = null
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = { VaTextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    hashTarget?.let { entry ->
        val hashes by produceState<Pair<String, String>?>(initialValue = null, entry.path) {
            value = withContext(Dispatchers.IO) {
                val file = File(entry.path)
                val md5 = FormatUtils.computeFileHash(file, "MD5")
                val sha256 = FormatUtils.computeFileHash(file, "SHA-256")
                md5 to sha256
            }
        }
        AlertDialog(
            onDismissRequest = { hashTarget = null },
            title = { Text(stringResource(R.string.fm_hashes_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    val pair = hashes
                    if (pair == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("MD5: ${pair.first}", style = MaterialTheme.typography.labelSmall)
                        Text("SHA-256: ${pair.second}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = { VaButton(onClick = { hashTarget = null }) { Text(stringResource(R.string.btn_close)) } }
        )
    }

    if (showBatchRename) {
        var prefix by remember { mutableStateOf("media") }
        AlertDialog(
            onDismissRequest = { showBatchRename = false },
            title = { Text(stringResource(R.string.fm_batch_rename_title, selectedPaths.size)) },
            text = { OutlinedTextField(value = prefix, onValueChange = { prefix = it }, label = { Text(stringResource(R.string.fm_prefix)) }, singleLine = true) },
            confirmButton = {
                VaButton(onClick = {
                    val chosen = filtered.filter { selectedPaths.contains(it.path) && !it.isDirectory }
                    viewModel.batchRenameEntries(chosen, prefix)
                    selectedPaths = emptySet()
                    showBatchRename = false
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = { VaTextButton(onClick = { showBatchRename = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    confirmDeleteEntries?.let { itemsToDelete ->
        AlertDialog(
            onDismissRequest = { confirmDeleteEntries = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.fm_delete_perm_msg, itemsToDelete.size)) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.deleteFileEntries(itemsToDelete)
                    selectedPaths = emptySet()
                    confirmDeleteEntries = null
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = { VaTextButton(onClick = { confirmDeleteEntries = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(
    viewModel: ProfileViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val summary by viewModel.storageSummary.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    var confirmClearCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshStorageAnalysis() }
    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_storage)) },
                navigationIcon = {
                    VaIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            summary?.let { s ->
                item {
                    VaCard(highlighted = true) {
                        Text(stringResource(R.string.storage_internal_title), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        val frac = if (s.totalBytes > 0L) (s.usedBytes.toFloat() / s.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.storage_summary_line,
                                FormatUtils.formatBytes(s.totalBytes),
                                FormatUtils.formatBytes(s.usedBytes),
                                FormatUtils.formatBytes(s.availableBytes)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.storage_app_media, FormatUtils.formatBytes(s.appMediaBytes)), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.storage_vault_enc, FormatUtils.formatBytes(s.vaultBytes)), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.storage_app_cache, FormatUtils.formatBytes(s.cacheBytes)), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        VaButton(onClick = { confirmClearCache = true }) { Text(stringResource(R.string.storage_clean_cache_btn, FormatUtils.formatBytes(s.cacheBytes))) }
                    }
                }

                item {
                    VaCard {
                        Text(stringResource(R.string.storage_large_files, s.largeFiles.size), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        if (s.largeFiles.isEmpty()) {
                            Text(stringResource(R.string.storage_no_large_files), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            s.largeFiles.take(8).forEach { f ->
                                Text("• ${f.name} (${FormatUtils.formatBytes(f.sizeBytes)})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item {
                    VaCard {
                        Text(stringResource(R.string.storage_duplicate_groups, s.duplicateGroups.size), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        if (s.duplicateGroups.isEmpty()) {
                            Text(stringResource(R.string.storage_no_duplicates), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            s.duplicateGroups.forEach { group ->
                                Text(
                                    stringResource(
                                        R.string.storage_group_label,
                                        FormatUtils.formatBytes(group.first().sizeBytes),
                                        group.joinToString { it.name }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.storage_clean_cache_title)) },
            text = { Text(stringResource(R.string.storage_clean_cache_msg)) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.clearAppCache()
                    confirmClearCache = false
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = { VaTextButton(onClick = { confirmClearCache = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var filterCat by remember { mutableStateOf<String?>(null) }

    val displayed = remember(notifications, filterCat) {
        notifications.filter { filterCat == null || it.category == filterCat }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_center_title)) },
                navigationIcon = {
                    VaIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    VaIconButton(onClick = viewModel::markAllNotificationsRead) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = stringResource(R.string.notif_mark_all_read))
                    }
                    VaIconButton(onClick = viewModel::clearNotifications) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.notif_clear_all))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterCat == null, onClick = { filterCat = null }, label = { Text(stringResource(R.string.gallery_all)) })
                listOf("DOWNLOAD", "PLAYBACK", "SECURITY", "STORAGE", "UPDATE").forEach { cat ->
                    FilterChip(selected = filterCat == cat, onClick = { filterCat = cat }, label = { Text(cat) })
                }
            }
            Spacer(Modifier.height(10.dp))
            if (displayed.isEmpty()) {
                VaCard { Text(stringResource(R.string.notif_empty), style = MaterialTheme.typography.bodyMedium) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayed, key = { it.id }, contentType = { "notif_item" }) { n ->
                        VaCard(highlighted = !n.isRead, contentPadding = PaddingValues(12.dp), modifier = Modifier.animateItem()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = tokens.iconTint)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(n.title, style = MaterialTheme.typography.titleMedium)
                                    Text(n.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                VaStatusChip(n.category, tokens.primaryAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: ProfileViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val records by viewModel.backupRecords.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
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
                title = { Text(stringResource(R.string.menu_backup)) },
                navigationIcon = {
                    VaIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                VaCard(highlighted = true) {
                    Text(stringResource(R.string.backup_card_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.backup_card_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    VaPrimaryButton(
                        text = stringResource(R.string.backup_export_btn),
                        icon = Icons.Rounded.Backup,
                        onClick = { exportLauncher.launch("vd_player_backup_${System.currentTimeMillis()}.json") }
                    )
                    Spacer(Modifier.height(8.dp))
                    VaOutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.backup_restore_btn))
                    }
                }
            }
            if (records.isNotEmpty()) {
                item { Text(stringResource(R.string.backup_history_title), style = MaterialTheme.typography.titleMedium) }
                items(records, key = { it.id }, contentType = { "backup_record" }) { rec ->
                    VaCard(contentPadding = PaddingValues(12.dp)) {
                        Text(rec.fileName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.backup_items_meta, rec.itemCount, rec.sha256Integrity.take(16)), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_msg)) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.restoreBackup(uri)
                    pendingRestoreUri = null
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = { VaTextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    viewModel: com.tkofficial.videodownloder.ui.home.HomeViewModel,
    onBack: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val history by viewModel.historyList.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qa_history)) },
                navigationIcon = {
                    VaIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        VaIconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.history_clear_cd))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (history.isEmpty()) {
                item { VaCard { Text(stringResource(R.string.history_empty)) } }
            } else {
                items(history, key = { it.id }, contentType = { "download_history" }) { h ->
                    VaCard(contentPadding = PaddingValues(12.dp)) {
                        Text(h.fileName, style = MaterialTheme.typography.titleMedium)
                        Text("${h.platform} • ${h.status} • ${FormatUtils.formatBytes(h.fileSize)}", style = MaterialTheme.typography.labelSmall)
                        Text(h.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.history_clear_msg)) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.clearDownloadHistory()
                    confirmClear = false
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = { VaTextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}
