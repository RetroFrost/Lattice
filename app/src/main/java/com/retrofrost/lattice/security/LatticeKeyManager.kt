package com.retrofrost.lattice.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.KeyGenerator

object LatticeKeyManager {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "lattice_local_unlock_key_v1"

    enum class Backing { STRONGBOX, TEE_OR_SOFTWARE, EXISTING }

    fun ensureHardwareBoundKey(): Backing {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS)) return Backing.EXISTING

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateKey(strongBox = true)
                return Backing.STRONGBOX
            } catch (_: StrongBoxUnavailableException) {
                // Fall through to the normal Android Keystore, normally TEE-backed.
            }
        }

        generateKey(strongBox = false)
        return Backing.TEE_OR_SOFTWARE
    }

    private fun generateKey(strongBox: Boolean) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        generator.init(builder.build())
        generator.generateKey()
    }
}
