package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.sync.Identity
import com.simposio.taskeer.data.sync.PullPage
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.RetryKind
import com.simposio.taskeer.data.sync.ServerRecord
import com.simposio.taskeer.data.sync.SyncCodes
import com.simposio.taskeer.data.sync.SyncTransport
import com.simposio.taskeer.data.sync.TransportResult
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.sync.ConflictRules
import com.simposio.taskeer.domain.sync.ConflictVerdict
import com.simposio.taskeer.domain.sync.LocalVersion
import com.simposio.taskeer.domain.sync.ServerVersion
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import java.time.Duration
import java.time.Instant
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// COBERTURA, RETIRABLE (D1): traduce el push a las rutas REST de /lists, /tasks y subtareas mientras el servidor no
// implemente /sync/*. No lee subtareas ni tableros; el conflicto se decide con una lectura previa. Solo existe en debug.
// Formas reales del backend (modules/lists y modules/tasks): las respuestas van envueltas en {lists}, {list}, {tasks},
// {task}, {subtask}; una lista es un board oculto (su id es el boardId de GET /tasks) con una columna viva (columnId);
// los POST no admiten null en description, dueAt, assigneeId ni completedAt (solo los PATCH); la posición se fija en
// el servidor al crear (se cambia con POST tasks/:id/move); las subtareas cuelgan de tasks/:taskId/subtasks/:subtaskId.
class RestBridgeTransport(
    private val client: ApiClient,
    private val accessToken: () -> String?,
    private val now: () -> Instant = Instant::now,
) : SyncTransport {

    class Snapshot(val lists: Map<String, ServerRecord>, val tasks: Map<String, ServerRecord>)

    // No hay GET /me: la cuenta sale del claim `sub` del access token.
    override suspend fun identify(): TransportResult<Identity> {
        val userId = accessToken()?.let(::subject) ?: return TransportResult.NotAvailable
        return TransportResult.Ok(Identity(userId, BRIDGE_VERSION, setOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK)))
    }

    // Cada lectura es un snapshot completo de listas y tareas, sin cursor ni subtareas: la página solo declara lo que
    // lee (list y task), así que la reconciliación nunca da por borradas subtareas que el puente no ve.
    override suspend fun pull(cursor: String?, limit: Int): TransportResult<PullPage> {
        val userId = accessToken()?.let(::subject) ?: return TransportResult.NotAvailable
        val snapshot = when (val read = snapshot()) {
            is TransportResult.Ok -> read.value
            is TransportResult.Retry -> return read
            else -> return TransportResult.Retry(null, RetryKind.SERVER)
        }
        val listByColumn = snapshot.lists.values.mapNotNull { list -> (list.fields["columnId"] as? String)?.let { it to list.id } }.toMap()
        val tasks = snapshot.tasks.values.mapNotNull { task ->
            val listId = listByColumn[task.fields["columnId"]] ?: return@mapNotNull null
            ServerRecord(task.entity, task.id, task.updatedAt, task.deletedAt, task.fields + mapOf("listId" to listId, "columnId" to null))
        }
        val page = PullPage(
            userId = userId,
            contractVersion = BRIDGE_VERSION,
            supportedEntities = setOf(SyncEntity.LIST, SyncEntity.TASK),
            serverTime = now(),
            isSnapshot = true,
            records = snapshot.lists.values.toList() + tasks,
            nextCursor = BRIDGE_CURSOR,
            hasMore = false,
        )
        return TransportResult.Ok(page)
    }

    override suspend fun push(batch: List<PushMutation>): TransportResult<List<PushOutcome>> {
        val snapshot = when (val read = snapshot()) {
            is TransportResult.Ok -> read.value
            is TransportResult.Retry -> return read
            else -> return TransportResult.Retry(null, RetryKind.SERVER)
        }
        val lists = snapshot.lists.toMutableMap()
        val tasks = snapshot.tasks.toMutableMap()
        val outcomes = mutableListOf<PushOutcome>()
        for (m in batch) {
            val known = when (m.entity) {
                SyncEntity.LIST -> lists[m.id]
                SyncEntity.TASK -> tasks[m.id]
                else -> null
            }
            val outcome = verdict(m, known)?.let { PushOutcome.Conflict(m.clientMutationId, it) }
                ?: when (val sent = send(m, known, lists)) {
                    is Sent.Done -> PushOutcome.Applied(m.clientMutationId, sent.record).also { remember(it.record, lists, tasks) }
                    is Sent.Rejected -> PushOutcome.Rejected(m.clientMutationId, sent.code, null)
                    is Sent.Transient -> return sent.result
                }
            outcomes += outcome
        }
        return TransportResult.Ok(outcomes)
    }

    // Lista y tareas del usuario: la lectura de la que sale la versión base y el conflicto. Las subtareas no se leen.
    suspend fun snapshot(): TransportResult<Snapshot> {
        val listBody = when (val result = client.execute(ApiRequest(HttpMethod.GET, "lists"))) {
            is ApiResult.Failure -> return failure(result)
            is ApiResult.Success -> result.response.body
        }
        val lists = records(listBody, "lists", SyncEntity.LIST).associateBy { it.id }
        val tasks = mutableMapOf<String, ServerRecord>()
        for (id in lists.keys) {
            when (val result = client.execute(ApiRequest(HttpMethod.GET, "tasks", query = listOf("boardId" to id)))) {
                is ApiResult.Failure -> return failure(result)
                is ApiResult.Success -> records(result.response.body, "tasks", SyncEntity.TASK).forEach { tasks[it.id] = it }
            }
        }
        return TransportResult.Ok(Snapshot(lists, tasks))
    }

    private fun verdict(m: PushMutation, known: ServerRecord?): ServerRecord? {
        if (known == null) return null
        val server = ServerVersion(known.updatedAt, known.isDeleted)
        val local = LocalVersion(m.clientUpdatedAt, m.op == SyncOp.DELETE)
        return if (ConflictRules.resolve(m.baseUpdatedAt, server, local, now()) == ConflictVerdict.ServerWins) known else null
    }

    private sealed interface Sent {
        class Done(val record: ServerRecord) : Sent

        class Rejected(val code: String) : Sent

        class Transient(val result: TransportResult<Nothing>) : Sent
    }

    // Lo que el puente no sabe expresar en REST es un fallo de forma de la petición: se conserva la fila local.
    private fun unsupported() = Sent.Rejected(SyncCodes.VALIDATION_ERROR)

    private suspend fun send(m: PushMutation, known: ServerRecord?, lists: Map<String, ServerRecord>): Sent {
        if (m.op == SyncOp.DELETE) return delete(m)
        val data = m.data.orEmpty()
        return when (m.entity) {
            SyncEntity.LIST ->
                if (known == null) call(post("lists", create(data, LIST_FIELDS).put("id", m.id)), m, "list")
                else call(patch("lists/${m.id}", update(data, LIST_FIELDS)), m, "list")
            SyncEntity.TASK -> task(m, known, data, lists)
            SyncEntity.SUBTASK -> subtask(m, data)
            else -> unsupported()
        }
    }

    private suspend fun delete(m: PushMutation): Sent {
        val path = when (m.entity) {
            SyncEntity.LIST -> "lists/${m.id}"
            SyncEntity.TASK -> "tasks/${m.id}"
            // El borrado no lleva `data` y la ruta real exige el taskId, que el puente no conoce.
            else -> return unsupported()
        }
        return call(ApiRequest(HttpMethod.DELETE, path), m, key = null, tombstone = true)
    }

    private suspend fun task(m: PushMutation, known: ServerRecord?, data: Map<String, Any?>, lists: Map<String, ServerRecord>): Sent {
        val columnId = lists[data["listId"]]?.fields?.get("columnId") as? String ?: return unsupported()
        if (known == null) {
            return call(post("tasks", create(data, TASK_FIELDS).put("id", m.id).put("columnId", columnId)), m, "task")
        }
        if (known.fields["columnId"] != columnId) {
            val position = (data["position"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val moved = call(post("tasks/${m.id}/move", JSONObject().put("columnId", columnId).put("position", position)), m, "task")
            if (moved !is Sent.Done) return moved
        }
        return call(patch("tasks/${m.id}", update(data, TASK_FIELDS)), m, "task")
    }

    // POST es idempotente por id pero no aplica cambios sobre una subtarea ya existente (200): ahí, y cuando el alta
    // deja completed o position distintos, hace falta un PATCH con el estado completo.
    private suspend fun subtask(m: PushMutation, data: Map<String, Any?>): Sent {
        val taskId = data["taskId"] as? String ?: return unsupported()
        val created = client.execute(post("tasks/$taskId/subtasks", create(data, SUBTASK_CREATE_FIELDS).put("id", m.id)))
        if (created is ApiResult.Failure) return classify(created)
        val response = (created as ApiResult.Success).response
        val record = parse(response.body, "subtask", m) ?: return unsupported()
        val position = (data["position"] as? Number)?.toInt()
        val needsPatch = response.status != 201 || data["completed"] == true || (position != null && position != (record.fields["position"] as? Number)?.toInt())
        if (!needsPatch) return Sent.Done(record)
        return call(patch("tasks/$taskId/subtasks/${m.id}", update(data, SUBTASK_UPDATE_FIELDS)), m, "subtask")
    }

    private fun patch(path: String, body: JSONObject) = ApiRequest(HttpMethod.PATCH, path, jsonBody = body.toString())

    private fun post(path: String, body: JSONObject) = ApiRequest(HttpMethod.POST, path, jsonBody = body.toString())

    // Los POST rechazan null en los opcionales: se omiten las claves nulas.
    private fun create(data: Map<String, Any?>, fields: Set<String>): JSONObject =
        JSONObject().also { json -> data.forEach { (k, v) -> if (k in fields && v != null) json.put(k, v) } }

    // Los PATCH sí admiten null para vaciar el campo.
    private fun update(data: Map<String, Any?>, fields: Set<String>): JSONObject =
        JSONObject().also { json -> data.forEach { (k, v) -> if (k in fields) json.put(k, v ?: JSONObject.NULL) } }

    private suspend fun call(request: ApiRequest, m: PushMutation, key: String?, tombstone: Boolean = false): Sent =
        when (val result = client.execute(request)) {
            is ApiResult.Failure ->
                if (tombstone && result.status == 404) Sent.Done(ServerRecord(m.entity, m.id, now(), now(), emptyMap())) else classify(result)
            is ApiResult.Success -> {
                val at = now()
                if (tombstone) {
                    Sent.Done(ServerRecord(m.entity, m.id, at, at, emptyMap()))
                } else {
                    parse(result.response.body, key.orEmpty(), m)?.let { Sent.Done(it) } ?: unsupported()
                }
            }
        }

    private fun parse(body: String, key: String, m: PushMutation): ServerRecord? =
        runCatching { JSONObject(body).optJSONObject(key) }.getOrNull()?.let { record(it, m.entity) }

    // 400 es la validación de la petición (un defecto de forma, no un veredicto sobre el dato): nunca se trata como rechazo
    // del servidor, para que el motor no descarte ni revierta la fila local.
    private fun classify(result: ApiResult.Failure): Sent {
        val status = result.status
        return when {
            status == null || status == 429 || status in 500..599 -> Sent.Transient(failure(result))
            status == 401 -> Sent.Transient(TransportResult.Unauthorized)
            status == 400 -> Sent.Rejected(SyncCodes.VALIDATION_ERROR)
            else -> Sent.Rejected(result.error.code)
        }
    }

    private fun failure(result: ApiResult.Failure): TransportResult.Retry = when {
        result.status == 429 -> TransportResult.Retry(result.retryAfterSeconds?.let(Duration::ofSeconds), RetryKind.RATE_LIMITED)
        result.error is DomainError.Network -> TransportResult.Retry(null, RetryKind.NETWORK)
        else -> TransportResult.Retry(result.retryAfterSeconds?.let(Duration::ofSeconds), RetryKind.SERVER)
    }

    private fun remember(record: ServerRecord, lists: MutableMap<String, ServerRecord>, tasks: MutableMap<String, ServerRecord>) {
        when (record.entity) {
            SyncEntity.LIST -> lists[record.id] = record
            SyncEntity.TASK -> tasks[record.id] = record
            else -> Unit
        }
    }

    private fun records(body: String, key: String, entity: SyncEntity): List<ServerRecord> = try {
        val array = JSONObject(body).optJSONArray(key) ?: JSONArray()
        (0 until array.length()).mapNotNull { record(array.getJSONObject(it), entity) }
    } catch (_: JSONException) {
        emptyList()
    }

    private fun record(json: JSONObject, entity: SyncEntity): ServerRecord? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val updatedAt = runCatching { Instant.parse(json.optString("updatedAt")) }.getOrNull() ?: return null
        val deletedAt = json.optString("deletedAt").takeIf { it.isNotEmpty() && !json.isNull("deletedAt") }?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val fields = json.keys().asSequence()
            .filterNot { it == "id" || it == "updatedAt" || it == "deletedAt" }
            .associateWith { json.get(it).takeUnless { v -> v == JSONObject.NULL } }
        return ServerRecord(entity, id, updatedAt, deletedAt, fields)
    }

    // java.util.Base64 exige API 26 y minSdk es 24: se decodifica el payload a mano.
    private fun subject(token: String): String? = try {
        val json = JSONObject(String(decodeBase64Url(token.split('.')[1])))
        json.optString("sub").takeIf(String::isNotEmpty)
    } catch (_: Exception) {
        null
    }

    private fun decodeBase64Url(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text.trimEnd('=')) {
            buffer = (buffer shl 6) or ALPHABET.indexOf(c).also { require(it >= 0) }
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    private companion object {
        const val BRIDGE_VERSION = "bridge"
        const val BRIDGE_CURSOR = "bridge"
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val LIST_FIELDS = setOf("name", "position")
        val TASK_FIELDS = setOf(
            "title", "description", "priority", "dueAt", "assigneeId", "completedAt",
            "repeatUnit", "repeatInterval", "repeatWeekdays", "repeatAnchorDay", "repeatSuccessorId",
        )
        val SUBTASK_CREATE_FIELDS = setOf("title", "dueAt", "assigneeId")
        val SUBTASK_UPDATE_FIELDS = setOf("title", "dueAt", "assigneeId", "completed", "position")
    }
}
