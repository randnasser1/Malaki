package com.example.malaki

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseMsgService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // Send token to your backend server
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "From: ${message.from}")

        // Check if message contains a notification payload
        message.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // Show notification to parent
            showNotification(it.title ?: "Alert", it.body ?: "")
        }

        // Check if message contains data payload
        message.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: ${message.data}")
        }
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: Send token to your backend
        // This will be used to send push notifications to this device
        Log.d(TAG, "Sending token to server: $token")
    }

    private fun showNotification(title: String, message: String) {
        // TODO: Implement notification display
        // You'll implement this with NotificationManagerCompat
    }
}