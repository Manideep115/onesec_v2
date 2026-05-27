package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class HabitRepository(
    private val monitoredAppDao: MonitoredAppDao,
    private val triggerLogDao: TriggerLogDao
) {
    val allMonitoredApps: Flow<List<MonitoredApp>> = monitoredAppDao.getAllAppsFlow()
    val allTriggerLogs: Flow<List<TriggerLog>> = triggerLogDao.getAllLogsFlow()

    suspend fun initDefaultAppsIfNeeded() {
        val existing = monitoredAppDao.getAllApps()
        if (existing.isEmpty()) {
            val defaults = listOf(
                MonitoredApp(packageName = "com.instagram.android", appName = "Instagram"),
                MonitoredApp(packageName = "com.google.android.youtube", appName = "YouTube"),
                MonitoredApp(packageName = "com.zhiliaoapp.musically", appName = "TikTok"),
                MonitoredApp(packageName = "com.facebook.katana", appName = "Facebook"),
                MonitoredApp(packageName = "com.twitter.android", appName = "X / Twitter"),
                MonitoredApp(packageName = "com.dts.freefireth", appName = "Free Fire"),
                MonitoredApp(packageName = "com.snapchat.android", appName = "Snapchat")
            )
            monitoredAppDao.insertApps(defaults)
        }
    }

    suspend fun getEnabledApps(): List<MonitoredApp> {
        return monitoredAppDao.getEnabledApps()
    }

    suspend fun toggleAppMonitoring(packageName: String, isEnabled: Boolean) {
        val apps = monitoredAppDao.getAllApps()
        val match = apps.firstOrNull { it.packageName == packageName }
        if (match != null) {
            monitoredAppDao.updateApp(match.copy(isEnabled = isEnabled))
        }
    }

    suspend fun addCustomApp(packageName: String, appName: String) {
        val existing = monitoredAppDao.getAllApps()
        if (existing.none { it.packageName == packageName }) {
            monitoredAppDao.insertApp(
                MonitoredApp(
                    packageName = packageName,
                    appName = appName,
                    isEnabled = true,
                    isCustom = true
                )
            )
        }
    }

    suspend fun removeCustomApp(packageName: String) {
        monitoredAppDao.deleteByPackageName(packageName)
    }

    suspend fun logTrigger(packageName: String, appName: String, focusMode: String, action: String) {
        val log = TriggerLog(
            packageName = packageName,
            appName = appName,
            focusMode = focusMode,
            actionTaken = action
        )
        triggerLogDao.insertLog(log)
    }

    suspend fun clearHistory() {
        triggerLogDao.clearAllLogs()
    }

    data class AnalyticsData(
        val totalTriggers: Int,
        val avoidedTriggers: Int,
        val screenTimeSavedMinutes: Int,
        val streakDays: Int,
        val topTriggeredApps: List<Map.Entry<String, Int>>
    )

    suspend fun calculateAnalytics(): AnalyticsData {
        val logs = triggerLogDao.getAllLogs()
        val total = logs.size
        val avoided = logs.count { it.actionTaken == "AVOIDED" }
        
        // Estimate 10 minutes saved per avoided check
        val savedTime = avoided * 10
        val streak = calculateFocusStreak(logs)
        
        val appCounts = logs.groupingBy { it.appName }.eachCount()
        val topApps = appCounts.entries
            .sortedByDescending { it.value }
            .take(3)

        return AnalyticsData(
            totalTriggers = total,
            avoidedTriggers = avoided,
            screenTimeSavedMinutes = savedTime,
            streakDays = streak,
            topTriggeredApps = topApps
        )
    }

    private fun calculateFocusStreak(logs: List<TriggerLog>): Int {
        if (logs.isEmpty()) return 0
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val avoidedDates = logs.filter { it.actionTaken == "AVOIDED" }
            .map { sdf.format(Date(it.timestamp)) }
            .toSet()

        if (avoidedDates.isEmpty()) return 0

        val todayStr = sdf.format(Date())
        val cal = Calendar.getInstance()
        var currentCheckDate = todayStr

        if (!avoidedDates.contains(todayStr)) {
            cal.add(Calendar.DATE, -1)
            val yesterdayStr = sdf.format(cal.time)
            if (avoidedDates.contains(yesterdayStr)) {
                currentCheckDate = yesterdayStr
            } else {
                return 0
            }
        }

        cal.time = sdf.parse(currentCheckDate) ?: return 0
        var streak = 0
        while (avoidedDates.contains(sdf.format(cal.time))) {
            streak++
            cal.add(Calendar.DATE, -1)
        }

        return streak
    }
}
