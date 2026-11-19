# Release Build & Signing Guide — VD Player

## 1. Gradle Configuration Summary
- **Application ID**: `com.tkofficial.videodownloder`
- **Min SDK**: 24 (Android 7.0) | **Target / Compile SDK**: 36
- **Version**: `1.0.0` (`versionCode = 1`)
- **Release Optimization**:
  - `isMinifyEnabled = true` (R8 code shrinking & obfuscation)
  - `isShrinkResources = true`
  - `app/proguard-rules.pro` strips verbose/debug/info logs (`Log.v`, `Log.d`, `Log.i`) and retains Room, Moshi, `kotlinx.serialization`, Media3, and Biometric reflection metadata.

## 2. Automatic Free GitHub Actions Build (v1, v2, v3 Signing)
This repository includes `.github/workflows/build-apk.yml`, which runs automatically on GitHub's free `ubuntu-latest` runner whenever you push to `main` / `master` or push a version tag (`v*`):
- Automatically accepts Android SDK licenses and installs `platform-tools`, `build-tools;36.0.0`, `platforms;android-36`, and `platforms;android-36.1`.
- Provisions Gradle `9.3.1`, `.env.example`, and `debug.keystore` automatically with CI-safe memory limits (`-Xmx2560m`) and `--no-configuration-cache`.
- Runs unit & Robolectric tests (`gradle :app:testDebugUnitTest`).
- Builds and signs **`VD-Player-v1.0.0.apk`** using APK Signature Scheme **v1 + v2 + v3** (`apksigner sign --min-sdk-version 21 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true`).
- Works out of the box with zero configuration using `debug.keystore.base64`, or automatically builds an R8-minified release APK if you add optional GitHub Secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`).
- Uploads the signed APK to the workflow's **Artifacts** tab on every push (and creates a GitHub Release automatically on `v*` tags).

## 3. Manual Signing Configuration
`app/build.gradle.kts` reads release upload keystore credentials exclusively from environment variables (never commit keystores or passwords to Git):

```bash
export KEYSTORE_PATH="/path/to/upload-keystore.jks"
export STORE_PASSWORD="your_store_password"
export KEY_PASSWORD="your_key_password"
```

The expected key alias in `signingConfigs.release` is `upload`.

## 4. Generating a Keystore (If Needed)
```bash
keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

## 5. Building Signed Release Artifacts Locally
### Release APK (Direct Sideload / QA)
```bash
gradle :app:assembleRelease
```
Output path: `app/build/outputs/apk/release/app-release.apk`

### Android App Bundle (Google Play Store)
```bash
gradle :app:bundleRelease
```
Output path: `app/build/outputs/bundle/release/app-release.aab`

## 6. Verifying v1, v2 & v3 Signature Schemes
```bash
apksigner verify --verbose --print-certs VD-Player-v1.0.0.apk
```
Confirm `Verified using v1 scheme`, `v2 scheme`, and `v3 scheme` all report `true`. Record the printed **SHA-256 certificate digest** for release verification.
