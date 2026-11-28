package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.AppSettingsState
import com.example.model.LiveCodingLayoutMode
import com.example.model.OpenEditorTab
import com.example.model.PreviewViewport
import com.example.model.ProjectType
import com.example.model.WorkspaceNode
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.SoftSlider
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioPalette
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState
import java.io.File

@Composable
fun LiveCodingScreen(
    state: StudioUiState,
    settings: AppSettingsState,
    onSelectProject: (String) -> Unit,
    onOpenFile: (String, WorkspaceNode) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onUpdateText: (String) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReplace: (String, String, Boolean) -> Unit,
    onSetLayoutMode: (LiveCodingLayoutMode) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onSetViewport: (PreviewViewport) -> Unit,
    onToggleFullscreen: (Boolean) -> Unit,
    onRefreshPreview: () -> Unit,
    onConsoleLog: (String, String, String) -> Unit,
    onClearConsole: () -> Unit,
    onNavigateProjects: () -> Unit,
    onNavigateBuildCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    var showProjectDropdown by remember { mutableStateOf(false) }
    var showFileDropdown by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showConsole by remember(settings.previewShowConsoleByDefault) { mutableStateOf(settings.previewShowConsoleByDefault) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val activeProject = state.projects.firstOrNull { it.id == state.liveProjectId }
    val activeTab = state.openTabs.firstOrNull { it.absolutePath == state.activeTabPath }

    if (state.isFullscreenPreview) {
        BackHandler { onToggleFullscreen(false) }
    }

    if (activeProject == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("live_coding_screen"),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateView(
                title = "No Active Project Selected",
                description = "Create a Web App or Web-to-APK project first to start live coding with real HTML/CSS/JS preview.",
                actionLabel = "Go to Projects",
                onAction = onNavigateProjects
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("live_coding_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!state.isFullscreenPreview) {
            // Project & File Picker + Layout Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SoftChip(
                        label = activeProject.name,
                        selected = true,
                        onClick = { showProjectDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = showProjectDropdown, onDismissRequest = { showProjectDropdown = false }) {
                        state.projects.filterNot { it.isArchived }.forEach { proj ->
                            DropdownMenuItem(
                                text = { Text("${proj.name} (${proj.type.badge})") },
                                onClick = {
                                    showProjectDropdown = false
                                    onSelectProject(proj.id)
                                }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    SoftChip(
                        label = activeTab?.fileName ?: "Open File",
                        selected = false,
                        onClick = { showFileDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = showFileDropdown, onDismissRequest = { showFileDropdown = false }) {
                        state.liveProjectFiles.forEach { file ->
                            DropdownMenuItem(
                                text = { Text(file.relativePath) },
                                onClick = {
                                    showFileDropdown = false
                                    onOpenFile(activeProject.id, file)
                                }
                            )
                        }
                    }
                }
            }

            // Open Tabs Strip
            if (state.openTabs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.openTabs.forEach { tab ->
                        val isActive = tab.absolutePath == activeTab?.absolutePath
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) p.primary.copy(alpha = 0.22f) else p.raised)
                                .border(1.dp, if (isActive) p.primary else p.border, RoundedCornerShape(12.dp))
                                .clickable { onSelectTab(tab.absolutePath) }
                                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (tab.isDirty) "• ${tab.fileName}" else tab.fileName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isActive) p.text else p.textSecondary,
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { onCloseTab(tab.absolutePath) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close tab", tint = p.textSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Action Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoftChip(
                    label = "Save",
                    selected = activeTab?.isDirty == true,
                    icon = Icons.Rounded.Save,
                    onClick = onSave
                )
                SoftChip(
                    label = "Undo",
                    selected = false,
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    onClick = onUndo
                )
                SoftChip(
                    label = "Redo",
                    selected = false,
                    icon = Icons.AutoMirrored.Rounded.Redo,
                    onClick = onRedo
                )
                SoftChip(
                    label = "Find",
                    selected = showFindReplace,
                    icon = Icons.Rounded.FindReplace,
                    onClick = { showFindReplace = !showFindReplace }
                )
                LiveCodingLayoutMode.entries.forEach { mode ->
                    SoftChip(
                        label = mode.label,
                        selected = state.liveLayoutMode == mode,
                        onClick = { onSetLayoutMode(mode) }
                    )
                }
                SoftChip(
                    label = "Console (${state.consoleLogs.size})",
                    selected = showConsole,
                    icon = Icons.Rounded.Terminal,
                    onClick = { showConsole = !showConsole }
                )
                SoftChip(
                    label = "Refresh",
                    selected = false,
                    icon = Icons.Rounded.Refresh,
                    onClick = onRefreshPreview
                )
                SoftChip(
                    label = "Full",
                    selected = false,
                    icon = Icons.Rounded.Fullscreen,
                    onClick = { onToggleFullscreen(true) }
                )
            }

            // Find & Replace Bar
            AnimatedVisibility(visible = showFindReplace) {
                GlassCard(contentPadding = PaddingValues(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = { findQuery = it },
                            label = { Text("Find") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            label = { Text("Replace") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioSecondaryButton(text = "Replace 1", onClick = { onReplace(findQuery, replaceQuery, false) })
                        StudioPrimaryButton(text = "Replace All", onClick = { onReplace(findQuery, replaceQuery, true) })
                    }
                }
            }

            // Split ratio control when in SPLIT mode
            if (state.liveLayoutMode == LiveCodingLayoutMode.SPLIT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Split ${(state.splitRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textSecondary
                    )
                    SoftSlider(
                        value = state.splitRatio,
                        onValueChange = onSetSplitRatio,
                        valueRange = 0.25f..0.75f,
                        modifier = Modifier.weight(1f)
                    )
                    if (settings.editorAutosave) {
                        StatusPill("Autosave", p.success)
                    }
                }
            }
        } else {
            // Fullscreen Preview Exit Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill("Fullscreen Preview • ${activeProject.name}", p.cyan)
                SoftChip(
                    label = "Exit Full",
                    selected = true,
                    icon = Icons.Rounded.FullscreenExit,
                    onClick = { onToggleFullscreen(false) }
                )
            }
        }

        // Main Workspace Area (Stacked vertically on compact, side-by-side on wide >= 720dp)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWide = maxWidth >= 720.dp
            val effectiveMode = if (state.isFullscreenPreview) LiveCodingLayoutMode.PREVIEW_ONLY else state.liveLayoutMode

            when (effectiveMode) {
                LiveCodingLayoutMode.EDITOR_ONLY -> {
                    CodeEditorPane(
                        tab = activeTab,
                        settings = settings,
                        onTextChange = onUpdateText,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                LiveCodingLayoutMode.PREVIEW_ONLY -> {
                    PreviewPane(
                        projectType = activeProject.type,
                        projectDirPath = activeProject.directoryPath,
                        entryFile = activeProject.entryFile,
                        openTabs = state.openTabs,
                        revision = state.previewRevision,
                        viewport = state.previewViewport,
                        safeMode = settings.webViewSafeMode,
                        enableJs = settings.previewEnableJs,
                        onViewportChange = onSetViewport,
                        onConsoleLog = onConsoleLog,
                        onNavigateBuildCenter = onNavigateBuildCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                LiveCodingLayoutMode.SPLIT -> {
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeEditorPane(
                                tab = activeTab,
                                settings = settings,
                                onTextChange = onUpdateText,
                                modifier = Modifier
                                    .weight(state.splitRatio)
                                    .fillMaxHeight()
                            )
                            PreviewPane(
                                projectType = activeProject.type,
                                projectDirPath = activeProject.directoryPath,
                                entryFile = activeProject.entryFile,
                                openTabs = state.openTabs,
                                revision = state.previewRevision,
                                viewport = state.previewViewport,
                                safeMode = settings.webViewSafeMode,
                                enableJs = settings.previewEnableJs,
                                onViewportChange = onSetViewport,
                                onConsoleLog = onConsoleLog,
                                onNavigateBuildCenter = onNavigateBuildCenter,
                                modifier = Modifier
                                    .weight(1f - state.splitRatio)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeEditorPane(
                                tab = activeTab,
                                settings = settings,
                                onTextChange = onUpdateText,
                                modifier = Modifier
                                    .weight(state.splitRatio)
                                    .fillMaxWidth()
                            )
                            PreviewPane(
                                projectType = activeProject.type,
                                projectDirPath = activeProject.directoryPath,
                                entryFile = activeProject.entryFile,
                                openTabs = state.openTabs,
                                revision = state.previewRevision,
                                viewport = state.previewViewport,
                                safeMode = settings.webViewSafeMode,
                                enableJs = settings.previewEnableJs,
                                onViewportChange = onSetViewport,
                                onConsoleLog = onConsoleLog,
                                onNavigateBuildCenter = onNavigateBuildCenter,
                                modifier = Modifier
                                    .weight(1f - state.splitRatio)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Console Drawer
        AnimatedVisibility(visible = showConsole && !state.isFullscreenPreview) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                glowAccent = p.cyan,
                contentPadding = PaddingValues(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Console & Errors (${state.consoleLogs.size})", style = MaterialTheme.typography.titleSmall, color = p.text)
                    SoftChip(label = "Clear", selected = false, onClick = onClearConsole)
                }
                Spacer(Modifier.height(6.dp))
                if (state.consoleLogs.isEmpty()) {
                    Text("No console output yet.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.consoleLogs, key = { it.id }) { log ->
                            val col = when (log.level) {
                                "ERROR" -> p.error
                                "WARN" -> p.warning
                                else -> p.success
                            }
                            Text(
                                text = "[${log.level}] ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = col,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeEditorPane(
    tab: OpenEditorTab?,
    settings: AppSettingsState,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    GlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(10.dp)
    ) {
        if (tab == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a file to begin editing.", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            }
            return@GlassCard
        }

        val lines = remember(tab.content) { tab.content.split('\n') }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tab.relativePath,
                style = MaterialTheme.typography.labelSmall,
                color = p.cyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${lines.size} lines • ${tab.content.length} chars",
                style = MaterialTheme.typography.labelSmall,
                color = p.textSecondary
            )
        }
        Spacer(Modifier.height(6.dp))

        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        val fontSize = settings.editorFontSizeSp.sp
        val lineHeight = (settings.editorFontSizeSp + 6).sp

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.background.copy(alpha = 0.85f))
                .border(1.dp, p.border, RoundedCornerShape(12.dp))
                .verticalScroll(vScroll)
                .padding(8.dp)
        ) {
            if (settings.editorLineNumbers) {
                Column(modifier = Modifier.padding(end = 10.dp)) {
                    lines.indices.forEach { idx ->
                        Text(
                            text = "${idx + 1}",
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                color = p.textSecondary.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
            val isLargeFile = settings.editorLargeFileMode || tab.content.length > 80_000
            val transformation = remember(p, tab.fileName, settings.editorSyntaxHighlight, isLargeFile) {
                if (settings.editorSyntaxHighlight && !isLargeFile) SyntaxHighlightTransformation(p)
                else VisualTransformation.None
            }
            BasicTextField(
                value = tab.content,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = p.text
                ),
                cursorBrush = SolidColor(p.cyan),
                visualTransformation = transformation,
                modifier = Modifier
                    .weight(1f)
                    .then(if (!settings.editorWordWrap) Modifier.horizontalScroll(hScroll) else Modifier)
                    .testTag("live_code_editor")
            )
        }
    }
}

private class SyntaxHighlightTransformation(private val palette: StudioPalette) : VisualTransformation {
    private val keywords = Regex("\\b(const|let|var|function|return|if|else|for|while|class|import|package|fun|val|override|true|false|null|export|default)\\b")
    private val tags = Regex("</?[A-Za-z0-9\\-]+|>")
    private val strings = Regex("\"[^\"]*\"|'[^']*'|`[^`]*`")
    private val comments = Regex("//.*|/\\*[\\s\\S]*?\\*/|<!--.*?-->")

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val highlighted = buildAnnotatedString {
            append(raw)
            keywords.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = palette.violet, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
            }
            tags.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = palette.cyan), m.range.first, m.range.last + 1)
            }
            strings.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = palette.success), m.range.first, m.range.last + 1)
            }
            comments.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = palette.textSecondary.copy(alpha = 0.7f)), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PreviewPane(
    projectType: ProjectType,
    projectDirPath: String,
    entryFile: String,
    openTabs: List<OpenEditorTab>,
    revision: Int,
    viewport: PreviewViewport,
    safeMode: Boolean,
    enableJs: Boolean,
    onViewportChange: (PreviewViewport) -> Unit,
    onConsoleLog: (String, String, String) -> Unit,
    onNavigateBuildCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    GlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        glowAccent = p.cyan,
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewViewport.entries.forEach { vp ->
                SoftChip(
                    label = vp.label,
                    selected = viewport == vp,
                    onClick = { onViewportChange(vp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (projectType == ProjectType.ANDROID_KOTLIN) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusPill("Native Kotlin/Compose Project", p.warning)
                Spacer(Modifier.height(10.dp))
                Text(
                    "On-device WebView renders HTML/CSS/JS bundles. Native Jetpack Compose UI requires Android host compilation (AAPT2 + Kotlinc/D8) via GitHub CI or Build Center.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                StudioPrimaryButton(
                    text = "Open Build Center",
                    icon = Icons.Rounded.Build,
                    onClick = onNavigateBuildCenter
                )
            }
            return@GlassCard
        }

        // Compose live HTML string from unsaved/saved tabs or disk entry file
        val entryAbs = File(projectDirPath, entryFile).absolutePath
        val entryContent = openTabs.firstOrNull { it.absolutePath == entryAbs }?.content
            ?: runCatching { File(entryAbs).readText() }.getOrElse {
                "<html><body><h3>No entry HTML found ($entryFile)</h3></body></html>"
            }

        val baseDirUrl = "file://${File(entryAbs).parentFile?.absolutePath ?: projectDirPath}/"

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White),
            contentAlignment = Alignment.TopCenter
        ) {
            val widthMod = if (viewport.widthDp != null) {
                Modifier.widthIn(max = viewport.widthDp.dp).fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            }

            AndroidView(
                modifier = widthMod.testTag("live_webview_preview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = enableJs
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = !safeMode
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage != null) {
                                    val lvl = when (consoleMessage.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                        ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                        else -> "LOG"
                                    }
                                    onConsoleLog(lvl, consoleMessage.message(), consoleMessage.sourceId() ?: "preview")
                                }
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                onConsoleLog("ERROR", error?.description?.toString() ?: "Resource load error", "WebView")
                            }
                        }
                    }
                },
                update = { webView ->
                    val tagKey = "$revision:${entryContent.hashCode()}:$safeMode:$enableJs"
                    if (webView.tag != tagKey) {
                        webView.tag = tagKey
                        webView.settings.javaScriptEnabled = enableJs
                        webView.settings.allowFileAccess = !safeMode
                        webView.loadDataWithBaseURL(baseDirUrl, entryContent, "text/html", "UTF-8", null)
                    }
                }
            )
        }
    }
}
