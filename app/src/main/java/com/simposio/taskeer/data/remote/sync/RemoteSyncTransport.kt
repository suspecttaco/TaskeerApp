package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.sync.Identity
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.RetryKind
import com.simposio.taskeer.data.sync.SyncTransport
import com.simposio.taskeer.data.sync.TransportResult
import com.simposio.taskeer.domain.model.DomainError
import java.time.Duration

class RemoteSyncTransport(private val client: ApiClient) : SyncTransport {

    private val headers = mapOf(SyncApiContract.HEADER to SyncApiContract.VERSION)

    override suspend fun push(batch: List<PushMutation>): TransportResult<List<PushOutcome>> {
        val request = ApiRequest(
            HttpMethod.POST,
            SyncApiContract.PUSH_PATH,
            jsonBody = SyncApiContract.serializeBatch(batch),
            headers = headers,
        )
        return when (val result = client.execute(request)) {
            is ApiResult.Failure -> failure(result)
            is ApiResult.Success -> when (val parsed = SyncApiContract.parsePush(result.response.body, batch)) {
                is SyncApiContract.Parsed.Ok -> TransportResult.Ok(parsed.value)
                SyncApiContract.Parsed.UpdateRequired -> TransportResult.UpdateRequired
                SyncApiContract.Parsed.Malformed -> TransportResult.Retry(null, RetryKind.SERVER)
            }
        }
    }

    override suspend fun identify(): TransportResult<Identity> {
        val request = ApiRequest(
            HttpMethod.GET,
            SyncApiContract.CHANGES_PATH,
            query = listOf("limit" to "1"),
            headers = headers,
        )
        return when (val result = client.execute(request)) {
            is ApiResult.Failure -> failure(result)
            is ApiResult.Success -> when (val parsed = SyncApiContract.parseIdentity(result.response.body)) {
                is SyncApiContract.Parsed.Ok -> TransportResult.Ok(parsed.value)
                SyncApiContract.Parsed.UpdateRequired -> TransportResult.UpdateRequired
                SyncApiContract.Parsed.Malformed -> TransportResult.Retry(null, RetryKind.SERVER)
            }
        }
    }

    private fun failure(result: ApiResult.Failure): TransportResult<Nothing> {
        val status = result.status
        val code = result.error.code
        val after = result.retryAfterSeconds?.let(Duration::ofSeconds)
        return when {
            status == 426 || code == SyncApiContract.CODE_UNSUPPORTED -> TransportResult.UpdateRequired
            status == 401 -> TransportResult.Unauthorized
            status == 404 || status == 405 || status == 501 -> TransportResult.NotAvailable
            status == 429 -> TransportResult.Retry(after, RetryKind.RATE_LIMITED)
            status == 413 || code == SyncApiContract.CODE_BATCH_TOO_LARGE -> TransportResult.Invalid(SyncApiContract.CODE_BATCH_TOO_LARGE)
            status == null || status in 500..599 -> TransportResult.Retry(after, if (result.error is DomainError.Network) RetryKind.NETWORK else RetryKind.SERVER)
            else -> TransportResult.Invalid(code)
        }
    }
}
