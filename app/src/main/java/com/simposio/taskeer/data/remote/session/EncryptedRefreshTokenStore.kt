package com.simposio.taskeer.data.remote.session

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EncryptedRefreshTokenStore(
    private val keyValueStore: KeyValueStore,
    private val cipher: TokenCipher,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RefreshTokenStore {

    override suspend fun read(): String? = withContext(dispatcher) {
        val stored = keyValueStore.get(KEY) ?: return@withContext null
        try {
            String(cipher.decrypt(fromHex(stored)), Charsets.UTF_8)
        } catch (_: TokenCipherException) {
            removeQuietly()
            null
        } catch (_: IllegalArgumentException) {
            removeQuietly()
            null
        }
    }

    // La limpieza es best effort: un fallo al borrar no debe convertir «sin sesión» (R6) en excepción.
    private fun removeQuietly() {
        try {
            keyValueStore.remove(KEY)
        } catch (_: Exception) {
        }
    }

    override suspend fun write(token: String) = withContext(dispatcher) {
        val encrypted = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
        keyValueStore.put(KEY, toHex(encrypted))
    }

    override suspend fun clear() = withContext(dispatcher) {
        keyValueStore.remove(KEY)
    }

    // Hex y no Base64: java.util.Base64 exige API 26 y android.util.Base64 no existe en los tests JVM.
    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun fromHex(text: String): ByteArray {
        require(text.length % 2 == 0) { "Dato guardado corrupto" }
        return ByteArray(text.length / 2) { index ->
            text.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val KEY = "refresh_token"
    }
}
