package com.example.malaki.ui.screens.child

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.malaki.db.EventRepository
import com.example.malaki.ui.components.SuccessState
import com.example.malaki.ui.components.angel.Angel
import com.example.malaki.ui.components.angel.AngelVariant
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
val moodOptions = listOf(
    Triple("great", "😊", Color(0xFFE8F5A8)),
    Triple("good", "👍", Color(0xFFF3D97F)),
    Triple("okay", "😐", Color(0xFFD4D4D8)),
    Triple("sad", "😢", Color(0xFF9CA3AF)),
    Triple("anxious", "😰", Color(0xFFF3E8A8))
)

@Composable
fun ChildHome(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMood by remember { mutableStateOf<String?>(null) }
    var showAngel by remember { mutableStateOf(true) }
    var showSuccess by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val authManager = remember { com.example.malaki.auth.AuthManager(context) }
    val childName = authManager.getChildName()
    val repository = remember { EventRepository(context) }

    val selectedMoodData = moodOptions.find { it.first == selectedMood }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF8E7), Color.White)
    )

    fun saveMoodToFirestore(date: String, mood: String) {
        val childId = authManager.currentUser?.uid ?: run {

        android.util.Log.e("MOOD", "❌ No child ID — Firebase Auth session may be null")
            return
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val moodData = hashMapOf(
            "date" to date,
            "mood" to mood,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users").document(childId)
            .collection("moods").document(date)
            .set(moodData)
            .addOnSuccessListener { android.util.Log.d("MOOD", "✅ Mood saved to Firestore for $childId") }
            .addOnFailureListener { android.util.Log.e("MOOD", "❌ Mood save failed for $childId: ${it.message}") }
    }

    fun handleMoodSelect(moodId: String) {
        selectedMood = moodId
        showAngel = false
        showSuccess = true

        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        // Save to SharedPrefs
        val moods = prefs.getString("moods", "{}") ?: "{}"
        val moodsMap = org.json.JSONObject(moods)
        moodsMap.put(today, moodId)
        prefs.edit().putString("moods", moodsMap.toString()).apply()

        // Save to Firestore
        val childId = authManager.currentUser?.uid
        if (childId != null) {
            val sentimentScore = when (moodId) {
                "great" -> 1.0
                "good" -> 0.75
                "okay" -> 0.5
                "anxious" -> 0.35
                "sad" -> 0.2
                else -> 0.5
            }

            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val summaryData = hashMapOf(
                "childId" to childId,
                "date" to today,
                "avg_sentiment" to sentimentScore,
                "dominantEmotion" to moodId,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("wellbeing_daily_summary")
                .document("${childId}_$today")
                .set(summaryData)
                .addOnSuccessListener {
                    android.util.Log.d("WELLBEING", "✅ Saved to Firestore!")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("WELLBEING", "❌ Firestore error: ${e.message}")
                }
        }

        // Hide success message after 2 seconds - FIXED: Use Handler instead of runOnUiThread
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            showSuccess = false
        }, 2000)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Top bar buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onNavigate("settings") }) {
                    Text("⚙️ Settings", color = Color(0xFF6B7280), fontSize = 12.sp)
                }
                TextButton(onClick = { onNavigate("logout") }) {
                    Text("🚪 Logout", color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Angel
            AnimatedVisibility(
                visible = showAngel,
                exit = fadeOut() + slideOutVertically()
            ) {
                Angel(
                    variant = AngelVariant.SMALL,
                    moodColor = selectedMoodData?.third
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Greeting
            Text(
                text = "Hi $childName! 👋",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937),
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "How are you feeling today?",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF1F2937),
                textAlign = TextAlign.Center
            )

            if (selectedMood != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("You're safe here", color = Color(0xFF6B7280), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Mood selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                moodOptions.forEach { (id, emoji, color) ->
                    val isSelected = selectedMood == id

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { handleMoodSelect(id) }
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(32.dp),
                            color = color.copy(alpha = 0.3f),
                            shadowElevation = if (isSelected) 8.dp else 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 28.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(id.capitalize(), color = Color(0xFF374151), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = "My Calendar",
                    subtitle = "See your mood journey",
                    onClick = { onNavigate("calendar") },
                    modifier = Modifier.weight(1f)
                )

                QuickActionCard(
                    title = "Journal",
                    subtitle = "Write your thoughts",
                    onClick = { onNavigate("journal") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (showSuccess) {
            SuccessState(
                message = "Thank you for sharing how you feel",
                onClose = { showSuccess = false }
            )
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = Color(0xFF1F2937), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFF6B7280), fontSize = 12.sp)
        }
    }
}