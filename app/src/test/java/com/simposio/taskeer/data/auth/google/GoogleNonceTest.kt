package com.simposio.taskeer.data.auth.google

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNonceTest {

    @Test
    fun tiene32BytesCodificadosEnBase64UrlSinRelleno() {
        val nonce = GoogleNonce.generate()

        assertEquals(43, nonce.raw.length)
        assertTrue(nonce.raw.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(!nonce.raw.contains("="))
    }

    @Test
    fun cadaLlamadaGeneraUnNonceDistinto() {
        val first = GoogleNonce.generate()
        val second = GoogleNonce.generate()

        assertNotEquals(first.raw, second.raw)
        assertNotEquals(first.sha256Hex, second.sha256Hex)
    }

    @Test
    fun elSha256HexCoincideConUnVectorConocido() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", GoogleNonce.sha256Hex("abc"))
    }

    @Test
    fun elSha256HexEsElDelNonceEnClaro() {
        val nonce = GoogleNonce.generate()

        assertEquals(GoogleNonce.sha256Hex(nonce.raw), nonce.sha256Hex)
        assertEquals(64, nonce.sha256Hex.length)
    }

    @Test
    fun base64Url_coincideConVectoresConocidos() {
        assertEquals("Zm9vYmFy", GoogleNonce.base64Url("foobar".toByteArray()))
        assertEquals("Zm9vYg", GoogleNonce.base64Url("foob".toByteArray()))
        assertEquals("Zm9vYmE", GoogleNonce.base64Url("fooba".toByteArray()))
        assertEquals("-_8", GoogleNonce.base64Url(byteArrayOf(0xFB.toByte(), 0xFF.toByte())))
    }

    @Test
    fun usaElGeneradorAleatorioRecibido() {
        val fixed = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) = bytes.fill(0)
        }

        assertEquals("A".repeat(43), GoogleNonce.generate(fixed).raw)
    }

    @Test
    fun toString_noIncluyeElNonce() {
        val nonce = GoogleNonce.generate()

        assertTrue(!nonce.toString().contains(nonce.raw))
    }
}
