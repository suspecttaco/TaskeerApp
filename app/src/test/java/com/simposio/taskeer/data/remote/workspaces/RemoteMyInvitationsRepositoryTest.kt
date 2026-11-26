package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.AcceptOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.testing.FakeApiClient
import com.simposio.taskeer.testing.apiOk
import com.simposio.taskeer.testing.uuid
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
class RemoteMyInvitationsRepositoryTest {

    private val client = FakeApiClient()
    private val session = MutableStateFlow<SessionState>(signedIn("yo@example.com"))

    private fun signedIn(email: String): SessionState = SessionState.SignedIn(AccountSummary(email, "Yo"), offline = false)

    private fun TestScope.repo() = RemoteMyInvitationsRepository(client, session, backgroundScope)

    private fun entry(n: Int) =
        """{"id":"${uuid(n)}","workspaceId":"${uuid(100 + n)}","workspaceName":"Equipo $n","role":"EDITOR","status":"PENDING","expiresAt":"2026-12-20T10:00:00Z","createdAt":"2026-11-2${n % 10}T10:00:00Z"}"""

    private fun listBody(vararg entries: String) = """{"invitations":[${entries.joinToString(",")}]}"""

    private fun acceptedBody(name: String = "Equipo 1", already: Boolean = false) =
        """{"workspace":{"name":"$name"},"alreadyMember":$already}"""

    private suspend fun RemoteMyInvitationsRepository.loaded(body: String = listBody(entry(1), entry(2))) {
        client.enqueue(apiOk(body))
        assertEquals(WorkspaceOutcome.Done, refresh())
        client.requests.clear()
    }

    private fun failure(error: DomainError): ApiResult = ApiResult.Failure(error)

    private fun api(code: String) = failure(DomainError.fromApi(code, "x"))

    private fun ids(repo: RemoteMyInvitationsRepository) = repo.state.value.items.map { it.id }

    @Test
    fun sinSesion_ningunaOperacionHacePeticion() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        val refresh = repo.refresh()
        val accept = repo.accept(uuid(1))
        val withToken = repo.acceptWithToken("abc")

        assertTrue(refresh is WorkspaceOutcome.Failed)
        assertTrue(accept is AcceptOutcome.Failed && accept.error is DomainError.Unauthenticated)
        assertTrue(withToken is AcceptOutcome.Failed)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun signedOut_dejaElEstadoVacio() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        assertEquals(0, repo.state.value.items.size)
        assertFalse(repo.state.value.loaded)
    }

    @Test
    fun cerrarSesion_vaciaElEstado() = runTest {
        val repo = repo()
        repo.loaded()

        session.value = SessionState.SignedOut(expired = true)
        runCurrent()

        assertEquals(0, repo.state.value.items.size)
        assertFalse(repo.state.value.loaded)
    }

    @Test
    fun respuestaTardiaTrasCerrarSesion_seDescarta() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1))) }
        var outcome: WorkspaceOutcome? = null
        launch { outcome = repo.refresh() }
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
    fun refresh_hacePeticionSinParametros() = runTest {
        val repo = repo()
        client.enqueue(apiOk(listBody()))

        repo.refresh()

        val request = client.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("invitations/mine", request.path)
        assertTrue(request.query.isEmpty())
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun refresh_reemplazaLaLista() = runTest {
        val repo = repo()
        repo.loaded(listBody(entry(9)))

        client.enqueue(apiOk(listBody(entry(3), entry(4))))
        repo.refresh()

        assertEquals(setOf(uuid(3), uuid(4)), ids(repo).toSet())
        assertTrue(repo.state.value.loaded)
    }

    @Test
    fun refresh_concurrente_haceUnaSolaPeticion() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1))) }

        launch { repo.refresh() }
        launch { repo.refresh() }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
    }

    @Test
    fun refresh_cuerpoIlegible_esFailedYConservaElEstado() = runTest {
        val repo = repo()
        repo.loaded()

        client.enqueue(apiOk("basura"))
        val outcome = repo.refresh()

        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun accept_hacePostSinCuerpo() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk(acceptedBody()))

        repo.accept(uuid(1))

        val request = client.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("invitations/${uuid(1)}/accept", request.path)
        assertNull(request.jsonBody)
        assertTrue(request.query.isEmpty())
    }

    @Test
    fun accept_200_quitaLaInvitacionDeLaBandeja() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk(acceptedBody("Diseño")))

        val outcome = repo.accept(uuid(1)) as AcceptOutcome.Accepted

        assertEquals("Diseño", outcome.workspaceName)
        assertEquals(listOf(uuid(2)), ids(repo))
    }

    @Test
    fun accept_200_devuelveAlreadyMember() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk(acceptedBody(already = true)))

        val outcome = repo.accept(uuid(1)) as AcceptOutcome.Accepted

        assertTrue(outcome.alreadyMember)
    }

    @Test
    fun accept_fallo_noCambiaLaBandeja() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(failure(DomainError.Server("INTERNAL_SERVER_ERROR", "x")))

        val outcome = repo.accept(uuid(1))

        assertTrue(outcome is AcceptOutcome.Failed)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun accept_dobleToque_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(acceptedBody()) }
        var second: AcceptOutcome? = null

        launch { repo.accept(uuid(1)) }
        runCurrent()
        launch { second = repo.accept(uuid(1)) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertTrue(second is AcceptOutcome.Failed)
    }

    @Test
    fun accept_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.accept("../x")

        assertTrue(outcome is AcceptOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun accept_400Invalida_devuelveValidationConCodigo() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("INVALID_OR_EXPIRED_INVITATION"))

        val outcome = repo.accept(uuid(1)) as AcceptOutcome.Failed

        val error = outcome.error as DomainError.Validation
        assertEquals("INVALID_OR_EXPIRED_INVITATION", error.code)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun accept_404_devuelveNotFound() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("INVITATION_NOT_FOUND"))

        val outcome = repo.accept(uuid(1)) as AcceptOutcome.Failed

        assertTrue(outcome.error is DomainError.NotFound)
    }

    @Test
    fun acceptWithToken_hacePostConTokenEnLaQuery() = runTest {
        val repo = repo()
        client.enqueue(apiOk(acceptedBody()))

        repo.acceptWithToken("tok123")

        val request = client.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("invitations/accept", request.path)
        assertEquals(listOf("token" to "tok123"), request.query)
        assertNull(request.jsonBody)
        assertFalse(request.path.contains("tok123"))
        assertFalse(request.toString().contains("tok123"))
    }

    @Test
    fun acceptWithToken_200_devuelveAccepted() = runTest {
        val repo = repo()
        client.enqueue(apiOk(acceptedBody("Diseño", already = true)))

        val outcome = repo.acceptWithToken("tok") as AcceptOutcome.Accepted

        assertEquals("Diseño", outcome.workspaceName)
        assertTrue(outcome.alreadyMember)
    }

    @Test
    fun acceptWithToken_fallo_noCambiaLaBandeja() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.acceptWithToken("tok")

        assertTrue(outcome is AcceptOutcome.Failed)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun acceptWithToken_400Invalida_noCambiaLaBandeja() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("INVALID_OR_EXPIRED_INVITATION"))

        val outcome = repo.acceptWithToken("tok") as AcceptOutcome.Failed

        assertTrue(outcome.error is DomainError.Validation)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun acceptWithToken_sinSesion_noHacePeticion() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        val outcome = repo.acceptWithToken("tok")

        assertTrue(outcome is AcceptOutcome.Failed)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun acceptWithToken_tokenVacio_noHacePeticion() = runTest {
        val repo = repo()

        val blank = repo.acceptWithToken("  ")
        val spaced = repo.acceptWithToken("a b")

        assertTrue(blank is AcceptOutcome.Failed && blank.error is DomainError.Validation)
        assertTrue(spaced is AcceptOutcome.Failed)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun refrescoDuranteAceptacion_noReinsertaLaInvitacion() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1), entry(2))) }
        launch { repo.refresh() }
        runCurrent()

        client.enqueue(apiOk(acceptedBody()))
        repo.accept(uuid(1))
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(uuid(2)), ids(repo))
    }
}
