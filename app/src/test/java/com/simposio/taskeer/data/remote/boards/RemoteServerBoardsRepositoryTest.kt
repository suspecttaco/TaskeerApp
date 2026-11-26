package com.simposio.taskeer.data.remote.boards

import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.testing.FakeApiClient
import com.simposio.taskeer.testing.apiOk
import com.simposio.taskeer.testing.uuid
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteServerBoardsRepositoryTest {

    private val client = FakeApiClient()
    private val session = MutableStateFlow<SessionState>(signedIn("yo@example.com"))
    private val ws = uuid(100)

    private fun signedIn(email: String, offline: Boolean = false): SessionState =
        SessionState.SignedIn(AccountSummary(email, "Yo"), offline)

    private fun TestScope.repo() = RemoteServerBoardsRepository(client, session, backgroundScope)

    private fun entry(n: Int, name: String = "Tablero $n") = """{"id":"${uuid(n)}","workspaceId":"$ws","name":"$name"}"""

    private fun listBody(vararg entries: String) = """{"boards":[${entries.joinToString(",")}]}"""

    private fun names(repo: RemoteServerBoardsRepository) = repo.state.value.items.map { it.name }

    private suspend fun RemoteServerBoardsRepository.loaded(body: String = listBody(entry(1), entry(2)), workspaceId: String = ws) {
        client.enqueue(apiOk(body))
        assertEquals(WorkspaceOutcome.Done, load(workspaceId))
        client.requests.clear()
    }

    @Test
    fun sinSesion_ningunaOperacionHacePeticion() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        val outcome = repo.load(ws)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Unauthenticated)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun signedOut_dejaElEstadoVacio() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        assertEquals(emptyList<Any>(), repo.state.value.items)
        assertFalse(repo.state.value.loaded)
        assertNull(repo.state.value.workspaceId)
    }

    @Test
    fun cerrarSesion_vaciaElEstado() = runTest {
        val repo = repo()
        repo.loaded()

        session.value = SessionState.SignedOut(expired = true)
        runCurrent()

        assertEquals(0, repo.state.value.items.size)
        assertFalse(repo.state.value.loaded)
        assertNull(repo.state.value.workspaceId)
    }

    @Test
    fun respuestaTardiaTrasCerrarSesion_seDescarta() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1))) }
        var outcome: WorkspaceOutcome? = null
        launch { outcome = repo.load(ws) }
        runCurrent()

        session.value = SessionState.SignedOut()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertEquals(0, repo.state.value.items.size)
    }

    @Test
    fun cambioDeCuenta_noMuestraLoDeLaAnterior() = runTest {
        val repo = repo()
        repo.loaded()

        session.value = signedIn("otra@example.com")
        runCurrent()

        assertEquals(0, repo.state.value.items.size)
    }

    @Test
    fun resuming_conSesionPrevia_noVaciaElEstado() = runTest {
        val repo = repo()
        repo.loaded()

        session.value = SessionState.Resuming
        runCurrent()

        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun sesionOffline_permiteIntentar() = runTest {
        session.value = signedIn("yo@example.com", offline = true)
        val repo = repo()
        client.enqueue(ApiResult.Failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.load(ws) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Network)
        assertEquals(1, client.requests.size)
    }

    @Test
    fun load_hacePeticionSinParametros() = runTest {
        val repo = repo()
        client.enqueue(apiOk(listBody()))

        repo.load(ws)

        val request = client.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("workspaces/$ws/boards", request.path)
        assertTrue(request.query.isEmpty())
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun load_reemplazaLaListaOrdenadaPorNombre() = runTest {
        val repo = repo()
        repo.loaded(listBody(entry(9, "Viejo")))

        client.enqueue(apiOk(listBody(entry(4, "beta"), entry(1, "Alfa"))))
        repo.load(ws)

        assertEquals(listOf("Alfa", "beta"), names(repo))
        assertTrue(repo.state.value.loaded)
        assertEquals(ws, repo.state.value.workspaceId)
    }

    @Test
    fun load_reemplazaLaLista() = runTest {
        val repo = repo()
        repo.loaded(listBody(entry(9)))

        client.enqueue(apiOk(listBody(entry(4))))
        repo.load(ws)

        assertEquals(listOf(uuid(4)), repo.state.value.items.map { it.id })
    }

    @Test
    fun load_otroWorkspace_vaciaLaListaAnterior() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(7))) }

        launch { repo.load(uuid(200)) }
        runCurrent()

        assertEquals(0, repo.state.value.items.size)
        assertFalse(repo.state.value.loaded)
        assertEquals(uuid(200), repo.state.value.workspaceId)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(uuid(7)), repo.state.value.items.map { it.id })
    }

    @Test
    fun load_concurrente_haceUnaSolaPeticion() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1))) }
        val outcomes = mutableListOf<WorkspaceOutcome>()
        launch { outcomes += repo.load(ws) }
        launch { outcomes += repo.load(ws) }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertEquals(listOf(WorkspaceOutcome.Done, WorkspaceOutcome.Done), outcomes)
    }

    @Test
    fun load_cuerpoIlegible_esFailedYConservaElEstado() = runTest {
        val repo = repo()
        repo.loaded()

        client.enqueue(apiOk("<html>"))
        val outcome = repo.load(ws)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Unknown)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun load_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.load("../x")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun con429_devuelveRetryAfter() = runTest {
        val repo = repo()
        client.enqueue(ApiResult.Failure(DomainError.fromApi("TOO_MANY_REQUESTS", "x"), 90))

        val outcome = repo.load(ws) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(90L, outcome.retryAfterSeconds)
    }

    @Test
    fun noReintentaAutomaticamente() = runTest {
        val repo = repo()
        val calls = AtomicInteger()
        client.fallback = { calls.incrementAndGet(); ApiResult.Failure(DomainError.Server("INTERNAL_SERVER_ERROR", "x")) }

        repo.load(ws)
        runCurrent()

        assertEquals(1, calls.get())
    }
}
