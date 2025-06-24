package com.example.emotiondetector.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.ListenableWorker
import androidx.worker.WorkerFactory
import androidx.work.WorkerParameters
import com.example.emotiondetector.worker.DownloadModelWorker
import com.example.emotiondetector.worker.ModelUpdateWorker
import com.example.emotiondetector.worker.UploadTelemetryWorker
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt WorkerFactory to handle dependency injection in workers
 */
class HiltWorkerFactory @Inject constructor(
    private val workerFactories: Map<Class<out ListenableWorker>, @JvmSuppressWildcards Provider<ChildWorkerFactory>>
) : WorkerFactory() {
    
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val foundEntry = workerFactories.entries.find { 
            Class.forName(workerClassName).isAssignableFrom(it.key)
        }
        
        val factoryProvider = foundEntry?.value
            ?: throw IllegalArgumentException("Unknown worker class name: $workerClassName")
        
        return factoryProvider.get().create(appContext, workerParameters)
    }
}

/**
 * Interface for creating workers with dependencies
 */
interface ChildWorkerFactory {
    fun create(appContext: Context, params: WorkerParameters): ListenableWorker
}

/**
 * Dagger key for worker factories
 */
@MapKey
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class WorkerKey(val value: KClass<out ListenableWorker>)

/**
 * Module for worker dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
interface WorkerModule {
    
    @Binds
    @Singleton
    fun bindWorkerFactory(factory: HiltWorkerFactory): WorkerFactory
    
    @Binds
    @IntoMap
    @WorkerKey(DownloadModelWorker::class)
    fun bindDownloadModelWorker(factory: DownloadModelWorker.Factory): ChildWorkerFactory
    
    @Binds
    @IntoMap
    @WorkerKey(ModelUpdateWorker::class)
    fun bindModelUpdateWorker(factory: ModelUpdateWorker.Factory): ChildWorkerFactory
    
    @Binds
    @IntoMap
    @WorkerKey(UploadTelemetryWorker::class)
    fun bindUploadTelemetryWorker(factory: UploadTelemetryWorker.Factory): ChildWorkerFactory
}

/**
 * Factory for creating DownloadModelWorker instances with dependencies
 */
class DownloadModelWorkerFactory @Inject constructor(
    private val worker: Provider<DownloadModelWorker>
) : ChildWorkerFactory {
    
    override fun create(appContext: Context, params: WorkerParameters): ListenableWorker {
        return worker.get()
    }
    
    @AssistedFactory
    interface Factory : ChildWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): DownloadModelWorker
    }
}

/**
 * Factory for creating ModelUpdateWorker instances with dependencies
 */
class ModelUpdateWorkerFactory @Inject constructor(
    private val worker: Provider<ModelUpdateWorker>
) : ChildWorkerFactory {
    
    override fun create(appContext: Context, params: WorkerParameters): ListenableWorker {
        return worker.get()
    }
    
    @AssistedFactory
    interface Factory : ChildWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): ModelUpdateWorker
    }
}

/**
 * Factory for creating UploadTelemetryWorker instances with dependencies
 */
class UploadTelemetryWorkerFactory @Inject constructor(
    private val worker: Provider<UploadTelemetryWorker>
) : ChildWorkerFactory {
    
    override fun create(appContext: Context, params: WorkerParameters): ListenableWorker {
        return worker.get()
    }
    
    @AssistedFactory
    interface Factory : ChildWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): UploadTelemetryWorker
    }
}
