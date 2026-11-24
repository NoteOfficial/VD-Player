package com.tkofficial.videodownloder

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.tkofficial.videodownloder.core.DeveloperIdentity
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.models.AppLanguage
import com.tkofficial.videodownloder.models.IconStyle
import com.tkofficial.videodownloder.models.NavStyle
import com.tkofficial.videodownloder.navigation.AppRoute
import com.tkofficial.videodownloder.navigation.BackupRestoreRoute
import com.tkofficial.videodownloder.navigation.BrowserRoute
import com.tkofficial.videodownloder.navigation.DownloadHistoryRoute
import com.tkofficial.videodownloder.navigation.FileManagerRoute
import com.tkofficial.videodownloder.navigation.GalleryRoute
import com.tkofficial.videodownloder.navigation.HomeRoute
import com.tkofficial.videodownloder.navigation.LegalLicensesRoute
import com.tkofficial.videodownloder.navigation.MediaStudioRoute
import com.tkofficial.videodownloder.navigation.NotificationCenterRoute
import com.tkofficial.videodownloder.navigation.PlayerRoute
import com.tkofficial.videodownloder.navigation.ProfileRoute
import com.tkofficial.videodownloder.navigation.SecurityCenterRoute
import com.tkofficial.videodownloder.navigation.SettingsCenterRoute
import com.tkofficial.videodownloder.navigation.StorageAnalyzerRoute
import com.tkofficial.videodownloder.navigation.ThemeStudioRoute
import com.tkofficial.videodownloder.navigation.VaultRoute
import com.tkofficial.videodownloder.navigation.VideoPlaybackRoute
import com.tkofficial.videodownloder.ui.browser.BrowserScreen
import com.tkofficial.videodownloder.ui.browser.BrowserViewModel
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaIconButton
import com.tkofficial.videodownloder.ui.components.VaOutlinedButton
import com.tkofficial.videodownloder.ui.components.VaPrimaryButton
import com.tkofficial.videodownloder.ui.file_manager.BackupRestoreScreen
import com.tkofficial.videodownloder.ui.file_manager.DownloadHistoryScreen
import com.tkofficial.videodownloder.ui.file_manager.FileManagerScreen
import com.tkofficial.videodownloder.ui.file_manager.NotificationCenterScreen
import com.tkofficial.videodownloder.ui.file_manager.StorageAnalyzerScreen
import com.tkofficial.videodownloder.ui.gallery.GalleryScreen
import com.tkofficial.videodownloder.ui.gallery.GalleryViewModel
import com.tkofficial.videodownloder.ui.home.HomeScreen
import com.tkofficial.videodownloder.ui.home.HomeViewModel
import com.tkofficial.videodownloder.ui.legal.LegalLicensesScreen
import com.tkofficial.videodownloder.ui.media_studio.MediaStudioScreen
import com.tkofficial.videodownloder.ui.player.PlayerScreen
import com.tkofficial.videodownloder.ui.player.PlayerViewModel
import com.tkofficial.videodownloder.ui.player.VideoPlayerScreen
import com.tkofficial.videodownloder.ui.profile.ProfileScreen
import com.tkofficial.videodownloder.ui.profile.ProfileViewModel
import com.tkofficial.videodownloder.ui.settings.SettingsCenterScreen
import com.tkofficial.videodownloder.ui.themes.DownloaderVaTheme
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.ui.themes.ThemeStudioScreen
import com.tkofficial.videodownloder.ui.vault.SecurityCenterScreen
import com.tkofficial.videodownloder.ui.vault.VaultScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TopLevelTab(
    val route: AppRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : AppCompatActivity() {

    private var pendingSharedUrl = mutableStateOf<String?>(null)
    private var pendingNavigateTarget = mutableStateOf<String?>(null)
    private var pendingOpenCompleted = mutableStateOf(false)
    private var isOnVideoPlayerRoute = false
    private val isInPipModeState = mutableStateOf(false)
    private var backgroundedAtMs = 0L
    private var forceRelockSignal = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addOnPictureInPictureModeChangedListener { info ->
                isInPipModeState.value = info.isInPictureInPictureMode
            }
        }

        setContent {
            val homeViewModel: HomeViewModel = viewModel()
            val galleryViewModel: GalleryViewModel = viewModel()
            val playerViewModel: PlayerViewModel = viewModel()
            val browserViewModel: BrowserViewModel = viewModel()
            val profileViewModel: ProfileViewModel = viewModel()

            val settings by profileViewModel.settings.collectAsStateWithLifecycle()
            val isSettingsLoaded by profileViewModel.isSettingsLoaded.collectAsStateWithLifecycle()
            val pinState by profileViewModel.pinState.collectAsStateWithLifecycle()

            var showSplash by rememberSaveable { mutableStateOf(true) }
            var appSessionUnlocked by rememberSaveable { mutableStateOf(false) }

            val needsAppLock = settings.appLockEnabled && pinState.first != null

            // Auto-lock check when returning from background
            val relockTick by forceRelockSignal
            LaunchedEffect(relockTick, settings.appLockEnabled, settings.autoLockSeconds) {
                if (relockTick > 0L && settings.appLockEnabled && backgroundedAtMs > 0L) {
                    val elapsedSec = (System.currentTimeMillis() - backgroundedAtMs) / 1000L
                    if (elapsedSec >= settings.autoLockSeconds.coerceAtLeast(1)) {
                        appSessionUnlocked = false
                        profileViewModel.lockVault()
                    }
                    backgroundedAtMs = 0L
                }
            }

            // Hide Thumbnails in Recents (FLAG_SECURE)
            DisposableEffect(settings.hideThumbnailsInRecents) {
                if (settings.hideThumbnailsInRecents) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose {}
            }

            // Dismiss splash as soon as persisted settings/language/theme load from DataStore
            LaunchedEffect(isSettingsLoaded) {
                if (isSettingsLoaded) {
                    delay(80L)
                    showSplash = false
                }
            }

            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val localizedConfig = remember(baseConfig, settings.language) {
                LocaleHelper.buildLocalizedConfiguration(baseConfig, settings.language)
            }
            val localizedContext = remember(baseContext, settings.language, baseConfig) {
                LocaleHelper.wrapContext(baseContext, settings.language)
            }
            val layoutDirection = if (settings.language == AppLanguage.ARABIC) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
                LocalLayoutDirection provides layoutDirection
            ) {
                DownloaderVaTheme(
                    config = settings.themeConfig,
                    batterySaver = settings.batterySaverMode,
                    lowEndMode = settings.lowEndDeviceMode,
                    highContrast = settings.highContrastLabels
                ) {
                val tokens = LocalVaTokens.current
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDest = navBackStackEntry?.destination

                val startRoute: AppRoute = remember(settings.startDestination) {
                    when (settings.startDestination.lowercase()) {
                        "gallery" -> GalleryRoute
                        "player" -> PlayerRoute
                        "browser" -> BrowserRoute
                        "profile" -> ProfileRoute
                        else -> HomeRoute
                    }
                }

                // Track whether VideoPlaybackRoute is active for PiP and connect MediaController lazily
                val onVideoRoute = currentDest?.hasRoute(VideoPlaybackRoute::class) == true
                val onPlayerOrVideo = currentDest?.hasRoute(PlayerRoute::class) == true || onVideoRoute
                LaunchedEffect(onVideoRoute) {
                    isOnVideoPlayerRoute = onVideoRoute
                }
                LaunchedEffect(onPlayerOrVideo) {
                    if (onPlayerOrVideo) {
                        playerViewModel.ensureControllerConnected()
                    }
                }

                // Handle Share-Intent URL or Notification tap
                val incomingUrl by pendingSharedUrl
                LaunchedEffect(incomingUrl) {
                    incomingUrl?.let { url ->
                        homeViewModel.analyzeSingleLink(url)
                        navController.navigate(HomeRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        pendingSharedUrl.value = null
                    }
                }

                val goTarget by pendingNavigateTarget
                val openCompleted by pendingOpenCompleted
                LaunchedEffect(goTarget, openCompleted) {
                    val target = goTarget
                    if (target != null) {
                        if (openCompleted) {
                            homeViewModel.openCompletedTab()
                            pendingOpenCompleted.value = false
                        }
                        when (target) {
                            "player" -> navController.navigate(PlayerRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            "notifications" -> navController.navigate(NotificationCenterRoute) {
                                launchSingleTop = true
                            }
                            else -> navController.navigate(HomeRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        pendingNavigateTarget.value = null
                    }
                }

                val tabs = remember {
                    listOf(
                        TopLevelTab(HomeRoute, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
                        TopLevelTab(GalleryRoute, R.string.nav_gallery, Icons.Rounded.PhotoLibrary, Icons.Outlined.PhotoLibrary, "nav_gallery"),
                        TopLevelTab(PlayerRoute, R.string.nav_player, Icons.Rounded.PlayCircle, Icons.Outlined.PlayCircle, "nav_player"),
                        TopLevelTab(BrowserRoute, R.string.nav_browser, Icons.Rounded.Public, Icons.Outlined.Public, "nav_browser"),
                        TopLevelTab(ProfileRoute, R.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
                    )
                }

                val showPrimaryNav = !isInPipModeState.value && (
                    currentDest == null ||
                        currentDest.hasRoute(HomeRoute::class) ||
                        currentDest.hasRoute(GalleryRoute::class) ||
                        currentDest.hasRoute(PlayerRoute::class) ||
                        currentDest.hasRoute(BrowserRoute::class) ||
                        currentDest.hasRoute(ProfileRoute::class)
                    )

                fun navigateToTopTab(route: AppRoute) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(tokens.background)) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useRail = maxWidth >= 600.dp && showPrimaryNav

                        Row(modifier = Modifier.fillMaxSize()) {
                            if (useRail) {
                                VaNavigationRail(
                                    tabs = tabs,
                                    isSelected = { tab -> currentDest?.hasRoute(tab.route::class) == true },
                                    onSelect = ::navigateToTopTab
                                )
                            }

                            val allowGlobalMiniBar = !isInPipModeState.value &&
                                currentDest?.hasRoute(PlayerRoute::class) != true &&
                                currentDest?.hasRoute(VideoPlaybackRoute::class) != true

                            Scaffold(
                                containerColor = tokens.background,
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                contentWindowInsets = if (onVideoRoute) {
                                    WindowInsets(0)
                                } else {
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom
                                    )
                                },
                                bottomBar = {
                                    Column {
                                        if (allowGlobalMiniBar) {
                                            GlobalMiniMusicBarHost(
                                                playerViewModel = playerViewModel,
                                                onOpenPlayer = { navigateToTopTab(PlayerRoute) }
                                            )
                                        }
                                        if (showPrimaryNav && !useRail) {
                                            VaBottomBar(
                                                tabs = tabs,
                                                isSelected = { tab -> currentDest?.hasRoute(tab.route::class) == true },
                                                onSelect = ::navigateToTopTab
                                            )
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                val navAnimMs = if (tokens.reduceMotion) 0 else tokens.animDurationMs.coerceIn(140, 300)
                                if (isSettingsLoaded) {
                                NavHost(
                                    navController = navController,
                                    startDestination = startRoute,
                                    enterTransition = {
                                        fadeIn(tween(navAnimMs)) + slideInHorizontally(tween(navAnimMs)) { it / 14 }
                                    },
                                    exitTransition = {
                                        fadeOut(tween(navAnimMs)) + slideOutHorizontally(tween(navAnimMs)) { -it / 18 }
                                    },
                                    popEnterTransition = {
                                        fadeIn(tween(navAnimMs)) + slideInHorizontally(tween(navAnimMs)) { -it / 14 }
                                    },
                                    popExitTransition = {
                                        fadeOut(tween(navAnimMs)) + slideOutHorizontally(tween(navAnimMs)) { it / 18 }
                                    },
                                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                                ) {
                                    composable<HomeRoute> {
                                        HomeScreen(
                                            viewModel = homeViewModel,
                                            snackbarHostState = snackbarHostState,
                                            onOpenNotifications = { navController.navigate(NotificationCenterRoute) },
                                            onOpenProfile = { navigateToTopTab(ProfileRoute) },
                                            onOpenSettings = { navController.navigate(SettingsCenterRoute("hub")) },
                                            onOpenBrowser = { navigateToTopTab(BrowserRoute) },
                                            onOpenFileManager = { navController.navigate(FileManagerRoute) },
                                            onOpenMediaStudio = { navController.navigate(MediaStudioRoute) },
                                            onOpenVault = { navController.navigate(VaultRoute) },
                                            onOpenStorageAnalyzer = { navController.navigate(StorageAnalyzerRoute) },
                                            onOpenHistory = { navController.navigate(DownloadHistoryRoute) },
                                            onPlayVideo = { uri, title ->
                                                navController.navigate(VideoPlaybackRoute(uri, title))
                                            }
                                        )
                                    }

                                    composable<GalleryRoute> {
                                        GalleryScreen(
                                            viewModel = galleryViewModel,
                                            gridColumns = settings.galleryGridColumns,
                                            slideshowSeconds = settings.gallerySlideshowSeconds,
                                            showFileNames = settings.galleryShowFileNames,
                                            snackbarHostState = snackbarHostState,
                                            onPlayVideo = { uri, title ->
                                                navController.navigate(VideoPlaybackRoute(uri, title))
                                            }
                                        )
                                    }

                                    composable<PlayerRoute> {
                                        PlayerScreen(
                                            viewModel = playerViewModel,
                                            onOpenVideoPlayer = { uri, title ->
                                                navController.navigate(VideoPlaybackRoute(uri, title))
                                            }
                                        )
                                    }

                                    composable<BrowserRoute> {
                                        BrowserScreen(
                                            viewModel = browserViewModel,
                                            snackbarHostState = snackbarHostState,
                                            onSendToDownloader = { detectedUrl ->
                                                homeViewModel.analyzeSingleLink(detectedUrl)
                                                navigateToTopTab(HomeRoute)
                                            }
                                        )
                                    }

                                    composable<ProfileRoute> {
                                        ProfileScreen(
                                            viewModel = profileViewModel,
                                            onOpenThemes = { navController.navigate(ThemeStudioRoute) },
                                            onOpenSettings = { navController.navigate(SettingsCenterRoute("hub")) },
                                            onOpenSecurity = { navController.navigate(SecurityCenterRoute) },
                                            onOpenVault = { navController.navigate(VaultRoute) },
                                            onOpenMediaStudio = { navController.navigate(MediaStudioRoute) },
                                            onOpenFileManager = { navController.navigate(FileManagerRoute) },
                                            onOpenStorageAnalyzer = { navController.navigate(StorageAnalyzerRoute) },
                                            onOpenNotifications = { navController.navigate(NotificationCenterRoute) },
                                            onOpenBackup = { navController.navigate(BackupRestoreRoute) },
                                            onOpenLegal = { navController.navigate(LegalLicensesRoute) }
                                        )
                                    }

                                    composable<SettingsCenterRoute> { entry ->
                                        val route = entry.toRoute<SettingsCenterRoute>()
                                        SettingsCenterScreen(
                                            initialSection = route.section,
                                            viewModel = profileViewModel,
                                            snackbarHostState = snackbarHostState,
                                            onOpenThemeStudio = { navController.navigate(ThemeStudioRoute) },
                                            onOpenStorageAnalyzer = { navController.navigate(StorageAnalyzerRoute) },
                                            onOpenBackupRestore = { navController.navigate(BackupRestoreRoute) },
                                            onOpenLegal = { navController.navigate(LegalLicensesRoute) },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<ThemeStudioRoute> {
                                        ThemeStudioScreen(
                                            viewModel = profileViewModel,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<VaultRoute> {
                                        VaultScreen(
                                            viewModel = profileViewModel,
                                            snackbarHostState = snackbarHostState,
                                            onPlayDecryptedVideo = { uri, title ->
                                                navController.navigate(VideoPlaybackRoute(uri, title))
                                            },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<MediaStudioRoute> {
                                        MediaStudioScreen(
                                            onSendUrlToDownloader = { scannedUrl ->
                                                homeViewModel.analyzeSingleLink(scannedUrl)
                                                navigateToTopTab(HomeRoute)
                                            },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<FileManagerRoute> {
                                        FileManagerScreen(
                                            viewModel = profileViewModel,
                                            snackbarHostState = snackbarHostState,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<StorageAnalyzerRoute> {
                                        StorageAnalyzerScreen(
                                            viewModel = profileViewModel,
                                            snackbarHostState = snackbarHostState,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<NotificationCenterRoute> {
                                        NotificationCenterScreen(
                                            viewModel = profileViewModel,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<BackupRestoreRoute> {
                                        BackupRestoreScreen(
                                            viewModel = profileViewModel,
                                            snackbarHostState = snackbarHostState,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<SecurityCenterRoute> {
                                        SecurityCenterScreen(
                                            viewModel = profileViewModel,
                                            snackbarHostState = snackbarHostState,
                                            onOpenVault = { navController.navigate(VaultRoute) },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<LegalLicensesRoute> {
                                        LegalLicensesScreen(onBack = { navController.popBackStack() })
                                    }

                                    composable<DownloadHistoryRoute> {
                                        DownloadHistoryScreen(
                                            viewModel = homeViewModel,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<VideoPlaybackRoute> { entry ->
                                        val route = entry.toRoute<VideoPlaybackRoute>()
                                        VideoPlayerScreen(
                                            mediaUri = route.mediaUri,
                                            title = route.title,
                                            playerViewModel = playerViewModel,
                                            isInPipMode = isInPipModeState.value,
                                            snackbarHostState = snackbarHostState,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }

                    // Global App Lock Gate (when enabled by user in Security / Settings)
                    if (needsAppLock && !appSessionUnlocked) {
                        AppLockOverlay(
                            biometricEnabled = settings.biometricEnabled,
                            onVerifyPin = { pin ->
                                val ok = profileViewModel.attemptPinUnlockAsync(pin)
                                if (ok) appSessionUnlocked = true
                                ok
                            },
                            onBiometricClick = {
                                launchAppBiometricPrompt(settings.language) {
                                    profileViewModel.unlockWithBiometricSuccess()
                                    appSessionUnlocked = true
                                }
                            }
                        )
                    }

                    // Branded Splash Overlay
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(if (tokens.reduceMotion) 0 else 280))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(tokens.background, tokens.cardSurface, tokens.background)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_art),
                                    contentDescription = stringResource(R.string.app_name),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(108.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                        .border(2.dp, tokens.heroGradient, RoundedCornerShape(26.dp))
                                )
                                Spacer(Modifier.height(18.dp))
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${DeveloperIdentity.DEVELOPER_NAME} • ${DeveloperIdentity.DEVELOPER_PROFILE_TAG}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.tertiaryAccent
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAtMs = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundedAtMs > 0L) {
            forceRelockSignal.longValue = System.currentTimeMillis()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isOnVideoPlayerRoute && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (sharedText.isNotBlank()) {
                val urlRegex = Regex("""https?://[^\s"'<>]+""")
                val extracted = urlRegex.find(sharedText)?.value ?: sharedText
                pendingSharedUrl.value = extracted
            }
        } else {
            val navTab = intent.getStringExtra("navigate_tab")
            val hasOpenDownload = intent.hasExtra("open_download_id")
            if (hasOpenDownload) {
                pendingOpenCompleted.value = true
                pendingNavigateTarget.value = "home"
            } else if (navTab != null) {
                pendingNavigateTarget.value = navTab
            }
        }
    }

    private fun launchAppBiometricPrompt(language: AppLanguage, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(LocaleHelper.getLocalizedString(this, language, R.string.app_name))
            .setSubtitle(LocaleHelper.getLocalizedString(this, language, R.string.vault_unlock_biometric))
            .setNegativeButtonText(LocaleHelper.getLocalizedString(this, language, R.string.btn_cancel))
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun GlobalMiniMusicBarHost(
    playerViewModel: PlayerViewModel,
    onOpenPlayer: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val playbackUi by playerViewModel.playbackUi.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = playbackUi.currentTitle.isNotBlank(),
        enter = fadeIn(tween(tokens.animDurationMs)),
        exit = fadeOut(tween(tokens.animDurationMs))
    ) {
        GlobalMiniMusicBar(
            title = playbackUi.currentTitle,
            artist = playbackUi.currentArtist,
            isPlaying = playbackUi.isPlaying,
            positionMs = playbackUi.positionMs,
            durationMs = playbackUi.durationMs,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayPause = playerViewModel::togglePlayPause,
            onSkipNext = playerViewModel::skipNext
        )
    }
}

@Composable
private fun GlobalMiniMusicBar(
    title: String,
    artist: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit
) {
    val tokens = LocalVaTokens.current
    VaCard(
        onClick = onOpenPlayer,
        highlighted = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("global_mini_player_bar")
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.elevatedSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = tokens.iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        artist.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                VaIconButton(onClick = onTogglePlayPause) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.player_play_pause),
                        tint = tokens.primaryAccent
                    )
                }
                VaIconButton(onClick = onSkipNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next))
                }
            }
            if (durationMs > 0L) {
                LinearProgressIndicator(
                    progress = { (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = tokens.primaryAccent
                )
            }
        }
    }
}

@Composable
private fun VaBottomBar(
    tabs: List<TopLevelTab>,
    isSelected: (TopLevelTab) -> Boolean,
    onSelect: (AppRoute) -> Unit
) {
    val tokens = LocalVaTokens.current
    val shape = when (tokens.navStyle) {
        NavStyle.FLOATING -> RoundedCornerShape(26.dp)
        NavStyle.PILL, NavStyle.MINIMAL -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    }

    val containerModifier = when (tokens.navStyle) {
        NavStyle.FLOATING -> Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .shadow((10 * tokens.shadowIntensity).dp, shape)
            .clip(shape)
            .border(1.dp, tokens.borderSubtle, shape)
        NavStyle.PILL -> Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .clip(shape)
            .border(1.dp, tokens.borderSubtle, shape)
        NavStyle.MINIMAL -> Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    }

    Surface(
        color = tokens.cardSurface.copy(alpha = 0.97f),
        modifier = containerModifier
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            windowInsets = if (tokens.navStyle == NavStyle.FLOATING) {
                WindowInsets(0)
            } else {
                WindowInsets(0)
            },
            modifier = Modifier.height(72.dp)
        ) {
            tabs.forEach { tab ->
                val selected = isSelected(tab)
                val iconScale by animateFloatAsState(
                    targetValue = if (selected && !tokens.reduceMotion) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "tab_icon_scale"
                )
                val icon = when (tokens.iconStyle) {
                    IconStyle.OUTLINED -> if (selected) tab.unselectedIcon else tab.unselectedIcon
                    IconStyle.ROUNDED, IconStyle.TWO_TONE -> if (selected) tab.selectedIcon else tab.unselectedIcon
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    modifier = Modifier.testTag(tab.testTag),
                    icon = {
                        Box(
                            modifier = (if (selected && tokens.iconStyle == IconStyle.TWO_TONE) {
                                Modifier
                                    .clip(CircleShape)
                                    .background(tokens.primaryAccent.copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            } else {
                                Modifier
                            }).graphicsLayer {
                                if (!tokens.reduceMotion) {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            }
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = stringResource(tab.labelRes)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(tab.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = tokens.primaryAccent,
                        selectedTextColor = tokens.primaryAccent,
                        indicatorColor = if (tokens.navStyle == NavStyle.MINIMAL) {
                            Color.Transparent
                        } else {
                            tokens.primaryAccent.copy(alpha = 0.16f)
                        },
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun VaNavigationRail(
    tabs: List<TopLevelTab>,
    isSelected: (TopLevelTab) -> Boolean,
    onSelect: (AppRoute) -> Unit
) {
    val tokens = LocalVaTokens.current
    NavigationRail(
        containerColor = tokens.cardSurface,
        modifier = Modifier.fillMaxHeight().border(1.dp, tokens.borderSubtle)
    ) {
        Spacer(Modifier.height(16.dp))
        tabs.forEach { tab ->
            val selected = isSelected(tab)
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                modifier = Modifier.testTag(tab.testTag),
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.labelRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun AppLockOverlay(
    biometricEnabled: Boolean,
    onVerifyPin: suspend (String) -> Boolean,
    onBiometricClick: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background.copy(alpha = 0.98f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        VaCard(highlighted = true, modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = tokens.primaryAccent,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.vault_enter_pin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                val pinIncorrectText = stringResource(R.string.pin_incorrect_error)
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 8 && it.all(Char::isDigit)) {
                            pinInput = it
                            errorMsg = null
                        }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text(stringResource(R.string.pin_field_label)) },
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { msg -> { Text(msg) } },
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_pin_input")
                )
                Spacer(Modifier.height(12.dp))
                VaPrimaryButton(
                    text = stringResource(R.string.btn_confirm),
                    icon = Icons.Rounded.LockOpen,
                    enabled = !verifying && pinInput.isNotBlank(),
                    onClick = {
                        val candidate = pinInput
                        pinInput = ""
                        scope.launch {
                            verifying = true
                            val ok = onVerifyPin(candidate)
                            if (!ok) {
                                errorMsg = pinIncorrectText
                            }
                            verifying = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_unlock_button")
                )
                if (biometricEnabled) {
                    Spacer(Modifier.height(10.dp))
                    VaOutlinedButton(
                        onClick = onBiometricClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.vault_unlock_biometric))
                    }
                }
            }
        }
    }
}
