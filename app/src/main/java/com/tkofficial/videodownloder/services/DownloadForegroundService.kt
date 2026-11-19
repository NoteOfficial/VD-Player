package com.tkofficial.videodownloder.services

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectorJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as? DownloaderVaApp ?: return START_NOT_STICKY
        val notifHelper = app.container.notificationHelper
        val count = app.container.downloadEngine.activeCount.value.coerceAtLeast(1)
        val notification = notifHelper.buildForegroundSummaryNotification(count)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.FOREGROUND_SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.FOREGROUND_SERVICE_ID, notification)
        }

        if (collectorJob == null || collectorJob?.isActive != true) {
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            collectorJob = serviceScope.launch {
                app.container.downloadEngine.activeCount.collect { active ->
                    if (active <= 0) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (notifHelper.hasNotificationPermission()) {
                        notifManager.notify(
                            NotificationHelper.FOREGROUND_SERVICE_ID,
                            notifHelper.buildForegroundSummaryNotification(active)
                        )
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collectorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
