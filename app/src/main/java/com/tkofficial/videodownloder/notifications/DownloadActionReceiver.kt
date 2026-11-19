package com.tkofficial.videodownloder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tkofficial.videodownloder.core.DownloaderVaApp

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(NotificationHelper.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return

        val engine = (context.applicationContext as? DownloaderVaApp)?.container?.downloadEngine ?: return
        when (intent.action) {
            NotificationHelper.ACTION_PAUSE -> engine.pauseDownload(downloadId)
            NotificationHelper.ACTION_RESUME -> engine.resumeDownload(downloadId)
            NotificationHelper.ACTION_CANCEL -> engine.cancelDownload(downloadId)
            NotificationHelper.ACTION_RETRY -> engine.retryDownload(downloadId)
        }
    }
}
