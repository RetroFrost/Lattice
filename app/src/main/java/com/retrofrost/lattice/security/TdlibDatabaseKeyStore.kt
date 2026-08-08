package com.retrofrost.lattice.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps TDLib's database encryption key out of plaintext app storage.
 *
 * A random 256-bit TDLib key is generated once, wrapped with an AES key held by
 * Android Keystore (StrongBox when available), and only unwrapped in memory
 * while TDLib is being initialised.
 */
object TdlibDatabaseKeyStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val WRAP_ALIAS = "lattice_tdlib_database_wrap_key_v1"
    private const val PREFS = "lattice_secure_local_state"
    private const val PREF_WRAPPED_KEY = "tdlib_database_key_v1"
    private const val KEY_BYTES = 32
    private const val IV_BYTES = 12

    fun getOrCreateBase64Key(context: Context): String {
        ensureWrappingKey()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = prefs.getString(PREF_WRAPPED_KEY, null)
        val rawKey = if (encoded == null) {
            ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes).also { key ->
                val wrapped = wrap(key)
                check(prefs.edit().putString(PREF_WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()) {
                    "Unable to persist the wrapped TDLib database key"
                }
            }
        } else {
            unwrap(Base64.decode(encoded, Base64.NO_WRAP))
        }
        return Base64.encodeToString(rawKey, Base64.NO_WRAP)
    }

    private fun ensureWrappingKey() {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(WRAP_ALIAS)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateWrappingKey(strongBox = true)
                return
            } catch (_: Exception) {
                // StrongBox is optional. Fall back to the normal Android Keystore.
            }
        }
        generateWrappingKey(strongBox = false)
    }

    private fun generateWrappingKey(strongBox: Boolean) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            WRAP_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        generator.init(builder.build())
        generator.generateKey()
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getKey(WRAP_ALIAS, null) as? SecretKey
            ?: error("TDLib wrapping key is unavailable")
    }

    private fun wrap(rawKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected AES-GCM IV length" }
        return iv + cipher.doFinal(rawKey)
    }

    private fun unwrap(payload: ByteArray): ByteArray {
        require(payload.size > IV_BYTES) { "Stored TDLib key payload is invalid" }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).also {
            check(it.size == KEY_BYTES) { "Stored TDLib database key has an invalid size" }
        }
    }
}
