package com.simposio.taskeer.data.remote.supabase

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.FakeHttpTransport
import com.simposio.taskeer.data.remote.auth.GoogleExchangeResult
import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertFalse
import org.junit.Test

class SupabaseModelsTest {

    @Test
    fun toString_noIncluyeTokens() {
        val config = ApiConfig("https://p.supabase.test", allowCleartext = false, userAgent = "t")
        val texts = listOf(
            GoogleExchangeResult.Token("jwt-super-secreto").toString(),
            GoogleExchangeResult.Failed(DomainError.Unauthenticated("X", "m")).toString(),
            SupabaseTokenExchange(FakeHttpTransport(), config, "anon-super-secreta").toString(),
        )

        texts.forEach { text ->
            assertFalse(text, text.contains("jwt-super-secreto"))
            assertFalse(text, text.contains("anon-super-secreta"))
        }
    }
}
