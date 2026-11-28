package com.example.model

import com.example.ui.theme.StudioThemeId
import com.example.ui.theme.ThemeBrightnessMode

enum class ProjectType(val label: String, val badge: String) {
    WEB_APP("Web App", "HTML5"),
    WEB_TO_APK("Web-to-APK", "WAP"),
    ANDROID_KOTLIN("Android Kotlin", "KT"),
    BLANK("Blank Workspace", "DIR")
}

enum class ProjectFilter {
    ALL, RECENT, FAVORITE, ARCHIVED
}

enum class ExplorerSortMode(val label: String) {
    NAME_ASC("Name (A-Z)"),
    SIZE_DESC("Size (Largest)"),
    MODIFIED_DESC("Recently Modified"),
    TYPE("File Type")
}

data class ProjectMetadata(
    val id: String,
    val name: String,
    val directoryPath: String,
    val type: ProjectType,
    val createdAt: Long,
    val updatedAt: Long,
    val sizeBytes: Long,
    val fileCount: Int,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val lastBuildStatus: String = "Not Built",
    val entryFile: String = "index.html",
    val packageId: String = ""
)

data class WorkspaceNode(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val childCount: Int = 0
)

data class OpenEditorTab(
    val projectId: String,
    val relativePath: String,
    val absolutePath: String,
    val fileName: String,
    val content: String,
    val savedContent: String,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList(),
    val cursorLine: Int = 1,
    val cursorCol: Int = 1
) {
    val isDirty: Boolean get() = content != savedContent
}

enum class PreviewViewport(val label: String, val widthDp: Int?) {
    MOBILE("Mobile 360dp", 360),
    TABLET("Tablet 768dp", 768),
    FULL("Full Width", null)
}

enum class LiveCodingLayoutMode(val label: String) {
    SPLIT("Split"),
    EDITOR_ONLY("Editor"),
    PREVIEW_ONLY("Preview")
}

data class ConsoleLogEntry(
    val id: String,
    val timestamp: Long,
    val level: String, // INFO, WARN, ERROR, LOG
    val message: String,
    val source: String
)

data class ApkEntryInfo(
    val path: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val method: String,
    val isAligned: Boolean
)

data class ApkSigningCertInfo(
    val subject: String,
    val issuer: String,
    val sha256Fingerprint: String,
    val algorithm: String
)

data class ApkInspectionReport(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val sha1: String,
    val md5: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val entries: List<ApkEntryInfo>,
    val dexCount: Int,
    val abis: List<String>,
    val isZipAligned: Boolean,
    val signatureSchemes: List<String>,
    val signingCerts: List<ApkSigningCertInfo>,
    val isValidApk: Boolean,
    val isVerifiedSignature: Boolean,
    val parseNotes: List<String>
)

data class ApkComparisonResult(
    val leftName: String,
    val rightName: String,
    val sizeDeltaBytes: Long,
    val addedFiles: List<String>,
    val removedFiles: List<String>,
    val modifiedFiles: List<String>,
    val addedPermissions: List<String>,
    val removedPermissions: List<String>,
    val sha256Match: Boolean,
    val signatureMatch: Boolean
)

enum class BuildMode(val label: String, val description: String) {
    AUTO("Auto Build", "Analyzes project and proposes verified config diff before building"),
    MANUAL("Manual Build", "Full control over app ID, SDK targets, permissions, and signing"),
    CUSTOM("Custom Build", "Advanced compiler flags, archive compression, and step controls")
}

enum class BuildProfile(
    val label: String,
    val description: String,
    val workers: Int,
    val cleanCacheFirst: Boolean,
    val useIncrementalCache: Boolean,
    val lowMemoryMode: Boolean,
    val debuggable: Boolean,
    val strictValidation: Boolean
) {
    AUTO("Auto", "Automatically matches project type and balanced 2-worker cache", 2, false, true, false, true, true),
    MANUAL("Manual", "Step-by-step confirmation with explicit packaging flags", 2, false, true, false, true, true),
    CUSTOM("Custom", "Uses user compiler, compression, and signing overrides", 2, false, true, false, false, true),
    FAST("Fast", "Max 4 workers with incremental SHA-256 cache reuse", 4, false, true, false, true, false),
    STABLE("Stable", "Strict schema/syntax verification with balanced workers", 2, false, true, false, true, true),
    CLEAN("Clean", "Purges incremental cache before full clean re-verification", 2, true, false, false, false, true),
    LOW_RAM("Low RAM", "Single-worker throttled I/O for memory-constrained devices", 1, false, true, true, true, true),
    DEBUG("Debug", "Includes diagnostic manifest checks and fast bundle metadata", 2, false, true, false, true, false),
    RELEASE("Release", "Strict security/manifest checks and signing verification", 2, true, false, false, false, true),
    WEB_BUNDLE("Web-to-APK Bundle", "Packages HTML/CSS/JS + Kotlin WebView wrapper project", 2, false, true, false, false, true),
    NATIVE("Native Android", "Runs Kotlin/Gradle compatibility audit & verified source check", 2, false, true, false, true, true)
}

enum class BuildStage(val label: String) {
    IDLE("Idle"),
    PREPARING("Preparing"),
    SCANNING("Scanning"),
    VALIDATING("Validating"),
    COMPILING("Compiling"),
    PACKAGING("Packaging"),
    SIGNING("Signing"),
    VERIFYING("Verifying"),
    DONE("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class BuildConfigSpec(
    val appName: String = "TK App",
    val packageId: String = "com.tkofficial.app",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val minSdk: Int = 24,
    val targetSdk: Int = 36,
    val orientation: String = "unspecified", // unspecified, portrait, landscape
    val splashTitle: String = "",
    val statusBarDarkText: Boolean = false,
    val enableJs: Boolean = true,
    val domStorage: Boolean = true,
    val allowlistDomains: String = "",
    val signingAlias: String = "",
    val outputFileName: String = "",
    val compressionLevel: Int = 6,
    val customCompilerFlags: String = "-Xlint:strict"
)

data class BuildPlanDiffItem(
    val setting: String,
    val currentValue: String,
    val proposedValue: String,
    val reason: String
)

data class AutoBuildProposal(
    val detectedType: ProjectType,
    val targetArtifactLabel: String,
    val canCompileBinaryApkLocally: Boolean,
    val proposedConfig: BuildConfigSpec,
    val diffItems: List<BuildPlanDiffItem>,
    val summaryNote: String
)

data class BuildStepRecord(
    val stepNumber: Int,
    val title: String,
    val stage: BuildStage,
    val status: String, // PENDING, RUNNING, PASS, WARN, FAIL, SKIPPED, CANCELLED
    val startedAt: Long,
    val durationMs: Long,
    val detail: String,
    val errorCode: String? = null,
    val sourceLocation: String? = null
)

enum class BuildReportFormat(val extension: String, val label: String) {
    TXT("txt", "Plain Text (.txt)"),
    JSON("json", "JSON (.json)"),
    HTML("html", "HTML Report (.html)")
}

data class BuildRecord(
    val id: String,
    val projectId: String,
    val projectName: String,
    val profile: BuildProfile,
    val mode: BuildMode = BuildMode.AUTO,
    val stage: BuildStage,
    val success: Boolean,
    val startedAt: Long,
    val durationMs: Long,
    val outputPath: String?,
    val outputSha256: String?,
    val logLines: List<String>,
    val progressFraction: Float? = null,
    val artifactKind: String = "VERIFIED_BUNDLE",
    val errorCode: String? = null,
    val failedStage: String? = null,
    val sourceLocation: String? = null,
    val reportId: String = id,
    val steps: List<BuildStepRecord> = emptyList(),
    val appName: String = projectName,
    val packageId: String = "",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val signingAlias: String = "",
    val signingFingerprint: String = ""
)

data class WebWrapperPreview(
    val generatedKotlin: String,
    val generatedManifest: String,
    val configJson: String,
    val assetRefs: List<String>,
    val missingAssets: List<String>,
    val securityDefaults: List<String>,
    val frameworkLimitations: List<String>,
    val suggestedPermissions: List<String>
)

data class NativeCompatIssue(
    val severity: String, // BLOCKING, WARNING, INFO
    val file: String,
    val line: Int?,
    val message: String,
    val canSafeRepair: Boolean = false
)

data class NativeCompatibilityReport(
    val projectName: String,
    val isLocallyCompilable: Boolean,
    val hasGradleFiles: Boolean,
    val detectedPlugins: List<String>,
    val detectedDependencies: List<String>,
    val missingToolchainItems: List<String>,
    val issues: List<NativeCompatIssue>,
    val canSafeRepairManifest: Boolean,
    val recommendation: String
)

data class ToolchainItem(
    val id: String,
    val name: String,
    val category: String,
    val isAvailable: Boolean,
    val version: String,
    val path: String,
    val sha256: String,
    val abi: String,
    val details: String,
    val lastTestResult: String? = null,
    val executable: Boolean = false,
    val licenseInfo: String = "System / Standard",
    val compatibilityNote: String = "Device runtime verified",
    val missingGuidance: String = ""
)

data class ToolchainEnvironmentReport(
    val items: List<ToolchainItem>,
    val readyCount: Int,
    val totalCount: Int,
    val completenessPercent: Int,
    val supportedAbis: List<String>,
    val maxHeapMb: Long,
    val availableRamMb: Long,
    val freeStorageMb: Long,
    val summaryNote: String
)

data class SigningKeyInfo(
    val alias: String,
    val commonName: String,
    val orgUnit: String,
    val organization: String,
    val locality: String,
    val state: String,
    val country: String,
    val algorithm: String,
    val keySize: Int,
    val validityYears: Int,
    val createdAt: Long,
    val sha256Fingerprint: String,
    val keystoreFileName: String
)

data class ApkSdkCompatibilityReport(
    val minSdk: Int?,
    val targetSdk: Int?,
    val deviceSdk: Int,
    val isInstallableOnDevice: Boolean,
    val statusLabel: String,
    val guidance: String
)

data class GitRepoConfig(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val hasToken: Boolean = false
) {
    val isConfigured: Boolean get() = owner.isNotBlank() && repo.isNotBlank() && hasToken
}

data class GitDiffFile(
    val relativePath: String,
    val changeType: String, // ADDED, MODIFIED, DELETED
    val diffPreview: String,
    val staged: Boolean = false
)

data class GitConflictItem(
    val relativePath: String,
    val oursContent: String,
    val theirsContent: String,
    val baseContent: String = ""
)

data class GitStashEntry(
    val id: String,
    val label: String,
    val branch: String,
    val createdAt: Long,
    val fileCount: Int
)

data class GitMergePreview(
    val sourceBranch: String,
    val targetBranch: String,
    val cleanFiles: List<String>,
    val conflictingFiles: List<GitConflictItem>,
    val isClean: Boolean = conflictingFiles.isEmpty()
)

data class GitStatusSummary(
    val initialized: Boolean = false,
    val currentBranch: String = "main",
    val branches: List<String> = listOf("main"),
    val stagedFiles: List<GitDiffFile> = emptyList(),
    val unstagedFiles: List<GitDiffFile> = emptyList(),
    val conflicts: List<GitConflictItem> = emptyList(),
    val stashes: List<GitStashEntry> = emptyList(),
    val remoteOwner: String = "",
    val remoteRepo: String = "",
    val remoteBranch: String = "main",
    val hasSystemGitBinary: Boolean = false,
    val runtimeNote: String = "System git binary is not bundled on Android; TK Pure-Kotlin Snapshot & GitHub REST Sync engine active."
)

data class GitCommitRecord(
    val id: String,
    val message: String,
    val author: String,
    val timestamp: Long,
    val fileCount: Int,
    val isRemote: Boolean = false,
    val branch: String = "main"
)

data class GitHubDeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSec: Int,
    val intervalSec: Int
)

data class GitHubAccountProfile(
    val login: String,
    val name: String,
    val htmlUrl: String,
    val scopes: List<String>
)

data class GitHubRemoteRepo(
    val id: Long,
    val fullName: String,
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val permissionsSummary: String,
    val htmlUrl: String,
    val canPush: Boolean,
    val stars: Int
)

data class CiWorkflowInfo(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

data class CiWorkflowRun(
    val id: Long,
    val name: String,
    val status: String, // queued, in_progress, completed
    val conclusion: String?, // success, failure, cancelled, null
    val branch: String,
    val createdAt: String,
    val htmlUrl: String,
    val commitSha: String = "",
    val actor: String = "",
    val runNumber: Int = 0
)

data class CiArtifactInfo(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val expired: Boolean,
    val createdAt: String
)

data class DownloadedArtifactRecord(
    val id: Long,
    val name: String,
    val localPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadedAt: Long,
    val extractedDirPath: String? = null,
    val extractedApkPath: String? = null
)

data class WorkflowAuditIssue(
    val severity: String, // ERROR, WARNING, INFO
    val message: String
)

data class WorkflowSecurityAudit(
    val isValid: Boolean,
    val issues: List<WorkflowAuditIssue>,
    val permissionsChecked: Boolean,
    val pinnedActionsChecked: Boolean,
    val hasOptInPushTrigger: Boolean,
    val hasPullRequestSecretRisk: Boolean
)

data class WorkflowDiffPreview(
    val fileName: String,
    val existedBefore: Boolean,
    val backupFileName: String,
    val diffText: String,
    val proposedYaml: String
)

data class CiReadinessItem(
    val label: String,
    val passed: Boolean,
    val detail: String
)

enum class FindingSeverity(val label: String) {
    HIGH("High Risk"),
    MEDIUM("Advisory"),
    LOW("Info"),
    PASS("Verified")
}

data class SecurityFinding(
    val id: String,
    val severity: FindingSeverity,
    val category: String,
    val title: String,
    val description: String,
    val filePath: String? = null,
    val lineNumber: Int? = null,
    val redactedPreview: String? = null,
    val canRepair: Boolean = false,
    val repairOriginalLine: String? = null,
    val repairReplacementLine: String? = null
)

data class BackupSnapshot(
    val id: String,
    val name: String,
    val projectId: String?,
    val projectName: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val sha256: String,
    val encrypted: Boolean,
    val fileCount: Int,
    val filePath: String
)

enum class TutorialDiagramType {
    WORKSPACE_FLOW,
    LIVE_CODING_SPLIT,
    APK_ANATOMY,
    SIGNING_VAULT,
    GITHUB_CI_PIPELINE,
    BACKUP_ROLLBACK
}

data class TutorialStep(
    val title: String,
    val instruction: String,
    val targetScreenHint: String
)

data class TutorialLesson(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val durationMinutes: Int,
    val prerequisites: List<String>,
    val steps: List<TutorialStep>,
    val diagramType: TutorialDiagramType,
    val seriesKey: String = "",
    val expectedResult: String = "",
    val commonError: String = "",
    val safetyNote: String? = null,
    val checkpointQuestion: String = "",
    val checkpointAnswerHint: String = "",
    val targetRoute: String = "home"
)

enum class SigningContinuityState(val label: String) {
    MATCHED("Key Verified"),
    KEY_MISMATCH("Signing Key Changed"),
    KEY_MISSING("Key Unavailable"),
    UNSIGNED("Unsigned"),
    ARTIFACT_MISSING("Artifact Missing")
}

data class MyApplicationRecord(
    val id: String,
    val appName: String,
    val packageId: String,
    val versionName: String,
    val versionCode: Int,
    val sourceProjectId: String?,
    val sourceProjectName: String,
    val sourceProjectExists: Boolean,
    val lastBuildAt: Long,
    val buildType: String,
    val artifactKind: String,
    val artifactPath: String?,
    val artifactExists: Boolean,
    val outputSha256: String?,
    val signingAlias: String,
    val signingFingerprint: String,
    val continuityState: SigningContinuityState,
    val continuityNote: String
)

data class ResourceAuditItem(
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val issueType: String, // DUPLICATE, LARGE, UNUSED
    val detail: String
)

data class AppLockPolicyConfig(
    val enabled: Boolean = false,
    val autoLockSeconds: Int = 300,
    val failedAttempts: Int = 0,
    val lockoutUntilMs: Long = 0L,
    val hasPinConfigured: Boolean = false
)

data class AppSettingsState(
    val themeId: StudioThemeId = StudioThemeId.CYBER_BLUE,
    val brightnessMode: ThemeBrightnessMode = ThemeBrightnessMode.DARK,
    val reduceMotion: Boolean = false,
    val lowPerformanceMode: Boolean = false,
    val animSpeedMultiplier: Float = 1.0f,
    val startupRoute: String = "home",
    val openLastProjectOnStartup: Boolean = true,
    val confirmOnExit: Boolean = true,
    val recentProjectsLimit: Int = 5,
    val editorFontSizeSp: Int = 13,
    val editorTabSize: Int = 2,
    val editorWordWrap: Boolean = false,
    val editorLineNumbers: Boolean = true,
    val editorSyntaxHighlight: Boolean = true,
    val editorAutoIndent: Boolean = true,
    val editorHighlightCurrentLine: Boolean = true,
    val editorLargeFileMode: Boolean = false,
    val editorAutosave: Boolean = true,
    val editorAutosaveIntervalSec: Int = 0,
    val previewAutoRefresh: Boolean = true,
    val previewEnableJs: Boolean = true,
    val previewShowConsoleByDefault: Boolean = false,
    val defaultSplitRatio: Float = 0.5f,
    val defaultPreviewViewport: PreviewViewport = PreviewViewport.MOBILE,
    val defaultBuildProfile: BuildProfile = BuildProfile.AUTO,
    val maxBuildWorkers: Int = 2,
    val buildOnSave: Boolean = false,
    val cleanCacheBeforeBuild: Boolean = false,
    val outputNamePrefix: String = "tk_build",
    val strictSecurityScan: Boolean = true,
    val zipSlipGuardEnabled: Boolean = true,
    val webViewSafeMode: Boolean = true,
    val backupEncryptByDefault: Boolean = false,
    val notifyBuildComplete: Boolean = true,
    val notifySecurityAlerts: Boolean = true,
    val notifyToolchainAlerts: Boolean = true,
    val notifyStorageAlerts: Boolean = true,
    val notifyBackupAlerts: Boolean = true,
    val highContrastBorders: Boolean = false,
    val largeTouchTargets: Boolean = true,
    val languageCode: String = "en",
    val developerDiagnostics: Boolean = false,
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val githubClientId: String = "",
    val hasGitHubToken: Boolean = false,
    val gitAuthorName: String = "TK OFFICIAL",
    val gitAuthorEmail: String = "tkofficial68@gmail.com",
    val confirmGitDestructive: Boolean = true,
    val gitPullStrategy: String = "merge",
    val apkConfirmInstall: Boolean = true,
    val autoBuildOnPush: Boolean = false,
    val pinLockEnabled: Boolean = false,
    val autoLockSeconds: Int = 300,
    val bookmarkedTutorials: Set<String> = emptySet(),
    val completedTutorialSteps: Set<String> = emptySet(),
    val highResPreviewScale: Boolean = true,
    val glowIntensity: Float = 1.0f,
    val glassOpacity: Float = 0.88f,
    val compactMode: Boolean = false,
    val textScale: Float = 1.0f
)
