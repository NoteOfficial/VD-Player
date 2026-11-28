package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ApkToolRepository
import com.example.model.ApkInspectionReport
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.TaskLoadingCard
import com.example.ui.components.VerificationCheckCanvas
import com.example.ui.components.formatBytes
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.io.File

@Composable
fun ApkToolScreen(
    state: StudioUiState,
    apkRepo: ApkToolRepository,
    onInspectSelfApk: () -> Unit,
    onInspectApkUri: (Uri, Boolean) -> Unit,
    onExtractPrimaryApk: () -> Unit,
    onNavigateSigningWizard: () -> Unit,
    onNavigateBuildCenter: () -> Unit,
    onShowMessage: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val context = LocalContext.current
    var inspectorTab by remember { mutableIntStateOf(0) }
    var compareMode by remember { mutableStateOf(false) }

    val primaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onInspectApkUri(uri, false)
    }
    val secondaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onInspectApkUri(uri, true)
    }

    val report = state.primaryApkReport
    val tabs = listOf("Overview", "Permissions", "Components", "Files", "Signature")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("apk_tool_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("APK Tool", style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("Inspector • Verify • Compare • Extract • Hash", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
                SoftChip(
                    label = if (compareMode) "Compare On" else "Compare",
                    selected = compareMode,
                    icon = Icons.Rounded.CompareArrows,
                    onClick = { compareMode = !compareMode }
                )
            }
        }

        // Hub Action Bar
        item {
            GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioPrimaryButton(
                        text = "Inspect This App",
                        icon = Icons.Rounded.Android,
                        onClick = onInspectSelfApk,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("apk_inspect_self_button")
                    )
                    StudioSecondaryButton(
                        text = "Pick APK",
                        icon = Icons.Rounded.FolderOpen,
                        onClick = { primaryPicker.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "*/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("apk_pick_file_button")
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoftChip(label = "Build Center", selected = false, onClick = onNavigateBuildCenter)
                    SoftChip(label = "Sign Keys", selected = false, icon = Icons.Rounded.VerifiedUser, onClick = onNavigateSigningWizard)
                    if (report != null) {
                        SoftChip(label = "Extract", selected = false, icon = Icons.Rounded.Unarchive, onClick = onExtractPrimaryApk)
                        SoftChip(
                            label = "Export/Share",
                            selected = false,
                            icon = Icons.Rounded.Share,
                            onClick = {
                                runCatching {
                                    val intent = apkRepo.createShareApkIntent(File(report.filePath))
                                    context.startActivity(Intent.createChooser(intent, "Share APK"))
                                }.onFailure { onShowMessage("Share unavailable: ${it.message}", true) }
                            }
                        )
                        if (report.isValidApk) {
                            SoftChip(
                                label = "Install Handoff",
                                selected = false,
                                icon = Icons.Rounded.InstallMobile,
                                onClick = {
                                    runCatching {
                                        val intent = apkRepo.createInstallHandoffIntent(File(report.filePath))
                                        context.startActivity(intent)
                                    }.onFailure { onShowMessage("System installer unavailable: ${it.message}", true) }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (state.isInspectingApk) {
            item {
                TaskLoadingCard(
                    stageLabel = state.apkTaskLabel,
                    detailText = "Streaming archive entries and computing cryptographic digests...",
                    progressFraction = state.apkTaskProgress
                )
            }
        }

        if (state.apkError != null) {
            item {
                ErrorStateView(
                    title = "APK Inspection Failed",
                    reason = state.apkError,
                    onRetry = onInspectSelfApk
                )
            }
        }

        // Compare Mode Section
        if (compareMode) {
            item {
                GlassCard(glowAccent = p.violet, contentPadding = PaddingValues(14.dp)) {
                    Text("Compare Two APKs", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StudioSecondaryButton(
                            text = report?.fileName?.take(14) ?: "Select Left APK",
                            onClick = { primaryPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        )
                        StudioSecondaryButton(
                            text = state.secondaryApkReport?.fileName?.take(14) ?: "Select Right APK",
                            onClick = { secondaryPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    state.apkComparison?.let { cmp ->
                        Spacer(Modifier.height(10.dp))
                        val sign = if (cmp.sizeDeltaBytes >= 0) "+" else ""
                        Text(
                            "Size Delta: $sign${formatBytes(cmp.sizeDeltaBytes)} • SHA Match: ${cmp.sha256Match} • Sig Match: ${cmp.signatureMatch}",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.cyan
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Added: ${cmp.addedFiles.size} • Removed: ${cmp.removedFiles.size} • Modified: ${cmp.modifiedFiles.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.text
                        )
                        if (cmp.addedPermissions.isNotEmpty() || cmp.removedPermissions.isNotEmpty()) {
                            Text(
                                "+Perms: ${cmp.addedPermissions.size} | -Perms: ${cmp.removedPermissions.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.warning
                            )
                        }
                    }
                }
            }
        }

        if (report == null) {
            item {
                EmptyStateView(
                    title = "No APK Loaded",
                    description = "Tap 'Inspect This App' to analyze the live TK Studio APK package, or pick any .apk archive from your device.",
                    actionLabel = "Inspect This App",
                    onAction = onInspectSelfApk
                )
            }
        } else {
            // 5 Inspector Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { idx, label ->
                        SoftChip(
                            label = label,
                            selected = inspectorTab == idx,
                            onClick = { inspectorTab = idx },
                            modifier = Modifier.testTag("apk_tab_${label.lowercase()}")
                        )
                    }
                }
            }

            when (inspectorTab) {
                0 -> item { ApkOverviewTab(report) }
                1 -> {
                    if (report.permissions.isEmpty()) {
                        item {
                            GlassCard {
                                Text("No requested permissions found in AndroidManifest.xml.", color = p.textSecondary)
                            }
                        }
                    } else {
                        items(report.permissions) { perm ->
                            GlassCard(contentPadding = PaddingValues(12.dp)) {
                                Text(perm, style = MaterialTheme.typography.bodyMedium, fontFamily = JetBrainsMonoFontFamily, color = p.text)
                            }
                        }
                    }
                }
                2 -> item { ApkComponentsTab(report) }
                3 -> {
                    items(report.entries.take(120), key = { it.path }) { entry ->
                        GlassCard(contentPadding = PaddingValues(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.path, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMonoFontFamily, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${entry.method} • ${formatBytes(entry.uncompressedSize)}", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                                }
                                StatusPill(
                                    text = if (entry.isAligned) "Aligned" else "Unaligned",
                                    color = if (entry.isAligned) p.success else p.warning
                                )
                            }
                        }
                    }
                }
                4 -> item { ApkSignatureTab(report) }
            }
        }
    }
}

@Composable
private fun ApkOverviewTab(report: ApkInspectionReport) {
    val p = StudioTheme.palette
    val clipboard = LocalClipboardManager.current
    val sdkCompat = remember(report.minSdk, report.targetSdk) {
        ApkToolRepository.assessSdkCompatibility(report.minSdk, report.targetSdk)
    }
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(report.fileName, style = MaterialTheme.typography.titleLarge, color = p.text)
                Text(report.packageName, style = MaterialTheme.typography.bodyMedium, color = p.cyan)
            }
            StatusPill(
                text = if (report.isValidApk) "Valid APK" else "Archive",
                color = if (report.isValidApk) p.success else p.warning
            )
        }
        Spacer(Modifier.height(10.dp))
        StatusPill(
            text = sdkCompat.statusLabel,
            color = if (sdkCompat.isInstallableOnDevice) p.success else p.error
        )
        Spacer(Modifier.height(4.dp))
        Text(sdkCompat.guidance, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        Spacer(Modifier.height(10.dp))
        InfoKV("Version", "${report.versionName} (code ${report.versionCode})")
        InfoKV("SDK", "minSdk ${report.minSdk ?: "?"} • targetSdk ${report.targetSdk ?: "?"}")
        InfoKV("Size", "${formatBytes(report.sizeBytes)} (${report.entries.size} files)")
        InfoKV("DEX / ABIs", "${report.dexCount} DEX • ${report.abis.ifEmpty { listOf("universal") }.joinToString(", ")}")
        InfoKV("ZipAlign", if (report.isZipAligned) "Verified" else "Unaligned STORED entries")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cryptographic Hashes", style = MaterialTheme.typography.titleSmall, color = p.text)
            SoftChip(
                label = "Copy SHA-256",
                selected = false,
                onClick = { clipboard.setText(AnnotatedString(report.sha256)) }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("SHA-256: ${report.sha256}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
        Text("SHA-1:   ${report.sha1}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
        Text("MD5:     ${report.md5}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
        if (report.parseNotes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            report.parseNotes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall, color = p.warning)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Toolchain Note: Binary AXML string-pool inspection, ZIP/alignment checks, hashes, and APK Signature v1/v2/v3 verification run natively on-device. Full smali/apktool decompilation and resource recompilation require external desktop/CI binaries and are not bundled in-app.",
            style = MaterialTheme.typography.labelSmall,
            color = p.textSecondary
        )
    }
}

@Composable
private fun ApkComponentsTab(report: ApkInspectionReport) {
    val p = StudioTheme.palette
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        ComponentGroup("Activities", report.activities)
        Spacer(Modifier.height(10.dp))
        ComponentGroup("Services", report.services)
        Spacer(Modifier.height(10.dp))
        ComponentGroup("Receivers", report.receivers)
        Spacer(Modifier.height(10.dp))
        ComponentGroup("Providers", report.providers)
        if (report.activities.isEmpty() && report.services.isEmpty() && report.receivers.isEmpty() && report.providers.isEmpty()) {
            Text("No exported/declared manifest components parsed.", color = p.textSecondary)
        }
    }
}

@Composable
private fun ComponentGroup(title: String, items: List<String>) {
    val p = StudioTheme.palette
    Text("$title (${items.size})", style = MaterialTheme.typography.titleSmall, color = p.cyan)
    items.take(20).forEach { c ->
        Text("• $c", style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMonoFontFamily, color = p.text)
    }
}

@Composable
private fun ApkSignatureTab(report: ApkInspectionReport) {
    val p = StudioTheme.palette
    GlassCard(
        glowAccent = if (report.isVerifiedSignature) p.success else p.warning,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VerificationCheckCanvas(verified = report.isVerifiedSignature, size = 44.dp)
            Column {
                Text(
                    if (report.isVerifiedSignature) "Signature Verified" else "Unsigned / Unverified",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )
                Text(
                    report.signatureSchemes.ifEmpty { listOf("No signature schemes found") }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        report.signingCerts.forEach { cert ->
            Text("Subject: ${cert.subject}", style = MaterialTheme.typography.bodySmall, color = p.text)
            Text("Algorithm: ${cert.algorithm}", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            Text("SHA-256: ${cert.sha256Fingerprint}", style = MaterialTheme.typography.labelSmall, color = p.cyan)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoKV(label: String, value: String) {
    val p = StudioTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = p.text)
    }
}
