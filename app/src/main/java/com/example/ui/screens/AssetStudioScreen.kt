package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Radar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AssetStudioEngine
import com.example.data.TechnicalLtrText
import com.example.model.ProjectMetadata
import com.example.model.ResourceAuditItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.SoftSlider
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioBrandMark
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.formatBytes
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState

@Composable
fun AssetStudioScreen(
    state: StudioUiState,
    onResizeImageAsset: (Uri, ProjectMetadata, String, Int, Int, AssetStudioEngine.AssetOutputFormat) -> Unit,
    onGenerateCodedAsset: (ProjectMetadata, String, Int, Int, Int) -> Unit,
    onScanResources: (ProjectMetadata) -> Unit = {},
    onDeleteAsset: (ProjectMetadata, ResourceAuditItem) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    BackHandler { onBack() }

    var selectedProjId by remember(state.projects) { mutableStateOf(state.projects.firstOrNull()?.id) }
    val activeProj = state.projects.firstOrNull { it.id == selectedProjId }

    var assetName by remember { mutableStateOf("app_banner") }
    var targetSize by remember { mutableIntStateOf(512) }
    var quality by remember { mutableIntStateOf(90) }
    var format by remember { mutableStateOf(AssetStudioEngine.AssetOutputFormat.PNG) }
    var monogram by remember { mutableStateOf("TK") }
    var deleteTarget by remember { mutableStateOf<ResourceAuditItem?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && activeProj != null) {
            onResizeImageAsset(uri, activeProj, assetName, targetSize, quality, format)
        }
    }

    val sanitizedPreview = remember(assetName) {
        assetName.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "asset_item" }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("asset_studio_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                Column {
                    Text("Asset & Resource Studio", style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("Photo Picker • Resize/Convert • Coded Icon • Duplicate/Unused Audit", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }

        if (activeProj == null) {
            item {
                EmptyStateView(
                    title = "No Project Selected",
                    description = "Create a project first to save resized user images, generate icons, or audit project resources."
                )
            }
            return@LazyColumn
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
                        onClick = { selectedProjId = proj.id }
                    )
                }
            }
        }

        item {
            GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(16.dp)) {
                Text("User Image Converter & Resizer", style = MaterialTheme.typography.titleMedium, color = p.text)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = assetName,
                    onValueChange = { assetName = it },
                    label = { Text("Resource Base Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                StatusPill("Validated Android name: $sanitizedPreview.${format.ext}", p.cyan)
                Spacer(Modifier.height(8.dp))
                Text("Target Size: ${targetSize}x${targetSize} px", style = MaterialTheme.typography.titleSmall, color = p.text)
                SoftSlider(value = targetSize.toFloat(), onValueChange = { targetSize = it.toInt() }, valueRange = 64f..1024f)
                Text("Compression Quality: $quality%", style = MaterialTheme.typography.titleSmall, color = p.text)
                SoftSlider(value = quality.toFloat(), onValueChange = { quality = it.toInt() }, valueRange = 40f..100f)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssetStudioEngine.AssetOutputFormat.entries.forEach { fmt ->
                        SoftChip(label = fmt.label, selected = format == fmt, onClick = { format = fmt })
                    }
                }
                Spacer(Modifier.height(10.dp))
                StudioPrimaryButton(
                    text = "Pick & Convert Image",
                    icon = Icons.Rounded.Image,
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StudioBrandMark(size = 48.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Code-Drawn Icon / Splash Exporter", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text("Generates PNG into ${activeProj.name}/assets/ using active theme palette", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = monogram,
                    onValueChange = { monogram = it.take(2) },
                    label = { Text("2-Letter Monogram") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                StudioSecondaryButton(
                    text = "Generate ${targetSize}px PNG",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = {
                        onGenerateCodedAsset(activeProj, monogram, p.background.toArgb(), p.cyan.toArgb(), targetSize)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Part 5: Resource Scanner (Duplicate / Oversized / Unused Assets)
        item {
            GlassCard(glowAccent = p.violet, contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Duplicate, Large & Unused Asset Audit", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text("Finds SHA-256 duplicates, >250KB images, and unreferenced assets", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    StudioPrimaryButton(
                        text = "Scan",
                        icon = Icons.Rounded.Radar,
                        onClick = { onScanResources(activeProj) }
                    )
                }
            }
        }

        if (state.resourceAuditItems.isNotEmpty()) {
            items(state.resourceAuditItems, key = { "${it.issueType}_${it.relativePath}" }) { item ->
                val pillColor = when (item.issueType) {
                    "DUPLICATE" -> p.error
                    "LARGE" -> p.warning
                    else -> p.cyan
                }
                GlassCard(glowAccent = pillColor, contentPadding = PaddingValues(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(item.issueType, pillColor)
                                Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            TechnicalLtrText(text = item.relativePath, style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                        IconButton(onClick = { deleteTarget = item }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete asset ${item.relativePath}", tint = p.error)
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        activeProj?.let { proj ->
            StudioDialog(
                title = "Delete '${target.relativePath}'?",
                onDismiss = { deleteTarget = null },
                confirmText = "Delete Asset",
                isDestructive = true,
                onConfirm = {
                    onDeleteAsset(proj, target)
                    deleteTarget = null
                }
            ) {
                Text(
                    text = "Removes ${target.relativePath} (${formatBytes(target.sizeBytes)}) from ${proj.name}. Issue: ${target.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
            }
        }
    }
}
