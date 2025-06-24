package com.example.emotiondetector.util

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for JSON serialization and deserialization using Gson
 */
@Singleton
class JsonUtils @Inject constructor() {
    /**
     * Custom Gson instance with custom type adapters and settings
     */
    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .registerTypeAdapter(Date::class.java, DateDeserializer())
        .registerTypeAdapter(Date::class.java, DateSerializer())
        .create()
    
    /**
     * Convert an object to JSON string
     */
    fun toJson(obj: Any?): String = gson.toJson(obj)
    
    /**
     * Convert a JSON string to an object of the specified type
     */
    inline fun <reified T> fromJson(json: String?): T? {
        return try {
            if (json.isNullOrBlank()) null else gson.fromJson(json, object : TypeToken<T>() {}.type)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert a JSON string to a list of objects of the specified type
     */
    inline fun <reified T> listFromJson(json: String?): List<T> {
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            if (json.isNullOrBlank()) emptyList() else gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Convert a JSON string to a map with string keys and values of the specified type
     */
    inline fun <reified T> mapFromJson(json: String?): Map<String, T> {
        return try {
            val type = object : TypeToken<Map<String, T>>() {}.type
            if (json.isNullOrBlank()) emptyMap() else gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Create a deep copy of an object by serializing and deserializing it
     */
    inline fun <reified T> deepCopy(obj: T): T? {
        return try {
            fromJson<T>(toJson(obj))
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert an object to a map
     */
    fun toMap(obj: Any?): Map<String, Any?> {
        return try {
            val json = toJson(obj)
            gson.fromJson(json, object : TypeToken<Map<String, Any?>>() {}.type)
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Convert a map to an object of the specified type
     */
    inline fun <reified T> fromMap(map: Map<*, *>?): T? {
        return try {
            val json = toJson(map)
            fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Pretty print a JSON string
     */
    fun prettyPrint(json: String?): String {
        return try {
            val jsonElement = JsonParser.parseString(json)
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            json ?: ""
        }
    }
    
    /**
     * Check if a string is valid JSON
     */
    fun isValidJson(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return try {
            JsonParser.parseString(json)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Merge two JSON strings
     */
    fun mergeJson(json1: String, json2: String): String {
        return try {
            val jsonElement1 = JsonParser.parseString(json1).asJsonObject
            val jsonElement2 = JsonParser.parseString(json2).asJsonObject
            
            for ((key, value) in jsonElement2.entrySet()) {
                jsonElement1.add(key, value)
            }
            
            gson.toJson(jsonElement1)
        } catch (e: Exception) {
            json1
        }
    }
    
    /**
     * Custom date deserializer for Gson
     */
    private class DateDeserializer : JsonDeserializer<Date> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Date {
            return try {
                val dateStr = json.asString
                // Try ISO 8601 format
                val date = try {
                    java.time.OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    // Try other formats if needed
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .parse(dateStr)?.time ?: 0L
                }
                Date(date)
            } catch (e: Exception) {
                Date(0)
            }
        }
    }
    
    /**
     * Custom date serializer for Gson
     */
    private class DateSerializer : JsonSerializer<Date> {
        override fun serialize(
            src: Date?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonPrimitive(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(src ?: Date(0))
            )
        }
    }
    
    companion object {
        /**
         * Get a Gson instance with default settings
         */
        fun getDefaultGson(): Gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()
        
        /**
         * Extension function to convert any object to JSON string
         */
        fun Any?.toJsonString(): String = JsonUtils().toJson(this)
        
        /**
         * Extension function to parse JSON string to object
         */
        inline fun <reified T> String?.fromJsonString(): T? = JsonUtils().fromJson(this)
        
        /**
         * Extension function to convert map to object
         */
        inline fun <reified T> Map<*, *>?.toObject(): T? = JsonUtils().fromMap(this ?: emptyMap<Any?, Any?>())
        
        /**
         * Extension function to convert object to map
         */
        fun Any?.toMap(): Map<String, Any?> = JsonUtils().toMap(this)
    }
}
