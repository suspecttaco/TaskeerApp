package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.sync.Identity
import com.simposio.taskeer.data.sync.PullPage
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.ServerRecord
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.domain.sync.SyncTimestamps
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Contrato de sincronización 1.0 (specs/sync-engine/backend-spec.md): serialización y lectura de sync/*.
object SyncApiContract {
    const val VERSION = "1.0"
    const val MAJOR = 1
    const val HEADER = "X-Sync-Contract"
    const val PUSH_PATH = "sync/push"
    const val CHANGES_PATH = "sync/changes"
    const val CODE_UNSUPPORTED = "CONTRACT_VERSION_UNSUPPORTED"
    const val CODE_BATCH_TOO_LARGE = "BATCH_TOO_LARGE"
    const val CODE_UNKNOWN_RESULT = "UNKNOWN_RESULT"

    // Orden del servidor dentro de una página: padres antes que hijos.
    private val CHANGE_KEYS = listOf(
        "lists" to SyncEntity.LIST,
        "boards" to SyncEntity.BOARD,
        "columns" to SyncEntity.COLUMN,
        "tasks" to SyncEntity.TASK,
        "subtasks" to SyncEntity.SUBTASK,
        "myDay" to SyncEntity.MY_DAY,
        "reminders" to SyncEntity.REMINDER,
    )

    sealed interface Parsed<out T> {
        class Ok<T>(val value: T) : Parsed<T>

        data object UpdateRequired : Parsed<Nothing>

        data object Malformed : Parsed<Nothing>
    }

    fun serializeBatch(batch: List<PushMutation>): String {
        val mutations = JSONArray()
        for (m in batch) {
            val node = JSONObject()
            node.put("clientMutationId", m.clientMutationId)
            node.put("entity", m.entity.wireName)
            node.put("op", if (m.op == SyncOp.DELETE) "delete" else "upsert")
            node.put("id", m.id)
            node.put("baseUpdatedAt", m.baseUpdatedAt?.let(SyncTimestamps::format) ?: JSONObject.NULL)
            node.put("clientUpdatedAt", SyncTimestamps.format(m.clientUpdatedAt))
            if (m.op == SyncOp.UPSERT) node.put("data", toJson(m.data.orEmpty()))
            mutations.put(node)
        }
        return JSONObject().put("mutations", mutations).toString()
    }

    fun parsePush(body: String, batch: List<PushMutation>): Parsed<List<PushOutcome>> = try {
        val root = JSONObject(body)
        if (!sameMajor(root.optString("contractVersion", VERSION))) {
            Parsed.UpdateRequired
        } else {
            val entities = batch.associate { it.clientMutationId to it.entity }
            val results = root.getJSONArray("results")
            val outcomes = (0 until results.length()).map { parseResult(results.getJSONObject(it), entities) }
            if (outcomes.any { it == null }) Parsed.Malformed else Parsed.Ok(outcomes.filterNotNull())
        }
    } catch (_: JSONException) {
        Parsed.Malformed
    }

    fun parseIdentity(body: String): Parsed<Identity> = try {
        val root = JSONObject(body)
        val version = root.optString("contractVersion", VERSION)
        if (!sameMajor(version)) {
            Parsed.UpdateRequired
        } else {
            Parsed.Ok(Identity(userIdOf(root), version, supportedOf(root)))
        }
    } catch (_: JSONException) {
        Parsed.Malformed
    }

    // `changes` lleva siempre todas las claves, pero una ausente se lee como vacía; las claves que la app no conoce
    // (o que aún no sincroniza, como workspaces y boardAccess) se ignoran.
    fun parsePull(body: String): Parsed<PullPage> = try {
        val root = JSONObject(body)
        val version = root.optString("contractVersion", VERSION)
        if (!sameMajor(version)) {
            Parsed.UpdateRequired
        } else {
            val changes = root.optJSONObject("changes")
            val records = CHANGE_KEYS.flatMap { (key, entity) ->
                val array = changes?.optJSONArray(key) ?: return@flatMap emptyList()
                (0 until array.length()).mapNotNull { parseRecord(array.getJSONObject(it), entity) }
            }
            Parsed.Ok(
                PullPage(
                    userId = userIdOf(root),
                    contractVersion = version,
                    supportedEntities = supportedOf(root),
                    serverTime = SyncTimestamps.parse(root.getString("serverTime")) ?: throw JSONException("serverTime"),
                    isSnapshot = root.optBoolean("isSnapshot", false),
                    records = records,
                    nextCursor = root.getString("nextCursor"),
                    hasMore = root.getBoolean("hasMore"),
                ),
            )
        }
    } catch (_: JSONException) {
        Parsed.Malformed
    }

    private fun userIdOf(root: JSONObject): String = root.getJSONObject("me").getString("userId")

    private fun supportedOf(root: JSONObject): Set<SyncEntity> = root.optJSONArray("supportedEntities")
        ?.let { array -> (0 until array.length()).mapNotNull { SyncEntity.fromWire(array.getString(it)) } }
        .orEmpty()
        .toSet()

    fun errorCode(body: String): String? = try {
        JSONObject(body).getJSONObject("error").optString("code").takeIf { it.isNotEmpty() }
    } catch (_: JSONException) {
        null
    }

    private fun sameMajor(version: String): Boolean = version.substringBefore('.').toIntOrNull() == MAJOR

    private fun parseResult(node: JSONObject, entities: Map<String, SyncEntity>): PushOutcome? {
        val id = node.optString("clientMutationId").takeIf { it.isNotEmpty() } ?: return null
        val entity = entities[id] ?: return null
        val record = node.optJSONObject("record")?.let { parseRecord(it, entity) ?: return null }
        return when (node.optString("status")) {
            "applied" -> PushOutcome.Applied(id, record ?: return null)
            "conflict" -> PushOutcome.Conflict(id, record ?: return null)
            "rejected" -> PushOutcome.Rejected(id, node.optJSONObject("error")?.optString("code").orEmpty().ifEmpty { CODE_UNKNOWN_RESULT }, record)
            else -> PushOutcome.Rejected(id, CODE_UNKNOWN_RESULT, record)
        }
    }

    private fun parseRecord(node: JSONObject, entity: SyncEntity): ServerRecord? {
        val id = node.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val updatedAt = SyncTimestamps.parse(node.optString("updatedAt")) ?: return null
        val deletedAt = if (node.isNull("deletedAt")) null else SyncTimestamps.parse(node.optString("deletedAt")) ?: return null
        val fields = node.keys().asSequence()
            .filterNot { it == "id" || it == "updatedAt" || it == "deletedAt" }
            .associateWith { fromJson(node.get(it)) }
        return ServerRecord(entity, id, updatedAt, deletedAt, fields)
    }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { obj -> value.forEach { (k, v) -> obj.put(k as String, toJson(v)) } }
        is Iterable<*> -> JSONArray().also { array -> value.forEach { array.put(toJson(it)) } }
        else -> value
    }

    private fun fromJson(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { fromJson(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { fromJson(value.get(it)) }
        else -> value
    }
}
