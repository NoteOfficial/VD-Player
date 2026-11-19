package com.tkofficial.videodownloder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tkofficial.videodownloder.core.DownloaderVaApp

class ScheduledDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
    }

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) return Result.failure()
        val app = applicationContext as? DownloaderVaApp ?: return Result.failure()
        app.container.downloadEngine.triggerScheduledDownload(id)
        return Result.success()
    }
}
