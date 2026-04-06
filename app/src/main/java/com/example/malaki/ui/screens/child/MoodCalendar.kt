package com.example.malaki.ui.screens.child

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

val moodColors = mapOf(
    "great" to Color(0xFFE8F5A8),
    "good" to Color(0xFFF3D97F),
    "okay" to Color(0xFFD4D4D8),
    "sad" to Color(0xFF9CA3AF),
    "anxious" to Color(0xFFF3E8A8)
)

@Composable
fun MoodCalendar(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDay by remember { mutableStateOf<String?>(null) }

    // Load moods and journals
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val moodsJson = prefs.getString("moods", "{}") ?: "{}"
    val moods = remember(moodsJson) { JSONObject(moodsJson) }
    val journalsJson = prefs.getString("journals", "{}") ?: "{}"
    val journals = remember(journalsJson) { JSONObject(journalsJson) }

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
        val mood = if (moods.has(dateStr)) moods.getString(dateStr) else null
        val journal = if (journals.has(dateStr)) journals.getString(dateStr) else null
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
                        val mood = if (moods.has(dateStr)) moods.getString(dateStr) else null
                        val moodColor = mood?.let { moodColors[it] } ?: Color.Transparent
                        val isSelected = selectedDay == dateStr

                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { selectedDay = dateStr },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) moodColor.copy(alpha = 0.3f) else moodColor.copy(alpha = 0.2f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, moodColor) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = dayNum.toString(),
                                    color = if (mood != null) Color(0xFF1F2937) else Color(0xFF9CA3AF),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected day details
            AnimatedVisibility(
                visible = selectedDayData != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                selectedDayData?.let { (dateStr, mood, journal) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = formatDateForDisplay(dateStr),
                                color = Color(0xFF1F2937),
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (mood != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Mood: ",
                                        color = Color(0xFF6B7280),
                                        fontSize = 14.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = moodColors[mood]?.copy(alpha = 0.3f) ?: Color.Transparent
                                    ) {
                                        Text(
                                            text = mood,
                                            color = moodColors[mood] ?: Color(0xFF6B7280),
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (!journal.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Journal entry:",
                                    color = Color(0xFF6B7280),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = journal,
                                    color = Color(0xFF374151),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (mood == null && journal == null) {
                                Text(
                                    text = "No entries for this day",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 14.sp
                                )
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