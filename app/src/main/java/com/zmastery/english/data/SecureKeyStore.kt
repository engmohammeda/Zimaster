package com.zmastery.english.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * تشفير المفاتيح باستخدام Android Keystore — الطبقة الأقوى لحماية
 * مفاتيح API على الجهاز.
 *
 * كيف يعمل:
 *  1. عند أول استخدام، يُنشئ مفتاح AES-256 داخل Android Keystore (المحمي بالأجهزة)
 *  2. المفتاح لا يغادر Keystore أبداً — التشفير/فك التشفير يتم داخل الأجهزة
 *  3. النص المشفر يُحفظ في SharedPreferences مع IV (Initialization Vector)
 *  4. حتى لو سرق شخص ملف SharedPreferences، لا يستطيع فك التشفير
 *
 * ملاحظة: هذا يعمل على API 23+. بما أن minSdk = 24، فهو متاح دائماً.
 */
object SecureKeyStore {

    private const val TAG = "SecureKeyStore"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "zmastery_api_key_encryption"
    private const val PREFS_NAME = "zmastery_secure_keys"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    /**
     * تشفير مفتاح API وتخزينه.
     *
     * @param context Android context
     * @param keyId معرّف المفتاح (مثل "k1234567890")
     * @param plaintextKey المفتاح النصي المراد تشفيره
     * @return true إذا نجح التشفير
     */
    fun encryptAndStore(context: Context, keyId: String, plaintextKey: String): Boolean {
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintextKey.toByteArray(Charsets.UTF_8))

            // Store encrypted key + IV in SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("${keyId}_encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("${keyId}_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()

            Log.d(TAG, "Key encrypted and stored for: $keyId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed for $keyId: ${e.message}")
            false
        }
    }

    /**
     * استرداد مفتاح API المشفر وفك تشفيره.
     *
     * @param context Android context
     * @param keyId معرّف المفتاح
     * @return المفتاح النصي، أو null إذا لم يوجد أو فشل فك التشفير
     */
    fun retrieveAndDecrypt(context: Context, keyId: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encryptedBase64 = prefs.getString("${keyId}_encrypted", null) ?: return null
            val ivBase64 = prefs.getString("${keyId}_iv", null) ?: return null

            val encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed for $keyId: ${e.message}")
            null
        }
    }

    /**
     * حذف مفتاح مشفر من التخزين.
     */
    fun deleteKey(context: Context, keyId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${keyId}_encrypted")
            .remove("${keyId}_iv")
            .apply()
        Log.d(TAG, "Encrypted key deleted for: $keyId")
    }

    /**
     * التحقق من وجود مفتاح مشفر.
     */
    fun hasEncryptedKey(context: Context, keyId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains("${keyId}_encrypted")
    }

    /**
     * حذف كل المفاتيح المشفرة (عند إعادة تعيين التطبيق).
     */
    fun deleteAllKeys(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Also delete the encryption key from Android Keystore
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Keystore key: ${e.message}")
        }
        Log.d(TAG, "All encrypted keys deleted")
    }

    // ─── Internal: AES key management ───

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Return existing key if it exists
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        // Create a new AES-256 key
        return createKey()
    }

    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)  // Unique IV per encryption
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
