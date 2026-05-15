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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.malaki.db.BackendSyncManager
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

                val prefs = context.getSharedPreferences("music_upload_prefs", android.content.Context.MODE_PRIVATE)
                val lastUploadTs = prefs.getLong("lastMusicUploadTimestamp", 0L)

                val fullArray = try {
                    JSONArray(musicFile.readText())
                } catch (e: Exception) {
                    JSONArray()
                }

                // Only collect entries newer than the last upload
                val newEntries = JSONArray()
                for (i in 0 until fullArray.length()) {
                    val entry = fullArray.getJSONObject(i)
                    if (entry.optLong("timestamp", 0L) > lastUploadTs) {
                        newEntries.put(entry)
                    }
                }

                if (newEntries.length() == 0) {
                    Log.d(TAG, "⏭️ No new music entries since last upload")
                    return@Thread
                }

                Log.d(TAG, "Found ${newEntries.length()} new music entries (${fullArray.length()} total in file)")

                saveMusicDataToFirestore(newEntries) {
                    // On success: record the upload time and clear the file
                    prefs.edit().putLong("lastMusicUploadTimestamp", System.currentTimeMillis()).apply()
                    try { musicFile.delete() } catch (_: Exception) {}
                    Log.d(TAG, "🗑️ Music cache cleared after upload")
                }

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
        GlobalScope.launch(Dispatchers.IO) {
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

                firestore.collection("wellbeing_daily_summary")
                    .document("${currentUser.uid}_$today")
                    .set(data).await()
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
        GlobalScope.launch(Dispatchers.IO) {
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

                val docId = "${currentUser.uid}_${todayEntry.getString("date")}"
                firestore.collection("app_usage")
                    .document(docId)
                    .set(appUsageData)
                    .await()

                // ❌ REMOVE THIS - DON'T send APP_USAGE to backend ML
                // repository.ensureDeviceProfile()
                // repository.captureEvent(
                //     eventType = "APP_USAGE",
                //     rawText = todayEntry.toString(),
                //     textPreview = "AppUsage ${todayEntry.getString("date")}: ${todayEntry.getLong("total_time_min")} min",
                //     timestampUtc = todayEntry.getLong("timestamp")
                // )

                Log.d(TAG, "✅ App usage written to Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error writing app usage to Firestore: ${e.message}")
            }
        }
    }

    private fun saveMusicDataToFirestore(musicArray: JSONArray, onSuccess: (() -> Unit)? = null) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch
                val firestore = FirebaseFirestore.getInstance()

                val entriesList = mutableListOf<Map<String, Any>>()
                for (i in 0 until musicArray.length()) {
                    val track = musicArray.getJSONObject(i)
                    val trackInfo = track.getJSONObject("track_info")
                    val timestamp = track.getLong("timestamp")
                    entriesList.add(
                        mapOf(
                            "track_info" to mapOf(
                                "artist" to trackInfo.optString("artist", "Unknown Artist"),
                                "track"  to trackInfo.optString("track",  "Unknown Track")
                            ),
                            "timestamp" to timestamp
                        )
                    )
                }

                val musicTrackingData = mapOf(
                    "childId"           to currentUser.uid,
                    "timestamp"         to System.currentTimeMillis(),
                    "entries"           to entriesList,
                    "emotion_processed" to false
                )
                firestore.collection("music_tracking")
                    .add(musicTrackingData)
                    .await()

                Log.d(TAG, "✅ Music batch written to music_tracking (${entriesList.size} tracks)")
                onSuccess?.invoke()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error writing music data: ${e.message}")
            }
        }
    }

    // Save Messages
    fun saveMessages() {
        // DISABLED - Messages are sent directly from AccessibilityService
        Log.d(TAG, "💬 saveMessages() is disabled - using real-time from service")
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
    // Add this to DataCollector.kt

    // Add this function to your current DataCollector.kt

    private val contentSafetyManager by lazy { ContentSafetyManager(context) }

    // ── URL analysis pipeline ──────────────────────────────────────────────────
    // sourceType: "BROWSER" (navigated in browser) or "MESSAGE" (found inside a message)
    //
    // Three-checkpoint pattern so Firestore always has a trace even if analysis fails:
    //   CHECKPOINT 1 — write stub immediately (proves URL was captured)
    //   CHECKPOINT 2 — run Jina + RapidAPI analysis
    //   CHECKPOINT 3 — update stub with full results (or mark failed)
    // Add this function - saves to url_safety for dashboard
    private fun saveUrlSafetyToFirestore(url: String, result: ContentSafetyManager.SafetyResult, sourceType: String = "BROWSER") {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch
                val firestore = FirebaseFirestore.getInstance()

                val domain = try {
                    java.net.URI(url).host?.replace("www.", "") ?: url
                } catch (e: Exception) { url }

                val safetyData = mapOf(
                    "childId" to currentUser.uid,
                    "url" to url,
                    "domain" to domain,
                    "riskLevel" to result.riskLevel.name,
                    "blockReasons" to result.blockReasons,
                    "confidenceScore" to result.confidenceScore,
                    "sourceType" to sourceType,
                    "timestamp" to System.currentTimeMillis()
                )

                firestore.collection("url_safety").add(safetyData).await()
                Log.d(TAG, "✅ URL safety saved: $domain (${result.riskLevel.name})")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving URL safety: ${e.message}")
            }
        }
    }
    // Your existing saveRiskAlert - remove the call to saveRiskAlertToFirestore
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

        if (existingArray.length() > 500) {
            val trimmed = JSONArray()
            for (i in existingArray.length() - 500 until existingArray.length()) {
                trimmed.put(existingArray.getJSONObject(i))
            }
            alertsFile.writeText(trimmed.toString(2))
        } else {
            alertsFile.writeText(existingArray.toString(2))
        }

        // Don't call saveRiskAlertToFirestore - it doesn't exist
    }
    // In DataCollector.kt - CHANGE THIS FUNCTION (remove 'suspend')
    fun analyzeUrlAndSave(url: String, sourceType: String = "MESSAGE") {
        Thread {
            var docId: String? = null
            try {
                // Check for existing analysis
                val alreadyProcessed = runBlocking {
                    checkExistingUrlAnalysis(url)
                }

                if (alreadyProcessed != null) {
                    val status = alreadyProcessed.getString("analysisStatus")
                    Log.d(TAG, "⏭️ URL already analyzed: $url (status=$status)")
                    return@Thread
                }

                docId = writeUrlStub(url, sourceType)
                Log.d(TAG, "📝 CP1 done: stub docId=$docId url=$url")

                val result = runBlocking {
                    contentSafetyManager.analyzeUrl(url)
                }
                Log.d(TAG, "✅ CP2 DONE: isSafe=${result.isSafe} level=${result.riskLevel}")

                updateUrlRecord(docId, url, result, sourceType)
                Log.d(TAG, "📝 CP3 done: Firestore record updated for $url")

            } catch (e: Exception) {
                Log.e(TAG, "❌ analyzeUrlAndSave failed for $url: ${e.message}")
                markUrlFailed(docId, e.message)
            }
        }.start()
    }    // Add this helper function to check for existing analysis
// Add this helper function to check for existing analysis
// Keep this as suspend - it's called with runBlocking
private suspend fun checkExistingUrlAnalysis(url: String): JSONObject? {
    return try {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val firestore = FirebaseFirestore.getInstance()
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

        val docs = firestore.collection("url_safety")
            .whereEqualTo("childId", uid)
            .whereEqualTo("url", url)
            .get()
            .await()

        for (doc in docs.documents) {
            val timestamp = doc.getLong("timestamp") ?: 0L
            if (timestamp > sevenDaysAgo) {
                val status = doc.getString("analysisStatus")
                if (status == "completed") {
                    Log.d(TAG, "Found existing analysis for $url with status=$status")
                    return JSONObject().apply {
                        put("analysisStatus", status)
                        put("riskLevel", doc.getString("riskLevel") ?: "UNKNOWN")
                    }
                }
            }
        }
        null
    } catch (e: Exception) {
        Log.e(TAG, "Error checking existing URL: ${e.message}")
        null
    }
}    // Update browser history analyzer
// Update this function - remove GlobalScope.launch
    fun analyzeAllBrowserUrls() {
        val browserHistory = getRecentBrowserHistory(30)
        for (entry in browserHistory) {
            Log.d(TAG, "🌐 Analyzing browser URL: ${entry.url}")
            analyzeUrlAndSave(entry.url, "BROWSER")  // Direct call, no GlobalScope.launch
        }
    }    // Write a "captured" stub immediately — ensures the url_safety collection always exists
    private fun writeUrlStub(url: String, sourceType: String): String? {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Log.e(TAG, "❌ writeUrlStub: no authenticated user")
                return null
            }
            var docId: String? = null
            runBlocking {
                val ref = FirebaseFirestore.getInstance()
                    .collection("url_safety")
                    .add(mapOf(
                        "childId"        to uid,
                        "url"            to url,
                        "domain"         to extractDomain(url),
                        "sourceType"     to sourceType,
                        "timestamp"      to System.currentTimeMillis(),
                        "riskLevel"      to "PENDING",
                        "analysisStatus" to "captured",
                        "isSafe"         to false,
                        "blockReasons"   to emptyList<String>(),
                        "confidenceScore" to 0f,
                        "categoryScores" to emptyMap<String, Float>()
                    ))
                    .await()
                docId = ref.id
                Log.d(TAG, "📝 Stub written: docId=${ref.id} domain=${extractDomain(url)}")
            }
            docId
        } catch (e: Exception) {
            Log.e(TAG, "❌ writeUrlStub FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    // Update the stub with real analysis results
    private fun updateUrlRecord(
        docId: String?,
        url: String,
        result: ContentSafetyManager.SafetyResult,
        sourceType: String
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    Log.e(TAG, "❌ updateUrlRecord: no authenticated user")
                    return@launch
                }
                val updates: Map<String, Any> = mapOf(
                    "riskLevel"       to result.riskLevel.name,
                    "isSafe"          to result.isSafe,
                    "blockReasons"    to result.blockReasons,
                    "confidenceScore" to result.confidenceScore,
                    "categoryScores"  to result.categoryScores,
                    "analysisStatus"  to "completed"
                )
                if (docId != null) {
                    FirebaseFirestore.getInstance()
                        .collection("url_safety")
                        .document(docId)
                        .update(updates)
                        .await()
                    Log.d(TAG, "✅ url_safety/$docId updated: level=${result.riskLevel.name}")
                } else {
                    // No stub docId — write a fresh full record
                    FirebaseFirestore.getInstance()
                        .collection("url_safety")
                        .add(updates + mapOf(
                            "childId"   to uid,
                            "url"       to url,
                            "domain"    to extractDomain(url),
                            "sourceType" to sourceType,
                            "timestamp" to System.currentTimeMillis()
                        ))
                        .await()
                    Log.d(TAG, "✅ url_safety new full record written (no stub)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ updateUrlRecord FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }

    // Mark stub as failed so we can see where the pipeline broke in Firestore
    private fun markUrlFailed(docId: String?, error: String?) {
        if (docId == null) return
        GlobalScope.launch(Dispatchers.IO) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("url_safety")
                    .document(docId)
                    .update(
                        "analysisStatus", "failed",
                        "analysisError", error?.take(300) ?: "unknown"
                    )
                    .await()
                Log.d(TAG, "📝 Marked url_safety/$docId as failed")
            } catch (e: Exception) {
                Log.e(TAG, "markUrlFailed error: ${e.message}")
            }
        }
    }

    private fun extractDomain(url: String): String = try {
        android.net.Uri.parse(url).host ?: url
    } catch (e: Exception) { url }

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
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch
                val firestore = FirebaseFirestore.getInstance()
                val docId = "${currentUser.uid}_$date"
                val docRef = firestore.collection("app_usage").document(docId)

                val existing = docRef.get().await()
                val existingApps: List<Map<String, Any>> = if (existing.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    existing.get("apps") as? List<Map<String, Any>> ?: emptyList()
                } else emptyList()
                val existingTotalMin = if (existing.exists()) (existing.getLong("totalTimeMin") ?: 0L) else 0L

                val mergedApps = mergeAppLists(existingApps, newApps)
                val addedMin = additionalTimeMs / 60000L

                val updatedData = mapOf(
                    "childId"      to currentUser.uid,
                    "date"         to date,
                    "timestamp"    to System.currentTimeMillis(),
                    "totalTimeMin" to existingTotalMin + addedMin,
                    "appCount"     to mergedApps.size,
                    "apps"         to mergedApps
                )
                docRef.set(updatedData).await()
                Log.d(TAG, "✅ Incremental app usage updated in Firestore (+${addedMin}min)")
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

    // Analyze recent browser history — called by BackgroundService
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
