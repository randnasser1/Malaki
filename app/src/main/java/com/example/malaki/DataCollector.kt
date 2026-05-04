package com.example.malaki

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.example.malaki.db.EventRepository
import com.example.malaki.security.ContentSafetyManager
import kotlinx.coroutines.runBlocking
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DataCollector(private val context: Context) {

    private val repository = EventRepository(context)

    companion object {
        private const val TAG = "DataCollector"
        private const val APP_USAGE_FILE = "app_usage.json"
        private const val MESSAGES_FILE = "messages.json"
        private const val MUSIC_FILE = "music_data.json"
    }

    fun saveMusicNotificationData() {
        Log.d(TAG, "🎵 Checking music data...")

        Thread {
            try {
                val musicFile = File(context.filesDir, MUSIC_FILE)
                if (!musicFile.exists() || musicFile.length() == 0L) {
                    Log.d(TAG, "📭 No music data found")
                    return@Thread
                }

                // Read existing music data
                val musicArray = try {
                    JSONArray(musicFile.readText())
                } catch (e: Exception) {
                    JSONArray()
                }

                if (musicArray.length() == 0) {
                    Log.d(TAG, "📭 No music entries")
                    return@Thread
                }

                Log.d(TAG, "Found ${musicArray.length()} music entries")

                // Write directly to Firestore
                saveMusicDataToFirestore(musicArray)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing music data: ${e.message}")
            }
        }.start()
    }

    private fun convertOldToNewFormat(oldNotification: JSONObject): JSONObject {
        val timestamp = oldNotification.optLong("timestamp", System.currentTimeMillis())

        return JSONObject().apply {
            put("timestamp", timestamp)
            put("datetime", oldNotification.optString("date_time",
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(timestamp))))
            put("package", oldNotification.optString("music_app", ""))
            put("app", oldNotification.optString("app_name", ""))
            put("title", oldNotification.optString("title", ""))
            put("text", oldNotification.optString("text", ""))
            put("big_text", oldNotification.optString("big_text", ""))
            put("sub_text", "")

            // Parse track info from old format
            val parsedTrack = oldNotification.optJSONObject("parsed_track")
            val artist = parsedTrack?.optString("artist", "") ?: ""
            val track = parsedTrack?.optString("track", "") ?: ""

            put("track_info", JSONObject().apply {
                put("artist", if (artist.isNotBlank()) artist else "Unknown Artist")
                put("track", if (track.isNotBlank()) track else "Unknown Track")
                put("state", if (oldNotification.optBoolean("is_playing", false)) "playing" else "unknown")
                put("parsed_from", "old_format")
            })

            put("is_playing", oldNotification.optBoolean("is_playing", false))
        }
    }
    // Add this function to DataCollector class
    fun saveWellbeingResponse(score: Int, answers: String) {
        GlobalScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch
                val firestore = FirebaseFirestore.getInstance()
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                val data = hashMapOf(
                    "childId" to currentUser.uid,
                    "date" to today,
                    "timestamp" to System.currentTimeMillis(),
                    "score" to score,
                    "answers" to answers
                )

                // Just add a new document each time
                firestore.collection("wellbeing_daily_summary").add(data).await()
                Log.d(TAG, "✅ Wellbeing saved: score=$score")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save wellbeing: ${e.message}")
            }
        }
    }
    // Save App Usage Data
    fun saveAppUsageData() {
        Log.d(TAG, "📱 Saving app usage data...")
        // Keep your existing implementation
        Thread {
            try {
                val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return@Thread

                val calendar = Calendar.getInstance()
                val endTime = calendar.timeInMillis
                calendar.add(Calendar.HOUR, -24)
                val startTime = calendar.timeInMillis

                val statsList = usageManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                ) ?: return@Thread

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = dateFormat.format(Date())

                val usageArray = JSONArray()
                var totalTime = 0L

                statsList.forEach { appStats ->
                    val timeUsed = appStats.totalTimeInForeground
                    if (timeUsed > 10000) {
                        totalTime += timeUsed

                        val appUsage = JSONObject().apply {
                            put("package", appStats.packageName)
                            put("time_ms", timeUsed)
                            put("time_min", timeUsed / 60000)
                            put("last_used", appStats.lastTimeUsed)

                            try {
                                val appInfo = context.packageManager.getApplicationInfo(appStats.packageName, 0)
                                put("app_name", context.packageManager.getApplicationLabel(appInfo).toString())
                            } catch (e: Exception) {
                                put("app_name", appStats.packageName)
                            }
                        }
                        usageArray.put(appUsage)
                    }
                }

                val todayEntry = JSONObject().apply {
                    put("date", today)
                    put("timestamp", System.currentTimeMillis())
                    put("total_time_min", totalTime / 60000)
                    put("app_count", usageArray.length())
                    put("apps", usageArray)
                }

                appendToFileWithDedup(APP_USAGE_FILE, todayEntry, "date")
                
                // Write to Firestore
                saveAppUsageToFirestore(todayEntry)
                
                Log.d(TAG, "✅ App usage saved")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving app usage: ${e.message}")
            }
        }.start()
    }

    private fun saveAppUsageToFirestore(todayEntry: JSONObject) {
        GlobalScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch

                val firestore = FirebaseFirestore.getInstance()
                
                val appUsageData = mapOf(
                    "childId" to currentUser.uid,
                    "date" to todayEntry.getString("date"),
                    "timestamp" to todayEntry.getLong("timestamp"),
                    "totalTimeMin" to todayEntry.getLong("total_time_min"),
                    "appCount" to todayEntry.getInt("app_count"),
                    "apps" to parseJsonArrayToList(todayEntry.getJSONArray("apps"))
                )
                
                // Use childId_date as doc ID so multiple children don't overwrite each other
                val docId = "${currentUser.uid}_${todayEntry.getString("date")}"
                firestore.collection("app_usage")
                    .document(docId)
                    .set(appUsageData)
                    .await()

                // Mirror to Room for backend ML analysis
                repository.ensureDeviceProfile()
                repository.captureEvent(
                    eventType = "APP_USAGE",
                    rawText = todayEntry.toString(),
                    textPreview = "AppUsage ${todayEntry.getString("date")}: ${todayEntry.getLong("total_time_min")} min",
                    timestampUtc = todayEntry.getLong("timestamp")
                )

                Log.d(TAG, "✅ App usage written to Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error writing app usage to Firestore: ${e.message}")
            }
        }
    }

    private fun saveMusicDataToFirestore(musicArray: JSONArray) {
        GlobalScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch

                val firestore = FirebaseFirestore.getInstance()

                // Parse music array to list of maps - FIXED: Properly convert JSONObject
                val musicList = mutableListOf<Map<String, Any>>()
                for (i in 0 until musicArray.length()) {
                    val obj = musicArray.getJSONObject(i)
                    val map = mutableMapOf<String, Any>()
                    obj.keys().forEach { key ->
                        val value = obj.get(key)
                        when (value) {
                            is JSONObject -> {
                                // Convert nested JSONObject to Map
                                val nestedMap = mutableMapOf<String, Any>()
                                value.keys().forEach { nestedKey ->
                                    nestedMap[nestedKey] = value.get(nestedKey)
                                }
                                map[key] = nestedMap
                            }
                            is JSONArray -> {
                                // Convert JSONArray to List
                                val list = mutableListOf<Any>()
                                for (j in 0 until value.length()) {
                                    list.add(value.get(j))
                                }
                                map[key] = list
                            }
                            else -> map[key] = value
                        }
                    }
                    musicList.add(map)
                }

                val musicData = mapOf(
                    "childId" to currentUser.uid,
                    "timestamp" to System.currentTimeMillis(),
                    "entries" to musicList
                )

                firestore.collection("music_tracking")
                    .add(musicData)
                    .await()

                Log.d(TAG, "✅ Music data written to Firestore with ${musicList.size} entries")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error writing music data to Firestore: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Save Messages
    fun saveMessages() {
        Log.d(TAG, "💬 Saving messages...")
        Thread {
            try {
                val messagesFile = File(context.filesDir, "messages.txt")
                if (!messagesFile.exists()) {
                    Log.d(TAG, "📭 No messages.txt found")
                    return@Thread
                }

                val messages = messagesFile.readLines().filter { it.isNotBlank() }
                if (messages.isEmpty()) return@Thread

                val now = System.currentTimeMillis()
                val entry = JSONObject().apply {
                    put("timestamp", now)
                    put("date", SimpleDateFormat("yyyy-MM-dd").format(Date()))
                    put("message_count", messages.size)
                    val messagesArray = JSONArray()
                    messages.takeLast(50).forEach { messagesArray.put(it) }
                    put("messages", messagesArray)
                }

                val file = File(context.filesDir, MESSAGES_FILE)
                val existingArray = if (file.exists() && file.length() > 0) {
                    JSONArray(file.readText())
                } else {
                    JSONArray()
                }
                existingArray.put(entry)

                if (existingArray.length() > 100) {
                    val trimmed = JSONArray()
                    for (i in existingArray.length() - 100 until existingArray.length()) {
                        trimmed.put(existingArray.getJSONObject(i))
                    }
                    file.writeText(trimmed.toString(2))
                } else {
                    file.writeText(existingArray.toString(2))
                }

                // Write each message to Room so the backend ML pipeline processes them
                runBlocking {
                    repository.ensureDeviceProfile()
                    messages.takeLast(50).forEach { msg ->
                        repository.captureEvent(
                            eventType = "MESSAGE",
                            rawText = msg,
                            textPreview = msg.take(100),
                            timestampUtc = now
                        )
                    }
                }

                Log.d(TAG, "✅ Messages saved (${messages.size} written to Room for ML)")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving messages: ${e.message}")
            }
        }.start()
    }

    private fun appendToFileWithDedup(fileName: String, newEntry: JSONObject, dedupKey: String) {
        try {
            val file = File(context.filesDir, fileName)
            val existingArray = if (file.exists() && file.length() > 0) {
                JSONArray(file.readText())
            } else {
                JSONArray()
            }

            val newKey = newEntry.getString(dedupKey)
            var found = false

            for (i in 0 until existingArray.length()) {
                val existing = existingArray.getJSONObject(i)
                if (existing.getString(dedupKey) == newKey) {
                    existingArray.put(i, newEntry)
                    found = true
                    break
                }
            }

            if (!found) {
                existingArray.put(newEntry)
            }

            if (existingArray.length() > 100) {
                val trimmed = JSONArray()
                for (i in existingArray.length() - 100 until existingArray.length()) {
                    trimmed.put(existingArray.getJSONObject(i))
                }
                file.writeText(trimmed.toString(2))
            } else {
                file.writeText(existingArray.toString(2))
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in appendToFileWithDedup: ${e.message}")
        }
    }

    // Get all data for export
    fun getAllData(): JSONObject {
        return JSONObject().apply {
            try {
                put("export_timestamp", System.currentTimeMillis())
                put("export_date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()))
                put("device", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.RELEASE)

                // App Usage
                val appUsageFile = File(context.filesDir, APP_USAGE_FILE)
                put("app_usage", if (appUsageFile.exists()) {
                    JSONArray(appUsageFile.readText())
                } else {
                    JSONArray()
                })

                // Messages
                val messagesFile = File(context.filesDir, MESSAGES_FILE)
                put("messages", if (messagesFile.exists()) {
                    JSONArray(messagesFile.readText())
                } else {
                    JSONArray()
                })

                // Music Data
                val musicFile = File(context.filesDir, MUSIC_FILE)
                put("music_data", if (musicFile.exists()) {
                    JSONArray(musicFile.readText())
                } else {
                    JSONArray()
                })

            } catch (e: Exception) {
                put("error", e.message ?: "Unknown error")
            }
        }
    }
    private val contentSafetyManager = ContentSafetyManager(context)

    // Add this function
    fun analyzeUrlAndSave(url: String) {
        Thread {
            try {
                val result = runBlocking {
                    contentSafetyManager.analyzeUrl(url)
                }

                if (!result.isSafe) {
                    // Save risk alert to Firebase
                    saveRiskAlert(url, result)

                    // Also save locally
                    saveUrlAnalysis(url, result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing URL: ${e.message}")
            }
        }.start()
    }

    private fun saveRiskAlert(url: String, result: ContentSafetyManager.SafetyResult) {
        val alert = JSONObject().apply {
            put("url", url)
            put("timestamp", System.currentTimeMillis())
            put("riskLevel", result.riskLevel.name)
            put("blockReasons", JSONArray(result.blockReasons))
            put("confidenceScore", result.confidenceScore)
        }

        val alertsFile = File(context.filesDir, "risk_alerts.json")
        val existingArray = if (alertsFile.exists() && alertsFile.length() > 0) {
            try {
                JSONArray(alertsFile.readText())
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }

        existingArray.put(alert)

        // Keep last 500 alerts
        if (existingArray.length() > 500) {
            val trimmed = JSONArray()
            for (i in existingArray.length() - 500 until existingArray.length()) {
                trimmed.put(existingArray.getJSONObject(i))
            }
            alertsFile.writeText(trimmed.toString(2))
        } else {
            alertsFile.writeText(existingArray.toString(2))
        }
        
        // Write to Firestore
        saveRiskAlertToFirestore(url, result)
    }

    private fun saveRiskAlertToFirestore(url: String, result: ContentSafetyManager.SafetyResult) {
        GlobalScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch

                val firestore = FirebaseFirestore.getInstance()

                // Store ONLY risk assessment data, NO URL or message content
                val assessmentData = mapOf(
                    "childId" to currentUser.uid,
                    "timestamp" to System.currentTimeMillis(),
                    "riskLevel" to result.riskLevel.name,
                    "confidenceScore" to result.confidenceScore,
                    "isAlertTriggered" to true,
                    "sourceType" to "URL"  // Just the type, not the actual URL
                    // ⚠️ NO "url" field - that stays local only
                )

                firestore.collection("risk_assessment")  // Note: renamed from risk_reports
                    .add(assessmentData)
                    .await()

                // URL stays LOCAL only (encrypted in Room, never sent to cloud)
                repository.ensureDeviceProfile()
                repository.captureEvent(
                    eventType = "URL",
                    rawText = url,  // Stays encrypted in local Room database
                    textPreview = url.take(100),
                    timestampUtc = System.currentTimeMillis()
                )

                Log.d(TAG, "✅ Risk assessment stored in Firestore (URL kept private locally)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error writing risk assessment to Firestore: ${e.message}")
            }
        }
    }

    private fun saveUrlAnalysis(url: String, result: ContentSafetyManager.SafetyResult) {
        val analysisFile = File(context.filesDir, "url_analysis.json")
        val existingArray = if (analysisFile.exists() && analysisFile.length() > 0) {
            try {
                JSONArray(analysisFile.readText())
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }

        val entry = JSONObject().apply {
            put("url", url)
            put("timestamp", System.currentTimeMillis())
            put("isSafe", result.isSafe)
            put("riskLevel", result.riskLevel.name)
            put("blockReasons", JSONArray(result.blockReasons))
        }

        existingArray.put(entry)

        // Keep last 1000 analyses
        if (existingArray.length() > 1000) {
            val trimmed = JSONArray()
            for (i in existingArray.length() - 1000 until existingArray.length()) {
                trimmed.put(existingArray.getJSONObject(i))
            }
            analysisFile.writeText(trimmed.toString(2))
        } else {
            analysisFile.writeText(existingArray.toString(2))
        }
    }
    // ========== NEW BROWSER HISTORY FUNCTIONS ==========

    fun getBrowserHistory(limit: Int = 50): List<String> {
        val browserFile = File(context.filesDir, "browser_history.txt")
        if (!browserFile.exists()) return emptyList()

        return browserFile.readLines()
            .takeLast(limit)
            .mapNotNull { line ->
                val urlMatch = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+").find(line)
                urlMatch?.value
            }
            .distinct()
    }
    // Add this function for incremental app usage (last 5 minutes)
    fun saveAppUsageDataIncremental() {
        Thread {
            try {
                val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return@Thread

                // Get LAST 5 MINUTES only
                val calendar = Calendar.getInstance()
                val endTime = calendar.timeInMillis
                calendar.add(Calendar.MINUTE, -5)
                val startTime = calendar.timeInMillis

                val statsList = usageManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                ) ?: return@Thread

                if (statsList.isEmpty()) return@Thread

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = dateFormat.format(Date())

                val usageArray = JSONArray()
                var totalTime = 0L

                statsList.forEach { appStats ->
                    val timeUsed = appStats.totalTimeInForeground
                    if (timeUsed > 1000) {  // More than 1 second
                        totalTime += timeUsed

                        val appUsage = JSONObject().apply {
                            put("package", appStats.packageName)
                            put("time_ms", timeUsed)
                            put("time_min", timeUsed / 60000)
                            put("last_used", appStats.lastTimeUsed)

                            try {
                                val appInfo = context.packageManager.getApplicationInfo(appStats.packageName, 0)
                                put("app_name", context.packageManager.getApplicationLabel(appInfo).toString())
                            } catch (e: Exception) {
                                put("app_name", appStats.packageName)
                            }
                        }
                        usageArray.put(appUsage)
                    }
                }

                if (usageArray.length() == 0) return@Thread

                // Update cumulative usage for today
                updateOrCreateTodayUsage(today, totalTime, usageArray)

                Log.d(TAG, "✅ Incremental app usage saved (last 5 min)")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving incremental app usage: ${e.message}")
            }
        }.start()
    }

    private fun updateOrCreateTodayUsage(date: String, additionalTimeMs: Long, newApps: JSONArray) {
        GlobalScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch

                val firestore = FirebaseFirestore.getInstance()
                val docId = "${currentUser.uid}_$date"
                val docRef = firestore.collection("app_usage").document(docId)

                val existingDoc = docRef.get().await()

                if (existingDoc.exists()) {
                    // Update existing document
                    val existingTotal = existingDoc.getLong("totalTimeMin") ?: 0L
                    val newTotalMin = (existingTotal + (additionalTimeMs / 60000))

                    @Suppress("UNCHECKED_CAST")
                    val existingApps = existingDoc.get("apps") as? List<Map<String, Any>> ?: emptyList()
                    val mergedApps = mergeAppLists(existingApps, newApps)

                    docRef.update(
                        mapOf(
                            "totalTimeMin" to newTotalMin,
                            "apps" to mergedApps,
                            "lastUpdated" to System.currentTimeMillis()
                        )
                    ).await()
                } else {
                    // Create new document
                    val appUsageData = mapOf(
                        "childId" to currentUser.uid,
                        "date" to date,
                        "timestamp" to System.currentTimeMillis(),
                        "totalTimeMin" to (additionalTimeMs / 60000),
                        "appCount" to newApps.length(),
                        "apps" to parseJsonArrayToList(newApps)
                    )
                    docRef.set(appUsageData).await()
                }

                // Also store in Room for ML pipeline
                repository.ensureDeviceProfile()
                repository.captureEvent(
                    eventType = "APP_USAGE",
                    rawText = "Incremental update for $date",
                    textPreview = "App usage updated",
                    timestampUtc = System.currentTimeMillis()
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error updating app usage: ${e.message}")
            }
        }
    }

    private fun mergeAppLists(existingApps: List<Map<String, Any>>, newApps: JSONArray): List<Map<String, Any>> {
        val merged = mutableMapOf<String, MutableMap<String, Any>>()

        // Add existing apps
        existingApps.forEach { app ->
            val name = app["app_name"] as? String ?: return@forEach
            merged[name] = app.toMutableMap()
        }

        // Add/update with new apps
        for (i in 0 until newApps.length()) {
            val newApp = newApps.getJSONObject(i)
            val name = newApp.getString("app_name")
            val additionalTime = newApp.getLong("time_min")

            if (merged.containsKey(name)) {
                val existing = merged[name]!!
                val existingTime = (existing["time_min"] as? Long) ?: 0L
                existing["time_min"] = existingTime + additionalTime
                existing["time_ms"] = (existingTime + additionalTime) * 60000
            } else {
                merged[name] = mapOf(
                    "app_name" to name,
                    "package" to newApp.getString("package"),
                    "time_min" to additionalTime,
                    "time_ms" to additionalTime * 60000,
                    "last_used" to newApp.getLong("last_used")
                ).toMutableMap()
            }
        }

        return merged.values.toList()
    }
    fun getRecentBrowserHistory(limit: Int = 20): List<BrowserHistoryEntry> {
        val browserFile = File(context.filesDir, "browser_history.txt")
        if (!browserFile.exists()) return emptyList()

        val entries = mutableListOf<BrowserHistoryEntry>()
        val lines = browserFile.readLines().takeLast(limit)

        val regex = Regex("\\[(.*?)\\] \\[(.*?)\\] \\[BROWSER\\] (.*)")

        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                entries.add(
                    BrowserHistoryEntry(
                        timestamp = parseTimestamp(match.groupValues[1]),
                        dateTime = match.groupValues[1],
                        packageName = match.groupValues[2],
                        url = match.groupValues[3]
                    )
                )
            }
        }

        return entries.sortedByDescending { it.timestamp }
    }

    private fun parseTimestamp(dateTime: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            format.parse(dateTime)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    // Function to analyze all unanalyzed browser URLs
    fun analyzeAllBrowserUrls() {
        val browserHistory = getRecentBrowserHistory(30)
        for (entry in browserHistory) {
            Log.d(TAG, "🌐 Analyzing browser URL: ${entry.url}")
            analyzeUrlAndSave(entry.url)
        }
    }
}

data class BrowserHistoryEntry(
    val timestamp: Long,
    val dateTime: String,
    val packageName: String,
    val url: String
)

// Helper function to parse JSONArray to List<Map>
private fun parseJsonArrayToList(array: JSONArray): List<Map<String, Any>> {
    val list = mutableListOf<Map<String, Any>>()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val map = mutableMapOf<String, Any>()
        obj.keys().forEach { key ->
            map[key] = obj.get(key) as Any
        }
        list.add(map)
    }
    return list
}
