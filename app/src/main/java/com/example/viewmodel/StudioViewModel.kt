package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApkToolRepository
import com.example.data.AssetStudioEngine
import com.example.data.BackupRepository
import com.example.data.BuildPipelineEngine
import com.example.data.CodeMapReport
import com.example.data.CodeMapScannerEngine
import com.example.data.EnvironmentHealthEngine
import com.example.data.EnvironmentHealthReport
import com.example.data.GitHubRepository
import com.example.data.LocalGitEngine
import com.example.data.MyApplicationsEngine
import com.example.data.NativeCompatibilityAnalyzer
import com.example.data.ProjectWorkspaceRepository
import com.example.data.QaCheckResult
import com.example.data.SecureCredentialStore
import com.example.data.SecurityDiagnosticsEngine
import com.example.data.SettingsDataStore
import com.example.data.SigningRepository
import com.example.data.StorageBreakdown
import com.example.data.StudioNotificationManager
import com.example.data.TestQaEngine
import com.example.data.ToolchainRepository
import com.example.data.UtilityToolsEngine
import com.example.data.WebToApkWrapperEngine
import com.example.data.WorkflowBuilderEngine
import com.example.data.ZipInspectSummary
import com.example.data.db.BuildQueueEntity
import com.example.data.db.CrashRecoveryEntity
import com.example.data.db.DatabaseIntegrityManager
import com.example.data.db.IncrementalCacheEntity
import com.example.data.db.StudioDatabase
import com.example.model.ApkComparisonResult
import com.example.model.ApkInspectionReport
import com.example.model.ApkSdkCompatibilityReport
import com.example.model.AppLockPolicyConfig
import com.example.model.AppSettingsState
import com.example.model.AutoBuildProposal
import com.example.model.BackupSnapshot
import com.example.model.BuildConfigSpec
import com.example.model.BuildMode
import com.example.model.BuildProfile
import com.example.model.BuildRecord
import com.example.model.BuildReportFormat
import com.example.model.BuildStage
import com.example.model.BuildStepRecord
import com.example.model.CiArtifactInfo
import com.example.model.ConsoleLogEntry
import com.example.model.DownloadedArtifactRecord
import com.example.model.ExplorerSortMode
import com.example.model.GitCommitRecord
import com.example.model.GitDiffFile
import com.example.model.GitHubAccountProfile
import com.example.model.GitHubDeviceCodeResponse
import com.example.model.GitHubRemoteRepo
import com.example.model.GitMergePreview
import com.example.model.GitRepoConfig
import com.example.model.GitStatusSummary
import com.example.model.LiveCodingLayoutMode
import com.example.model.MyApplicationRecord
import com.example.model.NativeCompatibilityReport
import com.example.model.OpenEditorTab
import com.example.model.PreviewViewport
import com.example.model.ProjectFilter
import com.example.model.ProjectMetadata
import com.example.model.ProjectType
import com.example.model.ResourceAuditItem
import com.example.model.SecurityFinding
import com.example.model.SigningKeyInfo
import com.example.model.StudioEventLogger
import com.example.model.ToolchainEnvironmentReport
import com.example.model.ToolchainItem
import com.example.model.WebWrapperPreview
import com.example.model.WorkflowDiffPreview
import com.example.model.WorkspaceNode
import com.example.ui.theme.StudioThemeId
import com.example.ui.theme.ThemeBrightnessMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class TaskNotification(
    val id: String,
    val title: String,
    val detail: String,
    val timestamp: Long,
    val isError: Boolean = false
)

data class StudioUiState(
    val isInitializing: Boolean = true,
    val projects: List<ProjectMetadata> = emptyList(),
    val projectFilter: ProjectFilter = ProjectFilter.ALL,
    val projectTypeFilter: ProjectType? = null,
    val projectSearchQuery: String = "",
    val projectGridView: Boolean = false,
    val explorerProject: ProjectMetadata? = null,
    val explorerRelativePath: String = "",
    val explorerNodes: List<WorkspaceNode> = emptyList(),
    val explorerSearchQuery: String = "",
    val explorerSortMode: ExplorerSortMode = ExplorerSortMode.NAME_ASC,
    val explorerSelectedPaths: Set<String> = emptySet(),
    val explorerMultiSelect: Boolean = false,
    val isImportingProject: Boolean = false,
    val importStageLabel: String = "",
    val importDetailText: String = "",

    // Live Coding & Crash Recovery
    val liveProjectId: String? = null,
    val liveProjectFiles: List<WorkspaceNode> = emptyList(),
    val openTabs: List<OpenEditorTab> = emptyList(),
    val activeTabPath: String? = null,
    val liveLayoutMode: LiveCodingLayoutMode = LiveCodingLayoutMode.SPLIT,
    val splitRatio: Float = 0.5f,
    val previewViewport: PreviewViewport = PreviewViewport.MOBILE,
    val isFullscreenPreview: Boolean = false,
    val previewRevision: Int = 0,
    val consoleLogs: List<ConsoleLogEntry> = emptyList(),
    val recoveryDrafts: List<CrashRecoveryEntity> = emptyList(),

    // APK Tool
    val primaryApkReport: ApkInspectionReport? = null,
    val secondaryApkReport: ApkInspectionReport? = null,
    val primaryApkSdkCompat: ApkSdkCompatibilityReport? = null,
    val apkComparison: ApkComparisonResult? = null,
    val isInspectingApk: Boolean = false,
    val apkTaskLabel: String = "",
    val apkTaskProgress: Float? = null,
    val apkError: String? = null,

    // Build Center & Incremental Cache
    val selectedBuildProjectId: String? = null,
    val selectedBuildMode: BuildMode = BuildMode.AUTO,
    val selectedBuildProfile: BuildProfile = BuildProfile.AUTO,
    val buildConfigSpec: BuildConfigSpec = BuildConfigSpec(),
    val autoBuildProposal: AutoBuildProposal? = null,
    val webWrapperPreview: WebWrapperPreview? = null,
    val nativeCompatReport: NativeCompatibilityReport? = null,
    val preflightChecks: List<Pair<String, Boolean>> = emptyList(),
    val isBuilding: Boolean = false,
    val activeBuildStage: BuildStage = BuildStage.IDLE,
    val activeBuildProgress: Float? = null,
    val activeBuildStatusText: String = "Ready",
    val activeBuildLogs: List<String> = emptyList(),
    val activeBuildSteps: List<BuildStepRecord> = emptyList(),
    val buildHistory: List<BuildRecord> = emptyList(),
    val lastExportedReportPath: String? = null,
    val incrementalCacheCount: Int = 0,
    val lastCacheHitSummary: String = "",

    // Toolchain
    val toolchainItems: List<ToolchainItem> = emptyList(),
    val toolchainEnvReport: ToolchainEnvironmentReport? = null,
    val isScanningToolchain: Boolean = false,

    // Signing
    val signingKeys: List<SigningKeyInfo> = emptyList(),

    // Git, Workflows & GitHub CI
    val localGitStatus: GitStatusSummary = GitStatusSummary(),
    val localGitDiffs: List<GitDiffFile> = emptyList(),
    val localGitCommits: List<GitCommitRecord> = emptyList(),
    val gitMergePreview: GitMergePreview? = null,
    val workflowDiffPreview: WorkflowDiffPreview? = null,
    val githubAccount: GitHubAccountProfile? = null,
    val githubAccessibleRepos: List<GitHubRemoteRepo> = emptyList(),
    val githubDeviceCode: GitHubDeviceCodeResponse? = null,
    val isPollingDeviceAuth: Boolean = false,
    val githubOverview: GitHubRepository.RepoOverview? = null,
    val githubRateLimitInfo: String? = null,
    val ciRunLogs: List<String> = emptyList(),
    val isPollingCi: Boolean = false,
    val isLoadingGitCi: Boolean = false,
    val isPushingOrCloningGit: Boolean = false,
    val gitCiError: String? = null,
    val downloadedArtifacts: List<DownloadedArtifactRecord> = emptyList(),
    val isDownloadingArtifact: Boolean = false,

    // Security, App Lock & Diagnostics
    val securityFindings: List<SecurityFinding> = emptyList(),
    val isScanningSecurity: Boolean = false,
    val declaredPermissions: List<String> = emptyList(),
    val threatModelChecklist: List<Pair<String, String>> = emptyList(),
    val lastPrivacyExportPath: String? = null,
    val appLockPolicy: AppLockPolicyConfig = AppLockPolicyConfig(),
    val isAppLocked: Boolean = false,
    val pinLockError: String? = null,

    // Backups
    val backups: List<BackupSnapshot> = emptyList(),
    val isBackupBusy: Boolean = false,
    val backupDiffPreview: List<String>? = null,
    val backupIntegrityMessage: String? = null,

    // QA, Code Map, Environment Health, Storage Breakdown, My Apps & Asset Audit
    val qaResults: List<QaCheckResult> = emptyList(),
    val codeMapReport: CodeMapReport? = null,
    val envHealth: EnvironmentHealthReport? = null,
    val storageBreakdown: StorageBreakdown? = null,
    val dbIntegritySummary: String? = null,
    val myApplications: List<MyApplicationRecord> = emptyList(),
    val resourceAuditItems: List<ResourceAuditItem> = emptyList(),
    val lastSettingsExportPath: String? = null,

    // Storage & Notifications
    val storageUsageBytes: Long = 0L,
    val notifications: List<TaskNotification> = emptyList(),
    val snackbarMessage: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsDataStore(application)
    private val credentialStore = SecureCredentialStore(application)
    private val notificationManager = StudioNotificationManager(application)
    private val db = StudioDatabase.getInstance(application)
    private val dao = db.studioDao()
    private val dbIntegrity = DatabaseIntegrityManager(application)

    val workspaceRepo = ProjectWorkspaceRepository(application)
    val apkRepo = ApkToolRepository(application)
    private val buildEngine = BuildPipelineEngine(application)
    private val toolchainRepo = ToolchainRepository(application)
    private val signingRepo = SigningRepository(application)
    private val githubRepo = GitHubRepository()
    private val securityEngine = SecurityDiagnosticsEngine(application)
    private val backupRepo = BackupRepository(application)
    private val codeMapEngine = CodeMapScannerEngine(application)
    private val envHealthEngine = EnvironmentHealthEngine(application)
    private val assetStudioEngine = AssetStudioEngine(application)
    private val testQaEngine = TestQaEngine(application)

    val settings: StateFlow<AppSettingsState> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettingsState())

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var activeBuildJob: Job? = null
    private var activeImportJob: Job? = null
    private var ciPollingJob: Job? = null
    private var lastBackgroundTimestampMs: Long = 0L

    private val artifactsDir: File
        get() = File(getApplication<Application>().filesDir, "tkstudio/ci_artifacts").apply { mkdirs() }

    init {
        notificationManager.ensureChannels()
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            settingsStore.migrateLegacyTokenIfNeeded(credentialStore)
            val projects = workspaceRepo.loadProjects()
            val builds = buildEngine.loadHistory()
            val tools = toolchainRepo.inspectToolchain()
            val toolEnv = toolchainRepo.inspectEnvironment(tools)
            val keys = signingRepo.loadKeys()
            val snaps = backupRepo.loadSnapshots()
            val perms = securityEngine.getDeclaredPermissions()
            val storage = workspaceRepo.getTotalStorageUsageBytes()
            val env = envHealthEngine.inspectHealth()
            val breakdown = envHealthEngine.computeStorageBreakdown()
            val cacheCount = runCatching { dao.countCacheEntries() }.getOrDefault(0)
            val recovery = runCatching { dao.getAllRecoveryRecords() }.getOrDefault(emptyList())
            val dbReport = runCatching { dbIntegrity.inspectAndVerify(db) }.getOrNull()
            val currentSettings = settings.first()
            val defaultProject = projects.firstOrNull()
            val lockPolicy = credentialStore.loadPinLockStatus(
                enabled = currentSettings.pinLockEnabled,
                autoLockSeconds = currentSettings.autoLockSeconds
            )
            val downloadedArts = loadDownloadedArtifactsFromDisk()

            StudioEventLogger.log(
                "INFO",
                "Startup",
                "Initialized workspace (${projects.size} projects, DB v${dbReport?.schemaVersion ?: 2} ${dbReport?.integrityMessage ?: "checked"})"
            )

            _uiState.update { state ->
                val liveId = state.liveProjectId?.takeIf { id -> projects.any { it.id == id } }
                    ?: if (currentSettings.openLastProjectOnStartup) defaultProject?.id else null
                val buildId = state.selectedBuildProjectId?.takeIf { id -> projects.any { it.id == id } } ?: defaultProject?.id
                val myApps = MyApplicationsEngine.deriveApplications(projects, builds, keys, downloadedArts)
                state.copy(
                    isInitializing = false,
                    projects = projects,
                    buildHistory = builds,
                    toolchainItems = tools,
                    toolchainEnvReport = toolEnv,
                    signingKeys = keys,
                    backups = snaps,
                    declaredPermissions = perms,
                    threatModelChecklist = securityEngine.getThreatModelChecklist(),
                    appLockPolicy = lockPolicy,
                    isAppLocked = lockPolicy.enabled && lockPolicy.hasPinConfigured && state.isInitializing,
                    downloadedArtifacts = downloadedArts,
                    myApplications = myApps,
                    storageUsageBytes = storage,
                    envHealth = env,
                    storageBreakdown = breakdown,
                    dbIntegritySummary = dbReport?.let { "Room DB v${it.schemaVersion}: ${it.integrityMessage}" },
                    incrementalCacheCount = cacheCount,
                    recoveryDrafts = recovery,
                    liveProjectId = liveId,
                    selectedBuildProjectId = buildId,
                    splitRatio = currentSettings.defaultSplitRatio,
                    previewViewport = currentSettings.defaultPreviewViewport,
                    selectedBuildProfile = currentSettings.defaultBuildProfile
                )
            }
            defaultProject?.let {
                if (currentSettings.openLastProjectOnStartup && _uiState.value.liveProjectFiles.isEmpty()) {
                    selectLiveCodingProject(it.id)
                }
                refreshBuildDiagnostics(it.id)
                refreshLocalGit(it)
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String, isError: Boolean = false) {
        val safeMsg = StudioEventLogger.redact(msg)
        StudioEventLogger.log(if (isError) "WARN" else "INFO", "StudioUI", safeMsg)
        val note = TaskNotification(
            id = UUID.randomUUID().toString().take(8),
            title = if (isError) "Task Issue" else "TK Studio",
            detail = safeMsg,
            timestamp = System.currentTimeMillis(),
            isError = isError
        )
        _uiState.update {
            it.copy(
                snackbarMessage = safeMsg,
                notifications = (listOf(note) + it.notifications).take(25)
            )
        }
    }

    // --- Settings & Theme (never resets editor/build state) ---
    fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    fun setTheme(themeId: StudioThemeId) = updateSettings { it.copy(themeId = themeId) }
    fun setBrightness(mode: ThemeBrightnessMode) = updateSettings { it.copy(brightnessMode = mode) }

    fun toggleTutorialBookmark(lessonId: String) {
        updateSettings {
            val next = if (lessonId in it.bookmarkedTutorials) it.bookmarkedTutorials - lessonId else it.bookmarkedTutorials + lessonId
            it.copy(bookmarkedTutorials = next)
        }
    }

    fun toggleTutorialStep(stepKey: String) {
        updateSettings {
            val next = if (stepKey in it.completedTutorialSteps) it.completedTutorialSteps - stepKey else it.completedTutorialSteps + stepKey
            it.copy(completedTutorialSteps = next)
        }
    }

    fun resetAllSettingsToDefaults() {
        viewModelScope.launch {
            settingsStore.resetAllToDefaultsPreserveAuth()
            showMessage("Reset all settings to defaults (GitHub auth, PIN & tutorial progress preserved)")
        }
    }

    fun exportSanitizedSettingsJsonFile() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().filesDir, "tkstudio/exports").apply { mkdirs() }
                    val file = File(dir, "tkstudio_settings_${System.currentTimeMillis()}.json")
                    file.writeText(SettingsDataStore.exportSanitizedSettingsJson(settings.value))
                    file
                }
            }.onSuccess { file ->
                _uiState.update { it.copy(lastSettingsExportPath = file.absolutePath) }
                showMessage("Exported sanitized settings: ${file.name}")
            }.onFailure { err ->
                showMessage("Settings export failed: ${err.message}", isError = true)
            }
        }
    }

    // --- Crash / Draft Recovery (#47) ---
    fun dismissRecoveryDraft(id: String) {
        viewModelScope.launch {
            dao.deleteRecovery(id)
            _uiState.update { it.copy(recoveryDrafts = dao.getAllRecoveryRecords()) }
        }
    }

    fun restoreRecoveryDraft(draft: CrashRecoveryEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(draft.targetPath).takeIf { it.parentFile?.exists() == true }?.writeText(draft.payload)
            }
            dao.deleteRecovery(draft.id)
            _uiState.update { it.copy(recoveryDrafts = dao.getAllRecoveryRecords()) }
            showMessage("Recovered draft for ${File(draft.targetPath).name}")
        }
    }

    // --- Projects & Explorer ---
    fun setProjectFilter(filter: ProjectFilter) = _uiState.update { it.copy(projectFilter = filter) }
    fun setProjectTypeFilter(type: ProjectType?) = _uiState.update { it.copy(projectTypeFilter = type) }
    fun setProjectSearch(q: String) = _uiState.update { it.copy(projectSearchQuery = q) }
    fun toggleProjectGridView() = _uiState.update { it.copy(projectGridView = !it.projectGridView) }

    fun createProject(name: String, type: ProjectType, packageId: String = "com.tkofficial.app") {
        viewModelScope.launch {
            val created = workspaceRepo.createProject(name, type, packageId)
            val projects = workspaceRepo.loadProjects()
            _uiState.update {
                it.copy(
                    projects = projects,
                    storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes(),
                    storageBreakdown = envHealthEngine.computeStorageBreakdown(),
                    selectedBuildProjectId = created.id
                )
            }
            selectLiveCodingProject(created.id)
            showMessage("Created '${created.name}'")
        }
    }

    fun importZipProject(uri: Uri) {
        activeImportJob?.cancel()
        activeImportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImportingProject = true,
                    importStageLabel = "Scanning Archive",
                    importDetailText = "Validating ZIP entries..."
                )
            }
            val result = workspaceRepo.importZipProject(
                uri = uri,
                zipSlipGuard = settings.value.zipSlipGuardEnabled
            ) { count, entry ->
                _uiState.update {
                    it.copy(importStageLabel = "Extracting ($count files)", importDetailText = entry)
                }
            }
            result.onSuccess { proj ->
                val projects = workspaceRepo.loadProjects()
                _uiState.update {
                    it.copy(
                        isImportingProject = false,
                        projects = projects,
                        storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes(),
                        storageBreakdown = envHealthEngine.computeStorageBreakdown()
                    )
                }
                selectLiveCodingProject(proj.id)
                showMessage("Imported '${proj.name}' (${proj.fileCount} files)")
            }.onFailure { err ->
                _uiState.update { it.copy(isImportingProject = false) }
                showMessage("Import failed: ${err.message}", isError = true)
            }
        }
    }

    fun cancelImport() {
        activeImportJob?.cancel()
        _uiState.update { it.copy(isImportingProject = false) }
        showMessage("Archive import cancelled")
    }

    fun exportProjectZip(project: ProjectMetadata, destUri: Uri) {
        viewModelScope.launch {
            workspaceRepo.exportProjectZip(project, destUri).onSuccess { bytes ->
                showMessage("Exported ${project.name} (${bytes / 1024} KB)")
            }.onFailure {
                showMessage("Export failed: ${it.message}", isError = true)
            }
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            workspaceRepo.renameProject(projectId, newName)
            refreshProjectsOnly()
            showMessage("Project renamed")
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            val copy = workspaceRepo.duplicateProject(projectId)
            refreshProjectsOnly()
            if (copy != null) showMessage("Duplicated as '${copy.name}'")
        }
    }

    fun toggleFavoriteProject(projectId: String) {
        viewModelScope.launch {
            workspaceRepo.toggleFavorite(projectId)
            refreshProjectsOnly()
        }
    }

    fun toggleArchiveProject(projectId: String) {
        viewModelScope.launch {
            workspaceRepo.toggleArchive(projectId)
            refreshProjectsOnly()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            workspaceRepo.deleteProject(projectId)
            dao.clearProjectCache(projectId)
            _uiState.update { state ->
                state.copy(
                    explorerProject = if (state.explorerProject?.id == projectId) null else state.explorerProject,
                    openTabs = state.openTabs.filterNot { it.projectId == projectId },
                    incrementalCacheCount = dao.countCacheEntries()
                )
            }
            refreshProjectsOnly()
            showMessage("Project deleted")
        }
    }

    fun openProjectExplorer(project: ProjectMetadata, relativePath: String = "") {
        viewModelScope.launch {
            val nodes = workspaceRepo.listDirectory(project, relativePath)
            _uiState.update {
                it.copy(
                    explorerProject = project,
                    explorerRelativePath = relativePath,
                    explorerNodes = sortNodes(nodes, it.explorerSortMode),
                    explorerSelectedPaths = emptySet(),
                    explorerMultiSelect = false
                )
            }
        }
    }

    fun closeProjectExplorer() {
        _uiState.update {
            it.copy(explorerProject = null, explorerRelativePath = "", explorerNodes = emptyList(), explorerMultiSelect = false)
        }
    }

    fun setExplorerSearch(q: String) = _uiState.update { it.copy(explorerSearchQuery = q) }

    fun setExplorerSort(mode: ExplorerSortMode) {
        _uiState.update { state ->
            state.copy(explorerSortMode = mode, explorerNodes = sortNodes(state.explorerNodes, mode))
        }
    }

    fun toggleExplorerMultiSelect() {
        _uiState.update { it.copy(explorerMultiSelect = !it.explorerMultiSelect, explorerSelectedPaths = emptySet()) }
    }

    fun toggleExplorerNodeSelection(absPath: String) {
        _uiState.update { state ->
            val next = if (absPath in state.explorerSelectedPaths) state.explorerSelectedPaths - absPath
            else state.explorerSelectedPaths + absPath
            state.copy(explorerSelectedPaths = next)
        }
    }

    fun createExplorerEntry(name: String, isDirectory: Boolean) {
        val proj = _uiState.value.explorerProject ?: return
        val rel = _uiState.value.explorerRelativePath
        viewModelScope.launch {
            workspaceRepo.createFileOrFolder(proj, rel, name, isDirectory).onSuccess {
                openProjectExplorer(proj, rel)
                refreshProjectsOnly()
                if (_uiState.value.liveProjectId == proj.id) {
                    _uiState.update { it.copy(liveProjectFiles = workspaceRepo.listAllEditableFiles(proj)) }
                }
            }.onFailure {
                showMessage("Create failed: ${it.message}", isError = true)
            }
        }
    }

    fun renameExplorerNode(absPath: String, newName: String) {
        val proj = _uiState.value.explorerProject ?: return
        val rel = _uiState.value.explorerRelativePath
        viewModelScope.launch {
            workspaceRepo.renameNode(proj.id, absPath, newName)
            openProjectExplorer(proj, rel)
            refreshProjectsOnly()
        }
    }

    fun deleteSelectedExplorerNodes() {
        val proj = _uiState.value.explorerProject ?: return
        val rel = _uiState.value.explorerRelativePath
        val paths = _uiState.value.explorerSelectedPaths.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            workspaceRepo.deleteNodes(proj.id, paths)
            openProjectExplorer(proj, rel)
            refreshProjectsOnly()
            showMessage("Deleted ${paths.size} item(s)")
        }
    }

    fun importFileIntoExplorer(uri: Uri) {
        val proj = _uiState.value.explorerProject ?: return
        val rel = _uiState.value.explorerRelativePath
        viewModelScope.launch {
            workspaceRepo.importDocumentIntoDir(proj, rel, uri).onSuccess { node ->
                openProjectExplorer(proj, rel)
                refreshProjectsOnly()
                showMessage("Imported ${node.name}")
            }.onFailure {
                showMessage("File import failed: ${it.message}", isError = true)
            }
        }
    }

    // --- Live Coding ---
    fun selectLiveCodingProject(projectId: String) {
        viewModelScope.launch {
            val proj = _uiState.value.projects.firstOrNull { it.id == projectId } ?: return@launch
            val files = workspaceRepo.listAllEditableFiles(proj)
            _uiState.update { it.copy(liveProjectId = projectId, liveProjectFiles = files) }
            val entry = files.firstOrNull { it.relativePath == proj.entryFile } ?: files.firstOrNull()
            if (entry != null) openFileInLiveCoding(proj.id, entry)
        }
    }

    fun openFileInLiveCoding(projectId: String, node: WorkspaceNode) {
        viewModelScope.launch {
            val existing = _uiState.value.openTabs.firstOrNull { it.absolutePath == node.absolutePath }
            if (existing != null) {
                _uiState.update { it.copy(activeTabPath = existing.absolutePath) }
                return@launch
            }
            val text = workspaceRepo.readFileText(node.absolutePath)
            val tab = OpenEditorTab(
                projectId = projectId,
                relativePath = node.relativePath,
                absolutePath = node.absolutePath,
                fileName = node.name,
                content = text,
                savedContent = text
            )
            _uiState.update {
                it.copy(openTabs = it.openTabs + tab, activeTabPath = tab.absolutePath)
            }
        }
    }

    fun selectEditorTab(absPath: String) = _uiState.update { it.copy(activeTabPath = absPath) }

    fun closeEditorTab(absPath: String) {
        _uiState.update { state ->
            val next = state.openTabs.filterNot { it.absolutePath == absPath }
            val nextActive = if (state.activeTabPath == absPath) next.lastOrNull()?.absolutePath else state.activeTabPath
            state.copy(openTabs = next, activeTabPath = nextActive)
        }
    }

    fun updateActiveEditorText(newText: String) {
        val activePath = _uiState.value.activeTabPath ?: return
        var tabToProcess: OpenEditorTab? = null
        _uiState.update { state ->
            val nextTabs = state.openTabs.map { tab ->
                if (tab.absolutePath == activePath && tab.content != newText) {
                    val updated = tab.copy(
                        content = newText,
                        undoStack = (tab.undoStack + tab.content).takeLast(40),
                        redoStack = emptyList()
                    )
                    tabToProcess = updated
                    updated
                } else tab
            }
            state.copy(openTabs = nextTabs)
        }
        val tab = tabToProcess ?: return
        viewModelScope.launch {
            if (settings.value.editorAutosave) {
                workspaceRepo.writeFileText(tab.projectId, tab.absolutePath, tab.content)
                markTabSaved(tab.absolutePath, tab.content)
                dao.deleteRecovery("draft:${tab.absolutePath}")
                if (settings.value.previewAutoRefresh) {
                    _uiState.update { it.copy(previewRevision = it.previewRevision + 1) }
                }
            } else {
                // Save crash-recovery draft in Room until user explicitly saves
                dao.upsertRecovery(
                    CrashRecoveryEntity(
                        id = "draft:${tab.absolutePath}",
                        kind = "EDITOR_DRAFT",
                        projectId = tab.projectId,
                        targetPath = tab.absolutePath,
                        payload = tab.content,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun saveActiveEditorTab() {
        val activePath = _uiState.value.activeTabPath ?: return
        val tab = _uiState.value.openTabs.firstOrNull { it.absolutePath == activePath } ?: return
        viewModelScope.launch {
            workspaceRepo.writeFileText(tab.projectId, tab.absolutePath, tab.content)
            markTabSaved(tab.absolutePath, tab.content)
            dao.deleteRecovery("draft:${tab.absolutePath}")
            _uiState.update { it.copy(previewRevision = it.previewRevision + 1) }
            showMessage("Saved ${tab.fileName}")
        }
    }

    fun undoActiveEditor() {
        val activePath = _uiState.value.activeTabPath ?: return
        _uiState.update { state ->
            val next = state.openTabs.map { tab ->
                if (tab.absolutePath == activePath && tab.undoStack.isNotEmpty()) {
                    val prev = tab.undoStack.last()
                    tab.copy(
                        content = prev,
                        undoStack = tab.undoStack.dropLast(1),
                        redoStack = (tab.redoStack + tab.content).takeLast(40)
                    )
                } else tab
            }
            state.copy(openTabs = next)
        }
    }

    fun redoActiveEditor() {
        val activePath = _uiState.value.activeTabPath ?: return
        _uiState.update { state ->
            val next = state.openTabs.map { tab ->
                if (tab.absolutePath == activePath && tab.redoStack.isNotEmpty()) {
                    val nxt = tab.redoStack.last()
                    tab.copy(
                        content = nxt,
                        undoStack = (tab.undoStack + tab.content).takeLast(40),
                        redoStack = tab.redoStack.dropLast(1)
                    )
                } else tab
            }
            state.copy(openTabs = next)
        }
    }

    fun replaceInActiveEditor(findText: String, replaceText: String, replaceAll: Boolean) {
        if (findText.isEmpty()) return
        val activePath = _uiState.value.activeTabPath ?: return
        val tab = _uiState.value.openTabs.firstOrNull { it.absolutePath == activePath } ?: return
        val updated = if (replaceAll) tab.content.replace(findText, replaceText)
        else tab.content.replaceFirst(findText, replaceText)
        if (updated != tab.content) {
            updateActiveEditorText(updated)
            showMessage("Replaced text in ${tab.fileName}")
        }
    }

    fun setLiveLayoutMode(mode: LiveCodingLayoutMode) = _uiState.update { it.copy(liveLayoutMode = mode) }
    fun setSplitRatio(ratio: Float) = _uiState.update { it.copy(splitRatio = ratio.coerceIn(0.25f, 0.75f)) }
    fun setPreviewViewport(vp: PreviewViewport) = _uiState.update { it.copy(previewViewport = vp) }
    fun setFullscreenPreview(full: Boolean) = _uiState.update { it.copy(isFullscreenPreview = full) }
    fun refreshWebPreview() = _uiState.update { it.copy(previewRevision = it.previewRevision + 1) }

    fun appendConsoleLog(level: String, message: String, source: String) {
        val entry = ConsoleLogEntry(
            id = UUID.randomUUID().toString().take(8),
            timestamp = System.currentTimeMillis(),
            level = level,
            message = securityEngine.redactLogLine(message),
            source = source
        )
        _uiState.update { it.copy(consoleLogs = (it.consoleLogs + entry).takeLast(120)) }
    }

    fun clearConsoleLogs() = _uiState.update { it.copy(consoleLogs = emptyList()) }

    private fun markTabSaved(absPath: String, savedText: String) {
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map {
                    if (it.absolutePath == absPath) it.copy(savedContent = savedText) else it
                }
            )
        }
    }

    // --- APK Tool ---
    fun inspectSelfApk() {
        val file = apkRepo.getSelfApkFile()
        if (file == null) {
            _uiState.update { it.copy(apkError = "Installed APK path not accessible in current runtime.") }
            return
        }
        inspectApkFile(file, isSecondary = false)
    }

    fun inspectApkUri(uri: Uri, isSecondary: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInspectingApk = true,
                    apkTaskLabel = "Scanning Archive",
                    apkTaskProgress = null,
                    apkError = null
                )
            }
            apkRepo.importApkToCache(uri).onSuccess { file ->
                inspectApkFile(file, isSecondary)
            }.onFailure { err ->
                _uiState.update { it.copy(isInspectingApk = false, apkError = err.message) }
            }
        }
    }

    fun inspectApkFile(file: File, isSecondary: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInspectingApk = true,
                    apkTaskLabel = "Parsing Manifest & Digests",
                    apkTaskProgress = null,
                    apkError = null
                )
            }
            apkRepo.inspectApk(file).onSuccess { report ->
                val compat = ApkToolRepository.assessSdkCompatibility(report.minSdk, report.targetSdk)
                _uiState.update { state ->
                    val nextPrimary = if (!isSecondary) report else state.primaryApkReport
                    val nextSecondary = if (isSecondary) report else state.secondaryApkReport
                    state.copy(
                        isInspectingApk = false,
                        primaryApkReport = nextPrimary,
                        secondaryApkReport = nextSecondary,
                        primaryApkSdkCompat = if (!isSecondary) compat else state.primaryApkSdkCompat,
                        storageBreakdown = envHealthEngine.computeStorageBreakdown()
                    )
                }
                val p = _uiState.value.primaryApkReport
                val s = _uiState.value.secondaryApkReport
                if (p != null && s != null) {
                    val diff = apkRepo.compareApks(p, s)
                    _uiState.update { it.copy(apkComparison = diff) }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isInspectingApk = false, apkError = err.message ?: "Unable to parse APK.") }
            }
        }
    }

    fun extractPrimaryApk() {
        val path = _uiState.value.primaryApkReport?.filePath ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingApk = true, apkTaskLabel = "Extracting APK", apkTaskProgress = 0f) }
            apkRepo.extractApkToFolder(File(path)) { done, total, _ ->
                _uiState.update { it.copy(apkTaskProgress = done.toFloat() / total) }
            }.onSuccess { outDir ->
                _uiState.update {
                    it.copy(
                        isInspectingApk = false,
                        apkTaskProgress = null,
                        storageBreakdown = envHealthEngine.computeStorageBreakdown()
                    )
                }
                showMessage("Extracted to ${outDir.name}")
            }.onFailure { err ->
                _uiState.update { it.copy(isInspectingApk = false, apkTaskProgress = null) }
                showMessage("Extract failed: ${err.message}", isError = true)
            }
        }
    }

    // --- Build Center + Incremental Cache (#17, #18, #22) ---
    fun selectBuildProject(projectId: String) {
        val proj = _uiState.value.projects.firstOrNull { it.id == projectId }
        _uiState.update { st ->
            st.copy(
                selectedBuildProjectId = projectId,
                buildConfigSpec = if (proj != null) {
                    st.buildConfigSpec.copy(
                        appName = proj.name,
                        packageId = proj.packageId.ifBlank {
                            "com.tkofficial." + proj.name.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "app" }
                        },
                        outputFileName = ""
                    )
                } else st.buildConfigSpec
            )
        }
        refreshBuildDiagnostics(projectId)
    }

    fun selectBuildMode(mode: BuildMode) {
        _uiState.update { it.copy(selectedBuildMode = mode) }
    }

    fun selectBuildProfile(profile: BuildProfile) {
        _uiState.update { it.copy(selectedBuildProfile = profile) }
    }

    fun updateBuildConfigSpec(spec: BuildConfigSpec) {
        _uiState.update { it.copy(buildConfigSpec = spec) }
        refreshBuildDiagnostics(_uiState.value.selectedBuildProjectId)
    }

    fun refreshPreflight(projectId: String? = _uiState.value.selectedBuildProjectId) {
        refreshBuildDiagnostics(projectId)
    }

    fun refreshBuildDiagnostics(projectId: String? = _uiState.value.selectedBuildProjectId) {
        val proj = _uiState.value.projects.firstOrNull { it.id == projectId } ?: return
        viewModelScope.launch {
            val tools = _uiState.value.toolchainItems.ifEmpty { toolchainRepo.inspectToolchain() }
            val checks = buildEngine.runPreflight(proj, tools)
            val cfg = _uiState.value.buildConfigSpec.let { c ->
                if (c.appName == "TK App" || c.appName.isBlank()) {
                    c.copy(
                        appName = proj.name,
                        packageId = proj.packageId.ifBlank {
                            "com.tkofficial." + proj.name.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "app" }
                        }
                    )
                } else c
            }
            val proposal = buildEngine.buildAutoProposal(proj, cfg, tools)
            val dir = File(proj.directoryPath)
            val webPrev = if (proj.type == ProjectType.WEB_APP || proj.type == ProjectType.WEB_TO_APK) {
                withContext(Dispatchers.IO) {
                    WebToApkWrapperEngine.inspectAndGeneratePreview(dir, proj, proposal.proposedConfig)
                }
            } else null
            val nativeRep = if (proj.type == ProjectType.ANDROID_KOTLIN) {
                withContext(Dispatchers.IO) {
                    NativeCompatibilityAnalyzer.analyze(dir, proj, tools)
                }
            } else null

            _uiState.update {
                it.copy(
                    preflightChecks = checks,
                    autoBuildProposal = proposal,
                    webWrapperPreview = webPrev,
                    nativeCompatReport = nativeRep,
                    buildConfigSpec = cfg
                )
            }
        }
    }

    fun applyProposedAutoConfig() {
        val prop = _uiState.value.autoBuildProposal ?: return
        _uiState.update { it.copy(buildConfigSpec = prop.proposedConfig) }
        showMessage("Applied proposed Auto Build configuration")
    }

    fun applyNativeSafeManifestRepair(projectId: String) {
        val proj = _uiState.value.projects.firstOrNull { it.id == projectId } ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                NativeCompatibilityAnalyzer.applyConfirmedSafeManifestRepair(File(proj.directoryPath))
            }
            res.onSuccess { (backup, count) ->
                refreshBuildDiagnostics(proj.id)
                refreshProjectsOnly()
                showMessage("Applied $count safe manifest fix(es); backup saved (${backup.name})")
            }.onFailure { err ->
                showMessage("Safe repair failed: ${err.message}", isError = true)
            }
        }
    }

    fun startBuild(signingPassword: String = "") {
        val projId = _uiState.value.selectedBuildProjectId ?: _uiState.value.projects.firstOrNull()?.id
        val proj = _uiState.value.projects.firstOrNull { it.id == projId }
        if (proj == null) {
            showMessage("Create or select a project first.", isError = true)
            return
        }
        activeBuildJob?.cancel()
        activeBuildJob = viewModelScope.launch {
            val queueId = UUID.randomUUID().toString().take(8)
            val mode = _uiState.value.selectedBuildMode
            val profile = _uiState.value.selectedBuildProfile
            val cfg = if (mode == BuildMode.AUTO) {
                _uiState.value.autoBuildProposal?.proposedConfig ?: _uiState.value.buildConfigSpec
            } else {
                _uiState.value.buildConfigSpec
            }
            val selectedKey = _uiState.value.signingKeys.firstOrNull { it.alias == cfg.signingAlias }
            val tools = _uiState.value.toolchainItems.ifEmpty { toolchainRepo.inspectToolchain() }

            dao.upsertBuildQueue(
                BuildQueueEntity(
                    id = queueId,
                    projectId = proj.id,
                    projectName = proj.name,
                    profile = profile.name,
                    priority = 1,
                    status = "RUNNING",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            dao.upsertRecovery(
                CrashRecoveryEntity(
                    id = "build:$queueId",
                    kind = "BUILD_TASK",
                    projectId = proj.id,
                    targetPath = proj.directoryPath,
                    payload = "Active build ${proj.name} (${profile.label})",
                    updatedAt = System.currentTimeMillis()
                )
            )

            val cacheSummary = computeAndUpdateIncrementalCache(proj)
            _uiState.update {
                it.copy(
                    isBuilding = true,
                    activeBuildStage = BuildStage.PREPARING,
                    activeBuildProgress = 0.02f,
                    activeBuildStatusText = "Preparing 18-step pipeline ($cacheSummary)...",
                    activeBuildLogs = listOf("[START] ${proj.name} • Mode=${mode.label} • Profile=${profile.label} • $cacheSummary"),
                    activeBuildSteps = emptyList(),
                    lastCacheHitSummary = cacheSummary
                )
            }
            val record = runCatching {
                buildEngine.executeBuildWithConfig(
                    project = proj,
                    mode = mode,
                    profile = profile,
                    config = cfg,
                    toolchainItems = tools,
                    signingKey = selectedKey,
                    signingPassword = signingPassword,
                    signingRepo = signingRepo,
                    onStageUpdate = { stage, progress, text ->
                        _uiState.update { st ->
                            st.copy(
                                activeBuildStage = stage,
                                activeBuildProgress = progress,
                                activeBuildStatusText = text,
                                activeBuildLogs = st.activeBuildLogs + "[${stage.label}] $text"
                            )
                        }
                    },
                    onStepUpdate = { steps ->
                        _uiState.update { it.copy(activeBuildSteps = steps) }
                    }
                )
            }.getOrNull()

            dao.deleteRecovery("build:$queueId")

            if (record != null) {
                dao.upsertBuildQueue(
                    BuildQueueEntity(
                        id = queueId,
                        projectId = proj.id,
                        projectName = proj.name,
                        profile = profile.name,
                        priority = 1,
                        status = if (record.success) "COMPLETED" else record.stage.name,
                        createdAt = record.startedAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val statusTag = when {
                    record.success && record.artifactKind == "WEB_TO_APK_WRAPPER_BUNDLE" -> "Web Wrapper Ready"
                    record.success -> "Source Verified"
                    else -> record.stage.label
                }
                workspaceRepo.updateBuildStatus(proj.id, statusTag)
                val history = buildEngine.loadHistory()
                refreshProjectsOnly()
                _uiState.update { st ->
                    val apps = MyApplicationsEngine.deriveApplications(st.projects, history, st.signingKeys, st.downloadedArtifacts)
                    st.copy(
                        isBuilding = false,
                        activeBuildStage = record.stage,
                        activeBuildLogs = record.logLines,
                        activeBuildSteps = record.steps,
                        buildHistory = history,
                        myApplications = apps,
                        incrementalCacheCount = dao.countCacheEntries(),
                        storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes(),
                        storageBreakdown = envHealthEngine.computeStorageBreakdown(),
                        recoveryDrafts = dao.getAllRecoveryRecords()
                    )
                }
                val statusMsg = if (record.success) {
                    "Verified ${record.artifactKind} (${record.durationMs}ms)"
                } else {
                    "Build ${record.stage.label}: ${record.errorCode ?: "Check logs"}"
                }
                showMessage(statusMsg, isError = !record.success)
                if (settings.value.notifyBuildComplete) {
                    notificationManager.postTaskNotification(
                        id = 1001,
                        title = if (record.success) "Build Complete: ${proj.name}" else "Build Failed: ${proj.name}",
                        message = statusMsg,
                        deepLinkRoute = "build"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isBuilding = false,
                        activeBuildStage = BuildStage.CANCELLED,
                        recoveryDrafts = dao.getAllRecoveryRecords()
                    )
                }
            }
        }
    }

    fun exportBuildReport(record: BuildRecord, format: BuildReportFormat) {
        viewModelScope.launch {
            buildEngine.exportBuildReport(record, format).onSuccess { file ->
                _uiState.update { it.copy(lastExportedReportPath = file.absolutePath) }
                showMessage("Exported report: ${file.name}")
            }.onFailure { err ->
                showMessage("Report export failed: ${err.message}", isError = true)
            }
        }
    }

    fun cancelBuild() {
        activeBuildJob?.cancel()
        _uiState.update {
            it.copy(
                isBuilding = false,
                activeBuildStage = BuildStage.CANCELLED,
                activeBuildStatusText = "Cancelled by user"
            )
        }
        showMessage("Build cancelled")
    }

    private suspend fun computeAndUpdateIncrementalCache(proj: ProjectMetadata): String = withContext(Dispatchers.IO) {
        val root = File(proj.directoryPath)
        val prevMap = dao.getProjectCache(proj.id).associateBy { it.relativePath }
        var hits = 0
        var misses = 0
        val newEntries = mutableListOf<IncrementalCacheEntity>()
        val now = System.currentTimeMillis()
        root.walkTopDown()
            .filter {
                it.isFile &&
                    !it.absolutePath.contains("${File.separator}.tkstudio_git") &&
                    !it.absolutePath.contains("${File.separator}.tkstudio_repair_backups")
            }
            .forEach { file ->
                val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
                val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                val prev = prevMap[rel]
                if (prev != null && prev.sha256 == sha) hits++ else misses++
                newEntries.add(
                    IncrementalCacheEntity(
                        cacheKey = "${proj.id}:$rel",
                        projectId = proj.id,
                        relativePath = rel,
                        sha256 = sha,
                        lastSizeBytes = file.length(),
                        lastVerifiedAt = now
                    )
                )
            }
        dao.upsertCacheEntries(newEntries)
        "Cache hits: $hits, changed: $misses"
    }

    fun clearIncrementalCache() {
        viewModelScope.launch {
            dao.clearAllCache()
            _uiState.update { it.copy(incrementalCacheCount = 0) }
            showMessage("Cleared incremental build cache")
        }
    }

    // --- Toolchain ---
    fun refreshToolchain() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningToolchain = true) }
            val items = toolchainRepo.inspectToolchain()
            val env = toolchainRepo.inspectEnvironment(items)
            _uiState.update {
                it.copy(
                    toolchainItems = items,
                    toolchainEnvReport = env,
                    isScanningToolchain = false
                )
            }
            refreshBuildDiagnostics(_uiState.value.selectedBuildProjectId)
        }
    }

    fun runToolchainSelfTest(itemId: String) {
        val item = _uiState.value.toolchainItems.firstOrNull { it.id == itemId } ?: return
        viewModelScope.launch {
            val result = toolchainRepo.runSelfTest(item)
            _uiState.update { state ->
                state.copy(
                    toolchainItems = state.toolchainItems.map {
                        if (it.id == itemId) it.copy(lastTestResult = result) else it
                    }
                )
            }
            showMessage(result)
        }
    }

    fun importToolchainFile(uri: Uri) {
        viewModelScope.launch {
            toolchainRepo.importToolchainBinary(uri).onSuccess { (file, note) ->
                refreshToolchain()
                showMessage("Imported ${file.name} • $note")
            }.onFailure {
                showMessage("Tool import failed: ${it.message}", isError = true)
            }
        }
    }

    // --- Signing ---
    fun createSigningKey(
        alias: String,
        cn: String,
        ou: String,
        org: String,
        locality: String,
        state: String,
        country: String,
        algorithm: String,
        keySize: Int,
        validityYears: Int,
        password: String,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            signingRepo.createSigningKey(alias, cn, ou, org, locality, state, country, algorithm, keySize, validityYears, password)
                .onSuccess { key ->
                    val nextKeys = signingRepo.loadKeys()
                    _uiState.update { st ->
                        st.copy(
                            signingKeys = nextKeys,
                            myApplications = MyApplicationsEngine.deriveApplications(st.projects, st.buildHistory, nextKeys, st.downloadedArtifacts)
                        )
                    }
                    showMessage("Created signing key '${key.alias}'")
                    onDone(true)
                }
                .onFailure { err ->
                    showMessage("Key creation failed: ${err.message}", isError = true)
                    onDone(false)
                }
        }
    }

    fun verifySigningKeyPassword(key: SigningKeyInfo, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            signingRepo.verifyKeyPassword(key, password).onSuccess { ok ->
                val msg = if (ok) "Password verified for '${key.alias}'" else "Password mismatch"
                showMessage(msg, isError = !ok)
                onResult(ok, msg)
            }.onFailure {
                val msg = "Wrong password or unreadable key vault for '${key.alias}'"
                showMessage(msg, isError = true)
                onResult(false, msg)
            }
        }
    }

    fun exportEncryptedSigningKey(key: SigningKeyInfo, password: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            signingRepo.exportEncryptedKeyBackup(key, password).onSuccess { file ->
                val msg = "Exported encrypted vault backup: ${file.name}"
                showMessage(msg)
                onResult(msg)
            }.onFailure { err ->
                val msg = "Encrypted backup failed: ${err.message}"
                showMessage(msg, isError = true)
                onResult(msg)
            }
        }
    }

    fun deleteSigningKey(key: SigningKeyInfo) {
        viewModelScope.launch {
            signingRepo.deleteKey(key)
            val nextKeys = signingRepo.loadKeys()
            _uiState.update { st ->
                st.copy(
                    signingKeys = nextKeys,
                    myApplications = MyApplicationsEngine.deriveApplications(st.projects, st.buildHistory, nextKeys, st.downloadedArtifacts)
                )
            }
            showMessage("Removed key '${key.alias}'")
        }
    }

    // --- Local Git, Workflow Builder & GitHub CI ---
    fun initLocalGit(project: ProjectMetadata) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.initRepo(File(project.directoryPath), settings.value.githubBranch.ifBlank { "main" })
            }.onSuccess {
                refreshLocalGit(project)
                showMessage("Initialized local Git repository in '${project.name}'")
            }.onFailure { showMessage("Git init failed: ${it.message}", isError = true) }
        }
    }

    fun refreshLocalGit(project: ProjectMetadata? = _uiState.value.projects.firstOrNull()) {
        val proj = project ?: return
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { LocalGitEngine.inspectStatus(File(proj.directoryPath)) }
            val commits = withContext(Dispatchers.IO) { LocalGitEngine.loadCommits(File(proj.directoryPath)) }
            val allDiffs = (status.stagedFiles + status.unstagedFiles).sortedBy { it.relativePath }
            _uiState.update {
                it.copy(
                    localGitStatus = status,
                    localGitDiffs = allDiffs,
                    localGitCommits = commits
                )
            }
        }
    }

    fun stageLocalFile(project: ProjectMetadata, relativePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LocalGitEngine.stageFile(File(project.directoryPath), relativePath) }
                .onSuccess { refreshLocalGit(project) }
                .onFailure { showMessage("Stage failed: ${it.message}", isError = true) }
        }
    }

    fun unstageLocalFile(project: ProjectMetadata, relativePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LocalGitEngine.unstageFile(File(project.directoryPath), relativePath) }
                .onSuccess { refreshLocalGit(project) }
                .onFailure { showMessage("Unstage failed: ${it.message}", isError = true) }
        }
    }

    fun stageAllLocalFiles(project: ProjectMetadata) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LocalGitEngine.stageAll(File(project.directoryPath)) }
                .onSuccess { count ->
                    refreshLocalGit(project)
                    showMessage("Staged $count change(s)")
                }
                .onFailure { showMessage("Stage all failed: ${it.message}", isError = true) }
        }
    }

    fun commitLocalGit(project: ProjectMetadata, message: String) {
        val s = settings.value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.commitStaged(
                    projectDir = File(project.directoryPath),
                    message = message,
                    authorName = s.gitAuthorName,
                    authorEmail = s.gitAuthorEmail,
                    stageAllIfEmpty = true
                )
            }.onSuccess { record ->
                refreshLocalGit(project)
                showMessage("Committed ${record.id} on ${record.branch}")
            }.onFailure { showMessage("Commit failed: ${it.message}", isError = true) }
        }
    }

    fun createGitBranch(project: ProjectMetadata, branchName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.createBranch(File(project.directoryPath), branchName, switchToIt = true)
            }.onSuccess { branch ->
                refreshLocalGit(project)
                showMessage("Switched to new branch '$branch'")
            }.onFailure { showMessage("Create branch failed: ${it.message}", isError = true) }
        }
    }

    fun switchGitBranch(project: ProjectMetadata, branchName: String, discardUncommitted: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.switchBranch(File(project.directoryPath), branchName, discardUncommitted)
            }.onSuccess {
                refreshLocalGit(project)
                refreshProjectsOnly()
                if (_uiState.value.liveProjectId == project.id) selectLiveCodingProject(project.id)
                showMessage("Checked out branch '$branchName'")
            }.onFailure { showMessage("Branch switch blocked: ${it.message}", isError = true) }
        }
    }

    fun deleteGitBranch(project: ProjectMetadata, branchName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.deleteBranch(File(project.directoryPath), branchName)
            }.onSuccess {
                refreshLocalGit(project)
                showMessage("Deleted branch '$branchName'")
            }.onFailure { showMessage("Delete branch failed: ${it.message}", isError = true) }
        }
    }

    fun previewGitMerge(project: ProjectMetadata, sourceBranch: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.previewMerge(File(project.directoryPath), sourceBranch)
            }.onSuccess { preview ->
                _uiState.update { it.copy(gitMergePreview = preview) }
            }.onFailure { showMessage("Merge preview failed: ${it.message}", isError = true) }
        }
    }

    fun dismissGitMergePreview() = _uiState.update { it.copy(gitMergePreview = null) }

    fun applyGitMerge(project: ProjectMetadata, sourceBranch: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.applyMerge(File(project.directoryPath), sourceBranch)
            }.onSuccess { res ->
                _uiState.update { it.copy(gitMergePreview = null) }
                refreshLocalGit(project)
                refreshProjectsOnly()
                if (res.conflictingFiles.isEmpty()) {
                    showMessage("Merged '$sourceBranch' cleanly (${res.cleanFiles.size} file(s))")
                } else {
                    showMessage("Merge has ${res.conflictingFiles.size} conflict(s) to resolve", isError = true)
                }
            }.onFailure { showMessage("Merge failed: ${it.message}", isError = true) }
        }
    }

    fun resolveGitConflict(project: ProjectMetadata, relativePath: String, keepOurs: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.resolveConflict(File(project.directoryPath), relativePath, keepOurs)
            }.onSuccess {
                refreshLocalGit(project)
                refreshProjectsOnly()
                showMessage("Resolved conflict in $relativePath (${if (keepOurs) "kept ours" else "accepted theirs"})")
            }.onFailure { showMessage("Resolve conflict failed: ${it.message}", isError = true) }
        }
    }

    fun stashLocalChanges(project: ProjectMetadata, label: String = "WIP") {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.stashChanges(File(project.directoryPath), label)
            }.onSuccess { entry ->
                refreshLocalGit(project)
                refreshProjectsOnly()
                showMessage("Stashed ${entry.fileCount} file(s) as ${entry.id}")
            }.onFailure { showMessage("Stash failed: ${it.message}", isError = true) }
        }
    }

    fun popLocalStash(project: ProjectMetadata, stashId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.popStash(File(project.directoryPath), stashId)
            }.onSuccess { count ->
                refreshLocalGit(project)
                refreshProjectsOnly()
                showMessage("Applied stash ($count file(s))")
            }.onFailure { showMessage("Pop stash failed: ${it.message}", isError = true) }
        }
    }

    fun resetLocalWorkingTree(project: ProjectMetadata) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LocalGitEngine.resetWorkingTreeToHead(File(project.directoryPath))
            }.onSuccess { count ->
                refreshLocalGit(project)
                refreshProjectsOnly()
                if (_uiState.value.liveProjectId == project.id) selectLiveCodingProject(project.id)
                showMessage("Reset workspace to branch HEAD ($count file(s) restored)")
            }.onFailure { showMessage("Reset failed: ${it.message}", isError = true) }
        }
    }

    fun previewWorkflowYaml(project: ProjectMetadata, fileName: String, yaml: String) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                WorkflowBuilderEngine.previewWorkflowDiff(File(project.directoryPath), fileName, yaml)
            }
            _uiState.update { it.copy(workflowDiffPreview = preview) }
        }
    }

    fun dismissWorkflowDiffPreview() = _uiState.update { it.copy(workflowDiffPreview = null) }

    fun saveWorkflowYamlToProject(project: ProjectMetadata, fileName: String, yaml: String) {
        viewModelScope.launch {
            WorkflowBuilderEngine.writeWorkflowSafely(File(project.directoryPath), fileName, yaml)
                .onSuccess {
                    _uiState.update { it.copy(workflowDiffPreview = null) }
                    refreshLocalGit(project)
                    refreshProjectsOnly()
                    showMessage("Saved .github/workflows/$fileName (with .bak backup if existing)")
                }
                .onFailure { showMessage("Workflow save failed: ${it.message}", isError = true) }
        }
    }

    fun saveGitHubConfig(
        owner: String,
        repo: String,
        branch: String,
        tokenInput: String,
        clientId: String = settings.value.githubClientId
    ) {
        viewModelScope.launch {
            if (tokenInput.isNotBlank()) {
                credentialStore.saveGitHubToken(tokenInput.trim())
            }
            val hasEncryptedToken = credentialStore.hasGitHubToken()
            updateSettings {
                it.copy(
                    githubOwner = owner.trim(),
                    githubRepo = repo.trim(),
                    githubBranch = branch.trim().ifEmpty { "main" },
                    githubClientId = clientId.trim(),
                    hasGitHubToken = hasEncryptedToken
                )
            }
            _uiState.value.projects.firstOrNull()?.let { proj ->
                withContext(Dispatchers.IO) {
                    LocalGitEngine.saveRemoteConfig(File(proj.directoryPath), owner, repo, branch)
                }
                refreshLocalGit(proj)
            }
            showMessage(if (tokenInput.isNotBlank()) "GitHub token encrypted in Keystore & config saved" else "GitHub repository config saved")
        }
    }

    fun disconnectGitHub() {
        viewModelScope.launch {
            ciPollingJob?.cancel()
            credentialStore.clearGitHubToken()
            updateSettings { it.copy(hasGitHubToken = false) }
            _uiState.update {
                it.copy(
                    githubAccount = null,
                    githubAccessibleRepos = emptyList(),
                    githubOverview = null,
                    githubDeviceCode = null,
                    isPollingDeviceAuth = false,
                    isPollingCi = false,
                    ciRunLogs = emptyList()
                )
            }
            showMessage("Disconnected GitHub account & wiped encrypted token")
        }
    }

    fun startGitHubDeviceFlow(clientId: String) {
        viewModelScope.launch {
            updateSettings { it.copy(githubClientId = clientId.trim()) }
            _uiState.update { it.copy(gitCiError = null, isLoadingGitCi = true) }
            githubRepo.requestDeviceCode(clientId.trim()).onSuccess { codeResp ->
                _uiState.update { it.copy(githubDeviceCode = codeResp, isLoadingGitCi = false) }
                showMessage("Enter code ${codeResp.userCode} at github.com/login/device")
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingGitCi = false, gitCiError = err.message) }
                showMessage("Device Flow failed: ${err.message}", isError = true)
            }
        }
    }

    fun pollGitHubDeviceToken() {
        val codeResp = _uiState.value.githubDeviceCode ?: return
        val clientId = settings.value.githubClientId
        viewModelScope.launch {
            _uiState.update { it.copy(isPollingDeviceAuth = true, gitCiError = null) }
            githubRepo.pollDeviceAccessToken(clientId, codeResp.deviceCode).onSuccess { res ->
                when {
                    res.accessToken != null -> {
                        credentialStore.saveGitHubToken(res.accessToken)
                        updateSettings { it.copy(hasGitHubToken = true) }
                        _uiState.update { it.copy(githubDeviceCode = null, isPollingDeviceAuth = false) }
                        fetchGitHubAccountAndRepos()
                        showMessage("Authorized via GitHub OAuth Device Flow")
                    }
                    res.pending -> {
                        _uiState.update { it.copy(isPollingDeviceAuth = false) }
                        showMessage("Waiting for authorization on GitHub (${codeResp.userCode})...")
                    }
                    else -> {
                        _uiState.update { it.copy(isPollingDeviceAuth = false, gitCiError = res.error) }
                        showMessage(res.error ?: "OAuth error", isError = true)
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isPollingDeviceAuth = false, gitCiError = err.message) }
                showMessage("OAuth poll error: ${err.message}", isError = true)
            }
        }
    }

    fun fetchGitHubAccountAndRepos() {
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            if (token.isBlank()) {
                _uiState.update { it.copy(gitCiError = "Authorize GitHub via Device Flow or scoped PAT first.") }
                return@launch
            }
            _uiState.update { it.copy(isLoadingGitCi = true, gitCiError = null) }
            githubRepo.fetchAuthenticatedUserAndScopes(token).onSuccess { account ->
                val repos = githubRepo.fetchAccessibleRepos(token).getOrDefault(emptyList())
                val rl = githubRepo.lastRateLimitRemaining?.let { rem ->
                    "Rate Limit: $rem/${githubRepo.lastRateLimitTotal ?: 5000}"
                }
                _uiState.update {
                    it.copy(
                        githubAccount = account,
                        githubAccessibleRepos = repos,
                        githubRateLimitInfo = rl,
                        isLoadingGitCi = false
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingGitCi = false, gitCiError = err.message) }
            }
        }
    }

    fun createRemoteGitHubRepo(name: String, description: String, isPrivate: Boolean) {
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            if (token.isBlank()) {
                showMessage("Authorize GitHub account first.", isError = true)
                return@launch
            }
            _uiState.update { it.copy(isLoadingGitCi = true, gitCiError = null) }
            githubRepo.createRemoteRepository(token, name, description, isPrivate).onSuccess { created ->
                updateSettings {
                    it.copy(
                        githubOwner = created.owner,
                        githubRepo = created.name,
                        githubBranch = created.defaultBranch
                    )
                }
                val repos = githubRepo.fetchAccessibleRepos(token).getOrDefault(emptyList())
                _uiState.update { it.copy(githubAccessibleRepos = repos, isLoadingGitCi = false) }
                showMessage("Created repository ${created.fullName}")
                fetchGitHubRemote()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingGitCi = false, gitCiError = err.message) }
                showMessage("Create repo failed: ${err.message}", isError = true)
            }
        }
    }

    fun cloneGitHubRepoToWorkspace(owner: String, repo: String, branch: String) {
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            if (token.isBlank()) {
                showMessage("Authorize GitHub account first.", isError = true)
                return@launch
            }
            _uiState.update { it.copy(isPushingOrCloningGit = true, gitCiError = null) }
            val destDir = workspaceRepo.allocateProjectDir(repo)
            githubRepo.cloneRepoToDirectory(owner, repo, branch, token, destDir).onSuccess { fileCount ->
                val meta = workspaceRepo.registerClonedWorkspaceDir(destDir, repo)
                withContext(Dispatchers.IO) {
                    LocalGitEngine.initRepo(destDir, branch)
                    LocalGitEngine.saveRemoteConfig(destDir, owner, repo, branch)
                    LocalGitEngine.commitStaged(
                        destDir,
                        "Initial clone from $owner/$repo@$branch",
                        settings.value.gitAuthorName,
                        settings.value.gitAuthorEmail,
                        stageAllIfEmpty = true
                    )
                }
                refreshProjectsOnly()
                selectLiveCodingProject(meta.id)
                refreshLocalGit(meta)
                _uiState.update { it.copy(isPushingOrCloningGit = false) }
                showMessage("Cloned $owner/$repo ($fileCount files)")
            }.onFailure { err ->
                destDir.deleteRecursively()
                _uiState.update { it.copy(isPushingOrCloningGit = false, gitCiError = err.message) }
                showMessage("Clone failed: ${err.message}", isError = true)
            }
        }
    }

    fun pullRemoteBranchToProject(project: ProjectMetadata) {
        val s = settings.value
        if (s.githubOwner.isBlank() || s.githubRepo.isBlank()) {
            showMessage("Select or configure a GitHub owner/repo first.", isError = true)
            return
        }
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            if (token.isBlank()) {
                showMessage("Authorize GitHub token first.", isError = true)
                return@launch
            }
            _uiState.update { it.copy(isPushingOrCloningGit = true, gitCiError = null) }
            githubRepo.cloneRepoToDirectory(s.githubOwner, s.githubRepo, s.githubBranch, token, File(project.directoryPath))
                .onSuccess { count ->
                    refreshProjectsOnly()
                    refreshLocalGit(project)
                    if (_uiState.value.liveProjectId == project.id) selectLiveCodingProject(project.id)
                    _uiState.update { it.copy(isPushingOrCloningGit = false) }
                    showMessage("Pulled $count file(s) from ${s.githubOwner}/${s.githubRepo}@${s.githubBranch}")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isPushingOrCloningGit = false, gitCiError = err.message) }
                    showMessage("Pull failed: ${err.message}", isError = true)
                }
        }
    }

    fun pushProjectToGitHub(project: ProjectMetadata, commitMessage: String) {
        val s = settings.value
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            val config = GitRepoConfig(s.githubOwner, s.githubRepo, s.githubBranch, token.isNotBlank())
            if (!config.isConfigured) {
                showMessage("Configure GitHub repository and authorize token first.", isError = true)
                return@launch
            }
            val status = withContext(Dispatchers.IO) { LocalGitEngine.inspectStatus(File(project.directoryPath)) }
            val paths = (status.stagedFiles + status.unstagedFiles).map { it.relativePath }.distinct()
            if (paths.isEmpty()) {
                showMessage("No local changed files to push via Contents API.")
                return@launch
            }
            _uiState.update { it.copy(isPushingOrCloningGit = true, gitCiError = null) }
            githubRepo.pushFilesViaContentsApi(config, token, File(project.directoryPath), paths, commitMessage)
                .onSuccess { count ->
                    withContext(Dispatchers.IO) {
                        LocalGitEngine.commitStaged(
                            File(project.directoryPath),
                            commitMessage.ifBlank { "Push $count file(s) to GitHub" },
                            s.gitAuthorName,
                            s.gitAuthorEmail,
                            stageAllIfEmpty = true
                        )
                    }
                    refreshLocalGit(project)
                    _uiState.update { it.copy(isPushingOrCloningGit = false) }
                    showMessage("Pushed $count file(s) to ${config.owner}/${config.repo}")
                    fetchGitHubRemote()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isPushingOrCloningGit = false, gitCiError = err.message) }
                    showMessage("Push failed: ${err.message}", isError = true)
                }
        }
    }

    fun fetchGitHubRemote() {
        val s = settings.value
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            val config = GitRepoConfig(s.githubOwner, s.githubRepo, s.githubBranch, token.isNotBlank())
            _uiState.update { it.copy(isLoadingGitCi = true, gitCiError = null) }
            githubRepo.fetchRepoOverview(config, token).onSuccess { overview ->
                val rl = githubRepo.lastRateLimitRemaining?.let { rem ->
                    "Rate Limit: $rem/${githubRepo.lastRateLimitTotal ?: 5000}"
                }
                _uiState.update { it.copy(githubOverview = overview, githubRateLimitInfo = rl, isLoadingGitCi = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingGitCi = false, gitCiError = err.message) }
            }
        }
    }

    fun toggleCiPolling(enabled: Boolean) {
        ciPollingJob?.cancel()
        _uiState.update { it.copy(isPollingCi = enabled) }
        if (!enabled) return
        ciPollingJob = viewModelScope.launch {
            while (true) {
                val rem = githubRepo.lastRateLimitRemaining
                if (rem != null && rem < 25) {
                    showMessage("Paused CI auto-polling to preserve GitHub rate limit ($rem left)", isError = true)
                    _uiState.update { it.copy(isPollingCi = false) }
                    break
                }
                val s = settings.value
                val token = credentialStore.readGitHubToken()
                val config = GitRepoConfig(s.githubOwner, s.githubRepo, s.githubBranch, token.isNotBlank())
                if (!config.isConfigured) {
                    _uiState.update { it.copy(isPollingCi = false) }
                    break
                }
                githubRepo.fetchRepoOverview(config, token).onSuccess { overview ->
                    val rl = githubRepo.lastRateLimitRemaining?.let { r -> "Rate Limit: $r/${githubRepo.lastRateLimitTotal ?: 5000}" }
                    _uiState.update { it.copy(githubOverview = overview, githubRateLimitInfo = rl) }
                    val hasActive = overview.runs.any { it.status == "queued" || it.status == "in_progress" }
                    if (!hasActive) {
                        val latest = overview.runs.firstOrNull()
                        if (latest != null && settings.value.notifyBuildComplete) {
                            notificationManager.postTaskNotification(
                                id = 2002,
                                title = "Remote CI: ${latest.name}",
                                message = "Status: ${latest.conclusion ?: latest.status} on ${latest.branch}",
                                deepLinkRoute = "git"
                            )
                        }
                        _uiState.update { it.copy(isPollingCi = false) }
                        return@launch
                    }
                }.onFailure {
                    _uiState.update { it.copy(isPollingCi = false) }
                    return@launch
                }
                delay(8000L)
            }
        }
    }

    fun fetchCiRunLogs(runId: Long) {
        val s = settings.value
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            val config = GitRepoConfig(s.githubOwner, s.githubRepo, s.githubBranch, token.isNotBlank())
            githubRepo.fetchRunJobLogsSummary(config, token, runId).onSuccess { lines ->
                _uiState.update { it.copy(ciRunLogs = lines) }
            }.onFailure { showMessage("Log fetch failed: ${it.message}", isError = true) }
        }
    }

    fun downloadCiArtifact(artifact: CiArtifactInfo) {
        val s = settings.value
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            val config = GitRepoConfig(s.githubOwner, s.githubRepo, s.githubBranch, token.isNotBlank())
            _uiState.update { it.copy(isDownloadingArtifact = true) }
            githubRepo.downloadArtifactZipAndVerify(config, token, artifact, artifactsDir).onSuccess { rec ->
                val list = loadDownloadedArtifactsFromDisk()
                _uiState.update {
                    it.copy(
                        downloadedArtifacts = list,
                        isDownloadingArtifact = false,
                        storageBreakdown = envHealthEngine.computeStorageBreakdown(),
                        storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes()
                    )
                }
                val apkNote = if (rec.extractedApkPath != null) " • APK ready to inspect" else ""
                showMessage("Downloaded ${rec.name} (SHA-256 ${rec.sha256.take(10)}...)$apkNote")
                if (settings.value.notifyBuildComplete) {
                    notificationManager.postTaskNotification(
                        id = 2003,
                        title = "Artifact Downloaded",
                        message = "${rec.name} (${rec.sizeBytes / 1024} KB) verified on disk.",
                        deepLinkRoute = "git"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isDownloadingArtifact = false) }
                showMessage("Artifact download failed: ${err.message}", isError = true)
            }
        }
    }

    fun deleteDownloadedArtifact(record: DownloadedArtifactRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(record.localPath).delete()
                record.extractedDirPath?.let { File(it).deleteRecursively() }
            }
            _uiState.update {
                it.copy(
                    downloadedArtifacts = loadDownloadedArtifactsFromDisk(),
                    storageBreakdown = envHealthEngine.computeStorageBreakdown(),
                    storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes()
                )
            }
            showMessage("Deleted local artifact '${record.name}'")
        }
    }

    fun publishGitHubRelease(
        tagName: String,
        releaseName: String,
        changelog: String,
        draft: Boolean,
        assetFilePath: String? = null
    ) {
        val s = settings.value
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            val config = GitRepoConfig(s.githubOwner, s.githubRepo, s.githubBranch, token.isNotBlank())
            val asset = assetFilePath?.let { File(it) }?.takeIf { it.exists() }
            githubRepo.createRelease(config, token, tagName, releaseName, changelog, draft, asset)
                .onSuccess { url -> showMessage("Release created: $url") }
                .onFailure { showMessage("Release failed: ${it.message}", isError = true) }
        }
    }

    fun triggerWorkflow(workflowId: Long) {
        val s = settings.value
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            val config = GitRepoConfig(s.githubOwner, s.githubRepo, s.githubBranch, token.isNotBlank())
            githubRepo.triggerWorkflowDispatch(config, token, workflowId).onSuccess {
                showMessage("Workflow dispatch queued")
                fetchGitHubRemote()
                toggleCiPolling(true)
            }.onFailure {
                showMessage("Dispatch failed: ${it.message}", isError = true)
            }
        }
    }

    fun rerunOrCancelCiRun(runId: Long, cancel: Boolean) {
        val s = settings.value
        viewModelScope.launch {
            val token = credentialStore.readGitHubToken()
            val config = GitRepoConfig(s.githubOwner, s.githubRepo, s.githubBranch, token.isNotBlank())
            githubRepo.rerunOrCancelRun(config, token, runId, cancel).onSuccess {
                showMessage(if (cancel) "Cancellation requested" else "Rerun requested")
                fetchGitHubRemote()
            }.onFailure {
                showMessage("CI action failed: ${it.message}", isError = true)
            }
        }
    }

    private suspend fun loadDownloadedArtifactsFromDisk(): List<DownloadedArtifactRecord> = withContext(Dispatchers.IO) {
        if (!artifactsDir.exists()) return@withContext emptyList()
        (artifactsDir.listFiles()?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) } ?: emptyList())
            .sortedByDescending { it.lastModified() }
            .map { zip ->
                val base = zip.nameWithoutExtension
                val id = base.substringAfterLast('_').toLongOrNull() ?: zip.lastModified()
                val name = base.substringBeforeLast('_', base)
                val extractDir = File(artifactsDir, "extracted_$base")
                val apkInside = if (extractDir.exists()) {
                    extractDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                } else null
                val sha = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
                DownloadedArtifactRecord(
                    id = id,
                    name = name,
                    localPath = zip.absolutePath,
                    sizeBytes = zip.length(),
                    sha256 = sha,
                    downloadedAt = zip.lastModified(),
                    extractedDirPath = extractDir.takeIf { it.exists() }?.absolutePath,
                    extractedApkPath = apkInside?.absolutePath
                )
            }
    }

    // --- Security, App Lock PIN & Diagnostics ---
    fun runSecurityAudit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningSecurity = true) }
            val findings = securityEngine.runAudit(_uiState.value.projects, settings.value)
            val highCount = findings.count { it.severity == com.example.model.FindingSeverity.HIGH }
            _uiState.update { it.copy(securityFindings = findings, isScanningSecurity = false) }
            if (highCount > 0 && settings.value.notifySecurityAlerts) {
                notificationManager.postTaskNotification(
                    id = 3001,
                    title = "Security Audit: $highCount High-Risk Finding(s)",
                    message = "Review potential secrets or workflow risks in Security Diagnostics.",
                    deepLinkRoute = "security",
                    isSecurityAlert = true
                )
            }
        }
    }

    fun applySecurityRepair(finding: SecurityFinding) {
        viewModelScope.launch {
            securityEngine.applyRepair(finding).onSuccess { ok ->
                if (ok) {
                    showMessage("Applied repair to ${File(finding.filePath ?: "").name}")
                    runSecurityAudit()
                } else {
                    showMessage("Line changed since scan; re-run audit.", isError = true)
                }
            }
        }
    }

    fun exportPrivacyMetadata() {
        viewModelScope.launch {
            securityEngine.exportPrivacyMetadataSummary(_uiState.value.projects, settings.value)
                .onSuccess { file ->
                    _uiState.update { it.copy(lastPrivacyExportPath = file.absolutePath) }
                    showMessage("Exported sanitized privacy report: ${file.name}")
                }
                .onFailure { showMessage("Privacy export failed: ${it.message}", isError = true) }
        }
    }

    fun setAppLockPin(pin: String, autoLockSeconds: Int) {
        viewModelScope.launch {
            credentialStore.saveAppPin(pin).onSuccess {
                updateSettings { it.copy(pinLockEnabled = true, autoLockSeconds = autoLockSeconds) }
                val policy = credentialStore.loadPinLockStatus(enabled = true, autoLockSeconds = autoLockSeconds)
                _uiState.update { it.copy(appLockPolicy = policy, pinLockError = null) }
                showMessage("App Lock PIN configured (auto-lock ${autoLockSeconds}s)")
            }.onFailure { err ->
                showMessage("PIN setup failed: ${err.message}", isError = true)
            }
        }
    }

    fun disableAppLockPin() {
        viewModelScope.launch {
            credentialStore.clearAppPin()
            updateSettings { it.copy(pinLockEnabled = false) }
            _uiState.update {
                it.copy(
                    appLockPolicy = AppLockPolicyConfig(enabled = false, autoLockSeconds = settings.value.autoLockSeconds),
                    isAppLocked = false,
                    pinLockError = null
                )
            }
            showMessage("App Lock PIN disabled and cleared")
        }
    }

    fun lockAppNow() {
        if (credentialStore.hasAppPin() && settings.value.pinLockEnabled) {
            _uiState.update { it.copy(isAppLocked = true, pinLockError = null) }
        }
    }

    fun attemptPinUnlock(pin: String) {
        viewModelScope.launch {
            val res = credentialStore.attemptUnlockWithPin(pin)
            val updatedPolicy = credentialStore.loadPinLockStatus(
                enabled = settings.value.pinLockEnabled,
                autoLockSeconds = settings.value.autoLockSeconds
            )
            _uiState.update {
                it.copy(
                    appLockPolicy = updatedPolicy,
                    isAppLocked = !res.unlocked,
                    pinLockError = if (res.unlocked) null else res.message
                )
            }
        }
    }

    fun onAppBackgrounded() {
        lastBackgroundTimestampMs = System.currentTimeMillis()
    }

    fun checkAutoLockOnResume() {
        val s = settings.value
        if (!s.pinLockEnabled || !credentialStore.hasAppPin()) return
        val timeoutSec = s.autoLockSeconds
        if (timeoutSec == 0 || (lastBackgroundTimestampMs > 0L && System.currentTimeMillis() - lastBackgroundTimestampMs >= timeoutSec * 1000L)) {
            _uiState.update { it.copy(isAppLocked = true, pinLockError = null) }
        }
        lastBackgroundTimestampMs = 0L
    }

    // --- Backup & Revision ---
    fun createBackupSnapshot(project: ProjectMetadata, label: String, password: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            backupRepo.createSnapshot(project, label, password).onSuccess { snap ->
                val list = backupRepo.loadSnapshots()
                _uiState.update {
                    it.copy(
                        backups = list,
                        isBackupBusy = false,
                        storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes(),
                        storageBreakdown = envHealthEngine.computeStorageBreakdown()
                    )
                }
                showMessage("Created snapshot '${snap.name}'")
            }.onFailure {
                _uiState.update { it.copy(isBackupBusy = false) }
                showMessage("Backup failed: ${it.message}", isError = true)
            }
        }
    }

    fun verifyBackup(snapshot: BackupSnapshot) {
        viewModelScope.launch {
            val (_, msg) = backupRepo.verifyIntegrity(snapshot)
            _uiState.update { it.copy(backupIntegrityMessage = msg) }
            showMessage(msg)
        }
    }

    fun previewBackupDiff(snapshot: BackupSnapshot, project: ProjectMetadata, password: String?) {
        viewModelScope.launch {
            backupRepo.inspectDiffAgainstCurrent(snapshot, project, password).onSuccess { diff ->
                _uiState.update { it.copy(backupDiffPreview = diff) }
            }.onFailure {
                showMessage("Diff failed: ${it.message}", isError = true)
            }
        }
    }

    fun restoreBackup(snapshot: BackupSnapshot, project: ProjectMetadata, password: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            backupRepo.restoreSnapshot(snapshot, project, password).onSuccess { count ->
                val list = backupRepo.loadSnapshots()
                refreshProjectsOnly()
                _uiState.update { it.copy(backups = list, isBackupBusy = false, backupDiffPreview = null) }
                showMessage("Restored $count files (pre-rollback backup saved)")
            }.onFailure {
                _uiState.update { it.copy(isBackupBusy = false) }
                showMessage("Restore failed: ${it.message}", isError = true)
            }
        }
    }

    fun deleteBackup(snapshot: BackupSnapshot) {
        viewModelScope.launch {
            backupRepo.deleteSnapshot(snapshot)
            _uiState.update {
                it.copy(
                    backups = backupRepo.loadSnapshots(),
                    storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes(),
                    storageBreakdown = envHealthEngine.computeStorageBreakdown()
                )
            }
            showMessage("Snapshot deleted")
        }
    }

    // --- Asset Studio (#26), QA Suite (#42), Code Map (#10/#11/#13), Storage Cleanup (#36), DB Backup (#43) ---
    fun resizeAndSaveAsset(
        uri: Uri,
        project: ProjectMetadata,
        outputName: String,
        targetSize: Int,
        quality: Int,
        format: AssetStudioEngine.AssetOutputFormat
    ) {
        viewModelScope.launch {
            assetStudioEngine.resizeAndSaveAsset(uri, project, outputName, targetSize, quality, format)
                .onSuccess { file ->
                    refreshProjectsOnly()
                    showMessage("Saved asset ${file.name}")
                }
                .onFailure { showMessage("Asset failed: ${it.message}", isError = true) }
        }
    }

    fun generateCodedAsset(project: ProjectMetadata, label: String, bgArgb: Int, accentArgb: Int, sizePx: Int) {
        viewModelScope.launch {
            assetStudioEngine.generateCodedBrandAsset(project, label, bgArgb, accentArgb, sizePx)
                .onSuccess { file ->
                    refreshProjectsOnly()
                    showMessage("Generated ${file.name}")
                }
                .onFailure { showMessage("Generation failed: ${it.message}", isError = true) }
        }
    }

    fun runQaSuite() {
        viewModelScope.launch {
            val results = testQaEngine.runFullSuite(_uiState.value.projects, settings.value, db)
            _uiState.update {
                it.copy(
                    qaResults = results,
                    envHealth = envHealthEngine.inspectHealth(),
                    storageBreakdown = envHealthEngine.computeStorageBreakdown(),
                    incrementalCacheCount = dao.countCacheEntries()
                )
            }
        }
    }

    fun scanProjectCodeMap(project: ProjectMetadata) {
        viewModelScope.launch {
            val report = codeMapEngine.scanProject(project)
            _uiState.update { it.copy(codeMapReport = report) }
        }
    }

    fun toggleProjectPermission(project: ProjectMetadata, permission: String, add: Boolean) {
        viewModelScope.launch {
            codeMapEngine.toggleProjectPermission(project, permission, add).onSuccess {
                scanProjectCodeMap(project)
                showMessage(if (add) "Added $permission" else "Removed $permission")
            }
        }
    }

    fun cleanSafeCaches() {
        viewModelScope.launch {
            val freed = envHealthEngine.cleanSafeCaches()
            _uiState.update {
                it.copy(
                    storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes(),
                    storageBreakdown = envHealthEngine.computeStorageBreakdown()
                )
            }
            showMessage("Cleaned ${freed / 1024} KB temporary cache (source/APKs/keys preserved)")
        }
    }

    fun backupRoomDatabase() {
        viewModelScope.launch {
            dbIntegrity.backupDatabaseFile().onSuccess { file ->
                runQaSuite()
                showMessage("Database backed up (${file.name})")
            }
        }
    }

    // --- Part 5: My Applications, Full Workspace+Settings Bundle, Asset Audit & ZIP Explorer ---
    fun upgradeApplicationVersion(
        projectId: String,
        appName: String,
        packageId: String,
        versionName: String,
        versionCode: Int
    ) {
        val proj = _uiState.value.projects.firstOrNull { it.id == projectId } ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                MyApplicationsEngine.updateProjectVersionSpec(proj, appName, packageId, versionName, versionCode)
            }.onSuccess {
                _uiState.update { st ->
                    st.copy(
                        selectedBuildProjectId = proj.id,
                        buildConfigSpec = st.buildConfigSpec.copy(
                            appName = appName,
                            packageId = packageId,
                            versionName = versionName,
                            versionCode = versionCode
                        )
                    )
                }
                refreshProjectsOnly()
                showMessage("Updated $appName to v$versionName ($versionCode)")
            }.onFailure { err ->
                showMessage("Version update failed: ${err.message}", isError = true)
            }
        }
    }

    fun prepareAppRebuild(app: MyApplicationRecord) {
        val proj = app.sourceProjectId?.let { id -> _uiState.value.projects.firstOrNull { it.id == id } } ?: return
        _uiState.update { st ->
            st.copy(
                selectedBuildProjectId = proj.id,
                selectedBuildMode = BuildMode.MANUAL,
                buildConfigSpec = st.buildConfigSpec.copy(
                    appName = app.appName,
                    packageId = app.packageId,
                    versionName = app.versionName,
                    versionCode = app.versionCode + 1,
                    signingAlias = app.signingAlias
                )
            )
        }
        refreshBuildDiagnostics(proj.id)
    }

    fun createFullWorkspaceAndSettingsBackup(label: String, password: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            val json = SettingsDataStore.exportSanitizedSettingsJson(settings.value)
            backupRepo.createStudioBundleSnapshot(_uiState.value.projects, json, label, password).onSuccess { snap ->
                val list = backupRepo.loadSnapshots()
                _uiState.update {
                    it.copy(
                        backups = list,
                        isBackupBusy = false,
                        storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes(),
                        storageBreakdown = envHealthEngine.computeStorageBreakdown()
                    )
                }
                showMessage("Created full studio bundle '${snap.name}' (excludes private signing keys)")
            }.onFailure { err ->
                _uiState.update { it.copy(isBackupBusy = false) }
                showMessage("Studio bundle failed: ${err.message}", isError = true)
            }
        }
    }

    fun scanAssetResources(project: ProjectMetadata) {
        viewModelScope.launch {
            val items = assetStudioEngine.scanProjectResources(project)
            _uiState.update { it.copy(resourceAuditItems = items) }
            showMessage("Asset scan complete: ${items.size} finding(s) in '${project.name}'")
        }
    }

    fun deleteAuditedAsset(project: ProjectMetadata, item: ResourceAuditItem) {
        viewModelScope.launch {
            val deleted = runCatching { assetStudioEngine.deleteProjectAsset(project, item.absolutePath) }.getOrDefault(false)
            if (deleted) {
                refreshProjectsOnly()
                scanAssetResources(project)
                showMessage("Deleted '${item.relativePath}'")
            } else {
                showMessage("Unable to delete '${item.relativePath}'", isError = true)
            }
        }
    }

    fun inspectUtilityZipUri(uri: Uri, onResult: (Result<ZipInspectSummary>) -> Unit) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot open selected ZIP URI.")
                    stream.use { UtilityToolsEngine.inspectZipStream(it).getOrThrow() }
                }
            }
            onResult(res)
        }
    }

    fun exportUtilityOutputToUri(destUri: Uri, content: String) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(destUri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Cannot write to destination file.")
                }
            }
            res.onSuccess { showMessage("Exported output file") }
                .onFailure { showMessage("Export failed: ${it.message}", isError = true) }
        }
    }

    private suspend fun refreshProjectsOnly() {
        val projects = workspaceRepo.loadProjects()
        _uiState.update {
            it.copy(
                projects = projects,
                myApplications = MyApplicationsEngine.deriveApplications(projects, it.buildHistory, it.signingKeys, it.downloadedArtifacts),
                storageUsageBytes = workspaceRepo.getTotalStorageUsageBytes(),
                storageBreakdown = envHealthEngine.computeStorageBreakdown()
            )
        }
    }

    private fun sortNodes(nodes: List<WorkspaceNode>, mode: ExplorerSortMode): List<WorkspaceNode> {
        val dirs = nodes.filter { it.isDirectory }
        val files = nodes.filterNot { it.isDirectory }
        val sortedDirs = dirs.sortedBy { it.name.lowercase() }
        val sortedFiles = when (mode) {
            ExplorerSortMode.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            ExplorerSortMode.SIZE_DESC -> files.sortedByDescending { it.sizeBytes }
            ExplorerSortMode.MODIFIED_DESC -> files.sortedByDescending { it.lastModified }
            ExplorerSortMode.TYPE -> files.sortedBy { it.extension }
        }
        return sortedDirs + sortedFiles
    }
}
