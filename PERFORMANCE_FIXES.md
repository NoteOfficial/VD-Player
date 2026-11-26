# Performance / Lag / Hang Fixes

## 1. Root cause of lag + freezing: the APK was a DEBUG build
Without GitHub secrets, `.github/workflows/build-apk.yml` ran `assembleDebug`. A debug Jetpack Compose
app has no R8 optimization, no resource shrinking and runs debuggable, which makes it very slow and
prone to freezing. The workflow now ALWAYS builds the optimized release APK (R8 + shrinking):
- `.github/workflows/build-apk.yml`: fallback branch now runs `assembleRelease`.
- `app/build.gradle.kts`: if `STORE_PASSWORD` is not provided, the release build is signed with the
  debug keystore instead of failing. With your real upload-key secrets it still uses that key.

## 2. Main-thread file read + regex compile per line (lyrics picker)
`PlayerScreen.kt`: picking a lyrics (.lrc) file read the file on the UI thread and compiled a new
Regex for every line. The file is now read/parsed on `Dispatchers.IO` with one precompiled Regex.

No design, layout, strings or features were changed.
