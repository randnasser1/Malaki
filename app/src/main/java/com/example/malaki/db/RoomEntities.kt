package com.example.malaki.db

import androidx.room.*

@Entity(tableName = "device_profile")
data class DeviceProfileEntity(
    @PrimaryKey val deviceId: String,
    val platform: String,
    val appVersion: String,
    val modelVersion: String,
    val createdAt: Long
)

@Entity(
    tableName = "monitoring_settings",
    foreignKeys = [ForeignKey(
        entity = DeviceProfileEntity::class,
        parentColumns = ["deviceId"], childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MonitoringSettingsEntity(
    @PrimaryKey val deviceId: String,
    val monitoringEnabled: Boolean = true,
    val enabledSourcesJson: String = """["MESSAGE","URL","APP_USAGE","JOURNAL"]""",
    val riskThreshold: Float = 0.5f,
    val retentionDays: Int = 30,
    val updatedAt: Long
)

@Entity(
    tableName = "captured_event",
    foreignKeys = [ForeignKey(
        entity = DeviceProfileEntity::class,
        parentColumns = ["deviceId"], childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId"), Index("timestampUtc"), Index("eventType")]
)
data class CapturedEventEntity(
    @PrimaryKey val eventId: String,
    val deviceId: String,
    // MESSAGE | URL | APP_USAGE | JOURNAL
    val eventType: String,
    val sourceApp: String?,
    val chatIdHash: String?,
    // SELF | OTHER | UNKNOWN
    val senderRole: String?,
    val timestampUtc: Long,
    // Encrypted in production; plain text for now
    val textPreviewEnc: String?,
    val rawTextEnc: String?
)

@Entity(
    tableName = "model_output",
    foreignKeys = [ForeignKey(
        entity = CapturedEventEntity::class,
        parentColumns = ["eventId"], childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ModelOutputEntity(
    @PrimaryKey val eventId: String,
    val groomingProb: Float,
    val stageLabel: String?,
    val sentimentScore: Float,
    val emotionVectorJson: String,
    val anomalyScore: Float,
    val createdAt: Long
)

@Entity(
    tableName = "risk_assessment",
    foreignKeys = [ForeignKey(
        entity = CapturedEventEntity::class,
        parentColumns = ["eventId"], childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RiskAssessmentEntity(
    @PrimaryKey val eventId: String,
    val finalRiskScore: Float,
    // LOW | MEDIUM | HIGH
    val riskLevel: String,
    val thresholdUsed: Float,
    val decisionModelVersion: String,
    val isAlertTriggered: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "explanation",
    foreignKeys = [ForeignKey(
        entity = CapturedEventEntity::class,
        parentColumns = ["eventId"], childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ExplanationEntity(
    @PrimaryKey val eventId: String,
    // IG | SHAP
    val method: String,
    val topTokensJson: String,
    val humanReasonEnc: String?,
    val createdAt: Long
)

@Entity(
    tableName = "alert",
    foreignKeys = [ForeignKey(
        entity = CapturedEventEntity::class,
        parentColumns = ["eventId"], childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventId")]
)
data class AlertEntity(
    @PrimaryKey val alertId: String,
    val eventId: String,
    // Derived from risk_level
    val severity: String,
    val sentAt: Long,
    // SENT | FAILED | QUEUED
    val deliveryStatus: String,
    val openedAt: Long?
)

@Entity(
    tableName = "journal_entry",
    foreignKeys = [ForeignKey(
        entity = DeviceProfileEntity::class,
        parentColumns = ["deviceId"], childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId"), Index("timestampUtc")]
)
data class JournalEntryEntity(
    @PrimaryKey val journalId: String,
    val deviceId: String,
    val timestampUtc: Long,
    val entryTextEnc: String,
    val moodLabel: String?,
    val sentimentScore: Float = 0f,
    val emotionVectorJson: String = "{}"
)

@Entity(
    tableName = "wellbeing_daily_summary",
    primaryKeys = ["deviceId", "date"],
    foreignKeys = [ForeignKey(
        entity = DeviceProfileEntity::class,
        parentColumns = ["deviceId"], childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId")]
)
data class WellbeingDailySummaryEntity(
    val deviceId: String,
    val date: String,
    val avgSentiment: Float,
    val dominantEmotion: String,
    val anomalyFlag: Boolean,
    val notesEnc: String?
)
