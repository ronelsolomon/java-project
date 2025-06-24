package com.example.emotiondetector.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.emotiondetector.data.TelemetryManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Worker that uploads telemetry data to the backend
 */
@HiltWorker
class UploadTelemetryWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val telemetryManager: com.example.emotiondetector.data.TelemetryManager,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    @AssistedInject.Factory
    interface Factory : ChildWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): UploadTelemetryWorker
    }

    companion object {
        private const val TAG = "UploadTelemetryWorker"
        private const val MAX_BATCH_SIZE = 100
        private const val UPLOAD_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        
        // In a real app, these would come from a configuration or build config
        private const val BASE_URL = "https://api.your-backend.com/telemetry"
        private const val API_KEY = "your_api_key_here"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting telemetry upload")
        
        try {
            // Get a batch of events to upload
            val events = telemetryManager.getEvents()
            
            if (events.isEmpty()) {
                Log.d(TAG, "No telemetry events to upload")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Uploading ${events.size} telemetry events")
            
            // Convert events to JSON
            val eventsJson = JSONArray().apply {
                events.take(MAX_BATCH_SIZE).forEach { event ->
                    put(JSONObject().apply {
                        put("id", event.id)
                        put("timestamp", event.timestamp)
                        put("event_type", event.eventType)
                        put("params", JSONObject(event.params))
                    })
                }
            }
            
            val requestBody = JSONObject()
                .put("events", eventsJson)
                .put("device_id", getDeviceId())
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            
            // Create the request
            val request = Request.Builder()
                .url("$BASE_URL/ingest")
                .header("Authorization", "Bearer $API_KEY")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            // Execute the request
            val response = okHttpClient.newBuilder()
                .connectTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
            
            if (!response.isSuccessful) {
                throw Exception("Upload failed: ${response.code} - ${response.message}")
            }
            
            // Parse the response
            val responseBody = response.body?.string()
            Log.d(TAG, "Telemetry upload successful: $responseBody")
            
            // Delete the uploaded events
            val uploadedIds = events.take(MAX_BATCH_SIZE).map { it.id }
            telemetryManager.deleteEvents(uploadedIds)
            
            // If there are more events to upload, chain another worker
            if (events.size > MAX_BATCH_SIZE) {
                Log.d(TAG, "More events to upload, chaining next batch")
                Result.retry()
            } else {
                Log.d(TAG, "All events uploaded successfully")
                Result.success()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading telemetry data", e)
            
            // Retry with exponential backoff
            val backoffDelay = runAttemptCount * 10_000L // 10s * attempt count
            Result.retry(workDataOf("backoff_delay" to backoffDelay))
        }
    }
    
    /**
     * Get a unique device ID for telemetry purposes
     * In a real app, you might want to use a more persistent ID
     */
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
}
