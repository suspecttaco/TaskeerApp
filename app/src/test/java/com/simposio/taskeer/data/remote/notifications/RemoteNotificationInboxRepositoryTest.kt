package com.simposio.taskeer.data.remote.notifications

import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.notification.InboxOutcome
import com.simposio.taskeer.testing.FakeApiClient
import com.simposio.taskeer.testing.apiOk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteNotificationInboxRepositoryTest {

    private val client = FakeApiClient()
    private val session = MutableStateFlow<SessionState>(signedIn("ana@example.com"))
    private var now = Instant.parse("2026-11-26T10:00:00Z")

    private fun signedIn(email: String, offline: Boolean = false): SessionState =
        SessionState.SignedIn(AccountSummary(email, "Ana"), offline)

    private fun uid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    private fun entry(n: Int, read: Boolean = false, ageSeconds: Long = n.toLong()): String {
        val created = Instant.parse("2026-11-26T09:00:00Z").minusSeconds(ageSeconds)
        val readAt = if (read) "\"$created\"" else "null"
        return """{"id":"${uid(n)}","userId":"u","type":"TASK_ASSIGNED","title":"Aviso $n","body":null,"taskId":null,"readAt":$readAt,"createdAt":"$created"}"""
    }

    private fun body(vararg entries: String) = """{"notifications":[${entries.joinToString(",")}]}"""

    private fun TestScope.repo() = RemoteNotificationInboxRepository(client, session, backgroundScope) { now }

    private suspend fun RemoteNotificationInboxRepository.load(vararg entries: String) {
        client.enqueue(apiOk(body(*entries)))
        assertEquals(InboxOutcome.Done, refresh())
        client.requests.clear()
    }

    private fun failure(error: DomainError, retryAfter: Long? = null): ApiResult = ApiResult.Failure(error, retryAfter)

    private fun serverDown() = DomainError.Server("INTERNAL_SERVER_ERROR", "x")

    private fun notFound() = DomainError.NotFound("NOTIFICATION_NOT_FOUND", "x")

    @Test
    fun sinSesion_ningunaOperacionHacePeticion() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        val outcomes = listOf(
            repo.refresh(), repo.refreshUnread(), repo.markRead(uid(1)), repo.markAllRead(), repo.delete(uid(1)), repo.deleteRead(),
        )

        assertTrue(outcomes.all { it is InboxOutcome.Failed && it.error is DomainError.Unauthenticated })
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun signedOut_dejaElEstadoVacio() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        assertEquals(emptyList<Any>(), repo.state.value.items)
        assertFalse(repo.state.value.listLoaded)
    }

    @Test
    fun cerrarSesion_vaciaElEstado() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))

        session.value = SessionState.SignedOut(expired = true)
        runCurrent()

        assertEquals(0, repo.state.value.items.size)
        assertEquals(0, repo.state.value.unreadCount)
    }

    @Test
    fun respuestaTardiaTrasCerrarSesion_seDescarta() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(body(entry(1))) }
        var outcome: InboxOutcome? = null
        launch { outcome = repo.refresh() }
        runCurrent()

        session.value = SessionState.SignedOut()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertTrue(outcome is InboxOutcome.Failed)
        assertEquals(0, repo.state.value.items.size)
    }

    @Test
    fun cambioDeCuenta_noMuestraLoDeLaAnterior() = runTest {
        val repo = repo()
        repo.load(entry(1))

        session.value = SessionState.SignedOut()
        runCurrent()
        session.value = signedIn("luis@example.com")
        runCurrent()

        assertEquals(0, repo.state.value.items.size)
    }

    @Test
    fun cambioDeCuentaSinPasarPorSignedOut_tambienVaciaElEstado() = runTest {
        val repo = repo()
        repo.load(entry(1))

        session.value = signedIn("luis@example.com")
        runCurrent()

        assertEquals(0, repo.state.value.items.size)
    }

    @Test
    fun resuming_conSesionPrevia_noVaciaElEstado() = runTest {
        val repo = repo()
        repo.load(entry(1))

        session.value = SessionState.Resuming
        runCurrent()

        assertEquals(1, repo.state.value.items.size)
    }

    @Test
    fun refreshUnread_hacePeticionConUnreadTrue() = runTest {
        val repo = repo()
        client.enqueue(apiOk(body(entry(1))))

        repo.refreshUnread()

        val request = client.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("notifications", request.path)
        assertEquals(listOf("unread" to "true"), request.query)
    }

    @Test
    fun refreshUnread_actualizaElContador() = runTest {
        val repo = repo()
        client.enqueue(apiOk(body(entry(1), entry(2), entry(3))))

        assertEquals(InboxOutcome.Done, repo.refreshUnread())

        assertEquals(3, repo.state.value.unreadCount)
        assertFalse(repo.state.value.listLoaded)
    }

    @Test
    fun refreshUnread_dentroDelIntervalo_noHacePeticion() = runTest {
        val repo = repo()
        client.enqueue(apiOk(body(entry(1))))
        repo.refreshUnread()

        now = now.plusSeconds(29)
        repo.refreshUnread()

        assertEquals(1, client.requests.size)
    }

    @Test
    fun refreshUnread_pasadosLos30s_vuelveAPedir() = runTest {
        val repo = repo()
        client.fallback = { apiOk(body(entry(1))) }
        repo.refreshUnread()

        now = now.plusSeconds(30)
        repo.refreshUnread()

        assertEquals(2, client.requests.size)
    }

    @Test
    fun refreshUnread_fallido_tambienCuentaParaElIntervalo() = runTest {
        val repo = repo()
        client.enqueue(failure(serverDown()))
        val first = repo.refreshUnread()

        now = now.plusSeconds(5)
        repo.refreshUnread()

        assertTrue(first is InboxOutcome.Failed)
        assertEquals(1, client.requests.size)
    }

    @Test
    fun refreshUnread_conListaCargada_conservaLaLista() = runTest {
        val repo = repo()
        repo.load(entry(1, read = true), entry(2))
        client.enqueue(apiOk(body(entry(2), entry(9, ageSeconds = -10))))

        repo.refreshUnread()

        assertEquals(listOf(uid(9), uid(1), uid(2)).sorted().size, repo.state.value.items.size)
        assertEquals(uid(9), repo.state.value.items.first().id)
        assertTrue(repo.state.value.items.any { it.id == uid(1) && !it.isUnread })
        assertEquals(2, repo.state.value.unreadCount)
    }

    @Test
    fun refresh_hacePeticionSinFiltro() = runTest {
        val repo = repo()
        client.enqueue(apiOk(body(entry(1))))

        repo.refresh()

        val request = client.requests.single()
        assertEquals("notifications", request.path)
        assertTrue(request.query.isEmpty())
        assertTrue(request.jsonBody == null)
    }

    @Test
    fun refresh_noEnviaParametrosDePaginacion() = runTest {
        val repo = repo()
        client.fallback = { apiOk(body(entry(1))) }

        repo.refresh()
        repo.refreshUnread()

        val keys = client.requests.flatMap { it.query }.map { it.first }
        assertTrue(keys.none { it in setOf("page", "cursor", "limit", "since") })
    }

    @Test
    fun refresh_ordenaPorFechaDescendente() = runTest {
        val repo = repo()
        client.enqueue(apiOk(body(entry(3, ageSeconds = 300), entry(1, ageSeconds = 10), entry(2, ageSeconds = 100))))

        repo.refresh()

        assertEquals(listOf(uid(1), uid(2), uid(3)), repo.state.value.items.map { it.id })
        assertTrue(repo.state.value.listLoaded)
    }

    @Test
    fun refresh_recalculaElContador() = runTest {
        val repo = repo()
        client.enqueue(apiOk(body(entry(1), entry(2, read = true), entry(3))))

        repo.refresh()

        assertEquals(2, repo.state.value.unreadCount)
    }

    @Test
    fun refresh_concurrente_haceUnaSolaPeticion() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(body(entry(1))) }
        val outcomes = mutableListOf<InboxOutcome>()
        launch { outcomes += repo.refresh() }
        launch { outcomes += repo.refresh() }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertEquals(listOf<InboxOutcome>(InboxOutcome.Done, InboxOutcome.Done), outcomes)
    }

    @Test
    fun refresh_cuerpoIlegible_esFailedYConservaElEstado() = runTest {
        val repo = repo()
        repo.load(entry(1))
        client.enqueue(apiOk("<html>"))

        val outcome = repo.refresh()

        assertTrue(outcome is InboxOutcome.Failed && outcome.error is DomainError.Unknown)
        assertEquals(1, repo.state.value.items.size)
    }

    @Test
    fun markRead_hacePatchYMarcaLeidaAlMomento() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk("{}") }

        launch { repo.markRead(uid(1)) }
        runCurrent()

        assertFalse(repo.state.value.items.first { it.id == uid(1) }.isUnread)
        val request = client.requests.single()
        assertEquals(HttpMethod.PATCH, request.method)
        assertEquals("notifications/${uid(1)}/read", request.path)
        gate.complete(Unit)
        runCurrent()
    }

    @Test
    fun markRead_fallo_revierteLaEntrada() = runTest {
        val repo = repo()
        repo.load(entry(1))
        client.enqueue(failure(serverDown()))

        val outcome = repo.markRead(uid(1))

        assertTrue(outcome is InboxOutcome.Failed)
        assertTrue(repo.state.value.items.single().isUnread)
        assertEquals(1, repo.state.value.unreadCount)
    }

    @Test
    fun markRead_yaLeida_noHacePeticion() = runTest {
        val repo = repo()
        repo.load(entry(1, read = true))

        assertEquals(InboxOutcome.Done, repo.markRead(uid(1)))

        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun markRead_conIdNoUuid_fallaSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.markRead("../auth/logout")

        assertTrue(outcome is InboxOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun markRead_decrementaElContador() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))
        client.enqueue(apiOk("{}"))

        repo.markRead(uid(1))

        assertEquals(1, repo.state.value.unreadCount)
    }

    @Test
    fun markAllRead_hacePatchYDejaElContadorEnCero() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2), entry(3, read = true))
        client.enqueue(apiOk("""{"updatedCount":2}"""))

        assertEquals(InboxOutcome.Done, repo.markAllRead())

        assertEquals("notifications/read-all", client.requests.single().path)
        assertEquals(HttpMethod.PATCH, client.requests.single().method)
        assertEquals(0, repo.state.value.unreadCount)
        assertTrue(repo.state.value.items.none { it.isUnread })
    }

    @Test
    fun markAllRead_fallo_restauraLasNoLeidas() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2), entry(3, read = true))
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.markAllRead()

        assertTrue(outcome is InboxOutcome.Failed)
        assertEquals(2, repo.state.value.unreadCount)
        assertEquals(3, repo.state.value.items.size)
    }

    @Test
    fun markAllRead_sinNoLeidas_noHacePeticion() = runTest {
        val repo = repo()
        repo.load(entry(1, read = true))

        repo.markAllRead()

        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun delete_haceDeleteYQuitaLaEntrada() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))
        client.enqueue(apiOk("", status = 204))

        assertEquals(InboxOutcome.Done, repo.delete(uid(1)))

        assertEquals(HttpMethod.DELETE, client.requests.single().method)
        assertEquals("notifications/${uid(1)}", client.requests.single().path)
        assertEquals(listOf(uid(2)), repo.state.value.items.map { it.id })
    }

    @Test
    fun delete_fallo_reinsertaLaEntrada() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2), entry(3))
        client.enqueue(failure(serverDown()))

        repo.delete(uid(2))

        assertEquals(listOf(uid(1), uid(2), uid(3)), repo.state.value.items.map { it.id })
        assertEquals(3, repo.state.value.unreadCount)
    }

    @Test
    fun delete_decrementaElContadorSiEraNoLeida() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2, read = true))
        client.fallback = { apiOk("", status = 204) }

        repo.delete(uid(2))
        assertEquals(1, repo.state.value.unreadCount)
        repo.delete(uid(1))

        assertEquals(0, repo.state.value.unreadCount)
    }

    @Test
    fun deleteRead_hacedeleteYConservaLasNoLeidas() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2, read = true), entry(3, read = true))
        client.enqueue(apiOk("""{"deletedCount":2}"""))

        assertEquals(InboxOutcome.Done, repo.deleteRead())

        assertEquals("notifications/read", client.requests.single().path)
        assertEquals(HttpMethod.DELETE, client.requests.single().method)
        assertEquals(listOf(uid(1)), repo.state.value.items.map { it.id })
    }

    @Test
    fun deleteRead_fallo_restauraLasLeidas() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2, read = true))
        client.enqueue(failure(serverDown()))

        repo.deleteRead()

        assertEquals(listOf(uid(1), uid(2)), repo.state.value.items.map { it.id })
    }

    @Test
    fun markRead_con404_eliminaLaEntradaSinError() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))
        client.enqueue(failure(notFound()))

        val outcome = repo.markRead(uid(1))

        assertEquals(InboxOutcome.Done, outcome)
        assertEquals(listOf(uid(2)), repo.state.value.items.map { it.id })
        assertEquals(1, repo.state.value.unreadCount)
    }

    @Test
    fun delete_con404_esExito() = runTest {
        val repo = repo()
        repo.load(entry(1))
        client.enqueue(failure(notFound()))

        assertEquals(InboxOutcome.Done, repo.delete(uid(1)))

        assertEquals(0, repo.state.value.items.size)
    }

    @Test
    fun con429_devuelveRetryAfter() = runTest {
        val repo = repo()
        repo.load(entry(1))
        client.enqueue(failure(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfter = 90))

        val outcome = repo.delete(uid(1))

        assertEquals(90L, (outcome as InboxOutcome.Failed).retryAfterSeconds)
        assertTrue(outcome.error is DomainError.RateLimited)
    }

    @Test
    fun sinRed_devuelveNetwork() = runTest {
        val repo = repo()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.refresh()

        assertTrue(outcome is InboxOutcome.Failed && outcome.error is DomainError.Network)
    }

    @Test
    fun doblePulsacion_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.load(entry(1))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk("{}") }

        launch { repo.markRead(uid(1)) }
        runCurrent()
        launch { repo.markRead(uid(1)) }
        launch { repo.delete(uid(1)) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
    }

    @Test
    fun refrescoDuranteBorrado_noReinsertaLaEntrada() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk("", status = 204) }
        launch { repo.delete(uid(1)) }
        runCurrent()

        client.enqueue(apiOk(body(entry(1), entry(2))))
        repo.refresh()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(uid(2)), repo.state.value.items.map { it.id })
    }

    @Test
    fun refrescoDuranteLectura_noLaDesmarca() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk("{}") }
        launch { repo.markRead(uid(1)) }
        runCurrent()

        client.enqueue(apiOk(body(entry(1), entry(2))))
        repo.refresh()
        gate.complete(Unit)
        runCurrent()

        assertFalse(repo.state.value.items.first { it.id == uid(1) }.isUnread)
        assertEquals(1, repo.state.value.unreadCount)
    }

    @Test
    fun noReintentaAutomaticamente() = runTest {
        val repo = repo()
        repo.load(entry(1))
        client.enqueue(failure(serverDown()))

        repo.delete(uid(1))
        runCurrent()

        assertEquals(1, client.requests.size)
    }
}
