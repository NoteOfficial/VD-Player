package com.tkofficial.videodownloder.ui.browser

import android.app.Application
import android.os.Bundle
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.data.AppSettings
import com.tkofficial.videodownloder.database.BrowserBookmarkEntity
import com.tkofficial.videodownloder.database.BrowserHistoryEntity
import com.tkofficial.videodownloder.models.BrowserTab
import com.tkofficial.videodownloder.models.DetectedWebMedia
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as DownloaderVaApp).container
    private val systemDao = container.database.systemDao()
    private val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val bookmarks: StateFlow<List<BrowserBookmarkEntity>> = systemDao.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<BrowserHistoryEntity>> = systemDao.observeBrowserHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tabs = MutableStateFlow(
        listOf(BrowserTab(id = UUID.randomUUID().toString(), title = "DuckDuckGo", url = "https://duckduckgo.com"))
    )
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _detectedMedia = MutableStateFlow<List<DetectedWebMedia>>(emptyList())
    val detectedMedia: StateFlow<List<DetectedWebMedia>> = _detectedMedia.asStateFlow()

    private val tabWebViewStates = mutableMapOf<String, Bundle>()

    @Volatile
    var snifferEnabledSnapshot: Boolean = true

    @Volatile
    var activeTabTitleSnapshot: String = "DuckDuckGo"

    init {
        viewModelScope.launch {
            val home = prefsRepo.settingsFlow.first().browserHomeUrl.trim()
            if (home.isNotBlank()) {
                _tabs.update { current ->
                    if (current.size == 1 && current[0].url == "https://duckduckgo.com" && current[0].title == "DuckDuckGo") {
                        val resolvedTitle = FormatUtils.extractHost(home).ifBlank { home }
                        activeTabTitleSnapshot = resolvedTitle
                        listOf(current[0].copy(url = home, title = resolvedTitle))
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun saveTabWebViewState(tabId: String, webView: WebView) {
        val bundle = Bundle()
        if (webView.saveState(bundle) != null) {
            tabWebViewStates[tabId] = bundle
        }
    }

    fun restoreTabWebViewState(tabId: String, webView: WebView): Boolean {
        val bundle = tabWebViewStates[tabId] ?: return false
        return webView.restoreState(bundle) != null
    }

    fun openNewTab(url: String = settings.value.browserHomeUrl.ifBlank { "https://duckduckgo.com" }) {
        val newTab = BrowserTab(id = UUID.randomUUID().toString(), title = "New Tab", url = url)
        _tabs.value = _tabs.value + newTab
        _activeTabIndex.value = _tabs.value.lastIndex
        activeTabTitleSnapshot = newTab.title
        _detectedMedia.value = emptyList()
    }

    fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
            activeTabTitleSnapshot = _tabs.value[index].title
            _detectedMedia.value = emptyList()
        }
    }

    fun closeTab(index: Int) {
        if (_tabs.value.size <= 1) return
        val closed = _tabs.value.getOrNull(index)
        if (closed != null) {
            tabWebViewStates.remove(closed.id)
        }
        val updated = _tabs.value.toMutableList().apply { removeAt(index) }
        _tabs.value = updated
        val nextIdx = _activeTabIndex.value.coerceIn(0, updated.lastIndex)
        _activeTabIndex.value = nextIdx
        activeTabTitleSnapshot = updated.getOrNull(nextIdx)?.title ?: ""
    }

    fun updateActiveTab(url: String? = null, title: String? = null, isLoading: Boolean? = null, progress: Int? = null) {
        val idx = _activeTabIndex.value
        val list = _tabs.value.toMutableList()
        val current = list.getOrNull(idx) ?: return
        val updatedTitle = title ?: current.title
        activeTabTitleSnapshot = updatedTitle
        list[idx] = current.copy(
            url = url ?: current.url,
            title = updatedTitle,
            isLoading = isLoading ?: current.isLoading,
            progress = progress ?: current.progress
        )
        _tabs.value = list
    }

    fun recordVisit(title: String, url: String) {
        if (!url.startsWith("http")) return
        if (!settings.value.browserSaveHistory) return
        viewModelScope.launch {
            systemDao.insertBrowserHistory(BrowserHistoryEntity(title = title.ifBlank { url }, url = url))
        }
    }

    fun toggleBookmark(title: String, url: String) {
        viewModelScope.launch {
            val exists = bookmarks.value.any { it.url == url }
            if (exists) {
                systemDao.deleteBookmarkByUrl(url)
            } else {
                systemDao.insertBookmark(BrowserBookmarkEntity(title = title.ifBlank { url }, url = url))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { systemDao.clearBrowserHistory() }
    }

    fun clearDetectedMedia() {
        _detectedMedia.value = emptyList()
    }

    fun onMediaUrlSniffed(url: String, mimeType: String, pageTitle: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val category = FormatUtils.classifyMimeType(mimeType, url)
        if (category !in setOf(MediaCategory.VIDEO, MediaCategory.AUDIO, MediaCategory.IMAGE)) return
        _detectedMedia.update { current ->
            if (current.any { it.url == url }) current
            else (current + DetectedWebMedia(url, mimeType, pageTitle, category)).takeLast(25)
        }
    }

    fun normalizeInputToUrl(input: String, homeUrl: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return homeUrl
        return if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
        }
    }
}
