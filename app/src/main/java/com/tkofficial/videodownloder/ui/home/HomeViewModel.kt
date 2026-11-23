package com.tkofficial.videodownloder.ui.home

import android.app.Application
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.data.AppSettings
import com.tkofficial.videodownloder.database.DownloadEntity
import com.tkofficial.videodownloder.database.DownloadHistoryEntity
import com.tkofficial.videodownloder.models.AnalyzedMedia
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.models.MediaFormatOption
import com.tkofficial.videodownloder.models.UiState
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class DownloadManagerTab { ACTIVE, QUEUE, COMPLETED, FAILED }

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DownloaderVaApp
    private val container = app.container
    private val downloadDao = container.database.downloadDao()
    private val prefsRepo = container.preferencesRepository
    private val linkAnalyzer = container.linkAnalyzer
    private val downloadEngine = container.downloadEngine
    private val securityManager = container.securityManager

    val settings: StateFlow<AppSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val activeDownloads: StateFlow<List<DownloadEntity>> = downloadDao.observeActiveDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedDownloads: StateFlow<List<DownloadEntity>> = downloadDao.observeQueuedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadEntity>> = downloadDao.observeCompletedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedDownloads: StateFlow<List<DownloadEntity>> = downloadDao.observeFailedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyList: StateFlow<List<DownloadHistoryEntity>> = downloadDao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCompletedBytes: StateFlow<Long> = downloadDao.observeTotalCompletedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val completedCount: StateFlow<Int> = downloadDao.observeCompletedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unreadNotifications: StateFlow<Int> = container.database.systemDao().observeUnreadNotificationCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _batchMode = MutableStateFlow(false)
    val batchMode: StateFlow<Boolean> = _batchMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(DownloadManagerTab.ACTIVE)
    val selectedTab: StateFlow<DownloadManagerTab> = _selectedTab.asStateFlow()

    private val _filterCategory = MutableStateFlow<MediaCategory?>(null)
    val filterCategory: StateFlow<MediaCategory?> = _filterCategory.asStateFlow()

    private val _analysisState = MutableStateFlow<UiState<AnalyzedMedia>>(UiState.Idle)
    val analysisState: StateFlow<UiState<AnalyzedMedia>> = _analysisState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private suspend fun locString(@StringRes resId: Int, vararg args: Any): String {
        val lang = prefsRepo.settingsFlow.first().language
        return LocaleHelper.getLocalizedString(app, lang, resId, *args)
    }

    private fun locStringSync(@StringRes resId: Int, vararg args: Any): String {
        return LocaleHelper.getLocalizedString(app, settings.value.language, resId, *args)
    }

    fun onUrlChange(value: String) {
        _urlInput.value = value
    }

    fun setBatchMode(enabled: Boolean) {
        _batchMode.value = enabled
        _analysisState.value = UiState.Idle
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun selectTab(tab: DownloadManagerTab) {
        _selectedTab.value = tab
    }

    fun openCompletedTab() {
        _selectedTab.value = DownloadManagerTab.COMPLETED
    }

    fun setFilterCategory(category: MediaCategory?) {
        _filterCategory.value = category
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearAnalysis() {
        _urlInput.value = ""
        _analysisState.value = UiState.Idle
    }

    fun analyzeSingleLink(customUrl: String? = null) {
        val target = (customUrl ?: _urlInput.value).trim()
        if (customUrl != null) _urlInput.value = target
        if (target.isBlank()) return

        viewModelScope.launch {
            _analysisState.value = UiState.Loading
            val s = prefsRepo.settingsFlow.first()
            val apiKey = securityManager.decryptSecretString(s.encryptedExtractorApiKey)
            val result = linkAnalyzer.analyze(
                rawUrl = target,
                allowHttp = s.allowCleartextHttp,
                timeoutSeconds = s.requestTimeoutSeconds,
                customEndpoint = s.customExtractorEndpoint,
                decryptedApiKey = apiKey,
                secondaryEndpoint = s.secondaryExtractorEndpoint,
                backupEndpoint = s.backupExtractorEndpoint,
                apiVersion = s.apiVersion,
                connectTimeoutSeconds = s.connectTimeoutSeconds,
                retryLimit = s.retryLimit,
                apiEnabled = s.apiEnabled
            )
            val failMsg = locString(R.string.msg_failed_analyze)
            _analysisState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: failMsg) }
            )
        }
    }

    fun enqueueAnalyzedOption(analyzed: AnalyzedMedia, option: MediaFormatOption, scheduleMinutes: Long = 0L) {
        viewModelScope.launch {
            val res = downloadEngine.enqueueMedia(analyzed, option, priority = 0, scheduledDelayMinutes = scheduleMinutes)
            res.onSuccess {
                _analysisState.value = UiState.Idle
                _urlInput.value = ""
                _selectedTab.value = if (scheduleMinutes > 0) DownloadManagerTab.QUEUE else DownloadManagerTab.ACTIVE
                _statusMessage.value = if (scheduleMinutes > 0) {
                    locString(R.string.msg_scheduled_in, analyzed.title, scheduleMinutes.toInt())
                } else {
                    locString(R.string.msg_queued_item, analyzed.title)
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: locString(R.string.msg_failed_enqueue)
            }
        }
    }

    fun processBatchUrls() {
        val lines = _urlInput.value
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (lines.isEmpty()) {
            _statusMessage.value = locStringSync(R.string.msg_batch_enter_url)
            return
        }

        viewModelScope.launch {
            val s = prefsRepo.settingsFlow.first()
            val apiKey = securityManager.decryptSecretString(s.encryptedExtractorApiKey)
            var queuedCount = 0
            var failedCount = 0

            for (line in lines) {
                if (!FormatUtils.isValidDownloadUrl(line, s.allowCleartextHttp)) {
                    failedCount++
                    continue
                }
                val analyzed = linkAnalyzer.analyze(
                    rawUrl = line,
                    allowHttp = s.allowCleartextHttp,
                    timeoutSeconds = s.requestTimeoutSeconds,
                    customEndpoint = s.customExtractorEndpoint,
                    decryptedApiKey = apiKey,
                    secondaryEndpoint = s.secondaryExtractorEndpoint,
                    backupEndpoint = s.backupExtractorEndpoint,
                    apiVersion = s.apiVersion,
                    connectTimeoutSeconds = s.connectTimeoutSeconds,
                    retryLimit = s.retryLimit,
                    apiEnabled = s.apiEnabled
                ).getOrNull()

                if (analyzed != null && analyzed.options.isNotEmpty()) {
                    val res = downloadEngine.enqueueMedia(analyzed, analyzed.options.first())
                    if (res.isSuccess) queuedCount++ else failedCount++
                } else {
                    failedCount++
                }
            }

            _urlInput.value = ""
            _selectedTab.value = DownloadManagerTab.ACTIVE
            _statusMessage.value = locString(R.string.msg_batch_processed, queuedCount, failedCount)
        }
    }

    fun pauseDownload(id: Long) = downloadEngine.pauseDownload(id)
    fun resumeDownload(id: Long) = downloadEngine.resumeDownload(id)
    fun cancelDownload(id: Long) = downloadEngine.cancelDownload(id)
    fun retryDownload(id: Long) = downloadEngine.retryDownload(id)
    fun renameDownload(id: Long, newName: String) = downloadEngine.renameDownload(id, newName)
    fun deleteDownload(id: Long, deleteFile: Boolean) = downloadEngine.deleteDownload(id, deleteFile)
    fun movePriority(id: Long, delta: Int) = downloadEngine.adjustPriority(id, delta)

    fun clearDownloadHistory() {
        viewModelScope.launch { downloadDao.clearHistory() }
    }

    fun shareDownload(entity: DownloadEntity) {
        val file = File(entity.localPath)
        if (!file.exists()) {
            _statusMessage.value = locStringSync(R.string.msg_file_not_found)
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = entity.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(Intent.createChooser(shareIntent, entity.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openExternal(entity: DownloadEntity) {
        val file = File(entity.localPath)
        if (!file.exists()) {
            _statusMessage.value = locStringSync(R.string.msg_file_missing)
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, entity.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { app.startActivity(intent) }.onFailure {
            _statusMessage.value = locStringSync(R.string.msg_no_external_app, entity.mimeType)
        }
    }
}
