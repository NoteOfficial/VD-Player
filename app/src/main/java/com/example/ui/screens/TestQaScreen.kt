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
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ProjectMetadata
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.formatBytes
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState

@Composable
fun TestQaScreen(
    state: StudioUiState,
    onRunQaSuite: () -> Unit,
    onScanProjectCodeMap: (ProjectMetadata) -> Unit,
    onToggleProjectPermission: (ProjectMetadata, String, Boolean) -> Unit,
    onCleanSafeCaches: () -> Unit,
    onBackupDatabase: () -> Unit,
    onClearIncrementalCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    BackHandler { onBack() }

    var selectedProjId by remember(state.projects) { mutableStateOf(state.projects.firstOrNull()?.id) }
    var showCachePreviewDialog by remember { mutableStateOf(false) }
    val activeProj = state.projects.firstOrNull { it.id == selectedProjId }

    LaunchedEffect(Unit) {
        if (state.qaResults.isEmpty()) onRunQaSuite()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("test_qa_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                Column {
                    Text("Test Center, Code Map & DB", style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("QA Suite • Code Map • Permissions • Cache & Storage Guard", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }

        // QA Suite Runner
        item {
            GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("System QA Verification", style = MaterialTheme.typography.titleMedium, color = p.text)
                    StudioPrimaryButton(text = "Run Suite", icon = Icons.Rounded.PlayArrow, onClick = onRunQaSuite)
                }
                Spacer(Modifier.height(8.dp))
                state.qaResults.forEach { res ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${res.suite}: ${res.title}", style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(res.detail, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                        StatusPill(if (res.passed) "PASS" else "WARN", if (res.passed) p.success else p.warning)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // Environment Health & Storage Cleanup (#15, #36, #22, #43)
        item {
            GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(14.dp)) {
                Text("Environment, Cache & Database", style = MaterialTheme.typography.titleMedium, color = p.text)
                Spacer(Modifier.height(4.dp))
                state.envHealth?.let { env ->
                    Text(env.statusSummary, style = MaterialTheme.typography.bodySmall, color = p.cyan)
                    Text("ABIs: ${env.supportedAbis.joinToString(", ")} • Safe Workers: ${env.recommendedMaxWorkers}", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                state.storageBreakdown?.let { sb ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Projects ${formatBytes(sb.projectsBytes)} • Builds ${formatBytes(sb.buildsBytes)} • Backups ${formatBytes(sb.backupsBytes)} • Temp Cache ${formatBytes(sb.cleanableCacheBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Incremental Cache Entries: ${state.incrementalCacheCount}", style = MaterialTheme.typography.labelSmall, color = p.text)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioPrimaryButton(text = "Clean Temp Cache", icon = Icons.Rounded.CleaningServices, onClick = { showCachePreviewDialog = true })
                    StudioSecondaryButton(text = "Backup Room DB", onClick = onBackupDatabase)
                    StudioSecondaryButton(text = "Purge Build Cache", onClick = onClearIncrementalCache)
                }
            }
        }

        // Code Map, Permission Analysis & Manifest Merge (#10, #11, #13, #16)
        if (activeProj != null) {
            item {
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Code Map & Permission Scan", style = MaterialTheme.typography.titleMedium, color = p.text)
                        StudioPrimaryButton(text = "Scan", icon = Icons.Rounded.Radar, onClick = { onScanProjectCodeMap(activeProj) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.projects.forEach { proj ->
                            SoftChip(label = proj.name, selected = proj.id == activeProj.id, onClick = { selectedProjId = proj.id })
                        }
                    }
                }
            }
        }

        state.codeMapReport?.let { map ->
            item {
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    Text("Code Map: ${map.projectName}", style = MaterialTheme.typography.titleMedium, color = p.cyan)
                    Text("Entry points: ${map.detectedEntryPoints.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = p.text)
                    Text("Imports/Refs (${map.importsAndRefs.size}) • Assets (${map.assets.size}) • Deps (${map.dependencies.size})", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    if (map.manifestValidationIssues.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        map.manifestValidationIssues.forEach { issue ->
                            Text("• $issue", style = MaterialTheme.typography.bodySmall, color = p.warning)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Merged Manifest Preview", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Text(map.mergedManifestPreview, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
                }
            }

            if (map.permissionSuggestions.isNotEmpty() && activeProj != null) {
                items(map.permissionSuggestions, key = { it.permission }) { sug ->
                    GlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sug.permission, style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text("${sug.sourceFile} (${sug.apiTrigger}) • Risk: ${sug.riskLevel}", style = MaterialTheme.typography.bodySmall, color = p.cyan)
                                Text(sug.reason, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                            }
                            SoftChip(
                                label = if (sug.isCurrentlyDeclared) "Remove" else "Add",
                                selected = sug.isCurrentlyDeclared,
                                onClick = { onToggleProjectPermission(activeProj, sug.permission, !sug.isCurrentlyDeclared) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCachePreviewDialog) {
        val sb = state.storageBreakdown
        com.example.ui.components.StudioDialog(
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
                    "APK cache: ${formatBytes(sb.apkCacheBytes)}\nExtracted files: ${formatBytes(sb.extractedBytes)}\nTotal cleanable: ${formatBytes(sb.cleanableCacheBytes)}\n\nSource projects, build outputs, snapshots, and signing keys stay intact."
                } else {
                    "Deletes temporary APK inspection cache only. Projects, backups, and keys stay safe."
                },
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary
            )
        }
    }
}
