package com.simposio.taskeer.testing

import com.simposio.taskeer.data.sync.Acquisition
import com.simposio.taskeer.data.sync.Identity
import com.simposio.taskeer.data.sync.PullPage
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.RetryKind
import com.simposio.taskeer.data.sync.ServerRecord
import com.simposio.taskeer.data.sync.SyncTransport
import com.simposio.taskeer.data.sync.TransportResult
import com.simposio.taskeer.data.sync.TransportSource
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.domain.sync.SyncTimestamps
import java.time.Duration
import java.time.Instant

// Servidor en memoria con la semántica de push del contrato 1.0 (idempotencia por clientMutationId, no-op, veredicto de
// versión con reloj acotado, tombstones sin resurrección, dependencias, nombre de lista repetido) y de pull: flujo ordenado
// por (updatedAt, rango, id) con cursor opaco, paginación sin pérdidas ni repeticiones y snapshot con tombstones.
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
    class Rejection(val code: String, val withRecord: Boolean = false, val once: Boolean = false, val recordId: String? = null)

    val rejectIds = mutableMapOf<String, Rejection>()
    var maxBatch: Int? = null
    var poisonedIds = mutableSetOf<String>()

    // Entidades cuya mutación se responde «applied» sin registro, como hace el backend real con un borrado idempotente.
    val appliedWithoutRecordIds = mutableSetOf<String>()
    var onProcessing: suspend () -> Unit = {}
    var serverRewrite: ((PushMutation, Map<String, Any?>) -> Map<String, Any?>)? = null
    var identifyResult: TransportResult<Identity>? = null
    var concurrent = 0
    var maxConcurrent = 0
        private set

    // Pull: cada llamada queda registrada; los fallos se inyectan por número de llamada (1 = la primera).
    val pulls = mutableListOf<Pair<String?, Int>>()
    val pullCalls: Int get() = pulls.size
    val failPullCalls = mutableSetOf<Int>()
    val invalidCursors = mutableSetOf<String>()

    // Resultados de transporte para las próximas llamadas a pull, una por llamada.
    val pullFailures = ArrayDeque<TransportResult<Nothing>>()
    var pageCap: Int? = null
    var forceSnapshotNext = false

    override suspend fun pull(cursor: String?, limit: Int): TransportResult<PullPage> {
        pulls += cursor to limit
        if (pulls.size in failPullCalls) return TransportResult.Retry(null, RetryKind.NETWORK)
        pullFailures.removeFirstOrNull()?.let { return it }
        if (cursor != null && (cursor in invalidCursors || !CURSOR.matches(cursor))) return TransportResult.Invalid("INVALID_CURSOR")
        val forced = cursor != null && cursor.startsWith("i") && forceSnapshotNext
        if (forced) forceSnapshotNext = false
        val snapshot = cursor == null || forced || cursor.startsWith("s")
        val after = if (cursor == null || forced) null else position(cursor)
        val flow = records.values
            .filter { it.entity in supported || (SyncEntity.BOARD in supported && it.entity in READ_ONLY) }
            .sortedWith(compareBy({ it.updatedAt }, { it.entity.ordinal }, { it.id }))
            .filter { after == null || compare(it, after) > 0 }
        val size = minOf(limit, pageCap ?: limit)
        val page = flow.take(size)
        val hasMore = flow.size > page.size
        val next = page.lastOrNull()?.let { cursorOf(it, snapshot && hasMore) }
            ?: cursor?.takeUnless { forced }
            ?: "i:${serverNow.toEpochMilli()}:0:"
        return TransportResult.Ok(
            PullPage(userId, "1.0", supported, serverNow, snapshot, page.map { it.record() }, next, hasMore),
        )
    }

    private fun cursorOf(stored: Stored, snapshot: Boolean) =
        "${if (snapshot) "s" else "i"}:${stored.updatedAt.toEpochMilli()}:${stored.entity.ordinal}:${stored.id}"

    private fun position(cursor: String): Triple<Long, Int, String> {
        val parts = cursor.split(':', limit = 4)
        return Triple(parts[1].toLong(), parts[2].toInt(), parts[3])
    }

    private fun compare(stored: Stored, after: Triple<Long, Int, String>): Int {
        val byTime = stored.updatedAt.toEpochMilli().compareTo(after.first)
        if (byTime != 0) return byTime
        val byRank = stored.entity.ordinal.compareTo(after.second)
        return if (byRank != 0) byRank else stored.id.compareTo(after.third)
    }

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
                return TransportResult.Retry(null, RetryKind.NETWORK)
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
            return PushOutcome.Rejected(id, rejection.code, if (rejection.withRecord) records[m.entity to (rejection.recordId ?: m.id)]?.record() else null)
        }
        if (m.id in appliedWithoutRecordIds) return PushOutcome.Applied(id, null)
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
        val data = m.data.orEmpty().let { sent -> serverRewrite?.invoke(m, sent) ?: sent }
        missingParent(m)?.let { return PushOutcome.Rejected(id, "DEPENDENCY_NOT_FOUND", null) }
        if (m.entity == SyncEntity.LIST && existing == null) {
            val clash = records.values.firstOrNull {
                it.entity == SyncEntity.LIST && it.deletedAt == null && it.fields["name"] == data["name"]
            }
            if (clash != null) return PushOutcome.Rejected(id, "LIST_NAME_TAKEN", clash.record())
        }
        if (m.entity == SyncEntity.MY_DAY) return upsertMyDay(m, existing, data)
        if (m.entity == SyncEntity.REMINDER && existing?.fields?.get("status") == "SENT" && data["status"] == "PENDING") {
            return PushOutcome.Rejected(id, "INVALID_MUTATION", existing.record())
        }
        if (m.entity == SyncEntity.REMINDER && existing == null) {
            val created = Stored(m.entity, m.id, data + ("origin" to "MOBILE"), serverNow, null)
            records[key] = created
            return PushOutcome.Applied(id, created.record())
        }
        if (existing == null && m.entity == SyncEntity.BOARD) return createBoard(m, data)
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

    // Contrato 1.0, sección 5.4: renovar solo con un `addedAt` posterior; un tombstone se reactiva; el instante se acota a `serverNow`.
    private fun upsertMyDay(m: PushMutation, existing: Stored?, data: Map<String, Any?>): PushOutcome {
        val sent = minOf(SyncTimestamps.parse(data["addedAt"] as String)!!, serverNow)
        val fields = mapOf("taskId" to m.id, "addedAt" to SyncTimestamps.format(sent))
        val stored = existing ?: Stored(SyncEntity.MY_DAY, m.id, fields, serverNow, null).also { records[SyncEntity.MY_DAY to m.id] = it }
        val current = SyncTimestamps.parse(stored.fields["addedAt"] as String)!!
        if (existing != null && (existing.deletedAt != null || sent.isAfter(current))) {
            existing.fields = fields
            existing.updatedAt = serverNow
            existing.deletedAt = null
        }
        return PushOutcome.Applied(m.clientMutationId, stored.record())
    }

    // Un alta de tablero siembra las columnas del cliente con sus ids (contrato 1.0, sección 5.4) y las devuelve con su versión.
    private fun createBoard(m: PushMutation, data: Map<String, Any?>): PushOutcome {
        val columns = (data["columns"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val board = Stored(SyncEntity.BOARD, m.id, data - "columns", serverNow, null)
        records[SyncEntity.BOARD to m.id] = board
        columns.forEach { column ->
            val columnId = column["id"] as String
            val fields = mapOf("boardId" to m.id) + column.entries.filter { it.key != "id" }.associate { it.key as String to it.value }
            records[SyncEntity.COLUMN to columnId] = Stored(SyncEntity.COLUMN, columnId, fields, serverNow, null)
        }
        val seeded = columns.map { mapOf("id" to it["id"], "updatedAt" to SyncTimestamps.format(serverNow)) }
        return PushOutcome.Applied(
            m.clientMutationId,
            ServerRecord(SyncEntity.BOARD, m.id, serverNow, null, board.fields + ("columns" to seeded)),
        )
    }

    // Contrato 1.0, secciones 4.3.2 y 4.3.3.
    private fun wins(existing: Stored, m: PushMutation): Boolean {
        val base = m.baseUpdatedAt
        if (base != null && !existing.updatedAt.isAfter(base)) return true
        val clock = minOf(m.clientUpdatedAt, serverNow + Duration.ofMinutes(5))
        return clock.isAfter(existing.updatedAt)
    }

    private companion object {
        val READ_ONLY = setOf(SyncEntity.WORKSPACE, SyncEntity.BOARD_ACCESS)
        val CURSOR = Regex("[si]:[0-9]+:[0-9]+:.*")
    }

    private fun tombstone(m: PushMutation) = ServerRecord(m.entity, m.id, serverNow, serverNow, emptyMap())

    private fun missingParent(m: PushMutation): Boolean? {
        val data = m.data.orEmpty()
        return when (m.entity) {
            SyncEntity.TASK ->
                (data["listId"] as? String)?.let { records[SyncEntity.LIST to it]?.deletedAt != null || it !in listIds() }
                    ?: (data["columnId"] as? String)?.let { records[SyncEntity.COLUMN to it]?.deletedAt != null || (SyncEntity.COLUMN to it) !in records }
            SyncEntity.COLUMN -> (data["boardId"] as? String)?.let { (SyncEntity.BOARD to it) !in records }
            SyncEntity.SUBTASK -> (data["taskId"] as? String)?.let { records[SyncEntity.TASK to it] == null }
            SyncEntity.REMINDER -> (data["taskId"] as? String)?.let { records[SyncEntity.TASK to it] == null }
            SyncEntity.MY_DAY -> records[SyncEntity.TASK to m.id] == null
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
