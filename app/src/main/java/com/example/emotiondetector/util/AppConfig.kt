package com.example.emotiondetector.util

import android.content.Context
import androidx.annotation.StringRes
import com.example.emotiondetector.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized configuration for the application
 */
@Singleton
class AppConfig @Inject constructor(
    private val context: Context
) {
    // Model configuration
    object Model {
        const val DEFAULT_MODEL_FILENAME = "emotion_model.tflite"
        const val MODEL_INPUT_SIZE = 48 // Width/height of the input image expected by the model
        const val MODEL_OUTPUT_SIZE = 7 // Number of emotion classes
        const val MODEL_MEAN = 0f // Mean normalization value
        const val MODEL_STD = 255f // Standard deviation for normalization
        const val DEFAULT_THRESHOLD = 0.7f // Confidence threshold for predictions
        
        // Model update configuration
        const val MODEL_UPDATE_CHECK_INTERVAL_HOURS = 24L
        const val MODEL_DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 60L
        const val MODEL_DOWNLOAD_READ_TIMEOUT_SECONDS = 60L
    }
    
    // Camera configuration
    object Camera {
        const val TARGET_ASPECT_RATIO = 4f / 3f // 4:3 aspect ratio for camera preview
        const val TARGET_RESOLUTION_WIDTH = 1280
        const val TARGET_RESOLUTION_HEIGHT = 720
        const val TARGET_FRAME_RATE = 30 // Target frames per second
        const val ANALYSIS_IMAGE_FORMAT = android.graphics.ImageFormat.YUV_420_888
    }
    
    // Telemetry configuration
    object Telemetry {
        const val UPLOAD_INTERVAL_HOURS = 24L
        const val MAX_BATCH_SIZE = 100
        const val MAX_STORED_EVENTS = 1000
        const val MAX_RETRY_ATTEMPTS = 3
        const val INITIAL_RETRY_DELAY_MS = 10_000L // 10 seconds
    }
    
    // API configuration
    object Api {
        const val BASE_URL = "https://api.your-backend.com/v1/"
        const val TIMEOUT_SECONDS = 30L
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1000L
    }
    
    // Storage configuration
    object Storage {
        const val DATABASE_NAME = "emotion_detector.db"
        const val MODEL_DIR = "models"
        const val CACHE_DIR = "cache"
        const val MAX_CACHE_SIZE_BYTES = 50L * 1024 * 1024 // 50MB
    }
    
    // Get string resources with formatting
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }
    
    // Get emotion labels
    fun getEmotionLabels(): List<String> = listOf(
        context.getString(R.string.emotion_angry),
        context.getString(R.string.emotion_disgust),
        context.getString(R.string.emotion_fear),
        context.getString(R.string.emotion_happy),
        context.getString(R.string.emotion_neutral),
        context.getString(R.string.emotion_sad),
        context.getString(R.string.emotion_surprise)
    )
    
    // Get emotion colors (as color resource IDs)
    fun getEmotionColors(): List<Int> = listOf(
        R.color.emotion_angry,
        R.color.emotion_disgust,
        R.color.emotion_fear,
        R.color.emotion_happy,
        R.color.emotion_neutral,
        R.color.emotion_sad,
        R.color.emotion_surprise
    )
    
    // Get default model download URL (could be overridden by remote config)
    fun getDefaultModelDownloadUrl(): String {
        return "${Api.BASE_URL}models/latest"
    }
    
    // Check if analytics are enabled (could be controlled by user settings)
    fun isAnalyticsEnabled(): Boolean {
        // In a real app, this could check user preferences
        return true
    }
    
    // Check if crash reporting is enabled
    fun isCrashReportingEnabled(): Boolean {
        // In a real app, this could check user preferences
        return true
    }
}
