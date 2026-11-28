package com.example.data

import com.example.model.BuildRecord
import com.example.model.DownloadedArtifactRecord
import com.example.model.MyApplicationRecord
import com.example.model.ProjectMetadata
import com.example.model.SigningContinuityState
import com.example.model.SigningKeyInfo
import java.io.File

object MyApplicationsEngine {

    fun deriveApplications(
        projects: List<ProjectMetadata>,
        builds: List<BuildRecord>,
        signingKeys: List<SigningKeyInfo>,
        ciArtifacts: List<DownloadedArtifactRecord> = emptyList()
    ): List<MyApplicationRecord> {
        val projectById = projects.associateBy { it.id }
        val keysByAlias = signingKeys.associateBy { it.alias }

        // Group latest build per projectId (preserve most recent record per project)
        val latestBuildByProject = linkedMapOf<String, BuildRecord>()
        builds.forEach { rec ->
            if (rec.success && !latestBuildByProject.containsKey(rec.projectId)) {
                latestBuildByProject[rec.projectId] = rec
            }
        }

        val records = mutableListOf<MyApplicationRecord>()

        latestBuildByProject.values.forEach { build ->
            val proj = projectById[build.projectId]
            val artifactFile = build.outputPath?.let { File(it) }
            val artifactExists = artifactFile?.exists() == true
            val (pkgFromProj, verNameProj, verCodeProj) = readProjectConfigTuple(proj)

            val resolvedPkg = build.packageId.ifBlank { pkgFromProj.ifBlank { "com.tkofficial.app" } }
            val resolvedVerName = build.versionName.ifBlank { verNameProj }
            val resolvedVerCode = if (build.versionCode > 0) build.versionCode else verCodeProj

            val (continuity, note) = evaluateSigningContinuity(
                artifactExists = artifactExists,
                signingAlias = build.signingAlias,
                recordedFingerprint = build.signingFingerprint,
                keysByAlias = keysByAlias
            )

            records.add(
                MyApplicationRecord(
                    id = "app_build_${build.id}",
                    appName = build.appName.ifBlank { proj?.name ?: build.projectName },
                    packageId = resolvedPkg,
                    versionName = resolvedVerName,
                    versionCode = resolvedVerCode,
                    sourceProjectId = proj?.id,
                    sourceProjectName = proj?.name ?: "${build.projectName} (Source Missing)",
                    sourceProjectExists = proj != null && File(proj.directoryPath).exists(),
                    lastBuildAt = build.startedAt,
                    buildType = "${build.mode.label} • ${build.profile.label}",
                    artifactKind = build.artifactKind,
                    artifactPath = build.outputPath,
                    artifactExists = artifactExists,
                    outputSha256 = build.outputSha256,
                    signingAlias = build.signingAlias,
                    signingFingerprint = build.signingFingerprint,
                    continuityState = continuity,
                    continuityNote = note
                )
            )
        }

        // Also surface any downloaded CI APK artifacts that have real extracted APKs
        ciArtifacts.filter { it.extractedApkPath != null }.forEach { art ->
            val apkFile = File(art.extractedApkPath!!)
            val exists = apkFile.exists()
            records.add(
                MyApplicationRecord(
                    id = "app_ci_${art.id}",
                    appName = art.name,
                    packageId = "CI Artifact",
                    versionName = "CI",
                    versionCode = 1,
                    sourceProjectId = null,
                    sourceProjectName = "GitHub Actions Artifact",
                    sourceProjectExists = false,
                    lastBuildAt = art.downloadedAt,
                    buildType = "GitHub Actions CI",
                    artifactKind = "CI_APK",
                    artifactPath = art.extractedApkPath,
                    artifactExists = exists,
                    outputSha256 = art.sha256,
                    signingAlias = "",
                    signingFingerprint = "",
                    continuityState = if (exists) SigningContinuityState.UNSIGNED else SigningContinuityState.ARTIFACT_MISSING,
                    continuityNote = if (exists) "Inspect APK in APK Tool to verify embedded release certificate." else "CI APK file missing on disk."
                )
            )
        }

        return records.sortedByDescending { it.lastBuildAt }
    }

    fun evaluateSigningContinuity(
        artifactExists: Boolean,
        signingAlias: String,
        recordedFingerprint: String,
        keysByAlias: Map<String, SigningKeyInfo>
    ): Pair<SigningContinuityState, String> {
        if (!artifactExists) {
            return SigningContinuityState.ARTIFACT_MISSING to "Build output file is missing on disk; rebuild to restore."
        }
        if (signingAlias.isBlank() && recordedFingerprint.isBlank()) {
            return SigningContinuityState.UNSIGNED to "Built without a vault signing key; assign a release key before distributing upgrades."
        }
        val activeKey = keysByAlias[signingAlias]
            ?: keysByAlias.values.firstOrNull { it.sha256Fingerprint == recordedFingerprint }
        return when {
            activeKey == null -> SigningContinuityState.KEY_MISSING to
                "Signing key '$signingAlias' is missing from this device's vault; Android will reject mismatched-key upgrades."
            recordedFingerprint.isNotBlank() && activeKey.sha256Fingerprint != recordedFingerprint ->
                SigningContinuityState.KEY_MISMATCH to
                    "Active vault key '${activeKey.alias}' fingerprint differs from previous build! Upgrading existing installs will fail."
            else -> SigningContinuityState.MATCHED to
                "Verified continuity with vault key '${activeKey.alias}' (${activeKey.sha256Fingerprint.take(14)}...)."
        }
    }

    private fun readProjectConfigTuple(project: ProjectMetadata?): Triple<String, String, Int> {
        if (project == null) return Triple("", "1.0.0", 1)
        val cfgFile = File(project.directoryPath, "app.config.json")
        if (cfgFile.exists()) {
            val raw = runCatching { cfgFile.readText() }.getOrNull()
            if (!raw.isNullOrBlank()) {
                val pkg = extractJsonString(raw, "packageId") ?: project.packageId
                val verName = extractJsonString(raw, "versionName") ?: "1.0.0"
                val verCode = extractJsonInt(raw, "versionCode") ?: 1
                return Triple(pkg, verName, verCode)
            }
        }
        return Triple(project.packageId, "1.0.0", 1)
    }

    fun updateProjectVersionSpec(
        project: ProjectMetadata,
        appName: String,
        packageId: String,
        versionName: String,
        versionCode: Int
    ): Result<Unit> = runCatching {
        val dir = File(project.directoryPath)
        if (!dir.exists()) throw IllegalStateException("Project directory not found.")
        val cfgFile = File(dir, "app.config.json")
        if (cfgFile.exists()) {
            val safeAppName = appName.trim().ifBlank { project.name }
            val safePkg = packageId.trim().ifBlank { project.packageId }
            val safeVerName = versionName.trim().ifBlank { "1.0.0" }
            val safeVerCode = versionCode.coerceAtLeast(1)
            val updated = upsertJsonFields(
                raw = cfgFile.readText(),
                stringFields = linkedMapOf(
                    "appName" to safeAppName,
                    "packageId" to safePkg,
                    "versionName" to safeVerName
                ),
                intFields = linkedMapOf("versionCode" to safeVerCode)
            )
            cfgFile.writeText(updated)
        }
    }

    private fun extractJsonString(raw: String, key: String): String? {
        val rx = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return rx.find(raw)?.groupValues?.getOrNull(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
    }

    private fun extractJsonInt(raw: String, key: String): Int? {
        val rx = Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
        return rx.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun escapeJson(value: String): String = buildString(value.length + 4) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun upsertJsonFields(
        raw: String,
        stringFields: Map<String, String>,
        intFields: Map<String, Int>
    ): String {
        var text = raw.trim()
        if (!text.startsWith("{") || !text.endsWith("}")) {
            text = "{}"
        }
        stringFields.forEach { (k, v) ->
            val replacement = "\"$k\": \"${escapeJson(v)}\""
            val rx = Regex(""""${Regex.escape(k)}"\s*:\s*"(?:[^"\\]|\\.)*"""")
            text = if (rx.containsMatchIn(text)) {
                rx.replaceFirst(text, Regex.escapeReplacement(replacement))
            } else {
                insertJsonEntry(text, replacement)
            }
        }
        intFields.forEach { (k, v) ->
            val replacement = "\"$k\": $v"
            val rx = Regex(""""${Regex.escape(k)}"\s*:\s*-?\d+""")
            text = if (rx.containsMatchIn(text)) {
                rx.replaceFirst(text, Regex.escapeReplacement(replacement))
            } else {
                insertJsonEntry(text, replacement)
            }
        }
        return text
    }

    private fun insertJsonEntry(jsonObj: String, entry: String): String {
        val body = jsonObj.removePrefix("{").removeSuffix("}").trim()
        return if (body.isEmpty()) "{\n  $entry\n}" else "{\n  $body,\n  $entry\n}"
    }
}
