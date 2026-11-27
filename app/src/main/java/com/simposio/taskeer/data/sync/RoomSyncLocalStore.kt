package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.BoardAccessEntity
import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.QUEUE_STATE_QUEUED
import com.simposio.taskeer.data.local.QUEUE_STATE_STUCK
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.SyncDao
import com.simposio.taskeer.data.local.SyncMetaEntity
import com.simposio.taskeer.data.local.SyncBaselineEntity
import com.simposio.taskeer.data.local.SyncQueueEntity
import com.simposio.taskeer.data.local.SyncWorkspaceEntity
import com.simposio.taskeer.data.local.SyncableEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListEntity
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.sync.AssigneeMapping
import com.simposio.taskeer.domain.sync.ConflictRules
import com.simposio.taskeer.domain.sync.ConflictVerdict
import com.simposio.taskeer.domain.sync.DeletionGraceRules
import com.simposio.taskeer.domain.sync.LocalVersion
import com.simposio.taskeer.domain.sync.QueuedOp
import com.simposio.taskeer.domain.sync.ScopeRow
import com.simposio.taskeer.domain.sync.ServerVersion
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncLoss
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

    private val purger = LocalBoardPurger(dao)

    override suspend fun collect(now: Instant) {
        val scope = scopeHolder.scope.value
        dropStaleEntries()
        if (SyncEntity.LIST in scope) {
            dao.pendingLists().forEach { reconcile(SyncEntity.LIST, it, now) }
        }
        if (SyncEntity.BOARD in scope) {
            dao.pendingBoards().forEach { reconcile(SyncEntity.BOARD, it, now) }
            if (SyncEntity.COLUMN in scope) dao.pendingColumns().forEach { reconcile(SyncEntity.COLUMN, it, now) }
        }
        dao.pendingTasks().forEach { task ->
            val shape = task.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.TASK, shape, scope)) reconcile(SyncEntity.TASK, task, now)
        }
        dao.pendingSubtasks().forEach { subtask ->
            val parent = dao.findTask(subtask.taskId)?.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.SUBTASK, parent, scope)) reconcile(SyncEntity.SUBTASK, subtask, now)
        }
        dao.pendingMyDay().forEach { entry ->
            val shape = dao.findTask(entry.id)?.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.MY_DAY, shape, scope)) reconcile(SyncEntity.MY_DAY, entry, now)
        }
        dao.pendingReminders().forEach { reminder ->
            val shape = dao.findTask(reminder.taskId)?.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.REMINDER, shape, scope)) reconcile(SyncEntity.REMINDER, reminder, now)
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
            SyncEntity.BOARD -> SyncEntity.BOARD in scope && !awaitingWorkspace(entry.entityId)
            SyncEntity.COLUMN -> SyncEntity.BOARD in scope && SyncEntity.COLUMN in scope &&
                dao.findColumn(entry.entityId)?.let { !awaitingWorkspace(it.boardId) } ?: false
            SyncEntity.TASK -> dao.findTask(entry.entityId)?.let { it.shape()?.let { shape -> SyncScope.allows(entity, shape, scope) } == true && !taskAwaitsWorkspace(it) } ?: false
            SyncEntity.SUBTASK -> {
                val parent = dao.findSubtask(entry.entityId)?.let { dao.findTask(it.taskId) }
                parent?.shape()?.let { SyncScope.allows(entity, it, scope) && !taskAwaitsWorkspace(parent) } ?: false
            }
            SyncEntity.MY_DAY, SyncEntity.REMINDER -> {
                val task = personalTask(entity, entry.entityId)
                task?.shape()?.let { SyncScope.allows(entity, it, scope) && !taskAwaitsWorkspace(task) } ?: false
            }
            else -> false
        }
    }

    // Las marcas de Mi día usan el id de la tarea; el recordatorio guarda el suyo y apunta a la tarea.
    private suspend fun personalTask(entity: SyncEntity, id: String): TaskEntity? = when (entity) {
        SyncEntity.MY_DAY -> dao.findTask(id)
        else -> dao.findReminder(id)?.let { dao.findTask(it.taskId) }
    }

    // Un tablero nuevo sube al workspace personal, que llega con la primera traída: hasta entonces ni él ni lo que cuelga
    // de él se envían (las columnas y tareas serían rechazadas por no existir el tablero).
    private suspend fun awaitingWorkspace(boardId: String): Boolean {
        val board = dao.findBoard(boardId) ?: return false
        return board.workspaceId == null && dao.baseline(SyncEntity.BOARD.wireName, boardId) == null &&
            dao.personalWorkspaceId() == null
    }

    private suspend fun taskAwaitsWorkspace(task: TaskEntity): Boolean =
        task.columnId?.let { dao.findColumn(it)?.boardId }?.let { awaitingWorkspace(it) } ?: false

    override suspend fun personalWorkspaceId(): String? = dao.personalWorkspaceId()

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
        SyncEntity.BOARD -> dao.findBoard(id)?.let { boardData(it) }
        SyncEntity.COLUMN -> dao.findColumn(id)?.let { columnData(it) }
        SyncEntity.MY_DAY -> dao.findMyDay(id)?.let { linkedMapOf("addedAt" to SyncTimestamps.format(Instant.ofEpochMilli(it.addedAt))) }
        SyncEntity.REMINDER -> dao.findReminder(id)?.let { reminderData(it) }
        else -> null
    }

    // Origen y creación los fija el servidor (`origin = MOBILE`); aquí solo viaja el estado.
    private fun reminderData(reminder: ReminderEntity): Map<String, Any?> = linkedMapOf(
        "taskId" to reminder.taskId,
        "remindAt" to SyncTimestamps.format(Instant.ofEpochMilli(reminder.remindAt)),
        "status" to reminder.status,
        "sentAt" to reminder.sentAt?.let { SyncTimestamps.format(Instant.ofEpochMilli(it)) },
    )

    // El alta lleva el workspace y las columnas del cliente (con sus ids) para que el servidor no siembre otras; después
    // solo viaja el nombre. Sin workspace personal conocido no hay alta posible.
    private suspend fun boardData(board: BoardEntity): Map<String, Any?>? {
        if (dao.baseline(SyncEntity.BOARD.wireName, board.id) != null) return linkedMapOf("name" to board.name)
        val workspaceId = board.workspaceId ?: dao.personalWorkspaceId() ?: return null
        return linkedMapOf(
            "workspaceId" to workspaceId,
            "name" to board.name,
            "columns" to dao.liveColumnsOf(board.id).map {
                linkedMapOf("id" to it.id, "name" to it.name, "position" to it.position, "wipLimit" to it.wipLimit, "isDoneColumn" to it.isDoneColumn)
            },
        )
    }

    private fun columnData(column: ColumnEntity): Map<String, Any?> = linkedMapOf(
        "boardId" to column.boardId,
        "name" to column.name,
        "position" to column.position,
        "wipLimit" to column.wipLimit,
        "isDoneColumn" to column.isDoneColumn,
    )

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
            is PushOutcome.Applied -> applied(prepared, outcome.record, me)
            is PushOutcome.Conflict -> adopt(prepared, outcome.record, me, OutcomeEffect.REPLACED_BY_SERVER)
            is PushOutcome.Rejected -> reject(prepared, outcome, me, now, retryDependency)
        }
    }

    // «Aplicado» no implica «tal como se envió»: el servidor renumera posiciones y puede aplicar solo una parte, así que
    // la fila adopta su registro. La edición hecha mientras viajaba el envío manda y solo avanza la versión base; la
    // versión local se conserva para que esa comparación siga valiendo en el siguiente ciclo.
    private suspend fun applied(prepared: PreparedOp, record: ServerRecord, me: String): OutcomeEffect {
        val op = prepared.op
        val row = rowOf(op.entity, op.entityId)
        val baseline = record.updatedAt.toEpochMilli()
        if (op.entity == SyncEntity.BOARD && op.op == SyncOp.UPSERT) recordSeededColumns(record)
        if (op.op == SyncOp.DELETE || record.isDeleted || row == null || row.updatedAt != prepared.sentVersion || row.deletedAt != null) {
            dao.commitApplied(op.entity.wireName, op.entityId, baseline, prepared.sentVersion, op.mutationId)
            return OutcomeEffect.APPLIED
        }
        commitRow(row.adoptedFrom(record, me).stamped(row.updatedAt, pending = false, deletedAt = null), baseline, op.mutationId)
        return OutcomeEffect.APPLIED
    }

    // El alta de un tablero devuelve la versión de las columnas que sembró: son la base de sus altas posteriores.
    private suspend fun recordSeededColumns(record: ServerRecord) {
        (record.fields["columns"] as? List<*>)?.forEach { item ->
            val column = item as? Map<*, *> ?: return@forEach
            val id = column["id"] as? String ?: return@forEach
            val updated = (column["updatedAt"] as? String)?.let(SyncTimestamps::parse) ?: return@forEach
            dao.upsertBaseline(SyncBaselineEntity(SyncEntity.COLUMN.wireName, id, updated.toEpochMilli()))
        }
    }

    private suspend fun commitRow(row: SyncableEntity, baseline: Long?, mutationId: String?) {
        when (row) {
            is TaskListEntity -> dao.commitList(row, baseline, mutationId)
            is TaskEntity -> dao.commitTask(row, baseline, mutationId)
            is SubtaskEntity -> dao.commitSubtask(row, baseline, mutationId)
            is BoardEntity -> dao.commitBoard(row, baseline, mutationId)
            is ColumnEntity -> dao.commitColumn(row, baseline, mutationId)
            is MyDayEntryEntity -> dao.commitMyDay(row, baseline, mutationId)
            is ReminderEntity -> dao.commitReminder(row, baseline, mutationId)
            else -> error("Entidad no sincronizable: ${row::class.simpleName}")
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
            outcome.code == CODE_LIST_NAME_TAKEN && op.entity == SyncEntity.LIST ->
                if (record != null && record.entity == SyncEntity.LIST && dao.baseline(op.entity.wireName, op.entityId) == null) {
                    adoptList(prepared, record)
                } else {
                    keepForRetry(op, outcome.code)
                }
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
        commitRow(row.adoptedFrom(record, me), record.updatedAt.toEpochMilli(), op.mutationId)
        return effect
    }

    // Solo una lista que nunca llegó al servidor (alta, primer sync) se funde con la del mismo nombre. En un renombrado de
    // una lista ya sincronizada el registro devuelto puede ser otra lista: fusionarla borraría la local y movería sus tareas.
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
            commitRow(row.stamped(row.updatedAt, pending = false, deletedAt = now.toEpochMilli()), null, op.mutationId)
        } else {
            dao.clearPending(op.entity.wireName, op.entityId, prepared.sentVersion)
            dao.removeQueue(op.mutationId)
        }
        return OutcomeEffect.REVERTED
    }

    override suspend fun applyPage(page: PullPage, me: String, now: Instant, commit: PageCommit): PageEffect {
        // El alcance de la página es lo que el servidor declara completo: lo demás ni se aplica ni se reconcilia.
        val scope = scopeHolder.scope.value intersect page.supportedEntities
        val losses = mutableListOf<SyncLoss>()
        var remindersChanged = false
        dao.inTransaction {
            page.records.forEach {
                val alarmsBefore = if (it.entity == SyncEntity.TASK) alarmFingerprint(it.id) else null
                val applied = applyRecord(it, scope, me, now, losses)
                val reminderApplied = applied && it.entity == SyncEntity.REMINDER
                val taskAlarmChanged = applied && it.entity == SyncEntity.TASK && alarmFingerprint(it.id) != alarmsBefore
                if (reminderApplied || taskAlarmChanged) remindersChanged = true
            }
            when (commit) {
                PageCommit.KeepCursor -> Unit
                PageCommit.SaveCursor -> dao.upsertMeta(SyncMetaEntity(SyncMetaKeys.CURSOR, page.nextCursor))
                is PageCommit.FinishSnapshot -> {
                    reconcileAbsent(commit.seen, scope, now)
                    dao.upsertMeta(SyncMetaEntity(SyncMetaKeys.CURSOR, page.nextCursor))
                }
            }
        }
        return PageEffect(losses, remindersChanged)
    }

    // Lo que decide si una tarea tiene alarma de fecha límite: si cambia por pull, hay que reconciliar las alarmas.
    private suspend fun alarmFingerprint(taskId: String): Triple<Long?, Long?, Long?>? =
        dao.findTask(taskId)?.takeIf { it.dueAt != null || it.completedAt != null || it.deletedAt != null }
            ?.let { Triple(it.dueAt, it.completedAt, it.deletedAt) }

    // Verdadero si el registro se aplicó (no estaba fuera de alcance ni era una versión ya conocida).
    private suspend fun applyRecord(record: ServerRecord, scope: Set<SyncEntity>, me: String, now: Instant, losses: MutableList<SyncLoss>): Boolean {
        if (record.entity !in PULLED || !recordInScope(record, scope)) return false
        val serverMillis = record.updatedAt.toEpochMilli()
        val base = dao.baseline(record.entity.wireName, record.id)?.serverUpdatedAt
        // Una versión ya conocida (o más vieja) no cambia nada: así repetir una página no pisa ni resucita filas.
        if (base != null && serverMillis <= base) return false
        val usable = when (record.entity) {
            SyncEntity.WORKSPACE -> true.also { applyWorkspace(record, losses) }
            SyncEntity.BOARD_ACCESS -> true.also { applyAccess(record, losses) }
            SyncEntity.BOARD ->
                if (record.isDeleted) true.also { retireBoard(record.id, losses, SyncLoss.DELETED_BY_SERVER) } else applyRow(record, base, me, now, losses)
            else -> applyRow(record, base, me, now, losses)
        }
        if (usable) recordBaseline(record)
        return usable
    }

    // El servidor retiró el tablero (o ya no lo vemos): no hay tombstone que conservar, solo se limpia todo lo suyo.
    private suspend fun retireBoard(boardId: String, losses: MutableList<SyncLoss>, loss: SyncLoss) {
        if (purger.purge(boardId)) losses += loss
    }

    private suspend fun applyWorkspace(record: ServerRecord, losses: MutableList<SyncLoss>) {
        val f = record.fields
        dao.upsertWorkspace(
            SyncWorkspaceEntity(
                id = record.id,
                name = f["name"] as? String ?: "",
                isPersonal = f["isPersonal"] as? Boolean ?: false,
                myRole = f["myRole"] as? String ?: "",
                deletedAt = record.deletedAt?.toEpochMilli(),
            ),
        )
        if (record.isDeleted) dao.boardIdsOfWorkspace(record.id).forEach { retireBoard(it, losses, SyncLoss.ACCESS_LOST) }
    }

    private suspend fun applyAccess(record: ServerRecord, losses: MutableList<SyncLoss>) {
        if (record.isDeleted) {
            retireBoard(record.id, losses, SyncLoss.ACCESS_LOST)
            dao.removeAccess(record.id)
            return
        }
        val workspaceId = record.fields["workspaceId"] as? String ?: return
        dao.upsertAccess(
            BoardAccessEntity(record.id, workspaceId, record.fields["effectiveRole"] as? String ?: "", record.updatedAt.toEpochMilli()),
        )
        dao.assignWorkspace(record.id, workspaceId)
    }

    // Falso si el registro no tiene forma utilizable: entonces ni siquiera se anota su versión.
    private suspend fun applyRow(record: ServerRecord, base: Long?, me: String, now: Instant, losses: MutableList<SyncLoss>): Boolean {
        val row = rowOf(record.entity, record.id)
        when {
            // Un tombstone de algo que no existe aquí solo deja la versión conocida.
            row == null -> if (!record.isDeleted) save(record.toNewRow(me) ?: return false)
            !row.pending -> save(row.adoptedFrom(record, me))
            else -> resolvePending(row, record, base, me, now, losses)
        }
        return true
    }

    // Fila con cambios sin enviar frente a un registro más nuevo del servidor: misma regla que aplica el servidor (R10).
    private suspend fun resolvePending(
        row: SyncableEntity,
        record: ServerRecord,
        base: Long?,
        me: String,
        now: Instant,
        losses: MutableList<SyncLoss>,
    ) {
        val localDeleted = row.deletedAt != null
        val sameContent = !record.isDeleted && !localDeleted &&
            row.adoptedFrom(record, me).stamped(row.updatedAt, row.pending, row.deletedAt) == row
        when {
            // El registro que el servidor ya tiene coincide con el local: no hay conflicto y el envío será un no-op.
            sameContent -> Unit
            record.isDeleted && localDeleted -> {
                save(row.adoptedFrom(record, me))
                dropQueueEntry(record.entity, record.id)
            }
            else -> {
                val server = ServerVersion(record.updatedAt, record.isDeleted)
                val local = LocalVersion(Instant.ofEpochMilli(row.updatedAt), localDeleted)
                val verdict = ConflictRules.resolve(base?.let(Instant::ofEpochMilli), server, local, now)
                if (verdict == ConflictVerdict.ServerWins) {
                    save(row.adoptedFrom(record, me))
                    dropQueueEntry(record.entity, record.id)
                    losses += if (record.isDeleted) SyncLoss.DELETED_BY_SERVER else SyncLoss.REPLACED_BY_SERVER
                }
            }
        }
    }

    private suspend fun recordBaseline(record: ServerRecord) =
        dao.upsertBaseline(SyncBaselineEntity(record.entity.wireName, record.id, record.updatedAt.toEpochMilli()))

    private suspend fun dropQueueEntry(entity: SyncEntity, id: String) {
        dao.queueEntry(entity.wireName, id)?.let { dao.removeQueue(it.mutationId) }
    }

    private suspend fun save(row: SyncableEntity) {
        when (row) {
            is TaskListEntity -> dao.upsertList(row)
            is TaskEntity -> dao.upsertTask(row)
            is SubtaskEntity -> dao.upsertSubtask(row)
            is BoardEntity -> dao.upsertBoard(row)
            is ColumnEntity -> dao.upsertColumn(row)
            is MyDayEntryEntity -> dao.upsertMyDay(row)
            is ReminderEntity -> dao.upsertReminder(row)
        }
    }

    // Una tarea que llega sin forma (tombstone mínimo) hereda la de su fila local; sin ninguna de las dos no hay nada que decidir.
    private suspend fun recordInScope(record: ServerRecord, scope: Set<SyncEntity>): Boolean = when (record.entity) {
        SyncEntity.LIST -> SyncEntity.LIST in scope
        SyncEntity.WORKSPACE, SyncEntity.BOARD_ACCESS, SyncEntity.BOARD -> SyncEntity.BOARD in scope
        SyncEntity.COLUMN -> SyncEntity.BOARD in scope && SyncEntity.COLUMN in scope
        SyncEntity.TASK -> {
            val shape = recordShape(record) ?: dao.findTask(record.id)?.shape()
            if (shape != null) SyncScope.allows(SyncEntity.TASK, shape, scope) else SyncEntity.TASK in scope
        }
        SyncEntity.SUBTASK -> {
            val taskId = record.fields["taskId"] as? String ?: dao.findSubtask(record.id)?.taskId
            val parent = taskId?.let { dao.findTask(it) }?.shape()
            // Un hijo puede llegar antes que su padre: se guarda y no se muestra hasta que el padre exista.
            if (parent != null) SyncScope.allows(SyncEntity.SUBTASK, parent, scope) else SyncEntity.SUBTASK in scope
        }
        SyncEntity.MY_DAY -> {
            val shape = dao.findTask(record.id)?.shape()
            if (shape != null) SyncScope.allows(SyncEntity.MY_DAY, shape, scope) else SyncEntity.MY_DAY in scope
        }
        SyncEntity.REMINDER -> {
            val taskId = record.fields["taskId"] as? String ?: dao.findReminder(record.id)?.taskId
            val shape = taskId?.let { dao.findTask(it) }?.shape()
            if (shape != null) SyncScope.allows(SyncEntity.REMINDER, shape, scope) else SyncEntity.REMINDER in scope
        }
        else -> false
    }

    private fun recordShape(record: ServerRecord): ScopeRow? = when {
        record.fields["listId"] is String -> ScopeRow(taskInList = true)
        record.fields["columnId"] is String -> ScopeRow(taskInBoard = true)
        else -> null
    }

    // Tras una carga completa, lo que ya estaba sincronizado y no apareció se da por borrado. Lo que nunca llegó al
    // servidor se conserva (y se vuelve a encolar); lo que está fuera del alcance ni se mira.
    private suspend fun reconcileAbsent(seen: Set<Pair<SyncEntity, String>>, scope: Set<SyncEntity>, now: Instant) {
        if (SyncEntity.LIST in scope) {
            dao.liveLists().filter { (SyncEntity.LIST to it.id) !in seen }.forEach { settleAbsent(SyncEntity.LIST, it, now) }
        }
        if (SyncEntity.BOARD in scope) {
            dao.liveBoards().filter { (SyncEntity.BOARD to it.id) !in seen }.forEach { settleAbsent(SyncEntity.BOARD, it, now) }
            if (SyncEntity.COLUMN in scope) {
                dao.liveColumns().filter { (SyncEntity.COLUMN to it.id) !in seen }.forEach { settleAbsent(SyncEntity.COLUMN, it, now) }
            }
        }
        dao.liveTasks().forEach { task ->
            val shape = task.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.TASK, shape, scope) && (SyncEntity.TASK to task.id) !in seen) {
                settleAbsent(SyncEntity.TASK, task, now)
            }
        }
        dao.liveSubtasks().forEach { subtask ->
            val parent = dao.findTask(subtask.taskId)?.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.SUBTASK, parent, scope) && (SyncEntity.SUBTASK to subtask.id) !in seen) {
                settleAbsent(SyncEntity.SUBTASK, subtask, now)
            }
        }
        dao.liveMyDay().forEach { entry ->
            val shape = dao.findTask(entry.id)?.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.MY_DAY, shape, scope) && (SyncEntity.MY_DAY to entry.id) !in seen) {
                settleAbsent(SyncEntity.MY_DAY, entry, now)
            }
        }
        dao.liveReminders().forEach { reminder ->
            val shape = dao.findTask(reminder.taskId)?.shape() ?: return@forEach
            if (SyncScope.allows(SyncEntity.REMINDER, shape, scope) && (SyncEntity.REMINDER to reminder.id) !in seen) {
                settleAbsent(SyncEntity.REMINDER, reminder, now)
            }
        }
    }

    private suspend fun settleAbsent(entity: SyncEntity, row: SyncableEntity, now: Instant) {
        when {
            row.pending -> Unit
            dao.baseline(entity.wireName, row.id) == null -> save(row.stamped(row.updatedAt, pending = true, deletedAt = null))
            else -> save(row.stamped(row.updatedAt, pending = false, deletedAt = now.toEpochMilli()))
        }
    }

    private fun SyncableEntity.stamped(updatedAt: Long, pending: Boolean, deletedAt: Long?): SyncableEntity = when (this) {
        is TaskListEntity -> copy(updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)
        is TaskEntity -> copy(updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)
        is SubtaskEntity -> copy(updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)
        is BoardEntity -> copy(updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)
        is ColumnEntity -> copy(updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)
        is MyDayEntryEntity -> copy(updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)
        is ReminderEntity ->
            copy(updatedAt = updatedAt, pending = pending, deletedAt = deletedAt, status = if (deletedAt != null && status == "PENDING") "DISMISSED" else status)
        else -> this
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
            if (SyncEntity.BOARD in scope) total += counts.boards
            if (SyncEntity.BOARD in scope && SyncEntity.COLUMN in scope) total += counts.columns
            if (SyncScope.allows(SyncEntity.MY_DAY, inList, scope)) total += counts.listMyDay
            if (SyncScope.allows(SyncEntity.MY_DAY, inBoard, scope)) total += counts.boardMyDay
            if (SyncScope.allows(SyncEntity.REMINDER, inList, scope)) total += counts.listReminders
            if (SyncScope.allows(SyncEntity.REMINDER, inBoard, scope)) total += counts.boardReminders
            total
        }.distinctUntilChanged()

    override fun observeStuckCount(): Flow<Int> = dao.observeStuckCount()

    private suspend fun rowOf(entity: SyncEntity, id: String): SyncableEntity? = when (entity) {
        SyncEntity.LIST -> dao.findList(id)
        SyncEntity.TASK -> dao.findTask(id)
        SyncEntity.SUBTASK -> dao.findSubtask(id)
        SyncEntity.BOARD -> dao.findBoard(id)
        SyncEntity.COLUMN -> dao.findColumn(id)
        SyncEntity.MY_DAY -> dao.findMyDay(id)
        SyncEntity.REMINDER -> dao.findReminder(id)
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
        val PULLED = setOf(
            SyncEntity.WORKSPACE, SyncEntity.BOARD_ACCESS, SyncEntity.BOARD, SyncEntity.COLUMN,
            SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK, SyncEntity.MY_DAY, SyncEntity.REMINDER,
        )
        const val CODE_DEPENDENCY_NOT_FOUND = "DEPENDENCY_NOT_FOUND"
        const val CODE_LIST_NAME_TAKEN = "LIST_NAME_TAKEN"
        const val CODE_TRANSIENT = "TRANSIENT"
    }
}
