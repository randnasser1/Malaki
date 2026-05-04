package com.example.malaki.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceProfileEntity::class,
        MonitoringSettingsEntity::class,
        CapturedEventEntity::class,
        ModelOutputEntity::class,
        RiskAssessmentEntity::class,
        ExplanationEntity::class,
        AlertEntity::class,
        JournalEntryEntity::class,
        WellbeingDailySummaryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MalakiDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile private var INSTANCE: MalakiDatabase? = null

        fun getInstance(context: Context): MalakiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MalakiDatabase::class.java,
                    "malaki_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
