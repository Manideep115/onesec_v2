package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HabitRepository
import com.example.data.MonitoredApp
import com.example.data.PreferenceHelper
import com.example.data.api.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HabitViewModel(
    application: Application,
    private val repository: HabitRepository,
    private val prefs: PreferenceHelper
) : AndroidViewModel(application) {

    val allMonitoredApps: StateFlow<List<MonitoredApp>> = repository.allMonitoredApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _analyticsData = MutableStateFlow<HabitRepository.AnalyticsData?>(null)
    val analyticsData: StateFlow<HabitRepository.AnalyticsData?> = _analyticsData.asStateFlow()

    private val _activeFocusMode = MutableStateFlow(prefs.activeFocusMode)
    val activeFocusMode: StateFlow<String> = _activeFocusMode.asStateFlow()

    private val _isHabitBreakerEnabled = MutableStateFlow(prefs.isHabitBreakerEnabled)
    val isHabitBreakerEnabled: StateFlow<Boolean> = _isHabitBreakerEnabled.asStateFlow()

    private val _aiCoachFeedback = MutableStateFlow<String?>(null)
    val aiCoachFeedback: StateFlow<String?> = _aiCoachFeedback.asStateFlow()

    private val _aiCoachLoading = MutableStateFlow(false)
    val aiCoachLoading: StateFlow<Boolean> = _aiCoachLoading.asStateFlow()

    init {
        viewModelScope.launch {
            // First populate default apps if database is empty
            repository.initDefaultAppsIfNeeded()
            // Observe logs to auto-calculate analytics
            repository.allTriggerLogs.collect {
                refreshAnalytics()
            }
        }
    }

    fun refreshAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = repository.calculateAnalytics()
            _analyticsData.value = stats
        }
    }

    fun toggleAppMonitoring(packageName: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAppMonitoring(packageName, isEnabled)
        }
    }

    fun addCustomApp(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.addCustomApp(packageName, appName)
        }
    }

    fun removeCustomApp(packageName: String) {
        viewModelScope.launch {
            repository.removeCustomApp(packageName)
        }
    }

    fun setFocusMode(mode: String) {
        prefs.activeFocusMode = mode
        _activeFocusMode.value = mode
    }

    fun toggleHabitBreaker(isEnabled: Boolean) {
        prefs.isHabitBreakerEnabled = isEnabled
        _isHabitBreakerEnabled.value = isEnabled
    }

    fun loadAICoachFeedback() {
        viewModelScope.launch {
            _aiCoachLoading.value = true
            val stats = _analyticsData.value ?: repository.calculateAnalytics()
            val topApps = stats.topTriggeredApps.map { it.key }
            
            val feedback = GeminiService.generateCoachingSession(
                totalTriggers = stats.totalTriggers,
                avoidedTriggers = stats.avoidedTriggers,
                savedTimeMinutes = stats.screenTimeSavedMinutes,
                streakDays = stats.streakDays,
                topApps = topApps
            )
            _aiCoachFeedback.value = feedback
            _aiCoachLoading.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            refreshAnalytics()
        }
    }

    class Factory(
        private val application: Application,
        private val repository: HabitRepository,
        private val prefs: PreferenceHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
                return HabitViewModel(application, repository, prefs) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
