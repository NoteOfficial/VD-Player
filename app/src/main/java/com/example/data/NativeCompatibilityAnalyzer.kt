package com.example.data

import com.example.model.NativeCompatIssue
import com.example.model.NativeCompatibilityReport
import com.example.model.ProjectMetadata
import com.example.model.ToolchainItem
import java.io.File

/**
 * Inspects imported Android Kotlin/Java/Gradle workspaces honestly against on-device toolchain
 * capabilities without silently modifying imported user sources.
 */
object NativeCompatibilityAnalyzer {

    private val pluginRegex = Regex("""(?:id\(["']([^"']+)["']\)|alias\(([^)]+)\)|apply\s+plugin:\s*["']([^"']+)["'])""")
    private val depRegex = Regex("""(?:implementation|api|ksp|kapt|testImplementation)\s*\(?\s*["']([^"']+)["']""")

    fun analyze(
        projectDir: File,
        project: ProjectMetadata,
        toolchainItems: List<ToolchainItem>
    ): NativeCompatibilityReport {
        val files = projectDir.walkTopDown()
            .filter { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") && !it.absolutePath.contains("${File.separator}builds") }
            .toList()

        val gradleFiles = files.filter { it.name.endsWith(".gradle") || it.name.endsWith(".gradle.kts") }
        val manifestFiles = files.filter { it.name == "AndroidManifest.xml" }
        val nativeSoFiles = files.filter { it.extension.equals("so", ignoreCase = true) }
        val jarAarFiles = files.filter { it.extension.lowercase() in setOf("jar", "aar") }

        val plugins = linkedSetOf<String>()
        val deps = linkedSetOf<String>()
        val issues = mutableListOf<NativeCompatIssue>()

        gradleFiles.forEach { gf ->
            val rel = gf.relativeTo(projectDir).path.replace(File.separatorChar, '/')
            val lines = runCatching { gf.readLines() }.getOrDefault(emptyList())
            lines.forEachIndexed { idx, line ->
                pluginRegex.findAll(line).forEach { m ->
                    val name = listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3]).firstOrNull { it.isNotBlank() }
                    if (name != null) plugins.add(name)
                }
                depRegex.findAll(line).forEach { m ->
                    deps.add(m.groupValues[1])
                }
                if (line.contains("com.android.application") || line.contains("libs.plugins.android.application")) {
                    issues.add(
                        NativeCompatIssue(
                            severity = "BLOCKING",
                            file = rel,
                            line = idx + 1,
                            message = "Android Gradle Plugin (AGP) daemon compilation requires a host JVM/CI runner (Gradle/AGP cannot execute inside Android app sandbox)."
                        )
                    )
                }
                if (line.contains("ndk") || line.contains("cmake") || line.contains("externalNativeBuild")) {
                    issues.add(
                        NativeCompatIssue(
                            severity = "BLOCKING",
                            file = rel,
                            line = idx + 1,
                            message = "Native C/C++ NDK/CMake build step is unsupported on device."
                        )
                    )
                }
                if (line.contains("kapt") || line.contains("ksp")) {
                    issues.add(
                        NativeCompatIssue(
                            severity = "WARNING",
                            file = rel,
                            line = idx + 1,
                            message = "Annotation processing / KSP compiler plugins require desktop/CI Gradle toolchain."
                        )
                    )
                }
            }
        }

        if (nativeSoFiles.isNotEmpty()) {
            issues.add(
                NativeCompatIssue(
                    severity = "WARNING",
                    file = nativeSoFiles.first().relativeTo(projectDir).path,
                    line = null,
                    message = "${nativeSoFiles.size} prebuilt .so native library file(s) found; verify target ABI compatibility."
                )
            )
        }
        if (jarAarFiles.isNotEmpty()) {
            issues.add(
                NativeCompatIssue(
                    severity = "INFO",
                    file = jarAarFiles.first().relativeTo(projectDir).path,
                    line = null,
                    message = "${jarAarFiles.size} local JAR/AAR binary dependencies detected."
                )
            )
        }

        var canSafeRepair = false
        manifestFiles.forEach { mf ->
            val rel = mf.relativeTo(projectDir).path.replace(File.separatorChar, '/')
            val lines = runCatching { mf.readLines() }.getOrDefault(emptyList())
            val fullText = lines.joinToString("\n")
            if (!fullText.contains("android:exported") && fullText.contains("<activity")) {
                canSafeRepair = true
                val actIdx = lines.indexOfFirst { it.contains("<activity") }.takeIf { it >= 0 }
                issues.add(
                    NativeCompatIssue(
                        severity = "WARNING",
                        file = rel,
                        line = actIdx?.plus(1),
                        message = "Activity declaration missing explicit android:exported attribute (required for Android 12+).",
                        canSafeRepair = true
                    )
                )
            }
            if (fullText.contains("""android:usesCleartextTraffic="true"""")) {
                canSafeRepair = true
                val cleartextIdx = lines.indexOfFirst { it.contains("usesCleartextTraffic=\"true\"") }.takeIf { it >= 0 }
                issues.add(
                    NativeCompatIssue(
                        severity = "WARNING",
                        file = rel,
                        line = cleartextIdx?.plus(1),
                        message = "Manifest enables cleartext HTTP traffic; safe repair recommends usesCleartextTraffic=\"false\".",
                        canSafeRepair = true
                    )
                )
            }
        }

        val requiredTools = listOf("kotlinc", "aapt2", "d8", "zipalign", "apksigner", "android.jar")
        val missingTools = requiredTools.filter { reqId ->
            toolchainItems.none { it.id == reqId && it.isAvailable }
        }
        val locallyCompilable = missingTools.isEmpty() && issues.none { it.severity == "BLOCKING" }

        val rec = when {
            locallyCompilable -> "Native sources and imported CLI tools pass pre-checks. Ready for staged packaging & signing verification."
            gradleFiles.isNotEmpty() -> "Gradle Android projects should be exported as a verified source bundle or built via GitHub Actions CI (.github/workflows). Local mode produces a verified source snapshot without fake APK output."
            else -> "Import missing native binaries in Toolchain & SDK (${missingTools.joinToString(", ")}) or dispatch via GitHub Actions CI."
        }

        return NativeCompatibilityReport(
            projectName = project.name,
            isLocallyCompilable = locallyCompilable,
            hasGradleFiles = gradleFiles.isNotEmpty(),
            detectedPlugins = plugins.toList(),
            detectedDependencies = deps.toList(),
            missingToolchainItems = missingTools,
            issues = issues,
            canSafeRepairManifest = canSafeRepair,
            recommendation = rec
        )
    }

    /**
     * Applies user-confirmed Safe Manifest Repair ONLY after creating a timestamped backup snapshot
     * of the original AndroidManifest.xml so imported source can be rolled back anytime.
     */
    fun applyConfirmedSafeManifestRepair(projectDir: File): Result<Pair<File, Int>> = runCatching {
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File(projectDir, "AndroidManifest.xml").takeIf { it.exists() }
            ?: throw IllegalStateException("No AndroidManifest.xml found to repair.")

        val backupDir = File(projectDir, ".tkstudio_repair_backups").apply { mkdirs() }
        val backupFile = File(backupDir, "AndroidManifest_${System.currentTimeMillis()}.xml.bak")
        manifest.copyTo(backupFile, overwrite = true)

        var text = manifest.readText()
        var fixes = 0
        if (text.contains("<activity") && !text.contains("android:exported")) {
            text = text.replaceFirst("<activity", "<activity\n            android:exported=\"true\"")
            fixes++
        }
        if (text.contains("""android:usesCleartextTraffic="true"""")) {
            text = text.replace("""android:usesCleartextTraffic="true"""", """android:usesCleartextTraffic="false"""")
            fixes++
        }
        manifest.writeText(text)
        backupFile to fixes
    }
}
