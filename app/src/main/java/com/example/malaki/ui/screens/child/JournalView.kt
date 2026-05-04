package com.example.malaki.ui.screens.child

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaki.db.EventRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val journalPrompts = listOf(
    "What made you smile today?",
    "What are you grateful for?",
    "How did you feel today?",
    "What's on your mind?",
    "What was the best part of your day?"
)

@Composable
fun JournalView(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { EventRepository(context) }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val randomPrompt = remember { journalPrompts.random() }

    var text by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    // Load existing journal entry from SharedPrefs
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val journals = prefs.getString("journals", "{}") ?: "{}"
        val journalsMap = org.json.JSONObject(journals)
        if (journalsMap.has(today)) {
            text = journalsMap.getString(today)
        }
    }

    LaunchedEffect(saved) {
        if (saved) {
            delay(2000)
            saved = false
        }
    }

    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFF8E7),
            Color(0xFFFFFBE6),
            Color(0xFFFFF0E0)
        ),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY)
    )

    fun handleSave() {
        if (text.isBlank()) return

        // 1. SharedPrefs (existing — for MoodCalendar display)
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val journals = prefs.getString("journals", "{}") ?: "{}"
        val journalsMap = org.json.JSONObject(journals)
        journalsMap.put(today, text)
        prefs.edit().putString("journals", journalsMap.toString()).apply()

        // 2. Firestore — users/{childId}/journals/{date}
        scope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .collection("journals").document(today)
                        .set(
                            mapOf(
                                "date" to today,
                                "text" to text,
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                }
            } catch (_: Exception) {}
        }

        // 3. Room — journal_entry + captured_event(JOURNAL) for ML pipeline
        scope.launch {
            try {
                repository.ensureDeviceProfile()
                // Read today's mood from SharedPrefs so we can attach it to the entry
                val moodsJson = prefs.getString("moods", "{}") ?: "{}"
                val todayMood = org.json.JSONObject(moodsJson).optString(today, "").ifBlank { null }
                repository.captureJournalEntry(
                    entryText = text,
                    moodLabel = todayMood,
                    timestampUtc = System.currentTimeMillis()
                )
            } catch (_: Exception) {}
        }

        saved = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
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
                    text = "My Journal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1F2937),
                    fontWeight = FontWeight.Medium
                )

                Button(
                    onClick = { handleSave() },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF374151)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text("Save", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Date
            Text(
                text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date()),
                color = Color(0xFF6B7280),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Prompt
            AnimatedContent(
                targetState = randomPrompt,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                }
            ) { prompt ->
                Text(
                    text = "\"$prompt\"",
                    color = Color(0xFF4B5563),
                    fontSize = 16.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Journal Text Field
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.6f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = Color(0xFF1F2937),
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text = "Start writing...",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        // Save confirmation
        AnimatedVisibility(
            visible = saved,
            enter = slideInVertically(initialOffsetY = { 100 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { 100 }) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .wrapContentSize(),
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF10B981)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = "Saved ✓",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}
