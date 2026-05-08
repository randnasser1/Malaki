package com.example.malaki.ui.screens.child

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import com.example.malaki.BuildConfig
import com.example.malaki.db.BackendSyncManager
import com.example.malaki.db.EventRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

val journalPrompts = listOf(
    "What made you smile today?",
    "What are you grateful for?",
    "How did you feel today?",
    "What's on your mind?",
    "What was the best part of your day?"
)

private val moodToScore = mapOf(
    "great" to 0.9f, "good" to 0.7f, "okay" to 0.5f, "sad" to 0.2f, "anxious" to 0.3f
)

private val journalHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
private val journalJsonMedia = "application/json".toMediaType()

data class JournalEntry(val date: String, val text: String, val timestamp: Long)

@Composable
fun JournalView(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { EventRepository(context) }
    val syncManager = remember { BackendSyncManager(context) }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val randomPrompt = remember { journalPrompts.random() }

    var mode by remember { mutableStateOf("write") }
    var text by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var pastJournals by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var loadingHistory by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFF8E7), Color(0xFFFFFBE6), Color(0xFFFFF0E0)),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY)
    )

    // Load today's existing entry
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val journalsMap = JSONObject(prefs.getString("journals", "{}") ?: "{}")
        if (journalsMap.has(today)) text = journalsMap.getString(today)
    }

    // Load history from Firestore when switching to history tab
    LaunchedEffect(mode) {
        if (mode != "history") return@LaunchedEffect
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        loadingHistory = true
        try {
            val docs = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("journals")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()
            pastJournals = docs.documents.mapNotNull { doc ->
                val date = doc.getString("date") ?: return@mapNotNull null
                val entryText = doc.getString("text") ?: return@mapNotNull null
                val ts = doc.getLong("timestamp") ?: 0L
                JournalEntry(date, entryText, ts)
            }
        } catch (_: Exception) {}
        loadingHistory = false
    }

    LaunchedEffect(saved) {
        if (saved) { delay(2000); saved = false }
    }

    fun handleSave() {
        if (text.isBlank()) return
        val savedText = text
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)

        // 1. SharedPrefs (local cache for MoodCalendar)
        val journalsMap = JSONObject(prefs.getString("journals", "{}") ?: "{}")
        journalsMap.put(today, savedText)
        prefs.edit().putString("journals", journalsMap.toString()).apply()

        // 2. Firestore: users/{uid}/journals/{date}
        scope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("journals").document(today)
                    .set(mapOf("date" to today, "text" to savedText, "timestamp" to System.currentTimeMillis()))
            } catch (_: Exception) {}
        }

        // 3. Room: JOURNAL captured_event (for local DB and sync pipeline)
        scope.launch {
            try {
                val moodsJson = prefs.getString("moods", "{}") ?: "{}"
                val todayMood = JSONObject(moodsJson).optString(today, "").ifBlank { null }
                repository.ensureDeviceProfile()
                repository.captureJournalEntry(
                    entryText = savedText,
                    moodLabel = todayMood,
                    timestampUtc = System.currentTimeMillis()
                )
            } catch (_: Exception) {}
        }

        // 4. Backend: POST /wellbeing/daily → DistilBERT → wellbeing_daily_summary (emotion calendar)
        scope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val moodsJson = prefs.getString("moods", "{}") ?: "{}"
                val todayMood = JSONObject(moodsJson).optString(today, "okay").ifBlank { "okay" }
                val moodScore = moodToScore[todayMood] ?: 0.5f

                val body = JSONObject().apply {
                    put("child_id", uid)
                    put("date", today)
                    put("daily_mood", todayMood)
                    put("daily_mood_score", moodScore)
                    put("journal_text", savedText)
                    put("timestamp", System.currentTimeMillis())
                }.toString().toRequestBody(journalJsonMedia)

                withContext(Dispatchers.IO) {
                    journalHttp.newCall(
                        Request.Builder()
                            .url("${BuildConfig.BACKEND_BASE_URL}/wellbeing/daily")
                            .post(body)
                            .build()
                    ).execute().close()
                }
            } catch (_: Exception) {}
        }

        // 5. Sync JOURNAL event → /events/analyze → event_analysis (Wellbeing Indicators on parent dashboard)
        scope.launch {
            try { syncManager.syncPendingEvents() } catch (_: Exception) {}
        }

        saved = true
    }

    // Show detail view for a past entry
    if (selectedEntry != null) {
        JournalDetailView(
            entry = selectedEntry!!,
            gradient = gradient,
            onBack = { selectedEntry = null }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

            // Header row
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
                if (mode == "write") {
                    Button(
                        onClick = ::handleSave,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF374151)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) { Text("Save", fontSize = 14.sp) }
                } else {
                    Spacer(modifier = Modifier.width(72.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Write / History toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(4.dp)
            ) {
                listOf("write" to "Write Today", "history" to "Past Journals").forEach { (m, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (mode == m) Color(0xFF374151) else Color.Transparent,
                                RoundedCornerShape(50)
                            )
                            .clickable { mode = m }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (mode == m) Color.White else Color(0xFF6B7280),
                            fontSize = 13.sp,
                            fontWeight = if (mode == m) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (mode == "write") {
                Text(
                    text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date()),
                    color = Color(0xFF6B7280),
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = randomPrompt,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
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

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f)),
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
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) {
                                    Text("Start writing...", color = Color(0xFF9CA3AF), fontSize = 16.sp)
                                }
                                inner()
                            }
                        }
                    )
                }

            } else {
                // History tab
                if (loadingHistory) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF374151))
                    }
                } else if (pastJournals.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📖", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No journal entries yet",
                                color = Color(0xFF6B7280),
                                fontSize = 16.sp
                            )
                            Text(
                                "Write your first entry!",
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pastJournals) { entry ->
                            JournalHistoryCard(entry = entry) { selectedEntry = entry }
                        }
                    }
                }
            }
        }

        // Save confirmation toast
        AnimatedVisibility(
            visible = saved,
            enter = slideInVertically(initialOffsetY = { 100 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { 100 }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier.padding(bottom = 32.dp).wrapContentSize(),
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981)),
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

@Composable
private fun JournalHistoryCard(entry: JournalEntry, onClick: () -> Unit) {
    val displayDate = remember(entry.date) {
        try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(entry.date)
            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(parsed ?: Date())
        } catch (_: Exception) { entry.date }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(displayDate, color = Color(0xFF4B5563), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.text.take(120) + if (entry.text.length > 120) "…" else "",
                color = Color(0xFF1F2937),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun JournalDetailView(entry: JournalEntry, gradient: Brush, onBack: () -> Unit) {
    val displayDate = remember(entry.date) {
        try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(entry.date)
            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(parsed ?: Date())
        } catch (_: Exception) { entry.date }
    }

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Text("←", fontSize = 24.sp, color = Color(0xFF4B5563))
                }
                Text(
                    text = "My Journal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1F2937),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = displayDate,
                color = Color(0xFF6B7280),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        text = entry.text,
                        color = Color(0xFF1F2937),
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}
