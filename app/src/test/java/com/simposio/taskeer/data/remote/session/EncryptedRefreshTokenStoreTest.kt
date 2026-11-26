package com.simposio.taskeer.data.remote.session

import com.simposio.taskeer.data.remote.FakeTokenCipher
import com.simposio.taskeer.data.remote.InMemoryKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EncryptedRefreshTokenStoreTest {

    private val keyValues = InMemoryKeyValueStore()
    private val cipher = FakeTokenCipher()
    private val store = EncryptedRefreshTokenStore(keyValues, cipher, Dispatchers.Unconfined)

    @Test
    fun write_guardaDatoQueNoContieneElToken() = runBlocking {
        store.write("refresh-token-secreto")

        val stored = keyValues.values.values.single()
        assertFalse(stored.contains("refresh-token-secreto"))
        val hexOfPlain = "refresh-token-secreto".toByteArray().joinToString("") { "%02x".format(it) }
        assertFalse(stored.contains(hexOfPlain))
    }

    @Test
    fun read_devuelveElTokenEscrito() = runBlocking {
        store.write("refresh-ñ-1")

        assertEquals("refresh-ñ-1", store.read())
    }

    @Test
    fun read_conDescifradoFallido_borraYDevuelveNull() = runBlocking {
        store.write("refresh-1")
        cipher.failOnDecrypt = true

        assertNull(store.read())
        assertTrue(keyValues.values.isEmpty())
    }

    @Test
    fun read_conDatoCorrupto_borraYDevuelveNull() = runBlocking {
        keyValues.put("refresh_token", "zz-no-es-hex")

        assertNull(store.read())
        assertTrue(keyValues.values.isEmpty())
    }

    @Test
    fun read_conDescifradoFallido_yBorradoFallido_devuelveNull() = runBlocking {
        store.write("refresh-1")
        cipher.failOnDecrypt = true
        keyValues.failOnRemove = true

        assertNull(store.read())
    }

    @Test
    fun read_conDatoCorrupto_yBorradoFallido_devuelveNull() = runBlocking {
        keyValues.put("refresh_token", "zz-no-es-hex")
        keyValues.failOnRemove = true

        assertNull(store.read())
    }

    @Test
    fun write_sinPoderCifrar_noEscribeNada() = runBlocking {
        cipher.failOnEncrypt = true

        try {
            store.write("refresh-1")
            fail("debía fallar")
        } catch (_: TokenCipherException) {
            assertTrue(keyValues.values.isEmpty())
        }
    }

    @Test
    fun clear_eliminaElDato() = runBlocking {
        store.write("refresh-1")

        store.clear()

        assertNull(store.read())
        assertTrue(keyValues.values.isEmpty())
    }
}
