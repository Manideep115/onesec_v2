package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY appName ASC")
    fun getAllAppsFlow(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps ORDER BY appName ASC")
    suspend fun getAllApps(): List<MonitoredApp>

    @Query("SELECT * FROM monitored_apps WHERE isEnabled = 1")
    suspend fun getEnabledApps(): List<MonitoredApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: MonitoredApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<MonitoredApp>)

    @Update
    suspend fun updateApp(app: MonitoredApp)

    @Delete
    suspend fun deleteApp(app: MonitoredApp)

    @Query("DELETE FROM monitored_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
