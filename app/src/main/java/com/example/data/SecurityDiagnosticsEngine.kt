package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.AppSettingsState
import com.example.model.FindingSeverity
import com.example.model.ProjectMetadata
import com.example.model.SecurityFinding
import com.example.model.StudioEventLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Pure-Kotlin secret & workflow scanner core so rules are deterministic and unit-testable.
 */
object SecretScannerCore {
    val secretPatterns = listOf(
        "Google API Key" to Regex("AIza[0-9A-Za-z\\-_]{20,}"),
        "GitHub Token" to Regex("gh[pousr]_[0-9A-Za-z]{20,}"),
        "Private Key Header" to Regex("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        "Bearer / Secret Assignment" to Regex("""(?i)(api_key|secret|password|token)\s*[:=]\s*["'][^"']{8,}["']""")
    )

    fun scanFileLines(projectName: String, filePath: String, fileName: String, lines: List<String>): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()
        lines.forEachIndexed { idx, line ->
            secretPatterns.forEach { (label, regex) ->
                val match = regex.find(line)
                if (match != null) {
                    val raw = match.value
                    val masked = if (raw.length > 10) {
                        raw.take(4) + "••••••••" + raw.takeLast(4)
                    } else "••••••••"
                    findings.add(
                        SecurityFinding(
                            id = UUID.randomUUID().toString().take(8),
                            severity = FindingSeverity.HIGH,
                            category = "Secret Scan (Verify for False Positives)",
                            title = "$label detected in $projectName",
                            description = "Pattern match found; inspect before committing. Use GitHub Secrets or encrypted vault instead of hardcoded values.",
                            filePath = filePath,
                            lineNumber = idx + 1,
                            redactedPreview = line.replace(raw, masked).trim().take(120),
                            canRepair = true,
                            repairOriginalLine = line,
                            repairReplacementLine = line.replace(raw, "BuildConfig.REDACTED_SECRET")
                        )
                    )
                }
            }
            if (line.contains("http://") && !line.contains("localhost") && !line.contains("127.0.0.1") && !line.contains("schemas.android.com")) {
                findings.add(
                    SecurityFinding(
                        id = UUID.randomUUID().toString().take(8),
                        severity = FindingSeverity.MEDIUM,
                        category = "Transport Security",
                        title = "Cleartext http:// reference in $fileName",
                        description = "Android blocks cleartext HTTP traffic by default; upgrade external URLs to https://.",
                        filePath = filePath,
                        lineNumber = idx + 1,
                        redactedPreview = line.trim().take(120),
                        canRepair = true,
                        repairOriginalLine = line,
                        repairReplacementLine = line.replace("http://", "https://")
                    )
                )
            }
        }
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            val audit = WorkflowBuilderEngine.auditWorkflow(lines.joinToString("\n"))
            if (audit.hasPullRequestSecretRisk) {
                findings.add(
                    SecurityFinding(
                        id = UUID.randomUUID().toString().take(8),
                        severity = FindingSeverity.HIGH,
                        category = "CI/CD Workflow Security",
                        title = "Secrets referenced in pull_request workflow ($fileName)",
                        description = "Do not pass signing or release secrets on pull_request triggers; restrict secrets to tag/release or protected dispatch.",
                        filePath = filePath,
                        lineNumber = 1
                    )
                )
            }
            if (!audit.permissionsChecked) {
                findings.add(
                    SecurityFinding(
                        id = UUID.randomUUID().toString().take(8),
                        severity = FindingSeverity.LOW,
                        category = "CI/CD Workflow Security",
                        title = "Missing explicit 'permissions:' block in $fileName",
                        description = "Declare least-privilege 'permissions: contents: read' in workflow YAML."
                    )
                )
            }
        }
        return findings
    }
}

class SecurityDiagnosticsEngine(private val context: Context) {

    suspend fun runAudit(
        projects: List<ProjectMetadata>,
        settings: AppSettingsState
    ): List<SecurityFinding> = withContext(Dispatchers.IO) {
        val findings = mutableListOf<SecurityFinding>()

        // 1. Token storage check
        findings.add(
            if (settings.hasGitHubToken) {
                SecurityFinding(
                    id = "gh_vault",
                    severity = FindingSeverity.PASS,
                    category = "Encrypted Credential Vault",
                    title = "GitHub Token Stored in AndroidKeyStore AES-GCM",
                    description = "OAuth/PAT tokens are encrypted with hardware-backed AES-256-GCM and redacted from all logs and exports."
                )
            } else {
                SecurityFinding(
                    id = "gh_unauth",
                    severity = FindingSeverity.LOW,
                    category = "Encrypted Credential Vault",
                    title = "No GitHub Token Persisted",
                    description = "Connect GitHub via OAuth Device Flow or scoped PAT when ready for remote CI/CD."
                )
            }
        )

        // 2. App Lock PIN check
        findings.add(
            if (settings.pinLockEnabled) {
                SecurityFinding(
                    id = "app_lock_on",
                    severity = FindingSeverity.PASS,
                    category = "App Lock Policy",
                    title = "App Lock PIN Enabled (Auto-lock ${settings.autoLockSeconds}s)",
                    description = "Salted PBKDF2-HMAC-SHA256 PIN check with 5-attempt temporary lockout protects local workspace access."
                )
            } else {
                SecurityFinding(
                    id = "app_lock_off",
                    severity = FindingSeverity.LOW,
                    category = "App Lock Policy",
                    title = "App Lock PIN Disabled",
                    description = "Enable a 4–12 digit PIN in Security & Privacy Center to lock sensitive signing and Git views."
                )
            }
        )

        // 3. WebView Safe Mode Check
        findings.add(
            if (settings.webViewSafeMode) {
                SecurityFinding(
                    id = "wv_safe",
                    severity = FindingSeverity.PASS,
                    category = "WebView Safety",
                    title = "Restricted Local File Access Enforced",
                    description = "Universal file URL access and cross-file script access are disabled in Live Coding WebView."
                )
            } else {
                SecurityFinding(
                    id = "wv_unsafe",
                    severity = FindingSeverity.MEDIUM,
                    category = "WebView Safety",
                    title = "WebView Safe Mode Disabled",
                    description = "Enable WebView Safe Mode in Settings to restrict cross-file URL access."
                )
            }
        )

        // 4. ZIP Slip Guard Check
        findings.add(
            if (settings.zipSlipGuardEnabled) {
                SecurityFinding(
                    id = "zip_guard",
                    severity = FindingSeverity.PASS,
                    category = "ZIP Safety",
                    title = "ZIP Path Traversal Guard Active",
                    description = "All imported archives, cloned repos, and extracted APKs verify canonical path containment before disk writes."
                )
            } else {
                SecurityFinding(
                    id = "zip_guard_off",
                    severity = FindingSeverity.HIGH,
                    category = "ZIP Safety",
                    title = "ZIP Path Traversal Guard Disabled",
                    description = "Importing untrusted archives without canonical path validation risks overwriting files."
                )
            }
        )

        // 5. Real Secret, Workflow YAML, .env & Unsafe HTTP Scan across workspace files
        projects.forEach { proj ->
            val dir = File(proj.directoryPath)
            if (!dir.exists()) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && !it.absolutePath.contains(".tkstudio_git") && it.length() < 512_000 }
                .forEach { file ->
                    val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
                    findings.addAll(SecretScannerCore.scanFileLines(proj.name, file.absolutePath, file.name, lines))
                }
        }
        findings
    }

    suspend fun applyRepair(finding: SecurityFinding): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val path = finding.filePath ?: return@runCatching false
            val lineNum = finding.lineNumber ?: return@runCatching false
            val orig = finding.repairOriginalLine ?: return@runCatching false
            val replacement = finding.repairReplacementLine ?: return@runCatching false
            val file = File(path)
            if (!file.exists()) return@runCatching false
            val lines = file.readLines().toMutableList()
            val idx = lineNum - 1
            if (idx !in lines.indices || lines[idx] != orig) return@runCatching false
            lines[idx] = replacement
            file.writeText(lines.joinToString("\n"))
            true
        }
    }

    suspend fun exportPrivacyMetadataSummary(
        projects: List<ProjectMetadata>,
        settings: AppSettingsState
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportsDir = File(context.filesDir, "tkstudio/exports").apply { mkdirs() }
            val out = File(exportsDir, "tkstudio_privacy_metadata_${System.currentTimeMillis()}.txt")
            val report = buildString {
                appendLine("=== TK Studio Workspace Metadata & Privacy Export ===")
                appendLine("App: com.tkofficial.tkstudio v0.6.8")
                appendLine("Developer: TK OFFICIAL")
                appendLine("Projects Count: ${projects.size}")
                appendLine("ZIP-Slip Guard: ${settings.zipSlipGuardEnabled}")
                appendLine("WebView Safe Mode: ${settings.webViewSafeMode}")
                appendLine("App Lock PIN: ${settings.pinLockEnabled} (Auto-lock ${settings.autoLockSeconds}s)")
                appendLine("Encrypted GitHub Token Present: ${settings.hasGitHubToken} (Value Redacted)")
                appendLine("Declared Permissions: ${getDeclaredPermissions().joinToString(", ")}")
                appendLine()
                appendLine("--- Projects Index (No source secrets included) ---")
                projects.forEach { p ->
                    appendLine("- ${p.name} [${p.type.label}] files=${p.fileCount} size=${p.sizeBytes}B status=${p.lastBuildStatus}")
                }
            }
            out.writeText(StudioEventLogger.redact(report))
            out
        }
    }

    fun getThreatModelChecklist(): List<Pair<String, String>> = listOf(
        "Credential Storage" to "GitHub OAuth/PAT encrypted via AndroidKeyStore AES-256-GCM; never written to DataStore, logs, or workflow files.",
        "Archive & Clone Safety" to "Zip-Slip canonical path confinement + 3,000-entry & 100 MB extraction caps prevent path overwrite & zip bombs.",
        "CI/CD Secrets Hygiene" to "Signing passwords belong exclusively in GitHub Repository Secrets, never inside pull_request triggers or source files.",
        "WebView Isolation" to "Live preview blocks universal/cross-file URL access and unsafe schemes; offline bundles cannot leak app private storage.",
        "Log & Report Redaction" to "Bearer tokens, GitHub PATs, Google API keys, PEM headers, and password assignments are redacted before logging or export."
    )

    fun getDeclaredPermissions(): List<String> {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
            info.requestedPermissions?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    fun redactLogLine(line: String): String = StudioEventLogger.redact(line)
}
