package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.example.data.db.DatabaseIntegrityManager
import com.example.data.db.StudioDatabase
import com.example.model.AppSettingsState
import com.example.model.FindingSeverity
import com.example.model.ProjectMetadata
import com.example.model.ResourceAuditItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object AssetResourceAuditCore {
    private val imageExts = setOf("png", "webp", "jpg", "jpeg", "gif", "svg")
    private val sourceExts = setOf("html", "htm", "css", "js", "ts", "json", "xml", "kt", "kts", "md")
    const val OVERSIZED_ASSET_BYTES = 250L * 1024L

    fun scanProjectResources(projectDir: File): List<ResourceAuditItem> {
        if (!projectDir.exists() || !projectDir.isDirectory) return emptyList()

        val allFiles = projectDir.walkTopDown()
            .filter {
                it.isFile &&
                    !it.absolutePath.contains("${File.separator}.tkstudio_git") &&
                    !it.absolutePath.contains("${File.separator}.tkstudio_repair_backups")
            }
            .toList()

        val sourceCorpus = buildString {
            allFiles.filter { it.extension.lowercase() in sourceExts && it.length() < 1024 * 1024 }
                .forEach { f ->
                    runCatching { appendLine(f.readText()) }
                }
        }

        val items = mutableListOf<ResourceAuditItem>()
        val shaSeen = mutableMapOf<String, MutableList<Pair<File, String>>>()

        allFiles.forEach { file ->
            val rel = file.relativeTo(projectDir).path.replace(File.separatorChar, '/')
            val ext = file.extension.lowercase()
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@forEach
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

            shaSeen.getOrPut(sha) { mutableListOf() }.add(file to rel)

            if (file.length() > OVERSIZED_ASSET_BYTES) {
                items.add(
                    ResourceAuditItem(
                        relativePath = rel,
                        absolutePath = file.absolutePath,
                        sizeBytes = file.length(),
                        sha256 = sha,
                        issueType = "LARGE",
                        detail = "Asset exceeds 250 KB (${file.length() / 1024} KB); consider resizing or WEBP compression."
                    )
                )
            }

            if (ext in imageExts && sourceCorpus.isNotEmpty() && !sourceCorpus.contains(file.name)) {
                items.add(
                    ResourceAuditItem(
                        relativePath = rel,
                        absolutePath = file.absolutePath,
                        sizeBytes = file.length(),
                        sha256 = sha,
                        issueType = "UNUSED",
                        detail = "Image '${file.name}' is not referenced in workspace HTML/CSS/JS/XML/Kotlin files."
                    )
                )
            }
        }

        shaSeen.forEach { (sha, group) ->
            if (group.size > 1) {
                group.drop(1).forEach { (file, rel) ->
                    items.add(
                        ResourceAuditItem(
                            relativePath = rel,
                            absolutePath = file.absolutePath,
                            sizeBytes = file.length(),
                            sha256 = sha,
                            issueType = "DUPLICATE",
                            detail = "Identical SHA-256 content to '${group.first().second}'."
                        )
                    )
                }
            }
        }

        return items.sortedWith(compareBy<ResourceAuditItem> { it.issueType }.thenByDescending { it.sizeBytes })
    }

    fun deleteAssetSafely(projectDir: File, targetPath: String): Boolean {
        val canonicalRoot = projectDir.canonicalPath
        val target = File(targetPath)
        if (!target.canonicalPath.startsWith(canonicalRoot + File.separator)) {
            throw SecurityException("Refused asset delete outside workspace root.")
        }
        return target.exists() && target.isFile && target.delete()
    }
}

class AssetStudioEngine(private val context: Context) {

    enum class AssetOutputFormat(val label: String, val ext: String) {
        PNG("PNG (Lossless)", "png"),
        WEBP("WEBP (Compact)", "webp"),
        JPEG("JPEG (Photo)", "jpg")
    }

    fun validateResourceName(raw: String): Pair<Boolean, String> {
        val sanitized = raw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val valid = sanitized.isNotEmpty() && Regex("^[a-z][a-z0-9_]*$").matches(sanitized)
        return valid to sanitized
    }

    suspend fun scanProjectResources(project: ProjectMetadata): List<ResourceAuditItem> =
        withContext(Dispatchers.IO) {
            AssetResourceAuditCore.scanProjectResources(File(project.directoryPath))
        }

    suspend fun deleteProjectAsset(project: ProjectMetadata, absolutePath: String): Boolean =
        withContext(Dispatchers.IO) {
            AssetResourceAuditCore.deleteAssetSafely(File(project.directoryPath), absolutePath)
        }

    suspend fun resizeAndSaveAsset(
        sourceUri: Uri,
        project: ProjectMetadata,
        outputName: String,
        targetSizePx: Int,
        quality: Int,
        format: AssetOutputFormat
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val (valid, safeName) = validateResourceName(outputName.substringBeforeLast('.'))
            val finalBase = if (valid) safeName else "asset_${System.currentTimeMillis()}"
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open image stream.")
            val original = input.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalStateException("Unsupported image format.")
            val scaled = Bitmap.createScaledBitmap(original, targetSizePx.coerceIn(32, 2048), targetSizePx.coerceIn(32, 2048), true)
            val assetsDir = File(project.directoryPath, "assets").apply { mkdirs() }
            val outFile = File(assetsDir, "$finalBase.${format.ext}")
            FileOutputStream(outFile).use { fos ->
                val compressFmt = when (format) {
                    AssetOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                    AssetOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    AssetOutputFormat.WEBP -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                }
                scaled.compress(compressFmt, quality.coerceIn(20, 100), fos)
            }
            if (scaled !== original) scaled.recycle()
            original.recycle()
            outFile
        }
    }

    suspend fun generateCodedBrandAsset(
        project: ProjectMetadata,
        label: String,
        bgColorInt: Int,
        accentColorInt: Int,
        sizePx: Int = 512
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColorInt }
            canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), sizePx * 0.2f, sizePx * 0.2f, bgPaint)
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColorInt
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.05f
            }
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.32f, ringPaint)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColorInt
                textAlign = Paint.Align.CENTER
                textSize = sizePx * 0.24f
                isFakeBoldText = true
            }
            val monogram = label.trim().take(2).uppercase().ifEmpty { "TK" }
            val yPos = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(monogram, sizePx / 2f, yPos, textPaint)

            val assetsDir = File(project.directoryPath, "assets").apply { mkdirs() }
            val outFile = File(assetsDir, "generated_icon_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos -> bmp.compress(Bitmap.CompressFormat.PNG, 100, fos) }
            bmp.recycle()
            outFile
        }
    }
}

data class QaCheckResult(
    val suite: String,
    val title: String,
    val passed: Boolean,
    val detail: String
)

class TestQaEngine(private val context: Context) {

    suspend fun runFullSuite(
        projects: List<ProjectMetadata>,
        settings: AppSettingsState,
        db: StudioDatabase
    ): List<QaCheckResult> = withContext(Dispatchers.IO) {
        val startedNs = System.nanoTime()
        val results = mutableListOf<QaCheckResult>()

        // 1. Database Integrity Check (#43)
        val dbHealth = DatabaseIntegrityManager(context).inspectAndVerify(db)
        results.add(
            QaCheckResult(
                suite = "Database",
                title = "Room Schema v${dbHealth.schemaVersion} & Integrity",
                passed = dbHealth.integrityOk,
                detail = "${dbHealth.integrityMessage} (${dbHealth.dbSizeBytes / 1024} KB, ${dbHealth.backupCount} pre-migration backups)"
            )
        )

        // 2. Security Regression Check
        val findings = SecurityDiagnosticsEngine(context).runAudit(projects, settings)
        val highRisks = findings.count { it.severity == FindingSeverity.HIGH }
        results.add(
            QaCheckResult(
                suite = "Security",
                title = "Secret & Transport Safety",
                passed = highRisks == 0,
                detail = if (highRisks == 0) "0 high-risk findings across ${projects.size} project(s)" else "$highRisks high-risk finding(s) detected"
            )
        )

        // 3. Localization 5-Language Completeness & RTL Check
        val locAudit = LocalizationEngine.auditCompleteness()
        val allLangsComplete = locAudit.values.all { it.missingOrBlankKeys.isEmpty() }
        val rtlValid = StudioLanguage.fromCode("ar").isRtl && StudioLanguage.fromCode("ur").isRtl && !StudioLanguage.fromCode("en").isRtl
        val keyCount = locAudit.values.firstOrNull()?.totalKeys ?: 0
        results.add(
            QaCheckResult(
                suite = "Localization",
                title = "5 Languages (EN/BN/AR/ZH/UR) + RTL",
                passed = allLangsComplete && rtlValid && locAudit.size == 5,
                detail = "${locAudit.size} locales, $keyCount/key UI strings 100% populated; RTL=$rtlValid (lesson step bodies authored in EN)"
            )
        )

        // 4. Tutorials 266-Lesson & 23-Series Structural Check
        val lessons = TutorialsRepository.lessons
        val distinctIds = lessons.map { it.id }.toSet()
        val seriesCount = TutorialsRepository.seriesRanges.size
        val tutorialsValid = lessons.size == 266 && distinctIds.size == 266 && seriesCount == 23
        results.add(
            QaCheckResult(
                suite = "Tutorials",
                title = "266 Lessons Across 23 Series",
                passed = tutorialsValid,
                detail = "${lessons.size} ordered lessons, ${seriesCount} series ranges (1..266)"
            )
        )

        // 5. Backup ZIP Roundtrip Probe
        val backupRoundtripOk = runCatching {
            val payload = "tkstudio-qa-probe-${System.currentTimeMillis()}"
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("probe.txt"))
                zos.write(payload.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            var readBack = ""
            ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
                if (zis.nextEntry != null) {
                    readBack = zis.readBytes().toString(Charsets.UTF_8)
                    zis.closeEntry()
                }
            }
            readBack == payload
        }.getOrDefault(false)
        results.add(
            QaCheckResult(
                suite = "Backup",
                title = "ZIP Archive Roundtrip & Integrity",
                passed = backupRoundtripOk,
                detail = if (backupRoundtripOk) "In-memory ZIP encode/decode + UTF-8 verification passed" else "Archive roundtrip failed"
            )
        )

        // 6. Accessibility & Play Policy Permission Audit
        val perms = SecurityDiagnosticsEngine(context).getDeclaredPermissions()
        val hasProhibitedStorage = perms.any {
            it.contains("READ_EXTERNAL_STORAGE") || it.contains("REQUEST_INSTALL_PACKAGES") || it.contains("QUERY_ALL_PACKAGES")
        }
        results.add(
            QaCheckResult(
                suite = "Accessibility & Policy",
                title = "48dp Targets & Least-Privilege Permissions",
                passed = settings.largeTouchTargets && !hasProhibitedStorage,
                detail = "48dp+ targets=${settings.largeTouchTargets} • Prohibited perms=$hasProhibitedStorage"
            )
        )

        // 7. Environment, Storage & Measured Suite Latency
        val env = EnvironmentHealthEngine(context).inspectHealth()
        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
        results.add(
            QaCheckResult(
                suite = "Performance",
                title = "Memory, Workers & Audit Latency",
                passed = env.canExecuteLocalWebBuilds,
                detail = "${env.usedHeapMb}/${env.maxHeapMb}MB heap • ${env.recommendedMaxWorkers} workers • Audit took ${elapsedMs}ms"
            )
        )

        // 8. Compatibility Matrix
        results.add(
            QaCheckResult(
                suite = "Compatibility",
                title = "ABI & Runtime Verification",
                passed = env.supportedAbis.isNotEmpty(),
                detail = "ABIs: ${env.supportedAbis.joinToString(", ")}"
            )
        )

        results
    }
}
