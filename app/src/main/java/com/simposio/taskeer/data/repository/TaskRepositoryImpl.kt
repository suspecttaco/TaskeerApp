package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.data.local.toColumn
import com.simposio.taskeer.data.local.toTask
import com.simposio.taskeer.data.local.toCandidate
import com.simposio.taskeer.domain.column.COLUMN_NOT_FOUND
import com.simposio.taskeer.domain.column.checkWipLimit
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.task.TASK_IN_INBOX
import com.simposio.taskeer.domain.task.TASK_NO_OPEN_COLUMN
import com.simposio.taskeer.domain.task.TASK_NOT_FOUND
import com.simposio.taskeer.domain.task.assignedToColumn
import com.simposio.taskeer.domain.task.checkSameBoard
import com.simposio.taskeer.domain.task.closingColumn
import com.simposio.taskeer.domain.task.completed
import com.simposio.taskeer.domain.task.movedTask
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.domain.task.newTaskWithExtras
import com.simposio.taskeer.domain.task.reopened
import com.simposio.taskeer.domain.task.reopeningColumn
import com.simposio.taskeer.domain.task.softDeleted
import com.simposio.taskeer.domain.task.updated
import com.simposio.taskeer.domain.task.withCompletionFor
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val dao: TaskDao,
    private val columnDao: ColumnDao,
    private val now: () -> Instant = Instant::now,
) : TaskRepository {

    override fun observeTasks(boardId: String): Flow<List<Task>> =
        dao.observeByBoard(boardId).map { entities -> entities.map { it.toTask() } }

    override fun observeInbox(): Flow<List<Task>> =
        dao.observeInbox().map { entities -> entities.map { it.toTask() } }

    override fun observeActiveCountsByColumn(): Flow<Map<String, Int>> =
        dao.observeCountsByColumn().map { rows -> rows.associate { it.columnId to it.count } }

    override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> =
        dao.observeReminderCandidates().map { rows -> rows.map { it.toCandidate() } }

    override suspend fun reminderCandidates(): List<ReminderCandidate> =
        observeReminderCandidates().first()

    override suspend fun activeTaskCount(): Int = dao.countActive()

    override suspend fun getTask(id: String): DomainResult<Task> {
        val task = activeTask(id) ?: return taskNotFound()
        return DomainResult.Success(task)
    }

    override suspend fun createTask(columnId: String?, draft: TaskDraft): DomainResult<Task> {
        val column = if (columnId == null) null else activeColumn(columnId) ?: return columnNotFound()
        val position = nextPosition(columnId)
        return when (val result = newTask(columnId, draft, position, now())) {
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
    ): DomainResult<Task> {
        val column = if (columnId == null) null else activeColumn(columnId) ?: return columnNotFound()
        val position = nextPosition(columnId)
        val createdAt = now()
        val bundle = when (val result = newTaskWithExtras(columnId, creation, position, createdAt)) {
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
        val destination = activeColumn(toColumnId) ?: return columnNotFound()
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
                if (changed.isNotEmpty()) {
                    dao.upsertAll(changed.map { it.toEntity() })
                }
                DomainResult.Success(changed.firstOrNull { it.id == id } ?: current)
            }
        }
    }

    override suspend fun assignToColumn(id: String, columnId: String): DomainResult<Task> {
        val current = activeTask(id) ?: return taskNotFound()
        val destination = activeColumn(columnId) ?: return columnNotFound()
        val position = dao.findActiveByColumn(columnId).size
        return when (val result = current.assignedToColumn(columnId, position, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                checkWipLimit(destination, position)?.let { return DomainResult.Failure(it) }
                val assigned = result.value.withCompletionFor(destination, now())
                dao.upsert(assigned.toEntity())
                DomainResult.Success(assigned)
            }
        }
    }

    override suspend fun setCompleted(id: String, completed: Boolean): DomainResult<Task> {
        val current = activeTask(id) ?: return taskNotFound()
        if (current.isCompleted == completed) return DomainResult.Success(current)
        val inPlace = if (completed) current.completed(now()) else current.reopened(now())
        val sourceColumn = current.columnId?.let { activeColumn(it) } ?: return saveInPlace(inPlace)
        val columns = columnDao.findActiveByBoard(sourceColumn.boardId).map { it.toColumn() }
        val destination = when {
            completed -> closingColumn(columns).takeUnless { sourceColumn.isDoneColumn }
            sourceColumn.isDoneColumn -> reopeningColumn(columns) ?: return noOpenColumn()
            else -> null
        }
        if (destination == null) return saveInPlace(inPlace)
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
        dao.upsertAll(changed.map { it.toEntity() })
        return DomainResult.Success(changed.first { it.id == id })
    }

    private suspend fun saveInPlace(task: Task): DomainResult<Task> {
        dao.upsert(task.toEntity())
        return DomainResult.Success(task)
    }

    override suspend fun deleteTask(id: String): DomainResult<Unit> {
        val current = activeTask(id) ?: return taskNotFound()
        val deletedAt = now()
        dao.upsertDeletedWithSubtasks(
            current.softDeleted(deletedAt).toEntity(),
            deletedAt.toEpochMilli(),
        )
        return DomainResult.Success(Unit)
    }

    private suspend fun activeColumn(id: String): Column? =
        columnDao.findById(id)?.takeIf { it.deletedAt == null }?.toColumn()

    private suspend fun nextPosition(columnId: String?): Int =
        if (columnId == null) dao.countInbox() else dao.findActiveByColumn(columnId).size

    private suspend fun activeTask(id: String): Task? =
        dao.findById(id)?.toTask()?.takeUnless { it.isDeleted }

    private fun columnNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(COLUMN_NOT_FOUND, "La columna no existe"))

    private fun noOpenColumn(): DomainResult.Failure = DomainResult.Failure(
        DomainError.Conflict(TASK_NO_OPEN_COLUMN, "El tablero no tiene una columna a la que devolver la tarea"),
    )

    private fun taskNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(TASK_NOT_FOUND, "La tarea no existe"))
}
