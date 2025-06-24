package com.example.emotiondetector.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.emotiondetector.data.ModelManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

/**
 * Worker that handles downloading and updating ML models
 */
@HiltWorker
class DownloadModelWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val modelManager: ModelManager,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    @AssistedInject.Factory
    interface Factory : ChildWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): DownloadModelWorker
    }

    companion object {
        const val KEY_MODEL_URL = "model_url"
        const val KEY_MODEL_VERSION = "model_version"
        const val KEY_PROGRESS = "progress"
        
        private const val TAG = "DownloadModelWorker"
        private const val BUFFER_SIZE = 8 * 1024 // 8KB buffer
        private const val PROGRESS_UPDATE_INTERVAL = 100L // Update progress every 100ms
    }
    
    private var isCancelled = false
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: return@withContext Result.failure()
        val modelVersion = inputData.getInt(KEY_MODEL_VERSION, -1)
        
        if (modelVersion == -1) {
            Log.e(TAG, "Invalid model version")
            return@withContext Result.failure()
        }
        
        val tempFile = File.createTempFile("temp_model", ".tflite", context.cacheDir)
        
        try {
            val request = Request.Builder()
                .url(modelUrl)
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("Unexpected response code: ${response.code}")
            }
            
            val contentLength = response.body?.contentLength() ?: -1L
            var downloadedBytes = 0L
            var lastUpdateTime = System.currentTimeMillis()
            
            response.body?.use { body ->
                body.source().use { source ->
                    tempFile.sink().buffer().use { sink ->
                        while (true) {
                            if (isStopped || isCancelled) {
                                Log.d(TAG, "Download cancelled")
                                return@withContext Result.failure()
                            }
                            
                            val read = source.read(sink.buffer, BUFFER_SIZE.toLong())
                            if (read == -1L) break
                            
                            downloadedBytes += read
                            sink.emit()
                            
                            // Update progress at reasonable intervals
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime > PROGRESS_UPDATE_INTERVAL) {
                                val progress = if (contentLength > 0) {
                                    (downloadedBytes * 100 / contentLength).toFloat() / 100f
                                } else 0f
                                
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                                lastUpdateTime = currentTime
                            }
                        }
                    }
                }
            }
            
            // Verify the downloaded file
            if (tempFile.length() == 0L) {
                throw IOException("Downloaded file is empty")
            }
            
            // Replace the old model with the new one
            val modelFile = modelManager.modelFile
            if (tempFile.renameTo(modelFile)) {
                modelManager.updateModelVersion(modelVersion)
                Log.d(TAG, "Model updated to version $modelVersion")
                Result.success()
            } else {
                throw IOException("Failed to save the downloaded model")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            Result.failure()
        } finally {
            // Clean up temp file if it still exists
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
    
    override fun onStopped() {
        super.onStopped()
        isCancelled = true
    }
}
