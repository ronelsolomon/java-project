package com.example.emotiondetector

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.emotiondetector.di.AppModule
import com.example.emotiondetector.di.WorkerModule
import com.example.emotiondetector.util.AppLogger
import com.example.emotiondetector.util.FileUtils
import com.example.emotiondetector.util.Prefs
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main application class that initializes Hilt and application-wide components
 */
@HiltAndroidApp
class EmotionDetectorApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appLogger: AppLogger

    @Inject
    lateinit var prefs: Prefs

    @Inject
    lateinit var fileUtils: FileUtils

    override fun onCreate() {
        super.onCreate()
        
        // Initialize application components
        initializeApp()
    }

    private fun initializeApp() {
        // Log app start
        appLogger.i("Application", "Emotion Detector app starting...")
        
        // Initialize directories
        fileUtils.initializeAppDirs()
        
        // Log initialization complete
        appLogger.i("Application", "Application initialization complete")
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }

    companion object {
        /**
         * Get the application instance
         */
        fun from(context: Context): EmotionDetectorApp {
            return context.applicationContext as EmotionDetectorApp
        }
    }
}

// Add this to your AndroidManifest.xml:
// <application
//     android:name=".EmotionDetectorApp"
//     ...
// >
