package com.simposio.taskeer.testing

import com.simposio.taskeer.data.sync.Acquisition
import com.simposio.taskeer.data.sync.Identity
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.ServerRecord
import com.simposio.taskeer.data.sync.SyncTransport
import com.simposio.taskeer.data.sync.TransportResult
import com.simposio.taskeer.data.sync.TransportSource
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import java.time.Duration
import java.time.Instant

// Servidor en memoria con la semántica de push del contrato 1.0 (idempotencia por clientMutationId, no-op, veredicto de
// versión con reloj acotado, tombstones sin resurrección, dependencias, nombre de lista repetido). Para el pull de S2 crecerá.
class FakeSyncBackend(
    var userId: String = USER_A,
    var supported: Set<SyncEntity> = setOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK),
    var serverNow: Instant = Instant.parse("2026-11-26T18:30:00Z"),
) : SyncTransport {

    class Stored(val entity: SyncEntity, val id: String, var fields: Map<String, Any?>, var updatedAt: Instant, var deletedAt: Instant?) {
        fun record() = ServerRecord(entity, id, updatedAt, deletedAt, fields)
    }

    val records = mutableMapOf<Pair<SyncEntity, String>, Stored>()
    private val processed = mutableMapOf<String, PushOutcome>()
    val batches = mutableListOf<List<PushMutation>>()
    var identifyCalls = 0
        private set
    val pushCalls: Int get() = batches.size

    // Resultados de transporte inyectados para las próximas llamadas a push (antes de procesar nada).
    val transportFailures = ArrayDeque<TransportResult<Nothing>>()

    // Pierde la respuesta tras procesar: el servidor guarda el resultado pero el cliente solo ve un fallo de red.
    var dropNextResponse = false
    class Rejection(val code: String, val withRecord: Boolean = false, val once: Boolean = false)

    val rejectIds = mutableMapOf<String, Rejection>()
    var maxBatch: Int? = null
    var poisonedIds = mutableSetOf<String>()
    var onProcessing: suspend () -> Unit = {}
    var identifyResult: TransportResult<Identity>? = null
    var concurrent = 0
    var maxConcurrent = 0
        private set

    override suspend fun identify(): TransportResult<Identity> {
        identifyCalls++
        return identifyResult ?: TransportResult.Ok(Identity(userId, "1.0", supported))
    }

    override suspend fun push(batch: List<PushMutation>): TransportResult<List<PushOutcome>> {
        concurrent++
        maxConcurrent = maxOf(maxConcurrent, concurrent)
        try {
            batches += batch
            transportFailures.removeFirstOrNull()?.let { return it }
            maxBatch?.let { if (batch.size > it) return TransportResult.Invalid("BATCH_TOO_LARGE") }
            if (batch.any { it.id in poisonedIds }) return TransportResult.Invalid("VALIDATION_ERROR")
            onProcessing()
            val outcomes = batch.map { process(it) }
            if (dropNextResponse) {
                dropNextResponse = false
                return TransportResult.Retry(null, com.simposio.taskeer.data.sync.RetryKind.NETWORK)
            }
            return TransportResult.Ok(outcomes)
        } finally {
            concurrent--
        }
    }

    fun seed(entity: SyncEntity, id: String, fields: Map<String, Any?>, updatedAt: Instant, deletedAt: Instant? = null) {
        records[entity to id] = Stored(entity, id, fields, updatedAt, deletedAt)
    }

    fun task(id: String): Stored? = records[SyncEntity.TASK to id]

    private fun process(m: PushMutation): PushOutcome {
        processed[m.clientMutationId]?.let { return it }
        val outcome = decide(m)
        processed[m.clientMutationId] = outcome
        return outcome
    }

    private fun decide(m: PushMutation): PushOutcome {
        val id = m.clientMutationId
        rejectIds[m.id]?.let { rejection ->
            if (rejection.once) rejectIds.remove(m.id)
            return PushOutcome.Rejected(id, rejection.code, if (rejection.withRecord) records[m.entity to m.id]?.record() else null)
        }
        val key = m.entity to m.id
        val existing = records[key]
        if (m.op == SyncOp.DELETE) {
            if (existing == null) return PushOutcome.Applied(id, tombstone(m))
            if (existing.deletedAt != null) return PushOutcome.Applied(id, existing.record())
            return if (wins(existing, m)) {
                existing.updatedAt = serverNow
                existing.deletedAt = serverNow
                PushOutcome.Applied(id, existing.record())
            } else {
                PushOutcome.Conflict(id, existing.record())
            }
        }
        val data = m.data.orEmpty()
        missingParent(m)?.let { return PushOutcome.Rejected(id, "DEPENDENCY_NOT_FOUND", null) }
        if (m.entity == SyncEntity.LIST && existing == null) {
            val clash = records.values.firstOrNull {
                it.entity == SyncEntity.LIST && it.deletedAt == null && it.fields["name"] == data["name"]
            }
            if (clash != null) return PushOutcome.Rejected(id, "LIST_NAME_TAKEN", clash.record())
        }
        if (existing == null) {
            val created = Stored(m.entity, m.id, data, serverNow, null)
            records[key] = created
            return PushOutcome.Applied(id, created.record())
        }
        if (existing.deletedAt != null) return PushOutcome.Conflict(id, existing.record())
        if (existing.fields == data) return PushOutcome.Applied(id, existing.record())
        return if (wins(existing, m)) {
            existing.fields = data
            existing.updatedAt = serverNow
            PushOutcome.Applied(id, existing.record())
        } else {
            PushOutcome.Conflict(id, existing.record())
        }
    }

    // Contrato 1.0, secciones 4.3.2 y 4.3.3.
    private fun wins(existing: Stored, m: PushMutation): Boolean {
        val base = m.baseUpdatedAt
        if (base != null && !existing.updatedAt.isAfter(base)) return true
        val clock = minOf(m.clientUpdatedAt, serverNow + Duration.ofMinutes(5))
        return clock.isAfter(existing.updatedAt)
    }

    private fun tombstone(m: PushMutation) = ServerRecord(m.entity, m.id, serverNow, serverNow, emptyMap())

    private fun missingParent(m: PushMutation): Boolean? {
        val data = m.data.orEmpty()
        return when (m.entity) {
            SyncEntity.TASK -> (data["listId"] as? String)?.let { records[SyncEntity.LIST to it]?.deletedAt != null || it !in listIds() }
            SyncEntity.SUBTASK -> (data["taskId"] as? String)?.let { records[SyncEntity.TASK to it] == null }
            else -> null
        }?.takeIf { it }
    }

    private fun listIds() = records.keys.filter { it.first == SyncEntity.LIST }.map { it.second }.toSet()
}

// Fuente de transporte de pruebas: un solo transporte, la cuenta se lee una vez por sesión.
class DirectTransportSource(private val transport: SyncTransport) : TransportSource {
    private var cached: Identity? = null
    var acquisitions = 0
        private set

    override suspend fun acquire(): Acquisition {
        acquisitions++
        cached?.let { return Acquisition.Ready(transport, it) }
        return when (val result = transport.identify()) {
            is TransportResult.Ok -> result.value.also { cached = it }.let { Acquisition.Ready(transport, it) }
            is TransportResult.Retry -> Acquisition.Retry(result.after)
            TransportResult.Unauthorized -> Acquisition.Unauthorized
            TransportResult.UpdateRequired -> Acquisition.Paused(com.simposio.taskeer.domain.sync.PauseReason.UPDATE_REQUIRED)
            TransportResult.NotAvailable -> Acquisition.Paused(com.simposio.taskeer.domain.sync.PauseReason.SERVER_NOT_READY)
            is TransportResult.Invalid -> Acquisition.Retry(null)
        }
    }

    override fun invalidate() {
        cached = null
    }
}
