package com.simposio.taskeer.data.remote.transport

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.TEST_CONFIG
import com.simposio.taskeer.data.remote.TransportFailureKind
import com.simposio.taskeer.data.remote.TransportResult
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OkHttpTransportTest {

    private lateinit var server: HttpServer
    private val release = CountDownLatch(1)
    private val seen = mutableListOf<Seen>()

    private class Seen(val method: String, val uri: String, val headers: Map<String, String>, val body: String)

    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    }

    @After
    fun stopServer() {
        release.countDown()
        server.stop(0)
    }

    private fun serve(path: String = "/", handler: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange ->
            seen += Seen(
                exchange.requestMethod,
                exchange.requestURI.toString(),
                exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") },
                exchange.requestBody.readBytes().toString(Charsets.UTF_8),
            )
            handler(exchange)
            exchange.close()
        }
        server.start()
    }

    private fun reply(exchange: HttpExchange, status: Int, body: String = "") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(status, if (status == 204) -1 else bytes.size.toLong())
        if (status != 204) exchange.responseBody.write(bytes)
    }

    private fun transport(readTimeoutMs: Long = 5_000) = OkHttpTransport(
        ApiConfig("http://127.0.0.1/", allowCleartext = true, userAgent = "ua", readTimeoutMs = readTimeoutMs),
    )

    private fun send(request: HttpRequest, transport: OkHttpTransport = transport()): TransportResult =
        runBlocking { transport.send(request) }

    private fun response(result: TransportResult) = result as TransportResult.Response

    @Test
    fun get_enviaMetodoRutaYCabeceras() {
        serve { reply(it, 200, "{}") }

        send(HttpRequest(HttpMethod.GET, "$baseUrl/tasks/1", mapOf("X-Client" to "mobile"), null))

        assertEquals("GET", seen.single().method)
        assertEquals("/tasks/1", seen.single().uri)
        assertEquals("mobile", seen.single().headers["x-client"])
    }

    @Test
    fun patch_enviaMetodoYCuerpoJson() {
        serve { reply(it, 200) }

        send(HttpRequest(HttpMethod.PATCH, "$baseUrl/tasks/1", emptyMap(), "{\"title\":\"ñandú\"}"))

        assertEquals("PATCH", seen.single().method)
        assertEquals("{\"title\":\"ñandú\"}", seen.single().body)
    }

    @Test
    fun post_conCuerpo_ponContentType() {
        serve { reply(it, 200) }

        send(HttpRequest(HttpMethod.POST, "$baseUrl/x", emptyMap(), "{}"))

        assertEquals("application/json; charset=utf-8", seen.single().headers["content-type"])
    }

    @Test
    fun respuesta200_devuelveEstadoCuerpoYCabecerasEnMinusculas() {
        serve {
            it.responseHeaders.add("Retry-After", "7")
            reply(it, 200, "hola ñ")
        }

        val result = response(send(HttpRequest(HttpMethod.GET, "$baseUrl/", emptyMap(), null)))

        assertEquals(200, result.status)
        assertEquals("hola ñ", result.body)
        assertEquals("7", result.headers["retry-after"])
    }

    @Test
    fun respuesta204_devuelveCuerpoVacio() {
        serve { reply(it, 204) }

        val result = response(send(HttpRequest(HttpMethod.DELETE, "$baseUrl/x", emptyMap(), null)))

        assertEquals(204, result.status)
        assertEquals("", result.body)
    }

    @Test
    fun respuesta500_noEsFalloDeTransporte() {
        serve { reply(it, 500, "boom") }

        val result = response(send(HttpRequest(HttpMethod.GET, "$baseUrl/", emptyMap(), null)))

        assertEquals(500, result.status)
        assertEquals("boom", result.body)
    }

    @Test
    fun puertoCerrado_esUnreachable() {
        val closedPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

        val result = send(HttpRequest(HttpMethod.GET, "http://127.0.0.1:$closedPort/", emptyMap(), null))

        assertEquals(TransportFailureKind.UNREACHABLE, (result as TransportResult.Failure).kind)
    }

    @Test
    fun lecturaVencida_esTimeout() {
        serve { release.await(10, TimeUnit.SECONDS) }

        val result = send(
            HttpRequest(HttpMethod.GET, "$baseUrl/", emptyMap(), null),
            transport(readTimeoutMs = 300),
        )

        assertEquals(TransportFailureKind.TIMEOUT, (result as TransportResult.Failure).kind)
    }

    @Test
    fun sslExceptionMapea_aTls() {
        assertEquals(TransportFailureKind.TLS, OkHttpTransport.classify(SSLHandshakeException("handshake")))
    }

    @Test
    fun ioExceptionGenerica_esIo() {
        assertEquals(TransportFailureKind.IO, OkHttpTransport.classify(IOException("corte")))
    }

    @Test
    fun cancelarLaCorrutina_cancelaLaLlamadaYPropagaCancellation() {
        val arrived = CountDownLatch(1)
        serve {
            arrived.countDown()
            release.await(10, TimeUnit.SECONDS)
        }

        runBlocking {
            val call = async(Dispatchers.Default) {
                transport().send(HttpRequest(HttpMethod.GET, "$baseUrl/", emptyMap(), null))
            }
            withContext(Dispatchers.IO) { assertTrue(arrived.await(5, TimeUnit.SECONDS)) }
            call.cancel()
            try {
                call.await()
                fail("debía propagar la cancelación")
            } catch (_: CancellationException) {
                assertTrue(call.isCancelled)
            }
        }
    }

    @Test
    fun consulta_seCodificaEnLaUrl() {
        serve { reply(it, 200) }

        send(HttpRequest(HttpMethod.GET, "$baseUrl/tasks?q=a%20b&since=2026-01-01", emptyMap(), null))

        assertEquals("/tasks?q=a%20b&since=2026-01-01", seen.single().uri)
    }

    @Test
    fun cliente_noSigueRedirecciones() {
        val client = OkHttpTransport.buildClient(TEST_CONFIG)

        assertEquals(false, client.followRedirects)
    }
}
