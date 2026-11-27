package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.SyncCodes
import com.simposio.taskeer.data.sync.TransportResult
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.testing.FakeApiClient
import com.simposio.taskeer.testing.apiOk
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Los dobles reproducen las formas reales de ~/Backend-Taskeer (modules/lists y modules/tasks): {lists}, {list}, {tasks},
// {task}, {subtask}, {deleted:true}; ListDto con columnId y sin deletedAt; TaskDto sin listId ni deletedAt.
class RestBridgeContractTest {

    private val client = FakeApiClient()
    private val now = Instant.parse("2026-11-26T18:30:00Z")
    private val bridge = RestBridgeTransport(client, accessToken = { null }, now = { now })

    private val listsJson = """{"lists":[{"id":"list-1","name":"Tareas","position":0,"columnId":"col-9","createdAt":"2026-11-01T00:00:00.000Z","updatedAt":"2026-11-20T00:00:00.000Z"}]}"""

    private fun taskJson(id: String, columnId: String = "col-9", updatedAt: String = "2026-11-25T00:00:00.000Z") =
        """{"id":"$id","columnId":"$columnId","assigneeId":null,"title":"x","description":null,"dueAt":null,"priority":"HIGH","position":0,"completedAt":null,"repeatUnit":null,"repeatInterval":null,"repeatWeekdays":null,"repeatAnchorDay":null,"repeatSuccessorId":null,"createdAt":"2026-11-01T00:00:00.000Z","updatedAt":"$updatedAt"}"""

    private fun readEverything(vararg tasks: String) {
        client.enqueue(apiOk(listsJson))
        client.enqueue(apiOk("""{"tasks":[${tasks.joinToString(",")}]}"""))
    }

    private fun failure(status: Int, code: String, error: DomainError = DomainError.Validation(code, "x")) =
        ApiResult.Failure(error, null, status)

    private fun task(id: String, base: Instant?, clientAt: String = "2026-11-26T18:00:00Z") = PushMutation(
        "m-$id", SyncEntity.TASK, SyncOp.UPSERT, id, base, Instant.parse(clientAt),
        linkedMapOf(
            "listId" to "list-1", "columnId" to null, "title" to "Comprar leche", "description" to null, "priority" to "HIGH",
            "dueAt" to null, "assigneeId" to null, "position" to 3, "completedAt" to null, "repeatUnit" to null,
        ),
    )

    @Test
    fun altaDeTarea_usaColumnaDeLaListaYOmiteNulos() = runTest {
        readEverything()
        client.enqueue(apiOk("""{"task":${taskJson("t1", updatedAt = "2026-11-26T18:30:01.000Z")}}""", 201))

        val result = bridge.push(listOf(task("t1", null))) as TransportResult.Ok

        val post = client.requests.last()
        assertEquals(HttpMethod.POST, post.method)
        assertEquals("tasks", post.path)
        val body = JSONObject(post.jsonBody!!)
        assertEquals("col-9", body.getString("columnId"))
        assertEquals(setOf("id", "columnId", "title", "priority"), body.keys().asSequence().toSet())
        val applied = result.value.single() as PushOutcome.Applied
        assertEquals(Instant.parse("2026-11-26T18:30:01Z"), applied.record!!.updatedAt)
    }

    @Test
    fun patchConservaNulosYNoLlevaVersion() = runTest {
        readEverything(taskJson("t1"))
        client.enqueue(apiOk("""{"task":${taskJson("t1", updatedAt = "2026-11-26T18:30:01.000Z")}}"""))

        bridge.push(listOf(task("t1", Instant.parse("2026-11-25T00:00:00Z"))))

        val patch = client.requests.last()
        assertEquals(HttpMethod.PATCH, patch.method)
        assertEquals("tasks/t1", patch.path)
        val body = JSONObject(patch.jsonBody!!)
        assertTrue(body.isNull("description"))
        assertFalse(body.has("updatedAt") || body.has("baseUpdatedAt") || body.has("clientUpdatedAt") || body.has("listId") || body.has("position"))
    }

    @Test
    fun tareaEnOtraLista_seMueveAntesDelPatch() = runTest {
        readEverything(taskJson("t1", columnId = "col-otra"))
        client.enqueue(apiOk("""{"task":${taskJson("t1", updatedAt = "2026-11-26T18:30:01.000Z")}}"""))
        client.enqueue(apiOk("""{"task":${taskJson("t1", updatedAt = "2026-11-26T18:30:02.000Z")}}"""))

        bridge.push(listOf(task("t1", Instant.parse("2026-11-25T00:00:00Z"))))

        assertEquals(listOf("tasks/t1/move", "tasks/t1"), client.requests.takeLast(2).map { it.path })
        val move = JSONObject(client.requests[2].jsonBody!!)
        assertEquals("col-9", move.getString("columnId"))
        assertEquals(3, move.getInt("position"))
    }

    @Test
    fun lecturaPrevia_detectaConflicto() = runTest {
        readEverything(taskJson("t1", updatedAt = "2026-11-26T12:00:00.000Z"))

        val result = bridge.push(
            listOf(task("t1", Instant.parse("2026-11-26T10:00:00Z"), clientAt = "2026-11-26T11:00:00Z")),
        ) as TransportResult.Ok

        assertTrue(result.value.single() is PushOutcome.Conflict)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun snapshotLeeLasClavesRealesYPideTareasPorBoardId() = runTest {
        readEverything(taskJson("t1"))

        val snapshot = (bridge.snapshot() as TransportResult.Ok).value

        assertEquals(setOf("list-1"), snapshot.lists.keys)
        assertEquals("col-9", snapshot.lists.getValue("list-1").fields["columnId"])
        assertEquals(setOf("t1"), snapshot.tasks.keys)
        assertEquals(listOf("lists", "tasks"), client.requests.map { it.path })
        assertEquals(listOf("boardId" to "list-1"), client.requests.last().query)
    }

    @Test
    fun altaDeLista_omiteNulosYLeeLaListaDeLaRespuesta() = runTest {
        client.enqueue(apiOk("""{"lists":[]}"""))
        client.enqueue(apiOk("""{"list":{"id":"list-2","name":"Casa","position":1,"columnId":"col-2","createdAt":"2026-11-26T18:30:00.000Z","updatedAt":"2026-11-26T18:30:03.000Z"}}""", 201))
        val list = PushMutation("m-l", SyncEntity.LIST, SyncOp.UPSERT, "list-2", null, Instant.parse("2026-11-26T18:00:00Z"), mapOf("name" to "Casa", "position" to 1))

        val result = bridge.push(listOf(list)) as TransportResult.Ok

        assertEquals("lists", client.requests.last().path)
        val applied = result.value.single() as PushOutcome.Applied
        assertEquals("col-2", applied.record!!.fields["columnId"])
    }

    @Test
    fun altaDeSubtarea_usaRutaAnidadaSinPatchSiNoHayCambios() = runTest {
        readEverything()
        client.enqueue(apiOk("""{"subtask":{"id":"s1","taskId":"t1","assigneeId":null,"title":"Sub","dueAt":null,"completed":false,"position":0,"createdAt":"2026-11-26T18:30:00.000Z","updatedAt":"2026-11-26T18:30:00.000Z"}}""", 201))
        val subtask = PushMutation(
            "m-s1", SyncEntity.SUBTASK, SyncOp.UPSERT, "s1", null, Instant.parse("2026-11-26T18:00:00Z"),
            linkedMapOf("taskId" to "t1", "title" to "Sub", "completed" to false, "position" to 0, "assigneeId" to null, "dueAt" to null),
        )

        val result = bridge.push(listOf(subtask)) as TransportResult.Ok

        assertEquals("tasks/t1/subtasks", client.requests.last().path)
        assertEquals(3, client.requests.size)
        assertEquals(setOf("id", "title"), JSONObject(client.requests.last().jsonBody!!).keys().asSequence().toSet())
        assertTrue(result.value.single() is PushOutcome.Applied)
    }

    @Test
    fun edicionDeSubtareaYaExistente_envíaPatchConRutaReal() = runTest {
        readEverything()
        val existing = """{"subtask":{"id":"s1","taskId":"t1","assigneeId":null,"title":"Vieja","dueAt":null,"completed":false,"position":0,"createdAt":"2026-11-26T18:00:00.000Z","updatedAt":"2026-11-26T18:00:00.000Z"}}"""
        client.enqueue(apiOk(existing, 200))
        client.enqueue(apiOk(existing.replace("Vieja", "Nueva")))
        val subtask = PushMutation(
            "m-s1", SyncEntity.SUBTASK, SyncOp.UPSERT, "s1", null, Instant.parse("2026-11-26T18:00:00Z"),
            linkedMapOf("taskId" to "t1", "title" to "Nueva", "completed" to true, "position" to 0, "assigneeId" to null, "dueAt" to null),
        )

        bridge.push(listOf(subtask))

        val patch = client.requests.last()
        assertEquals(HttpMethod.PATCH, patch.method)
        assertEquals("tasks/t1/subtasks/s1", patch.path)
        assertTrue(JSONObject(patch.jsonBody!!).getBoolean("completed"))
        assertTrue(client.requests.none { it.method == HttpMethod.GET && it.path.contains("subtasks") })
    }

    @Test
    fun borradoDeTarea_aplicaTombstoneYTolera404() = runTest {
        readEverything(taskJson("t1"), taskJson("t2"))
        client.enqueue(apiOk("""{"deleted":true}"""))
        client.enqueue(failure(404, "TASK_NOT_FOUND", DomainError.NotFound("TASK_NOT_FOUND", "x")))
        val deletes = listOf("t1", "t2").map {
            PushMutation("m-$it", SyncEntity.TASK, SyncOp.DELETE, it, Instant.parse("2026-11-25T00:00:00Z"), Instant.parse("2026-11-26T18:20:00Z"), null)
        }

        val result = bridge.push(deletes) as TransportResult.Ok

        assertEquals(listOf("tasks/t1", "tasks/t2"), client.requests.drop(2).map { it.path })
        assertTrue(result.value.all { it is PushOutcome.Applied && it.record?.isDeleted == true })
    }

    @Test
    fun borradoDeSubtarea_sinTaskId_noLlamaYConservaLaFila() = runTest {
        readEverything()
        val delete = PushMutation("m-s", SyncEntity.SUBTASK, SyncOp.DELETE, "s1", null, Instant.parse("2026-11-26T18:20:00Z"), null)

        val result = bridge.push(listOf(delete)) as TransportResult.Ok

        assertEquals(2, client.requests.size)
        assertEquals(SyncCodes.VALIDATION_ERROR, (result.value.single() as PushOutcome.Rejected).code)
    }

    @Test
    fun rejectedPorValidacion_noBorraLaFilaLocal() = runTest {
        readEverything()
        client.enqueue(failure(400, "VALIDATION_ERROR"))

        val result = bridge.push(listOf(task("t1", null))) as TransportResult.Ok

        val rejected = result.value.single() as PushOutcome.Rejected
        assertEquals(SyncCodes.VALIDATION_ERROR, rejected.code)
        assertNull(rejected.record)
    }

    @Test
    fun tareaSinListaConocida_noEsDescartable() = runTest {
        client.enqueue(apiOk("""{"lists":[]}"""))
        val result = bridge.push(listOf(task("t1", null))) as TransportResult.Ok

        assertEquals(SyncCodes.VALIDATION_ERROR, (result.value.single() as PushOutcome.Rejected).code)
        assertEquals(1, client.requests.size)
    }

    @Test
    fun permisoDenegado_siguiendoElCodigoDelServidor() = runTest {
        readEverything()
        client.enqueue(failure(403, "FORBIDDEN", DomainError.Forbidden("FORBIDDEN", "x")))

        val result = bridge.push(listOf(task("t1", null))) as TransportResult.Ok

        assertEquals("FORBIDDEN", (result.value.single() as PushOutcome.Rejected).code)
    }

    @Test
    fun fallosTransitoriosSeReintentan() = runTest {
        client.enqueue(failure(503, "INTERNAL_SERVER_ERROR", DomainError.Server("INTERNAL_SERVER_ERROR", "x")))

        assertTrue(bridge.push(listOf(task("t1", null))) is TransportResult.Retry)
    }

    @Test
    fun identify_tomaLaCuentaDelClaimSub() = runTest {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"user-7","email":"a@b.c"}""".toByteArray())
        val withToken = RestBridgeTransport(client, accessToken = { "h.$payload.s" }, now = { now })

        val identity = (withToken.identify() as TransportResult.Ok).value

        assertEquals("user-7", identity.userId)
    }

    @Test
    fun pull_snapshotDeListasYTareas_conListIdYSinSubtareas() = runTest {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"user-7"}""".toByteArray())
        val withToken = RestBridgeTransport(client, accessToken = { "h.$payload.s" }, now = { now })
        client.enqueue(apiOk(listsJson))
        client.enqueue(apiOk("""{"tasks":[${taskJson("t1")}]}"""))

        val page = (withToken.pull("bridge", 200) as TransportResult.Ok).value

        assertTrue(page.isSnapshot)
        assertFalse(page.hasMore)
        assertEquals(setOf(SyncEntity.LIST, SyncEntity.TASK), page.supportedEntities)
        val task = page.records.single { it.entity == SyncEntity.TASK }
        assertEquals("list-1", task.fields["listId"])
        assertNull(task.fields["columnId"])
        assertEquals("user-7", page.userId)
    }
}
