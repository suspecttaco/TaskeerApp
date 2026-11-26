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
import java.util.concurrent.atomic.AtomicInteger
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
class RemoteWorkspaceMembersRepositoryTest {

    private val client = FakeApiClient()
    private val session = MutableStateFlow<SessionState>(signedIn("m3@example.com"))
    private val ws = uuid(100)

    private fun signedIn(email: String, offline: Boolean = false): SessionState =
        SessionState.SignedIn(AccountSummary(email, "Yo"), offline)

    private fun TestScope.repo() = RemoteWorkspaceMembersRepository(client, session, backgroundScope)

    private fun entry(n: Int, role: String? = "EDITOR", name: String = "Miembro $n"): String {
        val roleJson = role?.let { "\"role\":\"$it\"," }.orEmpty()
        return """{"userId":"${uuid(n)}","email":"m$n@example.com","displayName":"$name",$roleJson"createdAt":"2026-10-01T10:00:00Z"}"""
    }

    private fun listBody(vararg entries: String) = """{"members":[${entries.joinToString(",")}]}"""

    private fun oneBody(n: Int, role: String) = """{"member":${entry(n, role)}}"""

    private fun defaultList() = listBody(entry(1, "OWNER"), entry(2, "ADMIN"), entry(3, "EDITOR"), entry(4, "VIEWER"))

    private suspend fun RemoteWorkspaceMembersRepository.loaded(body: String = defaultList(), workspaceId: String = ws) {
        client.enqueue(apiOk(body))
        assertEquals(WorkspaceOutcome.Done, load(workspaceId))
        client.requests.clear()
    }

    private fun failure(error: DomainError, retryAfter: Long? = null): ApiResult = ApiResult.Failure(error, retryAfter)

    private fun serverDown() = DomainError.Server("INTERNAL_SERVER_ERROR", "x")

    private fun api(code: String) = failure(DomainError.fromApi(code, "x"))

    private fun ids(repo: RemoteWorkspaceMembersRepository) = repo.state.value.items.map { it.userId }

    private fun roleOf(repo: RemoteWorkspaceMembersRepository, n: Int) =
        repo.state.value.items.first { it.userId == uuid(n) }.role

    @Test
    fun sinSesion_ningunaOperacionHacePeticion() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        val outcomes = listOf(
            repo.load(ws),
            repo.changeRole(uuid(3), WorkspaceRole.ADMIN),
            repo.remove(uuid(3)),
            repo.transferOwnership(uuid(3)),
        )

        assertTrue(outcomes.all { it is WorkspaceOutcome.Failed && it.error is DomainError.Unauthenticated })
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
        client.enqueue { gate.await(); apiOk(defaultList()) }
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

        assertEquals(4, repo.state.value.items.size)
    }

    @Test
    fun sesionOffline_permiteIntentar() = runTest {
        session.value = signedIn("m3@example.com", offline = true)
        val repo = repo()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

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
        assertEquals("workspaces/$ws/members", request.path)
        assertTrue(request.query.isEmpty())
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun load_reemplazaLaLista() = runTest {
        val repo = repo()
        repo.loaded(listBody(entry(9, "VIEWER")))

        client.enqueue(apiOk(listBody(entry(4, "VIEWER"), entry(1, "OWNER"))))
        repo.load(ws)

        assertEquals(listOf(uuid(1), uuid(4)), ids(repo))
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
        assertEquals(listOf(uuid(7)), ids(repo))
    }

    @Test
    fun load_concurrente_haceUnaSolaPeticion() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(defaultList()) }
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
        assertEquals(4, repo.state.value.items.size)
    }

    @Test
    fun load_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.load("../x")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun changeRole_hacePatchConElRol() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk(oneBody(4, "ADMIN")))

        repo.changeRole(uuid(4), WorkspaceRole.ADMIN)

        val request = client.requests.single()
        assertEquals(HttpMethod.PATCH, request.method)
        assertEquals("workspaces/$ws/members/${uuid(4)}", request.path)
        assertEquals("ADMIN", JSONObject(request.jsonBody!!).getString("role"))
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun changeRole_200_sustituyeLaEntrada() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk(oneBody(4, "ADMIN")))

        val outcome = repo.changeRole(uuid(4), WorkspaceRole.ADMIN)

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(WorkspaceRole.ADMIN, roleOf(repo, 4))
        assertEquals(listOf(uuid(1), uuid(2), uuid(4), uuid(3)), ids(repo))
    }

    @Test
    fun changeRole_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.loaded()

        client.enqueue(failure(serverDown()))
        val outcome = repo.changeRole(uuid(4), WorkspaceRole.ADMIN)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Server)
        assertEquals(WorkspaceRole.VIEWER, roleOf(repo, 4))
    }

    @Test
    fun changeRole_aOwner_noHacePeticion() = runTest {
        val repo = repo()
        repo.loaded()

        val outcomes = listOf(
            repo.changeRole(uuid(4), WorkspaceRole.OWNER),
            repo.changeRole(uuid(4), WorkspaceRole.UNKNOWN),
        )

        assertTrue(outcomes.all { it is WorkspaceOutcome.Failed && it.error is DomainError.Validation })
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun changeRole_sobreOwner_noHacePeticion() = runTest {
        val repo = repo()
        repo.loaded()

        val outcome = repo.changeRole(uuid(1), WorkspaceRole.EDITOR) as WorkspaceOutcome.Failed

        assertEquals("CANNOT_CHANGE_OWNER_ROLE", outcome.error.code)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun changeRole_mismoRol_noHacePeticion() = runTest {
        val repo = repo()
        repo.loaded()

        val outcome = repo.changeRole(uuid(4), WorkspaceRole.VIEWER)

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun changeRole_403_devuelveForbidden() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("FORBIDDEN"))

        val outcome = repo.changeRole(uuid(4), WorkspaceRole.ADMIN) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Forbidden)
        assertEquals(WorkspaceRole.VIEWER, roleOf(repo, 4))
    }

    @Test
    fun changeRole_conMemberNotFound_quitaAlMiembro() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("MEMBER_NOT_FOUND"))

        val outcome = repo.changeRole(uuid(4), WorkspaceRole.ADMIN) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.NotFound)
        assertEquals(listOf(uuid(1), uuid(2), uuid(3)), ids(repo))
    }

    @Test
    fun changeRole_dobleToque_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(oneBody(4, "ADMIN")) }
        val outcomes = mutableListOf<WorkspaceOutcome>()
        launch { outcomes += repo.changeRole(uuid(4), WorkspaceRole.ADMIN) }
        launch { outcomes += repo.changeRole(uuid(4), WorkspaceRole.ADMIN) }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertEquals(1, outcomes.count { it == WorkspaceOutcome.Done })
    }

    @Test
    fun changeRole_sinListaCargada_noHacePeticion() = runTest {
        val repo = repo()

        val outcome = repo.changeRole(uuid(4), WorkspaceRole.ADMIN) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.NotFound)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun remove_haceDeleteYQuitaAlMiembro() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk("""{"removed":true}"""))

        val outcome = repo.remove(uuid(4))

        val request = client.requests.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals("workspaces/$ws/members/${uuid(4)}", request.path)
        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(listOf(uuid(1), uuid(2), uuid(3)), ids(repo))
    }

    @Test
    fun remove_403_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("FORBIDDEN"))

        val outcome = repo.remove(uuid(4)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Forbidden)
        assertEquals(4, repo.state.value.items.size)
    }

    @Test
    fun remove_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(failure(serverDown()))

        val outcome = repo.remove(uuid(4))

        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertEquals(4, repo.state.value.items.size)
    }

    @Test
    fun remove_conMemberNotFound_esExito() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("MEMBER_NOT_FOUND"))

        val outcome = repo.remove(uuid(4))

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(3, repo.state.value.items.size)
    }

    @Test
    fun remove_aOwner_noHacePeticion() = runTest {
        val repo = repo()
        repo.loaded()

        val outcome = repo.remove(uuid(1))

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun remove_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()
        repo.loaded()

        val outcome = repo.remove("../1")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun leave_hacePeticionConMiUserId() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk("""{"removed":true}"""))

        repo.remove(uuid(3))

        assertEquals("workspaces/$ws/members/${uuid(3)}", client.requests.single().path)
    }

    @Test
    fun leave_200_vaciaLosMiembros() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk("""{"removed":true}"""))

        repo.remove(uuid(3))

        assertEquals(0, repo.state.value.items.size)
        assertNull(repo.state.value.workspaceId)
        assertFalse(repo.state.value.loaded)
    }

    @Test
    fun leave_ownerObsoleto_devuelveValidationConCodigo() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("OWNER_MUST_TRANSFER_OWNERSHIP"))

        val outcome = repo.remove(uuid(3)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Validation)
        assertEquals("OWNER_MUST_TRANSFER_OWNERSHIP", outcome.error.code)
        assertEquals(4, repo.state.value.items.size)
    }

    @Test
    fun transfer_hacePostConElNuevoOwner() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk("""{"workspace":{}}"""))

        val outcome = repo.transferOwnership(uuid(2))

        val request = client.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("workspaces/$ws/transfer-ownership", request.path)
        assertEquals(uuid(2), JSONObject(request.jsonBody!!).getString("newOwnerUserId"))
        assertEquals(AuthMode.BEARER, request.auth)
        assertEquals(WorkspaceOutcome.Done, outcome)
    }

    @Test
    fun transfer_200_noDeduceRoles() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(apiOk("""{"workspace":{}}"""))

        repo.transferOwnership(uuid(2))

        assertEquals(WorkspaceRole.OWNER, roleOf(repo, 1))
        assertEquals(WorkspaceRole.ADMIN, roleOf(repo, 2))
    }

    @Test
    fun transfer_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(failure(serverDown()))

        val outcome = repo.transferOwnership(uuid(2))

        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertEquals(WorkspaceRole.OWNER, roleOf(repo, 1))
    }

    @Test
    fun transfer_aOwner_noHacePeticion() = runTest {
        val repo = repo()
        repo.loaded()

        val outcome = repo.transferOwnership(uuid(1))

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun transfer_403_devuelveForbidden() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("FORBIDDEN"))

        val outcome = repo.transferOwnership(uuid(2)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Forbidden)
    }

    @Test
    fun transfer_422_devuelveValidationConCodigo() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(api("USER_NOT_WORKSPACE_MEMBER"))

        val outcome = repo.transferOwnership(uuid(2)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Validation)
        assertEquals("USER_NOT_WORKSPACE_MEMBER", outcome.error.code)
    }

    @Test
    fun transfer_dobleToque_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk("""{"workspace":{}}""") }
        val outcomes = mutableListOf<WorkspaceOutcome>()
        launch { outcomes += repo.transferOwnership(uuid(2)) }
        launch { outcomes += repo.transferOwnership(uuid(2)) }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertEquals(1, outcomes.count { it == WorkspaceOutcome.Done })
    }

    @Test
    fun con429_devuelveRetryAfter() = runTest {
        val repo = repo()
        client.enqueue(failure(DomainError.fromApi("TOO_MANY_REQUESTS", "x"), retryAfter = 90))

        val outcome = repo.load(ws) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(90L, outcome.retryAfterSeconds)
    }

    @Test
    fun sinRed_devuelveNetwork() = runTest {
        val repo = repo()
        repo.loaded()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.remove(uuid(4)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Network)
    }

    @Test
    fun noReintentaAutomaticamente() = runTest {
        val repo = repo()
        repo.loaded()
        val calls = AtomicInteger()
        client.fallback = { calls.incrementAndGet(); failure(serverDown()) }

        repo.load(ws)
        repo.changeRole(uuid(4), WorkspaceRole.ADMIN)
        repo.remove(uuid(4))
        repo.transferOwnership(uuid(2))
        runCurrent()

        assertEquals(4, calls.get())
    }

    @Test
    fun refrescoDuranteExpulsion_noReinsertaAlMiembro() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(defaultList()) }
        launch { repo.load(ws) }
        runCurrent()
        client.enqueue(apiOk("""{"removed":true}"""))
        repo.remove(uuid(4))

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(uuid(1), uuid(2), uuid(3)), ids(repo))
    }

    @Test
    fun refrescoDuranteCambioDeRol_noRevierteElRol() = runTest {
        val repo = repo()
        repo.loaded()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(defaultList()) }
        launch { repo.load(ws) }
        runCurrent()
        client.enqueue(apiOk(oneBody(4, "ADMIN")))
        repo.changeRole(uuid(4), WorkspaceRole.ADMIN)

        gate.complete(Unit)
        runCurrent()

        assertEquals(WorkspaceRole.ADMIN, roleOf(repo, 4))
    }
}
