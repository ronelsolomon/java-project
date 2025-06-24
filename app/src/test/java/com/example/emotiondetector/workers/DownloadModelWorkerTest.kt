package com.example.emotiondetector.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.emotiondetector.data.ModelManager
import com.example.emotiondetector.data.ModelPreferences
import com.example.emotiondetector.di.AppModule
import com.example.emotiondetector.di.WorkerModule
import com.example.emotiondetector.util.AppLogger
import com.example.emotiondetector.util.NetworkUtils
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.MockitoAnnotations
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@UninstallModules(AppModule::class, WorkerModule::class)
@RunWith(AndroidJUnit4::class)
class DownloadModelWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Mock
    private lateinit var mockModelManager: ModelManager

    @Mock
    private lateinit var mockModelPreferences: ModelPreferences

    @Mock
    private lateinit var mockNetworkUtils: NetworkUtils

    @Mock
    private lateinit var mockAppLogger: AppLogger

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        
        // Create test worker parameters
        workerParams = TestListenableWorkerBuilder<DownloadModelWorker>(context).build().run {
            this.workerParameters
        }
        
        // Initialize Hilt
        hiltRule.inject()
    }

    @Test
    fun `doWork when download succeeds returns success`() = runTest {
        // Given
        `when`(mockNetworkUtils.isNetworkAvailable()).thenReturn(true)
        `when`(mockModelManager.downloadModel(anyString())).thenReturn(Result.success())
        
        // Create worker with test dependencies
        val worker = TestListenableWorkerBuilder<DownloadModelWorker>(context)
            .setWorkerFactory(createWorkerFactory())
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isEqualTo(Result.success())
    }
    
    @Test
    fun `doWork when network is unavailable returns retry`() = runTest {
        // Given
        `when`(mockNetworkUtils.isNetworkAvailable()).thenReturn(false)
        
        // Create worker with test dependencies
        val worker = TestListenableWorkerBuilder<DownloadModelWorker>(context)
            .setWorkerFactory(createWorkerFactory())
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isEqualTo(Result.retry())
    }
    
    @Test
    fun `doWork when download fails returns failure`() = runTest {
        // Given
        `when`(mockNetworkUtils.isNetworkAvailable()).thenReturn(true)
        `when`(mockModelManager.downloadModel(anyString()))
            .thenReturn(Result.failure())
        
        // Create worker with test dependencies
        val worker = TestListenableWorkerBuilder<DownloadModelWorker>(context)
            .setWorkerFactory(createWorkerFactory())
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertThat(result).isEqualTo(Result.failure())
    }
    
    private fun createWorkerFactory(): TestWorkerFactory {
        return TestWorkerFactory(
            mockModelManager,
            mockModelPreferences,
            mockNetworkUtils,
            mockAppLogger
        )
    }
    
    // Test worker factory for dependency injection
    class TestWorkerFactory(
        private val modelManager: ModelManager,
        private val modelPreferences: ModelPreferences,
        private val networkUtils: NetworkUtils,
        private val appLogger: AppLogger
    ) : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? {
            return DownloadModelWorker(
                appContext,
                workerParameters,
                modelManager,
                modelPreferences,
                networkUtils,
                appLogger
            )
        }
    }
}
