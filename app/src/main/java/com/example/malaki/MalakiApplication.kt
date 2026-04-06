package com.example.malaki

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MalakiApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Create notification channels for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "malaki_alerts",
                "Malaki Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Safety alerts from Malaki"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}