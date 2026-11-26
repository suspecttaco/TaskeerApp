package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteWorkspaceInvitationsRepositoryTest {

    private val client = FakeApiClient()
    private val session = MutableStateFlow<SessionState>(signedIn("m1@example.com"))
    private val ws = uuid(100)

    private fun signedIn(email: String, offline: Boolean = false): SessionState =
        SessionState.SignedIn(AccountSummary(email, "Yo"), offline)

    private fun TestScope.repo() = RemoteWorkspaceInvitationsRepository(client, session, backgroundScope)

    private fun entry(n: Int, role: String = "EDITOR", status: String = "PENDING") =
        """{"id":"${uuid(n)}","workspaceId":"$ws","email":"i$n@example.com","role":"$role","status":"$status","invitedById":"${uuid(1)}","expiresAt":"2026-12-20T10:00:00Z","createdAt":"2026-11-2${n % 10}T10:00:00Z"}"""

    private fun listBody(vararg entries: String) = """{"invitations":[${entries.joinToString(",")}]}"""

    private fun oneBody(n: Int) = """{"invitation":${entry(n)}}"""

    private suspend fun RemoteWorkspaceInvitationsRepository.loaded(body: String = listBody(entry(1), entry(2))) {
        client.enqueue(apiOk(body))
        assertEquals(WorkspaceOutcome.Done, load(ws))
        client.requests.clear()
    }

    private fun failure(error: DomainError, retryAfter: Long? = null): ApiResult = ApiResult.Failure(error, retryAfter)

    private fun api(code: String) = failure(DomainError.fromApi(code, "x"))

    private fun ids(repo: RemoteWorkspaceInvitationsRepository) = repo.state.value.items.map { it.id }

    @Test
    fun sinSesion_ningunaOperacionHacePeticion() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        val outcomes = listOf(repo.load(ws), repo.invite("a@example.com", WorkspaceRole.VIEWER), repo.revoke(uuid(1)))

        assertTrue(outcomes.all { it is WorkspaceOutcome.Failed && it.error is DomainError.Unauthenticated })
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun signedOut_dejaElEstadoVacio() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        assertEquals(0, repo.state.value.items.size)
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

        session.value = signedIn("luis@example.com")
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
    fun load_hacePeticionSinParametros() = runTest {
        val repo = repo()
        client.enqueue(apiOk(listBody()))

        repo.load(ws)

        val request = client.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("workspaces/$ws/invitations", request.path)
        assertTrue(request.query.isEmpty())
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun load_reemplazaLaLista() = runTest {
        val repo = repo()
        repo.loaded(listBody(entry(9)))

        client.enqueue(apiOk(listBody(entry(3), entry(4))))
        repo.load(ws)

        assertEquals(setOf(uuid(3), uuid(4)), ids(repo).toSet())
        assertTrue(repo.state.value.loaded)
        assertEquals(ws, repo.state.value.workspaceId)
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
    }

    @Test
    fun load_concurrente_haceUnaSolaPeticion() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1))) }

        val first = launch { repo.load(ws) }
        val second = launch { repo.load(ws) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertTrue(first.isCompleted && second.isCompleted)
        assertEquals(1, client.requests.size)
    }

    @Test
    fun load_cuerpoIlegible_esFailedYConservaElEstado() = runTest {
        val repo = repo()
        repo.loaded()

        client.enqueue(apiOk("basura"))
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
    fun invite_hacePostConCorreoYRol() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk(oneBody(5), status = 201))

        repo.invite("Nueva@Example.com", WorkspaceRole.ADMIN)

        val request = client.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("workspaces/$ws/invitations", request.path)
        assertEquals(AuthMode.BEARER, request.auth)
        val body = JSONObject(request.jsonBody!!)
        assertEquals("Nueva@Example.com", body.getString("email"))
        assertEquals("ADMIN", body.getString("role"))
    }

    @Test
    fun invite_201_anadeLaInvitacion() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk(oneBody(5), status = 201))

        val outcome = repo.invite("i5@example.com", WorkspaceRole.VIEWER)

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertTrue(uuid(5) in ids(repo))
        assertEquals(3, repo.state.value.items.size)
        assertEquals(0, client.requests.count { it.method == HttpMethod.GET })
    }

    @Test
    fun invite_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("VALIDATION_ERROR"))

        val outcome = repo.invite("a@example.com", WorkspaceRole.VIEWER)

        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun invite_rolOwner_noHacePeticion() = runTest {
        val repo = repo()
        repo.loaded()

        val outcome = repo.invite("a@example.com", WorkspaceRole.OWNER)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun invite_dobleToque_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(oneBody(5), status = 201) }
        var second: WorkspaceOutcome? = null

        launch { repo.invite("a@example.com", WorkspaceRole.VIEWER) }
        runCurrent()
        launch { second = repo.invite("a@example.com", WorkspaceRole.VIEWER) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertTrue(second is WorkspaceOutcome.Failed && (second as WorkspaceOutcome.Failed).error is DomainError.Conflict)
    }

    @Test
    fun invite_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()

        val rejected = repo.load("no-uuid")
        val outcome = repo.invite("a@example.com", WorkspaceRole.VIEWER)

        assertTrue(rejected is WorkspaceOutcome.Failed && rejected.error is DomainError.Validation)
        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun invite_403EmailNotVerified_devuelveForbiddenConCodigo() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("EMAIL_NOT_VERIFIED"))

        val outcome = repo.invite("a@example.com", WorkspaceRole.VIEWER) as WorkspaceOutcome.Failed

        val error = outcome.error as DomainError.Forbidden
        assertEquals("EMAIL_NOT_VERIFIED", error.code)
    }

    @Test
    fun revoke_haceDeleteYQuitaLaInvitacion() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk("""{"revoked":true}"""))

        val outcome = repo.revoke(uuid(1))

        assertEquals(WorkspaceOutcome.Done, outcome)
        val request = client.requests.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals("workspaces/$ws/invitations/${uuid(1)}", request.path)
        assertEquals(listOf(uuid(2)), ids(repo))
    }

    @Test
    fun revoke_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(failure(DomainError.Server("INTERNAL_SERVER_ERROR", "x")))

        val outcome = repo.revoke(uuid(1))

        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun revoke_conInvitationNotFound_esExito() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("INVITATION_NOT_FOUND"))

        val outcome = repo.revoke(uuid(1))

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(listOf(uuid(2)), ids(repo))
    }

    @Test
    fun revoke_conWorkspaceNotFound_esFallo() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("WORKSPACE_NOT_FOUND"))

        val outcome = repo.revoke(uuid(1))

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.NotFound)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun revoke_403_devuelveForbidden() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("FORBIDDEN"))

        val outcome = repo.revoke(uuid(1)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Forbidden)
        assertEquals(2, repo.state.value.items.size)
    }

    @Test
    fun revoke_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()
        repo.loaded()

        val outcome = repo.revoke("../x")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun revoke_dobleToque_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk("""{"revoked":true}""") }
        var second: WorkspaceOutcome? = null

        launch { repo.revoke(uuid(1)) }
        runCurrent()
        launch { second = repo.revoke(uuid(1)) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertTrue(second is WorkspaceOutcome.Failed)
    }

    @Test
    fun con429_devuelveRetryAfter() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(failure(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfter = 120))

        val outcome = repo.invite("a@example.com", WorkspaceRole.VIEWER) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(120L, outcome.retryAfterSeconds)
    }

    @Test
    fun sinRed_devuelveNetwork() = runTest {
        val repo = repo()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.load(ws) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Network)
    }

    @Test
    fun sesionOffline_permiteIntentar() = runTest {
        session.value = signedIn("m1@example.com", offline = true)
        val repo = repo()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        repo.load(ws)

        assertEquals(1, client.requests.size)
    }

    @Test
    fun noReintentaAutomaticamente() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(failure(DomainError.Server("INTERNAL_SERVER_ERROR", "x")))

        repo.invite("a@example.com", WorkspaceRole.VIEWER)
        runCurrent()

        assertEquals(1, client.requests.size)
    }

    @Test
    fun refrescoDuranteRevocacion_noReinsertaLaInvitacion() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1), entry(2))) }
        launch { repo.load(ws) }
        runCurrent()

        client.enqueue(apiOk("""{"revoked":true}"""))
        repo.revoke(uuid(1))
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(uuid(2)), ids(repo))
    }

    @Test
    fun refrescoDuranteInvitacion_noPierdeLaNueva() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1), entry(2))) }
        launch { repo.load(ws) }
        runCurrent()

        client.enqueue(apiOk(oneBody(5), status = 201))
        repo.invite("i5@example.com", WorkspaceRole.VIEWER)
        gate.complete(Unit)
        runCurrent()

        assertTrue(uuid(5) in ids(repo))
        assertEquals(3, repo.state.value.items.size)
    }
}
