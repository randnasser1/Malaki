package com.example.malaki.ui.screens.child

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val moodColors = mapOf(
    "great" to Color(0xFF10B981),
    "good" to Color(0xFF34D399),
    "okay" to Color(0xFFF59E0B),
    "sad" to Color(0xFFEF4444),
    "anxious" to Color(0xFFF97316)
)

val moodEmojis = mapOf(
    "great" to "😊",
    "good" to "😊",
    "okay" to "😐",
    "sad" to "😢",
    "anxious" to "😰"
)

@Composable
fun MoodCalendar(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDay by remember { mutableStateOf<String?>(null) }

    // Firestore data states
    var isLoading by remember { mutableStateOf(true) }
    var moods by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var journals by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load data from Firestore when component loads or month changes
    LaunchedEffect(currentMonth) {
        isLoading = true
        error = null

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            error = "Not logged in"
            isLoading = false
            return@LaunchedEffect
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            val childId = currentUser.uid

            // Calculate date range for current month
            val year = currentMonth.get(Calendar.YEAR)
            val month = currentMonth.get(Calendar.MONTH) + 1
            val startDate = String.format("%04d-%02d-01", year, month)

            val cal = Calendar.getInstance()
            cal.set(year, currentMonth.get(Calendar.MONTH), 1)
            val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val endDate = String.format("%04d-%02d-%02d", year, month, lastDay)

            // Query Firestore for wellbeing data in this month
            val docs = firestore.collection("wellbeing_daily_summary")
                .whereEqualTo("childId", childId)
                .get()
                .await()

            val moodsMap = mutableMapOf<String, String>()
            val journalsMap = mutableMapOf<String, String>()

            for (doc in docs.documents) {
                val date = doc.getString("date") ?: continue
                // Only include dates from current month
                if (date.startsWith("$year-$month") || date.startsWith("$year-0$month")) {
                    val mood = doc.getString("dailyMood") ?: doc.getString("dominantEmotion")
                    if (mood != null) {
                        moodsMap[date] = mood
                    }
                    val journal = doc.getString("journalText")
                    if (!journal.isNullOrEmpty()) {
                        journalsMap[date] = journal
                    }
                }
            }

            moods = moodsMap
            journals = journalsMap
            isLoading = false

        } catch (e: Exception) {
            error = "Failed to load: ${e.message}"
            isLoading = false
        }
    }

    // Get days in month
    val calendar = Calendar.getInstance().apply {
        time = currentMonth.time
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1

    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    val selectedDayData = selectedDay?.let { dateStr ->
        val mood = moods[dateStr]
        val journal = journals[dateStr]
        Triple(dateStr, mood, journal)
    }

    fun formatDate(day: Int): String {
        val year = currentMonth.get(Calendar.YEAR)
        val month = currentMonth.get(Calendar.MONTH) + 1
        return String.format("%04d-%02d-%02d", year, month, day)
    }

    fun navigateMonth(delta: Int) {
        currentMonth = Calendar.getInstance().apply {
            time = currentMonth.time
            add(Calendar.MONTH, delta)
        }
        selectedDay = null  // Clear selection when month changes
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF8E7), Color.White)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onNavigate("child") }) {
                    Text("←", fontSize = 24.sp, color = Color(0xFF4B5563))
                }

                Text(
                    text = "My Mood Calendar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1F2937),
                    fontWeight = FontWeight.Medium
                )

                Box(modifier = Modifier.width(40.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigateMonth(-1) }) {
                    Text("◀", color = Color(0xFF6B7280))
                }

                Text(
                    text = "${monthNames[currentMonth.get(Calendar.MONTH)]} ${currentMonth.get(Calendar.YEAR)}",
                    color = Color(0xFF374151),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                IconButton(onClick = { navigateMonth(1) }) {
                    Text("▶", color = Color(0xFF6B7280))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Loading state
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading your moods...", color = Color(0xFF6B7280), fontSize = 12.sp)
                    }
                }
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error ?: "Unknown error", color = Color(0xFFEF4444), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            scope.launch {
                                isLoading = true
                                // Reload data by re-triggering the LaunchedEffect
                                // Force a refresh by resetting currentMonth
                                val tempMonth = currentMonth
                                currentMonth = Calendar.getInstance().apply {
                                    time = tempMonth.time
                                }
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                // Day names
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    dayNames.forEach { day ->
                        Text(
                            text = day,
                            color = Color(0xFF9CA3AF),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendar grid
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Empty cells for days before month starts
                        items(firstDayOfWeek) {
                            Box(modifier = Modifier.size(40.dp))
                        }

                        // Days of month
                        items(daysInMonth) { day ->
                            val dayNum = day + 1
                            val dateStr = formatDate(dayNum)
                            val mood = moods[dateStr]
                            val bgColor = mood?.let { moodColors[it] } ?: Color(0xFFF3F4F6)
                            val emoji = mood?.let { moodEmojis[it] }
                            val isSelected = selectedDay == dateStr

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) bgColor.copy(alpha = 0.8f) else bgColor)
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, bgColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .clickable { selectedDay = dateStr },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (emoji != null) {
                                        Text(emoji, fontSize = 14.sp)
                                    }
                                    Text(
                                        text = dayNum.toString(),
                                        color = if (mood != null) Color.White else Color(0xFF9CA3AF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected day details
            AnimatedVisibility(
                visible = selectedDayData != null && !isLoading,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                selectedDayData?.let { (dateStr, mood, journal) ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF3F4F6)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = formatDateForDisplay(dateStr),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            if (mood != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val moodEmoji = moodEmojis[mood] ?: "❓"
                                    Text("$moodEmoji Mood: ", fontSize = 12.sp, color = Color(0xFF6B7280))
                                    Text(
                                        mood.replaceFirstChar { it.uppercase() },
                                        color = moodColors[mood] ?: Color(0xFF6B7280),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }

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

private fun formatDateForDisplay(dateStr: String): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = format.parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        dateStr
    }
}