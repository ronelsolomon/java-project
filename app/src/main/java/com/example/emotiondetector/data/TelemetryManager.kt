package com.example.emotiondetector.data

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.emotiondetector.worker.UploadTelemetryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages collection and secure storage of telemetry data
 */
@Singleton
class TelemetryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val secureStorage: SecureStorage
) {
    companion object {
        private const val TAG = "TelemetryManager"
        private const val UPLOAD_WORK_NAME = "telemetry_upload"
        private const val UPLOAD_INTERVAL_HOURS = 24L // Upload every 24 hours
        
        // Event types
        const val EVENT_MODEL_LOAD = "model_load"
        const val EVENT_INFERENCE = "inference"
        const val EVENT_ERROR = "error"
        
        // Parameter keys
        const val PARAM_MODEL_VERSION = "model_version"
        const val PARAM_INFERENCE_TIME = "inference_time_ms"
        const val PARAM_EMOTION = "emotion"
        const val PARAM_CONFIDENCE = "confidence"
        const val PARAM_ERROR_MSG = "error_message"
    }
    
    /**
     * Record a telemetry event
     */
    suspend fun recordEvent(
        eventType: String,
        params: Map<String, Any> = emptyMap()
    ) {
        try {
            val event = TelemetryEvent(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                params = params
            )
            
            secureStorage.insertEvent(event)
            Log.d(TAG, "Recorded event: $eventType")
            
            // Schedule periodic upload if not already scheduled
            schedulePeriodicUpload()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recording telemetry event", e)
        }
    }
    
    /**
     * Get all events of a specific type
     */
    suspend fun getEvents(eventType: String? = null): List<TelemetryEvent> {
        return secureStorage.getEvents(eventType)
    }
    
    /**
     * Delete events older than the specified timestamp
     */
    suspend fun deleteOldEvents(olderThan: Long) {
        secureStorage.deleteEventsOlderThan(olderThan)
    }
    
    /**
     * Schedule periodic upload of telemetry data
     */
    private fun schedulePeriodicUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val uploadRequest = PeriodicWorkRequestBuilder<UploadTelemetryWorker>(
            repeatInterval = UPLOAD_INTERVAL_HOURS,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            UPLOAD_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            uploadRequest
        )
    }
    
    /**
     * Manually trigger telemetry upload
     */
    fun triggerUpload() {
        val uploadRequest = OneTimeWorkRequestBuilder<UploadTelemetryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        
        workManager.enqueue(uploadRequest)
    }
}

/**
 * Data class representing a telemetry event
 */
data class TelemetryEvent(
    val id: String,
    val timestamp: Long,
    val eventType: String,
    val params: Map<String, Any> = emptyMap()
)

/**
 * Interface for secure storage of telemetry events
 */
interface TelemetryStorage {
    suspend fun insertEvent(event: TelemetryEvent)
    suspend fun getEvents(eventType: String? = null): List<TelemetryEvent>
    suspend fun deleteEvents(ids: List<String>)
    suspend fun deleteEventsOlderThan(timestamp: Long)
}

/**
 * Implementation of TelemetryStorage using SQLCipher for encryption
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext context: Context,
    encryptionKey: ByteArray
) : TelemetryStorage {
    
    private val database: SQLiteDatabase
    
    init {
        val factory = SupportFactory(encryptionKey)
        val dbFile = context.getDatabasePath("telemetry.db")
        dbFile.parentFile?.mkdirs()
        
        database = SQLiteDatabase.openOrCreateDatabase(
            dbFile,
            factory,
            null,
            null
        )
        
        createTablesIfNeeded()
    }
    
    private fun createTablesIfNeeded() {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS events (
                id TEXT PRIMARY KEY,
                timestamp INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                params TEXT NOT NULL
            )
        """.trimIndent())
        
        // Create index on timestamp for faster queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_events_timestamp 
            ON events(timestamp)
        """.trimIndent())
        
        // Create index on event_type for faster filtering
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_events_type 
            ON events(event_type)
        """.trimIndent())
    }
    
    override suspend fun insertEvent(event: TelemetryEvent) {
        // In a real app, you'd use Room with SQLCipher
        // This is a simplified implementation
        val values = android.content.ContentValues().apply {
            put("id", event.id)
            put("timestamp", event.timestamp)
            put("event_type", event.eventType)
            // In a real app, you'd want to properly serialize the params map
            put("params", event.params.toString())
        }
        
        database.insertWithOnConflict(
            "events",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
    
    override suspend fun getEvents(eventType: String?): List<TelemetryEvent> {
        val events = mutableListOf<TelemetryEvent>()
        
        val selection = eventType?.let { "event_type = ?" } ?: "1"
        val selectionArgs = eventType?.let { arrayOf(it) } ?: emptyArray()
        
        database.query(
            "events",
            arrayOf("id", "timestamp", "event_type", "params"),
            selection,
            selectionArgs,
            null, null,
            "timestamp DESC",
            "1000" // Limit to 1000 most recent events
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getString(0)
                    val timestamp = cursor.getLong(1)
                    val type = cursor.getString(2)
                    val paramsStr = cursor.getString(3)
                    
                    // In a real app, you'd properly deserialize the params
                    val params = emptyMap<String, Any>()
                    
                    events.add(TelemetryEvent(id, timestamp, type, params))
                } catch (e: Exception) {
                    Log.e("TelemetryStorage", "Error reading event", e)
                }
            }
        }
        
        return events
    }
    
    override suspend fun deleteEvents(ids: List<String>) {
        if (ids.isEmpty()) return
        
        val placeholders = ids.joinToString(",") { "?" }
        val whereClause = "id IN ($placeholders)"
        
        database.delete(
            "events",
            whereClause,
            ids.toTypedArray()
        )
    }
    
    override suspend fun deleteEventsOlderThan(timestamp: Long) {
        database.delete(
            "events",
            "timestamp < ?",
            arrayOf(timestamp.toString())
        )
    }
}
