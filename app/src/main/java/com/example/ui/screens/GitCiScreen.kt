package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.WorkflowBuilderEngine
import com.example.model.AppSettingsState
import com.example.model.CiArtifactInfo
import com.example.model.DownloadedArtifactRecord
import com.example.model.GitConflictItem
import com.example.model.GitHubRemoteRepo
import com.example.model.ProjectMetadata
import com.example.ui.components.SoftChip
import com.example.ui.components.SoftSwitch
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.io.File

@Composable
fun GitCiScreen(
    state: StudioUiState,
    settings: AppSettingsState,
    onInitLocalGit: (ProjectMetadata) -> Unit,
    onRefreshLocalGit: (ProjectMetadata) -> Unit,
    onStageLocalFile: (ProjectMetadata, String) -> Unit,
    onUnstageLocalFile: (ProjectMetadata, String) -> Unit,
    onStageAllLocalFiles: (ProjectMetadata) -> Unit,
    onCommitLocalGit: (ProjectMetadata, String) -> Unit,
    onCreateBranch: (ProjectMetadata, String) -> Unit,
    onSwitchBranch: (ProjectMetadata, String, Boolean) -> Unit,
    onDeleteBranch: (ProjectMetadata, String) -> Unit,
    onPreviewMerge: (ProjectMetadata, String) -> Unit,
    onDismissMergePreview: () -> Unit,
    onApplyMerge: (ProjectMetadata, String) -> Unit,
    onResolveConflict: (ProjectMetadata, String, Boolean) -> Unit,
    onStashChanges: (ProjectMetadata, String) -> Unit,
    onPopStash: (ProjectMetadata, String) -> Unit,
    onResetWorkingTree: (ProjectMetadata) -> Unit,
    onPreviewWorkflowYaml: (ProjectMetadata, String, String) -> Unit,
    onDismissWorkflowPreview: () -> Unit,
    onSaveWorkflowYaml: (ProjectMetadata, String, String) -> Unit,
    onSaveGitHubConfig: (String, String, String, String, String) -> Unit,
    onDisconnectGitHub: () -> Unit,
    onStartDeviceFlow: (String) -> Unit,
    onPollDeviceToken: () -> Unit,
    onFetchAccountRepos: () -> Unit,
    onCreateRemoteRepo: (String, String, Boolean) -> Unit,
    onCloneRepo: (String, String, String) -> Unit,
    onPullRemote: (ProjectMetadata) -> Unit,
    onPushRemote: (ProjectMetadata, String) -> Unit,
    onFetchGitHub: () -> Unit,
    onTogglePolling: (Boolean) -> Unit,
    onFetchRunLogs: (Long) -> Unit,
    onDownloadArtifact: (CiArtifactInfo) -> Unit,
    onInspectArtifactApk: (File) -> Unit,
    onDeleteArtifact: (DownloadedArtifactRecord) -> Unit,
    onPublishRelease: (String, String, String, Boolean, String?) -> Unit,
    onTriggerWorkflow: (Long) -> Unit,
    onRerunOrCancelRun: (Long, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    BackHandler { onBack() }

    var activeTab by remember { mutableIntStateOf(0) }
    var selectedProjId by remember(state.projects) { mutableStateOf(state.projects.firstOrNull()?.id) }
    val activeProj = state.projects.firstOrNull { it.id == selectedProjId }

    var showCreateBranchDialog by remember { mutableStateOf(false) }
    var pendingDiscardSwitchBranch by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var conflictTarget by remember { mutableStateOf<GitConflictItem?>(null) }
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var showReleaseDialog by remember { mutableStateOf(false) }

    var wfTemplate by remember { mutableStateOf(WorkflowBuilderEngine.WorkflowTemplate.ANDROID_GRADLE) }
    var enablePushTrigger by remember { mutableStateOf(false) }
    var enablePrTrigger by remember { mutableStateOf(true) }
    var includeTests by remember { mutableStateOf(true) }
    var wfYaml by remember(wfTemplate, settings.githubBranch, enablePushTrigger, enablePrTrigger, includeTests) {
        mutableStateOf(
            WorkflowBuilderEngine.buildWorkflowYaml(
                template = wfTemplate,
                branch = settings.githubBranch,
                enablePushTrigger = enablePushTrigger,
                enablePullRequest = enablePrTrigger,
                enableTagRelease = wfTemplate == WorkflowBuilderEngine.WorkflowTemplate.SIGNED_RELEASE,
                includeTests = includeTests,
                includeSigningSecretsOnRelease = wfTemplate == WorkflowBuilderEngine.WorkflowTemplate.SIGNED_RELEASE
            )
        )
    }

    LaunchedEffect(activeProj?.id) {
        activeProj?.let { onRefreshLocalGit(it) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("git_ci_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                Column {
                    Text("Git, Workflows & CI/CD", style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("Branches • Staging • Actions • Artifacts • Releases", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SoftChip(label = "Local Git", selected = activeTab == 0, onClick = { activeTab = 0 }, modifier = Modifier.weight(1f))
                SoftChip(label = "Workflow YAML", selected = activeTab == 1, onClick = { activeTab = 1 }, modifier = Modifier.weight(1f))
                SoftChip(label = "GitHub & CI", selected = activeTab == 2, onClick = { activeTab = 2 }, modifier = Modifier.weight(1f))
            }
        }

        when (activeTab) {
            0 -> localGitTabItems(
                state = state,
                settings = settings,
                activeProj = activeProj,
                onSelectProj = {
                    selectedProjId = it.id
                    onRefreshLocalGit(it)
                },
                onInitGit = onInitLocalGit,
                onRefreshGit = onRefreshLocalGit,
                onStageFile = onStageLocalFile,
                onUnstageFile = onUnstageLocalFile,
                onStageAll = onStageAllLocalFiles,
                onCommit = onCommitLocalGit,
                onRequestCreateBranch = { showCreateBranchDialog = true },
                onSwitchBranch = { proj, target, discard ->
                    val dirty = state.localGitStatus.stagedFiles.isNotEmpty() || state.localGitStatus.unstagedFiles.isNotEmpty()
                    if (dirty && !discard) pendingDiscardSwitchBranch = target
                    else onSwitchBranch(proj, target, discard)
                },
                onDeleteBranch = onDeleteBranch,
                onPreviewMerge = onPreviewMerge,
                onResolveConflict = { conflictTarget = it },
                onStash = onStashChanges,
                onPopStash = onPopStash,
                onRequestReset = { showResetConfirm = true },
                onPullRemote = onPullRemote,
                onPushRemote = onPushRemote
            )

            1 -> workflowBuilderTabItems(
                state = state,
                settings = settings,
                activeProj = activeProj,
                wfTemplate = wfTemplate,
                onSelectTemplate = { wfTemplate = it },
                wfYaml = wfYaml,
                onYamlChange = { wfYaml = it },
                enablePushTrigger = enablePushTrigger,
                onTogglePushTrigger = { enablePushTrigger = it },
                enablePrTrigger = enablePrTrigger,
                onTogglePrTrigger = { enablePrTrigger = it },
                includeTests = includeTests,
                onToggleIncludeTests = { includeTests = it },
                onPreviewAndSave = onPreviewWorkflowYaml
            )

            else -> githubRemoteCiTabItems(
                state = state,
                settings = settings,
                onSaveGitHubConfig = onSaveGitHubConfig,
                onDisconnectGitHub = onDisconnectGitHub,
                onStartDeviceFlow = onStartDeviceFlow,
                onPollDeviceToken = onPollDeviceToken,
                onFetchAccountRepos = onFetchAccountRepos,
                onSelectRemoteRepo = { r: GitHubRemoteRepo ->
                    onSaveGitHubConfig(r.owner, r.name, r.defaultBranch, "", settings.githubClientId)
                    onFetchGitHub()
                },
                onRequestCreateRepo = { showCreateRepoDialog = true },
                onCloneRepo = onCloneRepo,
                onFetchGitHub = onFetchGitHub,
                onTogglePolling = onTogglePolling,
                onTriggerWorkflow = onTriggerWorkflow,
                onRerunOrCancelRun = onRerunOrCancelRun,
                onFetchRunLogs = onFetchRunLogs,
                onDownloadArtifact = onDownloadArtifact,
                onInspectArtifactApk = onInspectArtifactApk,
                onDeleteArtifact = onDeleteArtifact,
                onRequestReleaseDialog = { showReleaseDialog = true }
            )
        }
    }

    if (showCreateBranchDialog && activeProj != null) {
        var newBranch by remember { mutableStateOf("") }
        StudioDialog(
            title = "Create & Switch Branch",
            onDismiss = { showCreateBranchDialog = false },
            confirmText = "Create",
            onConfirm = {
                onCreateBranch(activeProj, newBranch)
                showCreateBranchDialog = false
            }
        ) {
            OutlinedTextField(value = newBranch, onValueChange = { newBranch = it }, label = { Text("Branch Name") }, placeholder = { Text("feature/ui-polish") }, modifier = Modifier.fillMaxWidth())
        }
    }

    pendingDiscardSwitchBranch?.let { target ->
        if (activeProj != null) {
            StudioDialog(
                title = "Uncommitted Changes",
                onDismiss = { pendingDiscardSwitchBranch = null },
                confirmText = "Discard & Switch",
                onConfirm = {
                    onSwitchBranch(activeProj, target, true)
                    pendingDiscardSwitchBranch = null
                }
            ) {
                Text("Switching to '$target' will discard uncommitted workspace changes unless you Stash or Commit first.", color = p.warning)
            }
        }
    }

    if (showResetConfirm && activeProj != null) {
        StudioDialog(
            title = "Reset Working Tree to HEAD?",
            onDismiss = { showResetConfirm = false },
            confirmText = "Discard & Reset",
            onConfirm = {
                onResetWorkingTree(activeProj)
                showResetConfirm = false
            }
        ) {
            Text("This destructive action discards all staged and unstaged edits back to the last commit on '${state.localGitStatus.currentBranch}'.", color = p.error)
        }
    }

    state.gitMergePreview?.let { preview ->
        if (activeProj != null) {
            StudioDialog(
                title = "Merge ${preview.sourceBranch} -> ${preview.targetBranch}",
                onDismiss = onDismissMergePreview,
                confirmText = if (preview.isClean) "Apply Merge" else "Apply with Conflicts",
                onConfirm = { onApplyMerge(activeProj, preview.sourceBranch) }
            ) {
                Text("Clean files (${preview.cleanFiles.size}): ${preview.cleanFiles.take(5).joinToString(", ").ifEmpty { "none" }}", style = MaterialTheme.typography.bodySmall, color = p.success)
                Spacer(Modifier.height(6.dp))
                Text("Conflicting files (${preview.conflictingFiles.size}): ${preview.conflictingFiles.joinToString(", ") { it.relativePath }.ifEmpty { "none" }}", style = MaterialTheme.typography.bodySmall, color = if (preview.isClean) p.textSecondary else p.error)
            }
        }
    }

    conflictTarget?.let { c ->
        if (activeProj != null) {
            StudioDialog(
                title = "Resolve ${c.relativePath}",
                onDismiss = { conflictTarget = null },
                confirmText = "Keep Ours",
                onConfirm = {
                    onResolveConflict(activeProj, c.relativePath, true)
                    conflictTarget = null
                }
            ) {
                Text("OURS:", style = MaterialTheme.typography.labelLarge, color = p.cyan)
                Text(c.oursContent.take(260), style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.text)
                Spacer(Modifier.height(8.dp))
                Text("THEIRS:", style = MaterialTheme.typography.labelLarge, color = p.warning)
                Text(c.theirsContent.take(260), style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.text)
                Spacer(Modifier.height(8.dp))
                StudioSecondaryButton(
                    text = "Accept Theirs Instead",
                    onClick = {
                        onResolveConflict(activeProj, c.relativePath, false)
                        conflictTarget = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    state.workflowDiffPreview?.let { prev ->
        if (activeProj != null) {
            StudioDialog(
                title = "Confirm Workflow Write",
                onDismiss = onDismissWorkflowPreview,
                confirmText = "Write Workflow",
                onConfirm = { onSaveWorkflowYaml(activeProj, prev.fileName, prev.proposedYaml) }
            ) {
                Text(
                    if (prev.existedBefore) "Overwriting .github/workflows/${prev.fileName} (backup saved as ${prev.backupFileName})"
                    else "Creating .github/workflows/${prev.fileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.cyan
                )
                Spacer(Modifier.height(6.dp))
                Text(prev.diffText.take(800), style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.text)
            }
        }
    }

    if (showCreateRepoDialog) {
        var rName by remember { mutableStateOf("") }
        var rDesc by remember { mutableStateOf("Created from TK Studio") }
        var rPriv by remember { mutableStateOf(true) }
        StudioDialog(
            title = "Create GitHub Repository",
            onDismiss = { showCreateRepoDialog = false },
            confirmText = "Create Repo",
            onConfirm = {
                onCreateRemoteRepo(rName, rDesc, rPriv)
                showCreateRepoDialog = false
            }
        ) {
            OutlinedTextField(value = rName, onValueChange = { rName = it }, label = { Text("Repository Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = rDesc, onValueChange = { rDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Private Repository", color = p.text)
                SoftSwitch(checked = rPriv, onCheckedChange = { rPriv = it })
            }
        }
    }

    if (showReleaseDialog) {
        var tag by remember { mutableStateOf("v0.6.8") }
        var releaseTitle by remember { mutableStateOf("TK Studio Release") }
        var changelog by remember { mutableStateOf("Release published from TK Studio.") }
        var isDraft by remember { mutableStateOf(true) }
        val attachableApk = state.downloadedArtifacts.firstOrNull { it.extractedApkPath != null }?.extractedApkPath
        var attachApk by remember { mutableStateOf(false) }
        StudioDialog(
            title = "Confirm GitHub Release",
            onDismiss = { showReleaseDialog = false },
            confirmText = if (isDraft) "Create Draft" else "Publish Release",
            onConfirm = {
                onPublishRelease(tag, releaseTitle, changelog, isDraft, if (attachApk) attachableApk else null)
                showReleaseDialog = false
            }
        ) {
            OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Tag Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = releaseTitle, onValueChange = { releaseTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = changelog, onValueChange = { changelog = it }, label = { Text("Changelog") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Save as Draft (Recommended)", style = MaterialTheme.typography.bodyMedium, color = p.text)
                SoftSwitch(checked = isDraft, onCheckedChange = { isDraft = it })
            }
            if (attachableApk != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Upload ${File(attachableApk).name}", style = MaterialTheme.typography.bodySmall, color = p.cyan)
                    SoftSwitch(checked = attachApk, onCheckedChange = { attachApk = it })
                }
            }
        }
    }
}
