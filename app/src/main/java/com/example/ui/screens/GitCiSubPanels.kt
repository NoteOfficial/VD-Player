package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.WorkflowBuilderEngine
import com.example.model.AppSettingsState
import com.example.model.CiArtifactInfo
import com.example.model.DownloadedArtifactRecord
import com.example.model.GitConflictItem
import com.example.model.GitHubRemoteRepo
import com.example.model.ProjectMetadata
import com.example.ui.components.ErrorStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.SoftSwitch
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.TaskLoadingCard
import com.example.ui.components.formatBytes
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.io.File

fun LazyListScope.localGitTabItems(
    state: StudioUiState,
    settings: AppSettingsState,
    activeProj: ProjectMetadata?,
    onSelectProj: (ProjectMetadata) -> Unit,
    onInitGit: (ProjectMetadata) -> Unit,
    onRefreshGit: (ProjectMetadata) -> Unit,
    onStageFile: (ProjectMetadata, String) -> Unit,
    onUnstageFile: (ProjectMetadata, String) -> Unit,
    onStageAll: (ProjectMetadata) -> Unit,
    onCommit: (ProjectMetadata, String) -> Unit,
    onRequestCreateBranch: () -> Unit,
    onSwitchBranch: (ProjectMetadata, String, Boolean) -> Unit,
    onDeleteBranch: (ProjectMetadata, String) -> Unit,
    onPreviewMerge: (ProjectMetadata, String) -> Unit,
    onResolveConflict: (GitConflictItem) -> Unit,
    onStash: (ProjectMetadata, String) -> Unit,
    onPopStash: (ProjectMetadata, String) -> Unit,
    onRequestReset: () -> Unit,
    onPullRemote: (ProjectMetadata) -> Unit,
    onPushRemote: (ProjectMetadata, String) -> Unit
) {
    item {
        val p = StudioTheme.palette
        GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Git Engine Status", style = MaterialTheme.typography.titleSmall, color = p.text)
                StatusPill(
                    text = if (state.localGitStatus.hasSystemGitBinary) "System Git + TK Engine" else "TK Pure-Kotlin Git",
                    color = p.cyan
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(state.localGitStatus.runtimeNote, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            Text(
                "Identity: ${settings.gitAuthorName} <${settings.gitAuthorEmail}>",
                style = MaterialTheme.typography.labelSmall,
                color = p.cyan
            )
        }
    }

    if (activeProj == null) {
        item {
            GlassCard {
                Text("Create or clone a project first to track branches, staging, diffs, and commits.", color = StudioTheme.palette.textSecondary)
            }
        }
        return
    }

    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.projects.forEach { proj ->
                SoftChip(
                    label = proj.name,
                    selected = proj.id == activeProj.id,
                    onClick = { onSelectProj(proj) }
                )
            }
        }
    }

    if (!state.localGitStatus.initialized) {
        item {
            val p = StudioTheme.palette
            GlassCard(glowAccent = p.primary) {
                Text("Repository Not Initialized", style = MaterialTheme.typography.titleMedium, color = p.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Initialize a local .tkstudio_git repository inside '${activeProj.name}' to unlock staging, branches, stashing, and commits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
                Spacer(Modifier.height(10.dp))
                StudioPrimaryButton(
                    text = "Initialize Git Repository",
                    icon = Icons.Rounded.AccountTree,
                    onClick = { onInitGit(activeProj) },
                    modifier = Modifier.fillMaxWidth().testTag("git_init_button")
                )
            }
        }
        return
    }

    // Branches & Merge Controls
    item {
        val p = StudioTheme.palette
        val st = state.localGitStatus
        GlassCard(glowAccent = p.violet) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Branch: ${st.currentBranch}", style = MaterialTheme.typography.titleMedium, color = p.text)
                StudioSecondaryButton(text = "New Branch", icon = Icons.Rounded.Add, onClick = onRequestCreateBranch)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                st.branches.forEach { br ->
                    SoftChip(
                        label = if (br == st.currentBranch) "* $br" else br,
                        selected = br == st.currentBranch,
                        onClick = {
                            if (br != st.currentBranch) {
                                onSwitchBranch(activeProj, br, false)
                            }
                        }
                    )
                }
            }
            val otherBranches = st.branches.filter { it != st.currentBranch }
            if (otherBranches.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    otherBranches.forEach { other ->
                        StudioSecondaryButton(
                            text = "Merge $other",
                            icon = Icons.Rounded.MergeType,
                            onClick = { onPreviewMerge(activeProj, other) }
                        )
                        StudioSecondaryButton(
                            text = "Del $other",
                            icon = Icons.Rounded.Delete,
                            onClick = { onDeleteBranch(activeProj, other) }
                        )
                    }
                }
            }
        }
    }

    // Conflicts if any
    if (state.localGitStatus.conflicts.isNotEmpty()) {
        item {
            Text("Merge Conflicts (${state.localGitStatus.conflicts.size})", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.error)
        }
        items(state.localGitStatus.conflicts) { conflict ->
            val p = StudioTheme.palette
            GlassCard(glowAccent = p.error, contentPadding = PaddingValues(12.dp)) {
                Text(conflict.relativePath, style = MaterialTheme.typography.titleSmall, color = p.error)
                Text("Resolve conflict before committing.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                Spacer(Modifier.height(6.dp))
                StudioPrimaryButton(text = "Open Conflict Resolver", onClick = { onResolveConflict(conflict) })
            }
        }
    }

    // Commit, Stage, Stash, Reset & Remote Sync Card
    item {
        val p = StudioTheme.palette
        var commitMsg by remember { mutableStateOf("") }
        val totalChanges = state.localGitStatus.stagedFiles.size + state.localGitStatus.unstagedFiles.size
        GlassCard(glowAccent = p.primary) {
            Text(
                "Changes: ${state.localGitStatus.stagedFiles.size} staged • ${state.localGitStatus.unstagedFiles.size} unstaged",
                style = MaterialTheme.typography.titleMedium,
                color = p.text
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = commitMsg,
                onValueChange = { commitMsg = it },
                label = { Text("Commit Message") },
                placeholder = { Text("feat: update workspace") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioPrimaryButton(
                    text = "Commit",
                    icon = Icons.Rounded.Commit,
                    enabled = totalChanges > 0 || state.localGitCommits.isEmpty(),
                    onClick = {
                        onCommit(activeProj, commitMsg)
                        commitMsg = ""
                    }
                )
                StudioSecondaryButton(text = "Stage All", onClick = { onStageAll(activeProj) })
                StudioSecondaryButton(text = "Refresh", icon = Icons.Rounded.Refresh, onClick = { onRefreshGit(activeProj) })
                StudioSecondaryButton(text = "Stash", enabled = totalChanges > 0, onClick = { onStash(activeProj, "WIP") })
                StudioSecondaryButton(text = "Reset HEAD", icon = Icons.Rounded.Restore, enabled = totalChanges > 0, onClick = onRequestReset)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioSecondaryButton(
                    text = "Pull Remote",
                    icon = Icons.Rounded.CloudDownload,
                    enabled = settings.hasGitHubToken && settings.githubRepo.isNotBlank() && !state.isPushingOrCloningGit,
                    onClick = { onPullRemote(activeProj) }
                )
                StudioSecondaryButton(
                    text = "Push Changed",
                    icon = Icons.Rounded.CloudUpload,
                    enabled = settings.hasGitHubToken && settings.githubRepo.isNotBlank() && totalChanges > 0 && !state.isPushingOrCloningGit,
                    onClick = {
                        onPushRemote(activeProj, commitMsg)
                        commitMsg = ""
                    }
                )
            }
            if (state.isPushingOrCloningGit) {
                Spacer(Modifier.height(8.dp))
                TaskLoadingCard("Syncing with GitHub", "Transferring files over HTTPS Contents/Zipball API...")
            }
        }
    }

    // Stashes
    if (state.localGitStatus.stashes.isNotEmpty()) {
        item {
            Text("Stashes (${state.localGitStatus.stashes.size})", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.text)
        }
        items(state.localGitStatus.stashes) { st ->
            val p = StudioTheme.palette
            GlassCard(contentPadding = PaddingValues(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${st.id} — ${st.label}", style = MaterialTheme.typography.titleSmall, color = p.text)
                        Text("${st.branch} • ${st.fileCount} files", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    StudioSecondaryButton(text = "Pop", onClick = { onPopStash(activeProj, st.id) })
                }
            }
        }
    }

    // Staged & Unstaged Diffs
    if (state.localGitStatus.stagedFiles.isNotEmpty()) {
        item { Text("Staged Files", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.success) }
        items(state.localGitStatus.stagedFiles, key = { "st_${it.relativePath}" }) { diff ->
            val p = StudioTheme.palette
            GlassCard(glowAccent = p.success, contentPadding = PaddingValues(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(diff.relativePath, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(diff.changeType, p.success)
                        StudioSecondaryButton(text = "Unstage", onClick = { onUnstageFile(activeProj, diff.relativePath) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(diff.diffPreview, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
            }
        }
    }

    if (state.localGitStatus.unstagedFiles.isNotEmpty()) {
        item { Text("Unstaged Changes", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.warning) }
        items(state.localGitStatus.unstagedFiles, key = { "un_${it.relativePath}" }) { diff ->
            val p = StudioTheme.palette
            GlassCard(contentPadding = PaddingValues(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(diff.relativePath, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(diff.changeType, if (diff.changeType == "DELETED") p.error else p.warning)
                        StudioSecondaryButton(text = "Stage", onClick = { onStageFile(activeProj, diff.relativePath) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(diff.diffPreview, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
            }
        }
    }

    if (state.localGitCommits.isNotEmpty()) {
        item { Text("Local Commit History", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.text) }
        items(state.localGitCommits) { c ->
            val p = StudioTheme.palette
            GlassCard(contentPadding = PaddingValues(12.dp)) {
                Text("${c.id} [${c.branch}] — ${c.message}", style = MaterialTheme.typography.titleSmall, color = p.cyan)
                Text("${c.author} • ${c.fileCount} files", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
        }
    }
}

fun LazyListScope.workflowBuilderTabItems(
    state: StudioUiState,
    settings: AppSettingsState,
    activeProj: ProjectMetadata?,
    wfTemplate: WorkflowBuilderEngine.WorkflowTemplate,
    onSelectTemplate: (WorkflowBuilderEngine.WorkflowTemplate) -> Unit,
    wfYaml: String,
    onYamlChange: (String) -> Unit,
    enablePushTrigger: Boolean,
    onTogglePushTrigger: (Boolean) -> Unit,
    enablePrTrigger: Boolean,
    onTogglePrTrigger: (Boolean) -> Unit,
    includeTests: Boolean,
    onToggleIncludeTests: (Boolean) -> Unit,
    onPreviewAndSave: (ProjectMetadata, String, String) -> Unit
) {
    item {
        val p = StudioTheme.palette
        val readiness = remember(activeProj, settings.hasGitHubToken, settings.githubRepo, wfYaml) {
            WorkflowBuilderEngine.evaluateCiReadiness(
                project = activeProj,
                hasGitHubAuth = settings.hasGitHubToken,
                hasRepoSelected = settings.githubOwner.isNotBlank() && settings.githubRepo.isNotBlank(),
                yaml = wfYaml
            )
        }
        GlassCard(glowAccent = p.cyan) {
            Text("CI/CD Readiness Checklist", style = MaterialTheme.typography.titleMedium, color = p.text)
            Spacer(Modifier.height(6.dp))
            readiness.forEach { chk ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chk.label, style = MaterialTheme.typography.titleSmall, color = p.text)
                        Text(chk.detail, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    StatusPill(if (chk.passed) "Ready" else "Needed", if (chk.passed) p.success else p.warning)
                }
            }
        }
    }

    item {
        val p = StudioTheme.palette
        val audit = remember(wfYaml) { WorkflowBuilderEngine.auditWorkflow(wfYaml) }
        GlassCard(glowAccent = p.violet) {
            Text("Visual + YAML Workflow Builder", style = MaterialTheme.typography.titleMedium, color = p.text)
            Text("Basic structural & secret linter (not a full GitHub runner schema validator).", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkflowBuilderEngine.WorkflowTemplate.entries.forEach { tpl ->
                    SoftChip(
                        label = tpl.label,
                        selected = wfTemplate == tpl,
                        onClick = { onSelectTemplate(tpl) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-run on push (opt-in)", style = MaterialTheme.typography.bodySmall, color = p.text)
                    Text("Off by default to protect Actions minutes", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                SoftSwitch(checked = enablePushTrigger, onCheckedChange = onTogglePushTrigger)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Trigger on pull_request", style = MaterialTheme.typography.bodySmall, color = p.text)
                SoftSwitch(checked = enablePrTrigger, onCheckedChange = onTogglePrTrigger)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Include unit test step", style = MaterialTheme.typography.bodySmall, color = p.text)
                SoftSwitch(checked = includeTests, onCheckedChange = onToggleIncludeTests)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = if (audit.isValid) "Audit Passed" else "Fix Errors",
                    color = if (audit.isValid) p.success else p.error
                )
                if (audit.permissionsChecked) StatusPill("Least-Privilege Perms", p.cyan)
                if (audit.pinnedActionsChecked) StatusPill("Pinned @v4", p.success)
            }
            Spacer(Modifier.height(6.dp))
            audit.issues.forEach { iss ->
                val c = when (iss.severity) {
                    "ERROR" -> p.error
                    "WARNING" -> p.warning
                    else -> p.cyan
                }
                Text("• [${iss.severity}] ${iss.message}", style = MaterialTheme.typography.bodySmall, color = c)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = wfYaml,
                onValueChange = onYamlChange,
                label = { Text(".github/workflows/${wfTemplate.fileName}") },
                minLines = 10,
                modifier = Modifier.fillMaxWidth().testTag("workflow_yaml_editor")
            )
            Spacer(Modifier.height(10.dp))
            if (activeProj != null) {
                StudioPrimaryButton(
                    text = "Preview Diff & Save to ${activeProj.name}",
                    icon = Icons.Rounded.Save,
                    enabled = !audit.hasPullRequestSecretRisk,
                    onClick = { onPreviewAndSave(activeProj, wfTemplate.fileName, wfYaml) },
                    modifier = Modifier.fillMaxWidth().testTag("workflow_preview_save_button")
                )
            } else {
                Text("Create a project first to save into .github/workflows/.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
        }
    }

    item {
        val p = StudioTheme.palette
        GlassCard {
            Text("GitHub Secrets Setup Guide", style = MaterialTheme.typography.titleSmall, color = p.cyan)
            Spacer(Modifier.height(4.dp))
            Text(
                "Store release keystores in GitHub Repo Settings -> Secrets -> Actions as SIGNING_KEYSTORE_B64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, and SIGNING_KEY_PASSWORD. Never store private keys or passwords inside workflow YAML or pull_request triggers.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary
            )
        }
    }
}

fun LazyListScope.githubRemoteCiTabItems(
    state: StudioUiState,
    settings: AppSettingsState,
    onSaveGitHubConfig: (String, String, String, String, String) -> Unit,
    onDisconnectGitHub: () -> Unit,
    onStartDeviceFlow: (String) -> Unit,
    onPollDeviceToken: () -> Unit,
    onFetchAccountRepos: () -> Unit,
    onSelectRemoteRepo: (GitHubRemoteRepo) -> Unit,
    onRequestCreateRepo: () -> Unit,
    onCloneRepo: (String, String, String) -> Unit,
    onFetchGitHub: () -> Unit,
    onTogglePolling: (Boolean) -> Unit,
    onTriggerWorkflow: (Long) -> Unit,
    onRerunOrCancelRun: (Long, Boolean) -> Unit,
    onFetchRunLogs: (Long) -> Unit,
    onDownloadArtifact: (CiArtifactInfo) -> Unit,
    onInspectArtifactApk: (File) -> Unit,
    onDeleteArtifact: (DownloadedArtifactRecord) -> Unit,
    onRequestReleaseDialog: () -> Unit
) {
    item {
        val p = StudioTheme.palette
        val clipboard = LocalClipboardManager.current
        var owner by remember(settings.githubOwner) { mutableStateOf(settings.githubOwner) }
        var repo by remember(settings.githubRepo) { mutableStateOf(settings.githubRepo) }
        var branch by remember(settings.githubBranch) { mutableStateOf(settings.githubBranch) }
        var clientId by remember(settings.githubClientId) { mutableStateOf(settings.githubClientId) }
        var tokenInput by remember { mutableStateOf("") }

        GlassCard(glowAccent = if (settings.hasGitHubToken) p.cyan else p.warning) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (settings.hasGitHubToken) "Authorized (${state.githubAccount?.login ?: "Keystore Token"})" else "GitHub Not Connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = p.text
                    )
                    state.githubAccount?.scopes?.takeIf { it.isNotEmpty() }?.let { sc ->
                        Text("Scopes: ${sc.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = p.cyan)
                    }
                }
                state.githubRateLimitInfo?.let { StatusPill(it, p.cyan) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("GitHub OAuth App Client ID (Device Flow)") },
                placeholder = { Text("Ov23li...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                StudioSecondaryButton(
                    text = "Start Device Flow",
                    enabled = clientId.isNotBlank(),
                    onClick = { onStartDeviceFlow(clientId) }
                )
                if (settings.hasGitHubToken) {
                    StudioSecondaryButton(text = "Load Account & Repos", onClick = onFetchAccountRepos)
                    StudioSecondaryButton(text = "Disconnect", icon = Icons.Rounded.LinkOff, onClick = onDisconnectGitHub)
                }
            }
            state.githubDeviceCode?.let { dc ->
                Spacer(Modifier.height(8.dp))
                GlassCard(glowAccent = p.violet, contentPadding = PaddingValues(12.dp)) {
                    Text("User Code: ${dc.userCode}", style = MaterialTheme.typography.titleLarge, color = p.cyan)
                    Text("Open ${dc.verificationUri}, enter this code, then tap Check Token.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioSecondaryButton(text = "Copy Code", icon = Icons.Rounded.ContentCopy, onClick = { clipboard.setText(AnnotatedString(dc.userCode)) })
                        StudioPrimaryButton(
                            text = if (state.isPollingDeviceAuth) "Checking..." else "Check Token",
                            enabled = !state.isPollingDeviceAuth,
                            onClick = onPollDeviceToken
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner / Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repository") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text(if (settings.hasGitHubToken) "Replace PAT (Encrypted in Keystore)" else "Or Scoped PAT (repo, workflow)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                StudioPrimaryButton(
                    text = "Save",
                    onClick = {
                        onSaveGitHubConfig(owner, repo, branch, tokenInput, clientId)
                        tokenInput = ""
                    }
                )
                StudioSecondaryButton(
                    text = "Sync CI",
                    icon = Icons.Rounded.CloudSync,
                    enabled = owner.isNotBlank() && repo.isNotBlank() && (settings.hasGitHubToken || tokenInput.isNotBlank()),
                    onClick = {
                        onSaveGitHubConfig(owner, repo, branch, tokenInput, clientId)
                        tokenInput = ""
                        onFetchGitHub()
                    }
                )
                if (settings.hasGitHubToken) {
                    StudioSecondaryButton(text = "New Repo", icon = Icons.Rounded.Add, onClick = onRequestCreateRepo)
                    if (owner.isNotBlank() && repo.isNotBlank()) {
                        StudioSecondaryButton(
                            text = "Clone Repo",
                            icon = Icons.Rounded.Download,
                            enabled = !state.isPushingOrCloningGit,
                            onClick = { onCloneRepo(owner, repo, branch) }
                        )
                        StudioSecondaryButton(text = "Release", icon = Icons.Rounded.NewReleases, onClick = onRequestReleaseDialog)
                    }
                }
            }
        }
    }

    if (state.githubAccessibleRepos.isNotEmpty()) {
        item {
            Text("Accessible Repositories (${state.githubAccessibleRepos.size})", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.text)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.githubAccessibleRepos.forEach { r ->
                    SoftChip(
                        label = "${r.fullName} [${r.permissionsSummary}]",
                        selected = r.owner == settings.githubOwner && r.name == settings.githubRepo,
                        onClick = { onSelectRemoteRepo(r) }
                    )
                }
            }
        }
    }

    if (state.isLoadingGitCi || state.isPushingOrCloningGit) {
        item {
            TaskLoadingCard(
                stageLabel = if (state.isPushingOrCloningGit) "Cloning Repository" else "Syncing GitHub API",
                detailText = "Fetching verified REST responses over TLS..."
            )
        }
    }

    if (state.gitCiError != null) {
        item {
            ErrorStateView(title = "GitHub Sync Issue", reason = state.gitCiError, onRetry = onFetchGitHub)
        }
    }

    if (state.ciRunLogs.isNotEmpty()) {
        item {
            val p = StudioTheme.palette
            GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(12.dp)) {
                Text("Remote Job Steps / Logs", style = MaterialTheme.typography.titleSmall, color = p.cyan)
                state.ciRunLogs.forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.text)
                }
            }
        }
    }

    state.githubOverview?.let { ov ->
        item {
            val p = StudioTheme.palette
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ov.fullName, style = MaterialTheme.typography.titleLarge, color = p.text)
                        Text("${if (ov.isPrivate) "Private" else "Public"} • ${ov.defaultBranch} • ★ ${ov.stars} • Issues: ${ov.openIssues}", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    SoftChip(
                        label = if (state.isPollingCi) "Polling On" else "Auto Poll",
                        selected = state.isPollingCi,
                        onClick = { onTogglePolling(!state.isPollingCi) }
                    )
                }
            }
        }
        if (ov.workflows.isNotEmpty()) {
            item { Text("Workflows (${ov.workflows.size})", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.text) }
            items(ov.workflows) { wf ->
                val p = StudioTheme.palette
                GlassCard(contentPadding = PaddingValues(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(wf.name, style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text("${wf.path} • ${wf.state}", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                        }
                        StudioPrimaryButton(text = "Dispatch", icon = Icons.Rounded.PlayArrow, onClick = { onTriggerWorkflow(wf.id) })
                    }
                }
            }
        }
        if (ov.runs.isNotEmpty()) {
            item { Text("Workflow Runs (${ov.runs.size})", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.text) }
            items(ov.runs) { run ->
                val p = StudioTheme.palette
                val active = run.status == "queued" || run.status == "in_progress"
                val pillColor = when {
                    active -> p.cyan
                    run.conclusion == "success" -> p.success
                    run.conclusion == "failure" -> p.error
                    else -> p.warning
                }
                GlassCard(glowAccent = if (active) p.cyan else null, contentPadding = PaddingValues(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${run.runNumber} ${run.name}", style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text("${run.branch} • ${run.commitSha} • ${run.actor.ifBlank { run.createdAt }}", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                        StatusPill(run.conclusion ?: run.status, pillColor)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (active) {
                            StudioSecondaryButton(text = "Cancel", icon = Icons.Rounded.Cancel, onClick = { onRerunOrCancelRun(run.id, true) })
                        } else {
                            StudioSecondaryButton(text = "Rerun", icon = Icons.Rounded.Refresh, onClick = { onRerunOrCancelRun(run.id, false) })
                        }
                        StudioSecondaryButton(text = "Logs", icon = Icons.Rounded.Terminal, onClick = { onFetchRunLogs(run.id) })
                    }
                }
            }
        }
        if (ov.artifacts.isNotEmpty()) {
            item { Text("Remote CI Artifacts (${ov.artifacts.size})", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.text) }
            items(ov.artifacts) { art ->
                val p = StudioTheme.palette
                GlassCard(contentPadding = PaddingValues(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(art.name, style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text("${formatBytes(art.sizeBytes)} • Expired: ${art.expired}", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                        StudioPrimaryButton(
                            text = if (state.isDownloadingArtifact) "Wait..." else "Download",
                            icon = Icons.Rounded.Download,
                            enabled = !art.expired && !state.isDownloadingArtifact,
                            onClick = { onDownloadArtifact(art) }
                        )
                    }
                }
            }
        }
    }

    if (state.downloadedArtifacts.isNotEmpty()) {
        item {
            Text("Downloaded Artifacts (${state.downloadedArtifacts.size})", style = MaterialTheme.typography.titleMedium, color = StudioTheme.palette.cyan)
        }
        items(state.downloadedArtifacts, key = { it.localPath }) { rec ->
            DownloadedArtifactCard(
                record = rec,
                onInspectApk = onInspectArtifactApk,
                onDelete = { onDeleteArtifact(rec) }
            )
        }
    }
}

@Composable
private fun DownloadedArtifactCard(
    record: DownloadedArtifactRecord,
    onInspectApk: (File) -> Unit,
    onDelete: () -> Unit
) {
    val p = StudioTheme.palette
    val context = LocalContext.current
    GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(12.dp)) {
        Text(record.name, style = MaterialTheme.typography.titleSmall, color = p.text)
        Text("${formatBytes(record.sizeBytes)} • SHA-256: ${record.sha256.take(16)}...", style = MaterialTheme.typography.labelSmall, color = p.cyan)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            record.extractedApkPath?.let { apkPath ->
                StudioPrimaryButton(
                    text = "Inspect APK",
                    icon = Icons.Rounded.VerifiedUser,
                    onClick = { onInspectApk(File(apkPath)) }
                )
            }
            StudioSecondaryButton(
                text = "Share",
                icon = Icons.Rounded.Share,
                onClick = {
                    val f = File(record.extractedApkPath ?: record.localPath)
                    if (f.exists()) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (f.extension == "apk") "application/vnd.android.package-archive" else "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share ${f.name}"))
                    }
                }
            )
            StudioSecondaryButton(text = "Delete", icon = Icons.Rounded.Delete, onClick = onDelete)
        }
    }
}
