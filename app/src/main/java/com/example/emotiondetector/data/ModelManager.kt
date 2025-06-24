package com.example.emotiondetector.data

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.emotiondetector.worker.DownloadModelWorker
import com.example.emotiondetector.worker.ModelUpdateWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of ML models including downloading, updating, and versioning
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val modelPreferences: ModelPreferences
) {
    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_DIR = "models"
        private const val MODEL_FILENAME = "emotion_model.tflite"
        private const val MODEL_VERSION_KEY = "model_version"
        private const val MODEL_CHECK_INTERVAL_HOURS = 24L // Check for updates daily
    }
    
    private val modelDir: File by lazy {
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }
    
    val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)
    
    val modelVersion: Int
        get() = modelPreferences.getInt(MODEL_VERSION_KEY, 1)
    
    /**
     * Initialize the model manager and schedule periodic update checks
     */
    fun initialize() {
        // Ensure we have the default model if none exists
        if (!modelFile.exists()) {
            copyDefaultModel()
        }
        
        // Schedule periodic update checks
        scheduleModelUpdateCheck()
    }
    
    /**
     * Check if a new model version is available
     */
    suspend fun checkForUpdates(force: Boolean = false): Boolean {
        // In a real app, this would check with your backend
        // For now, we'll just return false
        return false
    }
    
    /**
     * Download and update to a new model version
     */
    fun downloadModel(version: Int, url: String) {
        val inputData = workDataOf(
            DownloadModelWorker.KEY_MODEL_URL to url,
            DownloadModelWorker.KEY_MODEL_VERSION to version
        )
        
        val request = OneTimeWorkRequestBuilder<DownloadModelWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()
        
        workManager.enqueueUniqueWork(
            "download_model_$version",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
    
    /**
     * Get the download progress as a Flow
     */
    fun getDownloadProgress(version: Int): Flow<Float> {
        return workManager.getWorkInfosForUniqueWorkFlow("download_model_$version")
            .map { workInfos ->
                val info = workInfos.firstOrNull()
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> 1f
                    WorkInfo.State.FAILED -> -1f
                    WorkInfo.State.RUNNING -> {
                        info.progress.getFloat(DownloadModelWorker.KEY_PROGRESS, 0f)
                    }
                    else -> 0f
                }
            }
    }
    
    /**
     * Update the current model version
     */
    fun updateModelVersion(version: Int) {
        modelPreferences.putInt(MODEL_VERSION_KEY, version)
    }
    
    /**
     * Schedule periodic checks for model updates
     */
    private fun scheduleModelUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val updateRequest = PeriodicWorkRequestBuilder<ModelUpdateWorker>(
            repeatInterval = MODEL_CHECK_INTERVAL_HOURS,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "model_update_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateRequest
        )
    }
    
    /**
     * Copy the default model from assets to the app's files directory
     */
    private fun copyDefaultModel() {
        try {
            context.assets.open(MODEL_FILENAME).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Default model copied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying default model", e)
        }
    }
}

/**
 * Simple preferences wrapper for model-related settings
 */
class ModelPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
    
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    
    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
    
    fun getString(key: String, default: String): String = 
        prefs.getString(key, default) ?: default
    
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
