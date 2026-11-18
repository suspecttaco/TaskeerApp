package com.simposio.taskeer.testing

import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.ReminderDao
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.SubtaskDao
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListDao
import com.simposio.taskeer.data.local.TaskListEntity
import kotlinx.coroutines.flow.Flow

class UnusedColumnDao : ColumnDao() {
    override fun observeAllActive(): Flow<List<ColumnEntity>> = unused()

    override fun observeByBoard(boardId: String): Flow<List<ColumnEntity>> = unused()

    override suspend fun findActiveByBoard(boardId: String): List<ColumnEntity> = unused()

    override suspend fun findById(id: String): ColumnEntity? = unused()

    override suspend fun upsert(column: ColumnEntity) = unused()

    override suspend fun upsertAll(columns: List<ColumnEntity>) = unused()

    override suspend fun insertBoard(board: BoardEntity) = unused()
}

class UnusedSubtaskDao : SubtaskDao() {
    override fun observeByTask(taskId: String): Flow<List<SubtaskEntity>> = unused()

    override suspend fun findActiveByTask(taskId: String): List<SubtaskEntity> = unused()

    override suspend fun findById(id: String): SubtaskEntity? = unused()

    override fun observeTotalCount(taskId: String): Flow<Int> = unused()

    override fun observeCompletedCount(taskId: String): Flow<Int> = unused()

    override suspend fun upsert(subtask: SubtaskEntity) = unused()
}

class UnusedReminderDao : ReminderDao() {
    override fun observePendingByTask(taskId: String): Flow<ReminderEntity?> = unused()

    override fun observeAllPending(): Flow<List<ReminderEntity>> = unused()

    override suspend fun findPendingByTask(taskId: String): ReminderEntity? = unused()

    override suspend fun findLatestByTask(taskId: String): ReminderEntity? = unused()

    override suspend fun findById(id: String): ReminderEntity? = unused()

    override suspend fun findPendingUpTo(limit: Long): List<ReminderEntity> = unused()

    override suspend fun minPendingAfter(after: Long): Long? = unused()

    override suspend fun upsert(reminder: ReminderEntity) = unused()

    override suspend fun markSent(id: String, now: Long): Int = unused()

    override suspend fun markDismissed(id: String, now: Long): Int = unused()
}

class UnusedTaskListDao : TaskListDao() {
    override fun observeActive(): Flow<List<TaskListEntity>> = unused()

    override suspend fun findActive(): List<TaskListEntity> = unused()

    override suspend fun findById(id: String): TaskListEntity? = unused()

    override suspend fun upsert(list: TaskListEntity) = unused()

    override suspend fun findActiveTasks(listId: String): List<TaskEntity> = unused()

    override suspend fun countTasks(listId: String): Int = unused()

    override suspend fun upsertTasks(tasks: List<TaskEntity>) = unused()
}

private fun unused(): Nothing = error("este DAO no se usa en el test")
