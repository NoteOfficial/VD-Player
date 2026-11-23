# VD Player

A modular, native Android (Kotlin + Jetpack Compose + Material 3) media utility suite combining an HTTP download manager, downloaded-media gallery, Media3 video & audio player, in-app WebView browser with media sniffing, AES-256-GCM encrypted vault, on-device media studio, local file manager, and adaptive theming.

## Application Identity
- **App Name**: VD Player
- **Application ID**: `com.tkofficial.videodownloder`
- **Version**: `1.0.0` (versionCode `1`)
- **Developer**: Mr TK (`＞━╬٨-ﮩﮩT♡ᴋ٨ـﮩﮩـ╬━＜`)
- **Contact & Official Updates**: Telegram `@MR_TK_68` (`https://t.me/MR_TK_68`) • Email `tkofficial68@gmail.com` (contact via email for official VD Player updates)

## Architecture
- **UI**: Jetpack Compose + Material 3 (`ThemeEngine.kt` with 8 presets + full color/radius/motion/navigation customization), Navigation Compose (`NavRoutes.kt` with `@Serializable` routes), adaptive bottom bar & `NavigationRail`, and global persistent mini music bar.
- **State**: MVVM with `AndroidViewModel`, Kotlin Coroutines, `StateFlow`, `collectAsStateWithLifecycle()`, and sealed `UiState<T>`.
- **Persistence**:
  - **Room (`AppDatabase`)**: 18 entities covering downloads, queue, history, gallery media, albums, playlists, favorites, playback resume history, video bookmarks, song metadata overrides, notifications, encrypted vault metadata, backup records, browser tabs/bookmarks/history.
  - **DataStore Preferences (`PreferencesRepository`)**: User preferences, PBKDF2 PIN hash/salt, persisted SAF video folder URIs, encrypted extractor key storage, multi-endpoint failover settings seeded from `assets/configuration/app_settings.json`, and serialized `ThemeConfig`.
- **Services & Background Execution**:
  - `DownloadForegroundService` (`foregroundServiceType="dataSync"`): Keeps active HTTP downloads alive with grouped pause/resume/cancel/retry notification actions and deep-link navigation.
  - `PlaybackService` (`MediaLibraryService`, `foregroundServiceType="mediaPlayback"`): Background music playback, Bluetooth/notification controls, queue management, live gapless playback syncing, and `AudioEffectsBridge` (Equalizer, BassBoost, Virtualizer).
  - `ScheduledDownloadWorker` (`WorkManager`): Runs scheduled downloads cleanly under Doze/App Standby constraints.
- **Security**:
  - **Private Vault & API Secrets**: Android Keystore AES-256-GCM encryption (`SecurityManager.kt`), `.nomedia` app-private `filesDir/vault`, `FLAG_SECURE` recents privacy toggle, PBKDF2-HMAC-SHA256 (100,000 iterations + random 16-byte salt) PIN verification with brute-force lockout backoff, `BiometricPrompt` integration, and client-side integrity diagnostics.

## Navigation Structure
Five permanent top-level tabs (`HOME`, `GALLERY`, `PLAYER`, `BROWSER`, `PROFILE`), with the Download Manager integrated inside **HOME**, and sub-destinations for **Settings Center (16 sections)**, **Theme Studio**, **Private Vault**, **Security Center**, **Media Studio**, **File Manager**, **Storage Analyzer**, **Notification Center**, **Backup & Restore**, **Download History**, **Legal & Licenses**, and **Video Playback**.

## Known Technical & Platform Limitations
1. **Extractor / DRM Policy**: Direct HTTP/HTTPS media files and standard HTML5 `<video>/<audio>/<meta og:video>` pages are analyzed directly. Platforms using DRM or encrypted/signed stream manifests require a user-supplied HTTPS backend extractor endpoint configured in `assets/configuration/app_settings.json` or **Settings → API Management** (supports primary/secondary/backup failover + JSON import/export) and strictly only for authorized content.
2. **Pause / Resume**: HTTP pause/resume depends on the remote server returning `Accept-Ranges: bytes` (HTTP 206). Servers without byte-range support restart from byte 0 on retry.
3. **Android Scoped Storage & Play Policy**:
   - **Gallery** intentionally displays only VD Player downloaded media (`downloaded_media` table) without requesting broad `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` storage permissions.
   - **Vault**, **Media Studio**, and manual **Video Player** additions use the zero-permission Android Photo Picker (`PickVisualMedia`) or Storage Access Framework (`OpenDocumentTree` / `OpenDocument`).
   - **Music Player** uses `READ_MEDIA_AUDIO` (API 33+) or `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`), with SAF folder import fallback.
4. **Client-Side Tamper Resistance**: Signature/root/debugger checks in `SecurityManager` are local heuristics; determined attackers can bypass purely client-side checks by re-signing without server-side Google Play Integrity API attestation.
5. **Display Settings**: Contrast/color theme settings and playback UI scale do not upscale source video resolution.
