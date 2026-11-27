package com.simposio.taskeer.data.remote.supabase

import com.simposio.taskeer.domain.model.DomainError
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthContractTest {

    private fun errorJson(vararg pairs: Pair<String, String>): String =
        JSONObject().also { json -> pairs.forEach { json.put(it.first, it.second) } }.toString()

    @Test
    fun tokenRequest_llevaProviderIdTokenYNonce() {
        val body = JSONObject(SupabaseAuthContract.tokenBody("id-token", "nonce-claro"))

        assertEquals("google", body.getString("provider"))
        assertEquals("id-token", body.getString("id_token"))
        assertEquals("nonce-claro", body.getString("nonce"))
        assertEquals(3, body.length())
    }

    @Test
    fun tokenRequest_usaGrantTypeIdTokenEnLaUrl() {
        assertEquals("auth/v1/token", SupabaseAuthContract.TOKEN_PATH)
        assertEquals("grant_type=id_token", SupabaseAuthContract.GRANT_QUERY)
    }

    @Test
    fun parseToken_leeSoloAccessToken() {
        val body = """{"access_token":"jwt","refresh_token":"r","expires_in":3600,"user":{"id":"u"}}"""

        assertEquals("jwt", SupabaseAuthContract.parseAccessToken(body))
    }

    @Test
    fun parseToken_sinAccessToken_esIlegible() {
        assertNull(SupabaseAuthContract.parseAccessToken("""{"refresh_token":"r"}"""))
        assertNull(SupabaseAuthContract.parseAccessToken("""{"access_token":"  "}"""))
    }

    @Test
    fun parseToken_jsonRoto_esIlegible() {
        assertNull(SupabaseAuthContract.parseAccessToken("no es json"))
        assertNull(SupabaseAuthContract.parseAccessToken(""))
    }

    @Test
    fun classify_badIdTokenYValidationFailed_sonInvalidGoogleToken() {
        listOf("bad_id_token", "validation_failed").forEach { code ->
            val failed = SupabaseAuthContract.classify(400, emptyMap(), errorJson("error_code" to code))

            assertTrue(code, failed.error is DomainError.Unauthenticated)
            assertEquals(code, "INVALID_GOOGLE_TOKEN", failed.error.code)
        }
    }

    @Test
    fun classify_invalidGrant_esInvalidGoogleToken() {
        val failed = SupabaseAuthContract.classify(400, emptyMap(), errorJson("error" to "invalid_grant"))

        assertEquals("INVALID_GOOGLE_TOKEN", failed.error.code)
    }

    @Test
    fun classify_providerDisabled_esRechazado() {
        val failed = SupabaseAuthContract.classify(400, emptyMap(), errorJson("error_code" to "provider_disabled"))

        assertTrue(failed.error is DomainError.Validation)
        assertEquals("GOOGLE_EXCHANGE_REJECTED", failed.error.code)
    }

    @Test
    fun classify_otro4xxYCuerpoNoJson_esRechazado() {
        assertEquals("GOOGLE_EXCHANGE_REJECTED", SupabaseAuthContract.classify(404, emptyMap(), "").error.code)
        assertEquals("GOOGLE_EXCHANGE_REJECTED", SupabaseAuthContract.classify(400, emptyMap(), "<html>").error.code)
        assertEquals("GOOGLE_EXCHANGE_REJECTED", SupabaseAuthContract.classify(422, emptyMap(), errorJson("error_code" to "signup_disabled")).error.code)
    }

    @Test
    fun classify_429_esRateLimitedConRetryAfter() {
        val failed = SupabaseAuthContract.classify(429, mapOf("retry-after" to " 30 "), errorJson("error_code" to "over_request_rate_limit"))

        assertTrue(failed.error is DomainError.RateLimited)
        assertEquals(30L, failed.retryAfterSeconds)
        assertNull(SupabaseAuthContract.classify(429, emptyMap(), "").retryAfterSeconds)
    }

    @Test
    fun classify_5xx_esServer() {
        val failed = SupabaseAuthContract.classify(503, emptyMap(), "")

        assertTrue(failed.error is DomainError.Server)
        assertEquals("GOOGLE_EXCHANGE_UNAVAILABLE", failed.error.code)
    }

    @Test
    fun nuncaLeeMsgNiMessage() {
        val body = errorJson("msg" to "SECRETO-MSG", "message" to "SECRETO-MESSAGE", "error_description" to "SECRETO-DESC", "error_code" to "bad_id_token")

        val failed = SupabaseAuthContract.classify(400, emptyMap(), body)

        listOf("SECRETO-MSG", "SECRETO-MESSAGE", "SECRETO-DESC").forEach { assertTrue(!failed.error.message.contains(it)) }
        assertEquals("bad_id_token", SupabaseAuthContract.errorCodeOf(body))
        assertNull(SupabaseAuthContract.errorCodeOf(errorJson("msg" to "x")))
    }
}
