package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.task.newTask
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

class FakeDestinationBoardRepository(
    val boards: MutableStateFlow<List<Board>> = MutableStateFlow(emptyList()),
) : BoardRepository {
    override fun observeBoards(): Flow<List<Board>> = boards

    override suspend fun createBoard(name: String): DomainResult<Board> = unused()

    override suspend fun renameBoard(id: String, name: String): DomainResult<Board> = unused()

    override suspend fun deleteBoard(id: String): DomainResult<Unit> = unused()

    private fun unused(): Nothing = error("no se usa en este test")
}

class FakeDestinationColumnRepository(
    val columns: MutableStateFlow<List<Column>> = MutableStateFlow(emptyList()),
) : ColumnRepository {
    override fun observeColumns(boardId: String): Flow<List<Column>> = emptyFlow()

    override fun observeAllColumns(): Flow<List<Column>> = columns

    override suspend fun createColumn(boardId: String, name: String): DomainResult<Column> = unused()

    override suspend fun renameColumn(id: String, name: String): DomainResult<Column> = unused()

    override suspend fun setWipLimit(columnId: String, limit: Int?): DomainResult<Column> = unused()

    override suspend fun moveColumn(id: String, toIndex: Int): DomainResult<Column> = unused()

    override suspend fun deleteColumn(id: String): DomainResult<Unit> = unused()

    private fun unused(): Nothing = error("no se usa en este test")
}

class FakeDestinationTaskRepository : TaskRepository {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val inbox = MutableStateFlow<List<Task>>(emptyList())
    val creations = mutableListOf<Pair<String?, TaskCreation>>()
    val assignments = mutableListOf<Pair<String, String>>()
    var createFailure: DomainError? = null
    var assignFailure: DomainError? = null

    override fun observeInbox(): Flow<List<Task>> = inbox

    override fun observeActiveCountsByColumn(): Flow<Map<String, Int>> = counts

    override suspend fun createTaskWithExtras(columnId: String?, creation: TaskCreation): DomainResult<Task> {
        creations += columnId to creation
        createFailure?.let { return DomainResult.Failure(it) }
        return newTask(columnId, creation.task, position = 0, now = Instant.EPOCH).also { result ->
            (result as? DomainResult.Success)?.let { inbox.value = inbox.value + it.value }
        }
    }

    override suspend fun assignToColumn(id: String, columnId: String): DomainResult<Task> {
        assignments += id to columnId
        assignFailure?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(inbox.value.first { it.id == id }.copy(columnId = columnId))
    }

    override fun observeTasks(boardId: String): Flow<List<Task>> = emptyFlow()

    override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> = emptyFlow()

    override suspend fun reminderCandidates(): List<ReminderCandidate> = emptyList()

    override suspend fun activeTaskCount(): Int = 0

    override suspend fun getTask(id: String): DomainResult<Task> = unused()

    override suspend fun createTask(columnId: String?, draft: TaskDraft): DomainResult<Task> = unused()

    override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> = unused()

    override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> = unused()

    override suspend fun deleteTask(id: String): DomainResult<Unit> = unused()

    private fun unused(): Nothing = error("no se usa en este test")
}

fun testBoard(id: String, createdAt: Instant = Instant.EPOCH) = Board(
    name = "Tablero $id",
    createdAt = createdAt,
    sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
)

fun testColumn(
    id: String,
    boardId: String,
    position: Int = 0,
    isDone: Boolean = false,
    wipLimit: Int? = null,
) = Column(
    boardId = boardId,
    name = "Columna $id",
    position = position,
    wipLimit = wipLimit,
    isDoneColumn = isDone,
    createdAt = Instant.EPOCH,
    sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
)
