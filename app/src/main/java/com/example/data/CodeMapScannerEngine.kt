package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.model.ProjectMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.security.MessageDigest

data class CodeMapReport(
    val projectName: String,
    val detectedEntryPoints: List<String>,
    val importsAndRefs: List<String>,
    val assets: List<String>,
    val components: List<String>,
    val permissionSuggestions: List<PermissionSuggestion>,
    val dependencies: List<DependencyRecord>,
    val manifestValidationIssues: List<String>,
    val mergedManifestPreview: String
)

data class PermissionSuggestion(
    val permission: String,
    val sourceFile: String,
    val reason: String,
    val apiTrigger: String,
    val riskLevel: String, // Low, Normal, Elevated
    val isCurrentlyDeclared: Boolean
)

data class DependencyRecord(
    val name: String,
    val versionOrUrl: String,
    val format: String,
    val checksumHint: String,
    val licenseNote: String,
    val status: String
)

class CodeMapScannerEngine(private val context: Context) {

    private val importRegex = Regex("""(?m)^\s*(import\s+[^\n;]+|const\s+\w+\s*=\s*require\([^)]+\))""")
    private val htmlRefRegex = Regex("""<(?:script[^>]+src|link[^>]+href)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val resNameRegex = Regex("^[a-z0-9_]+$")

    suspend fun scanProject(project: ProjectMetadata): CodeMapReport = withContext(Dispatchers.IO) {
        val root = File(project.directoryPath)
        val files = root.walkTopDown()
            .filter { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") }
            .toList()

        val entryPoints = mutableListOf<String>()
        val imports = linkedSetOf<String>()
        val assets = mutableListOf<String>()
        val components = mutableListOf<String>()
        val manifestIssues = mutableListOf<String>()
        val dependencies = mutableListOf<DependencyRecord>()

        val declaredPerms = readDeclaredProjectPermissions(root).toMutableSet()
        val suggestions = linkedMapOf<String, PermissionSuggestion>()

        val isOnline = isNetworkAvailable()

        files.forEach { file ->
            val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
            val ext = file.extension.lowercase()
            if (rel.endsWith("index.html") || rel.endsWith("MainActivity.kt") || rel.endsWith("AndroidManifest.xml")) {
                entryPoints.add(rel)
            }
            if (ext in setOf("png", "jpg", "jpeg", "webp", "svg", "ttf", "otf", "mp3", "wav", "css")) {
                assets.add(rel)
            }
            // Resource name validation for Android res/ files
            if (rel.contains("res/") && !rel.endsWith(".xml")) {
                val base = file.nameWithoutExtension
                if (!resNameRegex.matches(base)) {
                    manifestIssues.add("Invalid resource name '$rel': must match [a-z0-9_]+")
                }
            }

            if (file.length() < 512_000 && ext in setOf("html", "htm", "js", "ts", "css", "kt", "java", "xml", "json", "toml")) {
                val content = runCatching { file.readText() }.getOrDefault("")
                importRegex.findAll(content).take(20).forEach { m ->
                    imports.add("$rel: ${m.value.trim()}")
                }
                htmlRefRegex.findAll(content).forEach { m ->
                    val ref = m.groupValues[1]
                    imports.add("$rel -> $ref")
                    if (ref.startsWith("http://") || ref.startsWith("https://")) {
                        dependencies.add(
                            DependencyRecord(
                                name = ref.substringAfterLast('/').ifBlank { ref },
                                versionOrUrl = ref,
                                format = "Web CDN Asset",
                                checksumHint = sha256Short(ref),
                                licenseNote = "External CDN (verify SRI integrity)",
                                status = if (isOnline) "Reachable network" else "Offline — cached/local recommended"
                            )
                        )
                    }
                }

                // Package.json / Gradle dependencies
                if (file.name == "package.json") {
                    runCatching {
                        val json = JSONObject(content)
                        val deps = json.optJSONObject("dependencies")
                        deps?.keys()?.forEach { key ->
                            dependencies.add(
                                DependencyRecord(
                                    name = key,
                                    versionOrUrl = deps.optString(key),
                                    format = "npm package.json",
                                    checksumHint = sha256Short("$key@${deps.optString(key)}"),
                                    licenseNote = json.optString("license", "Check npm registry"),
                                    status = if (isOnline) "Declared (requires npm host)" else "Offline"
                                )
                            )
                        }
                    }.onFailure { manifestIssues.add("Invalid package.json: ${it.message}") }
                }

                if (ext == "xml") {
                    validateXml(rel, content)?.let { manifestIssues.add(it) }
                }

                // Component detection
                Regex("""class\s+([A-Za-z0-9_]+)\s*:\s*(ComponentActivity|AppCompatActivity|Service|BroadcastReceiver)""")
                    .findAll(content).forEach { m -> components.add("${m.groupValues[1]} (${m.groupValues[2]})") }

                // Permission Analysis
                detectPermissionsInCode(rel, content, declaredPerms).forEach { sug ->
                    suggestions.putIfAbsent(sug.permission, sug)
                }
            }
        }

        val mergedPreview = buildMergedManifestPreview(project, declaredPerms.sorted())
        CodeMapReport(
            projectName = project.name,
            detectedEntryPoints = entryPoints,
            importsAndRefs = imports.toList(),
            assets = assets,
            components = components,
            permissionSuggestions = suggestions.values.toList(),
            dependencies = dependencies,
            manifestValidationIssues = manifestIssues,
            mergedManifestPreview = mergedPreview
        )
    }

    suspend fun toggleProjectPermission(project: ProjectMetadata, permission: String, add: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(project.directoryPath)
                val configFile = File(root, "app.config.json")
                if (configFile.exists()) {
                    val obj = JSONObject(configFile.readText())
                    val arr = obj.optJSONArray("permissions") ?: JSONArray()
                    val set = linkedSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    if (add) set.add(permission) else set.remove(permission)
                    obj.put("permissions", JSONArray(set.toList()))
                    configFile.writeText(obj.toString(2))
                } else {
                    val manifest = File(root, "app/src/main/AndroidManifest.xml").takeIf { it.exists() }
                        ?: File(root, "AndroidManifest.xml")
                    if (!manifest.exists()) {
                        manifest.parentFile?.mkdirs()
                        manifest.writeText("<manifest package=\"${project.packageId.ifBlank { "com.tkofficial.app" }}\">\n    <application android:label=\"${project.name}\" />\n</manifest>")
                    }
                    var xml = manifest.readText()
                    val tag = """    <uses-permission android:name="$permission" />"""
                    if (add && !xml.contains(permission)) {
                        xml = xml.replace("<application", "$tag\n    <application")
                    } else if (!add) {
                        xml = xml.lines().filterNot { it.contains(permission) }.joinToString("\n")
                    }
                    manifest.writeText(xml)
                }
            }
        }

    fun buildMergedManifestPreview(project: ProjectMetadata, permissions: List<String>): String {
        val pkg = project.packageId.ifBlank { "com.tkofficial.app" }
        val permsXml = permissions.joinToString("\n") { """    <uses-permission android:name="$it" />""" }
        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg">""")
            if (permsXml.isNotBlank()) appendLine(permsXml)
            appendLine("""    <application android:label="${project.name}" android:allowBackup="false">""")
            appendLine("""        <activity android:name=".MainActivity" android:exported="true" />""")
            appendLine("""    </application>""")
            appendLine("""</manifest>""")
        }.trimEnd()
    }

    private fun readDeclaredProjectPermissions(root: File): Set<String> {
        val out = linkedSetOf<String>()
        val cfg = File(root, "app.config.json")
        if (cfg.exists()) {
            runCatching {
                val arr = JSONObject(cfg.readText()).optJSONArray("permissions")
                if (arr != null) for (i in 0 until arr.length()) out.add(arr.getString(i))
            }
        }
        listOf(File(root, "AndroidManifest.xml"), File(root, "app/src/main/AndroidManifest.xml")).forEach { m ->
            if (m.exists()) {
                Regex("""android:name=["'](android\.permission\.[A-Z0-9_]+)["']""").findAll(m.readText())
                    .forEach { out.add(it.groupValues[1]) }
            }
        }
        return out
    }

    private fun detectPermissionsInCode(
        relPath: String,
        content: String,
        declared: Set<String>
    ): List<PermissionSuggestion> {
        val list = mutableListOf<PermissionSuggestion>()
        if (content.contains("fetch(") || content.contains("XMLHttpRequest") || content.contains("https://")) {
            val perm = "android.permission.INTERNET"
            list.add(
                PermissionSuggestion(
                    permission = perm,
                    sourceFile = relPath,
                    reason = "Network request or external HTTPS endpoint detected",
                    apiTrigger = "fetch / HTTPS",
                    riskLevel = "Normal",
                    isCurrentlyDeclared = perm in declared
                )
            )
        }
        if (content.contains("navigator.geolocation")) {
            val perm = "android.permission.ACCESS_COARSE_LOCATION"
            list.add(
                PermissionSuggestion(
                    permission = perm,
                    sourceFile = relPath,
                    reason = "Web Geolocation API invoked; coarse location recommended first",
                    apiTrigger = "navigator.geolocation",
                    riskLevel = "Elevated",
                    isCurrentlyDeclared = perm in declared
                )
            )
        }
        if (content.contains("getUserMedia")) {
            val perm = "android.permission.CAMERA"
            list.add(
                PermissionSuggestion(
                    permission = perm,
                    sourceFile = relPath,
                    reason = "Media capture stream requested in script",
                    apiTrigger = "navigator.mediaDevices.getUserMedia",
                    riskLevel = "Elevated",
                    isCurrentlyDeclared = perm in declared
                )
            )
        }
        return list
    }

    private fun validateXml(relPath: String, xml: String): String? {
        return runCatching {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            while (parser.next() != XmlPullParser.END_DOCUMENT) { /* validate */ }
            null
        }.getOrElse { "XML syntax error in $relPath: ${it.message}" }
    }

    private fun isNetworkAvailable(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    private fun sha256Short(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
}
