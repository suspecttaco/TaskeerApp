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
class RemoteWorkspaceRepositoryTest {

    private val client = FakeApiClient()
    private val session = MutableStateFlow<SessionState>(signedIn("ana@example.com"))

    private fun signedIn(email: String, offline: Boolean = false): SessionState =
        SessionState.SignedIn(AccountSummary(email, "Ana"), offline)

    private fun TestScope.repo() = RemoteWorkspaceRepository(client, session, backgroundScope)

    private fun entry(n: Int, name: String = "Espacio $n", role: String? = "OWNER", personal: Boolean = false): String {
        val roleJson = role?.let { "\"myRole\":\"$it\"," }.orEmpty()
        return """{"id":"${uuid(n)}","name":"$name","slug":"espacio-$n","ownerId":"u1",$roleJson"isPersonal":$personal,"createdAt":"2026-10-01T10:00:00Z","updatedAt":"2026-11-01T10:00:00Z"}"""
    }

    private fun listBody(vararg entries: String) = """{"workspaces":[${entries.joinToString(",")}]}"""

    private fun oneBody(n: Int, name: String) = """{"workspace":${entry(n, name, role = null)}}"""

    private suspend fun RemoteWorkspaceRepository.load(vararg entries: String) {
        client.enqueue(apiOk(listBody(*entries)))
        assertEquals(WorkspaceOutcome.Done, refresh())
        client.requests.clear()
    }

    private fun failure(error: DomainError, retryAfter: Long? = null): ApiResult = ApiResult.Failure(error, retryAfter)

    private fun serverDown() = DomainError.Server("INTERNAL_SERVER_ERROR", "x")

    private fun names(repo: RemoteWorkspaceRepository) = repo.state.value.items.map { it.name }

    @Test
    fun sinSesion_ningunaOperacionHacePeticion() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        val outcomes = listOf(repo.refresh(), repo.create("Nuevo"), repo.rename(uuid(1), "Otro"), repo.delete(uuid(1)))

        assertTrue(outcomes.all { it is WorkspaceOutcome.Failed && it.error is DomainError.Unauthenticated })
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun signedOut_dejaElEstadoVacio() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        assertEquals(emptyList<Any>(), repo.state.value.items)
        assertFalse(repo.state.value.loaded)
    }

    @Test
    fun cerrarSesion_vaciaElEstado() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))

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
    fun refresh_hacePeticionSinParametros() = runTest {
        val repo = repo()
        client.enqueue(apiOk(listBody()))

        repo.refresh()

        val request = client.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("workspaces", request.path)
        assertTrue(request.query.isEmpty())
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun refresh_reemplazaLaLista() = runTest {
        val repo = repo()
        repo.load(entry(1, "Viejo"))

        client.enqueue(apiOk(listBody(entry(3, "beta"), entry(2, "Alfa", personal = true))))
        repo.refresh()

        assertEquals(listOf("Alfa", "beta"), names(repo))
        assertTrue(repo.state.value.loaded)
    }

    @Test
    fun refresh_concurrente_haceUnaSolaPeticion() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1))) }
        val outcomes = mutableListOf<WorkspaceOutcome>()
        launch { outcomes += repo.refresh() }
        launch { outcomes += repo.refresh() }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertEquals(listOf(WorkspaceOutcome.Done, WorkspaceOutcome.Done), outcomes)
    }

    @Test
    fun refresh_cuerpoIlegible_esFailedYConservaElEstado() = runTest {
        val repo = repo()
        repo.load(entry(1))

        client.enqueue(apiOk("<html>"))
        val outcome = repo.refresh()

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Unknown)
        assertEquals(listOf("Espacio 1"), names(repo))
    }

    @Test
    fun create_hacePostConElNombreRecortado() = runTest {
        val repo = repo()
        client.enqueue(apiOk(oneBody(1, "Nuevo"), status = 201))

        repo.create("  Nuevo  ")

        val request = client.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("workspaces", request.path)
        assertEquals("Nuevo", JSONObject(request.jsonBody!!).getString("name"))
    }

    @Test
    fun create_201_anadeConRolOwner() = runTest {
        val repo = repo()
        client.enqueue(apiOk(oneBody(1, "Nuevo"), status = 201))

        val outcome = repo.create("Nuevo")

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(WorkspaceRole.OWNER, repo.state.value.items.single().myRole)
        assertFalse(repo.state.value.items.single().isPersonal)
    }

    @Test
    fun create_nombreInvalido_noHacePeticion() = runTest {
        val repo = repo()

        val outcomes = listOf(repo.create("a"), repo.create("   "), repo.create("a".repeat(151)))

        assertTrue(outcomes.all { it is WorkspaceOutcome.Failed && it.error is DomainError.Validation })
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun create_doblePulsacion_hacePeticionUnica() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(oneBody(1, "Nuevo"), status = 201) }
        val outcomes = mutableListOf<WorkspaceOutcome>()
        launch { outcomes += repo.create("Nuevo") }
        launch { outcomes += repo.create("Nuevo") }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertEquals(1, outcomes.count { it == WorkspaceOutcome.Done })
        assertEquals(1, repo.state.value.items.size)
    }

    @Test
    fun create_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.load(entry(1))

        client.enqueue(failure(serverDown()))
        val outcome = repo.create("Nuevo")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Server)
        assertEquals(listOf("Espacio 1"), names(repo))
    }

    @Test
    fun create_limiteAlcanzado_devuelveForbiddenConCodigo() = runTest {
        val repo = repo()
        client.enqueue(failure(DomainError.fromApi("WORKSPACE_LIMIT_REACHED", "x")))

        val outcome = repo.create("Nuevo") as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Forbidden)
        assertEquals("WORKSPACE_LIMIT_REACHED", outcome.error.code)
    }

    @Test
    fun create_limiteAlcanzado_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.load(entry(1))

        client.enqueue(failure(DomainError.fromApi("WORKSPACE_LIMIT_REACHED", "x")))
        repo.create("Nuevo")

        assertEquals(listOf("Espacio 1"), names(repo))
    }

    @Test
    fun rename_hacePatchSoloConName() = runTest {
        val repo = repo()
        repo.load(entry(1))
        client.enqueue(apiOk(oneBody(1, "Renombrado")))

        repo.rename(uuid(1), " Renombrado ")

        val request = client.requests.single()
        assertEquals(HttpMethod.PATCH, request.method)
        assertEquals("workspaces/${uuid(1)}", request.path)
        val body = JSONObject(request.jsonBody!!)
        assertEquals("Renombrado", body.getString("name"))
        assertEquals(1, body.length())
    }

    @Test
    fun rename_200_actualizaNombreYConservaElRol() = runTest {
        val repo = repo()
        repo.load(entry(1, role = "ADMIN"))
        client.enqueue(apiOk(oneBody(1, "Renombrado")))

        val outcome = repo.rename(uuid(1), "Renombrado")

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals("Renombrado", repo.state.value.items.single().name)
        assertEquals(WorkspaceRole.ADMIN, repo.state.value.items.single().myRole)
    }

    @Test
    fun rename_mismoNombre_noHacePeticion() = runTest {
        val repo = repo()
        repo.load(entry(1, "Equipo"))

        val outcome = repo.rename(uuid(1), "  Equipo ")

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun rename_nombreInvalido_noHacePeticion() = runTest {
        val repo = repo()
        repo.load(entry(1))

        val outcome = repo.rename(uuid(1), "x")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun rename_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.load(entry(1))

        client.enqueue(failure(serverDown()))
        repo.rename(uuid(1), "Otro nombre")

        assertEquals(listOf("Espacio 1"), names(repo))
    }

    @Test
    fun rename_sinRolAdecuado_noHacePeticion() = runTest {
        val repo = repo()
        repo.load(entry(1, role = "EDITOR"), entry(2, role = "VIEWER"), entry(3, role = null))

        val outcomes = listOf(1, 2, 3).map { repo.rename(uuid(it), "Otro nombre") }

        assertTrue(outcomes.all { it is WorkspaceOutcome.Failed && it.error is DomainError.Forbidden })
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun rename_con404_quitaElWorkspaceYAvisa() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))

        client.enqueue(failure(DomainError.fromApi("WORKSPACE_NOT_FOUND", "x")))
        val outcome = repo.rename(uuid(1), "Otro nombre")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.NotFound)
        assertEquals(listOf("Espacio 2"), names(repo))
    }

    @Test
    fun delete_haceDeleteYQuitaElWorkspace() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))
        client.enqueue(apiOk("""{"deleted":true}"""))

        val outcome = repo.delete(uuid(1))

        assertEquals(WorkspaceOutcome.Done, outcome)
        val request = client.requests.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals("workspaces/${uuid(1)}", request.path)
        assertNull(request.jsonBody)
        assertEquals(listOf("Espacio 2"), names(repo))
    }

    @Test
    fun delete_403_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.load(entry(1))

        client.enqueue(failure(DomainError.fromApi("FORBIDDEN", "x")))
        val outcome = repo.delete(uuid(1)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Forbidden)
        assertEquals(listOf("Espacio 1"), names(repo))
    }

    @Test
    fun delete_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.load(entry(1))

        client.enqueue(failure(serverDown()))
        repo.delete(uuid(1))

        assertEquals(listOf("Espacio 1"), names(repo))
    }

    @Test
    fun delete_con404_esExito() = runTest {
        val repo = repo()
        repo.load(entry(1))

        client.enqueue(failure(DomainError.fromApi("WORKSPACE_NOT_FOUND", "x")))
        val outcome = repo.delete(uuid(1))

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(0, repo.state.value.items.size)
    }

    @Test
    fun delete_sinSerOwner_noHacePeticion() = runTest {
        val repo = repo()
        repo.load(entry(1, role = "ADMIN"), entry(2, role = "EDITOR"), entry(3, role = "VIEWER"), entry(4, role = null))

        val outcomes = listOf(1, 2, 3, 4).map { repo.delete(uuid(it)) }

        assertTrue(outcomes.all { it is WorkspaceOutcome.Failed && it.error is DomainError.Forbidden })
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun delete_personal_noHacePeticion() = runTest {
        val repo = repo()
        repo.load(entry(1, personal = true))

        val outcome = repo.delete(uuid(1))

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Forbidden)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun delete_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.delete("../me")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun con429_devuelveRetryAfter() = runTest {
        val repo = repo()
        client.enqueue(failure(DomainError.fromApi("TOO_MANY_REQUESTS", "x"), retryAfter = 90))

        val outcome = repo.refresh() as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(90L, outcome.retryAfterSeconds)
    }

    @Test
    fun sinRed_devuelveNetwork() = runTest {
        val repo = repo()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.create("Nuevo") as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Network)
    }

    @Test
    fun noReintentaAutomaticamente() = runTest {
        val repo = repo()
        repo.load(entry(1))
        val calls = AtomicInteger()
        client.fallback = { calls.incrementAndGet(); failure(serverDown()) }

        repo.refresh()
        repo.create("Nuevo")
        repo.rename(uuid(1), "Otro nombre")
        repo.delete(uuid(1))
        runCurrent()

        assertEquals(4, calls.get())
    }

    @Test
    fun doblePulsacion_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.load(entry(1))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk("""{"deleted":true}""") }
        val outcomes = mutableListOf<WorkspaceOutcome>()
        launch { outcomes += repo.delete(uuid(1)) }
        launch { outcomes += repo.delete(uuid(1)) }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertEquals(1, outcomes.count { it is WorkspaceOutcome.Failed })
    }

    @Test
    fun refrescoDuranteBorrado_noReinsertaElWorkspace() = runTest {
        val repo = repo()
        repo.load(entry(1), entry(2))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1), entry(2))) }
        launch { repo.refresh() }
        runCurrent()
        client.enqueue(apiOk("""{"deleted":true}"""))
        repo.delete(uuid(1))

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("Espacio 2"), names(repo))
    }

    @Test
    fun refrescoDuranteRenombrado_noRevierteElNombre() = runTest {
        val repo = repo()
        repo.load(entry(1, "Viejo"))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1, "Viejo"))) }
        launch { repo.refresh() }
        runCurrent()
        client.enqueue(apiOk(oneBody(1, "Nuevo")))
        repo.rename(uuid(1), "Nuevo")

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("Nuevo"), names(repo))
    }

    @Test
    fun refrescoDuranteAlta_noPierdeElWorkspaceCreado() = runTest {
        val repo = repo()
        repo.load(entry(1))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(listBody(entry(1))) }
        launch { repo.refresh() }
        runCurrent()
        client.enqueue(apiOk(oneBody(2, "Creado"), status = 201))
        repo.create("Creado")

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("Creado", "Espacio 1"), names(repo))
    }
}
