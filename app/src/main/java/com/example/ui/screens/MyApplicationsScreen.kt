package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.ApkToolRepository
import com.example.data.LocalizationEngine
import com.example.data.TechnicalLtrText
import com.example.model.AppSettingsState
import com.example.model.MyApplicationRecord
import com.example.model.ProjectMetadata
import com.example.model.SigningContinuityState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSearchBar
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.io.File

@Composable
fun MyApplicationsScreen(
    state: StudioUiState,
    settings: AppSettingsState,
    apkRepo: ApkToolRepository,
    onOpenSourceProject: (ProjectMetadata) -> Unit,
    onEditCodeProject: (String) -> Unit,
    onUpgradeVersion: (String, String, String, String, Int) -> Unit,
    onPrepareRebuild: (MyApplicationRecord) -> Unit,
    onCompareApkFile: (File) -> Unit,
    onBackupProject: (ProjectMetadata) -> Unit,
    onOpenBuildHistory: () -> Unit,
    onNavigateBuildCenter: () -> Unit,
    onShowMessage: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val context = LocalContext.current
    val str = remember(settings.languageCode) { LocalizationEngine.stringsFor(settings.languageCode) }
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var upgradeTarget by remember { mutableStateOf<MyApplicationRecord?>(null) }
    var installConfirmTarget by remember { mutableStateOf<MyApplicationRecord?>(null) }

    val filtered = remember(state.myApplications, searchQuery) {
        if (searchQuery.isBlank()) state.myApplications
        else state.myApplications.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageId.contains(searchQuery, ignoreCase = true) ||
                it.sourceProjectName.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("my_applications_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    onClick = onBack
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(str.myApplications, style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text(
                        "${state.myApplications.size} tracked build/APK output(s) • Signing continuity guard",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.cyan
                    )
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

        if (filtered.isEmpty()) {
            item {
                EmptyStateView(
                    title = "No Built Applications Yet",
                    description = "Run a build in Build Center or download a CI APK artifact from GitHub Actions to track versions, hashes, and signing key continuity here.",
                    actionLabel = str.buildCenter,
                    onAction = onNavigateBuildCenter
                )
            }
        } else {
            items(filtered, key = { it.id }) { app ->
                val linkedProj = app.sourceProjectId?.let { id -> state.projects.firstOrNull { it.id == id } }
                val continuityColor = when (app.continuityState) {
                    SigningContinuityState.MATCHED -> p.success
                    SigningContinuityState.KEY_MISMATCH, SigningContinuityState.KEY_MISSING -> p.error
                    SigningContinuityState.UNSIGNED, SigningContinuityState.ARTIFACT_MISSING -> p.warning
                }
                val artifactFile = app.artifactPath?.let { File(it) }?.takeIf { it.exists() }
                val isApkArtifact = artifactFile?.extension.equals("apk", ignoreCase = true)

                GlassCard(
                    glowAccent = continuityColor,
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.testTag("my_app_card_${app.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Android, contentDescription = null, tint = p.cyan)
                            Column {
                                Text(app.appName, style = MaterialTheme.typography.titleMedium, color = p.text)
                                TechnicalLtrText(
                                    text = "${app.packageId} • v${app.versionName} (${app.versionCode})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = p.cyan
                                )
                            }
                        }
                        StatusPill(app.continuityState.label, continuityColor)
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Source: ${app.sourceProjectName} • ${app.buildType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.sourceProjectExists) p.textSecondary else p.warning
                    )
                    Text(
                        text = "Built: ${LocalizationEngine.formatShortDateTime(settings.languageCode, app.lastBuildAt)} • Kind: ${app.artifactKind}",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textSecondary
                    )
                    if (app.artifactPath != null) {
                        TechnicalLtrText(
                            text = if (app.artifactExists) {
                                "Artifact: ${File(app.artifactPath).name} • SHA-256 ${app.outputSha256?.take(14) ?: "—"}..."
                            } else {
                                "Artifact Missing: ${File(app.artifactPath).name}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (app.artifactExists) p.textSecondary else p.error
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (app.continuityState != SigningContinuityState.MATCHED) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = continuityColor)
                        }
                        Text(
                            text = app.continuityNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = continuityColor
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (linkedProj != null && app.sourceProjectExists) {
                            SoftChip(
                                label = "Open Source",
                                selected = false,
                                icon = Icons.Rounded.FolderOpen,
                                onClick = { onOpenSourceProject(linkedProj) }
                            )
                            SoftChip(
                                label = "Edit Code",
                                selected = false,
                                icon = Icons.Rounded.Code,
                                onClick = { onEditCodeProject(linkedProj.id) }
                            )
                            SoftChip(
                                label = "Upgrade",
                                selected = false,
                                icon = Icons.Rounded.Upgrade,
                                onClick = { upgradeTarget = app }
                            )
                            SoftChip(
                                label = "Rebuild",
                                selected = false,
                                icon = Icons.Rounded.Build,
                                onClick = {
                                    onPrepareRebuild(app)
                                    onNavigateBuildCenter()
                                }
                            )
                            SoftChip(
                                label = "Backup",
                                selected = false,
                                icon = Icons.Rounded.Backup,
                                onClick = { onBackupProject(linkedProj) }
                            )
                        }

                        if (artifactFile != null) {
                            if (isApkArtifact) {
                                SoftChip(
                                    label = "Compare / Inspect",
                                    selected = false,
                                    icon = Icons.Rounded.CompareArrows,
                                    onClick = { onCompareApkFile(artifactFile) }
                                )
                                SoftChip(
                                    label = "Install",
                                    selected = false,
                                    icon = Icons.Rounded.InstallMobile,
                                    onClick = {
                                        if (settings.apkConfirmInstall) {
                                            installConfirmTarget = app
                                        } else {
                                            launchApkInstallHandoff(context, apkRepo, artifactFile, onShowMessage)
                                        }
                                    }
                                )
                            }
                            SoftChip(
                                label = "Export",
                                selected = false,
                                icon = Icons.Rounded.Share,
                                onClick = {
                                    runCatching {
                                        val intent = if (isApkArtifact) {
                                            apkRepo.createShareApkIntent(artifactFile)
                                        } else {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", artifactFile)
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Export ${artifactFile.name}"))
                                    }.onFailure {
                                        onShowMessage("Export failed: ${it.message}", true)
                                    }
                                }
                            )
                        }

                        SoftChip(
                            label = "History",
                            selected = false,
                            icon = Icons.Rounded.History,
                            onClick = onOpenBuildHistory
                        )
                    }
                }
            }
        }
    }

    upgradeTarget?.let { target ->
        var appName by remember(target.id) { mutableStateOf(target.appName) }
        var pkg by remember(target.id) { mutableStateOf(target.packageId) }
        var verName by remember(target.id) { mutableStateOf(target.versionName) }
        var verCodeText by remember(target.id) { mutableStateOf((target.versionCode + 1).toString()) }

        StudioDialog(
            title = "Upgrade Version • ${target.appName}",
            onDismiss = { upgradeTarget = null },
            confirmText = "Save & Open Build Center",
            onConfirm = {
                val projId = target.sourceProjectId
                if (projId != null) {
                    val code = verCodeText.toIntOrNull()?.coerceAtLeast(1) ?: (target.versionCode + 1)
                    onUpgradeVersion(projId, appName, pkg, verName, code)
                    onPrepareRebuild(
                        target.copy(
                            appName = appName,
                            packageId = pkg,
                            versionName = verName,
                            versionCode = code - 1
                        )
                    )
                    upgradeTarget = null
                    onNavigateBuildCenter()
                } else {
                    upgradeTarget = null
                }
            }
        ) {
            if (target.continuityState != SigningContinuityState.MATCHED) {
                StatusPill(target.continuityNote, p.warning)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("Package ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = verName, onValueChange = { verName = it }, label = { Text("Version Name") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = verCodeText,
                    onValueChange = { if (it.all(Char::isDigit)) verCodeText = it },
                    label = { Text("Version Code") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    installConfirmTarget?.let { target ->
        val file = target.artifactPath?.let { File(it) }
        StudioDialog(
            title = "Install ${target.appName}?",
            onDismiss = { installConfirmTarget = null },
            confirmText = "Continue to Android Installer",
            onConfirm = {
                installConfirmTarget = null
                if (file != null && file.exists()) {
                    launchApkInstallHandoff(context, apkRepo, file, onShowMessage)
                }
            }
        ) {
            Text(
                text = "Package: ${target.packageId} • v${target.versionName} (${target.versionCode})\nSigning status: ${target.continuityState.label}\n${target.continuityNote}",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary
            )
        }
    }
}

private fun launchApkInstallHandoff(
    context: android.content.Context,
    apkRepo: ApkToolRepository,
    file: File,
    onShowMessage: (String, Boolean) -> Unit
) {
    runCatching {
        context.startActivity(apkRepo.createInstallHandoffIntent(file))
    }.onFailure { err ->
        onShowMessage("System installer handoff failed: ${err.message}", true)
    }
}
