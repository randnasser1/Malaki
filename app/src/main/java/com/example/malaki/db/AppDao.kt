package com.example.malaki.db

import androidx.room.*

@Dao
interface AppDao {

    // ── Device Profile ────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeviceProfile(entity: DeviceProfileEntity)

    @Query("SELECT * FROM device_profile WHERE deviceId = :id")
    suspend fun getDeviceProfile(id: String): DeviceProfileEntity?

    // ── Monitoring Settings ───────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonitoringSettings(entity: MonitoringSettingsEntity)

    @Query("SELECT * FROM monitoring_settings WHERE deviceId = :id")
    suspend fun getMonitoringSettings(id: String): MonitoringSettingsEntity?

    // ── Captured Events ───────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCapturedEvent(entity: CapturedEventEntity)

    @Query("SELECT * FROM captured_event WHERE deviceId = :id ORDER BY timestampUtc DESC LIMIT :limit")
    suspend fun getRecentEvents(id: String, limit: Int = 50): List<CapturedEventEntity>

    @Query("SELECT * FROM captured_event WHERE eventId = :id")
    suspend fun getEvent(id: String): CapturedEventEntity?

    // Events that have no model_output yet (i.e. not yet sent to backend)
    @Query("""
        SELECT e.* FROM captured_event e
        LEFT JOIN model_output m ON e.eventId = m.eventId
        WHERE e.deviceId = :deviceId AND m.eventId IS NULL
        ORDER BY e.timestampUtc ASC
        LIMIT :limit
    """)
    suspend fun getUnsyncedEvents(deviceId: String, limit: Int = 20): List<CapturedEventEntity>

    // ── Model Output ──────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModelOutput(entity: ModelOutputEntity)

    @Query("SELECT * FROM model_output WHERE eventId = :id")
    suspend fun getModelOutput(id: String): ModelOutputEntity?

    // ── Risk Assessment ───────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRiskAssessment(entity: RiskAssessmentEntity)

    @Query("SELECT * FROM risk_assessment WHERE eventId = :id")
    suspend fun getRiskAssessment(id: String): RiskAssessmentEntity?

    @Query("""
        SELECT r.* FROM risk_assessment r
        INNER JOIN captured_event e ON r.eventId = e.eventId
        WHERE e.deviceId = :deviceId AND r.riskLevel IN ('HIGH','MEDIUM')
        ORDER BY e.timestampUtc DESC
        LIMIT :limit
    """)
    suspend fun getHighRiskEvents(deviceId: String, limit: Int = 20): List<RiskAssessmentEntity>

    // ── Explanation ───────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExplanation(entity: ExplanationEntity)

    @Query("SELECT * FROM explanation WHERE eventId = :id")
    suspend fun getExplanation(id: String): ExplanationEntity?

    // ── Alert ─────────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlert(entity: AlertEntity)

    @Query("SELECT * FROM alert WHERE eventId = :id")
    suspend fun getAlertForEvent(id: String): AlertEntity?

    @Query("""
        SELECT a.* FROM alert a
        INNER JOIN captured_event e ON a.eventId = e.eventId
        WHERE e.deviceId = :deviceId AND a.deliveryStatus = 'QUEUED'
        ORDER BY a.sentAt ASC
    """)
    suspend fun getQueuedAlerts(deviceId: String): List<AlertEntity>

    @Query("UPDATE alert SET deliveryStatus = :status WHERE alertId = :alertId")
    suspend fun updateAlertStatus(alertId: String, status: String)

    @Query("UPDATE alert SET openedAt = :ts WHERE alertId = :alertId")
    suspend fun markAlertOpened(alertId: String, ts: Long)

    // ── Journal Entry ─────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJournalEntry(entity: JournalEntryEntity)

    @Query("SELECT * FROM journal_entry WHERE deviceId = :id ORDER BY timestampUtc DESC")
    suspend fun getJournalEntries(id: String): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entry WHERE journalId = :id")
    suspend fun getJournalEntry(id: String): JournalEntryEntity?

    // Journal entries not yet analyzed (no corresponding captured_event of type JOURNAL)
    @Query("""
        SELECT j.* FROM journal_entry j
        LEFT JOIN captured_event e ON (e.deviceId = j.deviceId AND e.eventType = 'JOURNAL'
            AND e.timestampUtc = j.timestampUtc)
        WHERE j.deviceId = :deviceId AND e.eventId IS NULL
        ORDER BY j.timestampUtc ASC
    """)
    suspend fun getUnsyncedJournalEntries(deviceId: String): List<JournalEntryEntity>

    // ── Wellbeing Daily Summary ───────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWellbeingSummary(entity: WellbeingDailySummaryEntity)

    @Query("SELECT * FROM wellbeing_daily_summary WHERE deviceId = :id ORDER BY date DESC LIMIT :days")
    suspend fun getWellbeingHistory(id: String, days: Int = 30): List<WellbeingDailySummaryEntity>

    @Query("SELECT * FROM wellbeing_daily_summary WHERE deviceId = :id AND date = :date")
    suspend fun getWellbeingSummaryForDate(id: String, date: String): WellbeingDailySummaryEntity?

    // ── Cleanup ───────────────────────────────────────────────────────────────
    @Query("DELETE FROM captured_event WHERE deviceId = :deviceId AND timestampUtc < :beforeMs")
    suspend fun purgeOldEvents(deviceId: String, beforeMs: Long)
}
