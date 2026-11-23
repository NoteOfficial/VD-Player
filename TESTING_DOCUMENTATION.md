# Testing Documentation — VD Player

## 1. Execution Environment Constraints
This build environment provides a cloud Android Gradle SDK and Robolectric JVM runtime, with **no live Android Emulator or ADB hardware bridge**. Therefore:
- **Verified in Environment**: Full Kotlin/Compose/KSP debug compilation (`compile_applet`), Android Lint (`lint_applet`, 0 errors), and 19 local JVM + Robolectric (`sdk = 36`) unit tests (`gradle :app:testDebugUnitTest`).
- **Pending On-Device QA**: Hardware CameraX ML Kit QR viewfinder, live ExoPlayer video surface & `PixelCopy` screenshot capture, Picture-in-Picture transition, `BiometricPrompt` hardware sensor prompt, Bluetooth media key actions, and live network HTTP download resume against real servers.

## 2. Executed Unit & Robolectric Tests (`:app:testDebugUnitTest`)
All **19 / 19** tests pass cleanly (`BUILD SUCCESSFUL`):

| Test Suite | Test Cases Covered | Status |
| :--- | :--- | :--- |
| `ExampleUnitTest` | Baseline arithmetic sanity check | Passed |
| `ApiConfigManagerUnitTest` | Verifies JSON config parsing/serialization (`app_settings.json`), endpoint validation (`https` vs `http`), failover ordering, and static API status computation on pure JVM | Passed |
| `ExampleRobolectricTest` | Verifies localized `R.string.app_name` (`VD Player`) and immutable `DeveloperIdentity` constants (`com.tkofficial.videodownloder`, `Mr TK`, `@MR_TK_68`, `tkofficial68@gmail.com`, `1.0.0`) under Robolectric SDK 36 | Passed |
| `LocalizationAndPermissionsTest` | Verifies all 4 locales (`en`, `bn`, `ar`, `hi`) translate core strings & formatted args cleanly without showing raw handles on the Telegram button, enforces Arabic RTL vs LTR layout direction, verifies `LocaleHelper.wrapContext` + `findActivity()` unwrapping, validates Manifest permissions compliance (`POST_NOTIFICATIONS`, `CAMERA`, `READ_MEDIA_AUDIO`, `USE_BIOMETRIC`, `FOREGROUND_SERVICE*` with zero `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`), and verifies `DeveloperIdentity` + profile drawable | Passed |
| `FormatUtilsUnitTest` | Enforces HTTPS-only default vs `allowHttp` toggle; rejects `javascript:` and `file://` URLs; MIME/extension media categorization; byte/duration/ETA formatting; path-traversal sanitization (`..` stripped & extension preserved); deterministic SHA-256 checksums; unique filename conflict resolution | Passed |
| `SecurityAndNetworkTest` | PBKDF2-HMAC-SHA256 PIN hashing (distinct 16-byte random salts, constant-time match & mismatch checks); `SecurityManager.runIntegrityCheck()` package validation; offline rejection of unauthorized cleartext HTTP and non-HTTP schemes in `LinkAnalyzer` | Passed |
| `DatabaseAndBackupRobolectricTest` | In-memory Room `AppDatabase`: transactional download enqueue (`downloads`, `download_queue`, `download_history`), boot crash recovery (`pauseInterruptedOnBoot()`), Gallery strict downloaded-media isolation + album & trash workflows, favorites/playlists/video bookmarks persistence, and Backup SHA-256 tamper detection | Passed |

## 3. Manual On-Device QA Checklist
1. **Home & Downloader**: Paste direct HTTPS `.mp4` / `.mp3` / `.webp` URL, verify header metadata, pause/resume (HTTP 206), concurrent queue enforcement, Wi-Fi-only pause & automatic reconnect resumption, and batch URL queuing.
2. **Share Intent & Notifications**: Share a link from an external browser into VD Player (`ACTION_SEND`) and test Pause / Resume / Cancel / Open actions from notification shade.
3. **Gallery Isolation**: Confirm only VD Player downloaded items appear without broad `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` prompts.
4. **Video & Music Players**: Verify brightness/volume/seek vertical & horizontal swipe gestures, PiP on Home button, `PixelCopy` screenshot, A-B repeat, audio-only handoff to `PlaybackService`, 5-band Equalizer / BassBoost, and SAF video folder import.
5. **Private Vault & Security**: Create 4–8 digit PIN, import via Photo Picker, verify `.nomedia` encrypted blob creation, test wrong-PIN brute-force lockout, `FLAG_SECURE` in Recents, and Backup export/restore integrity.
6. **Localization & RTL**: Switch between English (`en`), বাংলা (`bn`), العربية (`ar` RTL), and हिन्दी (`hi`) in Settings → General.
