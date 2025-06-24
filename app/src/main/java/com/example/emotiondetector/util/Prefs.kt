package com.example.emotiondetector.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.emotiondetector.di.AppScope
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Type-safe shared preferences wrapper
 */
@AppScope
class Prefs @Inject constructor(
    private val context: Context,
    private val prefsName: String = "${context.packageName}_prefs"
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    // Primitive types
    var int: Int by PrefDelegate(0)
    var long: Long by PrefDelegate(0L)
    var float: Float by PrefDelegate(0f)
    var boolean: Boolean by PrefDelegate(false)
    var string: String by PrefDelegate("")
    
    // Nullable types
    var nullableInt: Int? by NullablePrefDelegate()
    var nullableLong: Long? by NullablePrefDelegate()
    var nullableFloat: Float? by NullablePrefDelegate()
    var nullableBoolean: Boolean? by NullablePrefDelegate()
    var nullableString: String? by NullablePrefDelegate()
    
    // Object types (serialized to JSON)
    inline fun <reified T : Any> json(defaultValue: T? = null) =
        JsonPrefDelegate(defaultValue, defaultValue != null)
    
    // Set of strings
    var stringSet: Set<String> by StringSetPrefDelegate()
    
    // Enum support
    inline fun <reified T : Enum<T>> enum(defaultValue: T) =
        EnumPrefDelegate(defaultValue)
    
    // Clear all preferences
    fun clear() = prefs.edit().clear().apply()
    
    // Remove a specific key
    fun remove(key: String) = prefs.edit().remove(key).apply()
    
    // Check if a key exists
    fun contains(key: String) = prefs.contains(key)
    
    // Get all keys
    val all: Map<String, *> get() = prefs.all
    
    // Register/Unregister preference change listener
    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
    
    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
    
    // Base delegate for non-nullable types
    inner class PrefDelegate<T>(
        private val defaultValue: T
    ) : ReadWriteProperty<Any, T> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            return when (defaultValue) {
                is Int -> prefs.getInt(property.name, defaultValue) as T
                is Long -> prefs.getLong(property.name, defaultValue) as T
                is Float -> prefs.getFloat(property.name, defaultValue) as T
                is Boolean -> prefs.getBoolean(property.name, defaultValue) as T
                is String -> prefs.getString(property.name, defaultValue) as T
                else -> throw IllegalArgumentException("Unsupported type")
            }
        }
        
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            prefs.edit {
                when (value) {
                    is Int -> putInt(property.name, value)
                    is Long -> putLong(property.name, value)
                    is Float -> putFloat(property.name, value)
                    is Boolean -> putBoolean(property.name, value)
                    is String -> putString(property.name, value)
                    else -> throw IllegalArgumentException("Unsupported type")
                }
            }
        }
    }
    
    // Delegate for nullable types
    inner class NullablePrefDelegate<T> : ReadWriteProperty<Any, T?> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any, property: KProperty<*>): T? {
            return when (property.returnType.classifier) {
                Int::class -> if (prefs.contains(property.name)) prefs.getInt(property.name, 0) as T else null
                Long::class -> if (prefs.contains(property.name)) prefs.getLong(property.name, 0L) as T else null
                Float::class -> if (prefs.contains(property.name)) prefs.getFloat(property.name, 0f) as T else null
                Boolean::class -> if (prefs.contains(property.name)) prefs.getBoolean(property.name, false) as T else null
                String::class -> prefs.getString(property.name, null) as T?
                else -> throw IllegalArgumentException("Unsupported type")
            }
        }
        
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
            prefs.edit {
                when (value) {
                    is Int -> putInt(property.name, value)
                    is Long -> putLong(property.name, value)
                    is Float -> putFloat(property.name, value)
                    is Boolean -> putBoolean(property.name, value)
                    is String -> putString(property.name, value)
                    null -> remove(property.name)
                    else -> throw IllegalArgumentException("Unsupported type")
                }
            }
        }
    }
    
    // Delegate for JSON serialization
    inner class JsonPrefDelegate<T : Any>(
        private val defaultValue: T? = null,
        private val hasDefault: Boolean = false
    ) : ReadWriteProperty<Any, T?> {
        private val gson by lazy { JsonUtils.gson }
        
        override fun getValue(thisRef: Any, property: KProperty<*>): T? {
            val json = prefs.getString(property.name, null) ?: return defaultValue
            return try {
                gson.fromJson(json, object : com.google.gson.reflect.TypeToken<T>() {}.type)
            } catch (e: Exception) {
                if (hasDefault) defaultValue else null
            }
        }
        
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
            prefs.edit {
                if (value == null) {
                    remove(property.name)
                } else {
                    putString(property.name, gson.toJson(value))
                }
            }
        }
    }
    
    // Delegate for string sets
    inner class StringSetPrefDelegate : ReadWriteProperty<Any, Set<String>> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Set<String> {
            return prefs.getStringSet(property.name, emptySet()) ?: emptySet()
        }
        
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Set<String>) {
            prefs.edit {
                putStringSet(property.name, value.toSet()) // Create a new set to avoid caching issues
            }
        }
    }
    
    // Delegate for enums
    inner class EnumPrefDelegate<T : Enum<T>>(
        private val defaultValue: T
    ) : ReadWriteProperty<Any, T> {
        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            val value = prefs.getString(property.name, null)
            return value?.let {
                try {
                    enumValueOf<T>(it)
                } catch (e: Exception) {
                    defaultValue
                }
            } ?: defaultValue
        }
        
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            prefs.edit {
                putString(property.name, value.name)
            }
        }
    }
    
    companion object {
        // Extension function for easier access
        fun Context.prefs(name: String = "${this.packageName}_prefs") = Prefs(this, name)
    }
}

// Extension functions for easier preference access
inline fun <reified T : Any> Prefs.json(
    key: String,
    defaultValue: T? = null
): ReadWriteProperty<Any, T?> = JsonPrefDelegate(defaultValue, defaultValue != null).apply { 
    // We need to store the key somewhere, but since we can't modify the property name,
    // we'll use a custom delegate that takes the key as a parameter
    object : ReadWriteProperty<Any, T?> {
        private var value: T? = null
        private var initialized = false
        
        override fun getValue(thisRef: Any, property: KProperty<*>): T? {
            if (!initialized) {
                value = this@json.getValue(thisRef, property)
                initialized = true
            }
            return value
        }
        
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
            this.value = value
            this@json.setValue(thisRef, property, value)
        }
    }
}

// Extension for enum preferences
inline fun <reified T : Enum<T>> Prefs.enum(
    key: String,
    defaultValue: T
): ReadWriteProperty<Any, T> = object : ReadWriteProperty<Any, T> {
    private var value: T = defaultValue
    private var initialized = false
    
    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (!initialized) {
            val name = prefs.getString(key, null)
            value = name?.let { 
                try { 
                    enumValueOf<T>(it) 
                } catch (e: Exception) { 
                    defaultValue 
                } 
            } ?: defaultValue
            initialized = true
        }
        return value
    }
    
    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        this.value = value
        prefs.edit { putString(key, value.name) }
    }
}
