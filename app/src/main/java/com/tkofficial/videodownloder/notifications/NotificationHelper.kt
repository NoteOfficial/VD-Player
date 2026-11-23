package com.tkofficial.videodownloder.notifications

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
import androidx.core.content.FileProvider
import com.example.R
import com.tkofficial.videodownloder.MainActivity
import com.tkofficial.videodownloder.database.DownloadEntity
import com.tkofficial.videodownloder.utilities.FormatUtils
import java.io.File

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ACTIVE = "va_downloads_active"
        const val CHANNEL_COMPLETE = "va_downloads_complete"
        const val CHANNEL_PLAYBACK = "va_media_playback"
        const val CHANNEL_ALERTS = "va_security_alerts"

        const val GROUP_DOWNLOADS = "com.tkofficial.videodownloder.DOWNLOADS_GROUP"
        const val SUMMARY_NOTIFICATION_ID = 1000
        const val FOREGROUND_SERVICE_ID = 1001

        const val ACTION_PAUSE = "com.tkofficial.videodownloder.ACTION_PAUSE"
        const val ACTION_RESUME = "com.tkofficial.videodownloder.ACTION_RESUME"
        const val ACTION_CANCEL = "com.tkofficial.videodownloder.ACTION_CANCEL"
        const val ACTION_RETRY = "com.tkofficial.videodownloder.ACTION_RETRY"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val active = NotificationChannel(
                CHANNEL_ACTIVE,
                context.getString(R.string.notif_channel_active),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live download progress and foreground service"
                setShowBadge(false)
            }

            val complete = NotificationChannel(
                CHANNEL_COMPLETE,
                context.getString(R.string.notif_channel_complete),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Completed and failed download alerts"
            }

            val playback = NotificationChannel(
                CHANNEL_PLAYBACK,
                context.getString(R.string.notif_channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio and video playback controls"
            }

            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Security, vault, and storage alerts"
            }

            manager.createNotificationChannels(listOf(active, complete, playback, alerts))
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun buildForegroundSummaryNotification(activeCount: Int): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_downloads_summary, activeCount))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_DOWNLOADS)
            .setGroupSummary(true)
            .setContentIntent(openIntent)
            .build()
    }

    fun notifyDownloadProgress(entity: DownloadEntity) {
        if (!hasNotificationPermission()) return
        val total = entity.totalBytes
        val downloaded = entity.downloadedBytes
        val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
        val indeterminate = total <= 0L

        val isPaused = entity.status == "PAUSED"
        val sizeText = if (total > 0L) {
            "${FormatUtils.formatBytes(downloaded)} / ${FormatUtils.formatBytes(total)}"
        } else {
            FormatUtils.formatBytes(downloaded)
        }
        val pausedText = context.getString(R.string.status_paused)
        val statusLine = if (isPaused) {
            "$pausedText • $sizeText"
        } else {
            val etaText = context.getString(R.string.eta_prefix, FormatUtils.formatEta(entity.etaSeconds))
            "$percent% • $sizeText • ${FormatUtils.formatSpeed(entity.speedBytesPerSec)} • $etaText"
        }

        val openIntent = PendingIntent.getActivity(
            context,
            entity.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_tab", "home")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(if (isPaused) android.R.drawable.ic_media_pause else android.R.drawable.stat_sys_download)
            .setContentTitle("${context.getString(R.string.app_name)} • ${entity.fileName}")
            .setContentText(statusLine)
            .setSubText(if (isPaused) pausedText else "$percent%")
            .setProgress(100, percent, indeterminate && !isPaused)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_DOWNLOADS)
            .setContentIntent(openIntent)

        if (isPaused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.action_resume),
                actionPendingIntent(ACTION_RESUME, entity.id, 1)
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.action_pause),
                actionPendingIntent(ACTION_PAUSE, entity.id, 2)
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(R.string.action_cancel),
            actionPendingIntent(ACTION_CANCEL, entity.id, 3)
        )

        try {
            NotificationManagerCompat.from(context).notify(notificationIdFor(entity.id), builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun notifyDownloadCompleted(entity: DownloadEntity) {
        if (!hasNotificationPermission()) return
        cancelProgressNotification(entity.id)

        val file = File(entity.localPath)
        val openIntent = PendingIntent.getActivity(
            context,
            notificationIdFor(entity.id),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_download_id", entity.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notif_download_completed))
            .setContentText("${entity.fileName} (${FormatUtils.formatBytes(entity.totalBytes.coerceAtLeast(entity.downloadedBytes))})")
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_view, context.getString(R.string.action_open), openIntent)

        if (file.exists()) {
            val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = entity.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val sharePending = PendingIntent.getActivity(
                context,
                notificationIdFor(entity.id) + 50000,
                Intent.createChooser(shareIntent, context.getString(R.string.action_share)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_share, context.getString(R.string.action_share), sharePending)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationIdFor(entity.id) + 10000, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun notifyDownloadFailed(entity: DownloadEntity, reason: String) {
        if (!hasNotificationPermission()) return
        cancelProgressNotification(entity.id)

        val detailsIntent = PendingIntent.getActivity(
            context,
            notificationIdFor(entity.id) + 20000,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_tab", "home")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("${context.getString(R.string.notif_download_failed)} • ${entity.fileName}")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
            .setContentIntent(detailsIntent)
            .addAction(
                android.R.drawable.ic_menu_rotate,
                context.getString(R.string.action_retry),
                actionPendingIntent(ACTION_RETRY, entity.id, 4)
            )
            .addAction(
                android.R.drawable.ic_menu_info_details,
                context.getString(R.string.action_details),
                detailsIntent
            )

        try {
            NotificationManagerCompat.from(context).notify(notificationIdFor(entity.id) + 20000, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun notifyStorageWarning(availableBytes: Long) {
        if (!hasNotificationPermission()) return
        val openIntent = PendingIntent.getActivity(
            context,
            30001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_tab", "notifications")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val formatted = FormatUtils.formatBytes(availableBytes.coerceAtLeast(0L))
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notif_storage_warning_title))
            .setContentText(context.getString(R.string.notif_storage_warning_body, formatted))
            .setAutoCancel(true)
            .setContentIntent(openIntent)

        try {
            NotificationManagerCompat.from(context).notify(30001, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun cancelProgressNotification(downloadId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(downloadId))
    }

    private fun notificationIdFor(downloadId: Long): Int = 2000 + (downloadId % 8000).toInt()

    private fun actionPendingIntent(action: String, downloadId: Long, salt: Int): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getBroadcast(
            context,
            (downloadId * 10 + salt).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
