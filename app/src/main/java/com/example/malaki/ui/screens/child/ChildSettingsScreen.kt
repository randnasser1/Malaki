package com.example.malaki.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.malaki.PermissionHelper

private data class PermissionItem(
    val icon: String,
    val title: String,
    val description: String,
    val whyNeeded: String,
    val granted: Boolean,
    val onEnable: () -> Unit
)

@Composable
fun ChildSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val permissionHelper = remember { PermissionHelper(context) }

    var usageGranted by remember { mutableStateOf(permissionHelper.checkUsageStatsPermission()) }
    var notifGranted by remember { mutableStateOf(permissionHelper.checkNotificationAccess()) }
    var accessGranted by remember { mutableStateOf(permissionHelper.checkAccessibilityService()) }

    // Re-check all permissions every time the screen resumes (user may have just toggled in system settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = permissionHelper.checkUsageStatsPermission()
                notifGranted = permissionHelper.checkNotificationAccess()
                accessGranted = permissionHelper.checkAccessibilityService()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissions = listOf(
        PermissionItem(
            icon = "📊",
            title = "App Usage Access",
            description = "Tracks which apps you use and for how long each day.",
            whyNeeded = "Needed for screen time monitoring on the parent dashboard.",
            granted = usageGranted,
            onEnable = { permissionHelper.requestUsageStatsPermission() }
        ),
        PermissionItem(
            icon = "🔔",
            title = "Notification Access",
            description = "Reads music app notifications to detect what you're listening to.",
            whyNeeded = "Needed for music insights on the parent dashboard.",
            granted = notifGranted,
            onEnable = { permissionHelper.openNotificationAccessSettings() }
        ),
        PermissionItem(
            icon = "♿",
            title = "Accessibility Service",
            description = "Reads URLs opened in browsers to check for unsafe content.",
            whyNeeded = "Needed for URL safety scanning and risk alerts.",
            granted = accessGranted,
            onEnable = { permissionHelper.openAccessibilitySettings() }
        )
    )

    val grantedCount = permissions.count { it.granted }
    val allGranted = grantedCount == permissions.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("← Back", color = Color(0xFF6B7280))
                }
            }
        }

        item {
            Text(
                text = "App Permissions",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "These permissions let Malaki monitor and keep you safe.",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )
        }

        // Summary banner
        item {
            val bannerColor = if (allGranted) Color(0xFF10B981) else Color(0xFFF59E0B)
            val bannerBg = bannerColor.copy(alpha = 0.1f)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bannerBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, bannerColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (allGranted) "✅" else "⚠️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (allGranted) "All permissions granted" else "$grantedCount of ${permissions.size} permissions granted",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111827),
                            fontSize = 15.sp
                        )
                        if (!allGranted) {
                            Text(
                                text = "Some monitoring features are disabled.",
                                color = Color(0xFF6B7280),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // One card per permission
        items(permissions.size) { index ->
            PermissionCard(permissions[index])
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How to enable a permission",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E40AF),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap \"Enable\" next to any missing permission. Your phone will open the relevant settings page — find Malaki in the list and turn it on, then come back here.",
                        color = Color(0xFF3B82F6),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.granted) Color(0xFF10B981).copy(alpha = 0.1f)
                            else Color(0xFFF59E0B).copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(item.icon, fontSize = 22.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111827),
                        fontSize = 15.sp
                    )
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (item.granted) Color(0xFF10B981).copy(alpha = 0.1f)
                            else Color(0xFFEF4444).copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (item.granted) "Granted" else "Missing",
                        color = if (item.granted) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.description,
                color = Color(0xFF374151),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.whyNeeded,
                color = Color(0xFF6B7280),
                fontSize = 12.sp
            )

            if (!item.granted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = item.onEnable,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Enable in Settings", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}
