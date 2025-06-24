package com.example.emotiondetector.di

import android.content.Context
import androidx.work.WorkManager
import com.example.emotiondetector.data.ModelManager
import com.example.emotiondetector.data.ModelPreferences
import com.example.emotiondetector.data.SecureStorage
import com.example.emotiondetector.data.TelemetryManager
import com.example.emotiondetector.domain.EmotionDetector
import com.example.emotiondetector.security.KeyManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideEmotionDetector(
        @ApplicationContext context: Context,
        modelManager: ModelManager
    ): EmotionDetector {
        return EmotionDetector(
            context = context,
            modelPath = modelManager.modelFile.absolutePath,
            threshold = 0.7f
        )
    }
    
    @Provides
    @Singleton
    fun provideModelManager(
        @ApplicationContext context: Context,
        workManager: WorkManager,
        modelPreferences: ModelPreferences
    ): ModelManager {
        return ModelManager(context, workManager, modelPreferences).apply {
            initialize()
        }
    }
    
    @Provides
    @Singleton
    fun provideModelPreferences(
        @ApplicationContext context: Context
    ): ModelPreferences {
        return ModelPreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideKeyManager(
        @ApplicationContext context: Context
    ): KeyManager {
        return KeyManager(context).apply {
            migrateFromLegacyIfNeeded()
        }
    }
    
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context,
        keyManager: KeyManager
    ): SecureStorage {
        // In a real app, you'd derive a key from the KeyManager
        // For simplicity, we're using a fixed key here
        val encryptionKey = SQLiteDatabase.getBytes("your-secure-encryption-key".toCharArray())
        return SecureStorage(context, encryptionKey)
    }
    
    @Provides
    @Singleton
    fun provideTelemetryManager(
        @ApplicationContext context: Context,
        workManager: WorkManager,
        secureStorage: SecureStorage
    ): TelemetryManager {
        return TelemetryManager(context, workManager, secureStorage)
    }
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setPrettyPrinting()
            .create()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }
    
    // Add other dependencies as needed
}
