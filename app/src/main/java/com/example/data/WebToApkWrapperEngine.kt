package com.example.data

import com.example.model.BuildConfigSpec
import com.example.model.ProjectMetadata
import com.example.model.WebWrapperPreview
import java.io.File

/**
 * Validates Web / HTML5 projects and generates an inspectable, security-hardened
 * Kotlin Android WebView wrapper project + AndroidManifest + wrapper config.
 */
object WebToApkWrapperEngine {

    private val localAssetRefRegex = Regex(
        """<(?:script[^>]+src|link[^>]+href|img[^>]+src)=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    fun inspectAndGeneratePreview(
        projectDir: File,
        project: ProjectMetadata,
        config: BuildConfigSpec,
        approvedPermissions: List<String> = emptyList()
    ): WebWrapperPreview {
        val entryFile = File(projectDir, project.entryFile.ifBlank { "index.html" })
        val htmlText = if (entryFile.exists()) runCatching { entryFile.readText() }.getOrDefault("") else ""

        val assetRefs = mutableListOf<String>()
        val missingAssets = mutableListOf<String>()
        val suggestedPerms = linkedSetOf<String>()

        localAssetRefRegex.findAll(htmlText).forEach { m ->
            val rawRef = m.groupValues[1].trim()
            if (rawRef.isNotBlank() && !rawRef.startsWith("data:") && !rawRef.startsWith("javascript:")) {
                assetRefs.add(rawRef)
                if (rawRef.startsWith("https://") || rawRef.startsWith("http://")) {
                    suggestedPerms.add("android.permission.INTERNET")
                    suggestedPerms.add("android.permission.ACCESS_NETWORK_STATE")
                } else {
                    val cleanRel = rawRef.substringBefore('?').substringBefore('#').removePrefix("./")
                    val resolved = File(entryFile.parentFile ?: projectDir, cleanRel)
                    if (!resolved.exists()) {
                        missingAssets.add(rawRef)
                    }
                }
            }
        }

        // Scan JS/HTML for Web APIs that warrant Android permissions
        val allWebText = projectDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("html", "htm", "js", "mjs", "css") && it.length() < 512_000 }
            .joinToString("\n") { runCatching { it.readText() }.getOrDefault("") }

        if (allWebText.contains("fetch(") || allWebText.contains("XMLHttpRequest") || allWebText.contains("https://")) {
            suggestedPerms.add("android.permission.INTERNET")
            suggestedPerms.add("android.permission.ACCESS_NETWORK_STATE")
        }
        if (allWebText.contains("navigator.geolocation")) {
            suggestedPerms.add("android.permission.ACCESS_COARSE_LOCATION")
        }
        if (allWebText.contains("getUserMedia")) {
            suggestedPerms.add("android.permission.CAMERA")
        }

        val effectivePermissions = (approvedPermissions.ifEmpty { suggestedPerms.toList() }).distinct().sorted()
        val generatedKotlin = generateWrapperMainActivityKotlin(config, project.entryFile.ifBlank { "index.html" })
        val generatedManifest = generateWrapperManifestXml(config, effectivePermissions)
        val configJson = generateWrapperConfigJson(config, effectivePermissions)

        val limitations = detectWebFrameworkLimitations(projectDir, allWebText)
        val securityDefaults = listOf(
            "HTTPS-only navigation enforced; MIXED_CONTENT_NEVER_ALLOW set",
            "onReceivedSslError strictly calls handler.cancel() (unsafe proceed disabled)",
            "allowFileAccess = false, allowFileAccessFromFileURLs = false, allowUniversalAccessFromFileURLs = false",
            "Local assets served via WebViewAssetLoader (https://appassets.androidplatform.net/assets/)",
            "No unrestricted @JavascriptInterface bridge generated",
            if (config.allowlistDomains.isBlank()) {
                "Allowlist locked to local appassets origin only"
            } else {
                "External navigation restricted to allowlisted hosts: ${config.allowlistDomains}"
            }
        )

        return WebWrapperPreview(
            generatedKotlin = generatedKotlin,
            generatedManifest = generatedManifest,
            configJson = configJson,
            assetRefs = assetRefs.distinct(),
            missingAssets = missingAssets.distinct(),
            securityDefaults = securityDefaults,
            frameworkLimitations = limitations,
            suggestedPermissions = suggestedPerms.toList()
        )
    }

    fun detectWebFrameworkLimitations(projectDir: File, combinedSource: String = ""): List<String> {
        val out = mutableListOf<String>()
        val hasPkgJson = File(projectDir, "package.json").exists()
        val hasTs = projectDir.walkTopDown().any { it.isFile && (it.extension == "ts" || it.extension == "tsx" || it.extension == "jsx") }
        val hasVite = File(projectDir, "vite.config.js").exists() || File(projectDir, "vite.config.ts").exists()
        val hasNext = File(projectDir, "next.config.js").exists() || File(projectDir, "next.config.mjs").exists()
        val hasNodeModules = File(projectDir, "node_modules").exists()

        if (hasNext) {
            out.add("Next.js SSR routes / server actions cannot run inside an offline WebView; export static HTML/CSS/JS first ('next export').")
        }
        if (hasVite || hasPkgJson || hasTs) {
            out.add("Unbundled TypeScript/JSX/Vite projects require pre-building into static dist/ HTML+JS bundles on a Node host or CI before WebView packaging.")
        }
        if (hasNodeModules) {
            out.add("node_modules directory should be excluded from APK assets; package only built static runtime assets.")
        }
        if (combinedSource.contains("process.env") || combinedSource.contains("require(")) {
            out.add("CommonJS require() / Node process.env APIs are unsupported natively in browser WebView without bundling.")
        }
        if (out.isEmpty()) {
            out.add("Static HTML5/CSS3/ES6 project is fully compatible with TK Studio WebView Wrapper runtime.")
        }
        return out
    }

    fun generateWrapperMainActivityKotlin(config: BuildConfigSpec, entryFile: String): String {
        val pkg = config.packageId.ifBlank { "com.tkofficial.app" }
        val cleanEntry = entryFile.removePrefix("./").ifBlank { "index.html" }
        val allowHostsList = config.allowlistDomains
            .split(",", ";", " ")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val allowHostsKotlin = if (allowHostsList.isEmpty()) {
            "setOf(\"appassets.androidplatform.net\")"
        } else {
            "setOf(" + (listOf("appassets.androidplatform.net") + allowHostsList).joinToString(", ") { "\"${escapeKotlin(it)}\"" } + ")"
        }

        val orientationCall = when (config.orientation.lowercase()) {
            "portrait" -> "requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT"
            "landscape" -> "requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE"
            else -> "// Screen orientation follows manifest/system sensor"
        }

        return """
package $pkg

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var offlineOverlay: LinearLayout
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val allowedHosts: Set<String> = $allowHostsKotlin

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        fileUploadCallback?.onReceiveValue(uris.toTypedArray())
        fileUploadCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        $orientationCall

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val root = FrameLayout(this)
        webView = WebView(this)
        offlineOverlay = buildErrorView()

        with(webView.settings) {
            javaScriptEnabled = ${config.enableJs}
            domStorageEnabled = ${config.domStorage}
            allowFileAccess = false
            allowContentAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase().orEmpty()
                val host = uri.host?.lowercase().orEmpty()
                if (scheme != "https") return true // Block insecure cleartext or file:// navigation
                if (host in allowedHosts) return false
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel() // Never bypass SSL certificate validation
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    offlineOverlay.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                fileChooserLauncher.launch(fileChooserParams.acceptTypes.ifEmpty { arrayOf("*/*") })
                return true
            }
        }

        webView.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            if (url.startsWith("https://")) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        })

        root.addView(webView)
        root.addView(offlineOverlay)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl("https://appassets.androidplatform.net/assets/$cleanEntry")
    }

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(48, 96, 48, 48)
            addView(TextView(context).apply { text = "${escapeKotlin(config.appName)} — Unable to load page" })
            addView(Button(context).apply {
                text = "Retry"
                setOnClickListener {
                    visibility = View.GONE
                    webView.reload()
                }
            })
        }
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
        """.trimIndent()
    }

    fun generateWrapperManifestXml(config: BuildConfigSpec, permissions: List<String>): String {
        val pkg = config.packageId.ifBlank { "com.tkofficial.app" }
        val appName = config.appName.ifBlank { "TK App" }
        val screenOrientationAttr = when (config.orientation.lowercase()) {
            "portrait" -> """android:screenOrientation="portrait""""
            "landscape" -> """android:screenOrientation="landscape""""
            else -> """android:screenOrientation="unspecified""""
        }
        val permsBlock = permissions.joinToString("\n") { perm ->
            """    <uses-permission android:name="$perm" />"""
        }
        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<manifest xmlns:android="http://schemas.android.com/apk/res/android"""")
            appendLine("""    package="$pkg"""")
            appendLine("""    android:versionCode="${config.versionCode}"""")
            appendLine("""    android:versionName="${config.versionName}">""")
            appendLine("""    <uses-sdk android:minSdkVersion="${config.minSdk}" android:targetSdkVersion="${config.targetSdk}" />""")
            if (permsBlock.isNotBlank()) appendLine(permsBlock)
            appendLine("""    <application""")
            appendLine("""        android:label="${escapeXml(appName)}"""")
            appendLine("""        android:allowBackup="false"""")
            appendLine("""        android:usesCleartextTraffic="false">""")
            appendLine("""        <activity""")
            appendLine("""            android:name=".MainActivity"""")
            appendLine("""            android:exported="true"""")
            appendLine("""            $screenOrientationAttr>""")
            appendLine("""            <intent-filter>""")
            appendLine("""                <action android:name="android.intent.action.MAIN" />""")
            appendLine("""                <category android:name="android.intent.category.LAUNCHER" />""")
            appendLine("""            </intent-filter>""")
            appendLine("""        </activity>""")
            appendLine("""    </application>""")
            appendLine("""</manifest>""")
        }.trimEnd()
    }

    fun generateWrapperConfigJson(config: BuildConfigSpec, permissions: List<String>): String {
        val allowlist = config.allowlistDomains.split(",", ";", " ")
            .map { it.trim() }.filter { it.isNotBlank() }
        return """
{
  "appName": "${escapeJson(config.appName)}",
  "packageId": "${escapeJson(config.packageId)}",
  "versionName": "${escapeJson(config.versionName)}",
  "versionCode": ${config.versionCode},
  "minSdk": ${config.minSdk},
  "targetSdk": ${config.targetSdk},
  "orientation": "${escapeJson(config.orientation)}",
  "splashTitle": "${escapeJson(config.splashTitle)}",
  "statusBarDarkText": ${config.statusBarDarkText},
  "webView": {
    "javaScriptEnabled": ${config.enableJs},
    "domStorageEnabled": ${config.domStorage},
    "mixedContentAllowed": false,
    "allowFileAccess": false,
    "allowUniversalAccessFromFileURLs": false,
    "allowInsecureSslBypass": false,
    "allowlistDomains": [${allowlist.joinToString(", ") { "\"${escapeJson(it)}\"" }}]
  },
  "permissions": [${permissions.joinToString(", ") { "\"${escapeJson(it)}\"" }}]
}
        """.trimIndent()
    }

    private fun escapeKotlin(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun escapeXml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun escapeJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
