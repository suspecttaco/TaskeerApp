package com.simposio.taskeer.data.remote.transport

import com.simposio.taskeer.data.remote.ApiConfig
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugHttpLogInterceptorTest {

    private lateinit var server: HttpServer
    private val logs = mutableListOf<String>()
    private var status = 200
    private var body = "{}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun client() = OkHttpClient.Builder().addInterceptor(DebugHttpLogInterceptor({ logs += it })).build()

    private fun call(client: OkHttpClient, path: String, payload: String = "{\"password\":\"hunter2\"}") {
        val request = Request.Builder().url(url(path))
            .header("Authorization", "Bearer SECRET-BEARER")
            .header("Cookie", "sid=COOKIE-VALUE")
            .header("X-Probe", "visible")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().close()
    }

    @Test
    fun errorBodyMasksSecretsAndKeepsBusinessCode() {
        status = 401
        body = """{"code":"INVALID_CREDENTIALS","message":"no","accessToken":"abc.def","refreshToken":"r1","codes":["AB12-CD34"]}"""

        call(client(), "/auth/login?token=qq")

        val text = logs.joinToString("\n")
        assertTrue(text, "--> POST ${url("/auth/login")}" in logs)
        assertTrue(text, logs.any { it.startsWith("<-- 401 ${url("/auth/login")} (") })
        assertTrue(text, "INVALID_CREDENTIALS" in text)
        listOf("abc.def", "r1", "AB12-CD34", "hunter2", "SECRET-BEARER", "qq").forEach {
            assertFalse("filtra $it: $text", it in text)
        }
        assertTrue(text, "\"accessToken\":\"***\"" in text)
    }

    @Test
    fun successBodyIsLoggedWithTokensMasked() {
        body = """{"user":{"email":"a@b.co"},"accessToken":"abc.def","refreshToken":"r1"}"""

        call(client(), "/auth/login")

        val text = logs.joinToString("\n")
        assertTrue(text, "\"email\":\"a@b.co\"" in text)
        assertTrue(text, "\"accessToken\":\"***\"" in text && "\"refreshToken\":\"***\"" in text)
        assertFalse(text, "abc.def" in text || "\"r1\"" in text)
    }

    @Test
    fun requestBodyShowsEmailAndNeverThePassword() {
        call(client(), "/auth/login", """{"email":"user@example.com","password":"S3cret-pw!"}""")

        val text = logs.joinToString("\n")
        assertTrue(text, "\"email\":\"user@example.com\"" in text)
        assertTrue(text, "\"password\":\"***(len=10, trimmed=true)\"" in text)
        assertFalse(text, "S3cret-pw!" in text)
    }

    @Test
    fun passwordShapeFlagsEdgeWhitespaceForAllPasswordKeys() {
        call(client(), "/x", """{"currentPassword":"abc ","newPassword":" abcd"}""")

        val text = logs.joinToString("\n")
        assertTrue(text, "\"currentPassword\":\"***(len=4, trimmed=false)\"" in text)
        assertTrue(text, "\"newPassword\":\"***(len=5, trimmed=false)\"" in text)
    }

    @Test
    fun otherRequestSecretsAreMasked() {
        call(client(), "/x", """{"refreshToken":"RT-1","idToken":"IT-1","nonce":"NN-1","code":"AB12-CD34","secret":"SS-1"}""")

        val text = logs.joinToString("\n")
        listOf("RT-1", "IT-1", "NN-1", "AB12-CD34", "SS-1").forEach { assertFalse("filtra $it: $text", it in text) }
    }

    @Test
    fun authorizationAndCookieHeadersAreMaskedAndOthersShown() {
        call(client(), "/x")

        assertTrue(logs.toString(), "Authorization: ***" in logs && "Cookie: ***" in logs)
        assertTrue(logs.toString(), "X-Probe: visible" in logs)
        assertFalse(logs.toString(), logs.any { "SECRET-BEARER" in it || "COOKIE-VALUE" in it })
    }

    @Test
    fun responseHeadersShowContentTypeAndMaskSetCookie() {
        server.removeContext("/")
        server.createContext("/") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Set-Cookie", "sid=ZZ-COOKIE")
            exchange.responseHeaders.add("Retry-After", "30")
            exchange.responseHeaders.add("X-Other", "hidden")
            exchange.sendResponseHeaders(429, -1)
            exchange.close()
        }

        call(client(), "/x")

        val lines = logs.map { it.lowercase() }
        assertTrue(logs.toString(), "content-type: application/json" in lines)
        assertTrue(logs.toString(), "set-cookie: ***" in lines)
        assertTrue(logs.toString(), "retry-after: 30" in lines)
        assertFalse(logs.toString(), logs.any { "ZZ-COOKIE" in it || "X-Other" in it })
    }

    @Test
    fun nonJsonRequestBodyLogsOnlyItsSize() {
        val request = Request.Builder().url(url("/x")).post("plain-secret".toRequestBody("text/plain".toMediaType())).build()

        client().newCall(request).execute().close()

        assertTrue(logs.toString(), "<12 bytes>" in logs)
        assertFalse(logs.toString(), logs.any { "plain-secret" in it })
    }

    @Test
    fun networkFailureLogsExceptionClassAndMessage() {
        val dead = url("/x")
        server.stop(0)

        try {
            client().newCall(Request.Builder().url(dead).build()).execute()
        } catch (_: IOException) {
        }

        assertTrue(logs.toString(), logs.any { it.startsWith("<-- FAILED $dead: ConnectException:") && it.endsWith(" ms)") })
    }

    @Test
    fun disabledConfigInstallsNoInterceptor() {
        val config = ApiConfig("http://127.0.0.1/", allowCleartext = true, userAgent = "ua")

        val client = OkHttpTransport.buildClient(config)

        assertTrue(client.interceptors.none { it is DebugHttpLogInterceptor })
    }

    @Test
    fun enabledConfigInstallsInterceptor() {
        val config = ApiConfig("http://127.0.0.1/", allowCleartext = true, userAgent = "ua", httpLogging = true)

        val client = OkHttpTransport.buildClient(config)

        assertTrue(client.interceptors.any { it is DebugHttpLogInterceptor })
    }

    @Test
    fun syncMuestraPeticionYRespuestaConLaUrlRealConPrefijoDeApi() {
        body = """{"results":[{"title":"Respuesta visible"}]}"""

        call(client(), "/api/v1/sync/push", """{"mutations":[{"data":{"title":"Comprar leche"}}]}""")

        val text = logs.joinToString("\n")
        assertTrue(text.contains("Comprar leche"))
        assertTrue(text.contains("Respuesta visible"))
        assertFalse(text.contains("SECRET-BEARER"))
    }

    @Test
    fun syncSigueEnmascarandoSecretos() {
        body = """{"results":[],"access_token":"AT-SYNC"}"""

        call(client(), "/sync/push", """{"mutations":[],"refresh_token":"RT-SYNC"}""")

        val text = logs.joinToString("\n")
        assertFalse(text.contains("AT-SYNC"))
        assertFalse(text.contains("RT-SYNC"))
    }

    @Test
    fun cuerpoGrandeDeSyncSeParteEnTrozosSinPerderContenido() {
        val big = """{"results":[${(1..100).joinToString(",") { """{"clientMutationId":"m$it","pad":"${"x".repeat(150)}"}""" }}]}"""
        body = big

        call(client(), "/api/v1/sync/push", "{}")

        val chunks = logs.filter { it.startsWith("[") && "/" in it.take(8) }
        assertTrue(chunks.toString(), chunks.size > 1)
        assertTrue(chunks.all { it.length < 3600 })
        assertTrue(logs.joinToString("").contains("\"clientMutationId\":\"m100\""))
    }

    @Test
    fun rutasDelPuenteOmitenPeticionYRespuesta() {
        body = """{"task":{"title":"Titulo del servidor"},"lists":[{"name":"Lista del servidor"}]}"""

        call(client(), "/api/v1/tasks/abc", """{"title":"Llamar al banco"}""")
        call(client(), "/api/v1/tasks/abc/subtasks/def", """{"title":"Subtarea privada"}""")
        call(client(), "/api/v1/lists", """{"name":"Secreta"}""")

        val text = logs.joinToString("\n")
        assertFalse(text.contains("Llamar al banco"))
        assertFalse(text.contains("Subtarea privada"))
        assertFalse(text.contains("Secreta"))
        assertFalse(text.contains("Titulo del servidor"))
        assertFalse(text.contains("Lista del servidor"))
    }

    @Test
    fun enmascaraIdTokenAccessTokenNonceYSupabaseAccessToken() {
        body = """{"access_token":"AT-9","refresh_token":"RT-9","user":{"id":"u","access_token":"AT-NESTED"}}"""

        call(client(), "/auth/v1/token", """{"provider":"google","id_token":"IT-9","nonce":"NN-9","supabaseAccessToken":"SB-9"}""")

        val text = logs.joinToString("\n")
        listOf("AT-9", "RT-9", "IT-9", "NN-9", "SB-9", "AT-NESTED").forEach { assertFalse("filtra $it: $text", it in text) }
        assertTrue(text, "\"provider\":\"google\"" in text)
    }

    @Test
    fun enmascaraLaCabeceraApikey() {
        val request = Request.Builder().url(url("/auth/v1/token")).header("apikey", "ANON-KEY-SECRETA").build()

        client().newCall(request).execute().close()

        assertTrue(logs.toString(), "apikey: ***" in logs)
        assertFalse(logs.toString(), logs.any { "ANON-KEY-SECRETA" in it })
    }

    @Test
    fun enmascaraApikeyComoClaveJson() {
        call(client(), "/x", """{"apikey":"ANON-JSON"}""")

        assertFalse(logs.joinToString("\n"), logs.any { "ANON-JSON" in it })
    }

    @Test
    fun noEnmascaraErrorCodeCorto() {
        status = 400
        body = """{"error_code":"bad_id_token","msg":"Token inválido"}"""

        call(client(), "/auth/v1/token")

        assertTrue(logs.joinToString("\n"), logs.any { "\"error_code\":\"bad_id_token\"" in it })
    }

    @Test
    fun enmascaraErrorCodeQueNoEsUnIdentificadorCorto() {
        status = 400
        body = """{"error_code":"ey.JWT-LARGO-1234"}"""

        call(client(), "/auth/v1/token")

        assertFalse(logs.joinToString("\n"), logs.any { "JWT-LARGO" in it })
    }

    @Test
    fun laUrlDelCanjeSeLogueaSinQuery() {
        call(client(), "/auth/v1/token?grant_type=id_token&secret=QQ")

        val line = logs.first { it.startsWith("--> POST") }
        assertFalse(line, "grant_type" in line || "QQ" in line)
    }
}
