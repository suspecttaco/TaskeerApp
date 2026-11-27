package com.simposio.taskeer.testing

import com.simposio.taskeer.data.local.BoardAccessEntity
import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.DISMISSED_ADDED_AT
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.PendingCountsRow
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.SyncBaselineEntity
import com.simposio.taskeer.data.local.SyncDao
import com.simposio.taskeer.data.local.SyncMetaEntity
import com.simposio.taskeer.data.local.SyncQueueEntity
import com.simposio.taskeer.data.local.SyncWorkspaceEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

// Las tablas de negocio y de sincronización en memoria. Las operaciones compuestas (@Transaction) ejecutan la
// implementación real de SyncDao sobre estas filas, así que prueban la misma lógica que Room.
@Suppress("UNCHECKED_CAST")
class FakeSyncDao : SyncDao() {
    val lists = MutableStateFlow<Map<String, TaskListEntity>>(emptyMap())
    val tasks = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())
    val subtasks = MutableStateFlow<Map<String, SubtaskEntity>>(emptyMap())
    val queueRows = MutableStateFlow<Map<String, SyncQueueEntity>>(emptyMap())
    val baselines = MutableStateFlow<Map<Pair<String, String>, SyncBaselineEntity>>(emptyMap())
    val metas = MutableStateFlow<Map<String, String>>(emptyMap())
    val boards = MutableStateFlow<Map<String, BoardEntity>>(emptyMap())
    val columns = MutableStateFlow<Map<String, ColumnEntity>>(emptyMap())
    val workspaces = MutableStateFlow<Map<String, SyncWorkspaceEntity>>(emptyMap())
    val access = MutableStateFlow<Map<String, BoardAccessEntity>>(emptyMap())
    val myDay = MutableStateFlow<Map<String, MyDayEntryEntity>>(emptyMap())
    val reminders = MutableStateFlow<Map<String, ReminderEntity>>(emptyMap())

    fun put(list: TaskListEntity) { lists.value = lists.value + (list.id to list) }
    fun put(task: TaskEntity) { tasks.value = tasks.value + (task.id to task) }
    fun put(subtask: SubtaskEntity) { subtasks.value = subtasks.value + (subtask.id to subtask) }
    fun put(board: BoardEntity) { boards.value = boards.value + (board.id to board) }
    fun put(column: ColumnEntity) { columns.value = columns.value + (column.id to column) }
    fun put(workspace: SyncWorkspaceEntity) { workspaces.value = workspaces.value + (workspace.id to workspace) }
    fun put(entry: MyDayEntryEntity) { myDay.value = myDay.value + (entry.id to entry) }
    fun put(reminder: ReminderEntity) { reminders.value = reminders.value + (reminder.id to reminder) }
    fun put(row: BoardAccessEntity) { access.value = access.value + (row.boardId to row) }
    fun baseline(entity: String, id: String, serverUpdatedAt: Long) =
        putBaseline(SyncBaselineEntity(entity, id, serverUpdatedAt))

    private fun putBaseline(row: SyncBaselineEntity) { baselines.value = baselines.value + ((row.entity to row.entityId) to row) }

    override suspend fun queue(): List<SyncQueueEntity> =
        queueRows.value.values.sortedWith(compareBy({ it.enqueuedAt }, { it.mutationId }))

    override suspend fun queueEntry(entity: String, entityId: String): SyncQueueEntity? =
        queueRows.value.values.firstOrNull { it.entity == entity && it.entityId == entityId }

    override suspend fun upsertQueue(entry: SyncQueueEntity) {
        val clash = queueRows.value.values.firstOrNull {
            it.entity == entry.entity && it.entityId == entry.entityId && it.mutationId != entry.mutationId
        }
        check(clash == null) { "UNIQUE(entity, entityId) violada" }
        queueRows.value = queueRows.value + (entry.mutationId to entry)
    }

    override suspend fun removeQueue(mutationId: String) { queueRows.value = queueRows.value - mutationId }

    override fun observeStuckCount(): Flow<Int> = queueRows.map { rows -> rows.values.count { it.state == "STUCK" } }

    override suspend fun baseline(entity: String, entityId: String): SyncBaselineEntity? = baselines.value[entity to entityId]

    override suspend fun upsertBaseline(baseline: SyncBaselineEntity) = putBaseline(baseline)

    override suspend fun removeBaseline(entity: String, entityId: String) { baselines.value = baselines.value - (entity to entityId) }

    override suspend fun meta(key: String): String? = metas.value[key]

    override suspend fun upsertMeta(meta: SyncMetaEntity) { metas.value = metas.value + (meta.key to meta.value) }

    override suspend fun removeMeta(key: String) { metas.value = metas.value - key }

    override suspend fun pendingLists(): List<TaskListEntity> = lists.value.values.filter { it.pending }

    override suspend fun pendingTasks(): List<TaskEntity> = tasks.value.values.filter { it.pending }

    override suspend fun pendingSubtasks(): List<SubtaskEntity> = subtasks.value.values.filter { it.pending }

    override suspend fun pendingMyDay(): List<MyDayEntryEntity> = myDay.value.values.filter { it.pending }

    override suspend fun pendingReminders(): List<ReminderEntity> = reminders.value.values.filter { it.pending }

    override suspend fun liveMyDay(): List<MyDayEntryEntity> = myDay.value.values.filter { it.deletedAt == null }

    override suspend fun liveReminders(): List<ReminderEntity> = reminders.value.values.filter { it.deletedAt == null }

    override suspend fun findMyDay(id: String): MyDayEntryEntity? = myDay.value[id]

    override suspend fun findReminder(id: String): ReminderEntity? = reminders.value[id]

    override suspend fun upsertMyDay(entry: MyDayEntryEntity) = put(entry)

    override suspend fun upsertReminder(reminder: ReminderEntity) = put(reminder)

    override suspend fun clearPendingMyDay(id: String, version: Long): Int =
        myDay.value[id]?.takeIf { it.updatedAt == version }?.let { put(it.copy(pending = false)); 1 } ?: 0

    override suspend fun clearPendingReminder(id: String, version: Long): Int =
        reminders.value[id]?.takeIf { it.updatedAt == version }?.let { put(it.copy(pending = false)); 1 } ?: 0

    override suspend fun markUnsyncedMyDayPending() {
        myDay.value.values.filter { it.deletedAt == null && baselines.value[("myDay" to it.id)] == null }.forEach { put(it.copy(pending = true)) }
    }

    override suspend fun markUnsyncedRemindersPending() {
        reminders.value.values.filter { it.deletedAt == null && baselines.value[("reminder" to it.id)] == null }.forEach { put(it.copy(pending = true)) }
    }

    override suspend fun liveLists(): List<TaskListEntity> = lists.value.values.filter { it.deletedAt == null }

    override suspend fun liveTasks(): List<TaskEntity> = tasks.value.values.filter { it.deletedAt == null }

    override suspend fun liveSubtasks(): List<SubtaskEntity> = subtasks.value.values.filter { it.deletedAt == null }

    // Room hace rollback si el bloque lanza; aquí se restaura el estado en memoria para poder probar la atomicidad.
    var transactions = 0
        private set

    // Hace fallar la n-ésima escritura de tarea (1 = la primera) para simular un corte a mitad de una página.
    var failTaskUpsertAt: Int? = null
    private var taskUpserts = 0

    override suspend fun inTransaction(block: suspend () -> Unit) {
        transactions++
        val before = listOf(
            lists.value, tasks.value, subtasks.value, queueRows.value, baselines.value, metas.value,
            boards.value, columns.value, workspaces.value, access.value, myDay.value, reminders.value,
        )
        try {
            super.inTransaction(block)
        } catch (error: Throwable) {
            lists.value = before[0] as Map<String, TaskListEntity>
            tasks.value = before[1] as Map<String, TaskEntity>
            subtasks.value = before[2] as Map<String, SubtaskEntity>
            queueRows.value = before[3] as Map<String, SyncQueueEntity>
            baselines.value = before[4] as Map<Pair<String, String>, SyncBaselineEntity>
            metas.value = before[5] as Map<String, String>
            boards.value = before[6] as Map<String, BoardEntity>
            columns.value = before[7] as Map<String, ColumnEntity>
            workspaces.value = before[8] as Map<String, SyncWorkspaceEntity>
            access.value = before[9] as Map<String, BoardAccessEntity>
            myDay.value = before[10] as Map<String, MyDayEntryEntity>
            reminders.value = before[11] as Map<String, ReminderEntity>
            throw error
        }
    }

    override suspend fun pendingBoards(): List<BoardEntity> = boards.value.values.filter { it.pending }

    override suspend fun pendingColumns(): List<ColumnEntity> = columns.value.values.filter { it.pending }

    override suspend fun liveBoards(): List<BoardEntity> = boards.value.values.filter { it.deletedAt == null }

    override suspend fun liveColumns(): List<ColumnEntity> = columns.value.values.filter { it.deletedAt == null }

    override suspend fun findBoard(id: String): BoardEntity? = boards.value[id]

    override suspend fun findColumn(id: String): ColumnEntity? = columns.value[id]

    override suspend fun liveColumnsOf(boardId: String): List<ColumnEntity> =
        columns.value.values.filter { it.boardId == boardId && it.deletedAt == null }.sortedWith(compareBy({ it.position }, { it.id }))

    override suspend fun upsertBoard(board: BoardEntity) = put(board)

    override suspend fun upsertColumn(column: ColumnEntity) = put(column)

    override suspend fun clearPendingBoard(id: String, version: Long): Int =
        boards.value[id]?.takeIf { it.updatedAt == version }?.let { put(it.copy(pending = false)); 1 } ?: 0

    override suspend fun clearPendingColumn(id: String, version: Long): Int =
        columns.value[id]?.takeIf { it.updatedAt == version }?.let { put(it.copy(pending = false)); 1 } ?: 0

    override suspend fun markUnsyncedBoardsPending() {
        boards.value.values.filter { it.deletedAt == null && baselines.value[("board" to it.id)] == null }.forEach { put(it.copy(pending = true)) }
    }

    override suspend fun markUnsyncedColumnsPending() {
        columns.value.values.filter { it.deletedAt == null && baselines.value[("column" to it.id)] == null }.forEach { put(it.copy(pending = true)) }
    }

    override suspend fun upsertWorkspace(workspace: SyncWorkspaceEntity) = put(workspace)

    override suspend fun findWorkspace(id: String): SyncWorkspaceEntity? = workspaces.value[id]

    override suspend fun personalWorkspaceId(): String? =
        workspaces.value.values.filter { it.isPersonal && it.deletedAt == null }.minOfOrNull { it.id }

    override suspend fun boardIdsOfWorkspace(workspaceId: String): List<String> =
        boards.value.values.filter { it.workspaceId == workspaceId }.map { it.id }

    override suspend fun upsertAccess(access: BoardAccessEntity) = put(access)

    override suspend fun findAccess(boardId: String): BoardAccessEntity? = access.value[boardId]

    override suspend fun removeAccess(boardId: String) { access.value = access.value - boardId }

    override suspend fun assignWorkspace(id: String, workspaceId: String) {
        boards.value[id]?.takeIf { it.workspaceId == null }?.let { put(it.copy(workspaceId = workspaceId)) }
    }

    private fun columnIdsOf(boardId: String) = columns.value.values.filter { it.boardId == boardId }.map { it.id }.toSet()

    private fun taskIdsOf(boardId: String) = columnIdsOf(boardId).let { ids -> tasks.value.values.filter { it.columnId in ids }.map { it.id }.toSet() }

    private fun subtaskIdsOf(boardId: String) = taskIdsOf(boardId).let { ids -> subtasks.value.values.filter { it.taskId in ids }.map { it.id }.toSet() }

    private fun reminderIdsOf(boardId: String) = taskIdsOf(boardId).let { ids -> reminders.value.values.filter { it.taskId in ids }.map { it.id }.toSet() }

    override suspend fun purgeQueueOfBoard(boardId: String) {
        val doomed = mapOf(
            "subtask" to subtaskIdsOf(boardId),
            "task" to taskIdsOf(boardId),
            "myDay" to taskIdsOf(boardId),
            "reminder" to reminderIdsOf(boardId),
            "column" to columnIdsOf(boardId),
            "board" to setOf(boardId),
        )
        queueRows.value = queueRows.value.filterValues { it.entityId !in doomed[it.entity].orEmpty() }
    }

    override suspend fun purgeBaselineOfBoard(boardId: String) {
        val doomed = mapOf(
            "subtask" to subtaskIdsOf(boardId),
            "task" to taskIdsOf(boardId),
            "myDay" to taskIdsOf(boardId),
            "reminder" to reminderIdsOf(boardId),
            "column" to columnIdsOf(boardId),
            "board" to setOf(boardId),
        )
        baselines.value = baselines.value.filterKeys { (entity, id) -> id !in doomed[entity].orEmpty() }
    }

    override suspend fun purgeSubtasksOfBoard(boardId: String) {
        val ids = taskIdsOf(boardId)
        subtasks.value = subtasks.value.filterValues { it.taskId !in ids }
    }

    override suspend fun purgeMyDayOfBoard(boardId: String) { myDay.value = myDay.value - taskIdsOf(boardId) }

    override suspend fun purgeRemindersOfBoard(boardId: String) {
        val ids = taskIdsOf(boardId)
        reminders.value = reminders.value.filterValues { it.taskId !in ids }
    }

    override suspend fun purgeTasksOfBoard(boardId: String) {
        val ids = taskIdsOf(boardId)
        tasks.value = tasks.value.filterKeys { it !in ids }
    }

    override suspend fun purgeColumnsOfBoard(boardId: String) { columns.value = columns.value.filterValues { it.boardId != boardId } }

    override suspend fun purgeBoardRow(boardId: String) { boards.value = boards.value - boardId }

    override suspend fun wipeBoardAccess() { wipedBusinessTables += "board_access"; access.value = emptyMap() }

    override suspend fun wipeWorkspaces() { wipedBusinessTables += "sync_workspaces"; workspaces.value = emptyMap() }

    override suspend fun findList(id: String): TaskListEntity? = lists.value[id]

    override suspend fun findTask(id: String): TaskEntity? = tasks.value[id]

    override suspend fun findSubtask(id: String): SubtaskEntity? = subtasks.value[id]

    override suspend fun upsertList(list: TaskListEntity) = put(list)

    override suspend fun upsertTask(task: TaskEntity) {
        check(failTaskUpsertAt != ++taskUpserts) { "fallo inyectado al escribir una tarea" }
        put(task)
    }

    override suspend fun upsertSubtask(subtask: SubtaskEntity) = put(subtask)

    override suspend fun clearPendingList(id: String, version: Long): Int =
        lists.value[id]?.takeIf { it.updatedAt == version }?.let { put(it.copy(pending = false)); 1 } ?: 0

    override suspend fun clearPendingTask(id: String, version: Long): Int =
        tasks.value[id]?.takeIf { it.updatedAt == version }?.let { put(it.copy(pending = false)); 1 } ?: 0

    override suspend fun clearPendingSubtask(id: String, version: Long): Int =
        subtasks.value[id]?.takeIf { it.updatedAt == version }?.let { put(it.copy(pending = false)); 1 } ?: 0

    override suspend fun markUnsyncedListsPending() {
        lists.value.values.filter { it.deletedAt == null && baselines.value[("list" to it.id)] == null }.forEach { put(it.copy(pending = true)) }
    }

    override suspend fun markUnsyncedTasksPending() {
        tasks.value.values.filter { it.deletedAt == null && baselines.value[("task" to it.id)] == null }.forEach { put(it.copy(pending = true)) }
    }

    override suspend fun markUnsyncedSubtasksPending() {
        subtasks.value.values.filter { it.deletedAt == null && baselines.value[("subtask" to it.id)] == null }.forEach { put(it.copy(pending = true)) }
    }

    override suspend fun repointTasks(oldId: String, newId: String) {
        tasks.value.values.filter { it.listId == oldId }.forEach { put(it.copy(listId = newId)) }
    }

    override suspend fun moveTasksToList(oldId: String, newId: String) {
        tasks.value.values.filter { it.listId == oldId }.forEach { put(it.copy(listId = newId, pending = true)) }
    }

    override suspend fun renameList(oldId: String, newId: String) {
        val row = lists.value[oldId] ?: return
        lists.value = lists.value - oldId + (newId to row.copy(id = newId))
    }

    override suspend fun renameListInQueue(oldId: String, newId: String) {
        queueRows.value = queueRows.value.mapValues { (_, row) ->
            if (row.entity == "list" && row.entityId == oldId) row.copy(entityId = newId) else row
        }
    }

    override suspend fun renameListInBaseline(oldId: String, newId: String) {
        val row = baselines.value[("list" to oldId)] ?: return
        baselines.value = baselines.value - ("list" to oldId) + (("list" to newId) to row.copy(entityId = newId))
    }

    override suspend fun purgeList(id: String) { lists.value = lists.value - id }

    override fun observePendingCounts(): Flow<PendingCountsRow> = combine(listOf(lists, tasks, subtasks, boards, columns, myDay, reminders)) { values ->
        val l = values[0] as Map<String, TaskListEntity>
        val t = values[1] as Map<String, TaskEntity>
        val s = values[2] as Map<String, SubtaskEntity>
        val pendingTasks = t.values.filter { it.pending }
        val pendingSubtasks = s.values.filter { it.pending }
        val pendingMyDay = (values[5] as Map<String, MyDayEntryEntity>).values.filter { it.pending && it.addedAt != DISMISSED_ADDED_AT }
        val pendingReminders = (values[6] as Map<String, ReminderEntity>).values.filter { it.pending }
        fun parentIsList(sub: SubtaskEntity) = t[sub.taskId]?.listId != null
        fun parentIsBoard(sub: SubtaskEntity) = t[sub.taskId]?.columnId != null
        PendingCountsRow(
            lists = l.values.count { it.pending },
            listTasks = pendingTasks.count { it.listId != null },
            boardTasks = pendingTasks.count { it.columnId != null },
            listSubtasks = pendingSubtasks.count(::parentIsList),
            boardSubtasks = pendingSubtasks.count(::parentIsBoard),
            boards = boards.value.values.count { it.pending },
            columns = columns.value.values.count { it.pending },
            listMyDay = pendingMyDay.count { t[it.id]?.listId != null },
            boardMyDay = pendingMyDay.count { t[it.id]?.columnId != null },
            listReminders = pendingReminders.count { t[it.taskId]?.listId != null },
            boardReminders = pendingReminders.count { t[it.taskId]?.columnId != null },
        )
    }

    var wipedBusinessTables: MutableList<String> = mutableListOf()

    override suspend fun wipeBoards() { wipedBusinessTables += "boards"; boards.value = emptyMap() }

    override suspend fun wipeColumns() { wipedBusinessTables += "columns"; columns.value = emptyMap() }

    override suspend fun wipeTasks() { wipedBusinessTables += "tasks"; tasks.value = emptyMap() }

    override suspend fun wipeSubtasks() { wipedBusinessTables += "subtasks"; subtasks.value = emptyMap() }

    override suspend fun wipeMyDay() { wipedBusinessTables += "my_day_tasks"; myDay.value = emptyMap() }

    override suspend fun wipeReminders() { wipedBusinessTables += "reminders"; reminders.value = emptyMap() }

    override suspend fun wipeNotifications() { wipedBusinessTables += "app_notifications" }

    override suspend fun wipeLists() { wipedBusinessTables += "task_lists"; lists.value = emptyMap() }

    override suspend fun wipeQueue() { queueRows.value = emptyMap() }

    override suspend fun wipeBaseline() { baselines.value = emptyMap() }

    override suspend fun wipeMeta() { metas.value = emptyMap() }
}
