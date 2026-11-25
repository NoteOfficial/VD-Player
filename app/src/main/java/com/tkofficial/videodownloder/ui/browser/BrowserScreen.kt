package com.tkofficial.videodownloder.ui.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.ui.components.VaButton
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaIconButton
import com.tkofficial.videodownloder.ui.components.VaStatusChip
import com.tkofficial.videodownloder.ui.components.VaTextButton
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    snackbarHostState: SnackbarHostState,
    onSendToDownloader: (String) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeIdx by viewModel.activeTabIndex.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val detectedMedia by viewModel.detectedMedia.collectAsStateWithLifecycle()

    val currentTab = tabs.getOrElse(activeIdx) { tabs.first() }
    var addressText by remember(currentTab.id, currentTab.url) { mutableStateOf(currentTab.url) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webCanGoBack by remember { mutableStateOf(false) }
    val latestTabTitle by androidx.compose.runtime.rememberUpdatedState(currentTab.title)
    val latestSnifferEnabled by androidx.compose.runtime.rememberUpdatedState(settings.browserMediaSnifferEnabled)
    val latestAllowHttp by androidx.compose.runtime.rememberUpdatedState(settings.allowCleartextHttp)
    val latestJsEnabled by androidx.compose.runtime.rememberUpdatedState(settings.browserJavaScriptEnabled)

    BackHandler(enabled = webCanGoBack) {
        webViewRef?.goBack()
    }

    var showMenu by remember { mutableStateOf(false) }
    var showTabsDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showDetectedSheet by remember { mutableStateOf(false) }

    val isBookmarked = bookmarks.any { it.url == currentTab.url }

    LaunchedEffect(currentTab.id) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (wv.url != currentTab.url) {
            wv.loadUrl(currentTab.url)
        }
        webCanGoBack = wv.canGoBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        // Address Bar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.cardSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                modifier = Modifier.weight(1f).testTag("browser_url_input"),
                placeholder = { Text(stringResource(R.string.browser_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val normalized = viewModel.normalizeInputToUrl(addressText, settings.browserHomeUrl)
                        viewModel.updateActiveTab(url = normalized)
                        webViewRef?.loadUrl(normalized)
                    }
                ),
                shape = RoundedCornerShape(tokens.smallRadius),
                trailingIcon = {
                    VaIconButton(onClick = {
                        viewModel.toggleBookmark(currentTab.title, currentTab.url)
                    }) {
                        Icon(
                            if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = stringResource(R.string.browser_bookmarks),
                            tint = if (isBookmarked) tokens.primaryAccent else tokens.iconTint
                        )
                    }
                }
            )
            Spacer(Modifier.width(4.dp))
            VaIconButton(onClick = { showTabsDialog = true }, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tab, contentDescription = stringResource(R.string.browser_tabs), tint = tokens.iconTint)
                    Text(tabs.size.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
            Box {
                VaIconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.browser_more), tint = tokens.iconTint)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_send_to_dl)) },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onSendToDownloader(currentTab.url)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_bookmarks)) },
                        leadingIcon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            showBookmarksDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_history)) },
                        leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            showHistoryDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_copy_link)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("URL", currentTab.url))
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    LocaleHelper.getLocalizedString(context, settings.language, R.string.browser_copied_link)
                                )
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, currentTab.url)
                            }
                            context.startActivity(Intent.createChooser(share, currentTab.title))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_open_external)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentTab.url)))
                            }
                        }
                    )
                }
            }
        }

        if (currentTab.isLoading && currentTab.progress in 1..99) {
            LinearProgressIndicator(
                progress = { currentTab.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = tokens.primaryAccent
            )
        }

        // WebView Viewport
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.settings.apply {
                            javaScriptEnabled = settings.browserJavaScriptEnabled
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            @Suppress("DEPRECATION")
                            allowFileAccessFromFileURLs = false
                            @Suppress("DEPRECATION")
                            allowUniversalAccessFromFileURLs = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }
                            useWideViewPort = true
                            loadWithOverviewMode = true
                        }

                        webChromeClient = object : WebChromeClient() {
                            private var lastProgress = -1

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (newProgress == 0 || newProgress == 100 || kotlin.math.abs(newProgress - lastProgress) >= 5) {
                                    lastProgress = newProgress
                                    viewModel.updateActiveTab(progress = newProgress, isLoading = newProgress < 100)
                                }
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    viewModel.updateActiveTab(title = title)
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                viewModel.clearDetectedMedia()
                                webCanGoBack = view?.canGoBack() == true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val reqUri = request?.url ?: return false
                                val scheme = reqUri.scheme?.lowercase().orEmpty()
                                return when {
                                    scheme == "https" -> false
                                    scheme == "http" -> !latestAllowHttp
                                    scheme in setOf("javascript", "file", "content", "data", "about") -> true
                                    else -> {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, reqUri))
                                        }
                                        true
                                    }
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                webCanGoBack = view?.canGoBack() == true
                                val resolvedUrl = url ?: return
                                val pageTitle = view?.title ?: resolvedUrl
                                viewModel.updateActiveTab(url = resolvedUrl, title = pageTitle, isLoading = false, progress = 100)
                                viewModel.recordVisit(pageTitle, resolvedUrl)

                                if (latestSnifferEnabled) {
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            var urls = [];
                                            document.querySelectorAll('video,video source,audio,audio source').forEach(function(el) {
                                                if (el.src && el.src.startsWith('http')) urls.push(el.src);
                                            });
                                            return JSON.stringify(urls);
                                        })();
                                        """.trimIndent()
                                    ) { raw ->
                                        val clean = raw?.trim('"')?.replace("\\\"", "\"") ?: return@evaluateJavascript
                                        if (clean.startsWith("[")) {
                                            clean.removePrefix("[").removeSuffix("]")
                                                .split(",")
                                                .map { it.trim().trim('"') }
                                                .filter { it.startsWith("http") }
                                                .forEach { foundUrl ->
                                                    val mime = if (foundUrl.contains(".mp3", true) || foundUrl.contains(".m4a", true)) "audio/mpeg" else "video/mp4"
                                                    viewModel.onMediaUrlSniffed(foundUrl, mime, pageTitle)
                                                }
                                        }
                                    }
                                }
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val reqUrl = request?.url?.toString().orEmpty()
                                if (latestSnifferEnabled && reqUrl.isNotBlank()) {
                                    val lower = reqUrl.lowercase().substringBefore('?')
                                    val detectedMime = when {
                                        lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
                                        lower.endsWith(".webm") -> "video/webm"
                                        lower.endsWith(".mp3") -> "audio/mpeg"
                                        lower.endsWith(".m4a") -> "audio/mp4"
                                        lower.endsWith(".ogg") -> "audio/ogg"
                                        else -> null
                                    }
                                    if (detectedMime != null) {
                                        viewModel.onMediaUrlSniffed(reqUrl, detectedMime, latestTabTitle)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        loadUrl(currentTab.url)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    if (wv.settings.javaScriptEnabled != latestJsEnabled) {
                        wv.settings.javaScriptEnabled = latestJsEnabled
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Media Detected Floating Action Pill
            androidx.compose.animation.AnimatedVisibility(
                visible = detectedMedia.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showDetectedSheet = true },
                    icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    text = { Text(stringResource(R.string.browser_media_found, detectedMedia.size)) },
                    containerColor = tokens.primaryAccent,
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            }
        }

        // Browser Navigation Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.cardSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VaIconButton(onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = tokens.iconTint)
            }
            VaIconButton(onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.browser_forward), tint = tokens.iconTint)
            }
            VaIconButton(onClick = { webViewRef?.reload() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.browser_refresh), tint = tokens.iconTint)
            }
            VaIconButton(onClick = {
                viewModel.updateActiveTab(url = settings.browserHomeUrl)
                webViewRef?.loadUrl(settings.browserHomeUrl)
            }) {
                Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.browser_home), tint = tokens.iconTint)
            }
            VaIconButton(onClick = { onSendToDownloader(currentTab.url) }) {
                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.browser_send_to_dl), tint = tokens.primaryAccent)
            }
        }
    }

    // Detected Media Sheet
    if (showDetectedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetectedSheet = false },
            containerColor = tokens.cardSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.browser_detected_media_title, detectedMedia.size), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(280.dp)) {
                    items(detectedMedia, key = { it.url }, contentType = { "detected_media" }) { media ->
                        VaCard(contentPadding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(media.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    VaStatusChip(media.mimeType, tokens.tertiaryAccent)
                                }
                                Spacer(Modifier.width(8.dp))
                                VaButton(onClick = {
                                    showDetectedSheet = false
                                    onSendToDownloader(media.url)
                                }) {
                                    Text(stringResource(R.string.browser_download_btn))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Tabs Dialog
    if (showTabsDialog) {
        AlertDialog(
            onDismissRequest = { showTabsDialog = false },
            title = { Text(stringResource(R.string.browser_tabs_title, tabs.size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaButton(
                        onClick = {
                            viewModel.openNewTab(settings.browserHomeUrl)
                            showTabsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.browser_new_tab))
                    }
                    tabs.forEachIndexed { idx, tab ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectTab(idx)
                                    showTabsDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}. ${tab.title}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (idx == activeIdx) tokens.primaryAccent else MaterialTheme.colorScheme.onSurface
                            )
                            if (tabs.size > 1) {
                                VaIconButton(onClick = { viewModel.closeTab(idx) }, animatePress = false) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.browser_close_tab))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { VaTextButton(onClick = { showTabsDialog = false }) { Text(stringResource(R.string.btn_close)) } }
        )
    }

    // Bookmarks Dialog
    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text(stringResource(R.string.browser_bookmarks)) },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    if (bookmarks.isEmpty()) {
                        item { Text(stringResource(R.string.browser_no_bookmarks)) }
                    } else {
                        items(bookmarks, key = { it.id }, contentType = { "bookmark" }) { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateActiveTab(url = bm.url)
                                        webViewRef?.loadUrl(bm.url)
                                        showBookmarksDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(bm.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                VaIconButton(onClick = { viewModel.toggleBookmark(bm.title, bm.url) }, animatePress = false) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.vp_remove))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { VaTextButton(onClick = { showBookmarksDialog = false }) { Text(stringResource(R.string.btn_close)) } }
        )
    }

    // History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text(stringResource(R.string.browser_history)) },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    if (history.isEmpty()) {
                        item { Text(stringResource(R.string.browser_no_history)) }
                    } else {
                        items(history, key = { it.id }, contentType = { "history" }) { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateActiveTab(url = entry.url)
                                        webViewRef?.loadUrl(entry.url)
                                        showHistoryDialog = false
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (history.isNotEmpty()) {
                        VaTextButton(onClick = viewModel::clearHistory) { Text(stringResource(R.string.btn_clear)) }
                    }
                    VaTextButton(onClick = { showHistoryDialog = false }) { Text(stringResource(R.string.btn_close)) }
                }
            }
        )
    }
}
