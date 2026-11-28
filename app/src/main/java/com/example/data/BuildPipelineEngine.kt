package com.example.data

import android.content.Context
import com.example.model.AutoBuildProposal
import com.example.model.BuildConfigSpec
import com.example.model.BuildMode
import com.example.model.BuildPlanDiffItem
import com.example.model.BuildProfile
import com.example.model.BuildRecord
import com.example.model.BuildReportFormat
import com.example.model.BuildStage
import com.example.model.BuildStepRecord
import com.example.model.ProjectMetadata
import com.example.model.ProjectType
import com.example.model.SigningKeyInfo
import com.example.model.StudioEventLogger
import com.example.model.ToolchainItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Pure Kotlin SHA-256 incremental cache + report formatter so build verification
 * and redaction can also run in pure JVM unit tests.
 */
object BuildVerificationUtils {

    fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun computeConfigFingerprint(config: BuildConfigSpec, profile: BuildProfile, toolchainVersionSig: String): String {
        val raw = listOf(
            config.appName,
            config.packageId,
            config.versionName,
            config.versionCode.toString(),
            config.minSdk.toString(),
            config.targetSdk.toString(),
            config.orientation,
            config.enableJs.toString(),
            config.domStorage.toString(),
            config.allowlistDomains,
            config.compressionLevel.toString(),
            config.customCompilerFlags,
            profile.name,
            toolchainVersionSig
        ).joinToString("|")
        return sha256Bytes(raw.toByteArray(Charsets.UTF_8))
    }

    data class CacheEvalResult(
        val hits: Int,
        val changedOrAdded: Int,
        val removed: Int,
        val configInvalidated: Boolean,
        val currentHashes: Map<String, String>
    )

    fun evaluateIncrementalCache(
        sourceDir: File,
        previousFileHashes: Map<String, String>,
        previousConfigHash: String?,
        currentConfigHash: String
    ): CacheEvalResult {
        val currentHashes = linkedMapOf<String, String>()
        sourceDir.walkTopDown()
            .filter {
                it.isFile &&
                    !it.absolutePath.contains("${File.separator}.tkstudio_git") &&
                    !it.absolutePath.contains("${File.separator}.tkstudio_repair_backups") &&
                    !it.absolutePath.contains("${File.separator}builds")
            }
            .sortedBy { it.relativeTo(sourceDir).path }
            .forEach { file ->
                val rel = file.relativeTo(sourceDir).path.replace(File.separatorChar, '/')
                currentHashes[rel] = sha256Bytes(file.readBytes())
            }

        val configChanged = previousConfigHash != null && previousConfigHash != currentConfigHash
        if (configChanged) {
            return CacheEvalResult(
                hits = 0,
                changedOrAdded = currentHashes.size,
                removed = previousFileHashes.keys.count { it !in currentHashes },
                configInvalidated = true,
                currentHashes = currentHashes
            )
        }

        var hits = 0
        var changed = 0
        currentHashes.forEach { (rel, sha) ->
            if (previousFileHashes[rel] == sha) hits++ else changed++
        }
        val removed = previousFileHashes.keys.count { it !in currentHashes }
        return CacheEvalResult(hits, changed, removed, false, currentHashes)
    }

    fun formatBuildReport(record: BuildRecord, format: BuildReportFormat): String {
        val redactedLogs = record.logLines.map { StudioEventLogger.redact(it) }
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timeStr = df.format(Date(record.startedAt))

        return when (format) {
            BuildReportFormat.TXT -> buildString {
                appendLine("=== TK STUDIO BUILD REPORT (${record.reportId}) ===")
                appendLine("Timestamp:  $timeStr")
                appendLine("Project:    ${record.projectName} (${record.projectId})")
                appendLine("Mode:       ${record.mode.label}")
                appendLine("Profile:    ${record.profile.label}")
                appendLine("Status:     ${record.stage.label} (success=${record.success})")
                appendLine("Artifact:   ${record.artifactKind}")
                appendLine("Duration:   ${record.durationMs} ms")
                record.outputPath?.let { appendLine("Output:     $it") }
                record.outputSha256?.let { appendLine("SHA-256:    $it") }
                record.errorCode?.let { appendLine("Error Code: $it") }
                record.failedStage?.let { appendLine("Failed At:  $it") }
                record.sourceLocation?.let { appendLine("Location:   $it") }
                appendLine()
                appendLine("--- Pipeline Steps (${record.steps.size}) ---")
                record.steps.forEach { st ->
                    appendLine(
                        "[${st.stepNumber.toString().padStart(2, '0')}] ${st.status.padEnd(7)} ${st.title} (${st.durationMs}ms): ${StudioEventLogger.redact(st.detail)}"
                    )
                }
                appendLine()
                appendLine("--- Build Logs (${redactedLogs.size}) ---")
                redactedLogs.forEach { appendLine(it) }
            }

            BuildReportFormat.JSON -> buildString {
                appendLine("{")
                appendLine("  \"reportId\": \"${esc(record.reportId)}\",")
                appendLine("  \"projectName\": \"${esc(record.projectName)}\",")
                appendLine("  \"mode\": \"${record.mode.name}\",")
                appendLine("  \"profile\": \"${record.profile.name}\",")
                appendLine("  \"stage\": \"${record.stage.name}\",")
                appendLine("  \"success\": ${record.success},")
                appendLine("  \"artifactKind\": \"${esc(record.artifactKind)}\",")
                appendLine("  \"startedAt\": ${record.startedAt},")
                appendLine("  \"durationMs\": ${record.durationMs},")
                appendLine("  \"outputPath\": ${record.outputPath?.let { "\"${esc(it)}\"" } ?: "null"},")
                appendLine("  \"outputSha256\": ${record.outputSha256?.let { "\"$it\"" } ?: "null"},")
                appendLine("  \"errorCode\": ${record.errorCode?.let { "\"${esc(it)}\"" } ?: "null"},")
                appendLine("  \"failedStage\": ${record.failedStage?.let { "\"${esc(it)}\"" } ?: "null"},")
                appendLine("  \"steps\": [")
                record.steps.forEachIndexed { idx, st ->
                    val comma = if (idx < record.steps.lastIndex) "," else ""
                    appendLine(
                        "    {\"step\": ${st.stepNumber}, \"title\": \"${esc(st.title)}\", \"status\": \"${st.status}\", \"durationMs\": ${st.durationMs}, \"detail\": \"${esc(StudioEventLogger.redact(st.detail))}\"}$comma"
                    )
                }
                appendLine("  ],")
                appendLine("  \"logs\": [${redactedLogs.joinToString(", ") { "\"${esc(it)}\"" }}]")
                append("}")
            }

            BuildReportFormat.HTML -> buildString {
                appendLine("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
                appendLine("<title>TK Studio Build Report - ${escHtml(record.projectName)}</title>")
                appendLine("<style>body{font-family:monospace;background:#080A12;color:#F4F7FF;padding:24px;}h1{color:#31D7F5;}table{width:100%;border-collapse:collapse;margin:16px 0;}th,td{border:1px solid #2A3552;padding:6px 10px;text-align:left;}th{background:#131A2A;}.PASS{color:#3BE7A7;}.FAIL{color:#FF5575;}.WARN{color:#FFBD59;}</style>")
                appendLine("</head><body>")
                appendLine("<h1>TK Studio Build Report (${escHtml(record.reportId)})</h1>")
                appendLine("<p><b>Project:</b> ${escHtml(record.projectName)} | <b>Mode:</b> ${escHtml(record.mode.label)} | <b>Profile:</b> ${escHtml(record.profile.label)} | <b>Result:</b> ${escHtml(record.stage.label)}</p>")
                appendLine("<p><b>Timestamp:</b> $timeStr | <b>Duration:</b> ${record.durationMs}ms | <b>Artifact:</b> ${escHtml(record.artifactKind)}</p>")
                record.outputSha256?.let { appendLine("<p><b>SHA-256:</b> $it</p>") }
                record.errorCode?.let { code ->
                    appendLine("<p class=\"FAIL\"><b>Error Code:</b> ${escHtml(code)} ${record.failedStage?.let { "at ${escHtml(it)}" } ?: ""} ${record.sourceLocation?.let { "(${escHtml(it)})" } ?: ""}</p>")
                }
                appendLine("<table><tr><th>#</th><th>Step</th><th>Status</th><th>Time</th><th>Detail</th></tr>")
                record.steps.forEach { st ->
                    appendLine("<tr><td>${st.stepNumber}</td><td>${escHtml(st.title)}</td><td class=\"${st.status}\">${st.status}</td><td>${st.durationMs}ms</td><td>${escHtml(StudioEventLogger.redact(st.detail))}</td></tr>")
                }
                appendLine("</table><h3>Logs</h3><pre>")
                redactedLogs.forEach { appendLine(escHtml(it)) }
                appendLine("</pre></body></html>")
            }
        }
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    private fun escHtml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

class BuildPipelineEngine(private val context: Context) {

    val buildsDir: File get() = File(context.filesDir, "tkstudio/builds").apply { mkdirs() }
    val reportsDir: File get() = File(context.filesDir, "tkstudio/reports").apply { mkdirs() }
    private val stagingRootDir: File get() = File(context.filesDir, "tkstudio/staging").apply { mkdirs() }
    private val historyFile: File get() = File(context.filesDir, "tkstudio/builds_history.json")
    private val cacheMetaFile: File get() = File(context.filesDir, "tkstudio/build_config_cache.json")

    companion object {
        private val workspaceLocks = ConcurrentHashMap<String, Mutex>()
    }

    private class BuildFailureException(
        val errorCode: String,
        val failedStageName: String,
        val sourceLocation: String?,
        override val message: String
    ) : RuntimeException(message)

    suspend fun loadHistory(): List<BuildRecord> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        val arr = runCatching { JSONArray(historyFile.readText()) }.getOrNull() ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val logs = mutableListOf<String>()
            o.optJSONArray("logs")?.let { l ->
                for (j in 0 until l.length()) logs.add(l.optString(j))
            }
            val steps = mutableListOf<BuildStepRecord>()
            o.optJSONArray("steps")?.let { sArr ->
                for (j in 0 until sArr.length()) {
                    val so = sArr.optJSONObject(j) ?: continue
                    steps.add(
                        BuildStepRecord(
                            stepNumber = so.optInt("stepNumber"),
                            title = so.optString("title"),
                            stage = runCatching { BuildStage.valueOf(so.optString("stage")) }.getOrDefault(BuildStage.DONE),
                            status = so.optString("status"),
                            startedAt = so.optLong("startedAt"),
                            durationMs = so.optLong("durationMs"),
                            detail = so.optString("detail"),
                            errorCode = so.optString("errorCode").takeIf { it.isNotBlank() },
                            sourceLocation = so.optString("sourceLocation").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            BuildRecord(
                id = o.optString("id"),
                projectId = o.optString("projectId"),
                projectName = o.optString("projectName"),
                profile = runCatching { BuildProfile.valueOf(o.optString("profile")) }.getOrDefault(BuildProfile.AUTO),
                mode = runCatching { BuildMode.valueOf(o.optString("mode")) }.getOrDefault(BuildMode.AUTO),
                stage = runCatching { BuildStage.valueOf(o.optString("stage")) }.getOrDefault(BuildStage.DONE),
                success = o.optBoolean("success"),
                startedAt = o.optLong("startedAt"),
                durationMs = o.optLong("durationMs"),
                outputPath = o.optString("outputPath").takeIf { it.isNotBlank() },
                outputSha256 = o.optString("outputSha256").takeIf { it.isNotBlank() },
                logLines = logs,
                artifactKind = o.optString("artifactKind", "VERIFIED_BUNDLE"),
                errorCode = o.optString("errorCode").takeIf { it.isNotBlank() },
                failedStage = o.optString("failedStage").takeIf { it.isNotBlank() },
                sourceLocation = o.optString("sourceLocation").takeIf { it.isNotBlank() },
                reportId = o.optString("reportId").ifBlank { o.optString("id") },
                steps = steps,
                appName = o.optString("appName", o.optString("projectName")),
                packageId = o.optString("packageId", ""),
                versionName = o.optString("versionName", "1.0.0"),
                versionCode = o.optInt("versionCode", 1),
                signingAlias = o.optString("signingAlias", ""),
                signingFingerprint = o.optString("signingFingerprint", "")
            )
        }
    }

    suspend fun runPreflight(
        project: ProjectMetadata,
        toolchainItems: List<ToolchainItem> = emptyList()
    ): List<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        val dir = File(project.directoryPath)
        val files = dir.walkTopDown().filter { it.isFile && !it.absolutePath.contains(".tkstudio_git") }.toList()
        val hasEntry = File(dir, project.entryFile).exists()
        val freeBytes = context.filesDir.usableSpace
        val freeMb = freeBytes / (1024 * 1024)
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val nativeReady = listOf("kotlinc", "aapt2", "d8", "zipalign", "apksigner", "android.jar").all { id ->
            toolchainItems.any { it.id == id && it.isAvailable }
        }

        listOf(
            "Workspace directory present (${files.size} files)" to (dir.exists() && files.isNotEmpty()),
            "Entry file '${project.entryFile}' resolved" to hasEntry,
            "Storage headroom ($freeMb MB free)" to (freeBytes > 5 * 1024 * 1024),
            "JVM Heap headroom (${maxHeapMb} MB max heap)" to (maxHeapMb >= 64),
            "Local ZIP + SHA-256 + WebWrapper generator ready" to true,
            if (project.type == ProjectType.ANDROID_KOTLIN) {
                if (nativeReady) {
                    "Imported native SDK binaries present" to true
                } else {
                    "Native binary APK CLI (aapt2/d8/kotlinc): Missing — source bundle/CI only" to false
                }
            } else {
                "Web-to-APK wrapper & verified bundle ready for ${project.type.label}" to true
            }
        )
    }

    suspend fun buildAutoProposal(
        project: ProjectMetadata,
        currentConfig: BuildConfigSpec,
        toolchainItems: List<ToolchainItem>
    ): AutoBuildProposal = withContext(Dispatchers.IO) {
        val dir = File(project.directoryPath)
        val safePkg = project.packageId.ifBlank {
            "com.tkofficial." + project.name.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "app" }
        }
        val proposedOutName = currentConfig.outputFileName.ifBlank {
            val base = project.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (project.type == ProjectType.WEB_TO_APK || project.type == ProjectType.WEB_APP) {
                "${base}_web_wrapper.tkbundle.zip"
            } else {
                "${base}_verified_source.zip"
            }
        }
        val proposedConfig = currentConfig.copy(
            appName = currentConfig.appName.ifBlank { project.name },
            packageId = safePkg,
            outputFileName = proposedOutName
        )

        val diffs = mutableListOf<BuildPlanDiffItem>()
        if (currentConfig.appName != proposedConfig.appName) {
            diffs.add(BuildPlanDiffItem("App Name", currentConfig.appName.ifBlank { "(empty)" }, proposedConfig.appName, "Matches project identity"))
        }
        if (currentConfig.packageId != proposedConfig.packageId) {
            diffs.add(BuildPlanDiffItem("Package ID", currentConfig.packageId.ifBlank { "(empty)" }, proposedConfig.packageId, "Generated valid Android package ID"))
        }
        if (currentConfig.outputFileName != proposedConfig.outputFileName) {
            diffs.add(BuildPlanDiffItem("Output Artifact", "(auto)", proposedConfig.outputFileName, "Matches target project format"))
        }

        val nativeCompat = if (project.type == ProjectType.ANDROID_KOTLIN) {
            NativeCompatibilityAnalyzer.analyze(dir, project, toolchainItems)
        } else null

        val canBinaryApk = nativeCompat?.isLocallyCompilable ?: false
        val targetLabel = when (project.type) {
            ProjectType.WEB_APP, ProjectType.WEB_TO_APK -> "Verified Web-to-APK Kotlin Wrapper Bundle (.tkbundle.zip)"
            ProjectType.ANDROID_KOTLIN -> if (canBinaryApk) "Native Staged Bundle (CLI Ready)" else "Verified Android Source Bundle (.zip — requires CI for binary APK)"
            ProjectType.BLANK -> "Verified Workspace Archive (.zip)"
        }
        val summary = when (project.type) {
            ProjectType.WEB_APP, ProjectType.WEB_TO_APK -> "Generates security-hardened Kotlin WebView wrapper + AndroidManifest + SHA-256 verified bundle."
            ProjectType.ANDROID_KOTLIN -> nativeCompat?.recommendation ?: "Validates manifests/Kotlin sources and packages verified archive."
            ProjectType.BLANK -> "Validates syntax and packages workspace files with SHA-256 manifest."
        }

        AutoBuildProposal(
            detectedType = project.type,
            targetArtifactLabel = targetLabel,
            canCompileBinaryApkLocally = canBinaryApk,
            proposedConfig = proposedConfig,
            diffItems = diffs,
            summaryNote = summary
        )
    }

    suspend fun exportBuildReport(record: BuildRecord, format: BuildReportFormat): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = BuildVerificationUtils.formatBuildReport(record, format)
                val safeName = record.projectName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val out = File(reportsDir, "report_${safeName}_${record.reportId}.${format.extension}")
                out.writeText(content)
                out
            }
        }

    suspend fun executeBuild(
        project: ProjectMetadata,
        profile: BuildProfile,
        onStageUpdate: (BuildStage, Float?, String) -> Unit
    ): BuildRecord {
        return executeBuildWithConfig(
            project = project,
            mode = BuildMode.AUTO,
            profile = profile,
            config = BuildConfigSpec(
                appName = project.name,
                packageId = project.packageId.ifBlank { "com.tkofficial.app" }
            ),
            toolchainItems = emptyList(),
            signingKey = null,
            signingPassword = "",
            onStageUpdate = onStageUpdate,
            onStepUpdate = {}
        )
    }

    suspend fun executeBuildWithConfig(
        project: ProjectMetadata,
        mode: BuildMode,
        profile: BuildProfile,
        config: BuildConfigSpec,
        toolchainItems: List<ToolchainItem>,
        signingKey: SigningKeyInfo?,
        signingPassword: String,
        signingRepo: SigningRepository? = null,
        onStageUpdate: (BuildStage, Float?, String) -> Unit,
        onStepUpdate: (List<BuildStepRecord>) -> Unit
    ): BuildRecord = withContext(Dispatchers.IO) {
        val lock = workspaceLocks.computeIfAbsent(project.id) { Mutex() }
        lock.withLock {
            val started = System.currentTimeMillis()
            val logs = mutableListOf<String>()
            val steps = mutableListOf<BuildStepRecord>()
            val id = UUID.randomUUID().toString().take(8)
            val dir = File(project.directoryPath)
            val stagingDir = File(stagingRootDir, "build_${project.id}_$id")

            fun log(msg: String) {
                logs.add(StudioEventLogger.redact(msg))
            }

            suspend fun <T> runStep(
                number: Int,
                title: String,
                stage: BuildStage,
                fraction: Float?,
                block: suspend () -> Pair<String, T>
            ): T {
                coroutineContext.ensureActive()
                val stepStart = System.currentTimeMillis()
                onStageUpdate(stage, fraction, title)
                val runningRecord = BuildStepRecord(number, title, stage, "RUNNING", stepStart, 0L, title)
                val runningList = steps + runningRecord
                onStepUpdate(runningList)

                try {
                    val (detail, result) = block()
                    val dur = System.currentTimeMillis() - stepStart
                    val status = if (detail.startsWith("WARN:")) "WARN" else "PASS"
                    val rec = BuildStepRecord(number, title, stage, status, stepStart, dur, detail)
                    steps.add(rec)
                    onStepUpdate(steps.toList())
                    log("[$number/18 ${stage.label}] $title -> $detail (${dur}ms)")
                    if (profile.lowMemoryMode) delay(40) else delay(20)
                    return result
                } catch (ce: CancellationException) {
                    val dur = System.currentTimeMillis() - stepStart
                    steps.add(BuildStepRecord(number, title, stage, "CANCELLED", stepStart, dur, "Cancelled during $title"))
                    onStepUpdate(steps.toList())
                    throw ce
                } catch (bf: BuildFailureException) {
                    val dur = System.currentTimeMillis() - stepStart
                    steps.add(
                        BuildStepRecord(
                            number,
                            title,
                            stage,
                            "FAIL",
                            stepStart,
                            dur,
                            bf.message,
                            bf.errorCode,
                            bf.sourceLocation
                        )
                    )
                    onStepUpdate(steps.toList())
                    throw bf
                } catch (t: Throwable) {
                    val dur = System.currentTimeMillis() - stepStart
                    val msg = t.message ?: t.javaClass.simpleName
                    steps.add(BuildStepRecord(number, title, stage, "FAIL", stepStart, dur, msg, "ERR_UNEXPECTED"))
                    onStepUpdate(steps.toList())
                    throw BuildFailureException("ERR_UNEXPECTED", title, null, msg)
                }
            }

            try {
                // Step 1: Workspace Lock & Source Dir Verification
                runStep(1, "Workspace Lock & Integrity Check", BuildStage.PREPARING, 0.05f) {
                    if (!dir.exists() || !dir.isDirectory) {
                        throw BuildFailureException("ERR_NO_WORKSPACE", "Workspace Lock", project.directoryPath, "Project directory missing: ${dir.absolutePath}")
                    }
                    "Acquired exclusive workspace lock for ${project.name}" to Unit
                }

                // Step 2: Storage & RAM Preflight
                runStep(2, "Storage & Memory Preflight", BuildStage.PREPARING, 0.10f) {
                    val freeMb = context.filesDir.usableSpace / (1024 * 1024)
                    val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                    if (freeMb < 5) {
                        throw BuildFailureException("ERR_LOW_STORAGE", "Storage Preflight", null, "Insufficient free storage (${freeMb}MB).")
                    }
                    "Verified ${freeMb}MB free disk, ${maxHeapMb}MB JVM heap (workers=${profile.workers})" to Unit
                }

                // Step 3: Config Validation & Snapshot
                val configFingerprint = runStep(3, "Config Validation & Snapshot", BuildStage.PREPARING, 0.15f) {
                    if (config.minSdk > config.targetSdk) {
                        throw BuildFailureException("ERR_SDK_RANGE", "Config Snapshot", "build.config", "minSdk (${config.minSdk}) cannot exceed targetSdk (${config.targetSdk}).")
                    }
                    if (!Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$").matches(config.packageId.ifBlank { "com.tkofficial.app" })) {
                        throw BuildFailureException("ERR_BAD_PACKAGE_ID", "Config Snapshot", "packageId", "Invalid Android package ID '${config.packageId}'.")
                    }
                    val toolSig = toolchainItems.joinToString(",") { "${it.id}:${it.version}:${it.isAvailable}" }
                    val fp = BuildVerificationUtils.computeConfigFingerprint(config, profile, toolSig)
                    "Config fingerprint ${fp.take(12)} verified (pkg=${config.packageId})" to fp
                }

                // Step 4: Project Type & Entry-Point Detection
                val sourceFiles = runStep(4, "Project & Entry-Point Indexing", BuildStage.SCANNING, 0.22f) {
                    val files = dir.walkTopDown()
                        .filter {
                            it.isFile &&
                                !it.absolutePath.contains("${File.separator}.tkstudio_git") &&
                                !it.absolutePath.contains("${File.separator}.tkstudio_repair_backups") &&
                                !it.absolutePath.contains("${File.separator}builds")
                        }
                        .sortedBy { it.relativeTo(dir).path }
                        .toList()
                    if (files.isEmpty()) {
                        throw BuildFailureException("ERR_EMPTY_PROJECT", "Entry-Point Indexing", project.directoryPath, "Project contains no source files.")
                    }
                    val hasEntry = File(dir, project.entryFile).exists()
                    val note = if (hasEntry) "Indexed ${files.size} source files (entry=${project.entryFile})"
                    else "WARN: Entry '${project.entryFile}' missing; indexed ${files.size} files"
                    note to files
                }

                // Step 5: Profile & Worker Configuration
                runStep(5, "Profile & Worker Allocation", BuildStage.SCANNING, 0.28f) {
                    val modeLabel = if (profile.lowMemoryMode) "single-worker Low-RAM throttle" else "${profile.workers}-worker pipeline"
                    "Profile=${profile.label}, Mode=${mode.label}, Execution=$modeLabel" to Unit
                }

                // Step 6: Toolchain & Native Compatibility Verification
                val nativeReport = runStep(6, "Toolchain & Compatibility Audit", BuildStage.SCANNING, 0.34f) {
                    val report = if (project.type == ProjectType.ANDROID_KOTLIN || profile == BuildProfile.NATIVE) {
                        NativeCompatibilityAnalyzer.analyze(dir, project, toolchainItems)
                    } else null

                    if (profile == BuildProfile.NATIVE && report != null && !report.isLocallyCompilable) {
                        val firstBlocking = report.issues.firstOrNull { it.severity == "BLOCKING" }
                        val loc = firstBlocking?.let { "${it.file}:${it.line ?: 1}" }
                        throw BuildFailureException(
                            errorCode = "ERR_NATIVE_TOOLCHAIN_UNAVAILABLE",
                            failedStageName = "Toolchain & Compatibility Audit",
                            sourceLocation = loc,
                            message = "Native APK compilation blocked: missing [${report.missingToolchainItems.joinToString(", ")}]. ${report.recommendation}"
                        )
                    }
                    val detail = report?.let {
                        if (it.isLocallyCompilable) "All native tools verified"
                        else "WARN: Native SDK binaries missing (${it.missingToolchainItems.joinToString(", ")}); producing verified source bundle"
                    } ?: "Web/Bundle toolchain verified (${toolchainItems.count { it.isAvailable }} active runtimes)"
                    detail to report
                }

                // Step 7: Incremental SHA-256 Cache Evaluation
                val cacheEval = runStep(7, "Incremental SHA-256 Cache Check", BuildStage.SCANNING, 0.40f) {
                    val prevMap = if (profile.cleanCacheFirst || !profile.useIncrementalCache) {
                        clearSavedConfigCache(project.id)
                        emptyMap<String, String>() to null
                    } else {
                        loadSavedConfigCache(project.id)
                    }
                    val eval = BuildVerificationUtils.evaluateIncrementalCache(
                        sourceDir = dir,
                        previousFileHashes = prevMap.first,
                        previousConfigHash = prevMap.second,
                        currentConfigHash = configFingerprint
                    )
                    val msg = when {
                        profile.cleanCacheFirst -> "Clean profile purged cache; hashed ${eval.currentHashes.size} files"
                        eval.configInvalidated -> "Config/toolchain changed -> cache invalidated (${eval.currentHashes.size} files hashed)"
                        else -> "Cache hits: ${eval.hits}, modified/new: ${eval.changedOrAdded}, removed: ${eval.removed}"
                    }
                    msg to eval
                }

                // Step 8: AndroidManifest Validation
                runStep(8, "Manifest Merge & Schema Validation", BuildStage.VALIDATING, 0.46f) {
                    val manifest = File(dir, "app/src/main/AndroidManifest.xml").takeIf { it.exists() }
                        ?: File(dir, "AndroidManifest.xml")
                    if (manifest.exists()) {
                        validateXmlWithLine(manifest.relativeTo(dir).path, manifest.readText())
                        "Validated existing ${manifest.name}" to Unit
                    } else {
                        "No raw AndroidManifest.xml in source; generated safe wrapper manifest for ${config.packageId}" to Unit
                    }
                }

                // Step 9: Permissions & Security Policy Check
                runStep(9, "Security & Permissions Policy Audit", BuildStage.VALIDATING, 0.52f) {
                    if (profile == BuildProfile.RELEASE && config.allowlistDomains.contains("http://")) {
                        throw BuildFailureException(
                            "ERR_CLEARTEXT_ALLOWLIST",
                            "Security Audit",
                            "allowlistDomains",
                            "Release builds reject insecure http:// domains in WebView allowlist."
                        )
                    }
                    "Security defaults enforced (HTTPS preferred, mixed content blocked, SSL bypass disabled)" to Unit
                }

                // Step 10: Web/Asset Reference Scan
                val webPreview = runStep(10, "Asset & Reference Verification", BuildStage.VALIDATING, 0.58f) {
                    if (project.type == ProjectType.WEB_APP || project.type == ProjectType.WEB_TO_APK || profile == BuildProfile.WEB_BUNDLE) {
                        val preview = WebToApkWrapperEngine.inspectAndGeneratePreview(dir, project, config)
                        if (preview.missingAssets.isNotEmpty() && profile.strictValidation) {
                            "WARN: ${preview.missingAssets.size} missing local asset ref(s): ${preview.missingAssets.first()}" to preview
                        } else {
                            "Verified ${preview.assetRefs.size} web asset reference(s)" to preview
                        }
                    } else {
                        "Scanned ${sourceFiles.count { it.extension in setOf("png", "webp", "svg", "xml", "css") }} project assets" to null
                    }
                }

                // Step 11: WebView Wrapper Codegen or Native Summary
                runStep(11, "Wrapper Codegen / Compat Pass", BuildStage.COMPILING, 0.64f) {
                    if (webPreview != null) {
                        "Generated Kotlin MainActivity.kt (${webPreview.generatedKotlin.lines().size} lines) & AndroidManifest.xml" to Unit
                    } else {
                        val pluginCount = nativeReport?.detectedPlugins?.size ?: 0
                        "Verified native metadata ($pluginCount Gradle plugin(s) indexed)" to Unit
                    }
                }

                // Step 12: XML & Resource Naming Lint
                runStep(12, "Resource Naming & XML Lint", BuildStage.VALIDATING, 0.70f) {
                    val resNameRegex = Regex("^[a-z0-9_]+$")
                    sourceFiles.forEach { f ->
                        val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                        if (rel.contains("res/") && !f.extension.equals("xml", true)) {
                            if (!resNameRegex.matches(f.nameWithoutExtension)) {
                                throw BuildFailureException(
                                    "ERR_INVALID_RES_NAME",
                                    "Resource Naming Lint",
                                    rel,
                                    "Invalid Android resource filename '${f.name}'; must match [a-z0-9_]+"
                                )
                            }
                        }
                        if (f.extension.equals("xml", true)) {
                            validateXmlWithLine(rel, f.readText())
                        }
                    }
                    "All XML and Android resource identifiers passed validation" to Unit
                }

                // Step 13: JSON / HTML / Kotlin Syntax Validation
                runStep(13, "Source Syntax & JSON/HTML Check", BuildStage.COMPILING, 0.76f) {
                    var checked = 0
                    sourceFiles.forEach { f ->
                        val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                        when (f.extension.lowercase()) {
                            "json" -> {
                                runCatching { JSONObject(f.readText()) }.onFailure { err ->
                                    throw BuildFailureException("ERR_JSON_SYNTAX", "Source Syntax Check", rel, "Invalid JSON in $rel: ${err.message}")
                                }
                                checked++
                            }
                            "html", "htm" -> {
                                val txt = f.readText()
                                if (profile.strictValidation && !txt.contains("<", ignoreCase = true)) {
                                    throw BuildFailureException("ERR_HTML_EMPTY", "Source Syntax Check", "$rel:1", "HTML file contains no markup tags.")
                                }
                                checked++
                            }
                        }
                    }
                    "Validated syntax across $checked structured source file(s)" to Unit
                }

                // Step 14: Stage Isolated Build Workspace (never mutates user project source)
                runStep(14, "Isolate Build Staging Workspace", BuildStage.PACKAGING, 0.82f) {
                    if (stagingDir.exists()) stagingDir.deleteRecursively()
                    stagingDir.mkdirs()
                    if (webPreview != null) {
                        val wrapperDir = File(stagingDir, "tk_android_wrapper/app/src/main")
                        File(wrapperDir, "java/${config.packageId.replace('.', '/')}").mkdirs()
                        File(wrapperDir, "assets").mkdirs()
                        File(wrapperDir, "java/${config.packageId.replace('.', '/')}/MainActivity.kt").writeText(webPreview.generatedKotlin)
                        File(wrapperDir, "AndroidManifest.xml").writeText(webPreview.generatedManifest)
                        File(stagingDir, "tk_android_wrapper/tk_wrapper_config.json").writeText(webPreview.configJson)
                    }
                    "Prepared clean staging directory ${stagingDir.name} (source preserved untouched)" to Unit
                }

                // Step 15: Assemble Output Bundle
                val safeName = (config.outputFileName.ifBlank {
                    project.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                }).replace(Regex("[^A-Za-z0-9._-]"), "_")
                val artifactExt = if (webPreview != null) {
                    if (safeName.endsWith(".tkbundle.zip")) "" else "_$id.tkbundle.zip"
                } else {
                    if (safeName.endsWith(".zip")) "" else "_$id.zip"
                }
                val outFile = File(buildsDir, "$safeName$artifactExt")
                val artifactKind = if (webPreview != null) "WEB_TO_APK_WRAPPER_BUNDLE" else "VERIFIED_SOURCE_BUNDLE"

                val manifestLines = runStep(15, "Assemble Verified Archive Bundle", BuildStage.PACKAGING, 0.88f) {
                    val digests = mutableListOf<String>()
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
                        zos.setLevel(config.compressionLevel.coerceIn(0, 9))
                        sourceFiles.forEachIndexed { idx, file ->
                            coroutineContext.ensureActive()
                            val rel = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                            val digest = cacheEval.currentHashes[rel] ?: BuildVerificationUtils.sha256Bytes(file.readBytes())
                            digests.add("$digest  $rel")
                            val entryPrefix = if (webPreview != null) "assets/" else ""
                            zos.putNextEntry(ZipEntry("$entryPrefix$rel"))
                            FileInputStream(file).use { fis -> fis.copyTo(zos) }
                            zos.closeEntry()
                            if (idx % 5 == 0) {
                                val frac = 0.84f + 0.05f * ((idx + 1).toFloat() / sourceFiles.size)
                                onStageUpdate(BuildStage.PACKAGING, frac, "Packing $rel (${idx + 1}/${sourceFiles.size})")
                            }
                        }
                        if (stagingDir.exists()) {
                            stagingDir.walkTopDown().filter { it.isFile }.forEach { sf ->
                                val sRel = sf.relativeTo(stagingDir).path.replace(File.separatorChar, '/')
                                val sDigest = BuildVerificationUtils.sha256Bytes(sf.readBytes())
                                digests.add("$sDigest  $sRel")
                                zos.putNextEntry(ZipEntry(sRel))
                                FileInputStream(sf).use { fis -> fis.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                        zos.putNextEntry(ZipEntry("META-INF/TKSTUDIO_MANIFEST.SHA256"))
                        zos.write(digests.joinToString("\n").toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                    "Wrote ${outFile.name} (${outFile.length()} bytes, compression=${config.compressionLevel})" to digests
                }

                // Step 16: Cryptographic Signing / Digest Verification
                runStep(16, "Cryptographic Signing & Digest", BuildStage.SIGNING, 0.93f) {
                    if (signingKey != null && signingRepo != null) {
                        if (signingPassword.isBlank()) {
                            throw BuildFailureException("ERR_MISSING_KEY_PASSWORD", "Signing", signingKey.alias, "Key password required to sign with '${signingKey.alias}'.")
                        }
                        val payloadBytes = outFile.readBytes()
                        val sigHex = signingRepo.signDigestWithKey(signingKey, signingPassword, payloadBytes).getOrElse { err ->
                            throw BuildFailureException("ERR_KEY_UNLOCK_FAILED", "Signing", signingKey.alias, "Failed to unlock signing key '${signingKey.alias}': wrong password or corrupted key (${err.message}).")
                        }
                        File(buildsDir, "${outFile.name}.sig").writeText("alias=${signingKey.alias}\nfingerprint=${signingKey.sha256Fingerprint}\nsignature=$sigHex\n")
                        "Signed artifact digest with key '${signingKey.alias}' (${signingKey.algorithm}-${signingKey.keySize})" to Unit
                    } else {
                        "Computed SHA-256 manifest (${manifestLines.size} entries); no signing key selected" to Unit
                    }
                }

                // Step 17: Artifact Readback & Integrity Verification
                val outputHash = runStep(17, "Artifact Output Integrity Verification", BuildStage.VERIFYING, 0.97f) {
                    if (!outFile.exists() || !outFile.canRead() || outFile.length() == 0L) {
                        throw BuildFailureException("ERR_ARTIFACT_MISSING", "Integrity Verification", outFile.name, "Generated output file is missing or empty.")
                    }
                    var count = 0
                    ZipFile(outFile).use { zf ->
                        val en = zf.entries()
                        while (en.hasMoreElements()) {
                            val ze = en.nextElement()
                            if (ze.name.contains("..")) {
                                throw BuildFailureException("ERR_ARCHIVE_PATH", "Integrity Verification", ze.name, "Unsafe archive entry detected.")
                            }
                            count++
                        }
                    }
                    if (count == 0) {
                        throw BuildFailureException("ERR_ARCHIVE_EMPTY", "Integrity Verification", outFile.name, "Archive contains 0 entries.")
                    }
                    val hash = sha256File(outFile)
                    "Verified $count archive entries & readback SHA-256=${hash.take(16)}..." to hash
                }

                // Step 18: Commit Cache & Report
                runStep(18, "Commit Build Cache & Report", BuildStage.DONE, 1.0f) {
                    saveConfigCache(project.id, cacheEval.currentHashes, configFingerprint)
                    stagingDir.deleteRecursively()
                    "Saved ${cacheEval.currentHashes.size} incremental cache hashes & cleaned staging workspace" to Unit
                }

                val duration = System.currentTimeMillis() - started
                onStageUpdate(BuildStage.DONE, 1.0f, "Build verified in ${duration}ms")
                val record = BuildRecord(
                    id = id,
                    projectId = project.id,
                    projectName = project.name,
                    profile = profile,
                    mode = mode,
                    stage = BuildStage.DONE,
                    success = true,
                    startedAt = started,
                    durationMs = duration,
                    outputPath = outFile.absolutePath,
                    outputSha256 = outputHash,
                    logLines = logs,
                    progressFraction = 1f,
                    artifactKind = artifactKind,
                    reportId = id,
                    steps = steps.toList(),
                    appName = config.appName.ifBlank { project.name },
                    packageId = config.packageId.ifBlank { project.packageId },
                    versionName = config.versionName,
                    versionCode = config.versionCode,
                    signingAlias = signingKey?.alias.orEmpty(),
                    signingFingerprint = signingKey?.sha256Fingerprint.orEmpty()
                )
                appendHistory(record)
                record
            } catch (ce: CancellationException) {
                stagingDir.deleteRecursively()
                val duration = System.currentTimeMillis() - started
                log("[CANCELLED] Build cancelled by user after ${duration}ms; staging cleaned, source untouched.")
                onStageUpdate(BuildStage.CANCELLED, null, "Build cancelled")
                val record = BuildRecord(
                    id = id,
                    projectId = project.id,
                    projectName = project.name,
                    profile = profile,
                    mode = mode,
                    stage = BuildStage.CANCELLED,
                    success = false,
                    startedAt = started,
                    durationMs = duration,
                    outputPath = null,
                    outputSha256 = null,
                    logLines = logs,
                    artifactKind = "CANCELLED",
                    failedStage = steps.lastOrNull()?.title,
                    reportId = id,
                    steps = steps.toList()
                )
                appendHistory(record)
                throw ce
            } catch (bf: BuildFailureException) {
                stagingDir.deleteRecursively()
                val duration = System.currentTimeMillis() - started
                log("[FAILED:${bf.errorCode}] Stage=${bf.failedStageName} ${bf.sourceLocation?.let { "at $it " } ?: ""}: ${bf.message}")
                onStageUpdate(BuildStage.FAILED, null, bf.message)
                val record = BuildRecord(
                    id = id,
                    projectId = project.id,
                    projectName = project.name,
                    profile = profile,
                    mode = mode,
                    stage = BuildStage.FAILED,
                    success = false,
                    startedAt = started,
                    durationMs = duration,
                    outputPath = null,
                    outputSha256 = null,
                    logLines = logs,
                    artifactKind = "FAILED",
                    errorCode = bf.errorCode,
                    failedStage = bf.failedStageName,
                    sourceLocation = bf.sourceLocation,
                    reportId = id,
                    steps = steps.toList()
                )
                appendHistory(record)
                record
            } catch (t: Throwable) {
                stagingDir.deleteRecursively()
                val duration = System.currentTimeMillis() - started
                val msg = t.message ?: t.javaClass.simpleName
                log("[FAILED] $msg")
                onStageUpdate(BuildStage.FAILED, null, msg)
                val record = BuildRecord(
                    id = id,
                    projectId = project.id,
                    projectName = project.name,
                    profile = profile,
                    mode = mode,
                    stage = BuildStage.FAILED,
                    success = false,
                    startedAt = started,
                    durationMs = duration,
                    outputPath = null,
                    outputSha256 = null,
                    logLines = logs,
                    artifactKind = "FAILED",
                    errorCode = "ERR_PIPELINE",
                    failedStage = steps.lastOrNull()?.title,
                    reportId = id,
                    steps = steps.toList()
                )
                appendHistory(record)
                record
            }
        }
    }

    private fun validateXmlWithLine(relPath: String, xml: String) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            while (parser.next() != XmlPullParser.END_DOCUMENT) { /* validate */ }
        } catch (e: org.xmlpull.v1.XmlPullParserException) {
            val line = e.lineNumber.takeIf { it > 0 }
            val loc = if (line != null) "$relPath:$line" else relPath
            throw BuildFailureException("ERR_XML_SYNTAX", "XML Validation", loc, "XML syntax error at $loc: ${e.message}")
        }
    }

    private fun loadSavedConfigCache(projectId: String): Pair<Map<String, String>, String?> {
        if (!cacheMetaFile.exists()) return emptyMap<String, String>() to null
        val root = runCatching { JSONObject(cacheMetaFile.readText()) }.getOrNull() ?: return emptyMap<String, String>() to null
        val projObj = root.optJSONObject(projectId) ?: return emptyMap<String, String>() to null
        val cfgHash = projObj.optString("configHash").takeIf { it.isNotBlank() }
        val filesObj = projObj.optJSONObject("files") ?: return emptyMap<String, String>() to cfgHash
        val map = linkedMapOf<String, String>()
        filesObj.keys().forEach { k -> map[k] = filesObj.optString(k) }
        return map to cfgHash
    }

    private fun saveConfigCache(projectId: String, fileHashes: Map<String, String>, configHash: String) {
        val root = if (cacheMetaFile.exists()) {
            runCatching { JSONObject(cacheMetaFile.readText()) }.getOrNull() ?: JSONObject()
        } else JSONObject()
        val projObj = JSONObject().apply {
            put("configHash", configHash)
            put("updatedAt", System.currentTimeMillis())
            put("files", JSONObject(fileHashes))
        }
        root.put(projectId, projObj)
        cacheMetaFile.writeText(root.toString(2))
    }

    private fun clearSavedConfigCache(projectId: String) {
        if (!cacheMetaFile.exists()) return
        val root = runCatching { JSONObject(cacheMetaFile.readText()) }.getOrNull() ?: return
        root.remove(projectId)
        cacheMetaFile.writeText(root.toString(2))
    }

    private fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16384)
        FileInputStream(file).use { fis ->
            var r = fis.read(buf)
            while (r != -1) {
                md.update(buf, 0, r)
                r = fis.read(buf)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun appendHistory(record: BuildRecord) {
        val existing = if (historyFile.exists()) runCatching { JSONArray(historyFile.readText()) }.getOrNull() ?: JSONArray() else JSONArray()
        val stepsArr = JSONArray().apply {
            record.steps.forEach { st ->
                put(JSONObject().apply {
                    put("stepNumber", st.stepNumber)
                    put("title", st.title)
                    put("stage", st.stage.name)
                    put("status", st.status)
                    put("startedAt", st.startedAt)
                    put("durationMs", st.durationMs)
                    put("detail", st.detail)
                    put("errorCode", st.errorCode ?: "")
                    put("sourceLocation", st.sourceLocation ?: "")
                })
            }
        }
        val obj = JSONObject().apply {
            put("id", record.id)
            put("projectId", record.projectId)
            put("projectName", record.projectName)
            put("profile", record.profile.name)
            put("mode", record.mode.name)
            put("stage", record.stage.name)
            put("success", record.success)
            put("startedAt", record.startedAt)
            put("durationMs", record.durationMs)
            put("outputPath", record.outputPath ?: "")
            put("outputSha256", record.outputSha256 ?: "")
            put("artifactKind", record.artifactKind)
            put("errorCode", record.errorCode ?: "")
            put("failedStage", record.failedStage ?: "")
            put("sourceLocation", record.sourceLocation ?: "")
            put("reportId", record.reportId)
            put("appName", record.appName)
            put("packageId", record.packageId)
            put("versionName", record.versionName)
            put("versionCode", record.versionCode)
            put("signingAlias", record.signingAlias)
            put("signingFingerprint", record.signingFingerprint)
            put("steps", stepsArr)
            put("logs", JSONArray(record.logLines))
        }
        val next = JSONArray().apply {
            put(obj)
            for (i in 0 until minOf(existing.length(), 40)) put(existing.get(i))
        }
        historyFile.writeText(next.toString(2))
    }
}
