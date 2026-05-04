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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*  // This provides Date, Locale, UUID
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.platform.LocalContext


// Data classes
data class SentimentData(val day: String, val score: Int)
data class WellbeingData(val category: String, val score: Int)
data class SafetyIndicator(val type: String, val status: String, val description: String)
data class ChildInfo(val id: String, val name: String)

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
    var topAppsList by remember { mutableStateOf<List<String>>(emptyList()) }
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
            loadAlertsFromFirebase(context, childId) { loadedAlerts ->
                alerts = loadedAlerts
                isLoadingAlerts = false
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
                // Sentiment Trends Card
                item {
                    DashboardCard(
                        title = "Sentiment Trends",
                        subtitle = "Based on journal entries and mood tracking over the past week",
                        icon = "📈",
                        iconColor = Color(0xFF3B82F6)
                    ) {
                        if (sentimentData.all { it.score == 0 }) {
                            Text("No mood data yet. Ask your child to check in daily.", color = Color(0xFF6B7280))
                        } else {
                            SimpleBarChart(data = sentimentData)
                        }
                    }
                }

                // Wellbeing Indicators Card
                item {
                    DashboardCard(
                        title = "Wellbeing Indicators",
                        subtitle = "Multi-dimensional assessment of emotional health",
                        icon = "🛡️",
                        iconColor = Color(0xFF10B981)
                    ) {
                        if (wellbeingData.isEmpty() || wellbeingData.all { it.score == 0 }) {
                            Text("Complete wellbeing assessment to see insights", color = Color(0xFF6B7280))
                        } else {
                            SimpleBarChartHorizontal(data = wellbeingData)
                        }
                    }
                }

                // Music Insights Card
                item {
                    DashboardCard(
                        title = "Music Insights",
                        icon = "🎵",
                        iconColor = Color(0xFF8B5CF6)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsightRow(label = "Top Genre", value = musicInsights["topGenre"] ?: "No data yet")
                            InsightRow(label = "Listening Time", value = musicInsights["listeningTime"] ?: "No music detected")
                            InsightRow(label = "Pattern", value = musicInsights["moodCorrelation"] ?: "Listen to music for insights")
                        }
                    }
                }

                // App Usage Card
                item {
                    DashboardCard(
                        title = "App Usage",
                        icon = "📱",
                        iconColor = Color(0xFFF59E0B)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsightRow(label = "Screen Time", value = appUsage["screenTime"] ?: "No data yet")
                            InsightRow(label = "Top Apps", value = if (topAppsList.isNotEmpty()) topAppsList.joinToString(", ") else "No apps tracked")
                            InsightRow(label = "Productive Time", value = appUsage["productiveTime"] ?: "—")
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
        topApps: List<String>
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
                val avgSentiment = doc.getDouble("avg_sentiment") ?: 0.5
                moodScoreByDate[date] = (avgSentiment * 100).toInt()
            }

            val sentiments = (6 downTo 0).map { daysAgo ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                val dateKey = dateFormat.format(cal.time)
                SentimentData(dayFormat.format(cal.time), moodScoreByDate[dateKey] ?: 0)
            }

            // ========== ADDED: APP USAGE ==========
            val usageDocs = firestore.collection("app_usage")
                .whereEqualTo("childId", childId)
                .whereGreaterThan("timestamp", sevenDaysAgo)
                .get()
                .await()

            var totalScreenTimeMin = 0L
            val allApps = mutableMapOf<String, Long>()
            var daysWithData = 0

            for (doc in usageDocs.documents) {
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
                .map { it.key }
            // ========== END OF APP USAGE ==========

            // Everything below is EXACTLY as you had it
            val wellbeing = listOf(
                WellbeingData("Emotional", sentiments.filter { it.score > 0 }.map { it.score }.average().toInt().coerceIn(0, 100)),
                WellbeingData("Consistency", (moodDocs.size() * 15).coerceIn(0, 100)),
                WellbeingData("Screen Time", 75),
                WellbeingData("Music Engagement", 50)
            )

            val safety = listOf(
                SafetyIndicator("Content Safety", "none", "No risks detected"),
                SafetyIndicator("Online Activity", "none", "Normal activity patterns"),
                SafetyIndicator("Monitoring Status", "none", "Real-time protection active")
            )

            val musicMap = mapOf(
                "topGenre" to "Awaiting data",
                "listeningTime" to "No music detected",
                "moodCorrelation" to "Listen to music for insights"
            )

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

            // Read from risk_assessment (NO URL field stored)
            val assessments = firestore.collection("risk_assessment")
                .whereEqualTo("childId", childId)
                .whereGreaterThan("timestamp", twentyFourHoursAgo)
                .get()
                .await()

            // Create alerts without exposing URLs
            val alerts = assessments.documents.mapNotNull { doc ->
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
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        data.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height((item.score * 1.5).dp)
                        .background(Color(0xFF3B82F6), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
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
fun SimpleBarChartHorizontal(data: List<WellbeingData>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        data.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.category,
                    color = Color(0xFF374151),
                    fontSize = 14.sp,
                    modifier = Modifier.width(80.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .background(Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = item.score / 100f)
                            .background(Color(0xFF10B981), RoundedCornerShape(12.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${item.score}",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    modifier = Modifier.width(30.dp)
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