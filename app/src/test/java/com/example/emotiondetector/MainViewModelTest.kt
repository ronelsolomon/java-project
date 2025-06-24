package com.example.emotiondetector

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.emotiondetector.data.AppData
import com.example.emotiondetector.ui.MainViewModel
import com.example.emotiondetector.util.AppLogger
import com.example.emotiondetector.util.FileUtils
import com.example.emotiondetector.util.Prefs
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: MainViewModel
    private lateinit var testDispatcher: TestCoroutineDispatcher
    private lateinit var testScope: TestScope
    
    // Mocks
    private lateinit var mockAppLogger: AppLogger
    private lateinit var mockPrefs: Prefs
    private lateinit var mockFileUtils: FileUtils

    @Before
    fun setup() {
        testDispatcher = TestCoroutineDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        
        // Initialize mocks
        mockAppLogger = mock(AppLogger::class.java)
        mockPrefs = mock(Prefs::class.java)
        mockFileUtils = mock(FileUtils::class.java)
        
        // Initialize Hilt
        hiltRule.inject()
        
        // Create ViewModel with test dependencies
        viewModel = MainViewModel(mockAppLogger, mockPrefs, mockFileUtils)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun `initializeApp increments app launch count`() = testScope.runTest {
        // Given
        var launchCount = 0
        whenever(mockPrefs.json<Int>(anyString(), any())).thenReturn(launchCount)
        
        // When
        viewModel.initializeApp()
        advanceUntilIdle()
        
        // Then
        verify(mockPrefs).json<Int>("app_launch_count", 0)
        verify(mockAppLogger).d("MainViewModel", "App launch count: 1")
    }

    @Test
    fun `toggleDarkMode updates preference and logs change`() {
        // Given
        val darkModeEnabled = true
        
        // When
        viewModel.toggleDarkMode(darkModeEnabled)
        
        // Then
        verify(mockPrefs).json("dark_mode", darkModeEnabled)
        verify(mockAppLogger).d("MainViewModel", "Dark mode enabled")
    }
    
    @Test
    fun `saveImage with valid uri saves image and updates state`() = testScope.runTest {
        // Given
        val testUri = mock(Uri::class.java)
        val expectedPath = "/test/path/image.jpg"
        whenever(mockFileUtils.saveImageFromUri(testUri, "emotion_captures"))
            .thenReturn(expectedPath)
        
        // When
        viewModel.saveImage(testUri)
        advanceUntilIdle()
        
        // Then
        verify(mockFileUtils).saveImageFromUri(testUri, "emotion_captures")
        verify(mockAppLogger).i("MainViewModel", anyString())
        
        val state = viewModel.uiState.value as MainViewModel.MainUiState.Success
        assertThat(state.message).isEqualTo("Image saved successfully")
    }
    
    @Test
    fun `saveImage with error updates state with error message`() = testScope.runTest {
        // Given
        val testUri = mock(Uri::class.java)
        val errorMessage = "Failed to save image"
        whenever(mockFileUtils.saveImageFromUri(testUri, "emotion_captures"))
            .thenThrow(RuntimeException(errorMessage))
        
        // When
        viewModel.saveImage(testUri)
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value as MainViewModel.MainUiState.Error
        assertThat(state.message).contains(errorMessage)
    }
    
    @Test
    fun `loadInitialData loads cached data from prefs`() {
        // Given
        val testData = AppData()
        whenever(mockPrefs.json<AppData>("cached_data")).thenReturn(testData)
        
        // When
        viewModel.loadInitialData()
        
        // Then
        verify(mockPrefs).json<AppData>("cached_data")
    }
}
