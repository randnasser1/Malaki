package com.example.malaki.db

import android.content.Context
import android.provider.Settings
import java.util.UUID

class EventRepository(context: Context) {

    private val dao = MalakiDatabase.getInstance(context).appDao()

    // Stable device ID derived from Android's unique hardware identifier
    val deviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: UUID.randomUUID().toString()

    suspend fun ensureDeviceProfile(appVersion: String = "1.0", modelVersion: String = "1.0") {
        if (dao.getDeviceProfile(deviceId) == null) {
            dao.insertDeviceProfile(
                DeviceProfileEntity(
                    deviceId = deviceId,
                    platform = "android",
                    appVersion = appVersion,
                    modelVersion = modelVersion,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        if (dao.getMonitoringSettings(deviceId) == null) {
            dao.upsertMonitoringSettings(
                MonitoringSettingsEntity(
                    deviceId = deviceId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun captureEvent(
        eventType: String,              // MESSAGE | URL | APP_USAGE | JOURNAL
        sourceApp: String? = null,
        chatIdHash: String? = null,
        senderRole: String? = null,     // SELF | OTHER | UNKNOWN
        textPreview: String? = null,
        rawText: String? = null,
        timestampUtc: Long = System.currentTimeMillis()
    ): String {
        val eventId = UUID.randomUUID().toString()
        dao.insertCapturedEvent(
            CapturedEventEntity(
                eventId = eventId,
                deviceId = deviceId,
                eventType = eventType,
                sourceApp = sourceApp,
                chatIdHash = chatIdHash,
                senderRole = senderRole,
                timestampUtc = timestampUtc,
                textPreviewEnc = textPreview,
                rawTextEnc = rawText
            )
        )
        return eventId
    }

    suspend fun captureJournalEntry(
        entryText: String,
        moodLabel: String? = null,
        timestampUtc: Long = System.currentTimeMillis()
    ): String {
        val journalId = UUID.randomUUID().toString()
        dao.insertJournalEntry(
            JournalEntryEntity(
                journalId = journalId,
                deviceId = deviceId,
                timestampUtc = timestampUtc,
                entryTextEnc = entryText,
                moodLabel = moodLabel
            )
        )
        // Also store as a captured event so it enters the analysis pipeline
        captureEvent(
            eventType = "JOURNAL",
            rawText = entryText,
            textPreview = entryText.take(200),
            timestampUtc = timestampUtc
        )
        return journalId
    }

    suspend fun saveAnalysisResults(
        eventId: String,
        groomingProb: Float,
        stageLabel: String?,
        sentimentScore: Float,
        emotionVectorJson: String,
        anomalyScore: Float,
        finalRiskScore: Float,
        riskLevel: String,         // LOW | MEDIUM | HIGH
        thresholdUsed: Float,
        modelVersion: String,
        isAlertTriggered: Boolean,
        explanationMethod: String = "SHAP",
        topTokensJson: String = "[]",
        humanReason: String? = null
    ) {
        val now = System.currentTimeMillis()

        dao.upsertModelOutput(
            ModelOutputEntity(
                eventId = eventId,
                groomingProb = groomingProb,
                stageLabel = stageLabel,
                sentimentScore = sentimentScore,
                emotionVectorJson = emotionVectorJson,
                anomalyScore = anomalyScore,
                createdAt = now
            )
        )

        dao.upsertRiskAssessment(
            RiskAssessmentEntity(
                eventId = eventId,
                finalRiskScore = finalRiskScore,
                riskLevel = riskLevel,
                thresholdUsed = thresholdUsed,
                decisionModelVersion = modelVersion,
                isAlertTriggered = isAlertTriggered,
                createdAt = now
            )
        )

        dao.upsertExplanation(
            ExplanationEntity(
                eventId = eventId,
                method = explanationMethod,
                topTokensJson = topTokensJson,
                humanReasonEnc = humanReason,
                createdAt = now
            )
        )

        if (isAlertTriggered) {
            dao.upsertAlert(
                AlertEntity(
                    alertId = UUID.randomUUID().toString(),
                    eventId = eventId,
                    severity = riskLevel,
                    sentAt = now,
                    deliveryStatus = "QUEUED",
                    openedAt = null
                )
            )
        }
    }

    suspend fun getUnsyncedEvents(limit: Int = 20) = dao.getUnsyncedEvents(deviceId, limit)

    suspend fun saveDailyWellbeingSummary(
        date: String,
        avgSentiment: Float,
        dominantEmotion: String,
        anomalyFlag: Boolean = false,
        notesEnc: String? = null
    ) {
        dao.upsertWellbeingSummary(
            WellbeingDailySummaryEntity(
                deviceId = deviceId,
                date = date,
                avgSentiment = avgSentiment,
                dominantEmotion = dominantEmotion,
                anomalyFlag = anomalyFlag,
                notesEnc = notesEnc
            )
        )
    }

    suspend fun purgeOldEvents(retentionDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24 * 60 * 60 * 1000L
        dao.purgeOldEvents(deviceId, cutoff)
    }
}
