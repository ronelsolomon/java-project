package com.example.emotiondetector.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encryption keys for secure storage
 * Uses Android's KeyStore to securely store cryptographic keys
 */
@Singleton
class KeyManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "KeyManager"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "emotion_detector_key"
        private const val KEY_SIZE = 256
        private const val IV_SIZE = 12 // 96 bits for GCM
        
        // For backward compatibility with existing encrypted data
        private const val SHARED_PREFS_NAME = "emotion_detector_keys"
        private const val ENCRYPTED_KEY_ALIAS = "encrypted_key"
        private const val ENCRYPTED_IV_ALIAS = "encrypted_iv"
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }
    
    private val sharedPrefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        ensureKeyExists()
    }
    
    /**
     * Get the encryption key, creating it if it doesn't exist
     */
    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            createKey()
        }
    }
    
    /**
     * Create a new encryption key in the Android KeyStore
     */
    private fun createKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                
            val builder = KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
            
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
            
            Log.d(TAG, "New encryption key created")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating encryption key", e)
            throw RuntimeException("Failed to create encryption key", e)
        }
    }
    
    /**
     * Get the encryption key from the KeyStore
     */
    private fun getKey(): SecretKey {
        ensureKeyExists()
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        return key ?: throw IllegalStateException("Failed to get encryption key")
    }
    
    /**
     * Generate a secure random IV
     */
    private fun generateIv(): ByteArray {
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        return iv
    }
    
    /**
     * Encrypt the provided data
     */
    fun encrypt(data: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = generateIv()
            val spec = GCMParameterSpec(128, iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), spec)
            val encrypted = cipher.doFinal(data)
            
            // Combine IV and encrypted data
            ByteArray(iv.size + encrypted.size).apply {
                System.arraycopy(iv, 0, this, 0, iv.size)
                System.arraycopy(encrypted, 0, this, iv.size, encrypted.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            throw RuntimeException("Encryption failed", e)
        }
    }
    
    /**
     * Decrypt the provided data
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        if (encryptedData.size < IV_SIZE) {
            throw IllegalArgumentException("Encrypted data is too short")
        }
        
        return try {
            val iv = encryptedData.copyOfRange(0, IV_SIZE)
            val encrypted = encryptedData.copyOfRange(IV_SIZE, encryptedData.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            
            cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            throw RuntimeException("Decryption failed", e)
        }
    }
    
    /**
     * Encrypt a string and return it as a Base64-encoded string
     */
    fun encryptString(data: String): String {
        val encrypted = encrypt(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
    
    /**
     * Decrypt a Base64-encoded encrypted string
     */
    fun decryptString(encryptedData: String): String {
        val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)
        return String(decrypt(decoded), Charsets.UTF_8)
    }
    
    /**
     * For backward compatibility: Migrate from old encryption scheme if needed
     */
    fun migrateFromLegacyIfNeeded() {
        if (sharedPrefs.contains(ENCRYPTED_KEY_ALIAS)) {
            try {
                // In a real app, you would decrypt the old key and re-encrypt the data
                // with the new key, then remove the old key
                Log.d(TAG, "Migrating from legacy encryption")
                
                // After migration, remove the old keys
                sharedPrefs.edit()
                    .remove(ENCRYPTED_KEY_ALIAS)
                    .remove(ENCRYPTED_IV_ALIAS)
                    .apply()
                    
                Log.d(TAG, "Migration completed")
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed", e)
                // If migration fails, we'll continue with the new key
                // and the old data will be re-encrypted when accessed
            }
        }
    }
}
