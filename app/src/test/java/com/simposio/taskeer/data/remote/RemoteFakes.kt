package com.simposio.taskeer.data.remote

import com.simposio.taskeer.data.remote.session.KeyValueStore
import com.simposio.taskeer.data.remote.session.RefreshOutcome
import com.simposio.taskeer.data.remote.session.RefreshTokenStore
import com.simposio.taskeer.data.remote.session.SessionRefresher
import com.simposio.taskeer.data.remote.session.TokenCipher
import com.simposio.taskeer.data.remote.session.TokenCipherException
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

val TEST_CONFIG = ApiConfig(
    baseUrl = "https://api.test/api/v1/",
    allowCleartext = false,
    userAgent = "Taskeer-Android/1.2.3",
)

fun response(status: Int, body: String = "", headers: Map<String, String> = emptyMap()): TransportResult =
    TransportResult.Response(status, headers, body)

fun failure(kind: TransportFailureKind): TransportResult = TransportResult.Failure(kind)

fun errorBody(code: String, message: String = "mensaje"): String =
    JSONObject().put("error", JSONObject().put("code", code).put("message", message)).toString()

class FakeHttpTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    private val queue = ArrayDeque<suspend (HttpRequest) -> TransportResult>()
    var fallback: (suspend (HttpRequest) -> TransportResult)? = null

    fun enqueue(result: TransportResult) {
        queue.addLast { result }
    }

    fun enqueue(handler: suspend (HttpRequest) -> TransportResult) {
        queue.addLast(handler)
    }

    override suspend fun send(request: HttpRequest): TransportResult {
        requests += request
        val handler = queue.removeFirstOrNull() ?: fallback ?: error("FakeHttpTransport sin respuesta para $request")
        return handler(request)
    }
}

class FakeSessionRefresher : SessionRefresher {
    val receivedTokens = mutableListOf<String>()
    private val queue = ArrayDeque<suspend (String) -> RefreshOutcome>()
    var gate: CompletableDeferred<Unit>? = null

    fun enqueue(outcome: RefreshOutcome) {
        queue.addLast { outcome }
    }

    fun enqueue(handler: suspend (String) -> RefreshOutcome) {
        queue.addLast(handler)
    }

    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        receivedTokens += refreshToken
        gate?.await()
        val handler = queue.removeFirstOrNull() ?: error("FakeSessionRefresher sin respuesta")
        return handler(refreshToken)
    }
}

class InMemoryRefreshTokenStore(var value: String? = null) : RefreshTokenStore {
    var failOnWrite = false
    var failOnClear = false
    var onWrite: (String) -> Unit = {}
    var writes = 0

    override suspend fun read(): String? = value

    override suspend fun write(token: String) {
        if (failOnWrite) throw TokenCipherException("Keystore inutilizable")
        onWrite(token)
        writes++
        value = token
    }

    override suspend fun clear() {
        if (failOnClear) throw TokenCipherException("almacen inutilizable")
        value = null
    }
}

class InMemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    var failOnRemove = false

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        if (failOnRemove) throw IllegalStateException("almacen inutilizable")
        values.remove(key)
    }
}

class FakeTokenCipher : TokenCipher {
    var failOnEncrypt = false
    var failOnDecrypt = false

    override fun encrypt(plain: ByteArray): ByteArray {
        if (failOnEncrypt) throw TokenCipherException("sin Keystore")
        return plain.reversedArray().map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    }

    override fun decrypt(data: ByteArray): ByteArray {
        if (failOnDecrypt) throw TokenCipherException("clave invalidada")
        return data.map { (it.toInt() xor 0x5a).toByte() }.toByteArray().reversedArray()
    }
}

class FakeRotatingBackend(private val grace: Boolean) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    var dropNextRefreshResponse = false
    var revoked = false
        private set
    private var generation = 0
    private var currentRefresh = ""
    private var previousRefresh: String? = null
    private var lastAccess = ""
    private val validAccess = mutableSetOf<String>()

    fun seed(accessToken: String, refreshToken: String) {
        validAccess += accessToken
        currentRefresh = refreshToken
        lastAccess = accessToken
    }

    fun expireAccess(accessToken: String) {
        validAccess -= accessToken
    }

    override suspend fun send(request: HttpRequest): TransportResult {
        requests += request
        return if (request.url.endsWith("auth/refresh")) refresh(request) else api(request)
    }

    private fun api(request: HttpRequest): TransportResult {
        val token = request.headers["Authorization"]?.removePrefix("Bearer ")
        return if (token in validAccess) {
            response(200, "{}")
        } else {
            response(401, errorBody("UNAUTHENTICATED"))
        }
    }

    private fun refresh(request: HttpRequest): TransportResult {
        val presented = JSONObject(requireNotNull(request.body)).getString("refreshToken")
        if (revoked) return response(401, errorBody("INVALID_REFRESH_TOKEN"))
        return when {
            presented == currentRefresh -> {
                generation++
                previousRefresh = currentRefresh
                currentRefresh = "refresh-$generation"
                lastAccess = "access-$generation"
                validAccess += lastAccess
                if (dropNextRefreshResponse) {
                    dropNextRefreshResponse = false
                    failure(TransportFailureKind.TIMEOUT)
                } else {
                    pair()
                }
            }
            grace && presented == previousRefresh -> pair()
            else -> {
                revoked = true
                response(401, errorBody("REFRESH_TOKEN_REUSED"))
            }
        }
    }

    private fun pair(): TransportResult = response(
        200,
        JSONObject().put("accessToken", lastAccess).put("refreshToken", currentRefresh).toString(),
    )
}
