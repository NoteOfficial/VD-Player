<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/c56b8881-358f-4e58-835f-03e0760af2d8

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.


## GitHub Auto Build (APK)

Every `git push` runs `.github/workflows/android-build.yml`, which builds the app and uploads the APK.

1. Push the project to GitHub.
2. Open the **Actions** tab, pick the latest **Android Build** run.
3. Download the APK from the **Artifacts** section (`TK-Studio-debug-apk`).

### Signed release APK (optional)
Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 my-upload-key.jks` output (key alias must be `upload`) |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |

Push a tag such as `v0.6.8` to also publish the APKs as a GitHub Release.
