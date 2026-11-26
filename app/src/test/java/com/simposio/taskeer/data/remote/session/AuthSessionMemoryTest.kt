package com.simposio.taskeer.data.remote.session

import com.simposio.taskeer.data.remote.FakeSessionRefresher
import com.simposio.taskeer.data.remote.InMemoryRefreshTokenStore
import kotlinx.coroutines.runBlocking
import com.simposio.taskeer.domain.model.DomainError
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthSessionMemoryTest {

    @Test
    fun accessToken_noSeEscribeEnElAlmacen() = runBlocking {
        val store = InMemoryRefreshTokenStore()
        val written = mutableListOf<String>()
        store.onWrite = { written += it }
        val session = AuthSession(store, FakeSessionRefresher())

        session.start("access-secreto", "refresh-1")

        assertEquals(listOf("refresh-1"), written)
        assertEquals("access-secreto", session.currentAccessToken())
        assertEquals("refresh-1", store.value)
    }

    @Test
    fun renew_rechazado_conBorradoDelAlmacenFallido_limpiaMemoriaYEmiteUnaVez() = runBlocking {
        val store = InMemoryRefreshTokenStore("refresh-1")
        val refresher = FakeSessionRefresher()
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("REFRESH_TOKEN_REVOKED", "x")))
        val session = AuthSession(store, refresher)
        session.start("access-1", "refresh-1")
        store.failOnClear = true
        val emitted = mutableListOf<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { session.expirations.collect { emitted += it } }

        val outcome = session.renew("access-1")
        yield()
        collector.cancel()

        assertEquals(RenewOutcome.SessionExpired, outcome)
        assertNull(session.currentAccessToken())
        assertEquals(1, emitted.size)
    }

    @Test
    fun clear_conBorradoDelAlmacenFallido_limpiaMemoria() = runBlocking {
        val store = InMemoryRefreshTokenStore()
        val session = AuthSession(store, FakeSessionRefresher())
        session.start("access-1", "refresh-1")
        store.failOnClear = true

        session.clear()

        assertNull(session.currentAccessToken())
    }
}
