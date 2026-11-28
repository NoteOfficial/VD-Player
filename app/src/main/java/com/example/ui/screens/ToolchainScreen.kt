package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassCard
import com.example.ui.components.ScannerBeamBar
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState

@Composable
fun ToolchainScreen(
    state: StudioUiState,
    onRefresh: () -> Unit,
    onSelfTest: (String) -> Unit,
    onImportTool: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    BackHandler { onBack() }

    val toolPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportTool(uri)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("toolchain_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                    Column {
                        Text("Toolchain & Environment", style = MaterialTheme.typography.headlineSmall, color = p.text)
                        Text("Honest SDK inventory, ABI, licenses & self-tests", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
                StudioIconButton(icon = Icons.Rounded.Refresh, contentDescription = "Rescan toolchain", onClick = onRefresh)
            }
        }

        // Completeness + Device RAM / Storage / ABI Card
        state.toolchainEnvReport?.let { env ->
            item {
                GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Environment Completeness", style = MaterialTheme.typography.titleMedium, color = p.text)
                        StatusPill(
                            text = "${env.readyCount}/${env.totalCount} (${env.completenessPercent}%)",
                            color = if (env.readyCount == env.totalCount) p.success else p.cyan
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { env.completenessPercent / 100f },
                        color = p.cyan,
                        trackColor = p.raised,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ABIs: ${env.supportedAbis.joinToString(", ")} • Heap: ${env.maxHeapMb}MB • Free RAM: ${env.availableRamMb}MB • Storage: ${env.freeStorageMb}MB",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = p.cyan
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(env.summaryNote, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StudioPrimaryButton(
                    text = "Import Tool / JAR",
                    icon = Icons.Rounded.UploadFile,
                    onClick = { toolPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_tool_button")
                )
                StudioSecondaryButton(
                    text = "Verify All",
                    onClick = onRefresh,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("verify_toolchain_button")
                )
            }
        }

        if (state.isScanningToolchain) {
            item { ScannerBeamBar(isScanning = true) }
        }

        items(state.toolchainItems, key = { it.id }) { tool ->
            GlassCard(
                glowAccent = if (tool.isAvailable) p.success else p.warning,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tool.name, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text("${tool.category} • ${tool.version}", style = MaterialTheme.typography.bodySmall, color = p.cyan)
                    }
                    StatusPill(
                        text = if (tool.isAvailable) "Ready" else "Missing",
                        color = if (tool.isAvailable) p.success else p.warning
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Path:    ${tool.path}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
                Text("ABI:     ${tool.abi} • Exec: ${if (tool.executable) "Yes" else "No"}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
                Text("License: ${tool.licenseInfo}", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                Text("Compat:  ${tool.compatibilityNote}", style = MaterialTheme.typography.labelSmall, color = if (tool.isAvailable) p.success else p.warning)
                Text("Hash:    ${tool.sha256.take(28)}...", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
                Spacer(Modifier.height(4.dp))
                Text(tool.details, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                if (tool.missingGuidance.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Setup: ${tool.missingGuidance}", style = MaterialTheme.typography.bodySmall, color = p.warning)
                }
                tool.lastTestResult?.let { res ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        res,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (res.startsWith("PASS")) p.success else p.warning
                    )
                }
                Spacer(Modifier.height(8.dp))
                StudioSecondaryButton(
                    text = "Run Self-Test",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { onSelfTest(tool.id) },
                    modifier = Modifier.testTag("selftest_${tool.id}")
                )
            }
        }
    }
}
