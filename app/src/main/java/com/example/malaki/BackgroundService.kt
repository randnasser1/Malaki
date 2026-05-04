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
        private const val SYNC_INTERVAL_MINUTES = 1L        // Every 1 minute for ML
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

        // Schedule BACKEND SYNC every 1 minute (for ML analysis)
        syncExecutor.scheduleAtFixedRate({
            syncWithBackend()
        }, 30, SYNC_INTERVAL_MINUTES, TimeUnit.SECONDS)  // First sync after 30 sec

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
        Log.d(TAG, "🔄 Syncing with backend ML pipeline...")

        GlobalScope.launch {
            try {
                val syncManager = BackendSyncManager(this@BackgroundService)
                syncManager.syncPendingEvents()  // Send unanalyzed events to backend
                Log.d(TAG, "✅ Backend sync completed")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Backend sync failed: ${e.message}")
            }
        }
    }

    private fun analyzeCollectedUrls() {
        try {
            val messagesFile = File(filesDir, "messages.txt")
            if (!messagesFile.exists()) {
                return
            }

            val lines = messagesFile.readLines()
            val urlRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+")

            val dataCollector = DataCollector(this)
            val analyzedUrls = mutableSetOf<String>()  // Track to avoid duplicates

            lines.forEach { line ->
                urlRegex.findAll(line).forEach { match ->
                    val url = match.value
                    if (!analyzedUrls.contains(url)) {
                        analyzedUrls.add(url)
                        Log.d(TAG, "🌐 Found new URL to analyze: ${url.take(50)}...")
                        dataCollector.analyzeUrlAndSave(url)  // Instant analysis
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing URLs: ${e.message}")
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