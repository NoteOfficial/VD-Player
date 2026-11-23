package com.tkofficial.videodownloder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tkofficial.videodownloder.core.DownloaderVaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(NotificationHelper.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return

        val engine = (context.applicationContext as? DownloaderVaApp)?.container?.downloadEngine ?: return
        val job = when (intent.action) {
            NotificationHelper.ACTION_PAUSE -> engine.pauseDownload(downloadId)
            NotificationHelper.ACTION_RESUME -> engine.resumeDownload(downloadId)
            NotificationHelper.ACTION_CANCEL -> engine.cancelDownload(downloadId)
            NotificationHelper.ACTION_RETRY -> engine.retryDownload(downloadId)
            else -> null
        } ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                job.join()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

