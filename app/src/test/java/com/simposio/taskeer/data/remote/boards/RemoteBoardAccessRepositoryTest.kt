package com.simposio.taskeer.data.remote.boards

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
class RemoteBoardAccessRepositoryTest {

    private val client = FakeApiClient()
    private val session = MutableStateFlow<SessionState>(signedIn("yo@example.com"))
    private val ws = uuid(100)
    private val board = uuid(1001)

    private fun signedIn(email: String, offline: Boolean = false): SessionState =
        SessionState.SignedIn(AccountSummary(email, "Yo"), offline)

    private fun TestScope.repo() = RemoteBoardAccessRepository(client, session, backgroundScope)

    private fun detail(role: String? = "EDITOR", name: String = "Tablero"): String {
        val roleJson = role?.let { ",\"viewerEffectiveRole\":\"$it\"" }.orEmpty()
        return """{"board":{"id":"$board","name":"$name"$roleJson,"columns":[{"id":"c1","tasks":[{"id":"t1"}]}]}}"""
    }

    private fun overrideJson(userN: Int, role: String = "VIEWER") =
        """{"id":"${uuid(900 + userN)}","boardId":"$board","userId":"${uuid(userN)}","role":"$role","updatedAt":"2026-11-02T08:00:00Z"}"""

    private fun overridesBody(vararg entries: String) = """{"overrides":[${entries.joinToString(",")}]}"""

    private fun oneBody(userN: Int, role: String) = """{"override":${overrideJson(userN, role)}}"""

    private fun failure(error: DomainError, retryAfter: Long? = null): ApiResult = ApiResult.Failure(error, retryAfter)

    private fun api(code: String) = failure(DomainError.fromApi(code, "x"))

    private suspend fun RemoteBoardAccessRepository.opened(role: String = "EDITOR") {
        client.enqueue(apiOk(detail(role)))
        assertEquals(WorkspaceOutcome.Done, open(ws, board))
        client.requests.clear()
    }

    private suspend fun RemoteBoardAccessRepository.withOverrides(vararg entries: String) {
        opened()
        client.enqueue(apiOk(overridesBody(*entries)))
        assertEquals(WorkspaceOutcome.Done, loadOverrides())
        client.requests.clear()
    }

    private fun userIds(repo: RemoteBoardAccessRepository) = repo.state.value.overrides.map { it.userId }

    @Test
    fun sinSesion_ningunaOperacionHacePeticion() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        val outcomes = listOf(
            repo.open(ws, board),
            repo.refresh(),
            repo.loadOverrides(),
            repo.setOverride(uuid(3), WorkspaceRole.ADMIN),
            repo.removeOverride(uuid(3)),
        )

        assertTrue(outcomes.all { it is WorkspaceOutcome.Failed && it.error is DomainError.Unauthenticated })
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun signedOut_dejaElEstadoVacio() = runTest {
        session.value = SessionState.SignedOut()
        val repo = repo()

        assertNull(repo.state.value.boardId)
        assertNull(repo.state.value.effectiveRole)
        assertEquals(emptyList<Any>(), repo.state.value.overrides)
        assertFalse(repo.state.value.overridesLoaded)
    }

    @Test
    fun cerrarSesion_vaciaElEstado() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3))

        session.value = SessionState.SignedOut(expired = true)
        runCurrent()

        assertNull(repo.state.value.boardId)
        assertNull(repo.state.value.effectiveRole)
        assertEquals(0, repo.state.value.overrides.size)
    }

    @Test
    fun respuestaTardiaTrasCerrarSesion_seDescarta() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(detail("ADMIN")) }
        var outcome: WorkspaceOutcome? = null
        launch { outcome = repo.open(ws, board) }
        runCurrent()

        session.value = SessionState.SignedOut()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertNull(repo.state.value.effectiveRole)
    }

    @Test
    fun cambioDeCuenta_noMuestraLoDeLaAnterior() = runTest {
        val repo = repo()
        repo.opened()

        session.value = signedIn("otra@example.com")
        runCurrent()

        assertNull(repo.state.value.effectiveRole)
        assertNull(repo.state.value.boardId)
    }

    @Test
    fun resuming_conSesionPrevia_noVaciaElEstado() = runTest {
        val repo = repo()
        repo.opened()

        session.value = SessionState.Resuming
        runCurrent()

        assertEquals(WorkspaceRole.EDITOR, repo.state.value.effectiveRole)
    }

    @Test
    fun sesionOffline_permiteIntentar() = runTest {
        session.value = signedIn("yo@example.com", offline = true)
        val repo = repo()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.open(ws, board) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Network)
        assertEquals(1, client.requests.size)
    }

    @Test
    fun open_hacePeticionAlDetalle() = runTest {
        val repo = repo()
        client.enqueue(apiOk(detail()))

        repo.open(ws, board)

        val request = client.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("workspaces/$ws/boards/$board", request.path)
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun open_leeNombreYRolEfectivo() = runTest {
        val repo = repo()
        client.enqueue(apiOk(detail("VIEWER", name = "Plan")))

        repo.open(ws, board)

        assertEquals("Plan", repo.state.value.boardName)
        assertEquals(WorkspaceRole.VIEWER, repo.state.value.effectiveRole)
        assertEquals(board, repo.state.value.boardId)
    }

    @Test
    fun open_rolDesconocido_esUnknown() = runTest {
        val repo = repo()
        client.enqueue(apiOk(detail(role = null)))

        repo.open(ws, board)

        assertEquals(WorkspaceRole.UNKNOWN, repo.state.value.effectiveRole)
    }

    @Test
    fun open_ignoraColumnasYTareas() = runTest {
        val repo = repo()
        client.enqueue(apiOk(detail("ADMIN")))

        val outcome = repo.open(ws, board)

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(WorkspaceRole.ADMIN, repo.state.value.effectiveRole)
        assertEquals(emptyList<Any>(), repo.state.value.overrides)
    }

    @Test
    fun open_cuerpoIlegible_esFailedYConservaElEstado() = runTest {
        val repo = repo()
        repo.opened("ADMIN")

        client.enqueue(apiOk("<html>"))
        val outcome = repo.refresh()

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Unknown)
        assertEquals(WorkspaceRole.ADMIN, repo.state.value.effectiveRole)
    }

    @Test
    fun open_otroTablero_vaciaElEstadoAnterior() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(detail("VIEWER")) }

        launch { repo.open(ws, uuid(1002)) }
        runCurrent()

        assertNull(repo.state.value.effectiveRole)
        assertEquals(0, repo.state.value.overrides.size)
        assertFalse(repo.state.value.overridesLoaded)
        assertEquals(uuid(1002), repo.state.value.boardId)
        gate.complete(Unit)
        runCurrent()
        assertEquals(WorkspaceRole.VIEWER, repo.state.value.effectiveRole)
    }

    @Test
    fun open_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.open(ws, "../x")

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun refresh_hacePeticionAlTableroAbierto() = runTest {
        val repo = repo()
        repo.opened("VIEWER")
        client.enqueue(apiOk(detail("ADMIN")))

        repo.refresh()

        assertEquals("workspaces/$ws/boards/$board", client.requests.single().path)
        assertEquals(WorkspaceRole.ADMIN, repo.state.value.effectiveRole)
    }

    @Test
    fun refresh_sinTableroAbierto_esNotFoundSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.refresh()

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.NotFound)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun refresh_concurrente_haceUnaSolaPeticion() = runTest {
        val repo = repo()
        repo.opened()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(detail("VIEWER")) }
        launch { repo.refresh() }
        launch { repo.refresh() }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
    }

    @Test
    fun refrescoAntiguoTardio_noRevierteElRolMasNuevo() = runTest {
        val repo = repo()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(detail("VIEWER")) }
        launch { repo.open(ws, board) }
        runCurrent()
        client.enqueue(apiOk(detail("EDITOR", name = "Otro")))
        repo.open(ws, uuid(1002))
        client.enqueue(apiOk(detail("ADMIN")))
        repo.open(ws, board)

        gate.complete(Unit)
        runCurrent()

        assertEquals(WorkspaceRole.ADMIN, repo.state.value.effectiveRole)
        assertEquals(board, repo.state.value.boardId)
    }

    @Test
    fun loadOverrides_hacePeticion() = runTest {
        val repo = repo()
        repo.opened()
        client.enqueue(apiOk(overridesBody()))

        repo.loadOverrides()

        val request = client.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("workspaces/$ws/boards/$board/member-overrides", request.path)
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun loadOverrides_reemplazaLaLista() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(9))

        client.enqueue(apiOk(overridesBody(overrideJson(4, "ADMIN"), overrideJson(3))))
        repo.loadOverrides()

        assertEquals(listOf(uuid(3), uuid(4)), userIds(repo))
        assertTrue(repo.state.value.overridesLoaded)
    }

    @Test
    fun loadOverrides_cuerpoIlegible_esFailedYConservaElEstado() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3))

        client.enqueue(apiOk("<html>"))
        val outcome = repo.loadOverrides()

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Unknown)
        assertEquals(listOf(uuid(3)), userIds(repo))
    }

    @Test
    fun loadOverrides_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.open("../x", board)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun loadOverrides_sinTableroAbierto_esNotFoundSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.loadOverrides()

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.NotFound)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun setOverride_hacePutConElRol() = runTest {
        val repo = repo()
        repo.opened()
        client.enqueue(apiOk(oneBody(3, "ADMIN")))

        repo.setOverride(uuid(3), WorkspaceRole.ADMIN)

        val request = client.requests.single()
        assertEquals(HttpMethod.PUT, request.method)
        assertEquals("workspaces/$ws/boards/$board/member-overrides/${uuid(3)}", request.path)
        assertEquals("ADMIN", JSONObject(request.jsonBody!!).getString("role"))
        assertEquals(AuthMode.BEARER, request.auth)
    }

    @Test
    fun setOverride_200_insertaOSustituye() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3, "VIEWER"))

        client.enqueue(apiOk(oneBody(3, "ADMIN")))
        repo.setOverride(uuid(3), WorkspaceRole.ADMIN)
        client.enqueue(apiOk(oneBody(4, "EDITOR")))
        repo.setOverride(uuid(4), WorkspaceRole.EDITOR)

        assertEquals(listOf(uuid(3), uuid(4)), userIds(repo))
        assertEquals(listOf(WorkspaceRole.ADMIN, WorkspaceRole.EDITOR), repo.state.value.overrides.map { it.role })
    }

    @Test
    fun setOverride_200SinOverrideLegible_esDoneYRelee() = runTest {
        val repo = repo()
        repo.opened()
        client.enqueue(apiOk("{}"))
        client.enqueue(apiOk(overridesBody(overrideJson(3, "ADMIN"))))

        val outcome = repo.setOverride(uuid(3), WorkspaceRole.ADMIN)

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(listOf(HttpMethod.PUT, HttpMethod.GET), client.requests.map { it.method })
        assertEquals(listOf(uuid(3)), userIds(repo))
    }

    @Test
    fun setOverride_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3, "VIEWER"))
        client.enqueue(failure(DomainError.Server("INTERNAL_SERVER_ERROR", "x")))

        val outcome = repo.setOverride(uuid(3), WorkspaceRole.ADMIN)

        assertTrue(outcome is WorkspaceOutcome.Failed)
        assertEquals(WorkspaceRole.VIEWER, repo.state.value.overrides.single().role)
    }

    @Test
    fun setOverride_rolOwner_noHacePeticion() = runTest {
        val repo = repo()
        repo.opened()

        val outcome = repo.setOverride(uuid(3), WorkspaceRole.OWNER)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun setOverride_rolUnknown_noHacePeticion() = runTest {
        val repo = repo()
        repo.opened()

        val outcome = repo.setOverride(uuid(3), WorkspaceRole.UNKNOWN)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun setOverride_idNoUuid_esValidationSinPeticion() = runTest {
        val repo = repo()
        repo.opened()

        val outcome = repo.setOverride("../x", WorkspaceRole.ADMIN)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.Validation)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun setOverride_sinTableroAbierto_esNotFoundSinPeticion() = runTest {
        val repo = repo()

        val outcome = repo.setOverride(uuid(3), WorkspaceRole.ADMIN)

        assertTrue(outcome is WorkspaceOutcome.Failed && outcome.error is DomainError.NotFound)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun setOverride_403_devuelveForbidden() = runTest {
        val repo = repo()
        repo.opened()
        client.enqueue(api("FORBIDDEN"))

        val outcome = repo.setOverride(uuid(3), WorkspaceRole.ADMIN) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Forbidden)
    }

    @Test
    fun setOverride_422_devuelveValidationConCodigo() = runTest {
        val repo = repo()
        repo.opened()
        client.enqueue(api("USER_NOT_WORKSPACE_MEMBER"))

        val outcome = repo.setOverride(uuid(3), WorkspaceRole.ADMIN) as WorkspaceOutcome.Failed

        assertEquals("USER_NOT_WORKSPACE_MEMBER", (outcome.error as DomainError.Validation).code)
    }

    @Test
    fun setOverride_dobleToque_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.opened()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(oneBody(3, "ADMIN")) }
        val outcomes = mutableListOf<WorkspaceOutcome>()
        launch { outcomes += repo.setOverride(uuid(3), WorkspaceRole.ADMIN) }
        launch { outcomes += repo.setOverride(uuid(3), WorkspaceRole.ADMIN) }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, client.requests.size)
        assertEquals(1, outcomes.count { it == WorkspaceOutcome.Done })
        assertEquals(1, outcomes.count { it is WorkspaceOutcome.Failed })
    }

    @Test
    fun removeOverride_haceDeleteYQuitaLaEntrada() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3), overrideJson(4))
        client.enqueue(apiOk("""{"deleted":true}"""))

        val outcome = repo.removeOverride(uuid(3))

        val request = client.requests.single()
        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals("workspaces/$ws/boards/$board/member-overrides/${uuid(3)}", request.path)
        assertEquals(listOf(uuid(4)), userIds(repo))
    }

    @Test
    fun removeOverride_404SinOverride_esExitoYRelee() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3), overrideJson(4))
        client.enqueue(api("MEMBER_NOT_FOUND"))
        client.enqueue(apiOk(overridesBody(overrideJson(4))))

        val outcome = repo.removeOverride(uuid(3))

        assertEquals(WorkspaceOutcome.Done, outcome)
        assertEquals(listOf(HttpMethod.DELETE, HttpMethod.GET), client.requests.map { it.method })
        assertEquals(listOf(uuid(4)), userIds(repo))
    }

    @Test
    fun removeOverride_404DeTablero_esFallo() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3))
        client.enqueue(api("BOARD_NOT_FOUND"))

        val outcome = repo.removeOverride(uuid(3)) as WorkspaceOutcome.Failed

        assertEquals("BOARD_NOT_FOUND", outcome.error.code)
        assertEquals(listOf(uuid(3)), userIds(repo))
        assertEquals(1, client.requests.size)
    }

    @Test
    fun removeOverride_403_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3))
        client.enqueue(api("FORBIDDEN"))

        val outcome = repo.removeOverride(uuid(3)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Forbidden)
        assertEquals(listOf(uuid(3)), userIds(repo))
    }

    @Test
    fun removeOverride_fallo_noCambiaLaLista() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3))
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.removeOverride(uuid(3)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Network)
        assertEquals(listOf(uuid(3)), userIds(repo))
    }

    @Test
    fun removeOverride_dobleToque_hacePeticionUnica() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk("""{"deleted":true}""") }
        val outcomes = mutableListOf<WorkspaceOutcome>()
        launch { outcomes += repo.removeOverride(uuid(3)) }
        launch { outcomes += repo.removeOverride(uuid(3)) }
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

        val outcome = repo.open(ws, board) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(90L, outcome.retryAfterSeconds)
    }

    @Test
    fun sinRed_devuelveNetwork() = runTest {
        val repo = repo()
        repo.opened()
        client.enqueue(failure(DomainError.Network("NETWORK_ERROR", "x")))

        val outcome = repo.removeOverride(uuid(3)) as WorkspaceOutcome.Failed

        assertTrue(outcome.error is DomainError.Network)
    }

    @Test
    fun noReintentaAutomaticamente() = runTest {
        val repo = repo()
        repo.opened()
        val calls = AtomicInteger()
        client.fallback = { calls.incrementAndGet(); failure(DomainError.Server("INTERNAL_SERVER_ERROR", "x")) }

        repo.refresh()
        repo.loadOverrides()
        repo.setOverride(uuid(3), WorkspaceRole.ADMIN)
        repo.removeOverride(uuid(3))
        runCurrent()

        assertEquals(4, calls.get())
    }

    @Test
    fun refrescoDuranteFijacion_noRevierteElOverride() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3, "VIEWER"))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(overridesBody(overrideJson(3, "VIEWER"))) }
        launch { repo.loadOverrides() }
        runCurrent()
        client.enqueue(apiOk(oneBody(3, "ADMIN")))
        repo.setOverride(uuid(3), WorkspaceRole.ADMIN)

        gate.complete(Unit)
        runCurrent()

        assertEquals(WorkspaceRole.ADMIN, repo.state.value.overrides.single().role)
    }

    @Test
    fun refrescoDuranteQuitado_noReinsertaElOverride() = runTest {
        val repo = repo()
        repo.withOverrides(overrideJson(3), overrideJson(4))
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(overridesBody(overrideJson(3), overrideJson(4))) }
        launch { repo.loadOverrides() }
        runCurrent()
        client.enqueue(apiOk("""{"deleted":true}"""))
        repo.removeOverride(uuid(3))

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(uuid(4)), userIds(repo))
    }

    @Test
    fun respuestaDeOverridesDeOtroTablero_seDescarta() = runTest {
        val repo = repo()
        repo.opened()
        val gate = CompletableDeferred<Unit>()
        client.enqueue { gate.await(); apiOk(overridesBody(overrideJson(3))) }
        launch { repo.loadOverrides() }
        runCurrent()
        client.enqueue(apiOk(detail("VIEWER")))
        repo.open(ws, uuid(1002))

        gate.complete(Unit)
        runCurrent()

        assertEquals(0, repo.state.value.overrides.size)
        assertFalse(repo.state.value.overridesLoaded)
    }
}
