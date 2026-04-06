package com.example.malaki

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MusicDataService : NotificationListenerService() {

    companion object {
        private const val TAG = "MusicDataService"
        private const val MUSIC_FILE = "music_data.json"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎵 MusicDataService created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "✅ MusicDataService connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            Log.d(TAG, "📱 Notification from: $packageName")

            // Check if it's a music app
            if (isMusicApp(packageName)) {
                val notification = sbn.notification
                val extras = notification.extras

                // Get text fields safely
                val title = getStringExtra(extras, android.app.Notification.EXTRA_TITLE)
                val text = getStringExtra(extras, android.app.Notification.EXTRA_TEXT)
                val bigText = getStringExtra(extras, android.app.Notification.EXTRA_BIG_TEXT)
                val subText = getStringExtra(extras, android.app.Notification.EXTRA_SUB_TEXT)

                Log.d(TAG, "🎵 Music notification - Title: '$title', Text: '$text', BigText: '$bigText'")

                // Only process if we have meaningful content
                if (title.isNotBlank() || text.isNotBlank() || bigText.isNotBlank()) {
                    // Create music event
                    val musicEvent = createMusicEvent(packageName, title, text, bigText, subText)

                    // Save to single file
                    saveToSingleFile(musicEvent)

                    Log.d(TAG, "💾 Saved music event")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing notification: ${e.message}")
        }
    }

    private fun getStringExtra(extras: android.os.Bundle?, key: String): String {
        return try {
            when (val value = extras?.get(key)) {
                is String -> value
                is CharSequence -> value.toString()
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun isMusicApp(packageName: String): Boolean {
        val musicApps = listOf(
            "com.spotify.music",
            "com.spotify.lite",
            "com.google.android.apps.youtube.music",
            "com.apple.android.music",
            "com.soundcloud.android",
            "com.amazon.mp3",
            "com.gaana",
            "com.jiosaavn",
            "com.tencent.qqmusic",
            "com.netease.cloudmusic"
        )
        return musicApps.any { packageName.contains(it, ignoreCase = true) }
    }

    private fun createMusicEvent(packageName: String, title: String, text: String, bigText: String, subText: String): JSONObject {
        val timestamp = System.currentTimeMillis()

        // Parse artist and track
        val (artist, track, state) = parseTrackInfo(title, text, bigText, subText)

        return JSONObject().apply {
            put("timestamp", timestamp)
            put("datetime", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(timestamp)))
            put("package", packageName)
            put("app", getAppName(packageName))
            put("title", title)
            put("text", text)
            put("big_text", bigText)
            put("sub_text", subText)
            put("is_playing", state == "playing")
            put("track_info", JSONObject().apply {
                put("artist", artist)
                put("track", track)
                put("state", state)
            })
        }
    }

    private fun parseTrackInfo(title: String, text: String, bigText: String, subText: String): Triple<String, String, String> {
        val allText = listOf(title, text, bigText, subText)
        val combined = allText.joinToString(" ")
        val lowerCombined = combined.lowercase(Locale.getDefault())

        var artist = "Unknown Artist"
        var track = "Unknown Track"
        val state = detectPlaybackState(lowerCombined)

        // Try different parsing strategies
        val sources = allText.filter { it.isNotBlank() }

        for (source in sources) {
            // Strategy 1: Look for " - " separator (Artist - Track)
            if (source.contains(" - ")) {
                val parts = source.split(" - ", limit = 2)
                if (parts.size == 2) {
                    artist = parts[0].trim()
                    track = parts[1].trim()
                    break
                }
            }

            // Strategy 2: For Spotify pattern (track in title, artist in text)
            if (title.isNotBlank() && text.isNotBlank() && text.length > 2) {
                artist = text.trim()
                track = title.trim()
                break
            }

            // Strategy 3: Look for "by" keyword
            if (source.contains(" by ")) {
                val parts = source.split(" by ", limit = 2)
                if (parts.size == 2) {
                    track = parts[0].trim()
                    artist = parts[1].trim()
                    break
                }
            }
        }

        // Clean up results
        if (artist == "Unknown Artist" && title.isNotBlank() && !title.contains("null")) {
            track = title.trim()
        }

        if (track == "Unknown Track" && text.isNotBlank() && !text.contains("null")) {
            // Maybe text is the track name
            track = text.trim()
        }

        return Triple(artist, track, state)
    }

    private fun detectPlaybackState(text: String): String {
        return when {
            text.contains("▶") || text.contains("play") || text.contains("playing") -> "playing"
            text.contains("⏸") || text.contains("pause") || text.contains("paused") -> "paused"
            text.contains("⏹") || text.contains("stop") || text.contains("stopped") -> "stopped"
            else -> "unknown"
        }
    }

    private fun getAppName(packageName: String): String {
        return when {
            packageName.contains("spotify", ignoreCase = true) -> "Spotify"
            packageName.contains("youtube", ignoreCase = true) && packageName.contains("music", ignoreCase = true) -> "YouTube Music"
            packageName.contains("apple", ignoreCase = true) && packageName.contains("music", ignoreCase = true) -> "Apple Music"
            packageName.contains("soundcloud", ignoreCase = true) -> "SoundCloud"
            packageName.contains("amazon", ignoreCase = true) && packageName.contains("music", ignoreCase = true) -> "Amazon Music"
            packageName.contains("gaana", ignoreCase = true) -> "Gaana"
            packageName.contains("jiosaavn", ignoreCase = true) -> "JioSaavn"
            packageName.contains("qqmusic", ignoreCase = true) -> "QQ Music"
            packageName.contains("netease", ignoreCase = true) -> "NetEase Music"
            else -> packageName
        }
    }

    private fun saveToSingleFile(musicEvent: JSONObject) {
        try {
            val file = File(filesDir, MUSIC_FILE)
            val existingArray = if (file.exists() && file.length() > 0) {
                try {
                    JSONArray(file.readText())
                } catch (e: Exception) {
                    JSONArray()
                }
            } else {
                JSONArray()
            }

            // Get track info
            val trackInfo = musicEvent.getJSONObject("track_info")
            val artist = trackInfo.getString("artist")
            val track = trackInfo.getString("track")
            val newTimestamp = musicEvent.getLong("timestamp")

            // Check for duplicates (same artist/track within 5 minutes)
            val isDuplicate = checkForDuplicate(existingArray, artist, track, newTimestamp)

            if (!isDuplicate) {
                existingArray.put(musicEvent)

                // Keep only last 1000 entries
                if (existingArray.length() > 1000) {
                    val trimmed = JSONArray()
                    val startIndex = existingArray.length() - 1000
                    for (i in startIndex until existingArray.length()) {
                        trimmed.put(existingArray.getJSONObject(i))
                    }
                    file.writeText(trimmed.toString(2))
                } else {
                    file.writeText(existingArray.toString(2))
                }

                Log.d(TAG, "📝 Added music: $artist - $track")
            } else {
                Log.d(TAG, "⚠️ Duplicate skipped: $artist - $track")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving music file: ${e.message}")
        }
    }

    private fun checkForDuplicate(existingArray: JSONArray, newArtist: String, newTrack: String, newTimestamp: Long): Boolean {
        try {
            for (i in 0 until existingArray.length()) {
                val existing = existingArray.getJSONObject(i)
                val existingTrackInfo = existing.getJSONObject("track_info")
                val existingArtist = existingTrackInfo.getString("artist")
                val existingTrack = existingTrackInfo.getString("track")
                val existingTimestamp = existing.getLong("timestamp")

                // Same artist and track within 5 minutes
                if (existingArtist == newArtist &&
                    existingTrack == newTrack &&
                    Math.abs(existingTimestamp - newTimestamp) <= 5 * 60 * 1000) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore errors
        }
        return false
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "⏹️ MusicDataService destroyed")
    }
}