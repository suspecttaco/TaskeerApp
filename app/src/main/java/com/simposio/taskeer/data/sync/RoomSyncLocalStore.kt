package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.QUEUE_STATE_QUEUED
import com.simposio.taskeer.data.local.QUEUE_STATE_STUCK
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.SyncDao
import com.simposio.taskeer.data.local.SyncMetaEntity
import com.simposio.taskeer.data.local.SyncQueueEntity
import com.simposio.taskeer.data.local.SyncableEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListEntity
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.sync.AssigneeMapping
import com.simposio.taskeer.domain.sync.DeletionGraceRules
import com.simposio.taskeer.domain.sync.QueuedOp
import com.simposio.taskeer.domain.sync.ScopeRow
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.domain.sync.SyncScope
import com.simposio.taskeer.domain.sync.SyncStatusRules
import com.simposio.taskeer.domain.sync.SyncTimestamps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant

class RoomSyncLocalStore(
    private val dao: SyncDao,
    private val scopeHolder: SyncScopeHolder,
    private val newMutationId: () -> String = ::newClientId,
) : SyncLocalStore {

    override suspend fun collect(now: Instant) {
        val scope = scopeHolder.scope.value
        dropStaleEntries()
        if (SyncEntity.LIST in scope) {
            dao.pendingLists().forEach { reconcile(SyncEntity.LIST, it, now) }
        }
        dao.pendingTasks().forEach { task ->
            val shape = task.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.TASK, shape, scope)) reconcile(SyncEntity.TASK, task, now)
        }
        dao.pendingSubtasks().forEach { subtask ->
            val parent = dao.findTask(subtask.taskId)?.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.SUBTASK, parent, scope)) reconcile(SyncEntity.SUBTASK, subtask, now)
        }
    }

    // Una entrada cuya fila ya no está pendiente (o ya no existe) no tiene nada que enviar.
    private suspend fun dropStaleEntries() {
        dao.queue().forEach { entry ->
            val entity = SyncEntity.fromWire(entry.entity)
            val row = entity?.let { rowOf(it, entry.entityId) }
            if (row == null || !row.pending) dao.removeQueue(entry.mutationId)
        }
    }

    private suspend fun reconcile(entity: SyncEntity, row: SyncableEntity, now: Instant) {
        val entry = dao.queueEntry(entity.wireName, row.id)
        val op = if (row.deletedAt != null) SyncOp.DELETE else SyncOp.UPSERT
        val neverReachedServer = dao.baseline(entity.wireName, row.id) == null && (entry == null || !entry.wasAttempted())
        if (op == SyncOp.DELETE && neverReachedServer) {
            entry?.let { dao.removeQueue(it.mutationId) }
            dao.clearPending(entity.wireName, row.id, row.updatedAt)
            return
        }
        when {
            entry == null -> dao.upsertQueue(newEntry(entity, row, op, now))
            entry.op != op.name -> {
                dao.removeQueue(entry.mutationId)
                dao.upsertQueue(newEntry(entity, row, op, now))
            }
            entry.payloadVersion != row.updatedAt && entry.wasAttempted() -> {
                dao.removeQueue(entry.mutationId)
                dao.upsertQueue(newEntry(entity, row, op, now))
            }
            entry.payloadVersion != row.updatedAt -> dao.upsertQueue(entry.copy(payloadVersion = row.updatedAt))
        }
    }

    private fun newEntry(entity: SyncEntity, row: SyncableEntity, op: SyncOp, now: Instant) = SyncQueueEntity(
        mutationId = newMutationId(),
        entity = entity.wireName,
        entityId = row.id,
        op = op.name,
        enqueuedAt = now.toEpochMilli(),
        payloadVersion = row.updatedAt,
        attempts = 0,
        failures = 0,
        nextAttemptAt = null,
        lastErrorCode = null,
        state = QUEUE_STATE_QUEUED,
    )

    // Si ya se intentó enviar, el servidor pudo haberla recibido: un cambio posterior necesita un identificador nuevo.
    private fun SyncQueueEntity.wasAttempted(): Boolean = attempts > 0 || failures > 0

    override suspend fun eligible(now: Instant, ignoreBackoff: Boolean): List<QueuedOp> {
        val scope = scopeHolder.scope.value
        return dao.queue()
            .filter { it.state != QUEUE_STATE_STUCK }
            .filter { ignoreBackoff || (it.nextAttemptAt ?: 0L) <= now.toEpochMilli() }
            .filter { inScope(it, scope) && graceElapsed(it, now) }
            .mapNotNull { it.toQueuedOp() }
    }

    private suspend fun inScope(entry: SyncQueueEntity, scope: Set<SyncEntity>): Boolean {
        val entity = SyncEntity.fromWire(entry.entity) ?: return false
        return when (entity) {
            SyncEntity.LIST -> SyncEntity.LIST in scope
            SyncEntity.TASK -> dao.findTask(entry.entityId)?.shape()?.let { SyncScope.allows(entity, it, scope) } ?: false
            SyncEntity.SUBTASK -> {
                val parent = dao.findSubtask(entry.entityId)?.let { dao.findTask(it.taskId) }?.shape()
                parent?.let { SyncScope.allows(entity, it, scope) } ?: false
            }
            else -> false
        }
    }

    // El borrado de una tarea espera a que venza «Deshacer»; el de sus subtareas, también, para no dejar
    // subtareas borradas en el servidor bajo una tarea que se restaura.
    private suspend fun graceElapsed(entry: SyncQueueEntity, now: Instant): Boolean =
        graceEnd(entry)?.let { !it.isAfter(now) } ?: true

    private suspend fun graceEnd(entry: SyncQueueEntity): Instant? {
        if (entry.op != SyncOp.DELETE.name) return null
        val task = when (entry.entity) {
            SyncEntity.TASK.wireName -> dao.findTask(entry.entityId)
            SyncEntity.SUBTASK.wireName -> dao.findSubtask(entry.entityId)?.let { dao.findTask(it.taskId) }
            else -> null
        } ?: return null
        return task.deletedAt?.let { DeletionGraceRules.eligibleAt(Instant.ofEpochMilli(it)) }
    }

    override suspend fun nextWake(now: Instant): Instant? =
        dao.queue().filter { it.state != QUEUE_STATE_STUCK }.mapNotNull { entry ->
            val backoff = entry.nextAttemptAt?.let(Instant::ofEpochMilli)
            listOfNotNull(backoff, graceEnd(entry)).filter { it.isAfter(now) }.minOrNull()
        }.minOrNull()

    override suspend fun payloadFor(op: QueuedOp, me: String): PreparedOp? {
        val row = rowOf(op.entity, op.entityId) ?: return null
        if ((op.op == SyncOp.DELETE) != (row.deletedAt != null)) return null
        val data = if (op.op == SyncOp.UPSERT) dataFor(op.entity, op.entityId, me) ?: return null else null
        val base = dao.baseline(op.entity.wireName, op.entityId)?.serverUpdatedAt?.let(Instant::ofEpochMilli)
        val mutation = PushMutation(
            clientMutationId = op.mutationId,
            entity = op.entity,
            op = op.op,
            id = op.entityId,
            baseUpdatedAt = base,
            clientUpdatedAt = Instant.ofEpochMilli(row.updatedAt),
            data = data,
        )
        return PreparedOp(op, mutation, row.updatedAt)
    }

    private suspend fun dataFor(entity: SyncEntity, id: String, me: String): Map<String, Any?>? = when (entity) {
        SyncEntity.LIST -> dao.findList(id)?.let { mapOf("name" to it.name, "position" to it.position) }
        SyncEntity.TASK -> dao.findTask(id)?.takeIf { it.shape() != null }?.let { taskData(it, me) }
        SyncEntity.SUBTASK -> dao.findSubtask(id)?.let { subtaskData(it, me) }
        else -> null
    }

    private fun taskData(task: TaskEntity, me: String): Map<String, Any?> = linkedMapOf(
        "listId" to task.listId,
        "columnId" to task.columnId,
        "title" to task.title,
        "description" to task.description,
        "priority" to task.priority,
        "dueAt" to task.dueAt?.let { SyncTimestamps.format(Instant.ofEpochMilli(it)) },
        "assigneeId" to AssigneeMapping.toServer(task.assigneeId, me),
        "position" to task.position,
        "completedAt" to task.completedAt?.let { SyncTimestamps.format(Instant.ofEpochMilli(it)) },
        "repeatUnit" to task.repeatUnit,
        "repeatInterval" to task.repeatInterval,
        "repeatWeekdays" to task.repeatWeekdays,
        "repeatAnchorDay" to task.repeatAnchorDay,
        "repeatSuccessorId" to task.repeatSuccessorId,
    )

    private fun subtaskData(subtask: SubtaskEntity, me: String): Map<String, Any?> = linkedMapOf(
        "taskId" to subtask.taskId,
        "title" to subtask.title,
        "completed" to subtask.completed,
        "position" to subtask.position,
        "assigneeId" to AssigneeMapping.toServer(subtask.assigneeId, me),
        "dueAt" to subtask.dueAt?.let { SyncTimestamps.format(Instant.ofEpochMilli(it)) },
    )

    override suspend fun applyOutcome(
        prepared: PreparedOp,
        outcome: PushOutcome,
        me: String,
        now: Instant,
        retryDependency: Boolean,
    ): OutcomeEffect {
        val op = prepared.op
        return when (outcome) {
            is PushOutcome.Applied -> {
                dao.commitApplied(op.entity.wireName, op.entityId, outcome.record.updatedAt.toEpochMilli(), prepared.sentVersion, op.mutationId)
                OutcomeEffect.APPLIED
            }
            is PushOutcome.Conflict -> adopt(prepared, outcome.record, me, OutcomeEffect.REPLACED_BY_SERVER)
            is PushOutcome.Rejected -> reject(prepared, outcome, me, now, retryDependency)
        }
    }

    private suspend fun reject(
        prepared: PreparedOp,
        outcome: PushOutcome.Rejected,
        me: String,
        now: Instant,
        retryDependency: Boolean,
    ): OutcomeEffect {
        val op = prepared.op
        val record = outcome.record
        return when {
            outcome.code == SyncCodes.VALIDATION_ERROR -> keepForRetry(op, outcome.code)
            outcome.code == CODE_DEPENDENCY_NOT_FOUND && retryDependency -> reissue(op)
            outcome.code == CODE_LIST_NAME_TAKEN && op.entity == SyncEntity.LIST && record != null && record.entity == SyncEntity.LIST ->
                adoptList(prepared, record)
            record != null -> adopt(prepared, record, me, OutcomeEffect.REVERTED)
            else -> discard(prepared, now)
        }
    }

    // Un fallo de forma de la petición es un defecto del cliente o del puente, no un veredicto sobre el dato:
    // la fila conserva su contenido y su pending, y el fallo cuenta hacia «atascada» (R9).
    private suspend fun keepForRetry(op: QueuedOp, code: String): OutcomeEffect {
        recordPermanentFailure(op, code)
        return OutcomeEffect.KEPT_FOR_RETRY
    }

    // El servidor guarda también los rechazos por clientMutationId: reenviar el mismo id devolvería el mismo rechazo,
    // así que el reintento tras el padre es una operación nueva.
    private suspend fun reissue(op: QueuedOp): OutcomeEffect {
        val entry = dao.queueEntry(op.entity.wireName, op.entityId) ?: return OutcomeEffect.APPLIED
        dao.removeQueue(entry.mutationId)
        dao.upsertQueue(entry.copy(mutationId = newMutationId(), lastErrorCode = CODE_DEPENDENCY_NOT_FOUND))
        return OutcomeEffect.RETRY_AFTER_PARENT
    }

    // El veredicto del servidor es definitivo: se adopta su registro. Si el usuario editó la fila mientras
    // viajaba el envío, esa edición posterior manda y solo se actualiza la versión base.
    private suspend fun adopt(prepared: PreparedOp, record: ServerRecord, me: String, effect: OutcomeEffect): OutcomeEffect {
        val op = prepared.op
        val row = rowOf(op.entity, op.entityId)
        if (row == null || row.updatedAt != prepared.sentVersion) {
            dao.commitApplied(op.entity.wireName, op.entityId, record.updatedAt.toEpochMilli(), prepared.sentVersion, op.mutationId)
            return OutcomeEffect.APPLIED
        }
        val baseline = record.updatedAt.toEpochMilli()
        when (op.entity) {
            SyncEntity.LIST -> dao.commitList(dao.findList(op.entityId)!!.adoptedFrom(record), baseline, op.mutationId)
            SyncEntity.TASK -> dao.commitTask(dao.findTask(op.entityId)!!.adoptedFrom(record, me), baseline, op.mutationId)
            SyncEntity.SUBTASK -> dao.commitSubtask(dao.findSubtask(op.entityId)!!.adoptedFrom(record, me), baseline, op.mutationId)
            else -> dao.removeQueue(op.mutationId)
        }
        return effect
    }

    private suspend fun adoptList(prepared: PreparedOp, record: ServerRecord): OutcomeEffect {
        val op = prepared.op
        val local = dao.findList(op.entityId) ?: run {
            dao.removeQueue(op.mutationId)
            return OutcomeEffect.APPLIED
        }
        val serverList = TaskListEntity(
            id = record.id,
            name = record.fields["name"] as? String ?: local.name,
            position = (record.fields["position"] as? Number)?.toInt() ?: local.position,
            createdAt = local.createdAt,
            updatedAt = record.updatedAt.toEpochMilli(),
            pending = false,
            deletedAt = null,
        )
        dao.adoptList(op.entityId, serverList, record.updatedAt.toEpochMilli(), op.mutationId)
        return OutcomeEffect.LIST_ADOPTED
    }

    // Sin registro del servidor no hay a qué volver: lo que nunca llegó se descarta (borrado lógico local sin envío)
    // y lo que ya existía conserva su valor local, sin pendiente para no reenviar un cambio rechazado.
    private suspend fun discard(prepared: PreparedOp, now: Instant): OutcomeEffect {
        val op = prepared.op
        val row = rowOf(op.entity, op.entityId)
        val neverReachedServer = dao.baseline(op.entity.wireName, op.entityId) == null
        if (row == null || row.updatedAt != prepared.sentVersion) {
            dao.removeQueue(op.mutationId)
            return OutcomeEffect.APPLIED
        }
        if (neverReachedServer && row.deletedAt == null) {
            val deletedAt = now.toEpochMilli()
            when (op.entity) {
                SyncEntity.LIST -> dao.commitList(dao.findList(op.entityId)!!.copy(deletedAt = deletedAt, pending = false), null, op.mutationId)
                SyncEntity.TASK -> dao.commitTask(dao.findTask(op.entityId)!!.copy(deletedAt = deletedAt, pending = false), null, op.mutationId)
                SyncEntity.SUBTASK -> dao.commitSubtask(dao.findSubtask(op.entityId)!!.copy(deletedAt = deletedAt, pending = false), null, op.mutationId)
                else -> dao.removeQueue(op.mutationId)
            }
        } else {
            dao.clearPending(op.entity.wireName, op.entityId, prepared.sentVersion)
            dao.removeQueue(op.mutationId)
        }
        return OutcomeEffect.REVERTED
    }

    override suspend fun recordTransientFailure(op: QueuedOp, nextAttemptAt: Instant) {
        val entry = dao.queueEntry(op.entity.wireName, op.entityId) ?: return
        dao.upsertQueue(
            entry.copy(attempts = entry.attempts + 1, nextAttemptAt = nextAttemptAt.toEpochMilli(), lastErrorCode = CODE_TRANSIENT),
        )
    }

    override suspend fun recordPermanentFailure(op: QueuedOp, code: String) {
        val entry = dao.queueEntry(op.entity.wireName, op.entityId) ?: return
        val failures = entry.failures + 1
        val state = if (failures >= SyncStatusRules.STUCK_THRESHOLD) QUEUE_STATE_STUCK else entry.state
        dao.upsertQueue(entry.copy(failures = failures, lastErrorCode = code, state = state))
    }

    override suspend fun markAllUnsyncedPending() = dao.markAllUnsyncedPending()

    override suspend fun rekeyList(oldId: String, newId: String) = dao.rekeyList(oldId, newId)

    override suspend fun meta(key: String): String? = dao.meta(key)

    override suspend fun setMeta(key: String, value: String?) {
        if (value == null) dao.removeMeta(key) else dao.upsertMeta(SyncMetaEntity(key, value))
    }

    // Solo cuenta lo que el alcance permite enviar: lo que el servidor aún no soporta no es «pendiente» para el usuario.
    override fun observePendingCount(): Flow<Int> =
        combine(scopeHolder.scope, dao.observePendingCounts()) { scope, counts ->
            val inList = ScopeRow(taskInList = true)
            val inBoard = ScopeRow(taskInBoard = true)
            var total = 0
            if (SyncEntity.LIST in scope) total += counts.lists
            if (SyncScope.allows(SyncEntity.TASK, inList, scope)) total += counts.listTasks
            if (SyncScope.allows(SyncEntity.TASK, inBoard, scope)) total += counts.boardTasks
            if (SyncScope.allows(SyncEntity.SUBTASK, inList, scope)) total += counts.listSubtasks
            if (SyncScope.allows(SyncEntity.SUBTASK, inBoard, scope)) total += counts.boardSubtasks
            total
        }.distinctUntilChanged()

    override fun observeStuckCount(): Flow<Int> = dao.observeStuckCount()

    private suspend fun rowOf(entity: SyncEntity, id: String): SyncableEntity? = when (entity) {
        SyncEntity.LIST -> dao.findList(id)
        SyncEntity.TASK -> dao.findTask(id)
        SyncEntity.SUBTASK -> dao.findSubtask(id)
        else -> null
    }

    // Una tarea está en una lista o en una columna, exactamente una; cualquier otra forma no es enviable.
    private fun TaskEntity.shape(): ScopeRow? = when {
        listId != null && columnId == null -> ScopeRow(taskInList = true)
        listId == null && columnId != null -> ScopeRow(taskInBoard = true)
        else -> null
    }

    private fun SyncQueueEntity.toQueuedOp(): QueuedOp? {
        val syncEntity = SyncEntity.fromWire(entity) ?: return null
        return QueuedOp(
            mutationId = mutationId,
            entity = syncEntity,
            entityId = entityId,
            op = SyncOp.valueOf(op),
            enqueuedAt = Instant.ofEpochMilli(enqueuedAt),
            attempts = attempts,
            failures = failures,
            nextAttemptAt = nextAttemptAt?.let(Instant::ofEpochMilli),
            lastErrorCode = lastErrorCode,
        )
    }

    private companion object {
        const val CODE_DEPENDENCY_NOT_FOUND = "DEPENDENCY_NOT_FOUND"
        const val CODE_LIST_NAME_TAKEN = "LIST_NAME_TAKEN"
        const val CODE_TRANSIENT = "TRANSIENT"
    }
}
