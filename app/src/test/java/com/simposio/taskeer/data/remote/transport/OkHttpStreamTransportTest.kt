package com.simposio.taskeer.data.remote.transport

import com.simposio.taskeer.data.remote.TEST_CONFIG
import com.simposio.taskeer.data.remote.TransportFailureKind
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.InterruptedIOException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OkHttpStreamTransportTest {

    private val client = OkHttpStreamTransport.buildStreamClient(TEST_CONFIG)

    @Test
    fun clienteDeFlujo_sinCallTimeout() {
        assertEquals(0, client.callTimeoutMillis)
    }

    @Test
    fun clienteDeFlujo_readTimeoutDe45s() {
        assertEquals(45_000, client.readTimeoutMillis)
    }

    @Test
    fun clienteDeFlujo_sinRedireccionesNiReintentos() {
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
    }

    @Test
    fun classify_reutilizaLaClasificacionDeErrores() {
        assertEquals(TransportFailureKind.UNREACHABLE, OkHttpTransport.classify(UnknownHostException()))
        assertEquals(TransportFailureKind.TIMEOUT, OkHttpTransport.classify(SocketTimeoutException()))
        assertEquals(TransportFailureKind.TIMEOUT, OkHttpTransport.classify(InterruptedIOException()))
        assertEquals(TransportFailureKind.TLS, OkHttpTransport.classify(SSLHandshakeException("x")))
    }
}
