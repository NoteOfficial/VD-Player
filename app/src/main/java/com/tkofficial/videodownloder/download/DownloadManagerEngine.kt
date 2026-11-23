package com.tkofficial.videodownloder.download

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tkofficial.videodownloder.data.AppSettings
import com.tkofficial.videodownloder.data.PreferencesRepository
import com.tkofficial.videodownloder.database.AppNotificationEntity
import com.tkofficial.videodownloder.database.DownloadDao
import com.tkofficial.videodownloder.database.DownloadEntity
import com.tkofficial.videodownloder.database.DownloadHistoryEntity
import com.tkofficial.videodownloder.database.DownloadedMediaEntity
import com.tkofficial.videodownloder.database.GalleryDao
import com.tkofficial.videodownloder.database.SystemDao
import com.tkofficial.videodownloder.models.AnalyzedMedia
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.models.MediaFormatOption
import com.tkofficial.videodownloder.notifications.NotificationHelper
import com.tkofficial.videodownloder.services.DownloadForegroundService
import com.tkofficial.videodownloder.utilities.FormatUtils
import com.tkofficial.videodownloder.workers.ScheduledDownloadWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManagerEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadDao,
    private val galleryDao: GalleryDao,
    private val systemDao: SystemDao,
    private val prefsRepo: PreferencesRepository,
    private val notificationHelper: NotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pumpMutex = Mutex()
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    @Volatile
    private var latestSettings: AppSettings = AppSettings()

    @Volatile
    private var lastStorageWarningAtMs: Long = 0L

    private companion object {
        const val LOW_STORAGE_THRESHOLD_BYTES = 250L * 1024L * 1024L
        const val STORAGE_WARNING_COOLDOWN_MS = 10L * 60L * 1000L
    }

    init {
        scope.launch {
            prefsRepo.settingsFlow.collect { settings ->
                latestSettings = settings
                pumpQueue()
            }
        }
        registerNetworkRecoveryCallback()
    }

    private fun registerNetworkRecoveryCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        delay(800)
                        pumpQueue()
                    }
                }
            })
        }
    }

    suspend fun enqueueMedia(
        analyzed: AnalyzedMedia,
        option: MediaFormatOption,
        priority: Int = 0,
        scheduledDelayMinutes: Long = 0L
    ): Result<Long> = withContext(Dispatchers.IO) {
        val settings = prefsRepo.settingsFlow.first()
        latestSettings = settings
        if (settings.duplicateCheckEnabled) {
            val existing = downloadDao.findByUrl(option.downloadUrl)
            if (existing != null && existing.status in setOf("QUEUED", "DOWNLOADING", "PAUSED", "COMPLETED")) {
                return@withContext Result.failure(
                    IllegalStateException("Duplicate link already exists in downloads (${existing.fileName}).")
                )
            }
        }

        val category = FormatUtils.classifyMimeType(option.mimeType, analyzed.title)
        val ext = FormatUtils.extensionFromMime(option.mimeType)
        val cleanName = FormatUtils.sanitizeFileName(analyzed.title, ext)
        val targetDir = FormatUtils.getAppSubDir(context, category)
        val targetFile = if (settings.autoRenameDuplicates) {
            FormatUtils.resolveUniqueFile(targetDir, cleanName)
        } else {
            File(targetDir, cleanName)
        }

        val scheduledTime = if (scheduledDelayMinutes > 0L) {
            System.currentTimeMillis() + scheduledDelayMinutes * 60_000L
        } else null

        val entity = DownloadEntity(
            url = analyzed.sourceUrl,
            resolvedUrl = option.downloadUrl,
            title = targetFile.nameWithoutExtension,
            fileName = targetFile.name,
            mimeType = option.mimeType,
            mediaType = category.name,
            platform = analyzed.platform,
            thumbnailUrl = analyzed.thumbnailUrl,
            localPath = targetFile.absolutePath,
            contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile).toString(),
            totalBytes = option.estimatedBytes,
            downloadedBytes = 0L,
            status = "QUEUED",
            supportsRange = option.supportsRange,
            priority = priority,
            scheduledAt = scheduledTime
        )

        val id = downloadDao.enqueueDownload(entity, settings.wifiOnlyDownloads, settings.speedLimitKbps)

        if (scheduledDelayMinutes > 0L) {
            scheduleWithWorkManager(id, scheduledDelayMinutes, settings.wifiOnlyDownloads)
        } else {
            startForegroundServiceIfNeeded()
            pumpQueue()
        }
        Result.success(id)
    }

    suspend fun enqueueDirectUrl(
        url: String,
        fileName: String,
        mimeType: String,
        category: MediaCategory,
        priority: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        val settings = prefsRepo.settingsFlow.first()
        latestSettings = settings
        val ext = FormatUtils.extensionFromMime(mimeType)
        val cleanName = FormatUtils.sanitizeFileName(fileName, ext)
        val targetDir = FormatUtils.getAppSubDir(context, category)
        val targetFile = FormatUtils.resolveUniqueFile(targetDir, cleanName)

        val entity = DownloadEntity(
            url = url,
            resolvedUrl = url,
            title = targetFile.nameWithoutExtension,
            fileName = targetFile.name,
            mimeType = mimeType,
            mediaType = category.name,
            platform = FormatUtils.extractHost(url),
            localPath = targetFile.absolutePath,
            contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile).toString(),
            totalBytes = -1L,
            status = "QUEUED",
            priority = priority
        )
        val id = downloadDao.enqueueDownload(entity, settings.wifiOnlyDownloads, settings.speedLimitKbps)
        startForegroundServiceIfNeeded()
        pumpQueue()
        id
    }

    private fun scheduleWithWorkManager(downloadId: Long, delayMinutes: Long, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val data = Data.Builder().putLong(ScheduledDownloadWorker.KEY_DOWNLOAD_ID, downloadId).build()
        val request = OneTimeWorkRequestBuilder<ScheduledDownloadWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun triggerScheduledDownload(downloadId: Long) {
        scope.launch {
            val item = downloadDao.getById(downloadId) ?: return@launch
            if (item.status == "QUEUED" || item.status == "PAUSED") {
                downloadDao.update(item.copy(scheduledAt = null, status = "QUEUED", updatedAt = System.currentTimeMillis()))
                startForegroundServiceIfNeeded()
                pumpQueue()
            }
        }
    }

    fun pauseDownload(id: Long): Job = scope.launch {
        activeJobs[id]?.cancel(CancellationException("Paused by user"))
        activeJobs.remove(id)
        downloadDao.updateStatus(id, "PAUSED")
        downloadDao.getById(id)?.let { notificationHelper.notifyDownloadProgress(it) }
        updateActiveCountAndService()
        pumpQueue()
    }

    fun resumeDownload(id: Long): Job = scope.launch {
        val item = downloadDao.getById(id) ?: return@launch
        if (item.status == "PAUSED" || item.status == "FAILED") {
            downloadDao.updateStatus(id, "QUEUED", error = null)
            startForegroundServiceIfNeeded()
            pumpQueue()
        }
    }

    fun cancelDownload(id: Long): Job = scope.launch {
        activeJobs[id]?.cancel(CancellationException("Cancelled by user"))
        activeJobs.remove(id)
        val item = downloadDao.getById(id)
        if (item != null) {
            File("${item.localPath}.part").delete()
            downloadDao.updateStatus(id, "CANCELLED", error = "Cancelled by user")
        }
        notificationHelper.cancelProgressNotification(id)
        updateActiveCountAndService()
        pumpQueue()
    }

    fun retryDownload(id: Long): Job = scope.launch {
        val item = downloadDao.getById(id) ?: return@launch
        downloadDao.update(
            item.copy(
                status = "QUEUED",
                errorMessage = null,
                retryCount = item.retryCount + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        startForegroundServiceIfNeeded()
        pumpQueue()
    }

    fun renameDownload(id: Long, newFileName: String) {
        scope.launch {
            val item = downloadDao.getById(id) ?: return@launch
            val oldFile = File(item.localPath)
            val ext = oldFile.extension.ifBlank { FormatUtils.extensionFromMime(item.mimeType) }
            val safeName = FormatUtils.sanitizeFileName(newFileName, ext)
            val newFile = File(oldFile.parentFile ?: FormatUtils.getAppMediaRoot(context), safeName)
            if (oldFile.exists()) {
                oldFile.renameTo(newFile)
            }
            val partFile = File("${item.localPath}.part")
            if (partFile.exists()) {
                partFile.renameTo(File("${newFile.absolutePath}.part"))
            }
            downloadDao.renameDownload(id, safeName, newFile.absolutePath)
            val uriStr = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newFile).toString()
            }.getOrDefault(item.contentUri)
            galleryDao.renameMediaByDownloadId(
                downloadId = id,
                newTitle = newFile.nameWithoutExtension,
                newFileName = safeName,
                newPath = newFile.absolutePath,
                newContentUri = uriStr
            )
        }
    }

    fun deleteDownload(id: Long, deleteLocalFile: Boolean) {
        scope.launch {
            activeJobs[id]?.cancel(CancellationException("Deleted"))
            activeJobs.remove(id)
            val item = downloadDao.getById(id)
            if (item != null && deleteLocalFile) {
                File(item.localPath).delete()
                File("${item.localPath}.part").delete()
                galleryDao.deleteMediaByDownloadId(id)
            }
            downloadDao.deleteById(id)
            notificationHelper.cancelProgressNotification(id)
            updateActiveCountAndService()
            pumpQueue()
        }
    }

    fun adjustPriority(id: Long, delta: Int) {
        scope.launch {
            val item = downloadDao.getById(id) ?: return@launch
            downloadDao.updatePriority(id, (item.priority + delta).coerceIn(-10, 100))
        }
    }

    fun pumpQueue() {
        scope.launch {
            pumpMutex.withLock {
                val settings = latestSettings
                val maxConcurrent = settings.maxConcurrentDownloads.coerceIn(1, 5)
                val queued = downloadDao.getQueuedOnce()
                    .filter { it.scheduledAt == null || it.scheduledAt <= System.currentTimeMillis() }

                for (item in queued) {
                    if (activeJobs.size >= maxConcurrent) break
                    if (activeJobs.containsKey(item.id)) continue
                    if (!canDownloadOnCurrentNetwork(settings.wifiOnlyDownloads)) break

                    val job = scope.launch {
                        executeDownloadTask(item.id)
                    }
                    activeJobs[item.id] = job
                }
                updateActiveCountAndService()
            }
        }
    }

    private suspend fun executeDownloadTask(downloadId: Long) {
        try {
            val entity = downloadDao.getById(downloadId) ?: return
            val settings = latestSettings

            if (!canDownloadOnCurrentNetwork(settings.wifiOnlyDownloads)) {
                downloadDao.updateStatus(downloadId, "PAUSED", "Waiting for Wi-Fi connection")
                return
            }

            if (!FormatUtils.isValidDownloadUrl(entity.resolvedUrl, settings.allowCleartextHttp)) {
                val msg = "Blocked by HTTPS-only security setting. Enable HTTP in Network Settings if needed."
                failDownload(entity, msg)
                return
            }

            downloadDao.updateStatus(downloadId, "DOWNLOADING", null)
            val partFile = File("${entity.localPath}.part")
            val finalFile = File(entity.localPath)
            finalFile.parentFile?.mkdirs()

            val existingBytes = if (partFile.exists()) partFile.length() else 0L
            val canAttemptRange = existingBytes > 0L && entity.supportsRange

            val reqBuilder = Request.Builder()
                .url(entity.resolvedUrl)
                .header("User-Agent", "VDPlayer/1.0 (Android)")
            if (canAttemptRange) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            } else if (existingBytes > 0L) {
                partFile.delete()
            }

            val client = okHttpClient.newBuilder()
                .connectTimeout(settings.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(settings.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    val reason = "HTTP ${response.code}: ${response.message.ifBlank { "Server rejected request" }}"
                    if (settings.autoRetryOnFailure && entity.retryCount < 2 && response.code in 500..599) {
                        downloadDao.update(entity.copy(status = "QUEUED", retryCount = entity.retryCount + 1, errorMessage = reason))
                        delay(2000L * (entity.retryCount + 1))
                        return
                    }
                    failDownload(entity, reason)
                    return
                }

                val isResuming206 = response.code == 206
                val contentLength = response.body?.contentLength() ?: -1L
                val totalBytes = when {
                    isResuming206 && contentLength > 0L -> existingBytes + contentLength
                    contentLength > 0L -> contentLength
                    entity.totalBytes > 0L -> entity.totalBytes
                    else -> -1L
                }
                val supportsRange = isResuming206 ||
                    (response.header("Accept-Ranges")?.contains("bytes", true) == true)

                val storageDir = finalFile.parentFile ?: context.filesDir
                checkLowStorageWarning(storageDir, settings.notifyStorageWarnings)

                if (totalBytes > 0L && !hasEnoughStorage(storageDir, totalBytes - (if (isResuming206) existingBytes else 0L))) {
                    failDownload(entity, "Insufficient device storage available for ${FormatUtils.formatBytes(totalBytes)}.")
                    return
                }

                val bodyStream = response.body?.byteStream() ?: run {
                    failDownload(entity, "Empty response stream from server.")
                    return
                }

                var downloadedSoFar = if (isResuming206) existingBytes else 0L
                val buffer = ByteArray(16 * 1024)
                var lastUiUpdateMs = System.currentTimeMillis()
                var lastNotifUpdateMs = 0L
                var bytesInWindow = 0L

                val raf = if (isResuming206) {
                    RandomAccessFile(partFile, "rw").apply { seek(existingBytes) }
                } else {
                    if (partFile.exists()) partFile.delete()
                    null
                }
                val fos = if (raf == null) FileOutputStream(partFile, false) else null

                try {
                    bodyStream.use { input ->
                        var read: Int
                        var speedWindowStart = System.currentTimeMillis()
                        var speedBytes = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            if (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive != true) {
                                throw CancellationException("Job cancelled")
                            }

                            if (raf != null) raf.write(buffer, 0, read) else fos?.write(buffer, 0, read)
                            downloadedSoFar += read
                            bytesInWindow += read
                            speedBytes += read

                            // Speed limiter (KB/s)
                            val limitKbps = latestSettings.speedLimitKbps
                            if (limitKbps > 0) {
                                val maxBytesPerSec = limitKbps * 1024L
                                val elapsed = (System.currentTimeMillis() - speedWindowStart).coerceAtLeast(1L)
                                val expectedMs = (speedBytes * 1000L) / maxBytesPerSec
                                if (expectedMs > elapsed) {
                                    delay((expectedMs - elapsed).coerceAtMost(500L))
                                }
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdateMs >= 400L) {
                                val elapsedSec = ((now - lastUiUpdateMs).coerceAtLeast(1L)) / 1000.0
                                val speed = (bytesInWindow / elapsedSec).toLong()
                                val eta = if (totalBytes > downloadedSoFar && speed > 0L) {
                                    (totalBytes - downloadedSoFar) / speed
                                } else -1L

                                downloadDao.updateProgress(
                                    id = downloadId,
                                    downloaded = downloadedSoFar,
                                    total = totalBytes,
                                    speed = speed,
                                    eta = eta,
                                    supportsRange = supportsRange
                                )

                                if (latestSettings.notifyActiveDownloads && (now - lastNotifUpdateMs >= 750L)) {
                                    notificationHelper.notifyDownloadProgress(
                                        entity.copy(
                                            status = "DOWNLOADING",
                                            downloadedBytes = downloadedSoFar,
                                            totalBytes = totalBytes,
                                            speedBytesPerSec = speed,
                                            etaSeconds = eta,
                                            supportsRange = supportsRange
                                        )
                                    )
                                    lastNotifUpdateMs = now
                                }
                                lastUiUpdateMs = now
                                bytesInWindow = 0L
                                if (now - speedWindowStart > 2000L) {
                                    speedWindowStart = now
                                    speedBytes = 0L
                                }
                            }
                        }
                    }
                } finally {
                    raf?.close()
                    fos?.close()
                }

                // Finalize file atomically
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                val sha256 = if (latestSettings.verifySha256AfterDownload) {
                    FormatUtils.computeFileHash(finalFile, "SHA-256")
                } else null

                val finalSize = finalFile.length()
                val updatedEntity = entity.copy(
                    status = "COMPLETED",
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    supportsRange = supportsRange,
                    sha256Hash = sha256,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                downloadDao.update(updatedEntity)
                downloadDao.removeFromQueue(downloadId)
                downloadDao.insertHistory(
                    DownloadHistoryEntity(
                        downloadId = downloadId,
                        url = updatedEntity.url,
                        title = updatedEntity.title,
                        fileName = updatedEntity.fileName,
                        fileSize = finalSize,
                        mimeType = updatedEntity.mimeType,
                        platform = updatedEntity.platform,
                        status = "COMPLETED"
                    )
                )

                registerCompletedMedia(updatedEntity, finalFile)

                if (latestSettings.notifyCompletedDownloads) {
                    notificationHelper.notifyDownloadCompleted(updatedEntity)
                } else {
                    notificationHelper.cancelProgressNotification(downloadId)
                }

                systemDao.insertNotification(
                    AppNotificationEntity(
                        category = "DOWNLOAD",
                        title = "Download Completed",
                        message = "${updatedEntity.fileName} (${FormatUtils.formatBytes(finalSize)})",
                        actionTarget = "home"
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val current = downloadDao.getById(downloadId) ?: return
            if (current.status == "PAUSED" || current.status == "CANCELLED") return
            val reason = e.localizedMessage ?: "Network or storage I/O error"
            if (latestSettings.autoRetryOnFailure && current.retryCount < 2) {
                downloadDao.update(
                    current.copy(
                        status = "QUEUED",
                        retryCount = current.retryCount + 1,
                        errorMessage = reason,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                delay(2000L)
            } else {
                failDownload(current, reason)
            }
        } finally {
            activeJobs.remove(downloadId)
            updateActiveCountAndService()
            pumpQueue()
        }
    }

    private suspend fun failDownload(entity: DownloadEntity, reason: String) {
        downloadDao.updateStatus(entity.id, "FAILED", reason)
        downloadDao.removeFromQueue(entity.id)
        downloadDao.insertHistory(
            DownloadHistoryEntity(
                downloadId = entity.id,
                url = entity.url,
                title = entity.title,
                fileName = entity.fileName,
                fileSize = entity.totalBytes.coerceAtLeast(entity.downloadedBytes),
                mimeType = entity.mimeType,
                platform = entity.platform,
                status = "FAILED"
            )
        )
        if (latestSettings.notifyFailedDownloads) {
            notificationHelper.notifyDownloadFailed(entity, reason)
        }
        systemDao.insertNotification(
            AppNotificationEntity(
                category = "DOWNLOAD",
                title = "Download Failed: ${entity.fileName}",
                message = reason,
                actionTarget = "home"
            )
        )
    }

    private suspend fun registerCompletedMedia(entity: DownloadEntity, file: File) {
        val cat = runCatching { MediaCategory.valueOf(entity.mediaType) }.getOrDefault(MediaCategory.OTHER)
        val galleryType = when (cat) {
            MediaCategory.IMAGE -> "PHOTO"
            MediaCategory.VIDEO -> "VIDEO"
            MediaCategory.AUDIO -> "AUDIO"
            else -> return // Only media files belong in Gallery/Player downloaded collections
        }

        var durationMs = 0L
        var width = 0
        var height = 0
        if (galleryType == "VIDEO" || galleryType == "AUDIO") {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                retriever.release()
            }
        }

        val uriStr = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
        galleryDao.insertMedia(
            DownloadedMediaEntity(
                downloadId = entity.id,
                title = entity.title,
                fileName = entity.fileName,
                filePath = file.absolutePath,
                contentUri = uriStr,
                mediaType = galleryType,
                mimeType = entity.mimeType,
                sizeBytes = file.length(),
                durationMs = durationMs,
                width = width,
                height = height
            )
        )
    }

    private fun canDownloadOnCurrentNetwork(wifiOnly: Boolean): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false
        if (wifiOnly) {
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        return true
    }

    private fun hasEnoughStorage(dir: File, requiredBytes: Long): Boolean {
        return runCatching {
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes > requiredBytes + (10 * 1024 * 1024L)
        }.getOrDefault(true)
    }

    private suspend fun checkLowStorageWarning(dir: File, notifyEnabled: Boolean) {
        if (!notifyEnabled) return
        val available = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (available < LOW_STORAGE_THRESHOLD_BYTES) {
            val now = System.currentTimeMillis()
            if (now - lastStorageWarningAtMs >= STORAGE_WARNING_COOLDOWN_MS) {
                lastStorageWarningAtMs = now
                notificationHelper.notifyStorageWarning(available)
                systemDao.insertNotification(
                    AppNotificationEntity(
                        category = "STORAGE",
                        title = "Low Storage Warning",
                        message = "Only ${FormatUtils.formatBytes(available)} free on device storage.",
                        actionTarget = "notifications"
                    )
                )
            }
        }
    }

    private fun startForegroundServiceIfNeeded() {
        runCatching {
            val intent = Intent(context, DownloadForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun updateActiveCountAndService() {
        // Let DownloadForegroundService debounce and stop itself gracefully after verifying DAO idle state.
        _activeCount.value = activeJobs.size
    }
}
