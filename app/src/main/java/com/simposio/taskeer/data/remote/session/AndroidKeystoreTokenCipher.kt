package com.simposio.taskeer.data.remote.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreTokenCipher(private val alias: String = DEFAULT_ALIAS) : TokenCipher {

    override fun encrypt(plain: ByteArray): ByteArray = guarded {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray = guarded {
        if (data.size <= IV_BYTES) throw TokenCipherException("Dato cifrado demasiado corto")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES),
        )
        cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: TokenCipherException) {
        throw e
    } catch (e: GeneralSecurityException) {
        throw TokenCipherException("Fallo de cifrado", e)
    } catch (e: ProviderException) {
        throw TokenCipherException("Keystore no disponible", e)
    }

    private companion object {
        const val DEFAULT_ALIAS = "taskeer_refresh_token_key"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
