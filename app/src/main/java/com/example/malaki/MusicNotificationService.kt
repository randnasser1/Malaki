package com.example.malaki

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MusicNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "MusicNotificationService"
        var currentMusicData: JSONObject? = null
        var musicHistory = mutableListOf<JSONObject>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName

            // Music app packages
            val musicPackages = listOf(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.apple.android.music",
                "com.soundcloud.android",
                "com.amazon.mp3"
            )

            if (packageName in musicPackages) {
                Log.d(TAG, "Music notification from: $packageName")

                val notification = sbn.notification
                val extras = notification.extras

                // FIX: Handle SpannableString properly
                val titleRaw = extras?.get("android.title")
                val textRaw = extras?.get("android.text")
                val bigTextRaw = extras?.get("android.bigText")

                // Convert SpannableString to String safely
                val title = when (titleRaw) {
                    is String -> titleRaw
                    is SpannableString -> titleRaw.toString()
                    else -> ""
                }

                val text = when (textRaw) {
                    is String -> textRaw
                    is SpannableString -> textRaw.toString()
                    else -> ""
                }

                val bigText = when (bigTextRaw) {
                    is String -> bigTextRaw
                    is SpannableString -> bigTextRaw.toString()
                    else -> ""
                }

                Log.d(TAG, "Title: '$title', Text: '$text', BigText: '$bigText'")

                // Only process if we have actual content
                if (title.isNotBlank() || text.isNotBlank()) {
                    val musicData = JSONObject().apply {
                        put("timestamp", System.currentTimeMillis())
                        put("date_time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()))
                        put("music_app", packageName)
                        put("app_name", getAppName(packageName))
                        put("title", title)
                        put("text", text)
                        put("big_text", bigText)
                        put("is_playing", isMusicPlaying(title, text))
                        put("parsed_track", parseTrackInfo(title, text, bigText))
                        put("event_type", "music_notification")
                    }

                    currentMusicData = musicData
                    musicHistory.add(musicData)

                    // Keep only last 50 entries
                    if (musicHistory.size > 50) {
                        musicHistory = musicHistory.takeLast(50).toMutableList()
                    }

                    Log.d(TAG, "Music data captured: ${musicData.toString()}")

                    // Auto-save music data
                    autoSaveMusicData()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}")
        }
    }

    private fun autoSaveMusicData() {
        try {
            val latestMusic = currentMusicData ?: return

            val fileName = "music_notifications_${System.currentTimeMillis()}.json"
            val file = filesDir.resolve(fileName)
            file.writeText(latestMusic.toString(2))

            Log.d(TAG, "✅ Auto-saved music notification to: $fileName")

        } catch (e: Exception) {
            Log.e(TAG, "Error auto-saving music data: ${e.message}")
        }
    }

    private fun parseTrackInfo(title: String, text: String, bigText: String): JSONObject {
        return JSONObject().apply {
            // Try to extract artist and track
            val combined = if (bigText.isNotBlank()) bigText else text

            when {
                combined.contains(" - ") -> {
                    val parts = combined.split(" - ")
                    if (parts.size >= 2) {
                        put("artist", parts[0].trim())
                        put("track", parts[1].trim())
                    }
                }
                title.contains(" - ") -> {
                    val parts = title.split(" - ")
                    if (parts.size >= 2) {
                        put("artist", parts[0].trim())
                        put("track", parts[1].trim())
                    }
                }
                else -> {
                    put("artist", extractArtist(combined))
                    put("track", extractTrack(title, combined))
                }
            }

            put("raw_text", combined)
            put("has_track_info", optString("artist").isNotBlank() || optString("track").isNotBlank())
        }
    }

    private fun extractArtist(text: String): String {
        return when {
            text.contains("by") -> text.substringAfter("by").trim()
            text.contains("·") -> text.substringBefore("·").trim()
            else -> {
                val dashIndex = text.indexOf("-")
                if (dashIndex > 0) text.substring(0, dashIndex).trim() else ""
            }
        }
    }

    private fun extractTrack(title: String, text: String): String {
        return when {
            text.contains("-") -> {
                val dashIndex = text.indexOf("-")
                if (dashIndex > 0) text.substring(dashIndex + 1).trim() else title
            }
            title.isNotBlank() -> title
            else -> ""
        }
    }

    private fun isMusicPlaying(title: String, text: String): Boolean {
        val playingIndicators = listOf("▶", "▷", "play", "playing", "▶️", "now playing")
        val pausedIndicators = listOf("⏸", "⏸️", "pause", "paused", "❚❚", "stopped")

        val combined = "$title $text"
        return playingIndicators.any { it in combined } &&
                !pausedIndicators.any { it in combined }
    }

    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.spotify.music" -> "Spotify"
            "com.google.android.apps.youtube.music" -> "YouTube Music"
            "com.apple.android.music" -> "Apple Music"
            "com.soundcloud.android" -> "SoundCloud"
            "com.amazon.mp3" -> "Amazon Music"
            else -> packageName
        }
    }

    fun getMusicHistory(): List<JSONObject> {
        return musicHistory.toList()
    }

    fun getCurrentPlaying(): JSONObject? {
        return currentMusicData
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notification removed
    }
}