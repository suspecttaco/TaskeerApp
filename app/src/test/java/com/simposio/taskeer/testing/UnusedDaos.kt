package com.simposio.taskeer.testing

import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnCountRow
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.ListCountRow
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderDao
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.SearchTaskRow
import com.simposio.taskeer.data.local.SubtaskDao
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListDao
import com.simposio.taskeer.data.local.TaskListEntity
import kotlinx.coroutines.flow.Flow

open class UnusedColumnDao : ColumnDao() {
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

open class UnusedTaskDao : TaskDao() {
    override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = unused()

    override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = unused()

    override fun observeByList(listId: String): Flow<List<TaskEntity>> = unused()

    override suspend fun findActiveByList(listId: String): List<TaskEntity> = unused()

    override suspend fun countInList(listId: String): Int = unused()

    override fun observeActiveCountsByList(): Flow<List<ListCountRow>> = unused()

    override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = unused()

    override fun observeSearchable(): Flow<List<SearchTaskRow>> = unused()

    override suspend fun findById(id: String): TaskEntity? = unused()

    override suspend fun countByColumn(columnId: String): Int = unused()

    override suspend fun upsert(task: TaskEntity) = unused()

    override suspend fun upsertAll(tasks: List<TaskEntity>) = unused()

    override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = unused()

    override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = unused()

    override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = unused()

    override suspend fun countActive(): Int = unused()

    override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = unused()

    override fun observeCompletedCount(): Flow<Int> = unused()

    override suspend fun findCompleted(): List<TaskEntity> = unused()

    override suspend fun restoreSubtasksOfTask(taskId: String, deletedAt: Long, now: Long) = unused()

    override suspend fun restoreRemindersOfTask(taskId: String, deletedAt: Long, now: Long): Int = unused()

    override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = unused()

    override suspend fun insertReminder(reminder: ReminderEntity) = unused()

    override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) = unused()
}

private fun unused(): Nothing = error("este DAO no se usa en el test")
