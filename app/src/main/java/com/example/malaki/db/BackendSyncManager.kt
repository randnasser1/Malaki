package com.example.malaki.db

import android.content.Context
import android.util.Log
import com.example.malaki.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "BackendSyncManager"

private val BACKEND_BASE_URL get() = BuildConfig.BACKEND_BASE_URL

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
        .readTimeout(90, TimeUnit.SECONDS)   // ML inference on 5 events needs headroom
        .build()
    private val json = "application/json".toMediaType()

    /** Returns true if at least one event was successfully synced. */
    suspend fun syncPendingEvents(): Boolean {
        Log.d(TAG, "🔵 syncPendingEvents CALLED")

        val unsyncedEvents = repository.getUnsyncedEvents(limit = 5)
        Log.d(TAG, "🔵 Found ${unsyncedEvents.size} unsynced events")

        if (unsyncedEvents.isEmpty()) return true

        // Only sync events from the last 6 hours.
        // Stale events that timed out before (yesterday, etc.) can never be acked because
        // Android never received the response — they'll be purged by purgeOldEvents().
        val recentCutoff = System.currentTimeMillis() - 6 * 60 * 60 * 1000L
        val recentEvents = unsyncedEvents.filter { it.timestampUtc >= recentCutoff }

        if (recentEvents.isEmpty()) {
            Log.d(TAG, "🔵 No recent events to sync (${unsyncedEvents.size} old stale events — purging)")
            repository.purgeOldEvents(retentionDays = 1)
            return true
        }

        val payloads = recentEvents.map { e ->
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

        val body = gson.toJson(EventBatchRequest(payloads)).toRequestBody(json)
        val request = Request.Builder()
            .url("$BACKEND_BASE_URL/events/analyze")
            .post(body)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "❌ Backend returned ${response.code}")
                        return@withContext false
                    }
                    val raw = response.body?.string() ?: run {
                        Log.w(TAG, "❌ Empty response body")
                        return@withContext false
                    }
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
                    Log.i(TAG, "✅ Synced ${batchResponse.results.size}/${recentEvents.size} events")
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "❌ Backend sync failed [${e::class.simpleName}]: ${e.message}")
            false
        }
    }

    suspend fun isBackendReachable(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url("$BACKEND_BASE_URL/health").get().build()
                http.newCall(req).execute().use { it.isSuccessful }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun syncSingleEvent(eventId: String, text: String) {
        try {
            val payload = EventPayload(
                eventId = eventId,
                deviceId = repository.deviceId,
                eventType = when {
                    text.contains("\"track_info\"") -> "MUSIC"
                    else -> "MESSAGE"
                },
                sourceApp = null,
                senderRole = "OTHER",
                timestampUtc = System.currentTimeMillis(),
                text = text
            )

            val body = gson.toJson(EventBatchRequest(events = listOf(payload)))
                .toRequestBody(json)

            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/events/analyze")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Real-time sync successful for $eventId")
                    } else {
                        Log.w(TAG, "❌ Real-time sync returned ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "❌ Real-time sync failed [${e::class.simpleName}]: ${e.message}")
        }
    }
}