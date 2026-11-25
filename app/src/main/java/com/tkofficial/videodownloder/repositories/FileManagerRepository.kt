package com.tkofficial.videodownloder.repositories

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.tkofficial.videodownloder.models.FileEntry
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.models.StorageSummary
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileManagerRepository(private val context: Context) {

    fun getRootDirectory(): File = FormatUtils.getAppMediaRoot(context)

    suspend fun listDirectory(dir: File, showHidden: Boolean): List<FileEntry> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        dir.listFiles().orEmpty()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { file ->
                val cat = if (file.isDirectory) MediaCategory.OTHER else FormatUtils.classifyMimeType(null, file.name)
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    isHidden = file.isHidden || file.name.startsWith("."),
                    category = cat,
                    childCount = -1
                )
            }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun listByCategory(category: MediaCategory?, showHidden: Boolean): List<FileEntry> = withContext(Dispatchers.IO) {
        val root = getRootDirectory()
        val collected = mutableListOf<FileEntry>()
        root.walkTopDown()
            .filter { it.isFile && (showHidden || !it.name.startsWith(".")) }
            .forEach { file ->
                val cat = FormatUtils.classifyMimeType(null, file.name)
                if (category == null || cat == category) {
                    collected += FileEntry(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = false,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        isHidden = file.name.startsWith("."),
                        category = cat
                    )
                }
            }
        collected.sortedByDescending { it.lastModified }
    }

    suspend fun createFolder(parent: File, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val safe = FormatUtils.sanitizeFileName(folderName, "").trimEnd('.')
        if (safe.isBlank()) return@withContext false
        File(parent, safe).mkdirs()
    }

    suspend fun renameFile(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        val safe = FormatUtils.sanitizeFileName(newName, file.extension)
        val dest = File(file.parentFile ?: getRootDirectory(), safe)
        file.renameTo(dest)
    }

    suspend fun copyOrMove(files: List<File>, destDir: File, move: Boolean): Int = withContext(Dispatchers.IO) {
        if (!destDir.exists()) destDir.mkdirs()
        var count = 0
        files.forEach { src ->
            val target = FormatUtils.resolveUniqueFile(destDir, src.name)
            runCatching {
                if (src.isDirectory) {
                    src.copyRecursively(target, overwrite = false)
                    if (move) src.deleteRecursively()
                } else {
                    src.copyTo(target, overwrite = false)
                    if (move) src.delete()
                }
                count++
            }
        }
        count
    }

    suspend fun deleteFiles(files: List<File>): Int = withContext(Dispatchers.IO) {
        var count = 0
        files.forEach { f ->
            if (if (f.isDirectory) f.deleteRecursively() else f.delete()) count++
        }
        count
    }

    suspend fun createZip(files: List<File>, outputZip: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zos ->
                files.forEach { file ->
                    if (file.isDirectory) {
                        val baseLen = file.parentFile?.absolutePath?.length?.plus(1) ?: 0
                        file.walkTopDown().filter { it.isFile }.forEach { child ->
                            val entryName = child.absolutePath.drop(baseLen)
                            zos.putNextEntry(ZipEntry(entryName))
                            BufferedInputStream(FileInputStream(child)).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    } else if (file.exists()) {
                        zos.putNextEntry(ZipEntry(file.name))
                        BufferedInputStream(FileInputStream(file)).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            outputZip
        }
    }

    suspend fun extractZip(zipFile: File, destDir: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!destDir.exists()) destDir.mkdirs()
            val canonicalDest = destDir.canonicalPath
            var extracted = 0
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    // Zip Slip protection
                    if (!outFile.canonicalPath.startsWith(canonicalDest + File.separator)) {
                        throw SecurityException("Blocked unsafe ZIP path: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        extracted++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            extracted
        }
    }

    suspend fun batchRename(files: List<File>, prefix: String): Int = withContext(Dispatchers.IO) {
        var renamed = 0
        files.forEachIndexed { index, file ->
            val ext = if (file.extension.isNotBlank()) ".${file.extension}" else ""
            val newName = "${prefix}_${index + 1}$ext"
            val dest = FormatUtils.resolveUniqueFile(file.parentFile ?: getRootDirectory(), newName)
            if (file.renameTo(dest)) renamed++
        }
        renamed
    }

    suspend fun analyzeStorage(): StorageSummary = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        val used = (total - avail).coerceAtLeast(0L)

        val appRoot = getRootDirectory()
        val allAppFiles = appRoot.walkTopDown().filter { it.isFile }.toList()
        val appMediaBytes = allAppFiles.sumOf { it.length() }
        val downloadsBytes = FormatUtils.getAppSubDir(context, MediaCategory.VIDEO).walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            FormatUtils.getAppSubDir(context, MediaCategory.AUDIO).walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            FormatUtils.getAppSubDir(context, MediaCategory.IMAGE).walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val cacheBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val vaultBytes = FormatUtils.getVaultDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

        val entries = allAppFiles.map { f ->
            FileEntry(
                name = f.name,
                path = f.absolutePath,
                isDirectory = false,
                sizeBytes = f.length(),
                lastModified = f.lastModified(),
                isHidden = f.name.startsWith("."),
                category = FormatUtils.classifyMimeType(null, f.name)
            )
        }

        val largeFiles = entries.filter { it.sizeBytes >= 10 * 1024 * 1024L }
            .sortedByDescending { it.sizeBytes }
            .ifEmpty { entries.sortedByDescending { it.sizeBytes }.take(10) }

        val duplicates = entries
            .filter { it.sizeBytes > 0L }
            .groupBy { it.sizeBytes }
            .filter { it.value.size > 1 }
            .flatMap { (_, sameSize) ->
                sameSize.groupBy { FormatUtils.computeFileHash(File(it.path), "SHA-256") }
                    .values
                    .filter { it.size > 1 }
            }

        StorageSummary(
            totalBytes = total,
            usedBytes = used,
            availableBytes = avail,
            appMediaBytes = appMediaBytes,
            downloadsBytes = downloadsBytes,
            cacheBytes = cacheBytes,
            vaultBytes = vaultBytes,
            largeFiles = largeFiles,
            duplicateGroups = duplicates
        )
    }

    suspend fun clearAppCache(): Long = withContext(Dispatchers.IO) {
        val before = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        before
    }
}
