package com.example.malaki.ui.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Data classes
data class SentimentData(val day: String, val score: Int)
data class WellbeingData(val category: String, val score: Int)
data class SafetyIndicator(val type: String, val status: String, val description: String)

// Mock data
val sentimentData = listOf(
    SentimentData("Mon", 75), SentimentData("Tue", 68), SentimentData("Wed", 82),
    SentimentData("Thu", 79), SentimentData("Fri", 85), SentimentData("Sat", 90),
    SentimentData("Sun", 88)
)

val wellbeingData = listOf(
    WellbeingData("Social", 82), WellbeingData("Emotional", 75),
    WellbeingData("Activity", 88), WellbeingData("Sleep", 70)
)

val safetyIndicators = listOf(
    SafetyIndicator("Explicit Content", "low", "Minimal exposure to explicit content detected"),
    SafetyIndicator("Grooming Risk", "none", "No concerning patterns in conversations detected"),
    SafetyIndicator("Cyberbullying", "low", "Some negative language detected, monitoring ongoing")
)

@Composable
fun ParentDashboard(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val musicInsights = mapOf(
        "topGenre" to "Pop",
        "listeningTime" to "2h 15m/day",
        "moodCorrelation" to "Upbeat music correlates with positive mood entries"
    )

    val appUsage = mapOf(
        "screenTime" to "4h 32m/day",
        "productiveTime" to "1h 45m"
    )

    val topAppsList = listOf("Messages", "TikTok", "Instagram")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Wellbeing Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Overview of your child's emotional wellbeing and safety",
                    color = Color(0xFF6B7280),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onNavigate("child") },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White
                    )
                ) {
                    Text("Switch to Child View")
                }
            }
        }

        // Sentiment Trends Card
        item {
            DashboardCard(
                title = "Sentiment Trends",
                subtitle = "Based on journal entries and mood tracking over the past week",
                icon = "📈",
                iconColor = Color(0xFF3B82F6)
            ) {
                SimpleBarChart(data = sentimentData)
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
                SimpleBarChartHorizontal(data = wellbeingData)
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
                    InsightRow(label = "Top Genre", value = musicInsights["topGenre"] ?: "")
                    InsightRow(label = "Listening Time", value = musicInsights["listeningTime"] ?: "")
                    InsightRow(label = "Pattern", value = musicInsights["moodCorrelation"] ?: "")
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
                    InsightRow(label = "Screen Time", value = appUsage["screenTime"] ?: "")
                    InsightRow(label = "Top Apps", value = topAppsList.joinToString(", "))
                    InsightRow(label = "Productive Time", value = appUsage["productiveTime"] ?: "")
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