package com.example.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class EnvironmentHealthReport(
    val supportedAbis: List<String>,
    val cpuCores: Int,
    val usedHeapMb: Long,
    val maxHeapMb: Long,
    val usableStorageBytes: Long,
    val recommendedMaxWorkers: Int,
    val canExecuteLocalWebBuilds: Boolean,
    val statusSummary: String
)

data class StorageBreakdown(
    val projectsBytes: Long,
    val buildsBytes: Long,
    val backupsBytes: Long,
    val apkCacheBytes: Long,
    val dbBackupsBytes: Long,
    val extractedBytes: Long
) {
    val cleanableCacheBytes: Long get() = apkCacheBytes + extractedBytes
    val totalBytes: Long get() = projectsBytes + buildsBytes + backupsBytes + apkCacheBytes + dbBackupsBytes + extractedBytes
}

class EnvironmentHealthEngine(private val context: Context) {

    fun inspectHealth(): EnvironmentHealthReport {
        val rt = Runtime.getRuntime()
        val cores = rt.availableProcessors().coerceAtLeast(1)
        val maxHeapMb = rt.maxMemory() / (1024 * 1024)
        val usedHeapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val freeBytes = context.filesDir.usableSpace
        val workers = when {
            cores >= 6 && maxHeapMb >= 256 -> 3
            cores >= 4 && maxHeapMb >= 128 -> 2
            else -> 1
        }
        val storageOk = freeBytes > 25L * 1024 * 1024
        val summary = if (storageOk) {
            "Healthy ($cores cores • ${maxHeapMb}MB heap • ${freeBytes / (1024 * 1024)}MB free)"
        } else {
            "Low storage (${freeBytes / (1024 * 1024)}MB free) — cleanup recommended"
        }
        return EnvironmentHealthReport(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            cpuCores = cores,
            usedHeapMb = usedHeapMb,
            maxHeapMb = maxHeapMb,
            usableStorageBytes = freeBytes,
            recommendedMaxWorkers = workers,
            canExecuteLocalWebBuilds = storageOk,
            statusSummary = summary
        )
    }

    suspend fun computeStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "tkstudio")
        StorageBreakdown(
            projectsBytes = dirSize(File(root, "projects")),
            buildsBytes = dirSize(File(root, "builds")),
            backupsBytes = dirSize(File(root, "backups")),
            apkCacheBytes = dirSize(File(root, "apk_cache")),
            dbBackupsBytes = dirSize(File(root, "db_backups")),
            extractedBytes = dirSize(File(root, "extracted"))
        )
    }

    /**
     * Safely cleans temporary APK inspection caches and extracted temporary directories ONLY.
     * Source projects, build outputs, backups, and signing keys are strictly protected.
     */
    suspend fun cleanSafeCaches(): Long = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "tkstudio")
        val apkCache = File(root, "apk_cache")
        val extracted = File(root, "extracted")
        val freed = dirSize(apkCache) + dirSize(extracted)
        apkCache.deleteRecursively()
        extracted.deleteRecursively()
        apkCache.mkdirs()
        extracted.mkdirs()
        freed
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
