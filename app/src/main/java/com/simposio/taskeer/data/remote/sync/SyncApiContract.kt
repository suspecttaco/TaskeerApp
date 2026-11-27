package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.sync.Identity
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
            val userId = root.getJSONObject("me").getString("userId")
            val supported = root.optJSONArray("supportedEntities")
                ?.let { array -> (0 until array.length()).mapNotNull { SyncEntity.fromWire(array.getString(it)) } }
                .orEmpty()
                .toSet()
            Parsed.Ok(Identity(userId, version, supported))
        }
    } catch (_: JSONException) {
        Parsed.Malformed
    }

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
