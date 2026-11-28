package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.AutoBuildProposal
import com.example.model.BuildRecord
import com.example.model.BuildReportFormat
import com.example.model.NativeCompatibilityReport
import com.example.model.WebWrapperPreview
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme

@Composable
fun AutoBuildPlanDialog(
    proposal: AutoBuildProposal,
    onDismiss: () -> Unit,
    onConfirmAndBuild: () -> Unit
) {
    val p = StudioTheme.palette
    StudioDialog(
        title = "Auto Build Plan & Config Diff",
        onDismiss = onDismiss,
        confirmText = "Confirm & Build",
        onConfirm = onConfirmAndBuild
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(
                text = proposal.targetArtifactLabel,
                color = if (proposal.canCompileBinaryApkLocally) p.success else p.cyan
            )
            Text(proposal.summaryNote, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            if (proposal.diffItems.isEmpty()) {
                Text("No configuration overrides needed; existing manifest & config match target.", style = MaterialTheme.typography.bodySmall, color = p.success)
            } else {
                Text("Proposed Changes (${proposal.diffItems.size}):", style = MaterialTheme.typography.titleSmall, color = p.text)
                proposal.diffItems.forEach { d ->
                    Text(
                        "• ${d.setting}: '${d.currentValue}' → '${d.proposedValue}' (${d.reason})",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = p.cyan
                    )
                }
            }
        }
    }
}

@Composable
fun WebWrapperPreviewDialog(
    preview: WebWrapperPreview,
    onDismiss: () -> Unit
) {
    val p = StudioTheme.palette
    var tab by remember { mutableIntStateOf(0) }
    StudioDialog(
        title = "Web-to-APK Wrapper Preview",
        onDismiss = onDismiss,
        confirmText = "Close",
        onConfirm = onDismiss
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SoftChip("MainActivity.kt", tab == 0, onClick = { tab = 0 })
                SoftChip("Manifest.xml", tab == 1, onClick = { tab = 1 })
                SoftChip("Security & Limits", tab == 2, onClick = { tab = 2 })
            }
            when (tab) {
                0 -> Text(
                    preview.generatedKotlin,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = p.cyan
                )
                1 -> Text(
                    preview.generatedManifest,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = p.text
                )
                else -> {
                    Text("Hardened WebView Defaults:", style = MaterialTheme.typography.titleSmall, color = p.success)
                    preview.securityDefaults.forEach { sec ->
                        Text("✓ $sec", style = MaterialTheme.typography.bodySmall, color = p.text)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Framework & Runtime Scope:", style = MaterialTheme.typography.titleSmall, color = p.warning)
                    preview.frameworkLimitations.forEach { lim ->
                        Text("• $lim", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    if (preview.missingAssets.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Missing Local Asset Refs:", style = MaterialTheme.typography.titleSmall, color = p.error)
                        preview.missingAssets.forEach { ref ->
                            Text("✗ $ref", style = MaterialTheme.typography.labelSmall, color = p.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NativeCompatDialog(
    report: NativeCompatibilityReport,
    onApplySafeRepair: () -> Unit,
    onOpenToolchain: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = StudioTheme.palette
    StudioDialog(
        title = "Native Android Compatibility",
        onDismiss = onDismiss,
        confirmText = if (report.canSafeRepairManifest) "Apply Safe Manifest Repair" else "Open Toolchain",
        onConfirm = {
            if (report.canSafeRepairManifest) onApplySafeRepair() else onOpenToolchain()
        }
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(
                text = if (report.isLocallyCompilable) "CLI Tools Present" else "Verified Source / CI Mode Only",
                color = if (report.isLocallyCompilable) p.success else p.warning
            )
            Text(report.recommendation, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            if (report.missingToolchainItems.isNotEmpty()) {
                Text(
                    "Missing SDK Binaries: ${report.missingToolchainItems.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = p.warning
                )
            }
            if (report.detectedPlugins.isNotEmpty()) {
                Text(
                    "Gradle Plugins (${report.detectedPlugins.size}): ${report.detectedPlugins.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.cyan
                )
            }
            if (report.issues.isNotEmpty()) {
                Text("Diagnostics (${report.issues.size}):", style = MaterialTheme.typography.titleSmall, color = p.text)
                report.issues.forEach { iss ->
                    val col = when (iss.severity) {
                        "BLOCKING" -> p.error
                        "WARNING" -> p.warning
                        else -> p.textSecondary
                    }
                    val loc = iss.line?.let { "${iss.file}:$it" } ?: iss.file
                    Text(
                        "[${iss.severity}] $loc — ${iss.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = col
                    )
                }
            }
            if (report.canSafeRepairManifest) {
                Text(
                    "Safe Repair creates a timestamped .bak backup before adding android:exported or disabling cleartext traffic.",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.success
                )
            }
            StudioSecondaryButton(text = "Inspect Toolchain & SDK", onClick = onOpenToolchain)
        }
    }
}

@Composable
fun BuildReportDialog(
    record: BuildRecord,
    onExport: (BuildReportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val p = StudioTheme.palette
    StudioDialog(
        title = "Build Report #${record.reportId}",
        onDismiss = onDismiss,
        confirmText = "Done",
        onConfirm = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${record.projectName} • ${record.mode.label} • ${record.profile.label}",
                style = MaterialTheme.typography.titleSmall,
                color = p.text
            )
            Text(
                "Status: ${record.stage.label} (${record.durationMs}ms) • Artifact: ${record.artifactKind}",
                style = MaterialTheme.typography.bodySmall,
                color = if (record.success) p.success else p.error
            )
            record.errorCode?.let { code ->
                Text(
                    "Error: $code at ${record.failedStage ?: "Pipeline"} ${record.sourceLocation?.let { "($it)" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.error
                )
            }
            Text("Export Redacted Report:", style = MaterialTheme.typography.labelLarge, color = p.cyan)
            BuildReportFormat.entries.forEach { fmt ->
                StudioSecondaryButton(
                    text = "Export ${fmt.label}",
                    onClick = { onExport(fmt) }
                )
            }
        }
    }
}

@Composable
fun BuildSigningUnlockDialog(
    alias: String,
    onDismiss: () -> Unit,
    onUnlockAndBuild: (String) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    StudioDialog(
        title = "Unlock Signing Key '$alias'",
        onDismiss = onDismiss,
        confirmText = "Sign & Build",
        onConfirm = { onUnlockAndBuild(pwd) }
    ) {
        Text(
            "Enter keystore password in memory for this build only. Password is never saved to build logs or reports.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioTheme.palette.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pwd,
            onValueChange = { pwd = it },
            label = { Text("Keystore Password") },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password"
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
