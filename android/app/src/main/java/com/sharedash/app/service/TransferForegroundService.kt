package com.sharedash.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sharedash.app.MainActivity
import com.sharedash.app.ShareDashApplication

class TransferForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_UPDATE_PROGRESS

        if (action == ACTION_TRANSFER_COMPLETE) {
            val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Transfer Complete"
            showCompletedNotification(title)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Transferring Files"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val speedText = intent?.getStringExtra(EXTRA_SPEED) ?: "Multipath Accelerating..."

        val notification = buildNotification(title, progress, speedText)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.message}")
        }

        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ShareDash::TransferWakeLock"
            )?.apply {
                acquire(60 * 60 * 1000L) // 1 hour max safety timeout
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager?.createWifiLock(lockMode, "ShareDash::TransferWifiLock")?.apply {
                acquire()
            }
            Log.i(TAG, "🔒 WakeLock & High-Perf WifiLock acquired for background transfer")
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring locks: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            Log.i(TAG, "🔓 WakeLock & WifiLock released")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing locks: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseLocks()
    }

    private fun buildNotification(title: String, progress: Int, speedText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, ShareDashApplication.CHANNEL_TRANSFERS)
            .setContentTitle("ShareDash — $title")
            .setContentText(speedText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showCompletedNotification(title: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ShareDashApplication.CHANNEL_TRANSFERS)
            .setContentTitle("ShareDash — $title")
            .setContentText("✔ File transfer completed successfully!")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID + 1, notification)
    }

    companion object {
        private const val TAG = "TransferForegroundSvc"
        const val NOTIFICATION_ID = 1001
        const val ACTION_UPDATE_PROGRESS = "com.sharedash.app.ACTION_UPDATE_PROGRESS"
        const val ACTION_TRANSFER_COMPLETE = "com.sharedash.app.ACTION_TRANSFER_COMPLETE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED = "extra_speed"

        fun updateProgress(context: Context, title: String, progress: Int, speedText: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_SPEED, speedText)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start service: ${e.message}")
            }
        }

        fun complete(context: Context, title: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_TRANSFER_COMPLETE
                putExtra(EXTRA_TITLE, title)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
