package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.LocalizationEngine
import com.example.data.db.CrashRecoveryEntity
import com.example.model.AppSettingsState
import com.example.model.BackupSnapshot
import com.example.model.ProjectMetadata
import com.example.ui.components.BackupVaultCanvas
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.SoftSwitch
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.formatBytes
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupRevisionScreen(
    state: StudioUiState,
    settings: AppSettingsState,
    onCreateSnapshot: (ProjectMetadata, String, String?) -> Unit,
    onVerifyIntegrity: (BackupSnapshot) -> Unit,
    onPreviewDiff: (BackupSnapshot, ProjectMetadata, String?) -> Unit,
    onRestoreSnapshot: (BackupSnapshot, ProjectMetadata, String?) -> Unit,
    onDeleteSnapshot: (BackupSnapshot) -> Unit,
    onCreateFullBundle: (String, String?) -> Unit = { _, _ -> },
    onRestoreDraft: (CrashRecoveryEntity) -> Unit = {},
    onDismissDraft: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val str = remember(settings.languageCode) { LocalizationEngine.stringsFor(settings.languageCode) }
    BackHandler { onBack() }

    var selectedProjId by remember(state.projects) { mutableStateOf(state.projects.firstOrNull()?.id) }
    var snapName by remember { mutableStateOf("") }
    var encrypt by remember(settings.backupEncryptByDefault) { mutableStateOf(settings.backupEncryptByDefault) }
    var password by remember { mutableStateOf("") }

    var restoreTarget by remember { mutableStateOf<BackupSnapshot?>(null) }
    var restorePassword by remember { mutableStateOf("") }

    val selectedProj = state.projects.firstOrNull { it.id == selectedProjId }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("backup_revision_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str.backLabel, onClick = onBack)
                Column {
                    Text(str.backupRestore, style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("AES-256 Encryption • SHA-256 Integrity • Safe Rollback", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }

        if (state.recoveryDrafts.isNotEmpty()) {
            item {
                GlassCard(glowAccent = p.warning, contentPadding = PaddingValues(14.dp)) {
                    Text("Crash Recovery Drafts (${state.recoveryDrafts.size})", style = MaterialTheme.typography.titleMedium, color = p.warning)
                    Spacer(Modifier.height(6.dp))
                    state.recoveryDrafts.forEach { draft ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(File(draft.targetPath).name, style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text("${draft.payload.length} chars saved", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StudioPrimaryButton(text = "Restore", onClick = { onRestoreDraft(draft) })
                                StudioSecondaryButton(text = "Dismiss", onClick = { onDismissDraft(draft.id) })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        item {
            GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(14.dp)) {
                Text("Full Studio Bundle (All Projects + Settings)", style = MaterialTheme.typography.titleMedium, color = p.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Packages all ${state.projects.size} workspace(s) and sanitized studio_settings.json. Signing private keys and GitHub OAuth/PAT secrets are excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
                Spacer(Modifier.height(10.dp))
                StudioSecondaryButton(
                    text = "Export Full Studio Bundle",
                    icon = Icons.Rounded.Backup,
                    enabled = !state.isBackupBusy && (!encrypt || password.length >= 4),
                    onClick = { onCreateFullBundle("Studio Bundle", if (encrypt) password else null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (selectedProj != null) {
            item {
                GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BackupVaultCanvas(isActive = state.isBackupBusy, size = 52.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Create Workspace Snapshot", style = MaterialTheme.typography.titleMedium, color = p.text)
                            Text("Saves all files with SHA-256 verification", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.projects.forEach { proj ->
                            SoftChip(
                                label = proj.name,
                                selected = proj.id == selectedProj.id,
                                onClick = { selectedProjId = proj.id }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = snapName,
                        onValueChange = { snapName = it },
                        label = { Text("Snapshot Label") },
                        placeholder = { Text("Before UI refactor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Encrypt with AES-256-GCM", style = MaterialTheme.typography.bodyMedium, color = p.text)
                        SoftSwitch(checked = encrypt, onCheckedChange = { encrypt = it })
                    }
                    if (encrypt) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Encryption Password (min 4 chars)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    StudioPrimaryButton(
                        text = "Create Snapshot",
                        icon = Icons.Rounded.Backup,
                        enabled = !encrypt || password.length >= 4,
                        onClick = {
                            onCreateSnapshot(selectedProj, snapName, if (encrypt) password else null)
                            snapName = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        state.backupIntegrityMessage?.let { msg ->
            item {
                GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(12.dp)) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = p.cyan)
                }
            }
        }

        state.backupDiffPreview?.let { diff ->
            item {
                GlassCard(glowAccent = p.warning, contentPadding = PaddingValues(14.dp)) {
                    Text("Rollback Diff Preview (${diff.size} entries)", style = MaterialTheme.typography.titleSmall, color = p.warning)
                    Spacer(Modifier.height(6.dp))
                    diff.take(25).forEach { line ->
                        Text(line, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.text)
                    }
                }
            }
        }

        item {
            Text("Snapshots (${state.backups.size})", style = MaterialTheme.typography.titleLarge, color = p.text)
        }

        if (state.backups.isEmpty()) {
            item {
                EmptyStateView(
                    title = "No Snapshots Created",
                    description = "Create a project snapshot above to enable diffing, integrity checks, and rollback recovery."
                )
            }
        } else {
            items(state.backups, key = { it.id }) { snap ->
                val projForSnap = state.projects.firstOrNull { it.id == snap.projectId } ?: selectedProj
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(snap.name, style = MaterialTheme.typography.titleMedium, color = p.text)
                            Text(
                                "${snap.projectName} • ${snap.fileCount} files • ${formatBytes(snap.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textSecondary
                            )
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(snap.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = p.textSecondary
                            )
                        }
                        StatusPill(
                            text = if (snap.encrypted) "AES-256" else "SHA-256",
                            color = if (snap.encrypted) p.violet else p.cyan
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("SHA-256: ${snap.sha256.take(28)}...", style = MaterialTheme.typography.labelSmall, color = p.cyan)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudioSecondaryButton(
                            text = "Verify",
                            icon = Icons.Rounded.VerifiedUser,
                            onClick = { onVerifyIntegrity(snap) }
                        )
                        if (projForSnap != null) {
                            StudioPrimaryButton(
                                text = "Restore",
                                icon = Icons.Rounded.Restore,
                                onClick = {
                                    restorePassword = ""
                                    restoreTarget = snap
                                    if (!snap.encrypted) onPreviewDiff(snap, projForSnap, null)
                                }
                            )
                        }
                        IconButton(onClick = { onDeleteSnapshot(snap) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete snapshot", tint = p.error)
                        }
                    }
                }
            }
        }
    }

    restoreTarget?.let { snap ->
        val projForSnap = state.projects.firstOrNull { it.id == snap.projectId } ?: selectedProj
        if (projForSnap != null) {
            StudioDialog(
                title = "Restore '${snap.name}'?",
                onDismiss = { restoreTarget = null },
                confirmText = "Rollback Now",
                isDestructive = true,
                onConfirm = {
                    onRestoreSnapshot(snap, projForSnap, if (snap.encrypted) restorePassword else null)
                    restoreTarget = null
                }
            ) {
                Text(
                    "TK Studio will automatically save a pre-rollback snapshot of '${projForSnap.name}' before restoring ${snap.fileCount} files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                if (snap.encrypted) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Decryption Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
