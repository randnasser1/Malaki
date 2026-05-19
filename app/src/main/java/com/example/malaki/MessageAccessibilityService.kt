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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class TextNormalizer(private val context: Context) {
    
    companion object {
        private const val TAG = "TEXT_NORMALIZER"
        
        // Slang dictionary mapping (similar to Python version)
        private val SLANG_MAP = mapOf(
            // Personal info related
            "asl" to "age sex location",
            "a/s/l" to "age sex location",
            "m/f" to "male or female",
            "f/m" to "female or male",
            "s/l" to "sex location",
            
            // Media related
            "pic" to "picture",
            "pics" to "pictures",
            "snap" to "snapchat",
            "sc" to "snapchat",
            "kik" to "kik messenger",
            "vid" to "video",
            "vids" to "videos",
            
            // Social/chat specific
            "wyd" to "what you doing",
            "wya" to "where you at",
            "hmu" to "hit me up",
            "irl" to "in real life",
            "j4f" to "just for fun",
            
            // Dating/personal
            "age" to "age",
            "sex" to "sex",
            "location" to "location",
            "single" to "single",
            
            // Common abbreviations
            "u" to "you",
            "ur" to "your",
            "r" to "are",
            "yr" to "your",
            "yrs" to "yours",
            "plz" to "please",
            "pls" to "please",
            "thx" to "thanks",
            "ty" to "thank you",
            "tyvm" to "thank you very much",
            "np" to "no problem",
            "yw" to "you are welcome",
            "idk" to "i do not know",
            "ik" to "i know",
            "dunno" to "do not know",
            "idc" to "i do not care",
            "tbh" to "to be honest",
            "imo" to "in my opinion",
            "imho" to "in my humble opinion",
            "fwiw" to "for what it is worth",
            "afaik" to "as far as i know",
            
            // Emotional reactions
            "lol" to "laughing out loud",
            "lmao" to "laughing my ass off",
            "lmfao" to "laughing my fucking ass off",
            "rofl" to "rolling on the floor laughing",
            "roflmao" to "rolling on the floor laughing my ass off",
            "lulz" to "laughs",
            "lel" to "laugh",
            "omg" to "oh my god",
            "omfg" to "oh my fucking god",
            "wtf" to "what the fuck",
            "wth" to "what the hell",
            "nvm" to "never mind",
            "jk" to "just kidding",
            "j/k" to "just kidding",
            "smh" to "shaking my head",
            
            // Status/activity
            "afk" to "away from keyboard",
            "brb" to "be right back",
            "gtg" to "got to go",
            "ttyl" to "talk to you later",
            "cya" to "see you",
            "fyi" to "for your information",
            
            // Word contractions
            "gonna" to "going to",
            "wanna" to "want to",
            "gotta" to "got to",
            "kinda" to "kind of",
            "sorta" to "sort of",
            "lemme" to "let me",
            "gimme" to "give me",
            "outta" to "out of",
            "tryna" to "trying to"
        )
        
        // Common spelling corrections
        private val SPELLING_MAP = mapOf(
            "teh" to "the",
            "hte" to "the",
            "adn" to "and",
            "nad" to "and",
            "jsut" to "just",
            "siad" to "said",
            "fomr" to "from",
            "waht" to "what",
            "yuo" to "you",
            "thier" to "their",
            "recieve" to "receive",
            "seperate" to "separate",
            "definately" to "definitely",
            "accomodate" to "accommodate",
            "goverment" to "government"
        )
        
        // Contraction expansions (aggressive mode)
        private val CONTRACTION_MAP = mapOf(
            "im" to "i am",
            "i'm" to "i am",
            "i'll" to "i will",
            "ill" to "i will",
            "i'd" to "i would",
            "id" to "i would",
            "i've" to "i have",
            "ive" to "i have",
            "don't" to "do not",
            "dont" to "do not",
            "doesn't" to "does not",
            "doesnt" to "does not",
            "won't" to "will not",
            "wont" to "will not",
            "can't" to "cannot",
            "cant" to "cannot",
            "couldn't" to "could not",
            "couldnt" to "could not",
            "wouldn't" to "would not",
            "wouldnt" to "would not",
            "shouldn't" to "should not",
            "shouldnt" to "should not",
            "wasn't" to "was not",
            "wasnt" to "was not",
            "weren't" to "were not",
            "werent" to "were not",
            "haven't" to "have not",
            "havent" to "have not",
            "hasn't" to "has not",
            "hasnt" to "has not",
            "hadn't" to "had not",
            "hadnt" to "had not",
            "didn't" to "did not",
            "didnt" to "did not",
            "isn't" to "is not",
            "isnt" to "is not",
            "aren't" to "are not",
            "arent" to "are not",
            "ain't" to "is not",
            "aint" to "is not",
            "you're" to "you are",
            "youre" to "you are",
            "you'll" to "you will",
            "youll" to "you will",
            "you'd" to "you would",
            "youd" to "you would",
            "you've" to "you have",
            "youve" to "you have",
            "he's" to "he is",
            "hes" to "he is",
            "she's" to "she is",
            "shes" to "she is",
            "it's" to "it is",
            "its" to "it is",
            "we're" to "we are",
            "were" to "we are",
            "they're" to "they are",
            "theyre" to "they are"
        )
        
        // Single letter expansions (standalone only)
        private val SINGLE_LETTER_MAP = mapOf(
            "f" to "female",
            "u" to "you",
            "r" to "are",
            "c" to "see",
            "y" to "why",
            "b" to "be",
            "n" to "and",
            "k" to "okay"
        )
        
        // Regex patterns for cleaning
        private val URL_PATTERN = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+")
        private val WWW_PATTERN = Regex("www\\.[a-zA-Z0-9\\-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?")
        private val MENTION_PATTERN = Regex("@\\w+")
        private val TIME_PATTERN = Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:am|pm)?\\b", RegexOption.IGNORE_CASE)
        private val DATE_PATTERN = Regex("\\b\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\b")
        private val REPEAT_CHAR_PATTERN = Regex("(.)\\1{3,}")
        private val EXCESS_PUNCT_PATTERN = Regex("([!?.]){2,}")
        private val MULTIPLE_SPACES_PATTERN = Regex("\\s+")
    }
    
    /**
     * Normalize text by expanding slang, correcting spelling, and cleaning
     */
    fun normalizeText(
        text: String,
        aggressive: Boolean = false,
        expandContractions: Boolean = true,
        correctSpelling: Boolean = true
    ): String {
        if (text.isBlank()) return text

        var normalized = text.lowercase().trim()

        // Remove URLs
        normalized = URL_PATTERN.replace(normalized, " ")
        normalized = WWW_PATTERN.replace(normalized, " ")

        // Remove mentions
        normalized = MENTION_PATTERN.replace(normalized, " ")

        // Remove time/date stamps
        normalized = TIME_PATTERN.replace(normalized, " ")
        normalized = DATE_PATTERN.replace(normalized, " ")

        // Split into words for processing
        val words = normalized.split(Regex("\\s+"))
        val processedWords = mutableListOf<String>()

        for (word in words) {
            var processed = word

            // Apply spelling corrections first (if enabled)
            if (correctSpelling) {
                SPELLING_MAP[processed]?.let {
                    processed = it
                }
            }

            var expansionResult: String? = null

            // Expand contractions (if aggressive or specifically requested)
            if (aggressive || expandContractions) {
                expansionResult = CONTRACTION_MAP[processed]
            }

            // If no contraction expansion, try slang expansion
            if (expansionResult == null) {
                expansionResult = SLANG_MAP[processed]
            }

            if (expansionResult != null) {
                // Check if expansion contains spaces
                if (expansionResult.contains(" ")) {
                    // Add all parts separately
                    processedWords.addAll(expansionResult.split(" "))
                    continue  // This continue is fine - it's in the for loop, not a lambda
                } else {
                    processed = expansionResult
                }
            }

            // Handle single letters (but only if standalone and not part of word)
            if (processed.length == 1 && !processed.all { it.isDigit() }) {
                SINGLE_LETTER_MAP[processed]?.let {
                    processed = it
                }
            }

            processedWords.add(processed)
        }

        // Rebuild text
        normalized = processedWords.joinToString(" ")

        // Normalize repeated characters (keep max 2)
        normalized = REPEAT_CHAR_PATTERN.replace(normalized) { matchResult ->
            val char = matchResult.value[0]
            char.toString().repeat(2)
        }

        // Normalize excessive punctuation
        normalized = EXCESS_PUNCT_PATTERN.replace(normalized) { matchResult ->
            matchResult.value[0].toString()
        }

        // Fix spacing around punctuation
        normalized = normalized.replace(Regex("\\s+([.,!?;:])"), "$1")
        normalized = normalized.replace(Regex("([.,!?;:])(?=[^\\s])"), "$1 ")

        // Clean up multiple spaces and trim
        normalized = MULTIPLE_SPACES_PATTERN.replace(normalized, " ").trim()

        return normalized
    }
    
    /**
     * Batch normalize multiple texts
     */
    fun normalizeTexts(
        texts: List<String>,
        aggressive: Boolean = false,
        expandContractions: Boolean = true,
        correctSpelling: Boolean = true
    ): List<String> {
        return texts.map { 
            normalizeText(it, aggressive, expandContractions, correctSpelling)
        }
    }
    
    /**
     * Check if a message contains personal information patterns
     */
    fun containsPersonalInfo(text: String): Boolean {
        val normalized = normalizeText(text, aggressive = true)
        val lowerText = normalized.lowercase()
        
        // Patterns that might indicate personal info sharing
        val personalInfoPatterns = listOf(
            Regex("(age|old)\\s+\\d{1,3}"),  // age mention
            Regex("(male|female|gender|sex)"),
            Regex("(location|city|state|country)\\s+\\w+"),
            Regex("(phone|number|cell|whatsapp)\\s+\\d{5,}"),
            Regex("(snap|sc|kik|instagram|ig)"),
            Regex("(meet|date|hookup|cuddle)"),
            Regex("(single|available|looking for)"),
            Regex("(send|exchange)\\s+(pic|photo|picture)")
        )
        
        return personalInfoPatterns.any { it.containsMatchIn(lowerText) }
    }
    
    /**
     * Get risk score for message (0-100)
     */
    fun getRiskScore(text: String): Int {
        val normalized = normalizeText(text, aggressive = true)
        var score = 0
        
        // High risk keywords
        val highRiskKeywords = listOf(
            "age", "sex", "location", "phone", "number", "snapchat", 
            "instagram", "meet", "hookup", "send pic", "exchange"
        )
        
        // Medium risk keywords
        val mediumRiskKeywords = listOf(
            "single", "available", "what you doing", "where you at",
            "hit me up", "date"
        )
        
        val lowerText = normalized.lowercase()
        
        for (keyword in highRiskKeywords) {
            if (lowerText.contains(keyword)) {
                score += 15
            }
        }
        
        for (keyword in mediumRiskKeywords) {
            if (lowerText.contains(keyword)) {
                score += 8
            }
        }
        
        // Check for multiple personal info indicators
        if (containsPersonalInfo(normalized)) {
            score += 10
        }
        
        return minOf(score, 100)
    }
}

class MessageAccessibilityService : AccessibilityService() {
    private lateinit var textNormalizer: TextNormalizer
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MESSAGE_SERVICE"
        private val seenMessages = Collections.synchronizedSet(mutableSetOf<String>())
        private var lastProcessTime = 0L
        private const val MIN_PROCESS_INTERVAL = 500L
        private var lastCapturedUrl: String? = null
        private var lastCapturedUrlTime: Long = 0L
        private const val URL_DEDUP_WINDOW_MS = 30_000L // ignore same URL seen within 30 s
        private var lastBrowserScanTime = 0L
        private const val BROWSER_SCAN_INTERVAL = 1000L // scan URL bar at most once per second
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

        textNormalizer = TextNormalizer(applicationContext)

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

        // ── Browser: capture URL on STATE_CHANGED and CONTENT_CHANGED ─────────
        // STATE_CHANGED fires on page load but not always for in-tab navigation.
        // CONTENT_CHANGED fires on every DOM update — rate-limited to once/second.
        if (isBrowser && (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
            val now = System.currentTimeMillis()
            if (now - lastBrowserScanTime < BROWSER_SCAN_INTERVAL) return
            lastBrowserScanTime = now

            Log.d(TAG, "🌐 Browser event: pkg=$packageName type=${getEventTypeString(event.eventType)}")
            val rootNode = rootInActiveWindow ?: run {
                Log.w(TAG, "⚠️ rootInActiveWindow is null for $packageName")
                return
            }
            try {
                val url = captureBrowserUrl(rootNode, packageName)
                // Chrome strips https:// from the address bar — normalize bare domains
                val normalizedUrl = when {
                    url == null -> null
                    url.startsWith("http") -> url
                    url.contains(".") && !url.contains(" ") -> "https://$url"
                    else -> null
                }
                when {
                    normalizedUrl == null -> Log.w(TAG, "⚠️ No usable URL from bar text: $url")
                    normalizedUrl == lastCapturedUrl &&
                        (System.currentTimeMillis() - lastCapturedUrlTime) < URL_DEDUP_WINDOW_MS ->
                        Log.d(TAG, "⏭️ Same URL within dedup window, skipping: $normalizedUrl")
                    else -> {
                        lastCapturedUrl = normalizedUrl
                        lastCapturedUrlTime = System.currentTimeMillis()
                        Log.d(TAG, "✅ Browser URL captured: $normalizedUrl")
                        saveBrowserUrl(packageName, normalizedUrl)
                        try {
                            val dc = DataCollector(applicationContext)
                            dc.analyzeUrlAndSave(normalizedUrl, "BROWSER")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ analyzeUrlAndSave error: ${e.message}")
                        }
                    }
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
                        extractAndAnalyzeUrls(message, "MESSAGE")
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

        if (cleanText.isEmpty()) return

        // Normalize before any length filtering — short slang like "asl"/"wyd"/"hmu"
        // expand to full phrases and must not be dropped by a pre-normalization length gate.
        val normalizedText = textNormalizer.normalizeText(cleanText, aggressive = true, correctSpelling = true)
        val textToAnalyze = if (normalizedText.isNotEmpty()) normalizedText else cleanText

        if (cleanText != textToAnalyze) {
            Log.d(TAG, "📝 Normalized: '$cleanText' -> '$textToAnalyze'")
        }

        if (textNormalizer.containsPersonalInfo(textToAnalyze)) {
            Log.w(TAG, "⚠️ Personal info detected in: ${textToAnalyze.take(100)}")
        }

        val lowerText = textToAnalyze.lowercase(Locale.getDefault())

        // Comprehensive list of UI phrases to filter out (YOUR EXISTING LIST - UNCHANGED)
        val uiPhrases = listOf(
            "turn on blend", "add more actions", "suggested for you", "posted a photo",
            "add to story", "reels tray container", "double tap to like", "liked by",
            "view all comments", "add a comment", "send message", "type a message",
            "replied to", "mentioned you", "shared a post", "liked your",
            "follow", "following", "suggested", "explore", "discover", "trending",
            "for you", "because you watched", "popular", "related",
            "profile picture", "typing...", "typing", "seen", "delivered", "read",
            "online", "offline", "active now", "last active", "voice message",
            "press and hold to record", "swipe up", "disappearing messages",
            "you turned off", "you turned on", "no internet", "connecting",
            "loading...", "loading", "refresh", "retry", "try again",
            "play", "pause", "skip", "next", "previous", "mute", "unmute",
            "full screen", "exit full screen", "download", "share", "copy link",
            Regex("\\d{1,2}:\\d{2}\\s*(AM|PM)?"),
            Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}"),
            Regex("\\d{1,2}\\s+(min|hour|day|week|month).*ago"),
            Regex("^[😀-🙏\\s]+$")
        )
        
        // Skip if message matches any UI pattern
        var shouldSkip = false
        for (pattern in uiPhrases) {
            when (pattern) {
                is Regex -> {
                    if (pattern.containsMatchIn(lowerText)) {
                        shouldSkip = true
                        break
                    }
                }
                is String -> {
                    if (lowerText.contains(pattern)) {
                        shouldSkip = true
                        break
                    }
                }
            }
        }
        
        if (shouldSkip) {
            Log.d(TAG, "⏭️ Skipping UI text: $cleanText")
            return
        }
        
        // Drop messages too short for meaningful model evaluation.
        // Measured on the *normalized* text so expanded slang counts correctly
        // (e.g. "asl" → "age sex location" = 3 words, passes).
        val normalizedWords = textToAnalyze.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (normalizedWords.size < 2 && textToAnalyze.length < 10) {
            Log.d(TAG, "⏭️ Skipping too short for evaluation: '$cleanText' → '$textToAnalyze'")
            return
        }

        // Create hash and save (use cleanText for hash to maintain consistency)
        val messageHash = "${packageName}|${cleanText.hashCode()}"
        
        // Session dedup
        if (seenMessages.contains(messageHash)) return
        seenMessages.add(messageHash)

        // Persistent dedup
        val prefs = applicationContext.getSharedPreferences("sent_messages", Context.MODE_PRIVATE)
        val sentHashes = prefs.getStringSet("sent_hashes", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        if (sentHashes.contains(messageHash)) return

        sentHashes.add(messageHash)
        prefs.edit().putStringSet("sent_hashes", sentHashes).apply()

        if (sentHashes.size > 1000) {
            val limited = sentHashes.take(500).toMutableSet()
            prefs.edit().putStringSet("sent_hashes", limited).apply()
        }

        // Save to Room
        serviceScope.launch {
            val repository = EventRepository(applicationContext)
            repository.ensureDeviceProfile()
            repository.captureEvent(
                eventType = "MESSAGE",
                sourceApp = packageName,
                senderRole = "OTHER",
                rawText = textToAnalyze,
                textPreview = cleanText.take(100),
                timestampUtc = System.currentTimeMillis()
            )
            Log.d(TAG, "✅ Saved message (normalized): ${textToAnalyze.take(50)}")
        }

    } catch (e: Exception) {
        Log.e(TAG, "Error saving message: ${e.message}")
    }
}
    override fun onInterrupt() {
        Log.d(TAG, "❌ Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun captureBrowserUrl(rootNode: AccessibilityNodeInfo, packageName: String): String? {
        // Try known address-bar view IDs across Chrome versions
        val candidateIds = listOf(
            "$packageName:id/url_bar",
            "$packageName:id/search_box_text",
            "$packageName:id/url_bar_edittext",
            "com.android.chrome:id/url_bar",
            "com.google.android.apps.chrome:id/url_bar"
        )
        for (viewId in candidateIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                nodes.forEach { it.recycle() }
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "📍 URL bar found via $viewId → $text")
                    return text
                }
            }
        }
        // Fallback: scan all nodes for any http text
        Log.d(TAG, "📍 No viewId match — scanning tree for 'http' text")
        val httpNodes = rootNode.findAccessibilityNodeInfosByText("http")
        for (node in httpNodes) {
            val text = node.text?.toString()
            node.recycle()
            if (text != null && (text.startsWith("http://") || text.startsWith("https://"))) {
                Log.d(TAG, "📍 URL found via text scan: $text")
                return text
            }
        }
        Log.w(TAG, "📍 captureBrowserUrl: no URL found in tree (pkg=$packageName)")
        return null
    }

    private fun extractAndAnalyzeUrls(text: String, sourceType: String = "MESSAGE") {
        val urlRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+")
        urlRegex.findAll(text).forEach { match ->
            val url = match.value
            Log.d(TAG, "🔍 Found URL in $sourceType: $url")
            try {
                DataCollector(applicationContext).analyzeUrlAndSave(url, sourceType)
            } catch (e: Exception) {
                Log.e(TAG, "❌ analyzeUrlAndSave error for $url: ${e.message}")
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

        // Skip very short text
        if (text.length < 5) return false

        // Skip timestamps
        if (text.matches(Regex("\\d{1,2}:\\d{2}(\\s?(AM|PM))?"))) return false

        // 🆕 Skip Instagram/WhatsApp UI elements
        val uiPhrases = listOf(
            "profile picture", "double tap to like", "liked by", "view all comments",
            "add a comment", "send message", "type a message", "typing",
            "seen", "delivered", "read", "online", "offline",
            "instagram", "whatsapp", "messenger", "discord",
            "replied to", "mentioned you", "shared a post",
            "voice message", "press and hold", "swipe up",
            "disappearing messages", "you turned off", "you turned on",
            "no internet", "connecting", "loading", "refresh",
            "active yesterday", "active today", "active now", "active",
            "last active", "last seen", "last seen yesterday",
            "yesterday", "today at", "yesterday at",
            "joined instagram", "joined facebook", "joined",
            "follows you", "followed by", "suggested for you",
            "message request", "accept", "decline", "delete",
            "block", "report", "mute", "unmute",
            "typing...", "record voice message", "attach photo"
        )

        if (uiPhrases.any { lowerText.contains(it) }) {
            Log.d(TAG, "⏭️ Skipping UI text: $text")
            return false
        }

        // Skip if it's just emojis or very short with no words
        val wordCount = text.split(" ").size
        if (wordCount < 2 && !text.contains(Regex("[😀-🙏]"))) return false

        return true
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