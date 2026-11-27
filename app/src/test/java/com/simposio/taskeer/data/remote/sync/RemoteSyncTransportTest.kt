package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.ApiErrorMapper
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.RetryKind
import com.simposio.taskeer.data.sync.TransportResult
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.testing.FakeApiClient
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.apiOk
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSyncTransportTest {

    private val client = FakeApiClient()
    private val transport = RemoteSyncTransport(client)
    private val at = Instant.parse("2026-11-26T18:00:00Z")

    private fun mutation(id: String, entity: SyncEntity = SyncEntity.TASK) =
        PushMutation(id, entity, SyncOp.UPSERT, "e-$id", null, at, mapOf("title" to "x"))

    private fun failure(status: Int, code: String, retryAfter: Long? = null) =
        ApiResult.Failure(ApiErrorMapper.mapError(status, """{"error":{"code":"$code","message":"m"}}"""), retryAfter, status)

    @Test
    fun push_429_devuelveRetryConRetryAfter() = runTest {
        client.enqueue(failure(429, "RATE_LIMITED", 30))

        val result = transport.push(listOf(mutation("a"))) as TransportResult.Retry

        assertEquals(Duration.ofSeconds(30), result.after)
        assertEquals(RetryKind.RATE_LIMITED, result.kind)
    }

    @Test
    fun push_401_unauthorized() = runTest {
        client.enqueue(failure(401, "UNAUTHENTICATED"))

        assertEquals(TransportResult.Unauthorized, transport.push(listOf(mutation("a"))))
    }

    @Test
    fun push_413_batchTooLarge() = runTest {
        client.enqueue(failure(413, "BATCH_TOO_LARGE"))

        val result = transport.push(listOf(mutation("a"))) as TransportResult.Invalid

        assertEquals("BATCH_TOO_LARGE", result.code)
    }

    @Test
    fun push_5xx_retry() = runTest {
        client.enqueue(failure(503, "INTERNAL_SERVER_ERROR"))
        client.enqueue(ApiResult.Failure(DomainError.Network("NETWORK_UNREACHABLE", "x")))

        val server = transport.push(listOf(mutation("a"))) as TransportResult.Retry
        val network = transport.push(listOf(mutation("a"))) as TransportResult.Retry

        assertEquals(RetryKind.SERVER, server.kind)
        assertEquals(RetryKind.NETWORK, network.kind)
    }

    @Test
    fun lote_ordenYClientMutationId() = runTest {
        client.enqueue(apiOk("""{"contractVersion":"1.0","results":[]}"""))

        transport.push(listOf(mutation("m1"), mutation("m2"), mutation("m3")))

        val request = client.requests.single()
        val ids = JSONObject(request.jsonBody!!).getJSONArray("mutations").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("clientMutationId") } }
        assertEquals(listOf("m1", "m2", "m3"), ids)
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("sync/push", request.path)
    }

    @Test
    fun enviaCabeceraDeContrato() = runTest {
        client.enqueue(apiOk("""{"contractVersion":"1.0","results":[]}"""))
        client.enqueue(apiOk("""{"contractVersion":"1.0","supportedEntities":["list"],"me":{"userId":"$USER_A"}}"""))

        transport.push(listOf(mutation("a")))
        transport.identify()

        assertTrue(client.requests.all { it.headers["X-Sync-Contract"] == "1.0" })
    }

    @Test
    fun identify_leeMeSinAplicar() = runTest {
        client.enqueue(apiOk("""{"contractVersion":"1.2","supportedEntities":["list","task"],"me":{"userId":"$USER_A"},"changes":{"tasks":[{"id":"x"}]}}"""))

        val identity = (transport.identify() as TransportResult.Ok).value

        assertEquals(USER_A, identity.userId)
        assertEquals(setOf(SyncEntity.LIST, SyncEntity.TASK), identity.supportedEntities)
        assertEquals(listOf("limit" to "1"), client.requests.single().query)
        assertEquals("sync/changes", client.requests.single().path)
    }

    @Test
    fun sync404_notAvailable() = runTest {
        client.enqueue(failure(404, "NOT_FOUND"))
        client.enqueue(failure(501, "NOT_IMPLEMENTED"))

        assertEquals(TransportResult.NotAvailable, transport.identify())
        assertEquals(TransportResult.NotAvailable, transport.push(listOf(mutation("a"))))
    }

    private fun pageBody(cursor: String = "C1") = """{"contractVersion":"1.0","supportedEntities":["list","task"],"me":{"userId":"$USER_A"},""" +
        """"serverTime":"2026-11-26T18:30:00.000Z","isSnapshot":false,"changes":{"tasks":[]},"nextCursor":"$cursor","hasMore":false}"""

    @Test
    fun pull_conCursor_enQuery() = runTest {
        client.enqueue(apiOk(pageBody("C2")))
        client.enqueue(apiOk(pageBody()))

        val withCursor = (transport.pull("C1", 200) as TransportResult.Ok).value
        transport.pull(null, 50)

        assertEquals("C2", withCursor.nextCursor)
        assertEquals(listOf("cursor" to "C1", "limit" to "200"), client.requests[0].query)
        assertEquals(listOf("limit" to "50"), client.requests[1].query)
        assertEquals(HttpMethod.GET, client.requests[0].method)
        assertEquals("sync/changes", client.requests[0].path)
        assertEquals("1.0", client.requests[0].headers["X-Sync-Contract"])
    }

    @Test
    fun pull_400InvalidCursor() = runTest {
        client.enqueue(failure(400, "INVALID_CURSOR"))

        val result = transport.pull("viejo", 200) as TransportResult.Invalid

        assertEquals("INVALID_CURSOR", result.code)
    }

    @Test
    fun status426_updateRequired() = runTest {
        client.enqueue(failure(426, "CONTRACT_VERSION_UNSUPPORTED"))

        assertEquals(TransportResult.UpdateRequired, transport.identify())
    }

    @Test
    fun vectorV21_erroresHttp() = runTest {
        val cases = JSONObject(javaClass.getResource("/sync-contract/v1/V21.json")!!.readText()).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            client.enqueue(failure(case.getInt("status"), case.getString("code"), case.optString("retryAfter").toLongOrNull()))

            val result = transport.push(listOf(mutation("a")))

            val kind = when (result) {
                is TransportResult.Invalid -> "INVALID"
                TransportResult.Unauthorized -> "UNAUTHORIZED"
                is TransportResult.Retry -> "RETRY"
                TransportResult.UpdateRequired -> "UPDATE_REQUIRED"
                else -> "OTRO"
            }
            assertEquals("HTTP ${case.getInt("status")}", case.getString("expect"), kind)
        }
    }
}
