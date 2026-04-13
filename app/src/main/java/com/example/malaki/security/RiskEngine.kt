package com.example.malaki.security

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RiskEngine(private val context: Context) {

    companion object {
        private const val TAG = "RiskEngine"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    data class UnifiedRiskReport(
        val overallRisk: Float,
        val riskLevel: ContentSafetyManager.RiskLevel,
        val alerts: List<Alert>,
        val summary: String
    )

    data class Alert(
        val type: String,  // "url", "message", "music", "app_usage"
        val content: String,
        val riskScore: Float,
        val reason: String,
        val timestamp: Long
    )

    suspend fun processAndSendAlerts() {
        try {
            // Get current child user (in real app, get linked child ID)
            val currentUser = auth.currentUser ?: return
            val childId = currentUser.uid

            // Collect all recent alerts
            val alerts = collectRecentAlerts()

            if (alerts.isNotEmpty()) {
                // Calculate overall risk
                val overallRisk = alerts.map { it.riskScore }.average().toFloat()
                val riskLevel = getRiskLevel(overallRisk)

                // Store in Firestore
                val report = hashMapOf(
                    "childId" to childId,
                    "timestamp" to System.currentTimeMillis(),
                    "overallRisk" to overallRisk,
                    "riskLevel" to riskLevel.name,
                    "alertCount" to alerts.size,
                    "alerts" to alerts.map { hashMapOf(
                        "type" to it.type,
                        "content" to it.content.take(200),
                        "riskScore" to it.riskScore,
                        "reason" to it.reason,
                        "timestamp" to it.timestamp
                    ) }
                )

                firestore.collection("risk_reports")
                    .add(report)
                    .await()

                Log.d(TAG, "Sent ${alerts.size} alerts to Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing alerts: ${e.message}")
        }
    }

    private suspend fun collectRecentAlerts(): List<Alert> {
        val alerts = mutableListOf<Alert>()

        // Read URL analysis results
        val urlAnalysisFile = context.filesDir.resolve("url_analysis.json")
        if (urlAnalysisFile.exists()) {
            try {
                val jsonArray = org.json.JSONArray(urlAnalysisFile.readText())
                val now = System.currentTimeMillis()

                for (i in 0 until jsonArray.length()) {
                    val entry = jsonArray.getJSONObject(i)
                    val timestamp = entry.getLong("timestamp")

                    // Only process alerts from last hour
                    if (now - timestamp < 3600000) {
                        if (!entry.getBoolean("isSafe")) {
                            alerts.add(Alert(
                                type = "url",
                                content = entry.getString("url"),
                                riskScore = when (entry.getString("riskLevel")) {
                                    "CRITICAL" -> 0.95f
                                    "HIGH" -> 0.8f
                                    "MEDIUM" -> 0.6f
                                    else -> 0.4f
                                },
                                reason = buildString {
                                    val reasonsArray = entry.getJSONArray("blockReasons")
                                    for (i in 0 until reasonsArray.length()) {
                                        if (i > 0) append(", ")
                                        append(reasonsArray.getString(i))
                                    }
                                },                                timestamp = timestamp
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading URL analysis: ${e.message}")
            }
        }

        return alerts
    }

    private fun getRiskLevel(score: Float): ContentSafetyManager.RiskLevel {
        return when {
            score >= 0.9 -> ContentSafetyManager.RiskLevel.CRITICAL
            score >= 0.7 -> ContentSafetyManager.RiskLevel.HIGH
            score >= 0.4 -> ContentSafetyManager.RiskLevel.MEDIUM
            score >= 0.2 -> ContentSafetyManager.RiskLevel.LOW
            else -> ContentSafetyManager.RiskLevel.SAFE
        }
    }
}