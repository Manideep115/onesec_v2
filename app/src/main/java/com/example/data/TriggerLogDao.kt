package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerLogDao {
    @Query("SELECT * FROM trigger_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<TriggerLog>>

    @Query("SELECT * FROM trigger_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<TriggerLog>

    @Query("SELECT * FROM trigger_logs WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp ORDER BY timestamp DESC")
    suspend fun getLogsBetween(startTimestamp: Long, endTimestamp: Long): List<TriggerLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TriggerLog)

    @Query("DELETE FROM trigger_logs")
    suspend fun clearAllLogs()
}
