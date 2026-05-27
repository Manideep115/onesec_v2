package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.data.AppDatabase
import com.example.data.PreferenceHelper
import com.example.ui.InterruptionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HabitInterruptionService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var prefs: PreferenceHelper
    private var monitoredPackages = setOf<String>()

    companion object {
        private const val TAG = "OneSecService"
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceHelper(this)
        isServiceRunning = true
        observeDb()
    }

    private fun observeDb() {
        val db = AppDatabase.getDatabase(this)
        serviceScope.launch {
            db.monitoredAppDao().getAllAppsFlow().collect { apps ->
                monitoredPackages = apps.filter { it.isEnabled }.map { it.packageName }.toSet()
                Log.d(TAG, "Monitored packages updated: $monitoredPackages")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!prefs.isHabitBreakerEnabled) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageNameChar = event.packageName ?: return
            val packageName = packageNameChar.toString()

            // Don't monitor our own app package to prevent redirection loop
            if (packageName == this.packageName) return

            if (monitoredPackages.contains(packageName)) {
                // Check if bypassed temp
                if (prefs.isBypassed(packageName)) {
                    Log.d(TAG, "$packageName is bypassed, allowing access")
                    return
                }

                Log.d(TAG, "Interception triggered for: $packageName")
                val appName = getAppNameFromPackage(packageName)
                val intent = Intent(this, InterruptionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("TARGET_PACKAGE_NAME", packageName)
                    putExtra("TARGET_APP_NAME", appName)
                }
                startActivity(intent)
            }
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            when (packageName) {
                "com.instagram.android" -> "Instagram"
                "com.google.android.youtube" -> "YouTube"
                "com.zhiliaoapp.musically" -> "TikTok"
                "com.facebook.katana" -> "Facebook"
                "com.twitter.android" -> "X / Twitter"
                "com.dts.freefireth" -> "Free Fire"
                "com.snapchat.android" -> "Snapchat"
                else -> {
                    packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service onInterrupt called")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceJob.cancel()
    }
}
