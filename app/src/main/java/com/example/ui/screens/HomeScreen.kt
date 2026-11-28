package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WebAsset
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LocalizationEngine
import com.example.data.TutorialsRepository
import com.example.data.db.CrashRecoveryEntity
import com.example.model.AppSettingsState
import com.example.model.ProjectMetadata
import com.example.model.ProjectType
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.SoftControl
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioBrandMark
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSearchBar
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.TaskLoadingCard
import com.example.ui.components.formatBytes
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    state: StudioUiState,
    settings: AppSettingsState,
    onCreateProject: (String, ProjectType, String) -> Unit,
    onImportZip: (Uri) -> Unit,
    onCancelImport: () -> Unit,
    onOpenProjectInLiveCoding: (String) -> Unit,
    onOpenProjectExplorer: (ProjectMetadata) -> Unit,
    onNavigateLiveCoding: () -> Unit,
    onNavigateBuildCenter: () -> Unit,
    onNavigateApkInspector: () -> Unit,
    onNavigateToolchain: () -> Unit,
    onNavigateGitCi: () -> Unit,
    onNavigateTutorials: () -> Unit,
    onNavigateSigning: () -> Unit,
    onNavigateSecurity: () -> Unit,
    onNavigateBackup: () -> Unit,
    onNavigateAssetStudio: () -> Unit,
    onNavigateUtilities: () -> Unit,
    onNavigateTestQa: () -> Unit,
    onNavigateSettings: () -> Unit,
    onRestoreDraft: (CrashRecoveryEntity) -> Unit,
    onDismissDraft: (String) -> Unit,
    onQuickBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val str = remember(settings.languageCode) { LocalizationEngine.stringsFor(settings.languageCode) }

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showNotifications by remember { mutableStateOf(false) }
    var showHealthDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var preselectedType by remember { mutableStateOf(ProjectType.WEB_APP) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportZip(uri)
    }

    val githubConfigured = settings.githubOwner.isNotBlank() &&
        settings.githubRepo.isNotBlank() &&
        settings.hasGitHubToken

    val availableToolsCount = state.toolchainItems.count { it.isAvailable }
    val filteredRecent = remember(state.projects, searchQuery, settings.recentProjectsLimit) {
        val active = state.projects.filterNot { it.isArchived }
        if (searchQuery.isBlank()) active.take(settings.recentProjectsLimit.coerceIn(1, 15))
        else active.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageId.contains(searchQuery, ignoreCase = true)
        }
    }

    val commandTools = remember(str) {
        listOf(
            CommandEntry(str.buildCenter, "Run pipeline & preflight checks", Icons.Rounded.Build, onNavigateBuildCenter),
            CommandEntry(str.toolchain, "Inspect runtime tools & ABI", Icons.Rounded.Construction, onNavigateToolchain),
            CommandEntry("Git & CI/CD", "Commits, workflow YAML & GitHub Actions", Icons.Rounded.CloudUpload, onNavigateGitCi),
            CommandEntry("Signing Wizard", "Generate RSA/EC keys & vault", Icons.Rounded.Key, onNavigateSigning),
            CommandEntry(str.assetStudio, "Resize icons & generate coded PNGs", Icons.Rounded.Palette, onNavigateAssetStudio),
            CommandEntry(str.utilities, "JSON, XML, Color, Hash, Regex, Diff", Icons.Rounded.Tune, onNavigateUtilities),
            CommandEntry(str.testCenter, "QA suite, Code Map & cache cleanup", Icons.Rounded.CheckCircle, onNavigateTestQa),
            CommandEntry(str.security, "Secret audit & ZIP-slip diagnostics", Icons.Rounded.Security, onNavigateSecurity),
            CommandEntry(str.backupRestore, "Encrypted snapshots & rollback", Icons.Rounded.Backup, onNavigateBackup),
            CommandEntry(str.tutorials, "${TutorialsRepository.lessons.size} guided lessons", Icons.Rounded.MenuBook, onNavigateTutorials),
            CommandEntry(str.settings, "Themes, editor & permissions", Icons.Rounded.Settings, onNavigateSettings)
        )
    }

    val matchingTools = remember(searchQuery, commandTools) {
        if (searchQuery.isBlank()) emptyList()
        else commandTools.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    val matchingTutorials = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else TutorialsRepository.lessons.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }.take(4)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Brand + Health + Search/Notifications
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    StudioBrandMark(size = 42.dp)
                    Column {
                        Text(
                            text = "TK Studio",
                            style = MaterialTheme.typography.headlineSmall,
                            color = p.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val envPillText = when {
                            state.isInitializing -> "Checking Env..."
                            state.envHealth == null -> "Env Unknown"
                            else -> "Env Ready • $availableToolsCount/${state.toolchainItems.size} Tools"
                        }
                        val envPillColor = when {
                            state.isInitializing -> p.cyan
                            state.envHealth == null -> p.warning
                            else -> p.success
                        }
                        StatusPill(
                            text = envPillText,
                            color = envPillColor,
                            modifier = Modifier
                                .clickable { showHealthDialog = true }
                                .testTag("home_env_health_pill")
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Command Search",
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier.testTag("home_search_button")
                    )
                    StudioIconButton(
                        icon = Icons.Rounded.Notifications,
                        contentDescription = "Task notifications",
                        onClick = { showNotifications = true },
                        tint = if (state.notifications.any { it.isError }) p.warning else p.text,
                        modifier = Modifier.testTag("home_notifications_button")
                    )
                }
            }
        }

        // Command & Workspace Search Engine (#48)
        if (showSearch) {
            item {
                StudioSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search projects, 11 tools, 266 tutorials...",
                    onCancel = {
                        searchQuery = ""
                        showSearch = false
                    }
                )
            }
            if (searchQuery.isNotBlank() && (matchingTools.isNotEmpty() || matchingTutorials.isNotEmpty())) {
                item {
                    GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(12.dp)) {
                        Text("Command Matches", style = MaterialTheme.typography.titleSmall, color = p.cyan)
                        Spacer(Modifier.height(6.dp))
                        matchingTools.forEach { cmd ->
                            SoftControl(
                                onClick = cmd.onClick,
                                cornerRadius = 14.dp,
                                contentPadding = PaddingValues(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(cmd.icon, contentDescription = null, tint = p.cyan, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cmd.title, style = MaterialTheme.typography.titleSmall, color = p.text)
                                        Text(cmd.subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                                    }
                                }
                            }
                        }
                        matchingTutorials.forEach { tut ->
                            SoftControl(
                                onClick = onNavigateTutorials,
                                cornerRadius = 14.dp,
                                contentPadding = PaddingValues(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = p.violet, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tut.title, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(tut.category, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Crash / Unsaved Draft Recovery Banner (#47)
        if (state.recoveryDrafts.isNotEmpty()) {
            item {
                val draft = state.recoveryDrafts.first()
                GlassCard(glowAccent = p.warning, contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, tint = p.warning)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unsaved Draft Detected", style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(
                                "${File(draft.targetPath).name} (${state.recoveryDrafts.size} draft(s))",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textSecondary
                            )
                        }
                        StudioPrimaryButton(text = "Restore", onClick = { onRestoreDraft(draft) })
                        StudioSecondaryButton(text = "Dismiss", onClick = { onDismissDraft(draft.id) })
                    }
                }
            }
        }

        if (state.isImportingProject) {
            item {
                TaskLoadingCard(
                    stageLabel = state.importStageLabel,
                    detailText = state.importDetailText,
                    progressFraction = null,
                    onCancel = onCancelImport
                )
            }
        }

        // Welcome & Primary CTA Panel
        item {
            GlassCard(
                glowAccent = p.primary,
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                StatusPill(text = "TK OFFICIAL • v0.6.8", color = p.cyan)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Build, Inspect & Ship From Your Device",
                    style = MaterialTheme.typography.displaySmall,
                    color = p.text
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.projects.isEmpty()) {
                        "Your workspace is clean. Start a new Web App, Web-to-APK wrapper, or import an existing ZIP archive."
                    } else {
                        "${state.projects.size} workspace project(s) • ${formatBytes(state.storageUsageBytes)} used"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioPrimaryButton(
                        text = str.newProject,
                        icon = Icons.Rounded.Add,
                        onClick = {
                            preselectedType = ProjectType.WEB_APP
                            showNewProjectDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_new_project_button")
                    )
                    StudioSecondaryButton(
                        text = str.importZip,
                        icon = Icons.Rounded.FolderOpen,
                        onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_import_button")
                    )
                }
            }
        }

        // Quick Action Grid (8 core actions)
        item {
            Text(str.quickActions, style = MaterialTheme.typography.titleLarge, color = p.text)
            Spacer(Modifier.height(10.dp))
            val actions = listOf(
                QuickActionSpec("Import", "ZIP / Archive", Icons.Rounded.FolderOpen) {
                    zipPicker.launch(arrayOf("application/zip", "*/*"))
                },
                QuickActionSpec("Live Coding", "Split WebView", Icons.Rounded.Code, onClick = onNavigateLiveCoding),
                QuickActionSpec("Quick Build", "Package & Hash", Icons.Rounded.PlayArrow) {
                    if (state.projects.isEmpty()) {
                        preselectedType = ProjectType.WEB_APP
                        showNewProjectDialog = true
                    } else {
                        onQuickBuild()
                        onNavigateBuildCenter()
                    }
                },
                QuickActionSpec("Web-to-APK", "HTML Bundle", Icons.Rounded.WebAsset) {
                    preselectedType = ProjectType.WEB_TO_APK
                    showNewProjectDialog = true
                },
                QuickActionSpec("Native Build", "Kotlin Module", Icons.Rounded.DeveloperBoard) {
                    preselectedType = ProjectType.ANDROID_KOTLIN
                    showNewProjectDialog = true
                },
                QuickActionSpec(
                    title = "GitHub / CI",
                    subtitle = if (githubConfigured) "${settings.githubOwner}/${settings.githubRepo}" else "Setup required",
                    icon = Icons.Rounded.CloudUpload,
                    accentWarning = !githubConfigured,
                    onClick = onNavigateGitCi
                ),
                QuickActionSpec("APK Inspector", "Parse & Compare", Icons.Rounded.VerifiedUser, onClick = onNavigateApkInspector),
                QuickActionSpec("Toolchain", "$availableToolsCount Active", Icons.Rounded.Construction, onClick = onNavigateToolchain)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { act ->
                            SoftControl(
                                onClick = act.onClick,
                                cornerRadius = 18.dp,
                                accent = if (act.accentWarning) p.warning else null,
                                contentPadding = PaddingValues(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_${act.title.lowercase().replace(" ", "_").replace("/", "_")}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = act.icon,
                                        contentDescription = act.title,
                                        tint = if (act.accentWarning) p.warning else p.cyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            act.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = p.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            act.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (act.accentWarning) p.warning else p.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Studio Tools Strip (Asset Studio, Dev Utilities, Test & Code Map)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SoftControl(onClick = onNavigateAssetStudio, cornerRadius = 16.dp, contentPadding = PaddingValues(12.dp), modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = p.cyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(str.assetStudio, style = MaterialTheme.typography.labelMedium, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                SoftControl(onClick = onNavigateUtilities, cornerRadius = 16.dp, contentPadding = PaddingValues(12.dp), modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = p.violet, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(str.utilities, style = MaterialTheme.typography.labelMedium, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                SoftControl(onClick = onNavigateTestQa, cornerRadius = 16.dp, contentPadding = PaddingValues(12.dp), modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = p.success, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(str.testCenter, style = MaterialTheme.typography.labelMedium, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Active Build / Build Queue
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Build Queue & History", style = MaterialTheme.typography.titleLarge, color = p.text)
                StudioSecondaryButton(text = str.buildCenter, icon = Icons.Rounded.Build, onClick = onNavigateBuildCenter)
            }
            Spacer(Modifier.height(8.dp))
            if (state.isBuilding) {
                TaskLoadingCard(
                    stageLabel = state.activeBuildStage.label,
                    detailText = state.activeBuildStatusText,
                    progressFraction = state.activeBuildProgress
                )
            } else if (state.buildHistory.isEmpty()) {
                GlassCard {
                    Text(
                        str.noBuildsMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textSecondary
                    )
                }
            } else {
                val latest = state.buildHistory.first()
                GlassCard(onClick = onNavigateBuildCenter, glowAccent = if (latest.success) p.success else p.error) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(latest.projectName, style = MaterialTheme.typography.titleMedium, color = p.text)
                            Text(
                                "${latest.profile.label} • ${latest.durationMs} ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textSecondary
                            )
                        }
                        StatusPill(
                            text = latest.stage.label,
                            color = if (latest.success) p.success else p.error
                        )
                    }
                }
            }
        }

        // Recent Projects (real only, never fabricated)
        item {
            Text(str.recentProjects, style = MaterialTheme.typography.titleLarge, color = p.text)
        }
        if (filteredRecent.isEmpty()) {
            item {
                EmptyStateView(
                    title = str.noProjectsTitle,
                    description = str.noProjectsDesc,
                    actionLabel = str.newProject,
                    onAction = {
                        preselectedType = ProjectType.WEB_APP
                        showNewProjectDialog = true
                    }
                )
            }
        } else {
            items(filteredRecent, key = { it.id }) { proj ->
                GlassCard(
                    onClick = { onOpenProjectExplorer(proj) },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(proj.name, style = MaterialTheme.typography.titleMedium, color = p.text)
                                StatusPill(proj.type.badge, p.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${proj.fileCount} files • ${formatBytes(proj.sizeBytes)} • ${proj.lastBuildStatus}",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textSecondary
                            )
                        }
                        StudioSecondaryButton(
                            text = "Code",
                            onClick = {
                                onOpenProjectInLiveCoding(proj.id)
                                onNavigateLiveCoding()
                            }
                        )
                    }
                }
            }
        }

        // Storage & Contextual Tutorial Card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateTestQa,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text("Storage & Health", style = MaterialTheme.typography.titleSmall, color = p.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(formatBytes(state.storageUsageBytes), style = MaterialTheme.typography.titleLarge, color = p.cyan)
                    Text("${state.backups.size} snapshots • ${state.incrementalCacheCount} cache", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateTutorials,
                    glowAccent = p.violet,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = p.violet, modifier = Modifier.size(18.dp))
                        Text(str.tutorials, style = MaterialTheme.typography.titleSmall, color = p.text)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${TutorialsRepository.lessons.size} Guided Labs", style = MaterialTheme.typography.titleMedium, color = p.violet)
                    Text(
                        "${settings.completedTutorialSteps.size} steps done",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textSecondary
                    )
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            initialType = preselectedType,
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, type, pkg ->
                showNewProjectDialog = false
                onCreateProject(name, type, pkg)
            }
        )
    }

    if (showNotifications) {
        StudioDialog(
            title = "Task Notifications",
            onDismiss = { showNotifications = false },
            dismissText = "Close"
        ) {
            if (state.notifications.isEmpty()) {
                Text("No recent task alerts.", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.notifications.take(6).forEach { note ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(note.detail, style = MaterialTheme.typography.bodyMedium, color = p.text)
                                Text(
                                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(note.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.textSecondary
                                )
                            }
                            StatusPill(
                                text = if (note.isError) "Alert" else "Done",
                                color = if (note.isError) p.error else p.success
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHealthDialog) {
        StudioDialog(
            title = "Environment Health & First-Run Checks",
            onDismiss = { showHealthDialog = false },
            confirmText = "Open QA Suite",
            onConfirm = {
                showHealthDialog = false
                onNavigateTestQa()
            },
            dismissText = "Close"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.envHealth?.let { env ->
                    Text(env.statusSummary, style = MaterialTheme.typography.titleSmall, color = p.cyan)
                    Text("ABIs: ${env.supportedAbis.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    Text("CPU Cores: ${env.cpuCores} • Max Safe Workers: ${env.recommendedMaxWorkers}", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    Text("Free Storage: ${formatBytes(env.usableStorageBytes)} • Heap: ${env.usedHeapMb}/${env.maxHeapMb} MB", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                } ?: Text("Inspecting device environment...", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                state.dbIntegritySummary?.let { dbMsg ->
                    StatusPill(
                        text = dbMsg,
                        color = if (dbMsg.contains("ok", ignoreCase = true)) p.success else p.warning
                    )
                }
            }
        }
    }
}

private data class QuickActionSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentWarning: Boolean = false,
    val onClick: () -> Unit
)

private data class CommandEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun NewProjectDialog(
    initialType: ProjectType,
    onDismiss: () -> Unit,
    onCreate: (String, ProjectType, String) -> Unit
) {
    val p = StudioTheme.palette
    var name by remember { mutableStateOf("") }
    var packageId by remember { mutableStateOf("com.tkofficial.webapp") }
    var selectedType by remember { mutableStateOf(initialType) }

    StudioDialog(
        title = "Create Workspace Project",
        onDismiss = onDismiss,
        confirmText = "Create",
        onConfirm = {
            onCreate(name.ifBlank { "TK ${selectedType.label}" }, selectedType, packageId)
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Project Name") },
            placeholder = { Text("My Cyber App") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_project_name_input")
        )
        Spacer(Modifier.height(10.dp))
        Text("Project Template", style = MaterialTheme.typography.labelLarge, color = p.text)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProjectType.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { t ->
                        SoftChip(
                            label = t.label,
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        if (selectedType == ProjectType.WEB_TO_APK || selectedType == ProjectType.ANDROID_KOTLIN) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = packageId,
                onValueChange = { packageId = it },
                label = { Text("Package ID") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
