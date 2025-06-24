package com.example.emotiondetector.util

import android.content.Context
import android.util.Log
import com.example.emotiondetector.di.AppScope
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logging utility class for the application
 */
@Singleton
class AppLogger @Inject constructor(
    private val context: Context,
    private val fileUtils: FileUtils
) {
    companion object {
        private const val TAG = "EmotionDetector"
        private const val MAX_LOG_FILE_SIZE = 2 * 1024 * 1024 // 2MB
        private const val MAX_LOG_FILES = 5
        private const val LOG_FILE_PREFIX = "app_log_"
        private const val LOG_FILE_EXT = ".txt"
        
        // Log levels
        const val VERBOSE = 0
        const val DEBUG = 1
        const val INFO = 2
        const val WARN = 3
        const val ERROR = 4
        const val ASSERT = 5
    }
    
    private val logDir: File by lazy {
        fileUtils.createAppDir(FileUtils.DIR_LOGS)
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logExecutor = Executors.newSingleThreadExecutor()
    
    private var logLevel = if (BuildConfig.DEBUG) VERBOSE else INFO
    private var logToFile = !BuildConfig.DEBUG // Log to file in release by default
    
    /**
     * Set the minimum log level
     */
    fun setLogLevel(level: Int) {
        logLevel = level.coerceIn(VERBOSE, ASSERT)
    }
    
    /**
     * Enable or disable file logging
     */
    fun setFileLogging(enabled: Boolean) {
        logToFile = enabled
    }
    
    /**
     * Log a verbose message
     */
    fun v(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(VERBOSE, tag, message, throwable)
    }
    
    /**
     * Log a debug message
     */
    fun d(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(DEBUG, tag, message, throwable)
    }
    
    /**
     * Log an info message
     */
    fun i(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(INFO, tag, message, throwable)
    }
    
    /**
     * Log a warning message
     */
    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(WARN, tag, message, throwable)
    }
    
    /**
     * Log an error message
     */
    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(ERROR, tag, message, throwable)
    }
    
    /**
     * Log a WTF (What a Terrible Failure) message
     */
    fun wtf(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(ASSERT, tag, message, throwable)
    }
    
    /**
     * Get all log files
     */
    fun getLogFiles(): List<File> {
        return logDir.listFiles { _, name ->
            name.startsWith(LOG_FILE_PREFIX) && name.endsWith(LOG_FILE_EXT)
        }?.sortedBy { it.name } ?: emptyList()
    }
    
    /**
     * Clear all log files
     */
    fun clearLogs() {
        logExecutor.execute {
            getLogFiles().forEach { it.delete() }
        }
    }
    
    /**
     * Get the current log file
     */
    fun getCurrentLogFile(): File? {
        val files = getLogFiles()
        return files.lastOrNull()
    }
    
    private fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
        if (level < logLevel) return
        
        // Log to Android logcat
        when (level) {
            VERBOSE -> Log.v(tag, message, throwable)
            DEBUG -> Log.d(tag, message, throwable)
            INFO -> Log.i(tag, message, throwable)
            WARN -> Log.w(tag, message, throwable)
            ERROR -> Log.e(tag, message, throwable)
            ASSERT -> Log.wtf(tag, message, throwable)
        }
        
        // Log to file if enabled
        if (logToFile) {
            logToFile(level, tag, message, throwable)
        }
    }
    
    private fun logToFile(level: Int, tag: String, message: String, throwable: Throwable?) {
        logExecutor.execute {
            try {
                val logFile = getOrCreateLogFile()
                val timestamp = dateFormat.format(Date())
                val levelStr = when (level) {
                    VERBOSE -> "V"
                    DEBUG -> "D"
                    INFO -> "I"
                    WARN -> "W"
                    ERROR -> "E"
                    ASSERT -> "A"
                    else -> "?"
                }
                
                val logMessage = StringBuilder()
                    .append("$timestamp $levelStr/$tag: $message")
                    .apply {
                        if (throwable != null) {
                            append("\n")
                            append(Log.getStackTraceString(throwable))
                        }
                    }
                    .append("\n")
                
                // Write to file
                FileOutputStream(logFile, true).bufferedWriter().use { writer ->
                    writer.append(logMessage.toString())
                }
                
                // Rotate logs if needed
                rotateLogsIfNeeded()
            } catch (e: Exception) {
                // If logging fails, there's not much we can do
                Log.e(TAG, "Failed to write to log file", e)
            }
        }
    }
    
    private fun getOrCreateLogFile(): File {
        val currentFile = getCurrentLogFile()
        
        // If no file exists or current file is too large, create a new one
        return if (currentFile == null || currentFile.length() >= MAX_LOG_FILE_SIZE) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            File(logDir, "${LOG_FILE_PREFIX}${timestamp}${LOG_FILE_EXT}")
        } else {
            currentFile
        }
    }
    
    private fun rotateLogsIfNeeded() {
        val logFiles = getLogFiles()
        
        // If we have too many log files, delete the oldest ones
        if (logFiles.size > MAX_LOG_FILES) {
            val filesToDelete = logFiles.take(logFiles.size - MAX_LOG_FILES)
            filesToDelete.forEach { it.delete() }
        }
    }
    
    /**
     * Get the logs as a string
     */
    fun getLogsAsString(): String {
        return try {
            val logFiles = getLogFiles()
            val stringBuilder = StringBuilder()
            
            logFiles.forEach { file ->
                if (file.exists()) {
                    file.bufferedReader().use { reader ->
                        stringBuilder.append(reader.readText())
                    }
                }
            }
            
            stringBuilder.toString()
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }
    
    /**
     * Gracefully shutdown the logger
     */
    fun shutdown() {
        logExecutor.shutdown()
        try {
            if (!logExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                logExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            logExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
