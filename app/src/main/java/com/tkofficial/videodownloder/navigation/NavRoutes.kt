package com.tkofficial.videodownloder.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute

@Serializable data object HomeRoute : AppRoute
@Serializable data object GalleryRoute : AppRoute
@Serializable data object PlayerRoute : AppRoute
@Serializable data object BrowserRoute : AppRoute
@Serializable data object ProfileRoute : AppRoute

@Serializable data class SettingsCenterRoute(val section: String = "hub") : AppRoute
@Serializable data object ThemeStudioRoute : AppRoute
@Serializable data object VaultRoute : AppRoute
@Serializable data object MediaStudioRoute : AppRoute
@Serializable data object FileManagerRoute : AppRoute
@Serializable data object StorageAnalyzerRoute : AppRoute
@Serializable data object NotificationCenterRoute : AppRoute
@Serializable data object BackupRestoreRoute : AppRoute
@Serializable data object SecurityCenterRoute : AppRoute
@Serializable data object LegalLicensesRoute : AppRoute
@Serializable data object DownloadHistoryRoute : AppRoute
@Serializable data class VideoPlaybackRoute(val mediaUri: String, val title: String) : AppRoute
