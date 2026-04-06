package com.example.malaki

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class MessageCollector(private val context: Context) {

    companion object {
        private const val TAG = "MessageCollector"
    }

    fun isServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val serviceName = ComponentName(context, MessageAccessibilityService::class.java)
        val serviceString = serviceName.flattenToString()

        val isEnabled = enabledServices.contains(serviceString)
        Log.d(TAG, "Message service enabled: $isEnabled")

        return isEnabled
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun getMessageCount(): Int {
        return try {
            val file = context.filesDir.resolve("messages.txt")
            if (file.exists()) {
                file.readLines().size
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun getMessages(): List<String> {
        return try {
            val file = context.filesDir.resolve("messages.txt")
            if (file.exists()) {
                file.readLines()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearMessages() {
        try {
            val file = context.filesDir.resolve("messages.txt")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing messages: ${e.message}")
        }
    }
}