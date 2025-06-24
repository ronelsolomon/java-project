package com.example.emotiondetector.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.emotiondetector.di.AppScope
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Utility class for network-related operations
 */
@AppScope
class NetworkUtils @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "NetworkUtils"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
    
    /**
     * Check if the device has an active internet connection
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Make a GET request
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): Result<Response> = makeRequest(
        url = url,
        method = "GET",
        headers = headers,
        requestBody = null,
        timeoutSeconds = timeoutSeconds
    )
    
    /**
     * Make a POST request with JSON body
     */
    suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): Result<Response> = makeRequest(
        url = url,
        method = "POST",
        headers = headers + ("Content-Type" to "application/json"),
        requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE),
        timeoutSeconds = timeoutSeconds
    )
    
    /**
     * Make a POST request with form data
     */
    suspend fun postForm(
        url: String,
        formData: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): Result<Response> {
        val formBody = FormBody.Builder().apply {
            formData.forEach { (key, value) ->
                add(key, value)
            }
        }.build()
        
        return makeRequest(
            url = url,
            method = "POST",
            headers = headers,
            requestBody = formBody,
            timeoutSeconds = timeoutSeconds
        )
    }
    
    /**
     * Upload a file with additional form data
     */
    suspend fun uploadFile(
        url: String,
        file: java.io.File,
        fileFieldName: String = "file",
        fileMimeType: String = "application/octet-stream",
        formData: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 120L // Longer timeout for file uploads
    ): Result<Response> {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                // Add file
                addFormDataPart(
                    fileFieldName,
                    file.name,
                    file.asRequestBody(fileMimeType.toMediaTypeOrNull())
                )
                
                // Add additional form data
                formData.forEach { (key, value) ->
                    addFormDataPart(key, value)
                }
            }
            .build()
        
        return makeRequest(
            url = url,
            method = "POST",
            headers = headers,
            requestBody = requestBody,
            timeoutSeconds = timeoutSeconds
        )
    }
    
    /**
     * Make a generic HTTP request
     */
    private suspend fun makeRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        requestBody: RequestBody?,
        timeoutSeconds: Long
    ): Result<Response> = try {
        if (!isNetworkAvailable()) {
            return Result.failure(IOException("No network connection available"))
        }
        
        val request = Request.Builder()
            .url(url)
            .apply {
                when (method.uppercase()) {
                    "GET" -> get()
                    "POST" -> post(requestBody ?: FormBody.Builder().build())
                    "PUT" -> put(requestBody ?: FormBody.Builder().build())
                    "DELETE" -> delete(requestBody)
                    else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
                }
                
                // Add headers
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }
            .build()
        
        // Create a new client with the specified timeout
        val client = okHttpClient.newBuilder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        
        // Execute the request
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                return Result.failure(IOException("Unexpected response code: ${response.code}, $errorBody"))
            }
            return Result.success(response)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    /**
     * Parse a JSON response body to a JSONObject
     */
    fun parseJsonResponse(response: Response): Result<JSONObject> = try {
        val responseBody = response.body?.string()
            ?: return Result.failure(IOException("Empty response body"))
        
        Result.success(JSONObject(responseBody))
    } catch (e: Exception) {
        Result.failure(IOException("Failed to parse JSON response: ${e.message}", e))
    }
    
    /**
     * Parse a JSON response body to a String
     */
    fun parseStringResponse(response: Response): Result<String> = try {
        val responseBody = response.body?.string()
            ?: return Result.failure(IOException("Empty response body"))
        
        Result.success(responseBody)
    } catch (e: Exception) {
        Result.failure(IOException("Failed to read response: ${e.message}", e))
    }
    
    /**
     * Check if the response indicates success (status code 2xx)
     */
    fun isResponseSuccessful(response: Response): Boolean {
        return response.code in 200..299
    }
    
    /**
     * Add authentication headers to a request
     */
    fun addAuthHeaders(
        headers: MutableMap<String, String>,
        token: String? = null,
        apiKey: String? = null
    ) {
        token?.let { headers["Authorization"] = "Bearer $it" }
        apiKey?.let { headers["X-API-Key"] = it }
    }
}
