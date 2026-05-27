package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false,
    val timestampAdded: Long = System.currentTimeMillis()
)
