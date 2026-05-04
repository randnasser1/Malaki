package com.example.malaki.db

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "BackendSyncManager"

private const val BACKEND_BASE_URL = "http://10.0.2.2:8000"

// ── Request / Response DTOs ───────────────────────────────────────────────────

data class EventPayload(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("source_app") val sourceApp: String?,
    @SerializedName("sender_role") val senderRole: String?,
    @SerializedName("timestamp_utc") val timestampUtc: Long,
    @SerializedName("text") val text: String?
)

data class EventBatchRequest(
    @SerializedName("events") val events: List<EventPayload>
)

data class EventResult(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("grooming_prob") val groomingProb: Float,
    @SerializedName("stage_label") val stageLabel: String?,
    @SerializedName("sentiment_score") val sentimentScore: Float,
    @SerializedName("emotion_vector") val emotionVector: Map<String, Float>,
    @SerializedName("anomaly_score") val anomalyScore: Float,
    @SerializedName("final_risk_score") val finalRiskScore: Float,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("threshold_used") val thresholdUsed: Float,
    @SerializedName("model_version") val modelVersion: String,
    @SerializedName("is_alert_triggered") val isAlertTriggered: Boolean,
    @SerializedName("top_tokens") val topTokens: List<Map<String, Any>>,
    @SerializedName("human_reason") val humanReason: String?
)

data class EventBatchResponse(
    @SerializedName("results") val results: List<EventResult>
)

// ── Sync Manager ──────────────────────────────────────────────────────────────

class BackendSyncManager(context: Context) {

    private val repository = EventRepository(context)
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()
    suspend fun syncPendingEvents() {
        val unsyncedEvents = repository.getUnsyncedEvents(limit = 20)
        if (unsyncedEvents.isEmpty()) return

        val payloads = unsyncedEvents.map { e ->
            EventPayload(
                eventId = e.eventId,
                deviceId = e.deviceId,
                eventType = e.eventType,
                sourceApp = e.sourceApp,
                senderRole = e.senderRole,
                timestampUtc = e.timestampUtc,
                text = e.rawTextEnc ?: e.textPreviewEnc
            )
        }

        val body = gson.toJson(EventBatchRequest(payloads))
            .toRequestBody(json)

        val request = Request.Builder()
            .url("$BACKEND_BASE_URL/events/analyze")
            .post(body)
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Backend returned ${response.code} for batch sync")
                    return
                }
                val raw = response.body?.string() ?: return
                val batchResponse = gson.fromJson(raw, EventBatchResponse::class.java)

                for (result in batchResponse.results) {
                    repository.saveAnalysisResults(
                        eventId = result.eventId,
                        groomingProb = result.groomingProb,
                        stageLabel = result.stageLabel,
                        sentimentScore = result.sentimentScore,
                        emotionVectorJson = gson.toJson(result.emotionVector),
                        anomalyScore = result.anomalyScore,
                        finalRiskScore = result.finalRiskScore,
                        riskLevel = result.riskLevel,
                        thresholdUsed = result.thresholdUsed,
                        modelVersion = result.modelVersion,
                        isAlertTriggered = result.isAlertTriggered,
                        topTokensJson = gson.toJson(result.topTokens),
                        humanReason = result.humanReason
                    )
                }
                Log.i(TAG, "Synced ${batchResponse.results.size} events")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend sync failed: ${e.message}")
        }
    }

    fun isBackendReachable(): Boolean {
        return try {
            val req = Request.Builder().url("$BACKEND_BASE_URL/health").get().build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
