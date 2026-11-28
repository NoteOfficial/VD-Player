package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.TechnicalLtrText
import com.example.data.UtilityToolsEngine
import com.example.data.ZipInspectSummary
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.formatBytes
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme

@Composable
fun UtilityToolsScreen(
    onInspectZipUri: (Uri, (Result<ZipInspectSummary>) -> Unit) -> Unit = { _, cb ->
        cb(Result.failure(IllegalStateException("ZIP inspector handler unavailable")))
    },
    onExportOutputUri: (Uri, String) -> Unit = { _, _ -> },
    onShowMessage: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val clipboard = LocalClipboardManager.current
    BackHandler { onBack() }

    val tools = listOf("JSON / XML", "Color", "Hashes", "Regex", "Text Diff", "Base64 / URL", "ZIP Explorer")
    var activeTool by remember { mutableIntStateOf(0) }

    var primaryInput by remember { mutableStateOf("{\"studio\":\"TK Studio\",\"version\":\"0.6.8\"}") }
    var secondaryInput by remember { mutableStateOf("{\"studio\":\"TK Studio\",\"version\":\"0.6.9\"}") }
    var outputText by remember { mutableStateOf("") }
    var zipSummary by remember { mutableStateOf<ZipInspectSummary?>(null) }
    var zipError by remember { mutableStateOf<String?>(null) }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            zipError = null
            onInspectZipUri(uri) { res ->
                res.onSuccess { summary ->
                    zipSummary = summary
                    outputText = summary.entries.joinToString("\n") {
                        "${if (it.isUnsafePath) "[UNSAFE] " else ""}${it.name} (${formatBytes(it.uncompressedBytes)})"
                    }
                }.onFailure { err ->
                    zipSummary = null
                    zipError = err.message ?: "Unable to read ZIP"
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && outputText.isNotBlank()) {
            onExportOutputUri(uri, outputText)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("utility_tools_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                Column {
                    Text("Developer Utilities", style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("JSON • XML • Color • Hash • Regex • Diff • Base64 • ZIP Explorer", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tools.forEachIndexed { idx, label ->
                    SoftChip(
                        label = label,
                        selected = activeTool == idx,
                        onClick = {
                            activeTool = idx
                            outputText = ""
                            zipError = null
                        }
                    )
                }
            }
        }

        item {
            GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(16.dp)) {
                when (activeTool) {
                    0 -> {
                        OutlinedTextField(
                            value = primaryInput,
                            onValueChange = { primaryInput = it },
                            label = { Text("JSON or XML Input") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudioPrimaryButton(text = "Format JSON", onClick = {
                                outputText = UtilityToolsEngine.formatJson(primaryInput).getOrElse { "JSON Error: ${it.message}" }
                            })
                            StudioSecondaryButton(text = "Validate XML", onClick = {
                                outputText = UtilityToolsEngine.validateXml(primaryInput).getOrElse { "XML Error: ${it.message}" }
                            })
                            StudioSecondaryButton(text = "Pretty XML", onClick = {
                                outputText = UtilityToolsEngine.formatXmlPretty(primaryInput).getOrElse { "XML Error: ${it.message}" }
                            })
                        }
                    }
                    1 -> {
                        var hex by remember { mutableStateOf("#4169FF") }
                        val parsedColor = remember(hex) {
                            runCatching {
                                val clean = hex.trim().removePrefix("#")
                                Color(android.graphics.Color.parseColor("#$clean"))
                            }.getOrNull() ?: p.primary
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(parsedColor)
                                    .border(1.dp, p.border, RoundedCornerShape(12.dp))
                            )
                            OutlinedTextField(
                                value = hex,
                                onValueChange = { hex = it },
                                label = { Text("HEX Color (#RRGGBB)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        StudioPrimaryButton(text = "Convert HEX ↔ RGB ↔ HSL", onClick = {
                            outputText = UtilityToolsEngine.convertHexColor(hex).getOrElse { it.message ?: "Invalid color" }
                        })
                    }
                    2 -> {
                        OutlinedTextField(
                            value = primaryInput,
                            onValueChange = { primaryInput = it },
                            label = { Text("Input Text for Digests") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        StudioPrimaryButton(text = "Compute SHA-256 / SHA-1 / MD5", onClick = {
                            outputText = UtilityToolsEngine.computeHashes(primaryInput)
                        })
                    }
                    3 -> {
                        var pattern by remember { mutableStateOf("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b") }
                        var sample by remember { mutableStateOf("Contact tkofficial68@gmail.com or admin@tkstudio.dev") }
                        OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("Regex Pattern") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = sample, onValueChange = { sample = it }, label = { Text("Test Input") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        StudioPrimaryButton(text = "Run Regex", onClick = {
                            outputText = UtilityToolsEngine.testRegex(pattern, sample).getOrElse { "Regex Error: ${it.message}" }
                        })
                    }
                    4 -> {
                        OutlinedTextField(value = primaryInput, onValueChange = { primaryInput = it }, label = { Text("Original Text") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = secondaryInput, onValueChange = { secondaryInput = it }, label = { Text("Modified Text") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        StudioPrimaryButton(text = "Compute Line Diff", onClick = {
                            outputText = UtilityToolsEngine.diffTexts(primaryInput, secondaryInput)
                        })
                    }
                    5 -> {
                        OutlinedTextField(value = primaryInput, onValueChange = { primaryInput = it }, label = { Text("Payload") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            StudioPrimaryButton(text = "B64 Encode", onClick = { outputText = UtilityToolsEngine.base64Encode(primaryInput) })
                            StudioSecondaryButton(text = "B64 Decode", onClick = {
                                outputText = UtilityToolsEngine.base64Decode(primaryInput).getOrElse { "Decode error: ${it.message}" }
                            })
                            StudioSecondaryButton(text = "URL Encode", onClick = { outputText = UtilityToolsEngine.urlEncode(primaryInput) })
                            StudioSecondaryButton(text = "URL Decode", onClick = {
                                outputText = UtilityToolsEngine.urlDecode(primaryInput).getOrElse { "Decode error: ${it.message}" }
                            })
                        }
                    }
                    6 -> {
                        Text("Inspect any ZIP archive safely in memory without writing extracted files to disk.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        Spacer(Modifier.height(10.dp))
                        StudioPrimaryButton(
                            text = "Pick ZIP to Inspect",
                            icon = Icons.Rounded.FolderZip,
                            onClick = { zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        zipError?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            StatusPill(err, p.error)
                        }
                        zipSummary?.let { summary ->
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill("${summary.entryCount} entries", p.cyan)
                                StatusPill(formatBytes(summary.totalUncompressedBytes), p.primary)
                                StatusPill(
                                    text = if (summary.unsafeEntries.isEmpty()) "Path Safe" else "${summary.unsafeEntries.size} Unsafe Paths!",
                                    color = if (summary.unsafeEntries.isEmpty()) p.success else p.error
                                )
                            }
                        }
                    }
                }
                if (outputText.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Output", style = MaterialTheme.typography.titleSmall, color = p.cyan)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioSecondaryButton(
                                text = "Copy",
                                icon = Icons.Rounded.ContentCopy,
                                onClick = {
                                    clipboard.setText(AnnotatedString(outputText))
                                    onShowMessage("Copied utility output to clipboard")
                                }
                            )
                            StudioSecondaryButton(
                                text = "Export",
                                icon = Icons.Rounded.Download,
                                onClick = { exportLauncher.launch("tk_utility_output.txt") }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TechnicalLtrText(
                        text = outputText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = p.text
                    )
                }
            }
        }
    }
}
