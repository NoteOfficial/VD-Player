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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.model.BuildConfigSpec
import com.example.model.BuildMode
import com.example.model.BuildProfile
import com.example.model.BuildRecord
import com.example.model.BuildReportFormat
import com.example.model.ProjectType
import com.example.ui.components.CircuitBuildLoader
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.TaskLoadingCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState

@Composable
fun BuildCenterScreen(
    state: StudioUiState,
    onSelectProject: (String) -> Unit,
    onSelectMode: (BuildMode) -> Unit,
    onSelectProfile: (BuildProfile) -> Unit,
    onUpdateConfig: (BuildConfigSpec) -> Unit,
    onApplyAutoConfig: () -> Unit,
    onApplyNativeSafeRepair: (String) -> Unit,
    onRefreshPreflight: () -> Unit,
    onStartBuild: (signingPassword: String) -> Unit,
    onCancelBuild: () -> Unit,
    onExportReport: (BuildRecord, BuildReportFormat) -> Unit,
    onOpenToolchain: () -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val clipboard = LocalClipboardManager.current
    BackHandler { onBack() }

    LaunchedEffect(state.selectedBuildProjectId) {
        onRefreshPreflight()
    }

    val selectedProj = state.projects.firstOrNull { it.id == state.selectedBuildProjectId }
    val cfg = state.buildConfigSpec

    var showAutoPlanDialog by remember { mutableStateOf(false) }
    var showWebWrapperDialog by remember { mutableStateOf(false) }
    var showNativeCompatDialog by remember { mutableStateOf(false) }
    var reportDialogRecord by remember { mutableStateOf<BuildRecord?>(null) }
    var showPasswordUnlockDialog by remember { mutableStateOf(false) }

    var showAllSteps by remember { mutableStateOf(false) }
    var logFilter by remember { mutableStateOf("ALL") }
    var logSearch by remember { mutableStateOf("") }

    fun triggerBuildFlow() {
        val activeCfg = if (state.selectedBuildMode == BuildMode.AUTO) {
            state.autoBuildProposal?.proposedConfig ?: cfg
        } else cfg
        if (activeCfg.signingAlias.isNotBlank()) {
            showPasswordUnlockDialog = true
        } else {
            onStartBuild("")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("build_center_screen"),
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
                        Text("Build Center", style = MaterialTheme.typography.headlineSmall, color = p.text)
                        Text("18-Step Pipeline • Web-to-APK • Compatibility", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
                StudioIconButton(icon = Icons.Rounded.Build, contentDescription = "Open Toolchain", onClick = onOpenToolchain)
            }
        }

        if (state.projects.isEmpty()) {
            item {
                EmptyStateView(
                    title = "No Projects Available",
                    description = "Create or import a project before running the Build Center pipeline."
                )
            }
            return@LazyColumn
        }

        // Project Selector
        item {
            Text("Target Project", style = MaterialTheme.typography.titleMedium, color = p.text)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.projects.filterNot { it.isArchived }.forEach { proj ->
                    SoftChip(
                        label = "${proj.name} (${proj.type.badge})",
                        selected = proj.id == selectedProj?.id,
                        onClick = { onSelectProject(proj.id) }
                    )
                }
            }
        }

        // Build Mode Selector (Auto / Manual / Custom)
        item {
            Text("Build Mode", style = MaterialTheme.typography.titleMedium, color = p.text)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BuildMode.entries.forEach { mode ->
                    SoftChip(
                        label = mode.label,
                        selected = state.selectedBuildMode == mode,
                        onClick = { onSelectMode(mode) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(state.selectedBuildMode.description, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }

        // Profile Selector (Real mapped settings)
        item {
            Text("Build Profile", style = MaterialTheme.typography.titleMedium, color = p.text)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BuildProfile.entries.forEach { prof ->
                    SoftChip(
                        label = prof.label,
                        selected = state.selectedBuildProfile == prof,
                        onClick = { onSelectProfile(prof) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val prof = state.selectedBuildProfile
            Text(
                "${prof.description} (Workers: ${prof.workers}, Cache: ${if (prof.cleanCacheFirst) "Clean" else if (prof.useIncrementalCache) "Incremental" else "Off"})",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary
            )
        }

        // Mode-Specific Config Card (Auto Proposal vs Manual/Custom Form)
        item {
            if (state.selectedBuildMode == BuildMode.AUTO) {
                state.autoBuildProposal?.let { prop ->
                    GlassCard(glowAccent = p.cyan, contentPadding = PaddingValues(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto Build Analysis", style = MaterialTheme.typography.titleMedium, color = p.text)
                            StatusPill(
                                text = prop.detectedType.badge,
                                color = if (prop.canCompileBinaryApkLocally) p.success else p.cyan
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(prop.targetArtifactLabel, style = MaterialTheme.typography.bodySmall, color = p.cyan)
                        Spacer(Modifier.height(4.dp))
                        Text(prop.summaryNote, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioSecondaryButton(
                                text = "Review Plan (${prop.diffItems.size} diffs)",
                                icon = Icons.Rounded.Description,
                                onClick = { showAutoPlanDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                            StudioSecondaryButton(
                                text = "Apply Config",
                                onClick = onApplyAutoConfig,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(14.dp)) {
                    Text("${state.selectedBuildMode.label} Configuration", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cfg.appName,
                        onValueChange = { onUpdateConfig(cfg.copy(appName = it)) },
                        label = { Text("Application Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cfg.packageId,
                        onValueChange = { onUpdateConfig(cfg.copy(packageId = it)) },
                        label = { Text("Android Package ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cfg.versionName,
                            onValueChange = { onUpdateConfig(cfg.copy(versionName = it)) },
                            label = { Text("Version Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cfg.versionCode.toString(),
                            onValueChange = { v -> onUpdateConfig(cfg.copy(versionCode = v.toIntOrNull() ?: cfg.versionCode)) },
                            label = { Text("Version Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cfg.minSdk.toString(),
                            onValueChange = { v -> onUpdateConfig(cfg.copy(minSdk = (v.toIntOrNull() ?: 24).coerceIn(21, 36))) },
                            label = { Text("minSdk") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cfg.targetSdk.toString(),
                            onValueChange = { v -> onUpdateConfig(cfg.copy(targetSdk = (v.toIntOrNull() ?: 36).coerceIn(24, 36))) },
                            label = { Text("targetSdk") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Orientation", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("unspecified", "portrait", "landscape").forEach { ori ->
                            SoftChip(label = ori, selected = cfg.orientation == ori, onClick = { onUpdateConfig(cfg.copy(orientation = ori)) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WebView JavaScript & DOM Storage", style = MaterialTheme.typography.bodySmall, color = p.text)
                        Switch(
                            checked = cfg.enableJs && cfg.domStorage,
                            onCheckedChange = { on -> onUpdateConfig(cfg.copy(enableJs = on, domStorage = on)) }
                        )
                    }
                    OutlinedTextField(
                        value = cfg.allowlistDomains,
                        onValueChange = { onUpdateConfig(cfg.copy(allowlistDomains = it)) },
                        label = { Text("HTTPS Allowlist Domains (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cfg.outputFileName,
                        onValueChange = { onUpdateConfig(cfg.copy(outputFileName = it)) },
                        label = { Text("Custom Output Filename (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.signingKeys.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Signing Key Identity", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SoftChip("Unsigned Digest", cfg.signingAlias.isBlank(), onClick = { onUpdateConfig(cfg.copy(signingAlias = "")) })
                            state.signingKeys.forEach { k ->
                                SoftChip(k.alias, cfg.signingAlias == k.alias, onClick = { onUpdateConfig(cfg.copy(signingAlias = k.alias)) })
                            }
                        }
                    }
                    if (state.selectedBuildMode == BuildMode.CUSTOM) {
                        Spacer(Modifier.height(8.dp))
                        Text("ZIP Compression Level", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0 to "Store (0)", 6 to "Balanced (6)", 9 to "Best (9)").forEach { (lvl, lbl) ->
                                SoftChip(lbl, cfg.compressionLevel == lvl, onClick = { onUpdateConfig(cfg.copy(compressionLevel = lvl)) })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = cfg.customCompilerFlags,
                            onValueChange = { onUpdateConfig(cfg.copy(customCompilerFlags = it)) },
                            label = { Text("Compiler / Validator Flags") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Web-to-APK Wrapper Preview OR Native Compatibility Report Card
        if (state.webWrapperPreview != null && (selectedProj?.type == ProjectType.WEB_APP || selectedProj?.type == ProjectType.WEB_TO_APK)) {
            item {
                val wp = state.webWrapperPreview
                GlassCard(onClick = { showWebWrapperDialog = true }, glowAccent = p.success, contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Code, contentDescription = null, tint = p.success)
                            Column {
                                Text("Web-to-APK Wrapper Ready", style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text(
                                    "${wp.assetRefs.size} asset refs • ${wp.missingAssets.size} missing • ${wp.securityDefaults.size} hardened defaults",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.textSecondary
                                )
                            }
                        }
                        StatusPill("Inspect Code", p.cyan)
                    }
                }
            }
        }

        state.nativeCompatReport?.let { rep ->
            item {
                GlassCard(
                    onClick = { showNativeCompatDialog = true },
                    glowAccent = if (rep.isLocallyCompilable) p.success else p.warning,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = if (rep.isLocallyCompilable) p.success else p.warning)
                            Column {
                                Text("Native Android Compatibility Report", style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text(
                                    "${rep.issues.size} diagnostic(s) • Missing CLI: ${rep.missingToolchainItems.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.textSecondary
                                )
                            }
                        }
                        StatusPill(if (rep.isLocallyCompilable) "CLI Ready" else "Compat Report", p.warning)
                    }
                }
            }
        }

        // Preflight Checks
        item {
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preflight Checklist", style = MaterialTheme.typography.titleMedium, color = p.text)
                    StudioIconButton(icon = Icons.Rounded.Refresh, contentDescription = "Refresh preflight", onClick = onRefreshPreflight)
                }
                Spacer(Modifier.height(8.dp))
                state.preflightChecks.forEach { (label, ok) ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = if (ok) p.success else p.warning
                        )
                        Text(label, style = MaterialTheme.typography.bodySmall, color = p.text)
                    }
                }
            }
        }

        // 18-Step Pipeline Circuit + Start/Cancel + Searchable Log Viewer
        item {
            GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("18-Step Pipeline Circuit", style = MaterialTheme.typography.titleMedium, color = p.text)
                    StatusPill(
                        text = state.activeBuildStage.label,
                        color = if (state.isBuilding) p.cyan else p.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.lastCacheHitSummary.isNotBlank()) {
                        "${state.lastCacheHitSummary} • ${state.incrementalCacheCount} cached entries"
                    } else {
                        "Incremental SHA-256 Cache: ${state.incrementalCacheCount} entries indexed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                CircuitBuildLoader(activeStage = state.activeBuildStage, isRunning = state.isBuilding)
                Spacer(Modifier.height(10.dp))
                if (state.isBuilding) {
                    TaskLoadingCard(
                        stageLabel = state.activeBuildStage.label,
                        detailText = state.activeBuildStatusText,
                        progressFraction = state.activeBuildProgress,
                        onCancel = onCancelBuild
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val buttonText = if (state.selectedBuildMode == BuildMode.AUTO) "Plan & Build" else "Start Verified Build"
                        StudioPrimaryButton(
                            text = buttonText,
                            icon = Icons.Rounded.PlayArrow,
                            onClick = {
                                if (state.selectedBuildMode == BuildMode.AUTO && state.autoBuildProposal != null) {
                                    showAutoPlanDialog = true
                                } else {
                                    triggerBuildFlow()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_build_button")
                        )
                        if (state.nativeCompatReport != null && state.selectedBuildProfile == BuildProfile.NATIVE) {
                            StudioSecondaryButton(
                                text = "Compat Setup",
                                onClick = { showNativeCompatDialog = true }
                            )
                        }
                    }
                }

                if (state.activeBuildSteps.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Step Timeline (${state.activeBuildSteps.size}/18)", style = MaterialTheme.typography.titleSmall, color = p.cyan)
                        SoftChip(if (showAllSteps) "Show Recent" else "Show All", selected = showAllSteps, onClick = { showAllSteps = !showAllSteps })
                    }
                    val shownSteps = if (showAllSteps) state.activeBuildSteps else state.activeBuildSteps.takeLast(5)
                    shownSteps.forEach { st ->
                        val col = when (st.status) {
                            "PASS" -> p.success
                            "WARN" -> p.warning
                            "FAIL" -> p.error
                            "RUNNING" -> p.cyan
                            else -> p.textSecondary
                        }
                        Text(
                            "[${st.stepNumber.toString().padStart(2, '0')}] ${st.status} • ${st.title} (${st.durationMs}ms)",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = col
                        )
                    }
                }

                if (state.activeBuildLogs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Redacted Build Log (${state.activeBuildLogs.size})", style = MaterialTheme.typography.titleSmall, color = p.cyan)
                        StudioIconButton(
                            icon = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy build log",
                            onClick = {
                                clipboard.setText(AnnotatedString(state.activeBuildLogs.joinToString("\n")))
                                onShowMessage("Copied redacted build logs")
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL", "WARN", "FAILED").forEach { flt ->
                            SoftChip(flt, logFilter == flt, onClick = { logFilter = flt })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = logSearch,
                        onValueChange = { logSearch = it },
                        label = { Text("Filter logs...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    val visibleLogs = state.activeBuildLogs.filter { line ->
                        (logFilter == "ALL" || line.contains(logFilter, ignoreCase = true)) &&
                            (logSearch.isBlank() || line.contains(logSearch, ignoreCase = true))
                    }
                    visibleLogs.takeLast(14).forEach { line ->
                        Text(line, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
                    }
                }
            }
        }

        // Build History & Reports
        item {
            Text("Build History & Reports (${state.buildHistory.size})", style = MaterialTheme.typography.titleLarge, color = p.text)
        }
        items(state.buildHistory, key = { it.id }) { rec ->
            GlassCard(
                onClick = { reportDialogRecord = rec },
                glowAccent = if (rec.success) p.success else p.error,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rec.projectName, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            "${rec.mode.label} • ${rec.profile.label} • ${rec.durationMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary
                        )
                    }
                    StatusPill(rec.stage.label, if (rec.success) p.success else p.error)
                }
                Spacer(Modifier.height(6.dp))
                Text("Artifact: ${rec.artifactKind} • Report #${rec.reportId}", style = MaterialTheme.typography.labelSmall, color = p.cyan)
                rec.errorCode?.let { err ->
                    Text("Error $err (${rec.failedStage ?: ""})", style = MaterialTheme.typography.labelSmall, color = p.error)
                }
                rec.outputSha256?.let { sha ->
                    Text("SHA-256: ${sha.take(36)}...", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioSecondaryButton(
                        text = "Report / Export",
                        onClick = { reportDialogRecord = rec },
                        modifier = Modifier.weight(1f)
                    )
                    StudioSecondaryButton(
                        text = "Retry",
                        onClick = {
                            onSelectProject(rec.projectId)
                            onSelectProfile(rec.profile)
                            triggerBuildFlow()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showAutoPlanDialog) {
        state.autoBuildProposal?.let { prop ->
            AutoBuildPlanDialog(
                proposal = prop,
                onDismiss = { showAutoPlanDialog = false },
                onConfirmAndBuild = {
                    showAutoPlanDialog = false
                    onApplyAutoConfig()
                    triggerBuildFlow()
                }
            )
        }
    }

    if (showWebWrapperDialog) {
        state.webWrapperPreview?.let { prev ->
            WebWrapperPreviewDialog(preview = prev, onDismiss = { showWebWrapperDialog = false })
        }
    }

    if (showNativeCompatDialog) {
        state.nativeCompatReport?.let { rep ->
            NativeCompatDialog(
                report = rep,
                onApplySafeRepair = {
                    showNativeCompatDialog = false
                    selectedProj?.let { onApplyNativeSafeRepair(it.id) }
                },
                onOpenToolchain = {
                    showNativeCompatDialog = false
                    onOpenToolchain()
                },
                onDismiss = { showNativeCompatDialog = false }
            )
        }
    }

    reportDialogRecord?.let { rec ->
        BuildReportDialog(
            record = rec,
            onExport = { fmt -> onExportReport(rec, fmt) },
            onDismiss = { reportDialogRecord = null }
        )
    }

    if (showPasswordUnlockDialog) {
        BuildSigningUnlockDialog(
            alias = cfg.signingAlias,
            onDismiss = { showPasswordUnlockDialog = false },
            onUnlockAndBuild = { pwd ->
                showPasswordUnlockDialog = false
                onStartBuild(pwd)
            }
        )
    }
}
