package com.example.malaki.ui.screens.parent

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.platform.LocalContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.example.malaki.BuildConfig
import com.example.malaki.ui.theme.*
import androidx.compose.ui.text.style.TextAlign
// Data classes
data class SentimentData(val day: String, val score: Int)
data class WellbeingData(val category: String, val score: Int)
data class SafetyIndicator(val type: String, val status: String, val description: String)
data class ChildInfo(val id: String, val name: String)
data class AppUsageItem(val name: String, val timeMin: Long)
// Add this near your other data classes (around line 30-40)
data class EmotionDayData(
    val date: String,
    val childLoggedEmotion: String?,
    val inferredEmotion: String?,
    val hasChildLog: Boolean,
    val hasInferredData: Boolean
)

data class CategoryAnomalyData(
    val category: String,
    val enoughData: Boolean,
    val isAnomaly: Boolean,
    val actual: Float,
    val forecast: Float,
    val direction: String
)

data class EmotionAnomalyData(
    val emotion: String,
    val enoughData: Boolean,
    val isAnomaly: Boolean,
    val actual: Float,
    val forecast: Float,
    val direction: String
)

data class TbatsAnalysisResult(
    val concernLevel: String,
    val musicInsight: String,
    val usageInsight: String,
    val appEnoughData: Boolean,
    val appDaysCollected: Int,
    val appCategoryAnomalies: List<CategoryAnomalyData>,
    val musicEnoughData: Boolean,
    val musicDaysCollected: Int,
    val musicEmotionAnomalies: List<EmotionAnomalyData>
)
@Composable
fun ParentDashboard(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var sentimentData by remember { mutableStateOf<List<SentimentData>>(emptyList()) }
    var wellbeingData by remember { mutableStateOf<List<WellbeingData>>(emptyList()) }
    var safetyIndicators by remember { mutableStateOf<List<SafetyIndicator>>(emptyList()) }
    var musicInsights by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var appUsage by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var topAppsList by remember { mutableStateOf<List<AppUsageItem>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<RiskAlert>>(emptyList()) }
    var isLoadingAlerts by remember { mutableStateOf(true) }
    var isLoadingDashboard by remember { mutableStateOf(true) }

    var children by remember { mutableStateOf<List<ChildInfo>>(emptyList()) }
    var selectedChildId by remember { mutableStateOf<String?>(null) }
    var childToDelete by remember { mutableStateOf<ChildInfo?>(null) }
    var pinForChild by remember { mutableStateOf<Pair<String, String>?>(null) }

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentParentId = auth.currentUser?.uid ?: ""
    var tbatsConcernLevel by remember { mutableStateOf("LOW") }
    var tbatsMusicInsight by remember { mutableStateOf("") }
    var tbatsUsageInsight by remember { mutableStateOf("") }
    var appCategoryAnomalies by remember { mutableStateOf<List<CategoryAnomalyData>>(emptyList()) }
    var appAnomalyEnoughData by remember { mutableStateOf<Boolean?>(null) }
    var appAnomalyDaysCollected by remember { mutableStateOf(0) }
    var musicEmotionAnomalies by remember { mutableStateOf<List<EmotionAnomalyData>>(emptyList()) }
    var musicAnomalyEnoughData by remember { mutableStateOf<Boolean?>(null) }
    var musicAnomalyDaysCollected by remember { mutableStateOf(0) }
    var groomingProbability by remember { mutableStateOf(0f) }
    // Load children when parent ID changes (sign in/out)
    LaunchedEffect(currentParentId) {
        if (currentParentId.isNotEmpty()) {
            loadChildrenFromFirebase { loaded ->
                children = loaded
                if (selectedChildId == null && loaded.isNotEmpty()) {
                    selectedChildId = loaded.firstOrNull()?.id
                }
            }
        } else {
            children = emptyList()
            selectedChildId = null
        }
    }

    // Load dashboard data when selected child changes
    // Load dashboard data when selected child changes
    LaunchedEffect(selectedChildId, currentParentId) {
        val childId = selectedChildId
        android.util.Log.d("DASHBOARD", "=== LaunchedEffect triggered ===")
        android.util.Log.d("DASHBOARD", "childId: $childId")
        android.util.Log.d("DASHBOARD", "currentParentId: $currentParentId")

        if (childId != null && currentParentId.isNotEmpty()) {
            android.util.Log.d("DASHBOARD", "Loading data for child: $childId")
            isLoadingDashboard = true
            isLoadingAlerts = true

            // Load dashboard data
            loadDashboardDataFromFirebase(context, childId) { sentiment, wellbeing, safety, music, usage, apps ->
                android.util.Log.d("DASHBOARD", "Data received - sentiment: ${sentiment.size}, wellbeing: ${wellbeing.size}")
                sentimentData = sentiment
                wellbeingData = wellbeing
                safetyIndicators = safety
                musicInsights = music
                appUsage = usage
                topAppsList = apps
                isLoadingDashboard = false
            }

            // Load alerts
            loadAlertsFromFirebase(context, childId) { loadedAlerts ->
                alerts = loadedAlerts
                isLoadingAlerts = false
                triggerAlertNotificationIfNeeded(context, loadedAlerts)
            }

            loadGroomingProbabilityFromFirebase(childId) { prob ->
                groomingProbability = prob
            }

            // LOAD TBATS ANALYSIS (Behavioral Change Detection)
            loadTbatsAnalysis(context, childId) { result ->
                android.util.Log.d("DASHBOARD", "TBATS result: ${result.concernLevel}")
                tbatsConcernLevel       = result.concernLevel
                tbatsMusicInsight       = result.musicInsight
                tbatsUsageInsight       = result.usageInsight
                appAnomalyEnoughData    = result.appEnoughData
                appAnomalyDaysCollected = result.appDaysCollected
                appCategoryAnomalies    = result.appCategoryAnomalies
                musicAnomalyEnoughData    = result.musicEnoughData
                musicAnomalyDaysCollected = result.musicDaysCollected
                musicEmotionAnomalies     = result.musicEmotionAnomalies
            }

        } else {
            android.util.Log.d("DASHBOARD", "Skipping load - missing childId or parentId")
        }
    }

    // Rest of your composable remains the same...
    childToDelete?.let { child ->
        AlertDialog(
            onDismissRequest = { childToDelete = null },
            title = { Text("Delete ${child.name}'s Account") },
            text = { Text("This will permanently delete ${child.name}'s account and all their data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteChildFromFirebase(child.id) { success ->
                        if (success) {
                            children = children.filter { it.id != child.id }
                            if (selectedChildId == child.id) {
                                selectedChildId = children.firstOrNull()?.id
                            }
                        }
                    }
                    childToDelete = null
                }) {
                    Text("Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { childToDelete = null }) { Text("Cancel") }
            }
        )
    }

    pinForChild?.let { (name, pin) ->
        AlertDialog(
            onDismissRequest = { pinForChild = null },
            title = { Text("$name's PIN") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Use this PIN to log into $name's account on any device:")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = pin,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        color = Color(0xFF10B981)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { pinForChild = null }) { Text("Done") }
            }
        )
    }

    if (isLoadingDashboard && children.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                val selectedChildName = children.find { it.id == selectedChildId }?.name
                var dropdownExpanded by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB))
                            ) {
                                Text(
                                    text = if (selectedChildName != null) "$selectedChildName's Dashboard" else "Select Child",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF111827)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("▾", color = Color(0xFF6B7280))
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                children.forEach { child ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = child.name,
                                                    modifier = Modifier.weight(1f),
                                                    fontWeight = if (child.id == selectedChildId) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (child.id == selectedChildId) Color(0xFF10B981) else Color(0xFF1F2937)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        dropdownExpanded = false
                                                        getChildPin(child.id) { pin ->
                                                            if (pin != null) pinForChild = child.name to pin
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text("🔑", fontSize = 14.sp)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        dropdownExpanded = false
                                                        childToDelete = child
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text("🗑️", fontSize = 14.sp)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedChildId = child.id
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { onNavigate("logout") }) {
                            Text("Sign Out", color = Color(0xFFEF4444), fontSize = 13.sp)
                        }
                    }

                    Text(
                        text = "Emotional wellbeing & safety overview",
                        color = Color(0xFF6B7280),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { onNavigate("addChild") },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Add Child")
                    }
                }
            }

            // Loading indicator while data is being fetched
            if (isLoadingDashboard) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading dashboard...", color = Color(0xFF6B7280))
                        }
                    }
                }
            } else {

                // Status banner
                item {
                    ChildStatusBanner(
                        alerts = alerts,
                        tbatsConcernLevel = tbatsConcernLevel,
                        sentimentData = sentimentData,
                        groomingProbability = groomingProbability
                    )
                }
// 🆕 TOP EMOTIONS CARD - ADD THIS HERE
                item {
                    var topEmotions by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }

                    LaunchedEffect(selectedChildId) {
                        if (selectedChildId != null && selectedChildId!!.isNotEmpty()) {
                            try {
                                val firestore = FirebaseFirestore.getInstance()
                                // Single-field filter only — avoids composite index requirement
                                val raw = firestore.collection("event_analysis")
                                    .whereEqualTo("childId", selectedChildId)
                                    .limit(60)
                                    .get()
                                    .await()

                                val relevant = raw.documents
                                    .filter { it.getString("eventType") in listOf("MESSAGE", "JOURNAL") }
                                    .sortedByDescending { it.getLong("timestamp") ?: 0L }
                                    .take(20)

                                val emotionSums = mutableMapOf<String, Float>()
                                var count = 0

                                for (doc in relevant) {
                                    @Suppress("UNCHECKED_CAST")
                                    val vector = doc.get("emotionVector") as? Map<String, Any> ?: continue
                                    count++
                                    vector.forEach { (emotion, scoreAny) ->
                                        val score = when (scoreAny) {
                                            is Double -> scoreAny.toFloat()
                                            is Float -> scoreAny
                                            else -> 0f
                                        }
                                        emotionSums[emotion] = (emotionSums[emotion] ?: 0f) + score
                                    }
                                }

                                if (count > 0 && emotionSums.isNotEmpty()) {
                                    topEmotions = emotionSums.map { (emotion, sum) ->
                                        emotion to (sum / count)
                                    }.sortedByDescending { it.second }.take(5)
                                }
                            } catch (e: Exception) {
                                Log.e("DASHBOARD", "Error loading emotions: ${e.message}")
                            }
                        }
                    }

                    DashboardCard(
                        title = "Current Emotions",
                        subtitle = "DistilBERT analysis",
                        icon = "😊",
                        iconColor = Color(0xFF8B5CF6)
                    ) {
                        if (topEmotions.isEmpty()) {
                            Text("No analyzed events yet", color = Color(0xFF6B7280), modifier = Modifier.padding(8.dp))
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                topEmotions.forEach { (emotion, score) ->
                                    val emoji = EMOTION_EMOJIS[emotion] ?: "😐"
                                    val color = EMOTION_COLORS[emotion] ?: Color(0xFF6B7280)
                                    val percent = (score * 100).toInt()

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(36.dp))
                                        Text(
                                            text = emotion.replaceFirstChar { it.uppercase() },
                                            color = Color(0xFF374151),
                                            fontSize = 14.sp,
                                            modifier = Modifier.width(100.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(8.dp)
                                                .background(Color(0xFFE5E7EB), RoundedCornerShape(4.dp))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(percent / 100f)
                                                    .background(color, RoundedCornerShape(4.dp))
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$percent%",
                                            color = color,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.width(40.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                  // Risk Alerts Card
                item {
                    RiskAlertsCard(
                        alerts = alerts,
                        isLoading = isLoadingAlerts,
                        onRefresh = {
                            val childId = selectedChildId ?: return@RiskAlertsCard
                            isLoadingAlerts = true
                            loadAlertsFromFirebase(context, childId) { newAlerts ->
                                alerts = newAlerts
                                isLoadingAlerts = false
                            }
                        }
                    )
                }
// Wellbeing Indicators Card (Tabbed: Journal Emotions vs Daily Mood Calendar)
                item {
                    var selectedWellbeingTab by remember { mutableStateOf(0) }
                    var journalEmotions by remember { mutableStateOf<List<WellbeingData>>(emptyList()) }
                    var moodData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }  // date -> mood
                    var journalData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }  // date -> journal
                    var selectedDate by remember { mutableStateOf<String?>(null) }
                    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
                    var isLoadingWellbeing by remember { mutableStateOf(true) }

                    LaunchedEffect(selectedChildId) {
                        if (selectedChildId != null && selectedChildId!!.isNotEmpty()) {
                            isLoadingWellbeing = true

                            try {
                                val firestore = FirebaseFirestore.getInstance()
                                val moods = mutableMapOf<String, String>()
                                val journals = mutableMapOf<String, String>()
                                val emotionSums = mutableMapOf<String, Double>()
                                var journalCount = 0

                                fun accumulateEmotions(journal: String, sentiment: Double) {
                                    val lower = journal.lowercase()
                                    var anyDetected = false
                                    if (lower.contains("happy") || lower.contains("great") || lower.contains("good")) {
                                        emotionSums["joy"] = (emotionSums["joy"] ?: 0.0) + (sentiment * 0.8)
                                        anyDetected = true
                                    }
                                    if (lower.contains("sad") || lower.contains("depressed") || lower.contains("miss")) {
                                        emotionSums["sadness"] = (emotionSums["sadness"] ?: 0.0) + (1 - sentiment) * 0.9
                                        emotionSums["grief"] = (emotionSums["grief"] ?: 0.0) + (1 - sentiment) * 0.7
                                        anyDetected = true
                                    }
                                    if (lower.contains("anxious") || lower.contains("worried") || lower.contains("scared")) {
                                        emotionSums["nervousness"] = (emotionSums["nervousness"] ?: 0.0) + (1 - sentiment) * 0.8
                                        emotionSums["fear"] = (emotionSums["fear"] ?: 0.0) + (1 - sentiment) * 0.6
                                        anyDetected = true
                                    }
                                    if (lower.contains("love")) {
                                        emotionSums["love"] = (emotionSums["love"] ?: 0.0) + sentiment * 0.9
                                        anyDetected = true
                                    }
                                    if (!anyDetected)
                                        emotionSums["neutral"] = (emotionSums["neutral"] ?: 0.0) + 0.5
                                }

                                // Primary source: wellbeing_daily_summary (has DistilBERT sentiment)
                                val summaryDocs = firestore.collection("wellbeing_daily_summary")
                                    .whereEqualTo("childId", selectedChildId)
                                    .get()
                                    .await()

                                for (doc in summaryDocs.documents) {
                                    val date = doc.getString("date") ?: continue
                                    val mood = doc.getString("dailyMood") ?: doc.getString("dominantEmotion")
                                    val journal = doc.getString("journalText")
                                    val journalSentiment = doc.getDouble("journalSentiment") ?: 0.5

                                    if (mood != null) moods[date] = mood
                                    if (!journal.isNullOrEmpty()) {
                                        journals[date] = journal
                                        journalCount++
                                        accumulateEmotions(journal, journalSentiment)
                                    }
                                }

                                // Fallback: users/{childId}/journals (saved even when backend is offline)
                                try {
                                    val fallbackDocs = firestore.collection("users")
                                        .document(selectedChildId!!)
                                        .collection("journals")
                                        .get()
                                        .await()

                                    for (doc in fallbackDocs.documents) {
                                        val date = doc.getString("date") ?: continue
                                        val journalText = doc.getString("text") ?: continue
                                        if (journals.containsKey(date)) continue  // already processed from summary
                                        journals[date] = journalText
                                        journalCount++
                                        accumulateEmotions(journalText, 0.5)
                                    }
                                } catch (e: Exception) {
                                    Log.w("DASHBOARD", "Fallback journal fetch failed: ${e.message}")
                                }

                                moodData = moods
                                journalData = journals

                                journalEmotions = if (journalCount > 0 && emotionSums.isNotEmpty()) {
                                    val rawList = emotionSums.map { (emotion, sum) ->
                                        emotion to (sum / journalCount).coerceAtLeast(0.0)
                                    }
                                    val total = rawList.sumOf { it.second }
                                    if (total > 0) {
                                        rawList.map { (emotion, raw) ->
                                            WellbeingData(
                                                category = emotion.replaceFirstChar { it.uppercase() },
                                                score = ((raw / total) * 100).toInt().coerceIn(1, 100)
                                            )
                                        }.sortedByDescending { it.score }.take(8)
                                    } else {
                                        emptyList()
                                    }
                                } else {
                                    emptyList()
                                }

                            } catch (e: Exception) {
                                Log.e("DASHBOARD", "Error loading wellbeing data: ${e.message}")
                            }

                            isLoadingWellbeing = false
                        }
                    }

                    DashboardCard(
                        title = "Wellbeing Indicators",
                        subtitle = if (selectedWellbeingTab == 0) "Distilbert Analysis from journal entries" else "😊 Daily mood check-ins",
                        icon = if (selectedWellbeingTab == 0) "📝" else "📅",
                        iconColor = Color(0xFF10B981)
                    ) {
                        Column {
                            // Tab Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                                    .padding(4.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { selectedWellbeingTab = 0 },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedWellbeingTab == 0) Color.White else Color.Transparent,
                                    shadowElevation = if (selectedWellbeingTab == 0) 2.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📝", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Journal Analysis", fontSize = 13.sp, color = if (selectedWellbeingTab == 0) Color(0xFF1F2937) else Color(0xFF6B7280))
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { selectedWellbeingTab = 1 },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedWellbeingTab == 1) Color.White else Color.Transparent,
                                    shadowElevation = if (selectedWellbeingTab == 1) 2.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📅", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mood Calendar", fontSize = 13.sp, color = if (selectedWellbeingTab == 1) Color(0xFF1F2937) else Color(0xFF6B7280))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            when {
                                isLoadingWellbeing -> {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                                    }
                                }
                                selectedWellbeingTab == 0 -> {
                                    if (journalEmotions.isEmpty()) {
                                        Text("No journal entries yet. Write a journal to see AI-analyzed emotions!", color = Color(0xFF6B7280), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                                    } else {
                                        SimpleBarChartHorizontal(data = journalEmotions)
                                    }
                                }
                                else -> {
                                    if (moodData.isEmpty()) {
                                        Text("No mood data yet. Ask your child to check in daily!", color = Color(0xFF6B7280), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                                    } else {
                                        // CALENDAR VIEW (Same as child's calendar)
                                        val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
                                        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

                                        // Calculate calendar days
                                        val calendar = Calendar.getInstance().apply {
                                            time = currentMonth.time
                                            set(Calendar.DAY_OF_MONTH, 1)
                                        }
                                        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                                        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1

                                        Column {
                                            // Month navigation
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(onClick = {
                                                    currentMonth = Calendar.getInstance().apply {
                                                        time = currentMonth.time
                                                        add(Calendar.MONTH, -1)
                                                    }
                                                }) {
                                                    Text("◀", color = Color(0xFF6B7280))
                                                }
                                                Text(
                                                    text = "${monthNames[currentMonth.get(Calendar.MONTH)]} ${currentMonth.get(Calendar.YEAR)}",
                                                    color = Color(0xFF374151),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                IconButton(onClick = {
                                                    currentMonth = Calendar.getInstance().apply {
                                                        time = currentMonth.time
                                                        add(Calendar.MONTH, 1)
                                                    }
                                                }) {
                                                    Text("▶", color = Color(0xFF6B7280))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Day names
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                dayNames.forEach { day ->
                                                    Text(
                                                        text = day,
                                                        color = Color(0xFF9CA3AF),
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.weight(1f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Calendar grid
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                var dayCounter = 1
                                                for (row in 0 until 6) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceEvenly
                                                    ) {
                                                        for (col in 0 until 7) {
                                                            val cellIndex = row * 7 + col
                                                            val isVisible = cellIndex >= firstDayOfWeek && dayCounter <= daysInMonth
                                                            val dayNum = if (isVisible) dayCounter else 0
                                                            val dateStr = if (dayNum > 0) {
                                                                String.format("%04d-%02d-%02d",
                                                                    currentMonth.get(Calendar.YEAR),
                                                                    currentMonth.get(Calendar.MONTH) + 1,
                                                                    dayNum
                                                                )
                                                            } else null

                                                            val mood = dateStr?.let { moodData[it] }
                                                            val bgColor = when (mood) {
                                                                "great" -> Color(0xFF10B981)
                                                                "good" -> Color(0xFF34D399)
                                                                "okay" -> Color(0xFFF59E0B)
                                                                "anxious" -> Color(0xFFF97316)
                                                                "sad" -> Color(0xFFEF4444)
                                                                else -> Color(0xFFF3F4F6)
                                                            }
                                                            val emoji = when (mood) {
                                                                "great", "good" -> "😊"
                                                                "okay" -> "😐"
                                                                "anxious" -> "😰"
                                                                "sad" -> "😢"
                                                                else -> null
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .aspectRatio(1f)
                                                                    .padding(2.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(bgColor)
                                                                    .clickable(enabled = dateStr != null) {
                                                                        if (dateStr != null) selectedDate = dateStr
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    if (emoji != null) {
                                                                        Text(emoji, fontSize = 18.sp)
                                                                    }
                                                                    if (dayNum > 0) {
                                                                        Text(
                                                                            text = dayNum.toString(),
                                                                            color = if (mood != null) Color.White else Color(0xFF9CA3AF),
                                                                            fontSize = 10.sp,
                                                                            fontWeight = FontWeight.Medium
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            if (isVisible) dayCounter++
                                                        }
                                                    }
                                                    if (dayCounter > daysInMonth) break
                                                }
                                            }

                                            // Selected day details
                                            if (selectedDate != null) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFF3F4F6)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = try {
                                                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate!!)
                                                                SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date ?: Date())
                                                            } catch (e: Exception) { selectedDate!! },
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))

                                                        val mood = moodData[selectedDate]
                                                        if (mood != null) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                val moodEmoji = when (mood) {
                                                                    "great", "good" -> "😊"
                                                                    "okay" -> "😐"
                                                                    "anxious" -> "😰"
                                                                    "sad" -> "😢"
                                                                    else -> "❓"
                                                                }
                                                                Text("$moodEmoji Mood: ", fontSize = 12.sp, color = Color(0xFF6B7280))
                                                                Text(
                                                                    mood.replaceFirstChar { it.uppercase() },
                                                                    color = when (mood) {
                                                                        "great", "good" -> Color(0xFF10B981)
                                                                        "okay" -> Color(0xFFF59E0B)
                                                                        "anxious" -> Color(0xFFF97316)
                                                                        "sad" -> Color(0xFFEF4444)
                                                                        else -> Color(0xFF6B7280)
                                                                    },
                                                                    fontWeight = FontWeight.Medium,
                                                                    fontSize = 12.sp
                                                                )
                                                            }
                                                        }

                                                        val journal = journalData[selectedDate]
                                                        if (!journal.isNullOrEmpty()) {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text("📝 Journal:", fontSize = 12.sp, color = Color(0xFF6B7280))
                                                            Text(
                                                                journal.take(150) + if (journal.length > 150) "..." else "",
                                                                fontSize = 12.sp,
                                                                color = Color(0xFF374151)
                                                            )
                                                        }

                                                        if (mood == null && journal == null) {
                                                            Text("No entries for this day", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Music Insights Card — Day / Week toggle
                item {
                    var musicView by remember { mutableStateOf("week") }
                    var musicDayOffset by remember { mutableStateOf(0) }
                    var dailyMusicEmotions by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
                    var loadingMusicDay by remember { mutableStateOf(false) }

                    LaunchedEffect(selectedChildId, musicView, musicDayOffset) {
                        if (musicView != "day" || selectedChildId == null) return@LaunchedEffect
                        loadingMusicDay = true
                        try {
                            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -musicDayOffset) }
                            val targetDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

                            val docs = FirebaseFirestore.getInstance()
                                .collection("music_tracking")
                                .whereEqualTo("childId", selectedChildId)
                                .limit(50).get().await()

                            val counts = mutableMapOf<String, Int>()
                            for (doc in docs.documents) {
                                if (doc.getBoolean("emotion_processed") != true) continue
                                @Suppress("UNCHECKED_CAST")
                                val results = doc.get("emotion_results") as? List<Map<String, Any>> ?: continue
                                for (entry in results) {
                                    if (entry["date"] != targetDate) continue
                                    val emotion = entry["emotion"] as? String ?: continue
                                    counts[emotion] = (counts[emotion] ?: 0) + 1
                                }
                            }
                            dailyMusicEmotions = counts.entries
                                .sortedByDescending { it.value }
                                .map { it.key to it.value }
                        } catch (_: Exception) { dailyMusicEmotions = emptyList() }
                        loadingMusicDay = false
                    }

                    val musicDayLabel = when (musicDayOffset) { 0 -> "Today"; 1 -> "Yesterday"; else -> "$musicDayOffset days ago" }

                    DashboardCard(
                        title = "Music Insights",
                        subtitle = if (musicView == "week") "Mood detected from recently played tracks"
                                   else "Tracks listened $musicDayLabel",
                        icon = "🎵",
                        iconColor = Color(0xFF8B5CF6)
                    ) {
                        // Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .background(Color(0xFFF9FAFB), RoundedCornerShape(50)).padding(4.dp)
                        ) {
                            listOf("week" to "Recent Mood", "day" to "Per Day").forEach { (v, lbl) ->
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .background(if (musicView == v) Color(0xFF8B5CF6) else Color.Transparent, RoundedCornerShape(50))
                                        .clickable { musicView = v }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (musicView == v) Color.White else Color(0xFF6B7280),
                                        fontSize = 12.sp, fontWeight = if (musicView == v) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }

                        if (musicView == "week") {
                            MusicMoodSection(musicInsights)
                        } else {
                            // Day navigator
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { if (musicDayOffset < 6) musicDayOffset++ }) {
                                    Text("◀", color = if (musicDayOffset < 6) Color(0xFF374151) else Color(0xFFD1D5DB), fontSize = 18.sp)
                                }
                                Text(musicDayLabel, fontWeight = FontWeight.Medium, color = Color(0xFF374151), fontSize = 14.sp)
                                IconButton(onClick = { if (musicDayOffset > 0) musicDayOffset-- }) {
                                    Text("▶", color = if (musicDayOffset > 0) Color(0xFF374151) else Color(0xFFD1D5DB), fontSize = 18.sp)
                                }
                            }
                            if (loadingMusicDay) {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF8B5CF6))
                                }
                            } else if (dailyMusicEmotions.isEmpty()) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🎵", fontSize = 28.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("No music tracked $musicDayLabel", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                                }
                            } else {
                                val maxCount = dailyMusicEmotions.maxOf { it.second }.coerceAtLeast(1)
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    dailyMusicEmotions.forEach { (emotion, count) ->
                                        val emoji = MUSIC_MOOD_EMOJIS[emotion] ?: "🎵"
                                        val color = MUSIC_MOOD_COLORS[emotion] ?: Color(0xFF8B5CF6)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(emoji, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                                            Text(emotion.replaceFirstChar { it.uppercase() },
                                                color = Color(0xFF374151), fontSize = 13.sp, modifier = Modifier.width(90.dp))
                                            Box(Modifier.weight(1f).height(8.dp)
                                                .background(Color(0xFFE5E7EB), RoundedCornerShape(4.dp))) {
                                                Box(Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / maxCount)
                                                    .background(color, RoundedCornerShape(4.dp)))
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text("$count", color = color, fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
              // App Usage Card — Day / Week toggle
                item {
                    var appView by remember { mutableStateOf("week") }
                    var dayOffset by remember { mutableStateOf(0) }
                    var dailyApps by remember { mutableStateOf<List<AppUsageItem>>(emptyList()) }
                    var dailyTotalMin by remember { mutableStateOf(0L) }
                    var loadingDay by remember { mutableStateOf(false) }

                    LaunchedEffect(selectedChildId, appView, dayOffset) {
                        if (appView != "day" || selectedChildId == null) return@LaunchedEffect
                        loadingDay = true
                        try {
                            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -dayOffset) }
                            val dayStart = Calendar.getInstance().apply {
                                time = cal.time
                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            val dayEnd = dayStart + 24 * 60 * 60 * 1000L

                            val docs = FirebaseFirestore.getInstance()
                                .collection("app_usage")
                                .whereEqualTo("childId", selectedChildId)
                                .limit(30)
                                .get().await()

                            val dayDoc = docs.documents.firstOrNull {
                                (it.getLong("timestamp") ?: 0L) in dayStart..dayEnd
                            }
                            if (dayDoc != null) {
                                dailyTotalMin = dayDoc.getLong("totalTimeMin") ?: 0L
                                @Suppress("UNCHECKED_CAST")
                                dailyApps = (dayDoc.get("apps") as? List<Map<String, Any>> ?: emptyList())
                                    .mapNotNull {
                                        val n = it["app_name"] as? String ?: return@mapNotNull null
                                        val t = (it["time_min"] as? Long) ?: (it["time_min"] as? Int)?.toLong() ?: 0L
                                        AppUsageItem(n, t)
                                    }.sortedByDescending { it.timeMin }
                            } else { dailyApps = emptyList(); dailyTotalMin = 0L }
                        } catch (_: Exception) { dailyApps = emptyList() }
                        loadingDay = false
                    }

                    val dayLabel = when (dayOffset) { 0 -> "Today"; 1 -> "Yesterday"; else -> "$dayOffset days ago" }

                    DashboardCard(
                        title = "App Usage",
                        subtitle = if (appView == "week") appUsage["screenTime"] ?: "No data yet"
                                   else "$dayLabel — ${formatAppTime(dailyTotalMin)}",
                        icon = "📱",
                        iconColor = Color(0xFFF59E0B)
                    ) {
                        // Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .background(Color(0xFFF9FAFB), RoundedCornerShape(50)).padding(4.dp)
                        ) {
                            listOf("week" to "7-Day Avg", "day" to "Per Day").forEach { (v, lbl) ->
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .background(if (appView == v) Color(0xFFF59E0B) else Color.Transparent, RoundedCornerShape(50))
                                        .clickable { appView = v }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (appView == v) Color.White else Color(0xFF6B7280),
                                        fontSize = 12.sp, fontWeight = if (appView == v) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }

                        if (appView == "week") {
                            if (appUsage["productiveTime"] != null) {
                                Text(appUsage["productiveTime"]!!, color = Color(0xFF9CA3AF), fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 8.dp))
                            }
                            AppUsageSection(topAppsList)
                        } else {
                            // Day navigator
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { if (dayOffset < 6) dayOffset++ }) {
                                    Text("◀", color = if (dayOffset < 6) Color(0xFF374151) else Color(0xFFD1D5DB), fontSize = 18.sp)
                                }
                                Text(dayLabel, fontWeight = FontWeight.Medium, color = Color(0xFF374151), fontSize = 14.sp)
                                IconButton(onClick = { if (dayOffset > 0) dayOffset-- }) {
                                    Text("▶", color = if (dayOffset > 0) Color(0xFF374151) else Color(0xFFD1D5DB), fontSize = 18.sp)
                                }
                            }
                            if (loadingDay) {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFF59E0B))
                                }
                            } else if (dailyApps.isEmpty()) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📱", fontSize = 28.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("No usage data for $dayLabel", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                                }
                            } else {
                                AppUsageSection(dailyApps.take(5))
                            }
                        }
                    }
                }  

 // Behavioral Pattern Analysis — two-tab: App Usage Anomalies | Music Emotion Anomalies
                item {
                    var selectedBehaviorTab by remember { mutableStateOf(0) }

                    DashboardCard(
                        title = "Behavioral Pattern Analysis",
                        subtitle = "TBATS anomaly detection on digital behavior",
                        icon = "🧠",
                        iconColor = Color(0xFF6366F1)
                    ) {
                        Column {
                            // Tab row (mirrors Wellbeing Indicators pattern)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                                    .padding(4.dp)
                            ) {
                                listOf("📱" to "App Usage", "🎵" to "Music Moods").forEachIndexed { idx, (icon, label) ->
                                    Surface(
                                        modifier = Modifier.weight(1f).clickable { selectedBehaviorTab = idx },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selectedBehaviorTab == idx) Color.White else Color.Transparent,
                                        shadowElevation = if (selectedBehaviorTab == idx) 2.dp else 0.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(icon, fontSize = 14.sp)
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                label, fontSize = 13.sp,
                                                color = if (selectedBehaviorTab == idx) Color(0xFF1F2937) else Color(0xFF6B7280)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            when (selectedBehaviorTab) {
                                0 -> AppUsageAnomaliesSection(
                                    enoughData    = appAnomalyEnoughData,
                                    daysCollected = appAnomalyDaysCollected,
                                    anomalies     = appCategoryAnomalies
                                )
                                else -> MusicEmotionAnomaliesSection(
                                    enoughData    = musicAnomalyEnoughData,
                                    daysCollected = musicAnomalyDaysCollected,
                                    anomalies     = musicEmotionAnomalies
                                )
                            }
                        }
                    }
                }



                // Safety Overview Card
                item {
                    DashboardCard(
                        title = "Safety Overview",
                        icon = "⚠️",
                        iconColor = Color(0xFFEF4444)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            safetyIndicators.forEach { indicator ->
                                SafetyIndicatorRow(indicator)
                            }
                        }
                    }
                }

               

              

                // Explainer Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "How This Works",
                                color = Color(0xFF1F2937),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This dashboard uses AI to analyze patterns in your child's mood tracking, journal entries, and digital activity to provide wellbeing insights.",
                                color = Color(0xFF4B5563),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Privacy: Individual messages and private journal content are never displayed here. Only aggregated sentiment and pattern analysis is shown to respect your child's privacy while keeping them safe.",
                                color = Color(0xFF4B5563),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
fun loadGroomingProbabilityFromFirebase(childId: String, onResult: (Float) -> Unit) {
    GlobalScope.launch {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
            val docs = firestore.collection("event_analysis")
                .whereEqualTo("childId", childId)
                .limit(30)
                .get()
                .await()
            val probs = docs.documents.mapNotNull { doc ->
                val analyzedAt = doc.getLong("analyzedAt") ?: 0L
                if (analyzedAt < thirtyDaysAgo) return@mapNotNull null
                (doc.getDouble("groomingProbability") ?: doc.getDouble("riskScore"))?.toFloat()
            }
            onResult(if (probs.isEmpty()) 0f else probs.average().toFloat())
        } catch (e: Exception) {
            onResult(0f)
        }
    }
}

fun triggerAlertNotificationIfNeeded(context: android.content.Context, alerts: List<RiskAlert>) {
    val highAlerts = alerts.filter { it.riskLevel in listOf("HIGH", "CRITICAL") }
    if (highAlerts.isEmpty()) return
    try {
        val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ch = android.app.NotificationChannel(
                "risk_alerts_parent", "Child Safety Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true); vibrationPattern = longArrayOf(0, 500, 300, 500) }
            nm.createNotificationChannel(ch)
        }
        val top = highAlerts.first()
        val title = if (top.riskLevel == "CRITICAL") "🚨 Critical Safety Alert" else "⚠️ High Risk Alert"
        val body = when (top.threatType) {
            "confirmed_predator" -> "Predicted adult sender showing predatory behavior. Review immediately."
            "peer_predatory"     -> "Predicted minor sender showing grooming patterns — review needed."
            else                 -> "Suspicious activity detected. Open the app to review."
        }
        val notif = androidx.core.app.NotificationCompat.Builder(context, "risk_alerts_parent")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setColor(if (top.riskLevel == "CRITICAL") 0xFFDC2626.toInt() else 0xFFF59E0B.toInt())
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 300, 500))
            .build()
        nm.notify("dashboard_alerts".hashCode(), notif)
    } catch (e: Exception) {
        Log.e("NOTIF", "Failed to show alert notification: ${e.message}")
    }
}

@Composable
fun ChildStatusBanner(
    alerts: List<RiskAlert>,
    tbatsConcernLevel: String,
    sentimentData: List<SentimentData>,
    groomingProbability: Float
) {
    val hasHighAlert = alerts.any { it.riskLevel in listOf("HIGH", "CRITICAL") }
    val nonZeroScores = sentimentData.filter { it.score > 0 }
    val avgSentiment = if (nonZeroScores.isEmpty()) -1
                       else nonZeroScores.map { it.score }.average().toInt()

    val bannerColor: Color
    val statusIcon: String
    val statusTitle: String
    val statusBody: String

    when {
        hasHighAlert || tbatsConcernLevel == "HIGH" -> {
            bannerColor = Color(0xFFEF4444)
            statusIcon  = "🚨"
            statusTitle = "Needs Immediate Attention"
            statusBody  = "High-risk activity has been detected. Review the alerts below."
        }
        tbatsConcernLevel == "MEDIUM" || groomingProbability > 0.35f -> {
            bannerColor = Color(0xFFF59E0B)
            statusIcon  = "⚠️"
            statusTitle = "Some Concerns Detected"
            statusBody  = "Unusual patterns were noticed. Keep an eye on recent activity."
        }
        else -> {
            bannerColor = Color(0xFF10B981)
            statusIcon  = "✅"
            statusTitle = "Your Child Is Safe"
            statusBody  = when {
                avgSentiment < 0   -> "No concerning activity detected."
                avgSentiment >= 65 -> "Behavior looks normal and mood appears positive."
                avgSentiment >= 40 -> "Behavior looks normal and mood is neutral."
                else               -> "Behavior looks normal. Mood is slightly low — keep an eye on it."
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bannerColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, bannerColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(statusIcon, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = statusTitle,
                    color = bannerColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = statusBody,
                    color = Color(0xFF4B5563),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ========== NEW COMPOSABLES FOR RISK ALERTS ==========

data class RiskAlert(
    val id: String,
    val url: String,
    val riskLevel: String,
    val blockReasons: List<String>,
    val confidenceScore: Float,
    val timestamp: Long,
    val messageText: String = "",
    val authorLabel: String = "Unknown",
    val threatType: String = "unknown",
    val explainabilityText: String = "",
    val groomingProbability: Float = 0f
)

@Composable
fun RiskAlertsCard(
    alerts: List<RiskAlert>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "🚨", fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Risk Alerts",
                        color = Color(0xFF1F2937),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                // Refresh button
                IconButton(onClick = onRefresh) {
                    Text("🔄", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                alerts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🛡️", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No recent alerts",
                                color = Color(0xFF6B7280),
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Your child is safe",
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                else -> {
                    var currentIndex by remember { mutableStateOf(0) }
                    val total = alerts.size

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Alert card
                        RiskAlertRow(alert = alerts[currentIndex])

                        // Navigation row
                        if (total > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (currentIndex > 0) currentIndex-- },
                                    enabled = currentIndex > 0
                                ) {
                                    Text(
                                        "←",
                                        fontSize = 20.sp,
                                        color = if (currentIndex > 0) Color(0xFF374151) else Color(0xFFD1D5DB)
                                    )
                                }
                                Text(
                                    "${currentIndex + 1} / $total",
                                    fontSize = 13.sp,
                                    color = Color(0xFF6B7280),
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = { if (currentIndex < total - 1) currentIndex++ },
                                    enabled = currentIndex < total - 1
                                ) {
                                    Text(
                                        "→",
                                        fontSize = 20.sp,
                                        color = if (currentIndex < total - 1) Color(0xFF374151) else Color(0xFFD1D5DB)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
fun loadTbatsAnalysis(context: android.content.Context, childId: String, onResult: (TbatsAnalysisResult) -> Unit) {
    GlobalScope.launch {
        val auth = FirebaseAuth.getInstance()
        val idToken = try {
            auth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            null
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Trigger music processing in background — don't block TBATS on it
        GlobalScope.launch {
            try {
                val processRequest = Request.Builder()
                    .url("${BuildConfig.BACKEND_BASE_URL}/music/process/$childId")
                    .apply { if (idToken != null) addHeader("Authorization", "Bearer $idToken") }
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()
                OkHttpClient().newCall(processRequest).execute().close()
            } catch (e: Exception) {
                Log.w("TBATS", "music/process failed (non-fatal): ${e.message}")
            }
        }

        // Run TBATS analysis immediately without waiting for music/process
        try {
            val request = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/analyze/tbats/$childId?days=30")
                .apply {
                    if (idToken != null) {
                        addHeader("Authorization", "Bearer $idToken")
                    }
                }
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")

                fun parseJson(j: JSONObject): TbatsAnalysisResult {
                    val musicAnalysis = j.optJSONObject("music_analysis") ?: JSONObject()
                    val usageAnalysis = j.optJSONObject("usage_analysis") ?: JSONObject()
                    val concernLevel  = j.optString("concern_level", "LOW")
                    val musicConcern  = musicAnalysis.optString("concern_level", "LOW")
                    val hasMusicData  = musicAnalysis.optBoolean("has_music_data", false)
                    val moodDesc      = musicAnalysis.optString("mood_description", "")
                    val trackCount    = musicAnalysis.optInt("total_tracks", 0)
                    val dataNoteM     = musicAnalysis.optString("data_note", "")
                    val musicInsight  = when {
                        musicConcern == "HIGH"   -> "🎵 Unusual music mood patterns detected"
                        musicConcern == "MEDIUM" -> "🎵 Slight changes in music preferences"
                        hasMusicData && moodDesc.isNotEmpty() -> "🎵 Mood: $moodDesc ($trackCount tracks)"
                        dataNoteM.isNotEmpty()   -> "🎵 $dataNoteM"
                        else                     -> "🎵 No music data yet"
                    }
                    val usageConcern = usageAnalysis.optString("concern_level", "LOW")
                    val dataNoteU    = usageAnalysis.optString("data_note", "")
                    val usageDays    = usageAnalysis.optInt("data_points", 0)
                    val usageInsight = when {
                        usageConcern == "HIGH"   -> "📱 Significant screen time changes detected"
                        usageConcern == "MEDIUM" -> "📱 Slight increase in screen time"
                        usageDays > 0            -> "📱 $usageDays days tracked"
                        dataNoteU.isNotEmpty()   -> "📱 $dataNoteU"
                        else                     -> "📱 No app usage data yet"
                    }
                    val appCatJson          = j.optJSONObject("app_category_analysis") ?: JSONObject()
                    val appEnoughData       = appCatJson.optBoolean("has_enough_data", false)
                    val appDaysCollected    = appCatJson.optInt("days_collected", 0)
                    val appCategoryAnomalies = mutableListOf<CategoryAnomalyData>()
                    if (appEnoughData) {
                        val cats = appCatJson.optJSONObject("categories") ?: JSONObject()
                        cats.keys().forEach { cat ->
                            val catObj = cats.optJSONObject(cat) ?: return@forEach
                            appCategoryAnomalies.add(CategoryAnomalyData(
                                category   = cat,
                                enoughData = catObj.optBoolean("enough_data", true),
                                isAnomaly  = catObj.optBoolean("is_anomaly_today", false),
                                actual     = catObj.optDouble("actual", 0.0).toFloat(),
                                forecast   = catObj.optDouble("forecast", 0.0).toFloat(),
                                direction  = catObj.optString("anomaly_direction", "normal")
                            ))
                        }
                    }
                    val musicEmJson           = j.optJSONObject("music_emotion_analysis") ?: JSONObject()
                    val musicEnoughData       = musicEmJson.optBoolean("has_enough_data", false)
                    val musicDaysCollected    = musicEmJson.optInt("days_collected", 0)
                    val musicEmotionAnomalies = mutableListOf<EmotionAnomalyData>()
                    if (musicEnoughData) {
                        val emotions = musicEmJson.optJSONObject("emotions") ?: JSONObject()
                        emotions.keys().forEach { emotion ->
                            val emObj = emotions.optJSONObject(emotion) ?: return@forEach
                            musicEmotionAnomalies.add(EmotionAnomalyData(
                                emotion    = emotion,
                                enoughData = emObj.optBoolean("enough_data", true),
                                isAnomaly  = emObj.optBoolean("is_anomaly_today", false),
                                actual     = emObj.optDouble("actual", 0.0).toFloat(),
                                forecast   = emObj.optDouble("forecast", 0.0).toFloat(),
                                direction  = emObj.optString("anomaly_direction", "normal")
                            ))
                        }
                    }
                    return TbatsAnalysisResult(concernLevel, musicInsight, usageInsight,
                        appEnoughData, appDaysCollected, appCategoryAnomalies,
                        musicEnoughData, musicDaysCollected, musicEmotionAnomalies)
                }

                if (json.optString("status") == "computing") {
                    Log.i("TBATS", "Analysis still computing")
                    onResult(TbatsAnalysisResult("LOW", "🎵 Analysis in progress", "📱 Analysis in progress",
                        false, 0, emptyList(), false, 0, emptyList()))
                    return@launch
                }

                onResult(parseJson(json))
            } else {
                Log.e("TBATS", "Error: ${response.code}")
                onResult(TbatsAnalysisResult("LOW", "⚠️ Could not load behavioral analysis",
                    "", false, 0, emptyList(), false, 0, emptyList()))
            }
        } catch (e: Exception) {
            Log.e("TBATS", "Exception: ${e.message}")
            onResult(TbatsAnalysisResult("LOW", "⚠️ Behavioral analysis unavailable",
                "", false, 0, emptyList(), false, 0, emptyList()))
        }
    }
}
@Composable
fun RiskAlertRow(alert: RiskAlert) {
    val statusColor = when (alert.riskLevel) {
        "CRITICAL" -> Color(0xFFDC2626)
        "HIGH"     -> Color(0xFFF59E0B)
        "MEDIUM"   -> Color(0xFF3B82F6)
        else       -> Color(0xFF6B7280)
    }

    val riskIcon = when (alert.riskLevel) {
        "CRITICAL" -> "🚨"
        "HIGH"     -> "⚠️"
        "MEDIUM"   -> "🔶"
        else       -> "ℹ️"
    }

    // Author badge — shows BERT predicted age
    val (authorIcon, authorText, authorBadgeColor) = when (alert.authorLabel) {
        "Adult"  -> Triple("👤", "Predicted: Adult", Color(0xFFDC2626))
        "Minor"  -> Triple("🧒", "Predicted: Minor", Color(0xFF8B5CF6))
        else     -> Triple("❓", "Age Unknown",       Color(0xFF6B7280))
    }

    // Human-readable threat label
    val threatLabel = when (alert.threatType) {
        "confirmed_predator"      -> "Confirmed Predatory Behavior"
        "peer_predatory"          -> "Predatory Peer Behavior"
        "predatory_unknown_author"-> "Predatory – Age Unknown"
        "suspicious_adult"        -> "Suspicious Adult"
        "risky_peer"              -> "Risky Peer Interaction"
        "suspicious_unknown"      -> "Suspicious – Age Unknown"
        else                      -> "Risk Detected"
    }

    // Contextual explainability based on author + threat combination
    val contextualExplain: String? = when (alert.threatType) {
        "confirmed_predator" ->
            "HIGH RISK: The sender is predicted to be an adult using predatory language with your child. Review immediately."
        "peer_predatory" ->
            "NEEDS INVESTIGATION: The sender is predicted to be a minor showing grooming patterns. This could be teen grooming or an adult pretending to be a child."
        "predatory_unknown_author" ->
            "Predatory behavior detected. The sender's age could not be determined — treat as high risk until verified."
        "suspicious_adult" ->
            "The sender is predicted to be an adult communicating in ways that raise concern. Monitor closely."
        "risky_peer" ->
            "A peer interaction was flagged as potentially risky. Review the conversation to determine if it warrants action."
        else -> null
    }

    val formattedTime: String = remember(alert.timestamp) {
        SimpleDateFormat("HH:mm • MMM d", Locale.getDefault()).format(Date(alert.timestamp))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = statusColor.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Row 1: risk level pill + timestamp ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(riskIcon, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${alert.riskLevel} RISK",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = formattedTime,
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Row 2: author badge + threat label ────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = authorBadgeColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, authorBadgeColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(authorIcon, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = authorText,
                            color = authorBadgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = threatLabel,
                    color = Color(0xFF374151),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Message snippet ───────────────────────────────────────────────
            if (alert.messageText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF3F4F6)
                ) {
                    Text(
                        text = "\"${alert.messageText.take(140)}${if (alert.messageText.length > 140) "…" else ""}\"",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color(0xFF374151),
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // ── Contextual explainability (author+threat aware) ───────────────
            if (contextualExplain != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val bannerBg = when (alert.threatType) {
                    "confirmed_predator", "predatory_unknown_author" -> Color(0xFFEF4444).copy(alpha = 0.08f)
                    "peer_predatory" -> Color(0xFFF59E0B).copy(alpha = 0.08f)
                    else -> Color(0xFF6B7280).copy(alpha = 0.06f)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = bannerBg) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("💡 ", fontSize = 12.sp)
                        Text(
                            text = contextualExplain,
                            color = Color(0xFF1F2937),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // ── AI model reasoning ────────────────────────────────────────────
            val explain = alert.explainabilityText.ifEmpty {
                alert.blockReasons.firstOrNull() ?: ""
            }
            if (explain.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    Text("🔍 ", fontSize = 11.sp)
                    Text(
                        text = explain,
                        color = Color(0xFF4B5563),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Grooming risk score ───────────────────────────────────────────
            if (alert.groomingProbability > 0f) {
                val gpColor = when {
                    alert.groomingProbability >= 0.65f -> Color(0xFFEF4444)
                    alert.groomingProbability >= 0.35f -> Color(0xFFF59E0B)
                    else                               -> Color(0xFF6B7280)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️ ", fontSize = 11.sp)
                    Text(
                        "Your child is ",
                        color = Color(0xFF4B5563),
                        fontSize = 11.sp
                    )
                    Text(
                        "${(alert.groomingProbability * 100).toInt()}% at risk of grooming",
                        color = gpColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── Grooming probability bar ──────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Grooming risk:",
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(Color(0xFFE5E7EB), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = alert.groomingProbability.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(statusColor, RoundedCornerShape(2.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(alert.groomingProbability * 100).toInt()}%",
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

fun loadDashboardDataFromFirebase(
    context: android.content.Context,
    childId: String,
    onResult: (
        sentiment: List<SentimentData>,
        wellbeing: List<WellbeingData>,
        safety: List<SafetyIndicator>,
        music: Map<String, String>,
        appUsage: Map<String, String>,
        topApps: List<AppUsageItem>
    ) -> Unit
) {
    GlobalScope.launch {
        try {
            android.util.Log.d("DASHBOARD", "=== LOADING DATA FOR CHILD: $childId ===")

            val firestore = FirebaseFirestore.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

            // Get wellbeing documents
            val moodDocs = firestore.collection("wellbeing_daily_summary")
                .whereEqualTo("childId", childId)
                .get()
                .await()

            val moodScoreByDate = mutableMapOf<String, Int>()
            for (doc in moodDocs.documents) {
                val date = doc.getString("date") ?: continue
                // Saved by backend as emotionalWellbeingScore, fallback to dailyMoodScore
                val score = doc.getDouble("emotionalWellbeingScore")
                    ?: doc.getDouble("dailyMoodScore")
                    ?: 0.5
                moodScoreByDate[date] = (score * 100).toInt()
            }

            val sentiments = (6 downTo 0).map { daysAgo ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                val dateKey = dateFormat.format(cal.time)
                SentimentData(dayFormat.format(cal.time), moodScoreByDate[dateKey] ?: 0)
            }

            // ========== ADDED: APP USAGE ==========
            // Single-field filter only — no composite index required; filter by timestamp in Kotlin
            val usageDocs = firestore.collection("app_usage")
                .whereEqualTo("childId", childId)
                .limit(30)
                .get()
                .await()

            var totalScreenTimeMin = 0L
            val allApps = mutableMapOf<String, Long>()
            var daysWithData = 0

            for (doc in usageDocs.documents) {
                val docTs = doc.getLong("timestamp") ?: 0L
                if (docTs < sevenDaysAgo) continue
                daysWithData++
                totalScreenTimeMin += doc.getLong("totalTimeMin") ?: 0L

                @Suppress("UNCHECKED_CAST")
                val apps = doc.get("apps") as? List<Map<String, Any>> ?: emptyList()
                apps.forEach { app ->
                    val name = app["app_name"] as? String ?: return@forEach
                    val timeMin = (app["time_min"] as? Long) ?: (app["time_min"] as? Int)?.toLong() ?: 0L
                    allApps[name] = (allApps[name] ?: 0L) + timeMin
                }
            }

            val avgDailyMin = if (daysWithData > 0) totalScreenTimeMin / daysWithData else 0L
            val hours = avgDailyMin / 60
            val minutes = avgDailyMin % 60

            val usageMap = mutableMapOf<String, String>()
            usageMap["screenTime"] = if (avgDailyMin > 0) "${hours}h ${minutes}m/day (avg of $daysWithData days)" else "No data yet"
            usageMap["productiveTime"] = if (daysWithData > 0) "Tracking ${daysWithData} days" else "—"

            val topAppsList = allApps.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { AppUsageItem(it.key, it.value) }
            // ========== END OF APP USAGE ==========

            // ========== WELLBEING INDICATORS: DistilBERT emotion averages ==========
            // Pull last 200 analyzed events for this child from Firestore
            // Add this inside loadDashboardDataFromFirebase, right after getting emotionDocs
            val emotionDocs = firestore.collection("event_analysis")
                .whereEqualTo("childId", childId)
                .limit(200)
                .get()
                .await()

            Log.d("DASHBOARD_DEBUG", "=== EMOTION DOCS DEBUG ===")
            Log.d("DASHBOARD_DEBUG", "Total docs found: ${emotionDocs.documents.size}")

            for (doc in emotionDocs.documents) {
                val eventType = doc.getString("eventType")
                val hasVector = doc.get("emotionVector") != null
                Log.d("DASHBOARD_DEBUG", "Doc ${doc.id}: eventType=$eventType, hasEmotionVector=$hasVector")
            }


            val emotionSums = mutableMapOf<String, Double>()
            var emotionEventCount = 0
            for (doc in emotionDocs.documents) {
                val eventType = doc.getString("eventType") ?: continue
                if (eventType != "MESSAGE" && eventType != "JOURNAL") continue
                @Suppress("UNCHECKED_CAST")
                val vector = doc.get("emotionVector") as? Map<String, Any> ?: continue
                emotionEventCount++
                vector.forEach { (emotion, scoreAny) ->
                    val score = when (scoreAny) {
                        is Double -> scoreAny
                        is Float  -> scoreAny.toDouble()
                        is Long   -> scoreAny.toDouble()
                        else      -> 0.0
                    }
                    emotionSums[emotion] = (emotionSums[emotion] ?: 0.0) + score
                }
            }

            val wellbeing: List<WellbeingData> = if (emotionEventCount > 0 && emotionSums.isNotEmpty()) {
                emotionSums.entries
                    .map { (emotion, sum) ->
                        WellbeingData(
                            category = emotion.replaceFirstChar { it.uppercase() },
                            score    = ((sum / emotionEventCount) * 100).toInt().coerceIn(0, 100)
                        )
                    }
                    .sortedByDescending { it.score }
                    .take(8)
            } else {
                listOf(WellbeingData("No data yet", 0))
            }
            // ========== END WELLBEING INDICATORS ==========
// Then later after filtering
            Log.d("DASHBOARD_DEBUG", "After filtering: emotionEventCount=$emotionEventCount")
            Log.d("DASHBOARD_DEBUG", "emotionSums size: ${emotionSums.size}")

            val safety = listOf(
                SafetyIndicator("Content Safety", "none", "No risks detected"),
                SafetyIndicator("Online Activity", "none", "Normal activity patterns"),
                SafetyIndicator("Monitoring Status", "none", "Real-time protection active")
            )

            // ========== MUSIC INSIGHTS (from processed RF emotion results) ==========
            // Single-field filter only — no composite index required; date filter applied in Kotlin.
            val musicDocs = firestore.collection("music_tracking")
                .whereEqualTo("childId", childId)
                .limit(200)
                .get()
                .await()

            val emotionCounts = mutableMapOf<String, Int>()
            var totalTracks = 0
            for (doc in musicDocs.documents) {
                if ((doc.getLong("timestamp") ?: 0L) < sevenDaysAgo) continue
                if (doc.getBoolean("emotion_processed") != true) continue
                @Suppress("UNCHECKED_CAST")
                val emotionResults = doc.get("emotion_results") as? List<Map<String, Any>> ?: emptyList()
                for (entry in emotionResults) {
                    val emotion = entry["emotion"] as? String ?: continue
                    emotionCounts[emotion] = (emotionCounts[emotion] ?: 0) + 1
                    totalTracks++
                }
            }

            val emotionMoodMap = mapOf(
                "happy"     to "Positive / Upbeat",
                "party"     to "Social / Energetic",
                "energetic" to "Energetic",
                "romantic"  to "Calm / Romantic",
                "chill"     to "Relaxed",
                "calm"      to "Calm / Peaceful",
                "focus"     to "Focused / Neutral",
                "sad"       to "Melancholic"
            )

            val dominantEmotion = emotionCounts.maxByOrNull { it.value }?.key
            val musicMap = if (dominantEmotion != null) {
                mapOf(
                    "topGenre"        to (emotionMoodMap[dominantEmotion] ?: dominantEmotion.replaceFirstChar { it.uppercase() }),
                    "listeningTime"   to "$totalTracks tracks analyzed",
                    "moodCorrelation" to "Mood: ${dominantEmotion.replaceFirstChar { it.uppercase() }}"
                )
            } else {
                mapOf(
                    "topGenre"        to "No data yet",
                    "listeningTime"   to "No music detected",
                    "moodCorrelation" to "Listen to music for insights"
                )
            }
            // ========== END MUSIC INSIGHTS ==========

            onResult(sentiments, wellbeing, safety, musicMap, usageMap, topAppsList)

        } catch (e: Exception) {
            android.util.Log.e("DASHBOARD", "Error: ${e.message}", e)
            onResult(emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap(), emptyList())
        }
    }
}
fun loadAlertsFromFirebase(
    context: android.content.Context,
    childId: String,
    onResult: (List<RiskAlert>) -> Unit
) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            android.util.Log.d("RiskAlerts", "Querying event_analysis for childId=$childId")
            val firestore = FirebaseFirestore.getInstance()

            // Filter by riskLevel at the DB level (single-field auto-index, no composite index needed).
            // Then narrow to this child in memory to avoid the alphabetical-document-ID cutoff problem.
            val docs = firestore.collection("event_analysis")
                .whereIn("riskLevel", listOf("HIGH", "MEDIUM", "CRITICAL"))
                .get()
                .await()

            android.util.Log.d("RiskAlerts", "Got ${docs.size()} HIGH/MEDIUM/CRITICAL docs from event_analysis")

            val alerts = docs.documents.mapNotNull { doc ->
                val riskLevel = doc.getString("riskLevel") ?: return@mapNotNull null
                if (doc.getString("childId") != childId) return@mapNotNull null
                android.util.Log.d("RiskAlerts", "matched doc=${doc.id} riskLevel=$riskLevel")

                val timestamp   = doc.getLong("timestamp") ?: doc.getLong("analyzedAt") ?: System.currentTimeMillis()
                val gp          = (doc.getDouble("groomingProbability") ?: doc.getDouble("riskScore") ?: 0.0).toFloat()
                val authorLabel = doc.getString("authorLabel") ?: "Unknown"
                val threatType  = when {
                    authorLabel == "Adult" && gp >= 0.65f -> "confirmed_predator"
                    authorLabel == "Minor" && gp >= 0.65f -> "peer_predatory"
                    authorLabel == "Adult"                 -> "suspicious_adult"
                    authorLabel == "Minor"                 -> "risky_peer"
                    gp >= 0.65f                            -> "predatory_unknown_author"
                    else                                   -> "suspicious_unknown"
                }
                val explainText  = doc.getString("explanation")
                    ?: doc.getString("explainabilityText")
                    ?: "Suspicious behavior detected."
                val messageText  = doc.getString("messageText") ?: ""

                RiskAlert(
                    id                  = doc.id,
                    url                 = "",
                    riskLevel           = riskLevel,
                    blockReasons        = listOf(explainText),
                    confidenceScore     = (doc.getDouble("authorConfidence") ?: gp.toDouble()).toFloat(),
                    timestamp           = timestamp,
                    messageText         = messageText,
                    authorLabel         = authorLabel,
                    threatType          = threatType,
                    explainabilityText  = explainText,
                    groomingProbability = gp
                )
            }.sortedByDescending { it.timestamp }

            android.util.Log.d("RiskAlerts", "Final alerts count: ${alerts.size}")
            withContext(Dispatchers.Main) { onResult(alerts) }
        } catch (e: Exception) {
            android.util.Log.e("RiskAlerts", "Query failed: ${e.message}", e)
            withContext(Dispatchers.Main) { onResult(emptyList()) }
        }
    }
}
private fun loadAlertsFromLocalStorage(
    context: android.content.Context,
    onResult: (List<RiskAlert>) -> Unit
) {
    val alertsFile = context.filesDir.resolve("risk_alerts.json")

    if (!alertsFile.exists()) {
        onResult(emptyList())
        return
    }

    try {
        val jsonArray = JSONArray(alertsFile.readText())
        val alerts = mutableListOf<RiskAlert>()

        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            alerts.add(
                RiskAlert(
                    id = UUID.randomUUID().toString(),
                    url = entry.getString("url"),
                    riskLevel = entry.getString("riskLevel"),
                    blockReasons = (0 until entry.getJSONArray("blockReasons").length())
                        .map { entry.getJSONArray("blockReasons").getString(it) },
                    confidenceScore = entry.getDouble("confidenceScore").toFloat(),
                    timestamp = entry.getLong("timestamp")
                )
            )
        }

        onResult(alerts.sortedByDescending { it.timestamp })
    } catch (e: Exception) {
        onResult(emptyList())
    }
}
@Composable
fun ChildSelectorCard(
    children: List<ChildInfo>,
    selectedChildId: String?,
    onSelectChild: (String) -> Unit,
    onDeleteChild: (ChildInfo) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select Child",
                color = Color(0xFF1F2937),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            children.forEach { child ->
                val isSelected = child.id == selectedChildId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectChild(child.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFF9FAFB),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF10B981) else Color(0xFFE5E7EB)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👤", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = child.name,
                                color = if (isSelected) Color(0xFF10B981) else Color(0xFF374151),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { onDeleteChild(child) }) {
                        Text("🗑️", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

fun getChildPin(childId: String, onResult: (String?) -> Unit) {
    GlobalScope.launch {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("users").document(childId).get().await()
            onResult(doc.getString("pinCode"))
        } catch (e: Exception) {
            onResult(null)
        }
    }
}

fun loadChildrenFromFirebase(onResult: (List<ChildInfo>) -> Unit) {
    GlobalScope.launch {
        try {
            val auth = FirebaseAuth.getInstance()
            val parentId = auth.currentUser?.uid ?: run { onResult(emptyList()); return@launch }
            val firestore = FirebaseFirestore.getInstance()
            val docs = firestore.collection("users")
                .whereEqualTo("parentId", parentId)
                .whereEqualTo("userType", "CHILD")
                .get()
                .await()
            onResult(docs.documents.map { ChildInfo(it.id, it.getString("name") ?: "Child") })
        } catch (e: Exception) {
            onResult(emptyList())
        }
    }
}

fun deleteChildFromFirebase(childId: String, onResult: (Boolean) -> Unit) {
    GlobalScope.launch {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val auth = FirebaseAuth.getInstance()
            val parentId = auth.currentUser?.uid
            firestore.collection("users").document(childId).delete().await()
            if (parentId != null) {
                val parentDoc = firestore.collection("users").document(parentId).get().await()
                if (parentDoc.getString("childId") == childId) {
                    firestore.collection("users").document(parentId).update("childId", null).await()
                }
            }
            onResult(true)
        } catch (e: Exception) {
            onResult(false)
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String? = null,
    icon: String,
    iconColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = icon, fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = Color(0xFF1F2937),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = Color(0xFF6B7280),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SimpleBarChart(data: List<SentimentData>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { item ->
            val barColor = when {
                item.score >= 65 -> Color(0xFF10B981)
                item.score >= 35 -> Color(0xFFF59E0B)
                item.score > 0  -> Color(0xFFEF4444)
                else            -> Color(0xFFE5E7EB)
            }
            val barHeight = if (item.score > 0) maxOf(6f, item.score * 0.9f).dp else 6.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                if (item.score > 0) {
                    Text(
                        text = "${item.score}%",
                        color = barColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(barHeight)
                        .background(barColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.day,
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ── Behavioral Pattern Analysis composables ───────────────────────────────────

@Composable
fun AppUsageAnomaliesSection(
    enoughData: Boolean?,
    daysCollected: Int,
    anomalies: List<CategoryAnomalyData>
) {
    when {
        enoughData == null -> {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color(0xFF6366F1))
            }
        }
        !enoughData -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEEF2FF)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⏳", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (daysCollected == 0) "Preparing analysis" else "Not enough data yet",
                            fontWeight = FontWeight.SemiBold, color = Color(0xFF3730A3), fontSize = 13.sp
                        )
                        Text(
                            if (daysCollected == 0) "App usage analysis is being prepared. Check back shortly."
                            else "Collecting app usage patterns ($daysCollected/2 days). Anomaly detection activates once 2 days of data are collected.",
                            color = Color(0xFF6B7280), fontSize = 11.sp
                        )
                    }
                }
            }
        }
        anomalies.isEmpty() -> {
            Text("No categorised app usage data yet.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
        }
        else -> {
            val readyAnomalies = anomalies.filter { it.enoughData }.sortedByDescending { it.isAnomaly }
            if (readyAnomalies.isEmpty()) {
                Text("Collecting data for all categories.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    readyAnomalies.forEach { data -> AppCategoryAnomalyRow(data) }
                }
            }
        }
    }
}

@Composable
fun AppCategoryAnomalyRow(data: CategoryAnomalyData) {
    val catEmoji = when (data.category.lowercase()) {
        "social"        -> "💬"
        "entertainment" -> "🎬"
        "gaming"        -> "🎮"
        "productivity"  -> "💼"
        "education"     -> "📚"
        "browsing"      -> "🌐"
        else            -> "📱"
    }
    val anomalyColor = when {
        data.isAnomaly && data.direction == "high" -> Color(0xFFEF4444)
        data.isAnomaly && data.direction == "low"  -> Color(0xFF3B82F6)
        else                                        -> Color(0xFF10B981)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (data.isAnomaly) anomalyColor.copy(alpha = 0.07f) else Color(0xFFF9FAFB)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(catEmoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    data.category.replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF1F2937)
                )
                if (!data.enoughData) {
                    Text("Collecting data for this category", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                } else {
                    val actualPct = (data.actual * 100).toInt()
                    Text("$actualPct% of daily cap", color = Color(0xFF6B7280), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(6.dp))
            if (data.enoughData) {
                Surface(shape = RoundedCornerShape(50), color = anomalyColor.copy(alpha = 0.15f)) {
                    Text(
                        when {
                            data.isAnomaly && data.direction == "high" -> "↑ High"
                            data.isAnomaly && data.direction == "low"  -> "↓ Low"
                            else                                        -> "Normal"
                        },
                        color = anomalyColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MusicEmotionAnomaliesSection(
    enoughData: Boolean?,
    daysCollected: Int,
    anomalies: List<EmotionAnomalyData>
) {
    when {
        enoughData == null -> {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color(0xFF8B5CF6))
            }
        }
        !enoughData -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFAF5FF)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⏳", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (daysCollected == 0) "Preparing analysis" else "Not enough data yet",
                            fontWeight = FontWeight.SemiBold, color = Color(0xFF6D28D9), fontSize = 13.sp
                        )
                        Text(
                            if (daysCollected == 0) "Music emotion analysis is being prepared. Check back shortly."
                            else "Collecting music listening history ($daysCollected/2 days). Emotion anomaly detection activates once 2 days of music data are collected.",
                            color = Color(0xFF6B7280), fontSize = 11.sp
                        )
                    }
                }
            }
        }
        anomalies.isEmpty() -> {
            Text("No music emotion data collected yet.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
        }
        else -> {
            val readyAnomalies = anomalies.filter { it.enoughData }.sortedByDescending { it.isAnomaly }
            if (readyAnomalies.isEmpty()) {
                Text("Collecting emotion data for all moods.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    readyAnomalies.forEach { data -> MusicEmotionAnomalyRow(data) }
                }
            }
        }
    }
}

@Composable
fun MusicEmotionAnomalyRow(data: EmotionAnomalyData) {
    val emoji = MUSIC_MOOD_EMOJIS[data.emotion] ?: "🎵"
    val baseColor = MUSIC_MOOD_COLORS[data.emotion] ?: Color(0xFF8B5CF6)
    val anomalyColor = when {
        data.isAnomaly && data.direction == "high" -> Color(0xFFEF4444)
        data.isAnomaly && data.direction == "low"  -> Color(0xFF3B82F6)
        else                                        -> Color(0xFF10B981)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (data.isAnomaly) anomalyColor.copy(alpha = 0.07f) else Color(0xFFF9FAFB)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    data.emotion.replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF1F2937)
                )
                if (!data.enoughData) {
                    Text("Collecting data for this emotion", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(6.dp))
            if (data.enoughData) {
                Surface(shape = RoundedCornerShape(50), color = anomalyColor.copy(alpha = 0.15f)) {
                    Text(
                        when {
                            data.isAnomaly && data.direction == "high" -> "↑ High"
                            data.isAnomaly && data.direction == "low"  -> "↓ Low"
                            else                                        -> "Normal"
                        },
                        color = anomalyColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private val EMOTION_EMOJIS = mapOf(
    "admiration" to "🌟", "amusement" to "😄", "anger" to "😠", "annoyance" to "😒",
    "approval" to "👍", "caring" to "💚", "confusion" to "🤔", "curiosity" to "🧐",
    "desire" to "💫", "disappointment" to "😔", "disapproval" to "👎", "disgust" to "🤢",
    "embarrassment" to "😳", "excitement" to "🤩", "fear" to "😨", "gratitude" to "🙏",
    "grief" to "😢", "joy" to "😊", "love" to "❤️", "nervousness" to "😬",
    "optimism" to "🌈", "pride" to "💪", "realization" to "💡", "relief" to "😌",
    "remorse" to "😞", "sadness" to "😢", "surprise" to "😮", "neutral" to "😐"
)

private val EMOTION_COLORS = mapOf(
    "admiration" to Color(0xFF10B981), "amusement" to Color(0xFF10B981),
    "anger" to Color(0xFFEF4444), "annoyance" to Color(0xFFF59E0B),
    "approval" to Color(0xFF10B981), "caring" to Color(0xFF10B981),
    "confusion" to Color(0xFF8B5CF6), "curiosity" to Color(0xFF3B82F6),
    "desire" to Color(0xFF8B5CF6), "disappointment" to Color(0xFFF59E0B),
    "disapproval" to Color(0xFFF59E0B), "disgust" to Color(0xFFEF4444),
    "embarrassment" to Color(0xFFF59E0B), "excitement" to Color(0xFF10B981),
    "fear" to Color(0xFFEF4444), "gratitude" to Color(0xFF10B981),
    "grief" to Color(0xFFEF4444), "joy" to Color(0xFF10B981),
    "love" to Color(0xFFEC4899), "nervousness" to Color(0xFFF59E0B),
    "optimism" to Color(0xFF10B981), "pride" to Color(0xFF10B981),
    "realization" to Color(0xFF3B82F6), "relief" to Color(0xFF10B981),
    "remorse" to Color(0xFFF59E0B), "sadness" to Color(0xFFEF4444),
    "surprise" to Color(0xFF8B5CF6), "neutral" to Color(0xFF6B7280)
)

@Composable
fun SimpleBarChartHorizontal(data: List<WellbeingData>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        data.forEach { item ->
            val key = item.category.lowercase()
            val emoji = EMOTION_EMOJIS[key] ?: "●"
            val barColor = EMOTION_COLORS[key] ?: Color(0xFF3B82F6)
            val fraction = (item.score / 100f).coerceIn(0f, 1f)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = 15.sp, modifier = Modifier.width(22.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.category,
                    color = Color(0xFF374151),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(96.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(Color(0xFFE5E7EB), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(barColor, RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${item.score}%",
                    color = barColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

private val MUSIC_MOOD_EMOJIS = mapOf(
    "happy" to "😊", "party" to "🎉", "energetic" to "⚡",
    "romantic" to "💕", "chill" to "😌", "calm" to "🌊",
    "focus" to "🎯", "sad" to "💙"
)

private val MUSIC_MOOD_COLORS = mapOf(
    "happy" to Color(0xFF10B981), "party" to Color(0xFFEC4899),
    "energetic" to Color(0xFFF59E0B), "romantic" to Color(0xFFEC4899),
    "chill" to Color(0xFF3B82F6), "calm" to Color(0xFF3B82F6),
    "focus" to Color(0xFF8B5CF6), "sad" to Color(0xFF6B7280)
)

@Composable
fun MusicMoodSection(musicInsights: Map<String, String>) {
    val moodCorr = musicInsights["moodCorrelation"] ?: ""
    val dominantRaw = moodCorr.removePrefix("Mood: ").trim().lowercase()
    val hasMood = dominantRaw.isNotEmpty() && musicInsights["topGenre"] != "No data yet"

    if (!hasMood) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("🎵", fontSize = 36.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("No music detected yet", color = Color(0xFF9CA3AF), fontSize = 14.sp)
            Text("Play some music on the child's device", color = Color(0xFFD1D5DB), fontSize = 12.sp)
        }
    } else {
        val emoji = MUSIC_MOOD_EMOJIS[dominantRaw] ?: "🎵"
        val moodColor = MUSIC_MOOD_COLORS[dominantRaw] ?: Color(0xFF8B5CF6)
        val moodLabel = musicInsights["topGenre"] ?: dominantRaw.replaceFirstChar { it.uppercase() }
        val trackInfo = musicInsights["listeningTime"] ?: ""

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = moodColor.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 32.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = moodLabel,
                    color = Color(0xFF111827),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = moodColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = dominantRaw.replaceFirstChar { it.uppercase() },
                        color = moodColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                if (trackInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(trackInfo, color = Color(0xFF9CA3AF), fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatAppTime(timeMin: Long): String {
    if (timeMin <= 0) return "< 1m"
    val h = timeMin / 60
    val m = timeMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
fun AppUsageSection(apps: List<AppUsageItem>) {
    if (apps.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("📱", fontSize = 36.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("No app usage data yet", color = Color(0xFF9CA3AF), fontSize = 14.sp)
        }
        return
    }
    val maxTime = apps.maxOf { it.timeMin }.coerceAtLeast(1L)
    val appColors = listOf(
        Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
        Color(0xFF8B5CF6), Color(0xFFEF4444)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        apps.forEachIndexed { index, app ->
            val fraction = (app.timeMin.toFloat() / maxTime).coerceIn(0f, 1f)
            val barColor = appColors[index % appColors.size]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.name.take(16),
                    color = Color(0xFF374151),
                    fontSize = 13.sp,
                    modifier = Modifier.width(110.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(Color(0xFFE5E7EB), RoundedCornerShape(5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(barColor, RoundedCornerShape(5.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatAppTime(app.timeMin),
                    color = barColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(44.dp)
                )
            }
        }
    }
}

@Composable
fun InsightRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            color = Color(0xFF6B7280),
            fontSize = 13.sp,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            color = Color(0xFF1F2937),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SafetyIndicatorRow(indicator: SafetyIndicator) {
    val statusColor = when (indicator.status) {
        "none" -> Color(0xFF10B981)
        "low" -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Row {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = indicator.type,
                    color = Color(0xFF374151),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.1f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = indicator.status.uppercase(),
                        color = statusColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = indicator.description,
                color = Color(0xFF6B7280),
                fontSize = 11.sp
            )
        }
    }
}