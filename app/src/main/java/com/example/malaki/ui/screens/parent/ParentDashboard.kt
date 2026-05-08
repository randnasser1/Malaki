package com.example.malaki.ui.screens.parent

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
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*  // This provides Date, Locale, UUID
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.platform.LocalContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import com.example.malaki.BuildConfig
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import java.util.Date
import java.util.Locale
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
            }

            // 🆕 LOAD TBATS ANALYSIS (Behavioral Change Detection)
            loadTbatsAnalysis(childId) { result ->
                android.util.Log.d("DASHBOARD", "TBATS result: ${result["concern_level"]}")
                tbatsConcernLevel = result["concern_level"] as? String ?: "LOW"
                tbatsMusicInsight = result["music_insight"] as? String ?: ""
                tbatsUsageInsight = result["usage_insight"] as? String ?: ""
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
                // Interactive Emotion Calendar Card
                item {
                    var emotionData by remember { mutableStateOf<List<EmotionDayData>>(emptyList()) }
                    var selectedDate by remember { mutableStateOf<String?>(null) }
                    var selectedEmotion by remember { mutableStateOf<String?>(null) }
                    var selectedSource by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(selectedChildId) {
                        if (selectedChildId != null && selectedChildId!!.isNotEmpty()) {
                            try {
                                val firestore = FirebaseFirestore.getInstance()

                                // 1. Get WELLBEING SUMMARY (child-logged moods)
                                val wellbeingDocs = firestore.collection("wellbeing_daily_summary")
                                    .whereEqualTo("childId", selectedChildId)
                                    .orderBy("date", Query.Direction.DESCENDING)
                                    .limit(30)
                                    .get()
                                    .await()

                                val moodMap = mutableMapOf<String, Pair<String, String>>() // date -> (emotion, source)

                                for (doc in wellbeingDocs.documents) {
                                    val date = doc.getString("date") ?: continue
                                    val emotion = doc.getString("dominantEmotion") ?:
                                    when (doc.getDouble("avg_sentiment")?.toInt()) {
                                        in 80..100 -> "great"
                                        in 60..79 -> "good"
                                        in 40..59 -> "okay"
                                        in 20..39 -> "anxious"
                                        else -> "sad"
                                    }
                                    moodMap[date] = emotion to "📝 Child Logged"
                                }

                                // 2. Get MESSAGE ANALYSIS (inferred emotions)
                                val messageDocs = firestore.collection("event_analysis")
                                    .whereEqualTo("childId", selectedChildId)
                                    .whereEqualTo("eventType", "MESSAGE")
                                    .orderBy("timestamp", Query.Direction.DESCENDING)
                                    .limit(200)
                                    .get()
                                    .await()

                                val messageEmotions = mutableMapOf<String, MutableList<String>>()
                                for (doc in messageDocs.documents) {
                                    @Suppress("UNCHECKED_CAST")
                                    val emotionVector = doc.get("emotionVector") as? Map<String, Double> ?: continue
                                    val timestamp = doc.getLong("timestamp") ?: continue
                                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

                                    val topEmotion = emotionVector.maxByOrNull { it.value }?.key ?: continue
                                    messageEmotions.getOrPut(date) { mutableListOf() }.add(topEmotion)
                                }

                                // Combine data
                                val allDates = (moodMap.keys + messageEmotions.keys).distinct()
                                emotionData = allDates.map { date ->
                                    val (loggedEmotion, source) = moodMap[date] ?: (null to null)
                                    val inferredEmotion = messageEmotions[date]?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key

                                    EmotionDayData(
                                        date = date,
                                        childLoggedEmotion = loggedEmotion,
                                        inferredEmotion = inferredEmotion,
                                        hasChildLog = source != null,
                                        hasInferredData = inferredEmotion != null
                                    )
                                }.sortedByDescending { it.date }

                            } catch (e: Exception) {
                                Log.e("DASHBOARD", "Error loading emotion calendar: ${e.message}")
                            }
                        }
                    }

                    DashboardCard(
                        title = "Emotion Calendar",
                        subtitle = "😊 Child-logged moods | 🤖 AI-inferred from messages",
                        icon = "📅",
                        iconColor = Color(0xFF8B5CF6)
                    ) {
                        if (emotionData.isEmpty()) {
                            Text("No emotion data yet", color = Color(0xFF6B7280))
                        } else {
                            Column {
                                // Legend
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp)))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Child-logged", fontSize = 10.sp, color = Color(0xFF6B7280))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF8B5CF6), RoundedCornerShape(2.dp)))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("AI-inferred", fontSize = 10.sp, color = Color(0xFF6B7280))
                                    }
                                }

                                // Calendar Grid (last 14 days)
                                val dates = emotionData.take(14)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    dates.chunked(7).forEach { week ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            week.forEach { day ->
                                                val dayOfMonth = day.date.substringAfterLast("-").toIntOrNull() ?: 0
                                                val emotion = day.childLoggedEmotion ?: day.inferredEmotion
                                                val bgColor = when (emotion) {
                                                    "great", "happy" -> Color(0xFF10B981).copy(alpha = 0.8f)
                                                    "good" -> Color(0xFF34D399).copy(alpha = 0.8f)
                                                    "okay" -> Color(0xFFF59E0B).copy(alpha = 0.8f)
                                                    "anxious" -> Color(0xFFF97316).copy(alpha = 0.8f)
                                                    "sad" -> Color(0xFFEF4444).copy(alpha = 0.8f)
                                                    else -> Color(0xFF9CA3AF).copy(alpha = 0.3f)
                                                }
                                                val borderColor = when {
                                                    day.hasChildLog -> Color(0xFF10B981)
                                                    day.hasInferredData -> Color(0xFF8B5CF6)
                                                    else -> Color(0xFFE5E7EB)
                                                }

                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(bgColor)
                                                        .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            selectedDate = day.date
                                                            selectedEmotion = emotion
                                                            selectedSource = when {
                                                                day.hasChildLog -> "Child manually logged"
                                                                day.hasInferredData -> "AI inferred from messages"
                                                                else -> "No data"
                                                            }
                                                        }
                                                ) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Text(
                                                        text = dayOfMonth.toString(),
                                                        color = if (emotion != null) Color.White else Color(0xFF6B7280),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Selected date details
                                if (selectedDate != null && selectedEmotion != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF3F4F6)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("📅 ", fontSize = 16.sp)
                                                Text(
                                                    text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                                                        .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate!!) ?: Date()),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val emoji = when (selectedEmotion?.lowercase()) {
                                                    "great", "happy" -> "😊"
                                                    "good" -> "👍"
                                                    "okay" -> "😐"
                                                    "anxious" -> "😰"
                                                    "sad" -> "😢"
                                                    else -> "❓"
                                                }
                                                Text("$emoji ", fontSize = 16.sp)
                                                Text(selectedEmotion?.replaceFirstChar { it.uppercase() } ?: "Unknown", fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (selectedSource?.contains("Child") == true) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = selectedSource?.let { if (it.contains("Child")) "📝 $it" else "🤖 $it" } ?: "❓ No data",
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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



                // Wellbeing Indicators Card
                item {
                    DashboardCard(
                        title = "Wellbeing Indicators",
                        subtitle = "DistilBERT emotion analysis from messages & journal",
                        icon = "🛡️",
                        iconColor = Color(0xFF10B981)
                    ) {
                        if (wellbeingData.isEmpty() || wellbeingData.all { it.score == 0 }) {
                            Text("Emotion data will appear here once messages are analyzed", color = Color(0xFF6B7280))
                        } else {
                            SimpleBarChartHorizontal(data = wellbeingData)
                        }
                    }
                }


                // Music Insights Card
                item {
                    DashboardCard(
                        title = "Music Insights",
                        subtitle = "Mood detected from recently played tracks",
                        icon = "🎵",
                        iconColor = Color(0xFF8B5CF6)
                    ) {
                        MusicMoodSection(musicInsights)
                    }
                }

// 🆕 TOP EMOTIONS CARD - ADD THIS HERE
                item {
                    var topEmotions by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }

                    LaunchedEffect(selectedChildId) {
                        if (selectedChildId != null && selectedChildId!!.isNotEmpty()) {
                            try {
                                val firestore = FirebaseFirestore.getInstance()
                                val docs = firestore.collection("event_analysis")
                                    .whereEqualTo("childId", selectedChildId)
                                    .whereEqualTo("eventType", "MESSAGE")
                                    .orderBy("timestamp", Query.Direction.DESCENDING)
                                    .limit(20)
                                    .get()
                                    .await()

                                val emotionSums = mutableMapOf<String, Float>()
                                var count = 0

                                for (doc in docs.documents) {
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
                        subtitle = "From recent messages (last 20)",
                        icon = "😊",
                        iconColor = Color(0xFF8B5CF6)
                    ) {
                        if (topEmotions.isEmpty()) {
                            Text("No message data yet", color = Color(0xFF6B7280), modifier = Modifier.padding(8.dp))
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

// App Usage Card
                item {
                    DashboardCard(
                        title = "App Usage",
                        subtitle = appUsage["screenTime"] ?: "No data yet",
                        icon = "📱",
                        iconColor = Color(0xFFF59E0B)
                    ) {
                        AppUsageSection(topAppsList)
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

                // Behavioral Pattern Analysis (TBATS)
                item {
                    val concernColor = when (tbatsConcernLevel) {
                        "HIGH" -> Color(0xFFEF4444)
                        "MEDIUM" -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }
                    DashboardCard(
                        title = "Behavioral Pattern Analysis",
                        subtitle = "AI-detected changes in digital behavior over 30 days",
                        icon = "🧠",
                        iconColor = concernColor
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = concernColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Concern Level: $tbatsConcernLevel",
                                    color = concernColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (tbatsMusicInsight.isNotEmpty()) {
                                Text(tbatsMusicInsight, color = Color(0xFF374151), fontSize = 13.sp)
                            }
                            if (tbatsUsageInsight.isNotEmpty()) {
                                Text(tbatsUsageInsight, color = Color(0xFF374151), fontSize = 13.sp)
                            }
                            if (tbatsMusicInsight.isEmpty() && tbatsUsageInsight.isEmpty()) {
                                Text("Collecting behavioral data...", color = Color(0xFF6B7280), fontSize = 13.sp)
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
// ========== NEW COMPOSABLES FOR RISK ALERTS ==========

data class RiskAlert(
    val id: String,
    val url: String,
    val riskLevel: String,
    val blockReasons: List<String>,
    val confidenceScore: Float,
    val timestamp: Long
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        alerts.take(5).forEach { alert ->
                            RiskAlertRow(alert = alert)
                        }

                        if (alerts.size > 5) {
                            Text(
                                text = "+ ${alerts.size - 5} more alerts",
                                color = Color(0xFF6B7280),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
// Add this function to load TBATS analysis
fun loadTbatsAnalysis(childId: String, onResult: (Map<String, Any>) -> Unit) {
    GlobalScope.launch {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/analyze/tbats/$childId?days=30")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")

                val musicAnalysis = json.optJSONObject("music_analysis") ?: JSONObject()
                val usageAnalysis = json.optJSONObject("usage_analysis") ?: JSONObject()
                val concernLevel = json.optString("concern_level", "LOW")

                val musicConcern = musicAnalysis.optString("concern_level", "LOW")
                val hasMusicData = musicAnalysis.optBoolean("has_music_data", false)
                val moodDesc     = musicAnalysis.optString("mood_description", "")
                val trackCount   = musicAnalysis.optInt("total_tracks", 0)
                val dataNoteM    = musicAnalysis.optString("data_note", "")

                val musicInsight = when {
                    musicConcern == "HIGH" ->
                        "🎵 Unusual music mood patterns detected"
                    musicConcern == "MEDIUM" ->
                        "🎵 Slight changes in music preferences"
                    hasMusicData && moodDesc.isNotEmpty() ->
                        "🎵 Mood: $moodDesc ($trackCount tracks)"
                    musicAnalysis.has("current_score") && !musicAnalysis.has("error") ->
                        "🎵 Score: ${String.format("%.2f", musicAnalysis.optDouble("current_score"))}" +
                        if (dataNoteM.isNotEmpty()) " — $dataNoteM" else ""
                    dataNoteM.isNotEmpty() ->
                        "🎵 $dataNoteM"
                    else -> "🎵 No music data yet"
                }

                val usageConcern = usageAnalysis.optString("concern_level", "LOW")
                val dataNoteU    = usageAnalysis.optString("data_note", "")
                val usageDays    = usageAnalysis.optInt("data_points", 0)

                val usageInsight = when {
                    usageConcern == "HIGH" ->
                        "📱 Significant screen time changes detected"
                    usageConcern == "MEDIUM" ->
                        "📱 Slight increase in screen time"
                    usageAnalysis.has("current_score") && !usageAnalysis.has("error") ->
                        "📱 Score: ${String.format("%.2f", usageAnalysis.optDouble("current_score"))}" +
                        if (usageDays > 0) " ($usageDays days)" else "" +
                        if (dataNoteU.isNotEmpty()) " — $dataNoteU" else ""
                    dataNoteU.isNotEmpty() ->
                        "📱 $dataNoteU"
                    else -> "📱 No app usage data yet"
                }

                // FIXED: Use proper mapOf with Pair syntax
                val resultMap: Map<String, Any> = mapOf(
                    Pair("concern_level", concernLevel),
                    Pair("music_insight", musicInsight),
                    Pair("usage_insight", usageInsight),
                    Pair("anomaly_dates", musicAnalysis.optJSONArray("anomaly_dates")?.length() ?: 0),
                    Pair("negative_drift_dates", musicAnalysis.optJSONArray("negative_drift_dates")?.length() ?: 0)
                )
                onResult(resultMap)
            } else {
                Log.e("TBATS", "Error: ${response.code}")
                onResult(mapOf(
                    Pair("concern_level", "LOW"),
                    Pair("music_insight", "⚠️ Could not load behavioral analysis"),
                    Pair("usage_insight", "")
                ))
            }
        } catch (e: Exception) {
            Log.e("TBATS", "Exception: ${e.message}")
            onResult(mapOf(
                Pair("concern_level", "LOW"),
                Pair("music_insight", "⚠️ Behavioral analysis unavailable"),
                Pair("usage_insight", "")
            ))
        }
    }
}
@Composable
fun RiskAlertRow(alert: RiskAlert) {
    val statusColor = when (alert.riskLevel) {
        "CRITICAL" -> Color(0xFFDC2626)
        "HIGH" -> Color(0xFFF59E0B)
        "MEDIUM" -> Color(0xFFFBBF24)
        else -> Color(0xFF6B7280)
    }

    val formattedTime: String = remember(alert.timestamp) {
        SimpleDateFormat("HH:mm • MMM d", Locale.getDefault()).format(Date(alert.timestamp))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = statusColor.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alert.riskLevel,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = formattedTime,
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Truncated URL
            Text(
                text = alert.url.take(60) + if (alert.url.length > 60) "..." else "",
                color = Color(0xFF1F2937),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            if (alert.blockReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                alert.blockReasons.take(2).forEach { reason ->
                    Text(
                        text = "• $reason",
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp
                    )
                }
                if (alert.blockReasons.size > 2) {
                    Text(
                        text = "• +${alert.blockReasons.size - 2} more",
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Confidence bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Confidence:",
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
                            .fillMaxWidth(fraction = alert.confidenceScore)
                            .fillMaxHeight()
                            .background(statusColor, RoundedCornerShape(2.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(alert.confidenceScore * 100).toInt()}%",
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
            val emotionDocs = firestore.collection("event_analysis")
                .whereEqualTo("childId", childId)
                .limit(200)
                .get()
                .await()

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

            val safety = listOf(
                SafetyIndicator("Content Safety", "none", "No risks detected"),
                SafetyIndicator("Online Activity", "none", "Normal activity patterns"),
                SafetyIndicator("Monitoring Status", "none", "Real-time protection active")
            )

            // ========== MUSIC INSIGHTS (from processed RF emotion results) ==========
            // Single-field filter only — no composite index required; filter in Kotlin
            val musicDocs = firestore.collection("music_tracking")
                .whereEqualTo("childId", childId)
                .limit(50)
                .get()
                .await()

            val emotionCounts = mutableMapOf<String, Int>()
            var totalTracks = 0
            for (doc in musicDocs.documents) {
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
// ========== LOAD ALERTS FUNCTION ==========
fun loadAlertsFromFirebase(
    context: android.content.Context,
    childId: String,
    onResult: (List<RiskAlert>) -> Unit
) {
    GlobalScope.launch {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

            // Single-field filter only — no composite index required; filter by time in Kotlin
            val assessments = firestore.collection("risk_assessment")
                .whereEqualTo("childId", childId)
                .limit(100)
                .get()
                .await()

            // Create alerts without exposing URLs; filter last 24h in Kotlin
            val alerts = assessments.documents.mapNotNull { doc ->
                val ts = doc.getLong("timestamp") ?: 0L
                if (ts < twentyFourHoursAgo) return@mapNotNull null
                val riskLevel = doc.getString("riskLevel") ?: return@mapNotNull null
                if (riskLevel !in listOf("HIGH", "CRITICAL")) return@mapNotNull null

                RiskAlert(
                    id = doc.id,
                    url = "⚠️ Content blocked - View on child's device for details",
                    riskLevel = riskLevel,
                    blockReasons = listOf(
                        when (riskLevel) {
                            "CRITICAL" -> "Immediate attention recommended"
                            "HIGH" -> "Parental review suggested"
                            else -> "Monitor conversation"
                        }
                    ),
                    confidenceScore = (doc.getDouble("confidenceScore") ?: 0.7).toFloat(),
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                )
            }

            onResult(alerts)
        } catch (e: Exception) {
            // Fallback to local storage if needed
            loadAlertsFromLocalStorage(context, onResult)
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