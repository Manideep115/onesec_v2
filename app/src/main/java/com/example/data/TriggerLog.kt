package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trigger_logs")
data class TriggerLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val focusMode: String, // e.g., Study, Deep Work, Sleep, Dopamine Detox, standard
    val actionTaken: String // "CONTINUED" or "AVOIDED"
)
