package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.LocalizationEngine
import com.example.data.StudioLanguage
import com.example.ui.components.GlassCard
import com.example.ui.components.NavTabCanvasIcon
import com.example.ui.components.StudioBackground
import com.example.ui.components.StudioBrandMark
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.screens.ApkToolScreen
import com.example.ui.screens.AssetStudioScreen
import com.example.ui.screens.BackupRevisionScreen
import com.example.ui.screens.BuildCenterScreen
import com.example.ui.screens.GitCiScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveCodingScreen
import com.example.ui.screens.MyApplicationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProjectScreen
import com.example.ui.screens.SecurityDiagnosticsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SigningWizardScreen
import com.example.ui.screens.TestQaScreen
import com.example.ui.screens.ToolchainScreen
import com.example.ui.screens.TutorialsScreen
import com.example.ui.screens.UtilityToolsScreen
import com.example.ui.theme.StudioMotionConfig
import com.example.ui.theme.StudioTheme
import com.example.ui.theme.TKStudioTheme
import com.example.viewmodel.StudioViewModel

object StudioRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_PROJECT = "project"
    const val ROUTE_LIVE_CODING = "live_coding"
    const val ROUTE_APK_TOOL = "apk_tool"
    const val ROUTE_PROFILE = "profile"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_MY_APPLICATIONS = "my_applications"
    const val ROUTE_BUILD_CENTER = "build_center"
    const val ROUTE_TOOLCHAIN = "toolchain"
    const val ROUTE_GIT_CI = "git_ci"
    const val ROUTE_SIGNING_WIZARD = "signing_wizard"
    const val ROUTE_TUTORIALS = "tutorials"
    const val ROUTE_SECURITY_DIAGNOSTICS = "security_diagnostics"
    const val ROUTE_BACKUP_REVISION = "backup_revision"
    const val ROUTE_ASSET_STUDIO = "asset_studio"
    const val ROUTE_UTILITIES = "utilities"
    const val ROUTE_TEST_QA = "test_qa"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    private val pendingDeepLinkUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkUri.value = intent?.data
        enableEdgeToEdge()
        setContent {
            TKStudioAppRoot(
                deepLinkUri = pendingDeepLinkUri.value,
                onDeepLinkHandled = { pendingDeepLinkUri.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkUri.value = intent.data
    }
}

private fun resolveDeepLinkRoute(uri: Uri?): String? {
    val host = uri?.host?.lowercase() ?: return null
    return when (host) {
        "home" -> StudioRoutes.ROUTE_HOME
        "project", "projects" -> StudioRoutes.ROUTE_PROJECT
        "live", "live_coding" -> StudioRoutes.ROUTE_LIVE_CODING
        "apk", "apk_tool" -> StudioRoutes.ROUTE_APK_TOOL
        "profile" -> StudioRoutes.ROUTE_PROFILE
        "settings" -> StudioRoutes.ROUTE_SETTINGS
        "apps", "my_applications" -> StudioRoutes.ROUTE_MY_APPLICATIONS
        "build", "build_center" -> StudioRoutes.ROUTE_BUILD_CENTER
        "tools", "toolchain" -> StudioRoutes.ROUTE_TOOLCHAIN
        "git", "ci" -> StudioRoutes.ROUTE_GIT_CI
        "signing" -> StudioRoutes.ROUTE_SIGNING_WIZARD
        "tutorials" -> StudioRoutes.ROUTE_TUTORIALS
        "security" -> StudioRoutes.ROUTE_SECURITY_DIAGNOSTICS
        "backup" -> StudioRoutes.ROUTE_BACKUP_REVISION
        "assets", "asset_studio" -> StudioRoutes.ROUTE_ASSET_STUDIO
        "utilities" -> StudioRoutes.ROUTE_UTILITIES
        "qa", "test" -> StudioRoutes.ROUTE_TEST_QA
        else -> null
    }
}

@Composable
fun TKStudioAppRoot(
    deepLinkUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {},
    viewModel: StudioViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsInitialCategory by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showExitConfirm by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.checkAutoLockOnResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val motionConfig = remember(
        settings.reduceMotion,
        settings.lowPerformanceMode,
        settings.animSpeedMultiplier,
        settings.glowIntensity,
        settings.glassOpacity,
        settings.compactMode,
        settings.textScale,
        settings.highContrastBorders
    ) {
        StudioMotionConfig(
            reduceMotion = settings.reduceMotion,
            lowPerformanceMode = settings.lowPerformanceMode,
            animSpeedMultiplier = settings.animSpeedMultiplier,
            glowIntensity = settings.glowIntensity,
            glassOpacity = settings.glassOpacity,
            compactMode = settings.compactMode,
            textScale = settings.textScale,
            highContrastBorders = settings.highContrastBorders
        )
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    val lang = remember(settings.languageCode) { StudioLanguage.fromCode(settings.languageCode) }
    val strings = remember(settings.languageCode) { LocalizationEngine.stringsFor(settings.languageCode) }
    val layoutDirection = if (lang.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    // EXACT bottom nav order & labels in English:
    // Home / Project / Live Coding / APK Tool / Profile
    val bottomNavTabs = remember(strings) {
        listOf(
            BottomNavTab(StudioRoutes.ROUTE_HOME, strings.navHome, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
            BottomNavTab(StudioRoutes.ROUTE_PROJECT, strings.navProject, Icons.Rounded.Folder, Icons.Outlined.Folder, "nav_project"),
            BottomNavTab(StudioRoutes.ROUTE_LIVE_CODING, strings.navLiveCoding, Icons.Rounded.Code, Icons.Outlined.Code, "nav_live_coding"),
            BottomNavTab(StudioRoutes.ROUTE_APK_TOOL, strings.navApkTool, Icons.Rounded.VerifiedUser, Icons.Outlined.VerifiedUser, "nav_apk_tool"),
            BottomNavTab(StudioRoutes.ROUTE_PROFILE, strings.navProfile, Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        TKStudioTheme(
            themeId = settings.themeId,
            brightnessMode = settings.brightnessMode,
            motionConfig = motionConfig
        ) {
            val navController = rememberNavController()
            val navBackStack by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStack?.destination?.route ?: StudioRoutes.ROUTE_HOME
            val isTopTab = bottomNavTabs.any { it.route == currentRoute }
            var appliedStartupRoute by remember { mutableStateOf(false) }

            BackHandler(enabled = currentRoute == StudioRoutes.ROUTE_HOME && settings.confirmOnExit && !showExitConfirm) {
                showExitConfirm = true
            }

            fun navigateTab(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            LaunchedEffect(uiState.isInitializing, deepLinkUri) {
                if (!appliedStartupRoute && !uiState.isInitializing && deepLinkUri == null) {
                    appliedStartupRoute = true
                    val startTarget = settings.startupRoute
                    if (startTarget != StudioRoutes.ROUTE_HOME && bottomNavTabs.any { it.route == startTarget }) {
                        navigateTab(startTarget)
                    }
                }
            }

            LaunchedEffect(deepLinkUri) {
                val target = resolveDeepLinkRoute(deepLinkUri)
                if (target != null) {
                    appliedStartupRoute = true
                    if (bottomNavTabs.any { it.route == target }) navigateTab(target)
                    else navController.navigate(target) { launchSingleTop = true }
                    onDeepLinkHandled()
                }
            }

            fun openSettings(category: String? = null) {
                settingsInitialCategory = category
                navController.navigate(StudioRoutes.ROUTE_SETTINGS)
            }

            StudioBackground {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 720.dp

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useRail && isTopTab) {
                            val p = StudioTheme.palette
                            NavigationRail(
                                containerColor = p.elevated.copy(alpha = 0.92f),
                                contentColor = p.text,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            ) {
                                Spacer(Modifier.height(12.dp))
                                bottomNavTabs.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTab(tab.route) },
                                        icon = {
                                            NavTabCanvasIcon(
                                                route = tab.route,
                                                selected = selected,
                                                tint = if (selected) p.cyan else p.textSecondary,
                                                contentDescription = tab.label,
                                                size = 22.dp
                                            )
                                        },
                                        label = { Text(tab.label, maxLines = 1) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = p.cyan,
                                            selectedTextColor = p.text,
                                            indicatorColor = p.primary.copy(alpha = 0.25f),
                                            unselectedIconColor = p.textSecondary,
                                            unselectedTextColor = p.textSecondary
                                        ),
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }

                        Scaffold(
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets.safeDrawing,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                if (!useRail && isTopTab && !uiState.isFullscreenPreview) {
                                    StudioBottomNavigationBar(
                                        tabs = bottomNavTabs,
                                        currentRoute = currentRoute,
                                        onSelectRoute = ::navigateTab
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { innerPadding ->
                            val transitionMs = StudioTheme.motion.standardDurationMs
                            NavHost(
                                navController = navController,
                                startDestination = StudioRoutes.ROUTE_HOME,
                                enterTransition = { fadeIn(tween(transitionMs)) },
                                exitTransition = { fadeOut(tween(transitionMs)) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                composable(StudioRoutes.ROUTE_HOME) {
                                    HomeScreen(
                                        state = uiState,
                                        settings = settings,
                                        onCreateProject = viewModel::createProject,
                                        onImportZip = viewModel::importZipProject,
                                        onCancelImport = viewModel::cancelImport,
                                        onOpenProjectInLiveCoding = { id -> viewModel.selectLiveCodingProject(id) },
                                        onOpenProjectExplorer = { proj ->
                                            viewModel.openProjectExplorer(proj, "")
                                            navigateTab(StudioRoutes.ROUTE_PROJECT)
                                        },
                                        onNavigateLiveCoding = { navigateTab(StudioRoutes.ROUTE_LIVE_CODING) },
                                        onNavigateBuildCenter = { navController.navigate(StudioRoutes.ROUTE_BUILD_CENTER) },
                                        onNavigateApkInspector = { navigateTab(StudioRoutes.ROUTE_APK_TOOL) },
                                        onNavigateToolchain = { navController.navigate(StudioRoutes.ROUTE_TOOLCHAIN) },
                                        onNavigateGitCi = { navController.navigate(StudioRoutes.ROUTE_GIT_CI) },
                                        onNavigateTutorials = { navController.navigate(StudioRoutes.ROUTE_TUTORIALS) },
                                        onNavigateSigning = { navController.navigate(StudioRoutes.ROUTE_SIGNING_WIZARD) },
                                        onNavigateSecurity = { navController.navigate(StudioRoutes.ROUTE_SECURITY_DIAGNOSTICS) },
                                        onNavigateBackup = { navController.navigate(StudioRoutes.ROUTE_BACKUP_REVISION) },
                                        onNavigateAssetStudio = { navController.navigate(StudioRoutes.ROUTE_ASSET_STUDIO) },
                                        onNavigateUtilities = { navController.navigate(StudioRoutes.ROUTE_UTILITIES) },
                                        onNavigateTestQa = { navController.navigate(StudioRoutes.ROUTE_TEST_QA) },
                                        onNavigateSettings = { openSettings(null) },
                                        onRestoreDraft = viewModel::restoreRecoveryDraft,
                                        onDismissDraft = viewModel::dismissRecoveryDraft,
                                        onQuickBuild = { viewModel.startBuild() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_PROJECT) {
                                    ProjectScreen(
                                        state = uiState,
                                        onCreateProject = viewModel::createProject,
                                        onImportZip = viewModel::importZipProject,
                                        onCancelImport = viewModel::cancelImport,
                                        onExportZip = viewModel::exportProjectZip,
                                        onSetFilter = viewModel::setProjectFilter,
                                        onSetTypeFilter = viewModel::setProjectTypeFilter,
                                        onSetSearch = viewModel::setProjectSearch,
                                        onToggleGridView = viewModel::toggleProjectGridView,
                                        onOpenExplorer = viewModel::openProjectExplorer,
                                        onCloseExplorer = viewModel::closeProjectExplorer,
                                        onRenameProject = viewModel::renameProject,
                                        onDuplicateProject = viewModel::duplicateProject,
                                        onToggleFavorite = viewModel::toggleFavoriteProject,
                                        onToggleArchive = viewModel::toggleArchiveProject,
                                        onBackupProject = { proj ->
                                            viewModel.createBackupSnapshot(proj, "Manual Backup", null)
                                        },
                                        onDeleteProject = viewModel::deleteProject,
                                        onOpenFileInLiveCoding = { projId, node ->
                                            viewModel.selectLiveCodingProject(projId)
                                            viewModel.openFileInLiveCoding(projId, node)
                                            navigateTab(StudioRoutes.ROUTE_LIVE_CODING)
                                        },
                                        onSetExplorerSearch = viewModel::setExplorerSearch,
                                        onSetExplorerSort = viewModel::setExplorerSort,
                                        onToggleMultiSelect = viewModel::toggleExplorerMultiSelect,
                                        onToggleNodeSelect = viewModel::toggleExplorerNodeSelection,
                                        onCreateExplorerEntry = viewModel::createExplorerEntry,
                                        onRenameExplorerNode = viewModel::renameExplorerNode,
                                        onDeleteSelectedNodes = viewModel::deleteSelectedExplorerNodes,
                                        onImportFileToExplorer = viewModel::importFileIntoExplorer
                                    )
                                }

                                composable(StudioRoutes.ROUTE_LIVE_CODING) {
                                    LiveCodingScreen(
                                        state = uiState,
                                        settings = settings,
                                        onSelectProject = viewModel::selectLiveCodingProject,
                                        onOpenFile = viewModel::openFileInLiveCoding,
                                        onSelectTab = viewModel::selectEditorTab,
                                        onCloseTab = viewModel::closeEditorTab,
                                        onUpdateText = viewModel::updateActiveEditorText,
                                        onSave = viewModel::saveActiveEditorTab,
                                        onUndo = viewModel::undoActiveEditor,
                                        onRedo = viewModel::redoActiveEditor,
                                        onReplace = viewModel::replaceInActiveEditor,
                                        onSetLayoutMode = viewModel::setLiveLayoutMode,
                                        onSetSplitRatio = viewModel::setSplitRatio,
                                        onSetViewport = viewModel::setPreviewViewport,
                                        onToggleFullscreen = viewModel::setFullscreenPreview,
                                        onRefreshPreview = viewModel::refreshWebPreview,
                                        onConsoleLog = viewModel::appendConsoleLog,
                                        onClearConsole = viewModel::clearConsoleLogs,
                                        onNavigateProjects = { navigateTab(StudioRoutes.ROUTE_PROJECT) },
                                        onNavigateBuildCenter = { navController.navigate(StudioRoutes.ROUTE_BUILD_CENTER) }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_APK_TOOL) {
                                    ApkToolScreen(
                                        state = uiState,
                                        apkRepo = viewModel.apkRepo,
                                        onInspectSelfApk = viewModel::inspectSelfApk,
                                        onInspectApkUri = viewModel::inspectApkUri,
                                        onExtractPrimaryApk = viewModel::extractPrimaryApk,
                                        onNavigateSigningWizard = { navController.navigate(StudioRoutes.ROUTE_SIGNING_WIZARD) },
                                        onNavigateBuildCenter = { navController.navigate(StudioRoutes.ROUTE_BUILD_CENTER) },
                                        onShowMessage = viewModel::showMessage
                                    )
                                }

                                composable(StudioRoutes.ROUTE_PROFILE) {
                                    ProfileScreen(
                                        state = uiState,
                                        settings = settings,
                                        onOpenSettings = { cat -> openSettings(cat) },
                                        onNavigateMyApplications = { navController.navigate(StudioRoutes.ROUTE_MY_APPLICATIONS) },
                                        onNavigateProjects = { navigateTab(StudioRoutes.ROUTE_PROJECT) },
                                        onNavigateApkTool = { navigateTab(StudioRoutes.ROUTE_APK_TOOL) },
                                        onNavigateBuildCenter = { navController.navigate(StudioRoutes.ROUTE_BUILD_CENTER) },
                                        onNavigateToolchain = { navController.navigate(StudioRoutes.ROUTE_TOOLCHAIN) },
                                        onNavigateGitCi = { navController.navigate(StudioRoutes.ROUTE_GIT_CI) },
                                        onNavigateSigning = { navController.navigate(StudioRoutes.ROUTE_SIGNING_WIZARD) },
                                        onNavigateTutorials = { navController.navigate(StudioRoutes.ROUTE_TUTORIALS) },
                                        onNavigateSecurity = { navController.navigate(StudioRoutes.ROUTE_SECURITY_DIAGNOSTICS) },
                                        onNavigateBackup = { navController.navigate(StudioRoutes.ROUTE_BACKUP_REVISION) },
                                        onNavigateAssetStudio = { navController.navigate(StudioRoutes.ROUTE_ASSET_STUDIO) },
                                        onNavigateUtilities = { navController.navigate(StudioRoutes.ROUTE_UTILITIES) },
                                        onNavigateTestQa = { navController.navigate(StudioRoutes.ROUTE_TEST_QA) }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_MY_APPLICATIONS) {
                                    MyApplicationsScreen(
                                        state = uiState,
                                        settings = settings,
                                        apkRepo = viewModel.apkRepo,
                                        onOpenSourceProject = { proj ->
                                            viewModel.openProjectExplorer(proj, "")
                                            navigateTab(StudioRoutes.ROUTE_PROJECT)
                                        },
                                        onEditCodeProject = { projId ->
                                            viewModel.selectLiveCodingProject(projId)
                                            navigateTab(StudioRoutes.ROUTE_LIVE_CODING)
                                        },
                                        onUpgradeVersion = viewModel::upgradeApplicationVersion,
                                        onPrepareRebuild = viewModel::prepareAppRebuild,
                                        onCompareApkFile = { file ->
                                            viewModel.inspectApkFile(file, isSecondary = false)
                                            navigateTab(StudioRoutes.ROUTE_APK_TOOL)
                                        },
                                        onBackupProject = { proj ->
                                            viewModel.createBackupSnapshot(proj, "Pre-Upgrade Backup", null)
                                        },
                                        onOpenBuildHistory = { navController.navigate(StudioRoutes.ROUTE_BUILD_CENTER) },
                                        onNavigateBuildCenter = { navController.navigate(StudioRoutes.ROUTE_BUILD_CENTER) },
                                        onShowMessage = viewModel::showMessage,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_SETTINGS) {
                                    SettingsScreen(
                                        initialCategory = settingsInitialCategory,
                                        settings = settings,
                                        state = uiState,
                                        onUpdateSettings = viewModel::updateSettings,
                                        onSetTheme = viewModel::setTheme,
                                        onSetBrightness = viewModel::setBrightness,
                                        onOpenToolchain = { navController.navigate(StudioRoutes.ROUTE_TOOLCHAIN) },
                                        onOpenSecurity = { navController.navigate(StudioRoutes.ROUTE_SECURITY_DIAGNOSTICS) },
                                        onOpenSigning = { navController.navigate(StudioRoutes.ROUTE_SIGNING_WIZARD) },
                                        onOpenBackup = { navController.navigate(StudioRoutes.ROUTE_BACKUP_REVISION) },
                                        onOpenGitCi = { navController.navigate(StudioRoutes.ROUTE_GIT_CI) },
                                        onOpenTestQa = { navController.navigate(StudioRoutes.ROUTE_TEST_QA) },
                                        onOpenUtilities = { navController.navigate(StudioRoutes.ROUTE_UTILITIES) },
                                        onOpenApkTool = { navigateTab(StudioRoutes.ROUTE_APK_TOOL) },
                                        onOpenTutorials = { navController.navigate(StudioRoutes.ROUTE_TUTORIALS) },
                                        onCleanSafeCaches = viewModel::cleanSafeCaches,
                                        onResetAllSettings = viewModel::resetAllSettingsToDefaults,
                                        onExportSettingsJson = viewModel::exportSanitizedSettingsJsonFile,
                                        onSaveGitHubConfig = viewModel::saveGitHubConfig,
                                        onDisconnectGitHub = viewModel::disconnectGitHub,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_BUILD_CENTER) {
                                    BuildCenterScreen(
                                        state = uiState,
                                        onSelectProject = viewModel::selectBuildProject,
                                        onSelectMode = viewModel::selectBuildMode,
                                        onSelectProfile = viewModel::selectBuildProfile,
                                        onUpdateConfig = viewModel::updateBuildConfigSpec,
                                        onApplyAutoConfig = viewModel::applyProposedAutoConfig,
                                        onApplyNativeSafeRepair = viewModel::applyNativeSafeManifestRepair,
                                        onRefreshPreflight = { viewModel.refreshPreflight() },
                                        onStartBuild = { pwd -> viewModel.startBuild(pwd) },
                                        onCancelBuild = viewModel::cancelBuild,
                                        onExportReport = viewModel::exportBuildReport,
                                        onOpenToolchain = { navController.navigate(StudioRoutes.ROUTE_TOOLCHAIN) },
                                        onShowMessage = { msg -> viewModel.showMessage(msg) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_TOOLCHAIN) {
                                    ToolchainScreen(
                                        state = uiState,
                                        onRefresh = viewModel::refreshToolchain,
                                        onSelfTest = viewModel::runToolchainSelfTest,
                                        onImportTool = viewModel::importToolchainFile,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_GIT_CI) {
                                    GitCiScreen(
                                        state = uiState,
                                        settings = settings,
                                        onInitLocalGit = viewModel::initLocalGit,
                                        onRefreshLocalGit = viewModel::refreshLocalGit,
                                        onStageLocalFile = viewModel::stageLocalFile,
                                        onUnstageLocalFile = viewModel::unstageLocalFile,
                                        onStageAllLocalFiles = viewModel::stageAllLocalFiles,
                                        onCommitLocalGit = viewModel::commitLocalGit,
                                        onCreateBranch = viewModel::createGitBranch,
                                        onSwitchBranch = viewModel::switchGitBranch,
                                        onDeleteBranch = viewModel::deleteGitBranch,
                                        onPreviewMerge = viewModel::previewGitMerge,
                                        onDismissMergePreview = viewModel::dismissGitMergePreview,
                                        onApplyMerge = viewModel::applyGitMerge,
                                        onResolveConflict = viewModel::resolveGitConflict,
                                        onStashChanges = viewModel::stashLocalChanges,
                                        onPopStash = viewModel::popLocalStash,
                                        onResetWorkingTree = viewModel::resetLocalWorkingTree,
                                        onPreviewWorkflowYaml = viewModel::previewWorkflowYaml,
                                        onDismissWorkflowPreview = viewModel::dismissWorkflowDiffPreview,
                                        onSaveWorkflowYaml = viewModel::saveWorkflowYamlToProject,
                                        onSaveGitHubConfig = viewModel::saveGitHubConfig,
                                        onDisconnectGitHub = viewModel::disconnectGitHub,
                                        onStartDeviceFlow = viewModel::startGitHubDeviceFlow,
                                        onPollDeviceToken = viewModel::pollGitHubDeviceToken,
                                        onFetchAccountRepos = viewModel::fetchGitHubAccountAndRepos,
                                        onCreateRemoteRepo = viewModel::createRemoteGitHubRepo,
                                        onCloneRepo = viewModel::cloneGitHubRepoToWorkspace,
                                        onPullRemote = viewModel::pullRemoteBranchToProject,
                                        onPushRemote = viewModel::pushProjectToGitHub,
                                        onFetchGitHub = viewModel::fetchGitHubRemote,
                                        onTogglePolling = viewModel::toggleCiPolling,
                                        onFetchRunLogs = viewModel::fetchCiRunLogs,
                                        onDownloadArtifact = viewModel::downloadCiArtifact,
                                        onInspectArtifactApk = { apkFile ->
                                            viewModel.inspectApkFile(apkFile, isSecondary = false)
                                            navigateTab(StudioRoutes.ROUTE_APK_TOOL)
                                        },
                                        onDeleteArtifact = viewModel::deleteDownloadedArtifact,
                                        onPublishRelease = viewModel::publishGitHubRelease,
                                        onTriggerWorkflow = viewModel::triggerWorkflow,
                                        onRerunOrCancelRun = viewModel::rerunOrCancelCiRun,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_SIGNING_WIZARD) {
                                    SigningWizardScreen(
                                        state = uiState,
                                        onCreateKey = viewModel::createSigningKey,
                                        onVerifyKeyPassword = viewModel::verifySigningKeyPassword,
                                        onExportEncryptedKey = viewModel::exportEncryptedSigningKey,
                                        onDeleteKey = viewModel::deleteSigningKey,
                                        onNavigateApkInspector = { navigateTab(StudioRoutes.ROUTE_APK_TOOL) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_TUTORIALS) {
                                    TutorialsScreen(
                                        settings = settings,
                                        onToggleBookmark = viewModel::toggleTutorialBookmark,
                                        onToggleStep = viewModel::toggleTutorialStep,
                                        onNavigateToRoute = { route ->
                                            if (bottomNavTabs.any { it.route == route }) {
                                                navigateTab(route)
                                            } else {
                                                navController.navigate(route) { launchSingleTop = true }
                                            }
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_SECURITY_DIAGNOSTICS) {
                                    SecurityDiagnosticsScreen(
                                        state = uiState,
                                        onRunAudit = viewModel::runSecurityAudit,
                                        onApplyRepair = viewModel::applySecurityRepair,
                                        onSetAppLockPin = viewModel::setAppLockPin,
                                        onDisableAppLockPin = viewModel::disableAppLockPin,
                                        onLockNow = viewModel::lockAppNow,
                                        onExportPrivacyMetadata = viewModel::exportPrivacyMetadata,
                                        onWipeTempCache = viewModel::cleanSafeCaches,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_BACKUP_REVISION) {
                                    BackupRevisionScreen(
                                        state = uiState,
                                        settings = settings,
                                        onCreateSnapshot = viewModel::createBackupSnapshot,
                                        onVerifyIntegrity = viewModel::verifyBackup,
                                        onPreviewDiff = viewModel::previewBackupDiff,
                                        onRestoreSnapshot = viewModel::restoreBackup,
                                        onDeleteSnapshot = viewModel::deleteBackup,
                                        onCreateFullBundle = viewModel::createFullWorkspaceAndSettingsBackup,
                                        onRestoreDraft = viewModel::restoreRecoveryDraft,
                                        onDismissDraft = viewModel::dismissRecoveryDraft,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_ASSET_STUDIO) {
                                    AssetStudioScreen(
                                        state = uiState,
                                        onResizeImageAsset = viewModel::resizeAndSaveAsset,
                                        onGenerateCodedAsset = viewModel::generateCodedAsset,
                                        onScanResources = viewModel::scanAssetResources,
                                        onDeleteAsset = viewModel::deleteAuditedAsset,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_UTILITIES) {
                                    UtilityToolsScreen(
                                        onInspectZipUri = viewModel::inspectUtilityZipUri,
                                        onExportOutputUri = viewModel::exportUtilityOutputToUri,
                                        onShowMessage = { msg -> viewModel.showMessage(msg) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(StudioRoutes.ROUTE_TEST_QA) {
                                    TestQaScreen(
                                        state = uiState,
                                        onRunQaSuite = viewModel::runQaSuite,
                                        onScanProjectCodeMap = viewModel::scanProjectCodeMap,
                                        onToggleProjectPermission = viewModel::toggleProjectPermission,
                                        onCleanSafeCaches = viewModel::cleanSafeCaches,
                                        onBackupDatabase = viewModel::backupRoomDatabase,
                                        onClearIncrementalCache = viewModel::clearIncrementalCache,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.isAppLocked) {
                    var pinInput by remember { mutableStateOf("") }
                    val p = StudioTheme.palette
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(p.background.copy(alpha = 0.97f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(glowAccent = p.primary, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StudioBrandMark(size = 52.dp)
                                Text("TK Studio Locked", style = MaterialTheme.typography.headlineSmall, color = p.text)
                                Text("Enter your App Lock PIN to unlock workspace", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) pinInput = it },
                                    label = { Text("PIN") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                uiState.pinLockError?.let { err ->
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = p.error)
                                }
                                StudioPrimaryButton(
                                    text = "Unlock",
                                    onClick = {
                                        viewModel.attemptPinUnlock(pinInput)
                                        pinInput = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                if (showExitConfirm) {
                    StudioDialog(
                        title = strings.exitConfirmTitle,
                        onDismiss = { showExitConfirm = false },
                        confirmText = strings.exitConfirmAction,
                        dismissText = strings.cancelLabel,
                        onConfirm = {
                            showExitConfirm = false
                            (context as? Activity)?.finish()
                        }
                    ) {
                        Text(
                            text = strings.exitConfirmBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTheme.palette.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioBottomNavigationBar(
    tabs: List<BottomNavTab>,
    currentRoute: String,
    onSelectRoute: (String) -> Unit
) {
    val p = StudioTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(p.glassSurface.copy(alpha = 0.94f), p.elevated.copy(alpha = 0.98f))
                )
            )
            .border(width = 1.dp, color = p.border)
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag("bottom_navigation_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) p.primary.copy(alpha = 0.18f) else Color.Transparent)
                        .then(
                            if (selected) Modifier.border(1.dp, p.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .clickable { onSelectRoute(tab.route) }
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                        .testTag(tab.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NavTabCanvasIcon(
                        route = tab.route,
                        selected = selected,
                        tint = if (selected) p.cyan else p.textSecondary,
                        contentDescription = tab.label,
                        size = 22.dp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) p.text else p.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
