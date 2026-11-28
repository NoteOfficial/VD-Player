package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ExplorerSortMode
import com.example.model.ProjectFilter
import com.example.model.ProjectMetadata
import com.example.model.ProjectType
import com.example.model.WorkspaceNode
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FileRow
import com.example.ui.components.FolderTile
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSearchBar
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.TaskLoadingCard
import com.example.ui.components.formatBytes
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.text.DateFormat
import java.util.Date

@Composable
fun ProjectScreen(
    state: StudioUiState,
    onCreateProject: (String, ProjectType, String) -> Unit,
    onImportZip: (Uri) -> Unit,
    onCancelImport: () -> Unit,
    onExportZip: (ProjectMetadata, Uri) -> Unit,
    onSetFilter: (ProjectFilter) -> Unit,
    onSetTypeFilter: (ProjectType?) -> Unit,
    onSetSearch: (String) -> Unit,
    onToggleGridView: () -> Unit,
    onOpenExplorer: (ProjectMetadata, String) -> Unit,
    onCloseExplorer: () -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onBackupProject: (ProjectMetadata) -> Unit,
    onDeleteProject: (String) -> Unit,
    onOpenFileInLiveCoding: (String, WorkspaceNode) -> Unit,
    onSetExplorerSearch: (String) -> Unit,
    onSetExplorerSort: (ExplorerSortMode) -> Unit,
    onToggleMultiSelect: () -> Unit,
    onToggleNodeSelect: (String) -> Unit,
    onCreateExplorerEntry: (String, Boolean) -> Unit,
    onRenameExplorerNode: (String, String) -> Unit,
    onDeleteSelectedNodes: () -> Unit,
    onImportFileToExplorer: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val explorerProj = state.explorerProject
    if (explorerProj != null) {
        BackHandler {
            if (state.explorerRelativePath.isNotBlank()) {
                val parent = state.explorerRelativePath.substringBeforeLast('/', "")
                onOpenExplorer(explorerProj, parent)
            } else {
                onCloseExplorer()
            }
        }
        ProjectExplorerView(
            project = explorerProj,
            state = state,
            onNavigateDir = { rel -> onOpenExplorer(explorerProj, rel) },
            onClose = onCloseExplorer,
            onOpenFile = { node -> onOpenFileInLiveCoding(explorerProj.id, node) },
            onSetSearch = onSetExplorerSearch,
            onSetSort = onSetExplorerSort,
            onToggleMultiSelect = onToggleMultiSelect,
            onToggleNodeSelect = onToggleNodeSelect,
            onCreateEntry = onCreateExplorerEntry,
            onRenameNode = onRenameExplorerNode,
            onDeleteSelected = onDeleteSelectedNodes,
            onImportFile = onImportFileToExplorer,
            modifier = modifier
        )
        return
    }

    val p = StudioTheme.palette
    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectMetadata?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectMetadata?>(null) }
    var exportTarget by remember { mutableStateOf<ProjectMetadata?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportZip(uri)
    }
    val zipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val target = exportTarget
        if (uri != null && target != null) {
            onExportZip(target, uri)
        }
        exportTarget = null
    }

    val filtered = remember(state.projects, state.projectFilter, state.projectTypeFilter, state.projectSearchQuery) {
        state.projects.filter { proj ->
            val matchesFilter = when (state.projectFilter) {
                ProjectFilter.ALL -> !proj.isArchived
                ProjectFilter.RECENT -> !proj.isArchived
                ProjectFilter.FAVORITE -> proj.isFavorite && !proj.isArchived
                ProjectFilter.ARCHIVED -> proj.isArchived
            }
            val matchesType = state.projectTypeFilter == null || proj.type == state.projectTypeFilter
            val matchesQuery = state.projectSearchQuery.isBlank() || proj.name.contains(state.projectSearchQuery, ignoreCase = true)
            matchesFilter && matchesType && matchesQuery
        }.let { list ->
            if (state.projectFilter == ProjectFilter.RECENT) list.sortedByDescending { it.updatedAt } else list
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("project_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Projects", style = MaterialTheme.typography.headlineSmall, color = p.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioIconButton(
                        icon = if (state.projectGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = "Toggle list or grid layout",
                        onClick = onToggleGridView
                    )
                    StudioPrimaryButton(
                        text = "New",
                        icon = Icons.Rounded.Add,
                        onClick = { showNewDialog = true }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StudioSearchBar(
                    query = state.projectSearchQuery,
                    onQueryChange = onSetSearch,
                    placeholder = "Search projects...",
                    modifier = Modifier.weight(1f)
                )
                StudioSecondaryButton(
                    text = "Import",
                    icon = Icons.Rounded.FolderOpen,
                    onClick = { zipPicker.launch(arrayOf("application/zip", "*/*")) }
                )
            }
        }

        if (state.isImportingProject) {
            item {
                TaskLoadingCard(
                    stageLabel = state.importStageLabel,
                    detailText = state.importDetailText,
                    onCancel = onCancelImport
                )
            }
        }

        // Filter row: All / Recent / Favorite / Archived
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProjectFilter.entries.forEach { f ->
                    SoftChip(
                        label = f.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = state.projectFilter == f,
                        onClick = { onSetFilter(f) }
                    )
                }
                Spacer(Modifier.width(4.dp))
                SoftChip(
                    label = "All Types",
                    selected = state.projectTypeFilter == null,
                    onClick = { onSetTypeFilter(null) }
                )
                ProjectType.entries.forEach { t ->
                    SoftChip(
                        label = t.label,
                        selected = state.projectTypeFilter == t,
                        onClick = { onSetTypeFilter(if (state.projectTypeFilter == t) null else t) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateView(
                    title = "No Matching Projects",
                    description = "Create a new workspace project or import a ZIP archive to manage files and builds.",
                    actionLabel = "New Project",
                    onAction = { showNewDialog = true }
                )
            }
        } else if (state.projectGridView) {
            items(filtered.chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { proj ->
                        ProjectTileCard(
                            project = proj,
                            onOpen = { onOpenExplorer(proj, "") },
                            onToggleFavorite = { onToggleFavorite(proj.id) },
                            onRename = { renameTarget = proj },
                            onDuplicate = { onDuplicateProject(proj.id) },
                            onExport = {
                                exportTarget = proj
                                zipSaver.launch("${proj.name.replace(" ", "_")}.zip")
                            },
                            onBackup = { onBackupProject(proj) },
                            onArchive = { onToggleArchive(proj.id) },
                            onDelete = { deleteTarget = proj },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(filtered, key = { it.id }) { proj ->
                ProjectTileCard(
                    project = proj,
                    onOpen = { onOpenExplorer(proj, "") },
                    onToggleFavorite = { onToggleFavorite(proj.id) },
                    onRename = { renameTarget = proj },
                    onDuplicate = { onDuplicateProject(proj.id) },
                    onExport = {
                        exportTarget = proj
                        zipSaver.launch("${proj.name.replace(" ", "_")}.zip")
                    },
                    onBackup = { onBackupProject(proj) },
                    onArchive = { onToggleArchive(proj.id) },
                    onDelete = { deleteTarget = proj }
                )
            }
        }
    }

    if (showNewDialog) {
        NewProjectDialog(
            initialType = ProjectType.WEB_APP,
            onDismiss = { showNewDialog = false },
            onCreate = { name, type, pkg ->
                showNewDialog = false
                onCreateProject(name, type, pkg)
            }
        )
    }

    renameTarget?.let { proj ->
        var newName by remember(proj) { mutableStateOf(proj.name) }
        StudioDialog(
            title = "Rename Project",
            onDismiss = { renameTarget = null },
            confirmText = "Save",
            onConfirm = {
                onRenameProject(proj.id, newName)
                renameTarget = null
            }
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Project Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    deleteTarget?.let { proj ->
        StudioDialog(
            title = "Delete '${proj.name}'?",
            onDismiss = { deleteTarget = null },
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteProject(proj.id)
                deleteTarget = null
            }
        ) {
            Text(
                "This permanently removes ${proj.fileCount} files (${formatBytes(proj.sizeBytes)}) from disk.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
        }
    }
}

@Composable
private fun ProjectTileCard(
    project: ProjectMetadata,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    var menuOpen by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpen,
        glowAccent = if (project.isFavorite) p.warning else null,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(project.type.badge, p.primary)
                    StatusPill(project.lastBuildStatus, if (project.lastBuildStatus == "Verified") p.success else p.cyan)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (project.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = "Favorite project",
                        tint = if (project.isFavorite) p.warning else p.textSecondary
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Project menu", tint = p.textSecondary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Open Explorer") }, onClick = { menuOpen = false; onOpen() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuOpen = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Export ZIP") }, onClick = { menuOpen = false; onExport() })
                        DropdownMenuItem(text = { Text("Create Backup") }, onClick = { menuOpen = false; onBackup() })
                        DropdownMenuItem(
                            text = { Text(if (project.isArchived) "Unarchive" else "Archive") },
                            onClick = { menuOpen = false; onArchive() }
                        )
                        DropdownMenuItem(text = { Text("Delete", color = p.error) }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val dateStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(project.updatedAt))
        Text(
            text = "${project.fileCount} files • ${formatBytes(project.sizeBytes)} • $dateStr",
            style = MaterialTheme.typography.bodySmall,
            color = p.textSecondary
        )
    }
}

@Composable
private fun ProjectExplorerView(
    project: ProjectMetadata,
    state: StudioUiState,
    onNavigateDir: (String) -> Unit,
    onClose: () -> Unit,
    onOpenFile: (WorkspaceNode) -> Unit,
    onSetSearch: (String) -> Unit,
    onSetSort: (ExplorerSortMode) -> Unit,
    onToggleMultiSelect: () -> Unit,
    onToggleNodeSelect: (String) -> Unit,
    onCreateEntry: (String, Boolean) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onDeleteSelected: () -> Unit,
    onImportFile: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    var createIsDir by remember { mutableStateOf<Boolean?>(null) }
    var renameNodeTarget by remember { mutableStateOf<WorkspaceNode?>(null) }

    val fileImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportFile(uri)
    }

    val segments = remember(state.explorerRelativePath) {
        if (state.explorerRelativePath.isBlank()) emptyList()
        else state.explorerRelativePath.split('/').filter { it.isNotBlank() }
    }

    val visibleNodes = remember(state.explorerNodes, state.explorerSearchQuery) {
        if (state.explorerSearchQuery.isBlank()) state.explorerNodes
        else state.explorerNodes.filter { it.name.contains(state.explorerSearchQuery, ignoreCase = true) }
    }
    val dirs = visibleNodes.filter { it.isDirectory }
    val files = visibleNodes.filterNot { it.isDirectory }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("project_explorer"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    StudioIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to projects",
                        onClick = {
                            if (state.explorerRelativePath.isNotBlank()) {
                                onNavigateDir(state.explorerRelativePath.substringBeforeLast('/', ""))
                            } else onClose()
                        }
                    )
                    Column {
                        Text(project.name, style = MaterialTheme.typography.titleLarge, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${visibleNodes.size} items in folder", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudioIconButton(icon = Icons.Rounded.NoteAdd, contentDescription = "New File", onClick = { createIsDir = false })
                    StudioIconButton(icon = Icons.Rounded.CreateNewFolder, contentDescription = "New Folder", onClick = { createIsDir = true })
                    StudioIconButton(icon = Icons.Rounded.UploadFile, contentDescription = "Import File", onClick = { fileImporter.launch(arrayOf("*/*")) })
                }
            }
        }

        // Breadcrumbs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoftChip(label = "root", selected = segments.isEmpty(), onClick = { onNavigateDir("") })
                segments.forEachIndexed { idx, seg ->
                    val targetRel = segments.take(idx + 1).joinToString("/")
                    Text("/", color = p.textSecondary)
                    SoftChip(label = seg, selected = idx == segments.lastIndex, onClick = { onNavigateDir(targetRel) })
                }
            }
        }

        // Search, Sort & Multi-select
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioSearchBar(
                    query = state.explorerSearchQuery,
                    onQueryChange = onSetSearch,
                    placeholder = "Filter files in folder..."
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SoftChip(
                        label = if (state.explorerMultiSelect) "Selected (${state.explorerSelectedPaths.size})" else "Select",
                        selected = state.explorerMultiSelect,
                        icon = Icons.Rounded.CheckBox,
                        onClick = onToggleMultiSelect
                    )
                    if (state.explorerMultiSelect && state.explorerSelectedPaths.isNotEmpty()) {
                        SoftChip(
                            label = "Delete (${state.explorerSelectedPaths.size})",
                            selected = true,
                            icon = Icons.Rounded.Delete,
                            onClick = onDeleteSelected
                        )
                    }
                    ExplorerSortMode.entries.forEach { m ->
                        SoftChip(
                            label = m.label,
                            selected = state.explorerSortMode == m,
                            onClick = { onSetSort(m) }
                        )
                    }
                }
            }
        }

        if (visibleNodes.isEmpty()) {
            item {
                EmptyStateView(
                    title = "Empty Folder",
                    description = "Create a new file, subfolder, or import an asset into this directory.",
                    actionLabel = "Create File",
                    onAction = { createIsDir = false }
                )
            }
        } else {
            if (dirs.isNotEmpty()) {
                items(dirs.chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { dir ->
                            FolderTile(
                                node = dir,
                                selected = dir.absolutePath in state.explorerSelectedPaths,
                                onClick = {
                                    if (state.explorerMultiSelect) onToggleNodeSelect(dir.absolutePath)
                                    else onNavigateDir(dir.relativePath)
                                },
                                onLongClick = { renameNodeTarget = dir },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            items(files, key = { it.absolutePath }) { file ->
                FileRow(
                    node = file,
                    selected = file.absolutePath in state.explorerSelectedPaths,
                    multiSelectMode = state.explorerMultiSelect,
                    onClick = { onOpenFile(file) },
                    onToggleSelect = { onToggleNodeSelect(file.absolutePath) },
                    trailingAction = {
                        IconButton(onClick = { renameNodeTarget = file }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Rename ${file.name}", tint = p.textSecondary)
                        }
                    }
                )
            }
        }
    }

    createIsDir?.let { isDir ->
        var newName by remember { mutableStateOf("") }
        StudioDialog(
            title = if (isDir) "New Folder" else "New File",
            onDismiss = { createIsDir = null },
            confirmText = "Create",
            onConfirm = {
                if (newName.isNotBlank()) onCreateEntry(newName, isDir)
                createIsDir = null
            }
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(if (isDir) "Folder Name" else "File Name (e.g. script.js)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    renameNodeTarget?.let { node ->
        var newName by remember(node) { mutableStateOf(node.name) }
        StudioDialog(
            title = "Rename '${node.name}'",
            onDismiss = { renameNodeTarget = null },
            confirmText = "Rename",
            onConfirm = {
                if (newName.isNotBlank()) onRenameNode(node.absolutePath, newName)
                renameNodeTarget = null
            }
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
