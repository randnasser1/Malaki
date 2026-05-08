package com.example.malaki

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.malaki.db.BackendSyncManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.File

class BackgroundService : Service() {

    companion object {
        private const val TAG = "BackgroundService"
        private const val COLLECTION_INTERVAL_MINUTES = 2L  // Every 2 minutes (real-time)
        private const val SYNC_INTERVAL_SECONDS = 60L       // Every 60 seconds for ML
    }

    private val dataExecutor = Executors.newSingleThreadScheduledExecutor()
    private val syncExecutor = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ Background service created - REAL-TIME MODE ACTIVE")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ Background service started - collecting data every ${COLLECTION_INTERVAL_MINUTES} minutes")

        // Schedule DATA COLLECTION every 2 minutes
        dataExecutor.scheduleAtFixedRate({
            collectAllData()
        }, 0, COLLECTION_INTERVAL_MINUTES, TimeUnit.MINUTES)

        // Schedule BACKEND SYNC every 60 seconds for ML analysis
        syncExecutor.scheduleAtFixedRate({
            syncWithBackend()
        }, 30, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS)  // First sync after 30 sec

        return START_STICKY
    }

    private fun collectAllData() {
        Log.d(TAG, "🔄 Collecting data (real-time mode)...")

        try {
            val dataCollector = DataCollector(this)

            // Collect app usage (last 5 minutes only)
            dataCollector.saveAppUsageDataIncremental()  // You need to add this method

            // Collect messages (new since last collection)
            dataCollector.saveMessages()

            // Collect music (any new tracks)
            dataCollector.saveMusicNotificationData()

            // Analyze URLs from browser history
            dataCollector.analyzeAllBrowserUrls()

            // Analyze URLs from messages
            analyzeCollectedUrls()

            Log.d(TAG, "✅ Data collection completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Data collection failed: ${e.message}")
        }
    }

    private fun syncWithBackend() {
        GlobalScope.launch {
            val ok = BackendSyncManager(this@BackgroundService).syncPendingEvents()
            if (ok) Log.d(TAG, "✅ Backend sync completed")
            // failures already logged inside syncPendingEvents
        }
    }

    private fun analyzeCollectedUrls() {
        // messages.txt is no longer written (AccessibilityService sends directly to Room).
        // Delete any stale file left over from older builds so it stops being re-read.
        try {
            val messagesFile = File(filesDir, "messages.txt")
            if (messagesFile.exists()) {
                messagesFile.delete()
                Log.d(TAG, "🧹 Deleted stale messages.txt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting messages.txt: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dataExecutor.shutdown()
        syncExecutor.shutdown()
        Log.d(TAG, "⏹️ Background service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}