package com.example

import androidx.compose.ui.graphics.toArgb
import com.example.data.ApkToolRepository
import com.example.data.AssetResourceAuditCore
import com.example.data.BuildVerificationUtils
import com.example.data.GitHubApiDiagnostics
import com.example.data.KeyVaultCrypto
import com.example.data.LocalGitEngine
import com.example.data.LocalizationEngine
import com.example.data.MyApplicationsEngine
import com.example.data.NativeCompatibilityAnalyzer
import com.example.data.PinVaultCrypto
import com.example.data.SecretScannerCore
import com.example.data.SettingsDataStore
import com.example.data.StudioLanguage
import com.example.data.TutorialsRepository
import com.example.data.UtilityToolsEngine
import com.example.data.WebToApkWrapperEngine
import com.example.data.WorkflowBuilderEngine
import com.example.model.AppSettingsState
import com.example.model.BuildConfigSpec
import com.example.model.BuildMode
import com.example.model.BuildProfile
import com.example.model.BuildRecord
import com.example.model.BuildReportFormat
import com.example.model.BuildStage
import com.example.model.BuildStepRecord
import com.example.model.ProjectMetadata
import com.example.model.ProjectType
import com.example.model.SigningContinuityState
import com.example.model.SigningKeyInfo
import com.example.model.StudioError
import com.example.model.StudioEventLogger
import com.example.model.StudioTaskState
import com.example.model.ToolchainItem
import com.example.ui.theme.StudioThemeId
import com.example.ui.theme.studioPaletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.UnknownHostException
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class ExampleUnitTest {
    @Test
    fun allSevenThemesHaveDistinctDarkAndLightPalettes() {
        assertEquals(7, StudioThemeId.entries.size)
        StudioThemeId.entries.forEach { themeId ->
            val dark = studioPaletteFor(themeId, isDark = true)
            val light = studioPaletteFor(themeId, isDark = false)
            assertTrue(dark.isDark)
            assertTrue(!light.isDark)
            assertNotEquals(dark.background, light.background)
            assertNotEquals(dark.text, dark.background)
            assertNotEquals(light.text, light.background)
        }
    }

    @Test
    fun cyberBlueDefaultTokensMatchSpecification() {
        val dark = studioPaletteFor(StudioThemeId.CYBER_BLUE, isDark = true)
        assertEquals(0xFF080A12.toInt(), dark.background.toArgb())
        assertEquals(0xFF4169FF.toInt(), dark.primary.toArgb())
        assertEquals(0xFFF4F7FF.toInt(), dark.text.toArgb())

        val light = studioPaletteFor(StudioThemeId.CYBER_BLUE, isDark = false)
        assertEquals(0xFFF4F6FC.toInt(), light.background.toArgb())
        assertEquals(0xFF345BFA.toInt(), light.primary.toArgb())
        assertEquals(0xFF182238.toInt(), light.text.toArgb())
    }

    @Test
    fun tutorialsLibraryContainsAll266OrderedLessons() {
        val lessons = TutorialsRepository.lessons
        assertEquals(266, lessons.size)
        assertEquals("tut_001", lessons.first().id)
        assertEquals("tut_266", lessons.last().id)
        assertTrue(lessons.all { it.steps.size == 3 && it.title.isNotBlank() && it.summary.isNotBlank() })
    }

    @Test
    fun localizationSupportsFiveLanguagesWithExactEnglishNavAndRtlFlags() {
        assertEquals(5, StudioLanguage.entries.size)
        assertFalse(StudioLanguage.fromCode("en").isRtl)
        assertFalse(StudioLanguage.fromCode("bn").isRtl)
        assertTrue(StudioLanguage.fromCode("ar").isRtl)
        assertFalse(StudioLanguage.fromCode("zh").isRtl)
        assertTrue(StudioLanguage.fromCode("ur").isRtl)

        val en = LocalizationEngine.stringsFor("en")
        assertEquals("Home", en.navHome)
        assertEquals("Project", en.navProject)
        assertEquals("Live Coding", en.navLiveCoding)
        assertEquals("APK Tool", en.navApkTool)
        assertEquals("Profile", en.navProfile)

        StudioLanguage.entries.forEach { lang ->
            val s = LocalizationEngine.stringsFor(lang.code)
            assertTrue(s.navHome.isNotBlank())
            assertTrue(s.settings.isNotBlank())
        }
    }

    @Test
    fun workflowBuilderGeneratesValidYamlAndFlagsIssues() {
        WorkflowBuilderEngine.WorkflowTemplate.entries.forEach { template ->
            val yaml = WorkflowBuilderEngine.generateYaml(template, "main")
            val issues = WorkflowBuilderEngine.validateYaml(yaml)
            assertTrue("Expected no issues for ${template.name}, got $issues", issues.isEmpty())
        }
        val badIssues = WorkflowBuilderEngine.validateYaml("\tinvalid: true")
        assertTrue(badIssues.size >= 3)
    }

    @Test
    fun utilityToolsEngineComputesColorHashesRegexAndDiff() {
        val colorOut = UtilityToolsEngine.convertHexColor("#4169FF").getOrThrow()
        assertTrue(colorOut.contains("rgb(65, 105, 255)"))

        val hashes = UtilityToolsEngine.computeHashes("TK Studio")
        assertTrue(hashes.contains("SHA-256:"))
        assertTrue(hashes.contains("SHA-1:"))
        assertTrue(hashes.contains("MD5:"))

        val regexOut = UtilityToolsEngine.testRegex("\\d+", "v0.6.8").getOrThrow()
        assertTrue(regexOut.contains("3 match(es)"))

        val diff = UtilityToolsEngine.diffTexts("a\nb", "a\nc")
        assertTrue(diff.contains("- b"))
        assertTrue(diff.contains("+ c"))
    }

    @Test
    fun eventLoggerRedactsSecretsAndTaskStateWorks() {
        val redacted = StudioEventLogger.redact("Authorization: Bearer abc123secret== ghp_1234567890abcdef password=mysecret")
        assertFalse(redacted.contains("abc123secret"))
        assertFalse(redacted.contains("ghp_1234567890abcdef"))
        assertFalse(redacted.contains("mysecret"))
        assertTrue(redacted.contains("[REDACTED]"))

        StudioEventLogger.log("INFO", "UnitTest", "token=supersecret123")
        val latest = StudioEventLogger.snapshot().first()
        assertFalse(latest.message.contains("supersecret123"))

        val prog: StudioTaskState<String> = StudioTaskState.Progress("Scanning", "Working", null)
        assertTrue(prog is StudioTaskState.Progress)
        val fail: StudioTaskState<String> = StudioTaskState.Failure(StudioError("IO", "Failed"))
        assertTrue(fail is StudioTaskState.Failure)

        val defaults = AppSettingsState()
        assertEquals(StudioThemeId.CYBER_BLUE, defaults.themeId)
        assertEquals(1.0f, defaults.glowIntensity, 0.001f)
        assertEquals(0.88f, defaults.glassOpacity, 0.001f)
    }

    // --- PART 3 TESTS ---

    @Test
    fun buildProfilesMapToDistinctWorkerAndCacheBehaviors() {
        assertEquals(4, BuildProfile.FAST.workers)
        assertEquals(1, BuildProfile.LOW_RAM.workers)
        assertTrue(BuildProfile.LOW_RAM.lowMemoryMode)
        assertTrue(BuildProfile.CLEAN.cleanCacheFirst)
        assertFalse(BuildProfile.CLEAN.useIncrementalCache)
        assertTrue(BuildProfile.RELEASE.strictValidation)
        assertFalse(BuildProfile.RELEASE.debuggable)
    }

    @Test
    fun webToApkWrapperEnforcesSecurityDefaultsAndDetectsMissingAssets() {
        val dir = createTempDirectory("tk_web_test").toFile()
        try {
            File(dir, "index.html").writeText(
                """
                <!DOCTYPE html>
                <html>
                <head><link rel="stylesheet" href="styles.css"></head>
                <body>
                    <script src="app.js"></script>
                    <script src="missing_vendor.js"></script>
                    <script>fetch("https://api.example.com/data");</script>
                </body>
                </html>
                """.trimIndent()
            )
            File(dir, "styles.css").writeText("body { color: #fff; }")
            File(dir, "app.js").writeText("console.log('ready');")
            File(dir, "vite.config.ts").writeText("export default {}")

            val meta = ProjectMetadata(
                id = "p_web",
                name = "TestWeb",
                directoryPath = dir.absolutePath,
                type = ProjectType.WEB_TO_APK,
                createdAt = 1L,
                updatedAt = 1L,
                sizeBytes = 100L,
                fileCount = 4,
                entryFile = "index.html",
                packageId = "com.tkofficial.testweb"
            )
            val cfg = BuildConfigSpec(
                appName = "TestWeb",
                packageId = "com.tkofficial.testweb",
                allowlistDomains = "api.example.com"
            )
            val preview = WebToApkWrapperEngine.inspectAndGeneratePreview(dir, meta, cfg)

            assertTrue(preview.assetRefs.contains("styles.css"))
            assertTrue(preview.missingAssets.contains("missing_vendor.js"))
            assertFalse(preview.missingAssets.contains("app.js"))
            assertTrue(preview.suggestedPermissions.contains("android.permission.INTERNET"))
            assertTrue(preview.generatedKotlin.contains("MIXED_CONTENT_NEVER_ALLOW"))
            assertTrue(preview.generatedKotlin.contains("handler.cancel()"))
            assertTrue(preview.generatedKotlin.contains("allowUniversalAccessFromFileURLs = false"))
            assertTrue(preview.generatedKotlin.contains("api.example.com"))
            assertTrue(preview.generatedManifest.contains("""android:usesCleartextTraffic="false""""))
            assertTrue(preview.frameworkLimitations.any { it.contains("Vite", ignoreCase = true) })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun nativeCompatibilityAnalyzerFlagsUnsupportedGradleAndPreservesSourceUntilConfirmedRepair() {
        val dir = createTempDirectory("tk_native_test").toFile()
        try {
            val appDir = File(dir, "app/src/main").apply { mkdirs() }
            val manifestFile = File(appDir, "AndroidManifest.xml")
            val originalManifest = """
                <manifest package="com.example.nativeapp">
                    <application android:usesCleartextTraffic="true">
                        <activity android:name=".MainActivity" />
                    </application>
                </manifest>
            """.trimIndent()
            manifestFile.writeText(originalManifest)
            File(dir, "app/build.gradle.kts").writeText(
                """
                plugins {
                    id("com.android.application")
                }
                android {
                    externalNativeBuild { cmake { } }
                }
                """.trimIndent()
            )

            val meta = ProjectMetadata(
                id = "p_nat",
                name = "NativeDemo",
                directoryPath = dir.absolutePath,
                type = ProjectType.ANDROID_KOTLIN,
                createdAt = 1L,
                updatedAt = 1L,
                sizeBytes = 200L,
                fileCount = 2
            )
            val missingTools = listOf(
                ToolchainItem("kotlinc", "kotlinc", "Compiler", false, "Missing", "", "—", "arm64-v8a", "")
            )
            val report = NativeCompatibilityAnalyzer.analyze(dir, meta, missingTools)

            assertFalse(report.isLocallyCompilable)
            assertTrue(report.hasGradleFiles)
            assertTrue(report.detectedPlugins.contains("com.android.application"))
            assertTrue(report.issues.any { it.severity == "BLOCKING" && it.line != null })
            assertTrue(report.canSafeRepairManifest)
            // Original manifest must NOT be mutated by analysis alone
            assertEquals(originalManifest, manifestFile.readText())

            // Confirmed safe repair creates .bak snapshot and fixes cleartext + exported
            val (backupFile, fixes) = NativeCompatibilityAnalyzer.applyConfirmedSafeManifestRepair(dir).getOrThrow()
            assertTrue(backupFile.exists())
            assertEquals(originalManifest, backupFile.readText())
            assertEquals(2, fixes)
            val repaired = manifestFile.readText()
            assertTrue(repaired.contains("android:exported=\"true\""))
            assertTrue(repaired.contains("android:usesCleartextTraffic=\"false\""))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun incrementalSha256CacheHitsAndInvalidatesOnFileOrConfigChange() {
        val dir = createTempDirectory("tk_cache_test").toFile()
        try {
            val f1 = File(dir, "index.html").apply { writeText("<html><body>v1</body></html>") }
            val f2 = File(dir, "app.js").apply { writeText("const x = 1;") }

            val cfg1 = BuildConfigSpec(appName = "Demo", packageId = "com.tkofficial.demo", versionCode = 1)
            val cfgHash1 = BuildVerificationUtils.computeConfigFingerprint(cfg1, BuildProfile.FAST, "art:1")

            val firstEval = BuildVerificationUtils.evaluateIncrementalCache(dir, emptyMap(), null, cfgHash1)
            assertEquals(0, firstEval.hits)
            assertEquals(2, firstEval.changedOrAdded)

            // Same files & same config -> 2 hits, 0 changes
            val secondEval = BuildVerificationUtils.evaluateIncrementalCache(dir, firstEval.currentHashes, cfgHash1, cfgHash1)
            assertEquals(2, secondEval.hits)
            assertEquals(0, secondEval.changedOrAdded)
            assertFalse(secondEval.configInvalidated)

            // Modify 1 file -> 1 hit, 1 changed
            f2.writeText("const x = 2;")
            val thirdEval = BuildVerificationUtils.evaluateIncrementalCache(dir, secondEval.currentHashes, cfgHash1, cfgHash1)
            assertEquals(1, thirdEval.hits)
            assertEquals(1, thirdEval.changedOrAdded)

            // Change config -> full invalidation
            val cfg2 = cfg1.copy(versionCode = 2)
            val cfgHash2 = BuildVerificationUtils.computeConfigFingerprint(cfg2, BuildProfile.FAST, "art:1")
            val fourthEval = BuildVerificationUtils.evaluateIncrementalCache(dir, thirdEval.currentHashes, cfgHash1, cfgHash2)
            assertTrue(fourthEval.configInvalidated)
            assertEquals(0, fourthEval.hits)
            assertEquals(2, fourthEval.changedOrAdded)
            assertTrue(f1.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildReportExportsRedactSecretsAcrossTxtJsonHtml() {
        val record = BuildRecord(
            id = "rep001",
            projectId = "proj1",
            projectName = "DemoApp",
            profile = BuildProfile.RELEASE,
            mode = BuildMode.MANUAL,
            stage = BuildStage.FAILED,
            success = false,
            startedAt = 1_700_000_000_000L,
            durationMs = 420L,
            outputPath = null,
            outputSha256 = null,
            logLines = listOf("Unlocking with password=topsecretpass bearer token_abc_123456"),
            artifactKind = "FAILED",
            errorCode = "ERR_KEY_UNLOCK_FAILED",
            failedStage = "Signing",
            sourceLocation = "tk_release",
            steps = listOf(
                BuildStepRecord(1, "Workspace Lock", BuildStage.PREPARING, "PASS", 10L, 5L, "Locked"),
                BuildStepRecord(16, "Signing", BuildStage.SIGNING, "FAIL", 20L, 12L, "Failed token=secret999")
            )
        )
        BuildReportFormat.entries.forEach { fmt ->
            val out = BuildVerificationUtils.formatBuildReport(record, fmt)
            assertFalse("Leak in ${fmt.name}", out.contains("topsecretpass"))
            assertFalse("Leak in ${fmt.name}", out.contains("secret999"))
            assertTrue(out.contains("REDACTED"))
            assertTrue(out.contains("ERR_KEY_UNLOCK_FAILED"))
        }
    }

    @Test
    fun signingVaultCryptoVerifiesCorrectPasswordAndRejectsWrongPassword() {
        val kpg = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        val pair = kpg.generateKeyPair()
        val encrypted = KeyVaultCrypto.encryptPrivateKeyPayload(pair.private, pair.public, "StrongPass#2026")

        // Correct password signs successfully
        val sig = KeyVaultCrypto.signBytesWithEncryptedPayload(
            payload = encrypted,
            algorithm = "EC",
            password = "StrongPass#2026",
            data = "TK Studio Artifact".toByteArray()
        )
        assertTrue(sig.isNotBlank())

        // Wrong password must fail cleanly
        try {
            KeyVaultCrypto.decryptPrivateKeyPayload(encrypted, "WrongPass!")
            fail("Expected decryption to fail on wrong keystore password")
        } catch (_: Exception) {
            // Expected AEADBadTagException / GeneralSecurityException
        }
    }

    // --- PART 4 TESTS ---

    @Test
    fun localGitEngineHandlesInitStageCommitBranchesConflictsAndPathGuard() {
        val dir = createTempDirectory("tk_git_test").toFile()
        try {
            val initStatus = LocalGitEngine.initRepo(dir, "main").getOrThrow()
            assertTrue(initStatus.initialized)
            assertEquals("main", initStatus.currentBranch)

            val mainFile = File(dir, "src/Main.kt").apply {
                parentFile?.mkdirs()
                writeText("fun greet() = \"main v1\"")
            }
            val unstaged = LocalGitEngine.inspectStatus(dir)
            assertEquals(1, unstaged.unstagedFiles.size)
            assertEquals("src/Main.kt", unstaged.unstagedFiles.first().relativePath)

            LocalGitEngine.stageFile(dir, "src/Main.kt").getOrThrow()
            val staged = LocalGitEngine.inspectStatus(dir)
            assertEquals(1, staged.stagedFiles.size)
            assertEquals(0, staged.unstagedFiles.size)

            val c1 = LocalGitEngine.commitStaged(
                projectDir = dir,
                message = "Initial commit",
                authorName = "TK OFFICIAL",
                authorEmail = "tkofficial68@gmail.com"
            ).getOrThrow()
            assertEquals("Initial commit", c1.message)
            assertEquals("TK OFFICIAL <tkofficial68@gmail.com>", c1.author)
            assertEquals("main", c1.branch)

            val cleanAfterCommit = LocalGitEngine.inspectStatus(dir)
            assertTrue(cleanAfterCommit.stagedFiles.isEmpty())
            assertTrue(cleanAfterCommit.unstagedFiles.isEmpty())
            assertEquals(1, LocalGitEngine.loadCommits(dir).size)

            // Branching and dirty-tree guard (slashes sanitize safely for branch snapshot dirs)
            val createdBranch = LocalGitEngine.createBranch(dir, "feature/a", switchToIt = true).getOrThrow()
            assertEquals("feature-a", createdBranch)

            mainFile.writeText("fun greet() = \"feature branch change\"")
            // Switching back with dirty working tree must block unless discardUncommitted = true
            val blockedSwitch = LocalGitEngine.switchBranch(dir, "main", discardUncommitted = false)
            assertTrue(blockedSwitch.isFailure)

            // Commit on feature/a, switch cleanly back to main, modify main -> trigger merge conflict
            LocalGitEngine.commitStaged(dir, "Feature greeting", "TK", "tk@example.com").getOrThrow()
            LocalGitEngine.switchBranch(dir, "main", discardUncommitted = false).getOrThrow()
            assertEquals("fun greet() = \"main v1\"", mainFile.readText())

            mainFile.writeText("fun greet() = \"main conflicting edit\"")
            LocalGitEngine.commitStaged(dir, "Main update", "TK", "tk@example.com").getOrThrow()

            val preview = LocalGitEngine.previewMerge(dir, "feature/a").getOrThrow()
            assertEquals(1, preview.conflictingFiles.size)
            assertEquals("src/Main.kt", preview.conflictingFiles.first().relativePath)

            LocalGitEngine.applyMerge(dir, "feature/a").getOrThrow()
            assertEquals(1, LocalGitEngine.inspectStatus(dir).conflicts.size)

            LocalGitEngine.resolveConflict(dir, "src/Main.kt", keepOurs = false).getOrThrow()
            assertTrue(LocalGitEngine.inspectStatus(dir).conflicts.isEmpty())
            assertEquals("fun greet() = \"feature branch change\"", mainFile.readText())

            // Path traversal guard
            try {
                LocalGitEngine.requireConfinedRelativePath(dir, "../../etc/passwd")
                fail("Expected SecurityException on path traversal")
            } catch (_: SecurityException) {
                // Expected
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun workflowBuilderEnforcesOptInPushSecretsGuardAndBackupDiff() {
        val dir = createTempDirectory("tk_wf_test").toFile()
        try {
            val defaultYaml = WorkflowBuilderEngine.buildWorkflowYaml(
                template = WorkflowBuilderEngine.WorkflowTemplate.ANDROID_GRADLE,
                branch = "main"
            )
            val defaultAudit = WorkflowBuilderEngine.auditWorkflow(defaultYaml)
            assertTrue(defaultAudit.isValid)
            assertTrue(defaultAudit.permissionsChecked)
            assertTrue(defaultAudit.pinnedActionsChecked)
            assertFalse(defaultAudit.hasOptInPushTrigger)
            assertFalse(defaultAudit.hasPullRequestSecretRisk)

            val unsafePrYaml = """
                name: Unsafe PR
                on:
                  pull_request:
                    branches: [ "main" ]
                custom_preserved_key: keep-me
                permissions:
                  contents: read
                jobs:
                  sign:
                    runs-on: ubuntu-latest
                    steps:
                      - env:
                          KEYSTORE: ${'$'}{{ secrets.SIGNING_KEYSTORE_B64 }}
                        run: echo test
            """.trimIndent()
            val badAudit = WorkflowBuilderEngine.auditWorkflow(unsafePrYaml)
            assertFalse(badAudit.isValid)
            assertTrue(badAudit.hasPullRequestSecretRisk)
            assertTrue(badAudit.issues.any { it.severity == "INFO" && it.message.contains("custom_preserved_key") })

            // Preview diff & safe overwrite creates .bak backup
            val firstWrite = WorkflowBuilderEngine.writeWorkflowSafely(dir, "android-ci.yml", defaultYaml).getOrThrow()
            assertTrue(firstWrite.exists())

            val updatedYaml = WorkflowBuilderEngine.buildWorkflowYaml(
                template = WorkflowBuilderEngine.WorkflowTemplate.ANDROID_GRADLE,
                branch = "develop",
                enablePushTrigger = true
            )
            val preview = WorkflowBuilderEngine.previewWorkflowDiff(dir, "android-ci.yml", updatedYaml)
            assertTrue(preview.existedBefore)
            assertEquals("android-ci.yml.bak", preview.backupFileName)
            assertTrue(preview.diffText.contains("develop"))

            WorkflowBuilderEngine.writeWorkflowSafely(dir, "android-ci.yml", updatedYaml).getOrThrow()
            val backupFile = File(dir, ".github/workflows/android-ci.yml.bak")
            assertTrue(backupFile.exists())
            assertEquals(defaultYaml, backupFile.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun pinVaultEnforcesLockoutAfterFiveFailuresAndSecretScannerFlagsTokens() {
        val (salt, hash) = PinVaultCrypto.hashPin("2468")
        val ok = PinVaultCrypto.evaluateAttempt("2468", salt, hash, 0, 0L, 1_000L)
        assertTrue(ok.unlocked)

        var fails = 0
        var lockoutUntil = 0L
        for (attempt in 1..4) {
            val res = PinVaultCrypto.evaluateAttempt("0000", salt, hash, fails, lockoutUntil, 10_000L + attempt)
            assertFalse(res.unlocked)
            assertEquals(attempt, res.failedAttempts)
            assertEquals(0L, res.lockoutUntilMs)
            fails = res.failedAttempts
        }

        // 5th failed attempt locks for 60s
        val fifth = PinVaultCrypto.evaluateAttempt("0000", salt, hash, fails, lockoutUntil, 20_000L)
        assertFalse(fifth.unlocked)
        assertEquals(20_000L + PinVaultCrypto.LOCKOUT_DURATION_MS, fifth.lockoutUntilMs)
        lockoutUntil = fifth.lockoutUntilMs

        // Even correct PIN is blocked while inside lockout window
        val duringLockout = PinVaultCrypto.evaluateAttempt("2468", salt, hash, fifth.failedAttempts, lockoutUntil, 30_000L)
        assertFalse(duringLockout.unlocked)
        assertTrue(duringLockout.remainingLockoutSec > 0)

        // After lockout expiry, correct PIN unlocks cleanly
        val afterLockout = PinVaultCrypto.evaluateAttempt("2468", salt, hash, 0, lockoutUntil, lockoutUntil + 1L)
        assertTrue(afterLockout.unlocked)

        // SecretScannerCore detects PAT and pull_request secret risk with masked preview
        val fakePat = "ghp_" + "1234567890abcdefghij9999"
        val findings = SecretScannerCore.scanFileLines(
            projectName = "DemoRepo",
            filePath = "/tmp/.github/workflows/ci.yml",
            fileName = "ci.yml",
            lines = listOf(
                "on:",
                "  pull_request:",
                "env:",
                "  KEYSTORE: ${'$'}{{ secrets.SIGNING_STORE_PASSWORD }}",
                "  LEAK: \"$fakePat\""
            )
        )
        assertTrue(findings.any { f ->
            val preview = f.redactedPreview.orEmpty()
            f.category.contains("Secret Scan") && !preview.contains(fakePat) && preview.contains("9999")
        })
        assertTrue(findings.any { it.category == "CI/CD Workflow Security" && it.title.contains("pull_request") })

        val redactedLog = StudioEventLogger.redact("Authorization: Bearer $fakePat")
        assertFalse(redactedLog.contains(fakePat))
        assertTrue(redactedLog.contains("[REDACTED]"))
    }

    @Test
    fun gitHubDiagnosticsAndApkSdkCompatibilityReportRealStates() {
        val err401 = GitHubApiDiagnostics.classifyHttpError(401, "Bad credentials")
        assertTrue(err401.contains("401"))

        val err403Rate = GitHubApiDiagnostics.classifyHttpError(403, "API rate limit", rateLimitRemaining = 0, resetEpochSec = 1700000000L)
        assertTrue(err403Rate.contains("rate limit"))

        val err403Perm = GitHubApiDiagnostics.classifyHttpError(403, "Resource not accessible", rateLimitRemaining = 42)
        assertTrue(err403Perm.contains("Permission denied"))

        val err404 = GitHubApiDiagnostics.classifyHttpError(404, "Not Found")
        assertTrue(err404.contains("404"))

        val offline = GitHubApiDiagnostics.classifyThrowable(UnknownHostException("github.com"))
        assertTrue(offline.contains("Offline"))

        val incompatible = ApkToolRepository.assessSdkCompatibility(minSdk = 36, targetSdk = 36, deviceSdk = 33)
        assertFalse(incompatible.isInstallableOnDevice)
        assertTrue(incompatible.statusLabel.contains("36"))

        val compatible = ApkToolRepository.assessSdkCompatibility(minSdk = 24, targetSdk = 36, deviceSdk = 34)
        assertTrue(compatible.isInstallableOnDevice)
        assertTrue(compatible.statusLabel.contains("Compatible"))
    }

    // --- PART 5 TESTS ---

    @Test
    fun tutorialsHaveExact23SeriesAnd266ContiguousIds() {
        val series = TutorialsRepository.seriesRanges
        assertEquals(23, series.size)
        assertEquals(1, series.first().startLesson)
        assertEquals(266, series.last().endLesson)

        var expectedStart = 1
        series.forEachIndexed { idx, s ->
            assertEquals(idx + 1, s.seriesNumber)
            assertEquals(expectedStart, s.startLesson)
            assertTrue(s.endLesson >= s.startLesson)
            expectedStart = s.endLesson + 1
        }
        assertEquals(267, expectedStart)

        val lessons = TutorialsRepository.lessons
        assertEquals(266, lessons.size)
        assertEquals(266, lessons.map { it.id }.distinct().size)
        assertTrue(lessons.all {
            it.seriesKey.isNotBlank() &&
                it.targetRoute.isNotBlank() &&
                it.expectedResult.isNotBlank() &&
                it.commonError.isNotBlank() &&
                it.checkpointQuestion.isNotBlank() &&
                it.steps.size == 3
        })
    }

    @Test
    fun localizationCompletenessCoversAllFiveLanguagesAndRtl() {
        val audit = LocalizationEngine.auditCompleteness()
        assertEquals(5, audit.size)
        audit.values.forEach { report ->
            assertEquals("Missing keys for ${report.language.code}: ${report.missingOrBlankKeys}", 100, report.coveragePercent)
            assertTrue(report.missingOrBlankKeys.isEmpty())
            assertTrue(report.totalKeys >= 59)
        }
        TutorialsRepository.seriesRanges.forEach { sr ->
            StudioLanguage.entries.forEach { lang ->
                val translated = LocalizationEngine.seriesTitle(sr.categoryKey, lang.code)
                assertTrue("Blank seriesTitle for ${sr.categoryKey} in ${lang.code}", translated.isNotBlank())
            }
        }
    }

    @Test
    fun myApplicationsEngineDetectsSigningContinuityStates() {
        val dir = createTempDirectory("tk_myapps_test").toFile()
        try {
            val outFile = File(dir, "app_bundle.zip").apply { writeText("bundle") }
            val cfgFile = File(dir, "app.config.json").apply {
                writeText("""{"appName":"DemoApp","packageId":"com.tkofficial.demo","versionName":"1.0.0","versionCode":1}""")
            }
            val proj = ProjectMetadata(
                id = "p1",
                name = "DemoApp",
                directoryPath = dir.absolutePath,
                type = ProjectType.WEB_TO_APK,
                createdAt = 1L,
                updatedAt = 2L,
                sizeBytes = 100L,
                fileCount = 2,
                packageId = "com.tkofficial.demo"
            )
            val key = SigningKeyInfo(
                alias = "release_key",
                commonName = "TK",
                orgUnit = "Eng",
                organization = "TK OFFICIAL",
                locality = "Dhaka",
                state = "BD",
                country = "BD",
                algorithm = "EC",
                keySize = 256,
                validityYears = 25,
                createdAt = 1L,
                sha256Fingerprint = "AA:BB:CC:DD",
                keystoreFileName = "release.enc"
            )
            val matchedBuild = BuildRecord(
                id = "b1",
                projectId = "p1",
                projectName = "DemoApp",
                profile = BuildProfile.RELEASE,
                mode = BuildMode.MANUAL,
                stage = BuildStage.DONE,
                success = true,
                startedAt = 1000L,
                durationMs = 200L,
                outputPath = outFile.absolutePath,
                outputSha256 = "abc123",
                logLines = emptyList(),
                appName = "DemoApp",
                packageId = "com.tkofficial.demo",
                versionName = "1.0.0",
                versionCode = 1,
                signingAlias = "release_key",
                signingFingerprint = "AA:BB:CC:DD"
            )

            // 1. MATCHED
            val matched = MyApplicationsEngine.deriveApplications(listOf(proj), listOf(matchedBuild), listOf(key))
            assertEquals(1, matched.size)
            assertEquals(SigningContinuityState.MATCHED, matched.first().continuityState)

            // 2. KEY_MISMATCH
            val wrongKey = key.copy(sha256Fingerprint = "11:22:33:44")
            val mismatch = MyApplicationsEngine.deriveApplications(listOf(proj), listOf(matchedBuild), listOf(wrongKey))
            assertEquals(SigningContinuityState.KEY_MISMATCH, mismatch.first().continuityState)

            // 3. KEY_MISSING
            val keyMissing = MyApplicationsEngine.deriveApplications(listOf(proj), listOf(matchedBuild), emptyList())
            assertEquals(SigningContinuityState.KEY_MISSING, keyMissing.first().continuityState)

            // 4. UNSIGNED
            val unsignedBuild = matchedBuild.copy(signingAlias = "", signingFingerprint = "")
            val unsigned = MyApplicationsEngine.deriveApplications(listOf(proj), listOf(unsignedBuild), listOf(key))
            assertEquals(SigningContinuityState.UNSIGNED, unsigned.first().continuityState)

            // 5. ARTIFACT_MISSING
            val missingArtifact = matchedBuild.copy(outputPath = File(dir, "missing.apk").absolutePath)
            val missing = MyApplicationsEngine.deriveApplications(listOf(proj), listOf(missingArtifact), listOf(key))
            assertEquals(SigningContinuityState.ARTIFACT_MISSING, missing.first().continuityState)

            // 6. Upgrade version update writes cleanly
            MyApplicationsEngine.updateProjectVersionSpec(proj, "DemoApp Pro", "com.tkofficial.demo", "1.1.0", 2).getOrThrow()
            val updatedText = cfgFile.readText()
            assertTrue(updatedText.contains(""""appName": "DemoApp Pro""""))
            assertTrue(updatedText.contains(""""versionName": "1.1.0""""))
            assertTrue(updatedText.contains(""""versionCode": 2"""))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun utilityZipExplorerDetectsZipSlipAndFormatsPrettyXml() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("index.html"))
            zos.write("<html></html>".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("../evil.sh"))
            zos.write("bad".toByteArray())
            zos.closeEntry()
        }
        val summary = UtilityToolsEngine.inspectZipStream(ByteArrayInputStream(baos.toByteArray())).getOrThrow()
        assertEquals(2, summary.entryCount)
        assertEquals(listOf("../evil.sh"), summary.unsafeEntries)
        assertTrue(summary.entries.any { it.isUnsafePath && it.name == "../evil.sh" })

        val prettyXml = UtilityToolsEngine.formatXmlPretty("<manifest package=\"com.test\"><uses-permission android:name=\"INTERNET\"/></manifest>").getOrThrow()
        assertTrue(prettyXml.contains("<manifest"))
        assertTrue(prettyXml.contains("\n"))
    }

    @Test
    fun assetResourceAuditFlagsDuplicatesLargeAndUnusedSafely() {
        val dir = createTempDirectory("tk_asset_audit").toFile()
        val outside = createTempDirectory("tk_outside_file").toFile()
        try {
            val assets = File(dir, "assets").apply { mkdirs() }
            File(dir, "index.html").writeText("""<img src="assets/logo.png"/>""")
            File(assets, "logo.png").writeBytes(ByteArray(300_000) { 7 }) // >250KB AND duplicated below
            File(assets, "logo_copy.png").writeBytes(ByteArray(300_000) { 7 }) // unused + duplicate + large

            val findings = AssetResourceAuditCore.scanProjectResources(dir)
            assertTrue(findings.any { it.issueType == "DUPLICATE" })
            assertTrue(findings.any { it.issueType == "LARGE" })
            assertTrue(findings.any { it.issueType == "UNUSED" && it.relativePath.contains("logo_copy.png") })

            val externalVictim = File(outside, "keep.txt").apply { writeText("must survive") }
            try {
                AssetResourceAuditCore.deleteAssetSafely(dir, externalVictim.absolutePath)
                fail("Expected SecurityException when deleting asset outside workspace root")
            } catch (_: SecurityException) {
                // Expected security guard
            }
            assertTrue(externalVictim.exists())
            assertTrue(AssetResourceAuditCore.deleteAssetSafely(dir, File(assets, "logo_copy.png").absolutePath))
            assertFalse(File(assets, "logo_copy.png").exists())
        } finally {
            dir.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun settingsJsonExportExcludesSecretsAndRespectsBounds() {
        val sample = AppSettingsState(
            startupRoute = "live_coding",
            recentProjectsLimit = 9,
            animSpeedMultiplier = 1.5f,
            hasGitHubToken = true,
            pinLockEnabled = true,
            outputNamePrefix = "custom_app",
            languageCode = "ar"
        )
        val exported = SettingsDataStore.exportSanitizedSettingsJson(sample)
        assertTrue(exported.startsWith("{") && exported.endsWith("}"))
        assertTrue(exported.contains(""""startupRoute": "live_coding""""))
        assertTrue(exported.contains(""""recentProjectsLimit": 9"""))
        assertTrue(exported.contains(""""outputNamePrefix": "custom_app""""))
        assertTrue(exported.contains(""""languageCode": "ar""""))
        assertTrue(exported.contains(""""excludesSecretsAndSigningKeys": true"""))
        assertFalse(exported.contains("githubToken", ignoreCase = true))
        assertFalse(exported.contains("pinHash", ignoreCase = true))
    }
}

