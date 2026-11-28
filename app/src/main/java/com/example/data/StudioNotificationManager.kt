package com.example.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

class StudioNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_TASKS = "tkstudio_tasks"
        const val CHANNEL_SECURITY = "tkstudio_security"
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val taskChannel = NotificationChannel(
                CHANNEL_TASKS,
                "TK Studio Builds & CI",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Completion updates for builds, remote GitHub Actions runs, artifacts, and backups."
            }
            val secChannel = NotificationChannel(
                CHANNEL_SECURITY,
                "TK Studio Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Secret scanner and archive safety warnings."
            }
            mgr.createNotificationChannel(taskChannel)
            mgr.createNotificationChannel(secChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun postTaskNotification(
        id: Int,
        title: String,
        message: String,
        deepLinkRoute: String = "home",
        isSecurityAlert: Boolean = false
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("tkstudio://$deepLinkRoute")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channelId = if (isSecurityAlert) CHANNEL_SECURITY else CHANNEL_TASKS
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(if (isSecurityAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
