package com.example.malaki

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseMsgService"
        private const val CHANNEL_ID = "risk_alerts_parent"
        private const val CHANNEL_NAME = "Child Safety Alerts"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // Save token to Firestore for this parent
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "From: ${message.from}")

        // Priority 1: Data payload (for custom handling)
        message.data.let { data ->
            if (data.isNotEmpty()) {
                val riskLevel = data["riskLevel"] ?: "HIGH"
                val alertMessage = data["message"] ?: "Suspicious content detected"
                val childId = data["childId"] ?: ""

                Log.d(TAG, "ALERT: $riskLevel risk from child $childId")
                showAlertNotification(riskLevel, alertMessage)
            }
        }

        // Priority 2: Notification payload (fallback)
        message.notification?.let {
            Log.d(TAG, "Notification: ${it.body}")
            showAlertNotification("HIGH", it.body ?: "Safety alert")
        }
    }

    private fun saveTokenToFirestore(token: String) {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val tokenData = hashMapOf(
                    "fcmToken" to token,
                    "updatedAt" to System.currentTimeMillis(),
                    "userType" to "PARENT"
                )
                FirebaseFirestore.getInstance()
                    .collection("parent_tokens")
                    .document(currentUser.uid)
                    .set(tokenData)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Token saved to Firestore for parent ${currentUser.uid}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to save token: ${e.message}")
                    }
            } else {
                Log.w(TAG, "No logged in user to save token for")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving token: ${e.message}")
        }
    }

    private fun showAlertNotification(riskLevel: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical safety alerts about your child"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Set icon and color based on risk level
        val icon = when (riskLevel) {
            "CRITICAL" -> android.R.drawable.stat_notify_error
            "HIGH" -> android.R.drawable.stat_notify_error
            "MEDIUM" -> android.R.drawable.stat_notify_error
            else -> android.R.drawable.stat_notify_sync
        }

        val color = when (riskLevel) {
            "CRITICAL", "HIGH" -> 0xFFEF4444.toInt()
            "MEDIUM" -> 0xFFF59E0B.toInt()
            else -> 0xFF10B981.toInt()
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 Child Safety Alert")
            .setContentText("$riskLevel Risk: ${message.take(100)}")
            .setSmallIcon(icon)
            .setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 300, 500))
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}