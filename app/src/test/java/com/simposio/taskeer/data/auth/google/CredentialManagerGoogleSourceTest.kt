package com.simposio.taskeer.data.auth.google

import com.simposio.taskeer.domain.auth.GoogleCredentialResult
import com.simposio.taskeer.domain.auth.GoogleSignInConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialManagerGoogleSourceTest {

    private class RecordingClient(var result: CredentialClientResult, var failure: Throwable? = null) : CredentialClient {
        var calls = 0
        var webClientId: String? = null
        var hashedNonce: String? = null

        override suspend fun request(host: Any, webClientId: String, hashedNonce: String): CredentialClientResult {
            calls++
            this.webClientId = webClientId
            this.hashedNonce = hashedNonce
            failure?.let { throw it }
            return result
        }
    }

    private val host = Any()

    private fun source(client: CredentialClient, webClientId: String = "web-client", isHost: (Any) -> Boolean = { true }) =
        CredentialManagerGoogleSource(GoogleSignInConfig(webClientId), client, isHost)

    @Test
    fun sinClientId_esUnavailable() = runTest {
        val client = RecordingClient(CredentialClientResult.IdToken("t"))

        val result = source(client, webClientId = "").obtain(host)

        assertEquals(GoogleCredentialResult.Unavailable, result)
        assertEquals(0, client.calls)
    }

    @Test
    fun hostQueNoEsActivity_esUnavailable() = runTest {
        val client = RecordingClient(CredentialClientResult.IdToken("t"))

        val result = CredentialManagerGoogleSource(GoogleSignInConfig("web-client"), client) { it is android.app.Activity }
            .obtain("no soy una activity")

        assertEquals(GoogleCredentialResult.Unavailable, result)
        assertEquals(0, client.calls)
    }

    @Test
    fun tokenValido_esObtainedConElNonceEnClaro() = runTest {
        val client = RecordingClient(CredentialClientResult.IdToken("id-token"))

        val result = source(client).obtain(host) as GoogleCredentialResult.Obtained

        assertEquals("id-token", result.credential.idToken)
        assertEquals(GoogleNonce.sha256Hex(result.credential.rawNonce), client.hashedNonce)
    }

    @Test
    fun elClienteRecibeElNonceHasheadoYElWebClientId() = runTest {
        val client = RecordingClient(CredentialClientResult.IdToken("id-token"))

        val result = source(client).obtain(host) as GoogleCredentialResult.Obtained

        assertEquals("web-client", client.webClientId)
        assertEquals(64, client.hashedNonce?.length)
        assertTrue(client.hashedNonce != result.credential.rawNonce)
    }

    @Test
    fun cadaIntentoUsaUnNonceNuevo() = runTest {
        val client = RecordingClient(CredentialClientResult.IdToken("id-token"))
        val source = source(client)

        val first = source.obtain(host) as GoogleCredentialResult.Obtained
        val second = source.obtain(host) as GoogleCredentialResult.Obtained

        assertTrue(first.credential.rawNonce != second.credential.rawNonce)
    }

    @Test
    fun cancelacion_esCancelled() = runTest {
        assertEquals(GoogleCredentialResult.Cancelled, source(RecordingClient(CredentialClientResult.UserCancelled)).obtain(host))
    }

    @Test
    fun sinCredencial_esNoAccount() = runTest {
        assertEquals(GoogleCredentialResult.NoAccount, source(RecordingClient(CredentialClientResult.NoCredential)).obtain(host))
    }

    @Test
    fun sinProveedor_esUnavailable() = runTest {
        assertEquals(GoogleCredentialResult.Unavailable, source(RecordingClient(CredentialClientResult.ProviderMissing)).obtain(host))
    }

    @Test
    fun otroFallo_esFailed() = runTest {
        assertEquals(GoogleCredentialResult.Failed, source(RecordingClient(CredentialClientResult.Other)).obtain(host))
    }

    @Test
    fun excepcionInesperadaDelCliente_esFailed() = runTest {
        val client = RecordingClient(CredentialClientResult.Other, failure = IllegalStateException("token-secreto"))

        assertEquals(GoogleCredentialResult.Failed, source(client).obtain(host))
    }

    @Test
    fun laCancelacionDeLaCorrutina_sePropaga() = runTest {
        val cancellation = CancellationException("cancelada")
        val client = RecordingClient(CredentialClientResult.Other, failure = cancellation)

        val thrown = try {
            source(client).obtain(host)
            null
        } catch (e: CancellationException) {
            e
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun unavailable_delContenedorSinClientId() = runTest {
        val unavailable = createGoogleCredentialSource(GoogleSignInConfig(""))

        assertSame(UnavailableGoogleCredentialSource, unavailable)
        assertEquals(GoogleCredentialResult.Unavailable, unavailable.obtain(host))
    }

    @Test
    fun idTokenDelCliente_toStringRedactado() {
        assertTrue(!CredentialClientResult.IdToken("id-token-secreto").toString().contains("secreto"))
    }
}
