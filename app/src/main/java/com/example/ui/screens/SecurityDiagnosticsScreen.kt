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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.FindingSeverity
import com.example.model.SecurityFinding
import com.example.model.StudioEventLogger
import com.example.ui.components.GlassCard
import com.example.ui.components.ScannerBeamBar
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.io.File

@Composable
fun SecurityDiagnosticsScreen(
    state: StudioUiState,
    onRunAudit: () -> Unit,
    onApplyRepair: (SecurityFinding) -> Unit,
    onSetAppLockPin: (String, Int) -> Unit,
    onDisableAppLockPin: () -> Unit,
    onLockNow: () -> Unit,
    onExportPrivacyMetadata: () -> Unit,
    onWipeTempCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    BackHandler { onBack() }

    var repairPreviewTarget by remember { mutableStateOf<SecurityFinding?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state.securityFindings.isEmpty()) onRunAudit()
    }

    val runtime = remember { Runtime.getRuntime() }
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxMb = runtime.maxMemory() / (1024 * 1024)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("security_diagnostics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                Column {
                    Text("Security & Privacy Center", style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("App Lock PIN • Secret & Workflow Scan • Redaction", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }

        // App Lock PIN & Auto-Lock Policy Card
        item {
            val lock = state.appLockPolicy
            GlassCard(glowAccent = if (lock.enabled) p.success else p.violet, contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Lock PIN & Vault Protection", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            if (lock.enabled && lock.hasPinConfigured) "Enabled • Auto-lock: ${lock.autoLockSeconds}s • 5-attempt lockout"
                            else "Disabled • Protect workspace, signing & Git credentials",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary
                        )
                    }
                    StatusPill(if (lock.enabled) "Active" else "Off", if (lock.enabled) p.success else p.warning)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    StudioPrimaryButton(
                        text = if (lock.hasPinConfigured) "Change PIN" else "Set PIN",
                        icon = Icons.Rounded.Lock,
                        onClick = { showPinDialog = true },
                        modifier = Modifier.testTag("security_set_pin_button")
                    )
                    if (lock.enabled && lock.hasPinConfigured) {
                        StudioSecondaryButton(text = "Lock Now", onClick = onLockNow)
                        StudioSecondaryButton(text = "Disable PIN", onClick = onDisableAppLockPin)
                    }
                }
            }
        }

        // Runtime & Audit Trigger + Privacy Export & Temp Wipe
        item {
            GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Runtime Heap: $usedMb / $maxMb MB", style = MaterialTheme.typography.titleSmall, color = p.text)
                        Text("Declared Permissions: ${state.declaredPermissions.size}", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    StudioPrimaryButton(text = "Run Audit", icon = Icons.Rounded.Security, onClick = onRunAudit)
                }
                if (state.isScanningSecurity) {
                    Spacer(Modifier.height(8.dp))
                    ScannerBeamBar(isScanning = true)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    StudioSecondaryButton(text = "Export Privacy Summary", icon = Icons.Rounded.Download, onClick = onExportPrivacyMetadata)
                    StudioSecondaryButton(text = "Wipe Temp Caches", icon = Icons.Rounded.CleaningServices, onClick = onWipeTempCache)
                }
                state.lastPrivacyExportPath?.let { path ->
                    Spacer(Modifier.height(4.dp))
                    Text("Last export: ${File(path).name}", style = MaterialTheme.typography.labelSmall, color = p.cyan)
                }
            }
        }

        // Secret Scan False-Positive Notice + Redaction Preview
        item {
            val sampleRedacted = remember {
                StudioEventLogger.redact("Authorization: Bearer ghp_exampleToken1234567890abcd password=demoPass")
            }
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Text("Secret Scanner & Log Redaction Policy", style = MaterialTheme.typography.titleSmall, color = p.cyan)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Regex secret scanning checks source files, .env files, and .github/workflows/*.yml. Pattern matches may include false positives (example placeholders or test strings); review diffs before applying repairs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text("Redaction Sample: $sampleRedacted", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.success)
            }
        }

        // Threat Model Checklist
        if (state.threatModelChecklist.isNotEmpty()) {
            item {
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    Text("Security Architecture & Threat Model", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    state.threatModelChecklist.forEach { (title, desc) ->
                        Text("• $title: $desc", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        item {
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Text("App Permissions (Least-Privilege)", style = MaterialTheme.typography.titleMedium, color = p.text)
                Spacer(Modifier.height(6.dp))
                state.declaredPermissions.forEach { perm ->
                    Text("• $perm", style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMonoFontFamily, color = p.cyan)
                }
            }
        }

        item {
            Text("Audit Findings (${state.securityFindings.size})", style = MaterialTheme.typography.titleLarge, color = p.text)
        }

        items(state.securityFindings, key = { it.id }) { finding ->
            val col = when (finding.severity) {
                FindingSeverity.HIGH -> p.error
                FindingSeverity.MEDIUM -> p.warning
                FindingSeverity.LOW -> p.cyan
                FindingSeverity.PASS -> p.success
            }
            GlassCard(glowAccent = col, contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(finding.severity.label, col)
                    Text(finding.category, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Text(finding.title, style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(finding.description, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                if (finding.filePath != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${File(finding.filePath).name}:${finding.lineNumber ?: 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.cyan
                    )
                }
                if (finding.redactedPreview != null) {
                    Text(
                        finding.redactedPreview,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = p.warning
                    )
                }
                if (finding.canRepair) {
                    Spacer(Modifier.height(8.dp))
                    StudioSecondaryButton(
                        text = "Repair Preview",
                        icon = Icons.Rounded.AutoFixHigh,
                        onClick = { repairPreviewTarget = finding }
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var timeoutSec by remember { mutableIntStateOf(state.appLockPolicy.autoLockSeconds.takeIf { it >= 0 } ?: 300) }
        val mismatch = pin != confirmPin
        val invalidLen = pin.length !in 4..12 || !pin.all { it.isDigit() }
        StudioDialog(
            title = "Configure App Lock PIN",
            onDismiss = { showPinDialog = false },
            confirmText = "Save PIN",
            onConfirm = {
                if (!mismatch && !invalidLen) {
                    onSetAppLockPin(pin, timeoutSec)
                    showPinDialog = false
                }
            }
        ) {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) pin = it },
                label = { Text("Enter 4–12 Digit PIN") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) confirmPin = it },
                label = { Text("Confirm PIN") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (invalidLen && pin.isNotEmpty()) {
                Text("PIN must be 4 to 12 digits.", style = MaterialTheme.typography.bodySmall, color = p.error)
            } else if (mismatch && confirmPin.isNotEmpty()) {
                Text("PIN entries do not match.", style = MaterialTheme.typography.bodySmall, color = p.error)
            }
            Spacer(Modifier.height(8.dp))
            Text("Auto-Lock Timeout:", style = MaterialTheme.typography.titleSmall, color = p.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Immediate", 60 to "1 min", 300 to "5 min").forEach { (sec, label) ->
                    SoftChip(label = label, selected = timeoutSec == sec, onClick = { timeoutSec = sec })
                }
            }
        }
    }

    repairPreviewTarget?.let { finding ->
        StudioDialog(
            title = "Repair Preview",
            onDismiss = { repairPreviewTarget = null },
            confirmText = "Apply Repair",
            onConfirm = {
                onApplyRepair(finding)
                repairPreviewTarget = null
            }
        ) {
            Text("File: ${File(finding.filePath ?: "").name} (Line ${finding.lineNumber})", style = MaterialTheme.typography.labelLarge, color = p.cyan)
            Spacer(Modifier.height(8.dp))
            Text("- ${finding.repairOriginalLine?.trim()}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.error)
            Spacer(Modifier.height(4.dp))
            Text("+ ${finding.repairReplacementLine?.trim()}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.success)
        }
    }
}
