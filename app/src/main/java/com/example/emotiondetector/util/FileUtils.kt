package com.example.emotiondetector.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.example.emotiondetector.di.AppScope
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Utility class for file operations
 */
@AppScope
class FileUtils @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "FileUtils"
        private const val BUFFER_SIZE = 8192 // 8KB buffer size
        
        // Directory names
        const val DIR_MODELS = "models"
        const val DIR_CACHE = "cache"
        const val DIR_LOGS = "logs"
        
        // File extensions
        const val EXT_TFLITE = ".tflite"
        const val EXT_TXT = ".txt"
        const val EXT_JSON = ".json"
        
        // MIME types
        const val MIME_TFLITE = "application/octet-stream"
        const val MIME_JSON = "application/json"
        const val MIME_TEXT = "text/plain"
    }
    
    /**
     * Get the app's private files directory
     */
    fun getAppFilesDir(): File {
        return context.filesDir
    }
    
    /**
     * Get the app's cache directory
     */
    fun getAppCacheDir(): File {
        return context.cacheDir
    }
    
    /**
     * Get the app's external files directory
     */
    fun getExternalFilesDir(dirName: String? = null): File? {
        return context.getExternalFilesDir(dirName)
    }
    
    /**
     * Get a file in the app's private files directory
     */
    fun getFileInAppDir(fileName: String): File {
        return File(context.filesDir, fileName)
    }
    
    /**
     * Get a file in the app's cache directory
     */
    fun getFileInCacheDir(fileName: String): File {
        return File(context.cacheDir, fileName)
    }
    
    /**
     * Create a directory in the app's private files directory
     */
    fun createAppDir(dirName: String): File {
        val dir = File(context.filesDir, dirName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Create a directory in the app's cache directory
     */
    fun createCacheDir(dirName: String): File {
        val dir = File(context.cacheDir, dirName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Create a temporary file with the given prefix and extension
     */
    @Throws(IOException::class)
    fun createTempFile(prefix: String, extension: String): File {
        return File.createTempFile(
            prefix,
            if (extension.startsWith(".")) extension else ".$extension",
            context.cacheDir
        )
    }
    
    /**
     * Create a timestamped file name with the given prefix and extension
     */
    fun createTimestampedFileName(prefix: String, extension: String): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        return "${prefix}_${timeStamp}${if (extension.startsWith(".")) extension else ".$extension"}"
    }
    
    /**
     * Copy a file from source to destination
     */
    @Throws(IOException::class)
    fun copyFile(source: File, destination: File) {
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
    }
    
    /**
     * Copy a file from an input stream to a destination file
     */
    @Throws(IOException::class)
    fun copyFile(inputStream: InputStream, destination: File) {
        inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
    }
    
    /**
     * Read a text file as a string
     */
    @Throws(IOException::class)
    fun readTextFile(file: File): String {
        return file.readText()
    }
    
    /**
     * Write text to a file
     */
    @Throws(IOException::class)
    fun writeTextFile(file: File, text: String) {
        file.writeText(text)
    }
    
    /**
     * Delete a file or directory recursively
     */
    fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                deleteRecursively(it)
            }
        }
        return file.delete()
    }
    
    /**
     * Get the MIME type of a file
     */
    fun getMimeType(file: File): String {
        return getMimeType(file.absolutePath)
    }
    
    /**
     * Get the MIME type of a file by its path
     */
    fun getMimeType(path: String): String {
        val extension = path.substringAfterLast('.').lowercase(Locale.getDefault())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
    
    /**
     * Get the extension of a file
     */
    fun getFileExtension(file: File): String {
        return getFileExtension(file.name)
    }
    
    /**
     * Get the extension of a file by its name
     */
    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }
    
    /**
     * Get the file name without extension
     */
    fun getFileNameWithoutExtension(file: File): String {
        return getFileNameWithoutExtension(file.name)
    }
    
    /**
     * Get the file name without extension from a file name
     */
    fun getFileNameWithoutExtension(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }
    
    /**
     * Check if external storage is available for read and write
     */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
    
    /**
     * Check if external storage is available to at least read
     */
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in 
            setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }
    
    /**
     * Get the file extension from a URI
     */
    fun getFileExtension(uri: Uri): String? {
        return context.contentResolver.getType(uri)?.let { mimeType ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        }
    }
    
    /**
     * Get a file's size in a human-readable format
     */
    fun getReadableFileSize(size: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var sizeInUnits = size.toDouble()
        var unitIndex = 0
        
        while (sizeInUnits >= 1024 && unitIndex < units.size - 1) {
            sizeInUnits /= 1024
            unitIndex++
        }
        
        return "%.2f %s".format(sizeInUnits, units[unitIndex])
    }
}
