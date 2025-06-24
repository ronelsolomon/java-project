package com.example.emotiondetector.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emotiondetector.domain.EmotionDetector
import com.example.emotiondetector.domain.EmotionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class EmotionViewModel @Inject constructor(
    private val emotionDetector: EmotionDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmotionUiState())
    val uiState: StateFlow<EmotionUiState> = _uiState.asStateFlow()
    
    private val isProcessing = AtomicBoolean(false)
    private var processingJob: Job? = null
    
    // Track FPS for performance monitoring
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    
    // Model performance metrics
    private var totalInferenceTime = 0L
    private var inferenceCount = 0
    
    // Current camera facing direction
    var cameraFacingFront by mutableStateOf(true)
        private set
    
    /**
     * Process a camera frame for emotion detection
     */
    fun processImage(imageProxy: ImageProxy) {
        // Skip if already processing or UI is not ready
        if (isProcessing.get() || !_uiState.value.isReady) {
            imageProxy.close()
            return
        }
        
        isProcessing.set(true)
        
        // Cancel any existing processing job
        processingJob?.cancel()
        
        processingJob = viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // Detect emotions in the image
                val results = emotionDetector.detectEmotions(imageProxy)
                
                // Calculate inference time
                val inferenceTime = System.currentTimeMillis() - startTime
                
                // Update metrics
                totalInferenceTime += inferenceTime
                inferenceCount++
                
                // Update FPS counter
                updateFps()
                
                // Update UI state with results
                _uiState.update { currentState ->
                    currentState.copy(
                        emotionResults = results,
                        lastInferenceTime = inferenceTime,
                        averageInferenceTime = if (inferenceCount > 0) {
                            totalInferenceTime / inferenceCount
                        } else {
                            0L
                        },
                        isProcessing = false
                    )
                }
                
                // Log performance metrics
                if (inferenceCount % 30 == 0) { // Log every 30 frames
                    Log.d("EmotionViewModel", 
                        "Avg inference time: ${_uiState.value.averageInferenceTime}ms, " +
                        "FPS: ${_uiState.value.currentFps}")
                }
                
            } catch (e: Exception) {
                Log.e("EmotionViewModel", "Error processing image", e)
                _uiState.update { it.copy(error = e.message, isProcessing = false) }
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    /**
     * Toggle between front and back camera
     */
    fun toggleCamera() {
        cameraFacingFront = !cameraFacingFront
        _uiState.update { it.copy(cameraFacingFront = cameraFacingFront) }
    }
    
    /**
     * Update FPS counter
     */
    private fun updateFps() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - lastFpsUpdateTime
        
        // Update FPS every second
        if (elapsedTime >= 1000) {
            val fps = (frameCount * 1000 / elapsedTime.toFloat()).toInt()
            _uiState.update { it.copy(currentFps = fps) }
            frameCount = 0
            lastFpsUpdateTime = currentTime
        }
    }
    
    /**
     * Reset the emotion detection state
     */
    fun reset() {
        _uiState.update { EmotionUiState() }
        frameCount = 0
        lastFpsUpdateTime = System.currentTimeMillis()
        totalInferenceTime = 0L
        inferenceCount = 0
    }
    
    /**
     * Set error state
     */
    fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
        emotionDetector.close()
    }
}

/**
 * UI state for the emotion detection screen
 */
data class EmotionUiState(
    val emotionResults: List<EmotionResult> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentFps: Int = 0,
    val lastInferenceTime: Long = 0,
    val averageInferenceTime: Long = 0,
    val cameraFacingFront: Boolean = true,
    val isReady: Boolean = true // Indicates if the detector is ready to process frames
)
