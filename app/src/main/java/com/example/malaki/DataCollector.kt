package com.example.malaki

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.example.malaki.security.ContentSafetyManager
import kotlinx.coroutines.runBlocking

class DataCollector(private val context: Context) {

    companion object {
        private const val TAG = "DataCollector"
        private const val APP_USAGE_FILE = "app_usage.json"
        private const val MESSAGES_FILE = "messages.json"
        private const val MUSIC_FILE = "music_data.json"
    }

    // Save Music Notification Data - Consolidate all music files
    fun saveMusicNotificationData() {
        Log.d(TAG, "🎵 Consolidating music data...")

        Thread {
            try {
                // 1. First, find all music notification files
                val filesDir = context.filesDir
                val allFiles = filesDir.listFiles() ?: emptyArray()

                val musicNotificationFiles = allFiles.filter {
                    it.name.startsWith("music_notifications_") && it.name.endsWith(".json")
                }

                Log.d(TAG, "Found ${musicNotificationFiles.size} music notification files")

                // 2. Read existing consolidated music data
                val consolidatedFile = File(filesDir, MUSIC_FILE)
                val consolidatedArray = if (consolidatedFile.exists() && consolidatedFile.length() > 0) {
                    try {
                        JSONArray(consolidatedFile.readText())
                    } catch (e: Exception) {
                        JSONArray()
                    }
                } else {
                    JSONArray()
                }

                val seenTracks = mutableSetOf<String>()

                // 3. Add existing consolidated entries to seen set
                for (i in 0 until consolidatedArray.length()) {
                    try {
                        val entry = consolidatedArray.getJSONObject(i)
                        val trackInfo = entry.getJSONObject("track_info")
                        val key = "${trackInfo.getString("artist")}|${trackInfo.getString("track")}|${entry.getLong("timestamp")}"
                        seenTracks.add(key)
                    } catch (e: Exception) {
                        continue
                    }
                }

                // 4. Process each notification file and add to consolidated data
                var addedCount = 0
                musicNotificationFiles.forEach { file ->
                    try {
                        val content = file.readText()
                        val notification = JSONObject(content)

                        // Convert old format to new format
                        val musicEvent = convertOldToNewFormat(notification)

                        // Check for duplicates
                        val trackInfo = musicEvent.getJSONObject("track_info")
                        val key = "${trackInfo.getString("artist")}|${trackInfo.getString("track")}|${musicEvent.getLong("timestamp")}"

                        if (!seenTracks.contains(key)) {
                            consolidatedArray.put(musicEvent)
                            seenTracks.add(key)
                            addedCount++

                            // Delete the old file after processing
                            file.delete()
                            Log.d(TAG, "✅ Processed and deleted: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error processing ${file.name}: ${e.message}")
                    }
                }

                // 5. Save consolidated data
                if (addedCount > 0 || !consolidatedFile.exists()) {
                    // Keep only last 1000 entries
                    val finalArray = if (consolidatedArray.length() > 1000) {
                        JSONArray().apply {
                            for (i in consolidatedArray.length() - 1000 until consolidatedArray.length()) {
                                put(consolidatedArray.getJSONObject(i))
                            }
                        }
                    } else {
                        consolidatedArray
                    }

                    consolidatedFile.writeText(finalArray.toString(2))
                    Log.d(TAG, "✅ Consolidated music data saved: ${finalArray.length()} entries total")
                } else {
                    Log.d(TAG, "📭 No new music data to consolidate")
                }

                // 6. Also clean up any other music_*.json files
                allFiles.filter {
                    it.name.startsWith("music_") &&
                            it.name != MUSIC_FILE &&
                            it.name.endsWith(".json")
                }.forEach { file ->
                    file.delete()
                    Log.d(TAG, "🗑️ Deleted duplicate file: ${file.name}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error consolidating music data: ${e.message}")
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
                Log.d(TAG, "✅ App usage saved")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving app usage: ${e.message}")
            }
        }.start()
    }

    // Save Messages
    fun saveMessages() {
        Log.d(TAG, "💬 Saving messages...")
        // Keep your existing implementation
        Thread {
            try {
                val messagesFile = File(context.filesDir, "messages.txt")
                if (!messagesFile.exists()) {
                    Log.d(TAG, "📭 No messages.txt found")
                    return@Thread
                }

                val messages = messagesFile.readLines()
                if (messages.isEmpty()) return@Thread

                val entry = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
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

                Log.d(TAG, "✅ Messages saved")

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
}