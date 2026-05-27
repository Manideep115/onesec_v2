package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("onesec_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACTIVE_MODE = "active_mode"
        private const val KEY_BREAK_ENABLED = "break_enabled"
        private const val BYPASS_PREFIX = "bypass_"
    }

    var activeFocusMode: String
        get() = prefs.getString(KEY_ACTIVE_MODE, "Standard") ?: "Standard"
        set(value) = prefs.edit().putString(KEY_ACTIVE_MODE, value).apply()

    var isHabitBreakerEnabled: Boolean
        get() = prefs.getBoolean(KEY_BREAK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BREAK_ENABLED, value).apply()

    fun setTemporaryBypass(packageName: String, durationMinutes: Int) {
        val until = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        prefs.edit().putLong(BYPASS_PREFIX + packageName, until).apply()
    }

    fun isBypassed(packageName: String): Boolean {
        val until = prefs.getLong(BYPASS_PREFIX + packageName, 0L)
        return System.currentTimeMillis() < until
    }

    fun clearBypasses() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(BYPASS_PREFIX) }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }
}
