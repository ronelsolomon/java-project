package com.example.emotiondetector.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emotiondetector.util.AppLogger
import com.example.emotiondetector.util.DateTimeUtils
import com.example.emotiondetector.util.FileUtils
import com.example.emotiondetector.util.Prefs
import com.example.emotiondetector.util.json
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appLogger: AppLogger,
    private val prefs: Prefs,
    private val fileUtils: FileUtils
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Preferences example
    private var appLaunchCount by prefs.json("app_launch_count", 0)
    
    // User settings with default values
    private var isDarkMode by prefs.json("dark_mode", false)
    private var lastUpdateCheck by prefs.json<Date>("last_update_check")
    
    init {
        initializeApp()
    }
    
    private fun initializeApp() {
        viewModelScope.launch {
            try {
                // Track app launches
                appLaunchCount++
                appLogger.d("MainViewModel", "App launch count: $appLaunchCount")
                
                // Update last check time
                lastUpdateCheck = Date()
                
                // Check for updates if needed
                checkForUpdates()
                
                // Load initial data
                loadInitialData()
                
                _uiState.value = MainUiState.Success("App initialized successfully")
            } catch (e: Exception) {
                appLogger.e("MainViewModel", "Error initializing app", e)
                _uiState.value = MainUiState.Error("Initialization failed: ${e.message}")
            }
        }
    }
    
    private fun checkForUpdates() {
        // Example: Check for updates if it's been more than 24 hours
        val lastCheck = lastUpdateCheck?.time ?: 0L
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        
        if (System.currentTimeMillis() - lastCheck > oneDayInMillis) {
            appLogger.i("MainViewModel", "Checking for updates...")
            // TODO: Implement actual update check
        }
    }
    
    private fun loadInitialData() {
        // Example: Load some initial data
        val cachedData = prefs.json<AppData>("cached_data") ?: AppData()
        // Process cached data...
    }
    
    fun toggleDarkMode(enabled: Boolean) {
        isDarkMode = enabled
        appLogger.d("MainViewModel", "Dark mode ${if (enabled) "enabled" else "disabled"}")
        // TODO: Apply theme changes
    }
    
    fun saveImage(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = MainUiState.Loading
                
                // Example: Save image using FileUtils
                val savedPath = fileUtils.saveImageFromUri(uri, "emotion_captures")
                
                // Log the action with timestamp
                val timestamp = DateTimeUtils.formatDate(Date(), "yyyy-MM-dd HH:mm:ss")
                appLogger.i("MainViewModel", "Image saved at: $savedPath ($timestamp)")
                
                _uiState.value = MainUiState.Success("Image saved successfully")
            } catch (e: Exception) {
                appLogger.e("MainViewModel", "Error saving image", e)
                _uiState.value = MainUiState.Error("Failed to save image: ${e.message}")
            }
        }
    }
    
    // Data classes for state management
    sealed class MainUiState {
        object Loading : MainUiState()
        data class Success(val message: String) : MainUiState()
        data class Error(val message: String) : MainUiState()
    }
    
    data class AppData(
        val lastSync: Date = Date(),
        val settings: Map<String, Any> = emptyMap(),
        val recentEmotions: List<String> = emptyList()
    )
}
