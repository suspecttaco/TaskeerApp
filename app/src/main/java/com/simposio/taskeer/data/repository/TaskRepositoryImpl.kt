package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.ReminderDao
import com.simposio.taskeer.data.local.SubtaskDao
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskListDao
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.data.local.toColumn
import com.simposio.taskeer.data.local.toTask
import com.simposio.taskeer.data.local.toTaskList
import com.simposio.taskeer.data.local.toCandidate
import com.simposio.taskeer.data.local.toReminder
import com.simposio.taskeer.data.local.toSubtask
import com.simposio.taskeer.domain.column.COLUMN_NOT_FOUND
import com.simposio.taskeer.domain.column.checkWipLimit
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.list.LIST_NOT_FOUND
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.task.TASK_IN_INBOX
import com.simposio.taskeer.domain.task.TASK_NO_OPEN_COLUMN
import com.simposio.taskeer.domain.task.TASK_NOT_DELETED
import com.simposio.taskeer.domain.task.TASK_NOT_FOUND
import com.simposio.taskeer.domain.task.TaskPlacement
import com.simposio.taskeer.domain.task.assignedToColumn
import com.simposio.taskeer.domain.task.checkSameBoard
import com.simposio.taskeer.domain.task.closingColumn
import com.simposio.taskeer.domain.task.completed
import com.simposio.taskeer.domain.task.movedTask
import com.simposio.taskeer.domain.task.movedToList
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.domain.task.newTaskWithExtras
import com.simposio.taskeer.domain.task.reopened
import com.simposio.taskeer.domain.task.reopeningColumn
import com.simposio.taskeer.domain.task.resolvePlacement
import com.simposio.taskeer.domain.task.restoredAt
import com.simposio.taskeer.domain.task.restoredPosition
import com.simposio.taskeer.domain.task.softDeleted
import com.simposio.taskeer.domain.task.successor
import com.simposio.taskeer.domain.task.successorReminder
import com.simposio.taskeer.domain.task.successorSubtasks
import com.simposio.taskeer.domain.task.updated
import com.simposio.taskeer.domain.task.withCompletionFor
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val dao: TaskDao,
    private val columnDao: ColumnDao,
    private val subtaskDao: SubtaskDao,
    private val reminderDao: ReminderDao,
    private val taskListDao: TaskListDao,
    private val defaultListId: suspend () -> String,
    private val now: () -> Instant = Instant::now,
    private val timeZone: TimeZoneProvider = TimeZoneProvider { ZoneId.systemDefault() },
    private val gate: BoardPermissionGate = BoardPermissionGate(),
) : TaskRepository {

    override fun observeTasks(boardId: String): Flow<List<Task>> =
        dao.observeByBoard(boardId).map { entities -> entities.map { it.toTask() } }

    override fun observeList(listId: String): Flow<List<Task>> =
        dao.observeByList(listId).map { entities -> entities.map { it.toTask() } }

    override fun observeActiveCountsByColumn(): Flow<Map<String, Int>> =
        dao.observeCountsByColumn().map { rows -> rows.associate { it.columnId to it.count } }

    override fun observeCompletedCount(): Flow<Int> = dao.observeCompletedCount()

    override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> =
        dao.observeReminderCandidates().map { rows -> rows.map { it.toCandidate() } }

    override suspend fun reminderCandidates(): List<ReminderCandidate> =
        observeReminderCandidates().first()

    override suspend fun activeTaskCount(): Int = dao.countActive()

    override suspend fun getTask(id: String): DomainResult<Task> {
        val task = activeTask(id) ?: return taskNotFound()
        return DomainResult.Success(task)
    }

    override suspend fun createTask(columnId: String?, draft: TaskDraft, listId: String?): DomainResult<Task> {
        denied(columnId)?.let { return it }
        val placement = when (val result = placementFor(columnId, listId)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        val column = (placement as? TaskPlacement.InColumn)?.let { activeColumn(it.columnId) ?: return columnNotFound() }
        val targetListId = if (placement is TaskPlacement.InColumn) null else listTarget(placement) ?: return listNotFound()
        val position = nextPosition(columnId, targetListId)
        return when (val result = newTask(columnId, draft, position, now(), listId = targetListId)) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                column?.let { checkWipLimit(it, position) }?.let { return DomainResult.Failure(it) }
                dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }
    }

    override suspend fun createTaskWithExtras(
        columnId: String?,
        creation: TaskCreation,
        listId: String?,
    ): DomainResult<Task> {
        denied(columnId)?.let { return it }
        val placement = when (val result = placementFor(columnId, listId)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        val column = (placement as? TaskPlacement.InColumn)?.let { activeColumn(it.columnId) ?: return columnNotFound() }
        val targetListId = if (placement is TaskPlacement.InColumn) null else listTarget(placement) ?: return listNotFound()
        val position = nextPosition(columnId, targetListId)
        val createdAt = now()
        val bundle = when (val result = newTaskWithExtras(columnId, creation, position, createdAt, listId = targetListId)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        column?.let { checkWipLimit(it, position) }?.let { return DomainResult.Failure(it) }
        val myDayEntry = if (creation.addToMyDay) {
            val at = createdAt.toEpochMilli()
            MyDayEntryEntity(bundle.task.id, addedAt = at, updatedAt = at, pending = true, deletedAt = null)
        } else {
            null
        }
        dao.insertTaskWithExtras(
            bundle.task.toEntity(),
            bundle.subtasks.map { it.toEntity() },
            bundle.reminder?.toEntity(),
            myDayEntry,
        )
        return DomainResult.Success(bundle.task)
    }

    override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> {
        val current = activeTask(id) ?: return taskNotFound()
        denied(current.columnId)?.let { return it }
        return when (val result = current.updated(draft, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }
    }

    override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> {
        val current = activeTask(id) ?: return taskNotFound()
        val sourceColumnId = current.columnId
            ?: return DomainResult.Failure(
                DomainError.Validation(TASK_IN_INBOX, "La tarea no tiene tablero: asígnala primero"),
            )
        denied(sourceColumnId)?.let { return it }
        val destination = activeColumn(toColumnId) ?: return columnNotFound()
        denied(toColumnId)?.let { return it }
        // El boardId de una columna no cambia, así que sirve aunque la columna origen esté borrada.
        val sourceColumn = columnDao.findById(sourceColumnId)
        val sourceBoardId = sourceColumn?.boardId
        if (sourceBoardId != null) {
            checkSameBoard(sourceBoardId, destination)?.let { return DomainResult.Failure(it) }
        }
        val source = dao.findActiveByColumn(sourceColumnId).map { it.toTask() }
        val target = if (toColumnId == sourceColumnId) source else dao.findActiveByColumn(toColumnId).map { it.toTask() }
        return when (val result = movedTask(current, source, target, toColumnId, toIndex, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                if (toColumnId != sourceColumnId) {
                    checkWipLimit(destination, target.count { it.id != id })
                        ?.let { return DomainResult.Failure(it) }
                }
                val changed = result.value.map { task ->
                    when {
                        task.id != id -> task
                        destination.isDoneColumn -> task.completed(now())
                        sourceColumn?.isDoneColumn == true -> task.reopened(now())
                        else -> task
                    }
                }
                val completedNow = destination.isDoneColumn && !current.isCompleted
                val written = when {
                    completedNow -> {
                        val sourcePosition =
                            if (toColumnId == sourceColumnId) source.size else source.count { it.id != id }
                        persistCompletion(current, changed, sourceColumnId, null, sourcePosition)
                    }
                    changed.isNotEmpty() -> changed.also { dao.upsertAll(it.map { task -> task.toEntity() }) }
                    else -> changed
                }
                DomainResult.Success(written.firstOrNull { it.id == id } ?: current)
            }
        }
    }

    override suspend fun moveToList(id: String, listId: String): DomainResult<Task> {
        val current = activeTask(id) ?: return taskNotFound()
        denied(current.columnId)?.let { return it }
        activeList(listId) ?: return listNotFound()
        return when (val result = current.movedToList(listId, dao.countInList(listId), now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                if (result.value != current) dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }
    }

    override suspend fun assignToColumn(id: String, columnId: String): DomainResult<Task> {
        val current = activeTask(id) ?: return taskNotFound()
        denied(current.columnId)?.let { return it }
        val destination = activeColumn(columnId) ?: return columnNotFound()
        denied(columnId)?.let { return it }
        val position = dao.findActiveByColumn(columnId).size
        return when (val result = current.assignedToColumn(columnId, position, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                checkWipLimit(destination, position)?.let { return DomainResult.Failure(it) }
                val assigned = result.value.withCompletionFor(destination, now())
                if (assigned.isCompleted && !current.isCompleted) {
                    val written = persistCompletion(
                        current,
                        listOf(assigned),
                        null,
                        current.listId,
                        nextPosition(null, current.listId) - 1,
                    )
                    DomainResult.Success(written.single())
                } else {
                    dao.upsert(assigned.toEntity())
                    DomainResult.Success(assigned)
                }
            }
        }
    }

    override suspend fun setCompleted(id: String, completed: Boolean): DomainResult<Task> {
        val current = activeTask(id) ?: return taskNotFound()
        if (current.isCompleted == completed) return DomainResult.Success(current)
        denied(current.columnId)?.let { return it }
        val inPlace = if (completed) current.completed(now()) else current.reopened(now())
        val sourceColumn = current.columnId?.let { activeColumn(it) }
            ?: return completeInPlace(current, inPlace, completed)
        val columns = columnDao.findActiveByBoard(sourceColumn.boardId).map { it.toColumn() }
        val destination = when {
            completed -> closingColumn(columns).takeUnless { sourceColumn.isDoneColumn }
            sourceColumn.isDoneColumn -> reopeningColumn(columns) ?: return noOpenColumn()
            else -> null
        }
        if (destination == null) return completeInPlace(current, inPlace, completed)
        val source = dao.findActiveByColumn(sourceColumn.id).map { it.toTask() }
        val target = dao.findActiveByColumn(destination.id).map { it.toTask() }
        checkWipLimit(destination, target.size)?.let { return DomainResult.Failure(it) }
        val moved = when (val result = movedTask(current, source, target, destination.id, target.size, now())) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        val changed = moved.map { task ->
            if (task.id != id) task else if (completed) task.completed(now()) else task.reopened(now())
        }
        val written = if (completed) {
            persistCompletion(current, changed, sourceColumn.id, null, source.count { it.id != id })
        } else {
            changed.also { dao.upsertAll(it.map { task -> task.toEntity() }) }
        }
        return DomainResult.Success(written.first { it.id == id })
    }

    private suspend fun completeInPlace(current: Task, inPlace: Task, completed: Boolean): DomainResult<Task> {
        if (!completed) {
            dao.upsert(inPlace.toEntity())
            return DomainResult.Success(inPlace)
        }
        return DomainResult.Success(
            persistCompletion(
                current,
                listOf(inPlace),
                current.columnId,
                current.listId,
                nextPosition(current.columnId, current.listId),
            ).single(),
        )
    }

    // La sucesora se escribe en la misma transacción que la original completada; su id determinista
    // y repeatSuccessorId evitan duplicados si se completa dos veces. No aplica el límite WIP.
    private suspend fun persistCompletion(
        original: Task,
        changed: List<Task>,
        sourceColumnId: String?,
        sourceListId: String?,
        sourcePosition: Int,
    ): List<Task> {
        val at = now()
        val zone = timeZone.current()
        // Si la lista de origen se borró entre medias, completar no falla: la sucesora va a la lista por defecto.
        val successorListId = when {
            sourceColumnId != null -> null
            sourceListId != null && activeList(sourceListId) != null -> sourceListId
            else -> defaultListId()
        }
        val position = if (sourceColumnId == null && successorListId != sourceListId) {
            dao.countInList(requireNotNull(successorListId))
        } else {
            sourcePosition
        }
        val successor = original.takeIf { it.repeatSuccessorId == null }
            ?.successor(at, zone, sourceColumnId, successorListId, position)
        if (successor == null) {
            dao.upsertAll(changed.map { it.toEntity() })
            return changed
        }
        val subtasks = subtaskDao.findActiveByTask(original.id).map { it.toSubtask() }
        val reminder = reminderDao.findLatestByTask(original.id)?.toReminder()
        val written = changed.map { if (it.id == original.id) it.copy(repeatSuccessorId = successor.id) else it }
        dao.upsertAllWithSuccessor(
            tasks = written.map { it.toEntity() },
            successor = successor.toEntity(),
            subtasks = original.successorSubtasks(successor, subtasks, zone, at).map { it.toEntity() },
            reminder = original.successorReminder(successor, reminder, at)?.toEntity(),
        )
        return written
    }

    override suspend fun deleteTask(id: String): DomainResult<Unit> {
        val current = activeTask(id) ?: return taskNotFound()
        denied(current.columnId)?.let { return it }
        val deletedAt = now()
        dao.upsertDeletedWithSubtasks(
            current.softDeleted(deletedAt).toEntity(),
            deletedAt.toEpochMilli(),
        )
        return DomainResult.Success(Unit)
    }

    // El borrado en bloque es de SQL: se comprueba antes el rol de cada tablero afectado y, si alguno no permite borrar,
    // no se borra nada (un rechazo claro en vez de un borrado parcial).
    override suspend fun deleteCompleted(): DomainResult<List<String>> {
        dao.findCompleted().mapNotNull { it.columnId }.distinct().forEach { columnId ->
            denied(columnId)?.let { return it }
        }
        return DomainResult.Success(dao.softDeleteCompleted(now().toEpochMilli()))
    }

    // Todo se valida antes de escribir: un fallo en cualquier tarea del lote no deja nada restaurado.
    override suspend fun restoreTasks(ids: List<String>): DomainResult<Unit> {
        val deleted = ids.distinct().map { id ->
            val task = dao.findById(id)?.toTask() ?: return taskNotFound()
            if (!task.isDeleted) return taskNotDeleted()
            task
        }
        val at = now()
        val byColumn = deleted.filter { it.columnId != null }.groupBy { requireNotNull(it.columnId) }
        val restored = mutableListOf<Task>()
        for ((columnId, tasks) in byColumn) {
            denied(columnId)?.let { return it }
            val column = activeColumn(columnId) ?: return columnNotFound()
            val occupied = dao.findActiveByColumn(columnId).map { it.position }.toMutableSet()
            checkWipLimit(column, occupied.size + tasks.size - 1)?.let { return DomainResult.Failure(it) }
            tasks.sortedWith(compareBy({ it.position }, { it.id })).forEach { task ->
                val position = restoredPosition(task.position, occupied, (occupied.maxOrNull() ?: -1) + 1)
                occupied += position
                restored += task.restoredAt(columnId, null, position, at)
            }
        }
        val inLists = deleted.filter { it.columnId == null }
        if (inLists.isNotEmpty()) {
            val defaultId = defaultListId()
            val targets = inLists.map { task -> task to (task.listId?.takeIf { activeList(it) != null } ?: defaultId) }
            for ((listId, group) in targets.groupBy({ it.second }, { it.first })) {
                val occupied = dao.findActiveByList(listId).map { it.position }.toMutableSet()
                group.sortedWith(compareBy({ it.position }, { it.id })).forEach { task ->
                    val keepsList = task.listId == listId
                    val position = if (keepsList) {
                        restoredPosition(task.position, occupied, (occupied.maxOrNull() ?: -1) + 1)
                    } else {
                        (occupied.maxOrNull() ?: -1) + 1
                    }
                    occupied += position
                    restored += task.restoredAt(null, listId, position, at)
                }
            }
        }
        dao.upsertRestoredWithSubtasks(
            restored.map { it.toEntity() },
            deleted.associate { it.id to requireNotNull(it.sync.deletedAt).toEpochMilli() },
            at.toEpochMilli(),
        )
        return DomainResult.Success(Unit)
    }

    // Una tarea de lista o de un tablero local propio no tiene rol: solo restringe el rol efectivo del tablero de la columna.
    private suspend fun denied(columnId: String?): DomainResult.Failure? =
        gate.denyTaskChange(columnId?.let { columnDao.findById(it)?.boardId })

    private suspend fun activeColumn(id: String): Column? =
        columnDao.findById(id)?.takeIf { it.deletedAt == null }?.toColumn()

    private suspend fun activeList(id: String): TaskList? =
        taskListDao.findById(id)?.takeIf { it.deletedAt == null }?.toTaskList()

    // La lista por defecto solo se consulta cuando no hay destino.
    private suspend fun placementFor(columnId: String?, listId: String?): DomainResult<TaskPlacement> =
        resolvePlacement(columnId, listId, if (columnId == null && listId == null) defaultListId() else "")

    private suspend fun listTarget(placement: TaskPlacement): String? = when (placement) {
        is TaskPlacement.InColumn -> null
        is TaskPlacement.InList -> placement.listId.takeIf { activeList(it) != null }
    }

    private suspend fun nextPosition(columnId: String?, listId: String?): Int = when {
        columnId != null -> dao.findActiveByColumn(columnId).size
        else -> dao.countInList(listId ?: defaultListId())
    }

    private suspend fun activeTask(id: String): Task? =
        dao.findById(id)?.toTask()?.takeUnless { it.isDeleted }

    private fun columnNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(COLUMN_NOT_FOUND, "La columna no existe"))

    private fun listNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(LIST_NOT_FOUND, "La lista no existe"))

    private fun noOpenColumn(): DomainResult.Failure = DomainResult.Failure(
        DomainError.Conflict(TASK_NO_OPEN_COLUMN, "El tablero no tiene una columna a la que devolver la tarea"),
    )

    private fun taskNotDeleted(): DomainResult.Failure =
        DomainResult.Failure(DomainError.Conflict(TASK_NOT_DELETED, "La tarea no está borrada"))

    private fun taskNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(TASK_NOT_FOUND, "La tarea no existe"))
}
