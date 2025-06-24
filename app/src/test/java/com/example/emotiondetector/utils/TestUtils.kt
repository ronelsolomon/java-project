package com.example.emotiondetector.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.emotiondetector.util.AppLogger
import com.example.emotiondetector.util.FileUtils
import com.example.emotiondetector.util.Prefs
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.File
import java.util.concurrent.Executors

/**
 * Test utilities for common test functionality
 */
object TestUtils {
    
    /**
     * Create a test application context
     */
    fun getTestContext(): Context {
        return ApplicationProvider.getApplicationContext()
    }
    
    /**
     * Create a test file in the test app's cache directory
     */
    fun createTestFile(fileName: String, content: String = "test content"): File {
        val context = getTestContext()
        val file = File(context.cacheDir, fileName)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }
    
    /**
     * Create a mocked Prefs instance with common configurations
     */
    fun createMockPrefs(): Prefs {
        val prefs = Mockito.mock(Prefs::class.java)
        `when`(prefs.json<Any>(anyString(), any())).thenAnswer { it.arguments[1] as Any }
        return prefs
    }
    
    /**
     * Create a mocked AppLogger instance
     */
    fun createMockLogger(): AppLogger {
        return Mockito.mock(AppLogger::class.java)
    }
    
    /**
     * Create a test FileUtils instance
     */
    fun createTestFileUtils(): FileUtils {
        val context = getTestContext()
        return FileUtils(context).apply {
            // Override any methods if needed for testing
        }
    }
    
    /**
     * Create a test executor for coroutine testing
     */
    fun createTestExecutor() = Executors.newSingleThreadExecutor()
    
    /**
     * Get a test asset file path
     */
    fun getTestAssetPath(assetName: String): String {
        return "src/test/assets/$assetName"
    }
    
    /**
     * Sleep for a short duration to allow coroutines to complete
     */
    suspend fun delayForCoroutines() {
        kotlinx.coroutines.delay(100)
    }
    
    /**
     * Helper to create mock with relaxed settings
     */
    inline fun <reified T> relaxedMock(): T = Mockito.mock(T::class.java, 
        Mockito.CALLS_REAL_METHODS ?: Mockito.RETURNS_DEFAULTS
    )
}

/**
 * Helper function to read a file from test resources
 */
fun readTestResourceFile(fileName: String): String {
    val classLoader = TestUtils::class.java.classLoader
    return classLoader?.getResourceAsStream(fileName)?.bufferedReader()?.use { it.readText() }
        ?: throw IllegalStateException("Could not read test resource: $fileName")
}

/**
 * Helper function to create a temporary file for testing
 */
fun createTempTestFile(prefix: String = "test", suffix: String = ".tmp"): File {
    return File.createTempFile(prefix, suffix, ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir).apply {
        deleteOnExit()
    }
}
