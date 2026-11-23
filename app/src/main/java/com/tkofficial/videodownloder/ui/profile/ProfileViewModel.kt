package com.tkofficial.videodownloder.ui.profile

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.data.AppSettings
import com.tkofficial.videodownloder.database.AppNotificationEntity
import com.tkofficial.videodownloder.database.BackupRecordEntity
import com.tkofficial.videodownloder.database.VaultMetadataEntity
import com.tkofficial.videodownloder.models.AppLanguage
import com.tkofficial.videodownloder.models.FileEntry
import com.tkofficial.videodownloder.models.IntegrityReport
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.models.StorageSummary
import com.tkofficial.videodownloder.models.ThemeConfig
import com.tkofficial.videodownloder.network.ApiConfigManager
import com.tkofficial.videodownloder.network.ApiConfigStatus
import com.tkofficial.videodownloder.network.ApiTestResult
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DownloaderVaApp
    private val container = app.container
    private val prefsRepo = container.preferencesRepository
    private val securityManager = container.securityManager
    private val vaultRepo = container.vaultRepository
    private val fileRepo = container.fileManagerRepository
    private val backupRepo = container.backupRepository
    private val apiConfigManager = container.apiConfigManager
    private val systemDao = container.database.systemDao()

    val settings: StateFlow<AppSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val pinState = prefsRepo.pinStateFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Triple<String?, String?, Pair<Int, Long>>(null, null, 0 to 0L))

    val vaultItems: StateFlow<List<VaultMetadataEntity>> = vaultRepo.vaultItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<AppNotificationEntity>> = systemDao.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupRecords: StateFlow<List<BackupRecordEntity>> = systemDao.observeBackups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritesCount = container.database.playerLibraryDao().observeAllFavorites()
    val playlistsCount = container.database.playerLibraryDao().observePlaylists()
    val completedDownloadsCount = container.database.downloadDao().observeCompletedCount()
    val totalDownloadedBytes = container.database.downloadDao().observeTotalCompletedBytes()

    private val _vaultUnlocked = MutableStateFlow(false)
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

    private val _decryptedPreviewBytes = MutableStateFlow<Pair<VaultMetadataEntity, ByteArray>?>(null)
    val decryptedPreviewBytes: StateFlow<Pair<VaultMetadataEntity, ByteArray>?> = _decryptedPreviewBytes.asStateFlow()

    private val _decryptedVideoFile = MutableStateFlow<Pair<VaultMetadataEntity, File>?>(null)
    val decryptedVideoFile: StateFlow<Pair<VaultMetadataEntity, File>?> = _decryptedVideoFile.asStateFlow()

    private val _integrityReport = MutableStateFlow<IntegrityReport?>(null)
    val integrityReport: StateFlow<IntegrityReport?> = _integrityReport.asStateFlow()

    private val _currentDir = MutableStateFlow(fileRepo.getRootDirectory())
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _fileEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val fileEntries: StateFlow<List<FileEntry>> = _fileEntries.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _storageSummary = MutableStateFlow<StorageSummary?>(null)
    val storageSummary: StateFlow<StorageSummary?> = _storageSummary.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _apiTestResult = MutableStateFlow<ApiTestResult?>(null)
    val apiTestResult: StateFlow<ApiTestResult?> = _apiTestResult.asStateFlow()

    private val _isTestingApi = MutableStateFlow(false)
    val isTestingApi: StateFlow<Boolean> = _isTestingApi.asStateFlow()

    private suspend fun locString(@StringRes resId: Int, vararg args: Any): String {
        val lang = prefsRepo.settingsFlow.first().language
        return LocaleHelper.getLocalizedString(app, lang, resId, *args)
    }

    private fun locStringSync(@StringRes resId: Int, vararg args: Any): String {
        return LocaleHelper.getLocalizedString(app, settings.value.language, resId, *args)
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { prefsRepo.updateSettings(transform) }
    }

    fun saveThemeConfig(config: ThemeConfig) {
        viewModelScope.launch {
            prefsRepo.updateSettings { it.copy(themeConfig = config) }
            _statusMessage.value = locString(R.string.msg_theme_saved)
        }
    }

    fun resetThemeConfig() {
        viewModelScope.launch {
            prefsRepo.updateSettings { it.copy(themeConfig = ThemeConfig()) }
            _statusMessage.value = locString(R.string.msg_theme_reset)
        }
    }

    fun saveApiConfig(endpoint: String, rawApiKey: String) {
        saveFullApiConfig(
            enabled = endpoint.isNotBlank(),
            primary = endpoint,
            secondary = settings.value.secondaryExtractorEndpoint,
            backup = settings.value.backupExtractorEndpoint,
            version = settings.value.apiVersion,
            rawApiKey = rawApiKey,
            clearStoredKey = false,
            connectTimeoutSec = settings.value.connectTimeoutSeconds,
            requestTimeoutSec = settings.value.requestTimeoutSeconds,
            retryLimit = settings.value.retryLimit
        )
    }

    fun saveFullApiConfig(
        enabled: Boolean,
        primary: String,
        secondary: String,
        backup: String,
        version: String,
        rawApiKey: String,
        clearStoredKey: Boolean,
        connectTimeoutSec: Int,
        requestTimeoutSec: Int,
        retryLimit: Int
    ) {
        viewModelScope.launch {
            val cleanPrimary = primary.trim()
            val cleanSecondary = secondary.trim()
            val cleanBackup = backup.trim()
            val allowHttp = settings.value.allowCleartextHttp
            val nonEmpty = listOf(cleanPrimary, cleanSecondary, cleanBackup).filter { it.isNotBlank() }
            if (nonEmpty.any { !ApiConfigManager.isValidEndpointUrl(it, allowHttp = allowHttp) }) {
                _apiTestResult.value = ApiTestResult(
                    status = ApiConfigStatus.INVALID_CONFIG,
                    message = locString(R.string.api_invalid_url_error)
                )
                _statusMessage.value = locString(R.string.api_invalid_url_error)
                return@launch
            }
            val encrypted = withContext(Dispatchers.IO) {
                when {
                    clearStoredKey -> ""
                    rawApiKey.isNotBlank() -> securityManager.encryptSecretString(rawApiKey.trim())
                    else -> settings.value.encryptedExtractorApiKey
                }
            }
            prefsRepo.updateSettings {
                it.copy(
                    apiEnabled = enabled,
                    customExtractorEndpoint = cleanPrimary,
                    secondaryExtractorEndpoint = cleanSecondary,
                    backupExtractorEndpoint = cleanBackup,
                    apiVersion = version.trim().ifBlank { "v1" },
                    encryptedExtractorApiKey = encrypted,
                    connectTimeoutSeconds = connectTimeoutSec.coerceIn(5, 60),
                    requestTimeoutSeconds = requestTimeoutSec.coerceIn(10, 120),
                    retryLimit = retryLimit.coerceIn(0, 5)
                )
            }
            _apiTestResult.value = null
            _statusMessage.value = locString(R.string.msg_api_saved)
        }
    }

    fun testApiConnection(
        enabled: Boolean,
        primary: String,
        secondary: String,
        backup: String,
        version: String,
        rawApiKey: String,
        connectTimeoutSec: Int,
        requestTimeoutSec: Int,
        retryLimit: Int
    ) {
        viewModelScope.launch {
            _isTestingApi.value = true
            try {
                val candidate = settings.value.copy(
                    apiEnabled = enabled,
                    customExtractorEndpoint = primary.trim(),
                    secondaryExtractorEndpoint = secondary.trim(),
                    backupExtractorEndpoint = backup.trim(),
                    apiVersion = version.trim().ifBlank { "v1" },
                    connectTimeoutSeconds = connectTimeoutSec.coerceIn(5, 60),
                    requestTimeoutSeconds = requestTimeoutSec.coerceIn(10, 120),
                    retryLimit = retryLimit.coerceIn(0, 5)
                )
                val key = withContext(Dispatchers.IO) {
                    if (rawApiKey.isNotBlank()) rawApiKey.trim()
                    else securityManager.decryptSecretString(candidate.encryptedExtractorApiKey)
                }
                val result = apiConfigManager.testConnection(candidate, key)
                _apiTestResult.value = result
                _statusMessage.value = result.message
            } finally {
                _isTestingApi.value = false
            }
        }
    }

    fun resetApiConfigToDefaults() {
        viewModelScope.launch {
            val defaults = withContext(Dispatchers.IO) { apiConfigManager.loadDefaultAssetConfig() }
            prefsRepo.updateSettings {
                it.copy(
                    apiEnabled = defaults.enabled,
                    customExtractorEndpoint = defaults.primaryEndpoint,
                    secondaryExtractorEndpoint = defaults.secondaryEndpoint,
                    backupExtractorEndpoint = defaults.backupEndpoint,
                    apiVersion = defaults.apiVersion,
                    connectTimeoutSeconds = defaults.connectTimeoutSeconds,
                    requestTimeoutSeconds = defaults.requestTimeoutSeconds,
                    retryLimit = defaults.retryLimit,
                    encryptedExtractorApiKey = ""
                )
            }
            _apiTestResult.value = null
            _statusMessage.value = locString(R.string.api_reset_done)
        }
    }

    fun importApiConfig(uri: Uri) {
        viewModelScope.launch {
            apiConfigManager.importConfigFromUri(uri)
                .onSuccess { cfg ->
                    prefsRepo.updateSettings {
                        it.copy(
                            apiEnabled = cfg.enabled,
                            customExtractorEndpoint = cfg.primaryEndpoint,
                            secondaryExtractorEndpoint = cfg.secondaryEndpoint,
                            backupExtractorEndpoint = cfg.backupEndpoint,
                            apiVersion = cfg.apiVersion,
                            connectTimeoutSeconds = cfg.connectTimeoutSeconds,
                            requestTimeoutSeconds = cfg.requestTimeoutSeconds,
                            retryLimit = cfg.retryLimit
                        )
                    }
                    _apiTestResult.value = null
                    _statusMessage.value = locString(R.string.api_imported_ok)
                }
                .onFailure {
                    _apiTestResult.value = ApiTestResult(
                        status = ApiConfigStatus.INVALID_CONFIG,
                        message = it.localizedMessage ?: locString(R.string.api_import_failed)
                    )
                    _statusMessage.value = it.localizedMessage ?: locString(R.string.api_import_failed)
                }
        }
    }

    fun exportApiConfig(uri: Uri) {
        viewModelScope.launch {
            apiConfigManager.exportConfigToUri(uri, settings.value)
                .onSuccess { _statusMessage.value = locString(R.string.api_exported_ok) }
                .onFailure { _statusMessage.value = it.localizedMessage ?: locString(R.string.api_export_failed) }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            Locale.setDefault(Locale.forLanguageTag(language.tag))
            prefsRepo.updateSettings { it.copy(language = language) }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }

    // Security & Vault
    fun setOrChangePin(pin: String) {
        if (pin.length !in 4..8 || !pin.all { it.isDigit() }) {
            _statusMessage.value = locStringSync(R.string.msg_pin_digits_err)
            return
        }
        viewModelScope.launch {
            val (hash, salt) = withContext(Dispatchers.Default) {
                securityManager.hashPin(pin)
            }
            prefsRepo.savePinHash(hash, salt)
            prefsRepo.updateSettings { it.copy(appLockEnabled = true) }
            _vaultUnlocked.value = true
            _statusMessage.value = locString(R.string.msg_pin_saved)
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            prefsRepo.clearPin()
            _vaultUnlocked.value = false
            _statusMessage.value = locString(R.string.msg_pin_disabled)
        }
    }

    suspend fun attemptPinUnlockAsync(pin: String): Boolean {
        val (hash, salt, attemptInfo) = pinState.value
        val (failedCount, lockoutUntil) = attemptInfo
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            val sec = ((lockoutUntil - now) / 1000L).coerceAtLeast(1)
            _statusMessage.value = locString(R.string.msg_too_many_attempts, sec.toInt())
            return false
        }
        if (hash == null || salt == null) {
            _vaultUnlocked.value = true
            return true
        }
        val ok = withContext(Dispatchers.Default) {
            securityManager.verifyPin(pin, hash, salt)
        }
        if (ok) {
            prefsRepo.recordUnlockAttempt(true)
            _vaultUnlocked.value = true
        } else {
            val newCount = failedCount + 1
            val lockMs = if (newCount >= 5) now + 30_000L * (newCount - 4) else 0L
            prefsRepo.recordUnlockAttempt(false, lockMs)
            if (lockMs > 0L) {
                systemDao.insertNotification(
                    AppNotificationEntity(
                        category = "SECURITY",
                        title = "Failed Unlock Protection Triggered",
                        message = "$newCount failed PIN attempts recorded."
                    )
                )
            }
            _statusMessage.value = locString(R.string.msg_incorrect_pin)
        }
        return ok
    }

    fun attemptPinUnlock(pin: String): Boolean {
        val (hash, salt, attemptInfo) = pinState.value
        val (failedCount, lockoutUntil) = attemptInfo
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            val sec = ((lockoutUntil - now) / 1000L).coerceAtLeast(1)
            _statusMessage.value = locStringSync(R.string.msg_too_many_attempts, sec.toInt())
            return false
        }
        if (hash == null || salt == null) {
            _vaultUnlocked.value = true
            return true
        }
        val ok = securityManager.verifyPin(pin, hash, salt)
        viewModelScope.launch {
            if (ok) {
                prefsRepo.recordUnlockAttempt(true)
            } else {
                val newCount = failedCount + 1
                val lockMs = if (newCount >= 5) now + 30_000L * (newCount - 4) else 0L
                prefsRepo.recordUnlockAttempt(false, lockMs)
                if (lockMs > 0L) {
                    systemDao.insertNotification(
                        AppNotificationEntity(
                            category = "SECURITY",
                            title = "Failed Unlock Protection Triggered",
                            message = "$newCount failed PIN attempts recorded."
                        )
                    )
                }
            }
        }
        if (ok) _vaultUnlocked.value = true
        else _statusMessage.value = locStringSync(R.string.msg_incorrect_pin)
        return ok
    }

    fun unlockWithBiometricSuccess() {
        _vaultUnlocked.value = true
    }

    fun lockVault() {
        _vaultUnlocked.value = false
        _decryptedPreviewBytes.value = null
        _decryptedVideoFile.value = null
        viewModelScope.launch(Dispatchers.IO) {
            cleanupVaultTempFiles()
        }
    }

    fun importToVault(uri: Uri) {
        viewModelScope.launch {
            val res = vaultRepo.importUriToVault(uri)
            res.onSuccess {
                _statusMessage.value = locString(R.string.msg_encrypted_vault_item, it.originalName)
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: locString(R.string.msg_vault_import_fail)
            }
        }
    }

    fun openVaultItem(item: VaultMetadataEntity) {
        viewModelScope.launch {
            if (item.mediaType == "VIDEO") {
                val dest = File(app.cacheDir, "vault_play_${item.id}.mp4")
                val res = vaultRepo.decryptVaultToFile(item, dest)
                res.onSuccess { file ->
                    _decryptedVideoFile.value = item to file
                }.onFailure {
                    _statusMessage.value = it.localizedMessage ?: locString(R.string.msg_decrypt_fail)
                }
            } else {
                val res = vaultRepo.decryptVaultBytes(item)
                res.onSuccess { bytes ->
                    _decryptedPreviewBytes.value = item to bytes
                }.onFailure {
                    _statusMessage.value = it.localizedMessage ?: locString(R.string.msg_decrypt_fail)
                }
            }
        }
    }

    fun closeVaultPreview() {
        _decryptedPreviewBytes.value = null
        _decryptedVideoFile.value = null
    }

    private fun cleanupVaultTempFiles() {
        app.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("vault_play_")) {
                file.delete()
            }
        }
    }

    fun deleteVaultItem(item: VaultMetadataEntity) {
        viewModelScope.launch {
            vaultRepo.deleteVaultItem(item)
            _statusMessage.value = locString(R.string.msg_deleted_vault_item, item.originalName)
        }
    }

    fun verifyVaultIntegrity() {
        viewModelScope.launch {
            val (valid, total) = vaultRepo.verifyAllVaultIntegrity()
            _statusMessage.value = locString(R.string.msg_vault_integrity, valid, total)
        }
    }

    fun runIntegrityAudit() {
        viewModelScope.launch(Dispatchers.IO) {
            _integrityReport.value = securityManager.runIntegrityCheck()
        }
    }

    // File Manager
    fun loadDirectory(dir: File = _currentDir.value) {
        _currentDir.value = dir
        viewModelScope.launch {
            _fileEntries.value = fileRepo.listDirectory(dir, _showHiddenFiles.value)
        }
    }

    fun loadCategoryFiles(category: MediaCategory?) {
        viewModelScope.launch {
            _fileEntries.value = fileRepo.listByCategory(category, _showHiddenFiles.value)
        }
    }

    fun toggleHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        loadDirectory(_currentDir.value)
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            if (fileRepo.createFolder(_currentDir.value, name)) {
                loadDirectory()
                _statusMessage.value = locString(R.string.msg_folder_created)
            }
        }
    }

    fun renameFileEntry(entry: FileEntry, newName: String) {
        viewModelScope.launch {
            if (fileRepo.renameFile(File(entry.path), newName)) {
                loadDirectory()
                _statusMessage.value = locString(R.string.msg_renamed_ok)
            }
        }
    }

    fun deleteFileEntries(entries: List<FileEntry>) {
        viewModelScope.launch {
            val count = fileRepo.deleteFiles(entries.map { File(it.path) })
            loadDirectory()
            _statusMessage.value = locString(R.string.msg_deleted_count, count)
        }
    }

    fun zipFileEntries(entries: List<FileEntry>, zipName: String) {
        viewModelScope.launch {
            val safe = FormatUtils.sanitizeFileName(zipName, "zip")
            val outZip = FormatUtils.resolveUniqueFile(_currentDir.value, safe)
            fileRepo.createZip(entries.map { File(it.path) }, outZip)
                .onSuccess {
                    loadDirectory()
                    _statusMessage.value = locString(R.string.msg_created_archive, it.name)
                }
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun extractZipEntry(entry: FileEntry) {
        viewModelScope.launch {
            val dest = File(_currentDir.value, File(entry.path).nameWithoutExtension)
            fileRepo.extractZip(File(entry.path), dest)
                .onSuccess {
                    loadDirectory()
                    _statusMessage.value = locString(R.string.msg_extracted_files, it, dest.name)
                }
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun batchRenameEntries(entries: List<FileEntry>, prefix: String) {
        viewModelScope.launch {
            val count = fileRepo.batchRename(entries.map { File(it.path) }, prefix)
            loadDirectory()
            _statusMessage.value = locString(R.string.msg_batch_renamed, count)
        }
    }

    fun shareFileEntry(entry: FileEntry) {
        val file = File(entry.path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(Intent.createChooser(intent, entry.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // Storage Analyzer
    fun refreshStorageAnalysis() {
        viewModelScope.launch {
            _storageSummary.value = fileRepo.analyzeStorage()
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            val freed = fileRepo.clearAppCache()
            refreshStorageAnalysis()
            _statusMessage.value = locString(R.string.msg_cleared_cache, FormatUtils.formatBytes(freed))
        }
    }

    // Notifications
    fun markAllNotificationsRead() {
        viewModelScope.launch { systemDao.markAllNotificationsRead() }
    }

    fun clearNotifications() {
        viewModelScope.launch { systemDao.clearNotifications() }
    }

    // Backup & Restore
    fun exportBackup(destUri: Uri) {
        viewModelScope.launch {
            backupRepo.exportBackupToUri(destUri)
                .onSuccess { _statusMessage.value = locString(R.string.msg_backup_saved, it.itemCount) }
                .onFailure { _statusMessage.value = it.localizedMessage ?: locString(R.string.msg_backup_fail) }
        }
    }

    fun restoreBackup(sourceUri: Uri) {
        viewModelScope.launch {
            backupRepo.validateAndRestoreBackup(sourceUri)
                .onSuccess { count -> _statusMessage.value = locString(R.string.msg_restored_count, count) }
                .onFailure { _statusMessage.value = it.localizedMessage ?: locString(R.string.msg_restore_fail) }
        }
    }
}
