package com.example.data

import com.example.model.CiReadinessItem
import com.example.model.ProjectMetadata
import com.example.model.ProjectType
import com.example.model.WorkflowAuditIssue
import com.example.model.WorkflowDiffPreview
import com.example.model.WorkflowSecurityAudit
import java.io.File

object WorkflowBuilderEngine {

    enum class WorkflowTemplate(val label: String, val fileName: String) {
        ANDROID_GRADLE("Android Gradle CI", "android-ci.yml"),
        KOTLIN_TEST_CI("Unit Tests & Lint CI", "kotlin-test-ci.yml"),
        WEB_TO_APK_BUNDLE("Web-to-APK Bundle CI", "web-bundle-ci.yml"),
        SIGNED_RELEASE("Signed Release Tag CI", "release-sign.yml")
    }

    /**
     * Backward-compatible generator defaulting push triggers to OFF (opt-in only).
     */
    fun generateYaml(template: WorkflowTemplate, branch: String = "main"): String =
        buildWorkflowYaml(
            template = template,
            branch = branch,
            enablePushTrigger = false,
            enablePullRequest = template == WorkflowTemplate.ANDROID_GRADLE || template == WorkflowTemplate.KOTLIN_TEST_CI,
            enableTagRelease = template == WorkflowTemplate.SIGNED_RELEASE,
            includeTests = true,
            includeSigningSecretsOnRelease = template == WorkflowTemplate.SIGNED_RELEASE,
            javaVersion = "17"
        )

    fun buildWorkflowYaml(
        template: WorkflowTemplate,
        branch: String = "main",
        enablePushTrigger: Boolean = false,
        enablePullRequest: Boolean = true,
        enableTagRelease: Boolean = false,
        includeTests: Boolean = true,
        includeSigningSecretsOnRelease: Boolean = false,
        javaVersion: String = "17"
    ): String {
        val cleanBranch = branch.trim().ifBlank { "main" }
        val cleanJava = javaVersion.trim().ifBlank { "17" }
        val triggerBlock = buildString {
            appendLine("on:")
            appendLine("  workflow_dispatch:")
            if (enablePushTrigger) {
                appendLine("  push:")
                appendLine("    branches: [ \"$cleanBranch\" ]")
            }
            if (enableTagRelease) {
                appendLine("  push:")
                appendLine("    tags: [ \"v*\" ]")
            }
            if (enablePullRequest) {
                appendLine("  pull_request:")
                appendLine("    branches: [ \"$cleanBranch\" ]")
            }
        }.trimEnd()

        return when (template) {
            WorkflowTemplate.ANDROID_GRADLE -> buildString {
                appendLine("name: Android Gradle CI")
                appendLine()
                appendLine(triggerBlock)
                appendLine()
                appendLine("permissions:")
                appendLine("  contents: read")
                appendLine()
                appendLine("jobs:")
                appendLine("  build:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: actions/checkout@v4")
                appendLine("      - uses: actions/setup-java@v4")
                appendLine("        with:")
                appendLine("          distribution: temurin")
                appendLine("          java-version: $cleanJava")
                appendLine("      - uses: gradle/actions/setup-gradle@v4")
                if (includeTests) {
                    appendLine("      - name: Run JVM Unit Tests")
                    appendLine("        run: ./gradlew testDebugUnitTest")
                }
                appendLine("      - name: Assemble Debug APK")
                appendLine("        run: ./gradlew assembleDebug")
                appendLine("      - name: Upload APK Artifact")
                appendLine("        uses: actions/upload-artifact@v4")
                appendLine("        with:")
                appendLine("          name: app-debug-apk")
                appendLine("          path: app/build/outputs/apk/debug/*.apk")
            }.trimEnd()

            WorkflowTemplate.KOTLIN_TEST_CI -> buildString {
                appendLine("name: Kotlin Unit Test & Lint CI")
                appendLine()
                appendLine(triggerBlock)
                appendLine()
                appendLine("permissions:")
                appendLine("  contents: read")
                appendLine()
                appendLine("jobs:")
                appendLine("  verify:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: actions/checkout@v4")
                appendLine("      - uses: actions/setup-java@v4")
                appendLine("        with:")
                appendLine("          distribution: temurin")
                appendLine("          java-version: $cleanJava")
                appendLine("      - uses: gradle/actions/setup-gradle@v4")
                appendLine("      - name: Run Unit Tests")
                appendLine("        run: ./gradlew testDebugUnitTest")
                appendLine("      - name: Upload Test Reports")
                appendLine("        if: always()")
                appendLine("        uses: actions/upload-artifact@v4")
                appendLine("        with:")
                appendLine("          name: unit-test-reports")
                appendLine("          path: app/build/reports/tests/")
            }.trimEnd()

            WorkflowTemplate.WEB_TO_APK_BUNDLE -> buildString {
                appendLine("name: Web-to-APK Bundle CI")
                appendLine()
                appendLine(triggerBlock)
                appendLine()
                appendLine("permissions:")
                appendLine("  contents: read")
                appendLine()
                appendLine("jobs:")
                appendLine("  bundle:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: actions/checkout@v4")
                appendLine("      - name: Validate HTML & Manifest Config")
                appendLine("        run: test -f index.html -o -f www/index.html")
                appendLine("      - name: Upload Web Bundle")
                appendLine("        uses: actions/upload-artifact@v4")
                appendLine("        with:")
                appendLine("          name: tk-web-bundle")
                appendLine("          path: .")
            }.trimEnd()

            WorkflowTemplate.SIGNED_RELEASE -> buildString {
                appendLine("name: Signed Release CI")
                appendLine()
                appendLine(if (enableTagRelease || enablePushTrigger) triggerBlock else "on:\n  workflow_dispatch:")
                appendLine()
                appendLine("permissions:")
                appendLine("  contents: write")
                appendLine()
                appendLine("jobs:")
                appendLine("  release:")
                appendLine("    runs-on: ubuntu-latest")
                appendLine("    steps:")
                appendLine("      - uses: actions/checkout@v4")
                appendLine("      - uses: actions/setup-java@v4")
                appendLine("        with:")
                appendLine("          distribution: temurin")
                appendLine("          java-version: $cleanJava")
                appendLine("      - uses: gradle/actions/setup-gradle@v4")
                if (includeTests) {
                    appendLine("      - name: Verify Tests Before Signing")
                    appendLine("        run: ./gradlew testDebugUnitTest")
                }
                appendLine("      - name: Build Signed Release APK")
                if (includeSigningSecretsOnRelease) {
                    appendLine("        env:")
                    appendLine("          SIGNING_KEYSTORE_B64: \${{ secrets.SIGNING_KEYSTORE_B64 }}")
                    appendLine("          SIGNING_STORE_PASSWORD: \${{ secrets.SIGNING_STORE_PASSWORD }}")
                    appendLine("          SIGNING_KEY_ALIAS: \${{ secrets.SIGNING_KEY_ALIAS }}")
                    appendLine("          SIGNING_KEY_PASSWORD: \${{ secrets.SIGNING_KEY_PASSWORD }}")
                }
                appendLine("        run: ./gradlew assembleRelease")
                appendLine("      - name: Upload Release APK")
                appendLine("        uses: actions/upload-artifact@v4")
                appendLine("        with:")
                appendLine("          name: app-release-apk")
                appendLine("          path: app/build/outputs/apk/release/*.apk")
            }.trimEnd()
        }
    }

    /**
     * Basic structural & security linter (explicitly not a full GitHub Actions schema validator).
     */
    fun auditWorkflow(yaml: String): WorkflowSecurityAudit {
        val issues = mutableListOf<WorkflowAuditIssue>()
        val lines = yaml.lines()
        if (!yaml.contains(Regex("""(?m)^name:\s*\S+"""))) {
            issues.add(WorkflowAuditIssue("ERROR", "Missing top-level 'name:' key"))
        }
        if (!yaml.contains(Regex("""(?m)^on:"""))) {
            issues.add(WorkflowAuditIssue("ERROR", "Missing trigger 'on:' block"))
        }
        if (!yaml.contains(Regex("""(?m)^jobs:"""))) {
            issues.add(WorkflowAuditIssue("ERROR", "Missing 'jobs:' block"))
        }
        if (lines.any { it.startsWith("\t") }) {
            issues.add(WorkflowAuditIssue("ERROR", "Tab indentation detected; YAML requires spaces"))
        }
        val hasPermissions = yaml.contains(Regex("""(?m)^permissions:"""))
        if (!hasPermissions) {
            issues.add(WorkflowAuditIssue("WARNING", "Add least-privilege top-level 'permissions: contents: read'"))
        }
        val unpinnedUses = lines.filter { line ->
            val t = line.trim()
            (t.startsWith("uses:") || t.startsWith("- uses:")) && !t.contains("@")
        }
        if (unpinnedUses.isNotEmpty()) {
            issues.add(WorkflowAuditIssue("WARNING", "Pin action versions (e.g. @v4 or commit SHA): ${unpinnedUses.first().trim()}"))
        }
        val hasPush = yaml.contains(Regex("""(?m)^\s+push:"""))
        val hasPr = yaml.contains(Regex("""(?m)^\s+pull_request"""))
        val usesSecrets = yaml.contains(Regex("""\$\{\{\s*secrets\.[A-Za-z0-9_]+\s*\}\}"""))
        val prSecretRisk = hasPr && usesSecrets
        if (prSecretRisk) {
            issues.add(
                WorkflowAuditIssue(
                    "ERROR",
                    "Security block: Do not expose signing/deploy secrets in pull_request workflows; restrict secrets to tag/release or protected dispatch."
                )
            )
        }
        val rawTokenLeak = Regex("""gh[pousr]_[0-9A-Za-z]{20,}|AIza[0-9A-Za-z\-_]{20,}""").containsMatchIn(yaml)
        if (rawTokenLeak) {
            issues.add(WorkflowAuditIssue("ERROR", "Hardcoded token literal detected inside YAML; use \${{ secrets.NAME }}."))
        }

        // Known top-level key informational notice (preserves unknown keys cleanly)
        val knownTopKeys = setOf("name", "on", "permissions", "env", "defaults", "concurrency", "jobs", "run-name")
        lines.forEach { l ->
            if (l.isNotBlank() && !l.startsWith(" ") && !l.startsWith("#") && l.contains(':')) {
                val key = l.substringBefore(':').trim()
                if (key !in knownTopKeys) {
                    issues.add(WorkflowAuditIssue("INFO", "Unrecognized top-level key '$key' preserved in editor"))
                }
            }
        }

        val hasErrors = issues.any { it.severity == "ERROR" }
        return WorkflowSecurityAudit(
            isValid = !hasErrors,
            issues = issues,
            permissionsChecked = hasPermissions,
            pinnedActionsChecked = unpinnedUses.isEmpty(),
            hasOptInPushTrigger = hasPush,
            hasPullRequestSecretRisk = prSecretRisk
        )
    }

    fun validateYaml(yaml: String): List<String> =
        auditWorkflow(yaml).issues
            .filter { it.severity == "ERROR" || it.severity == "WARNING" }
            .map { "${it.severity}: ${it.message}" }

    fun previewWorkflowDiff(projectDir: File, fileName: String, proposedYaml: String): WorkflowDiffPreview {
        val cleanName = sanitizeWorkflowFileName(fileName)
        val target = File(projectDir, ".github/workflows/$cleanName")
        val existingText = if (target.exists()) target.readText() else ""
        val diffText = if (!target.exists()) {
            proposedYaml.lines().joinToString("\n") { "+ $it" }
        } else {
            UtilityToolsEngine.diffTexts(existingText, proposedYaml)
        }
        return WorkflowDiffPreview(
            fileName = cleanName,
            existedBefore = target.exists(),
            backupFileName = "$cleanName.bak",
            diffText = diffText,
            proposedYaml = proposedYaml
        )
    }

    fun writeWorkflowSafely(projectDir: File, fileName: String, yaml: String): Result<File> = runCatching {
        val cleanName = sanitizeWorkflowFileName(fileName)
        val wfDir = File(projectDir, ".github/workflows").apply { mkdirs() }
        val target = File(wfDir, cleanName)
        if (!target.canonicalPath.startsWith(wfDir.canonicalPath + File.separator)) {
            throw SecurityException("Invalid workflow filename: $fileName")
        }
        if (target.exists()) {
            File(wfDir, "$cleanName.bak").writeText(target.readText())
        }
        target.writeText(yaml)
        target
    }

    fun evaluateCiReadiness(
        project: ProjectMetadata?,
        hasGitHubAuth: Boolean,
        hasRepoSelected: Boolean,
        yaml: String
    ): List<CiReadinessItem> {
        val audit = auditWorkflow(yaml)
        val hasBuildEntry = if (project == null) false else {
            val dir = File(project.directoryPath)
            when (project.type) {
                ProjectType.ANDROID_KOTLIN -> File(dir, "build.gradle.kts").exists() || File(dir, "app/build.gradle.kts").exists() || File(dir, "build.gradle").exists()
                ProjectType.WEB_APP, ProjectType.WEB_TO_APK -> File(dir, "index.html").exists() || File(dir, "www/index.html").exists()
                ProjectType.BLANK -> dir.listFiles()?.isNotEmpty() == true
            }
        }
        return listOf(
            CiReadinessItem("GitHub Account Authorized", hasGitHubAuth, if (hasGitHubAuth) "Encrypted token active" else "Authorize via OAuth Device Flow or PAT"),
            CiReadinessItem("Remote Repository Selected", hasRepoSelected, if (hasRepoSelected) "Target owner/repo configured" else "Choose or create a repository"),
            CiReadinessItem("Workflow Syntax & Security Audit", audit.isValid && !audit.hasPullRequestSecretRisk, if (audit.isValid) "Basic structural & secret checks passed" else "Fix workflow errors before committing"),
            CiReadinessItem("Project Entry / Build Files", hasBuildEntry, if (hasBuildEntry) "Workspace files detected" else "Note: Remote Gradle CI requires gradlew + build.gradle(.kts) in repository")
        )
    }

    private fun sanitizeWorkflowFileName(name: String): String {
        val base = name.trim().substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .ifBlank { "tk-ci.yml" }
        return if (base.endsWith(".yml") || base.endsWith(".yaml")) base else "$base.yml"
    }
}
