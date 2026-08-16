package com.sharedash.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ShareDashApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val transferChannel = NotificationChannel(
                CHANNEL_TRANSFERS,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for active multipath file transfers"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(transferChannel)
        }
    }

    companion object {
        const val CHANNEL_TRANSFERS = "sharedash_transfers"
    }
}
