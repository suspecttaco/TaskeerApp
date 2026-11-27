package com.simposio.taskeer.testing

import com.simposio.taskeer.data.local.PendingCountsRow
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.SyncBaselineEntity
import com.simposio.taskeer.data.local.SyncDao
import com.simposio.taskeer.data.local.SyncMetaEntity
import com.simposio.taskeer.data.local.SyncQueueEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

// Las tablas de negocio y de sincronización en memoria. Las operaciones compuestas (@Transaction) ejecutan la
// implementación real de SyncDao sobre estas filas, así que prueban la misma lógica que Room.
class FakeSyncDao : SyncDao() {
    val lists = MutableStateFlow<Map<String, TaskListEntity>>(emptyMap())
    val tasks = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())
    val subtasks = MutableStateFlow<Map<String, SubtaskEntity>>(emptyMap())
    val queueRows = MutableStateFlow<Map<String, SyncQueueEntity>>(emptyMap())
    val baselines = MutableStateFlow<Map<Pair<String, String>, SyncBaselineEntity>>(emptyMap())
    val metas = MutableStateFlow<Map<String, String>>(emptyMap())

    fun put(list: TaskListEntity) { lists.value = lists.value + (list.id to list) }
    fun put(task: TaskEntity) { tasks.value = tasks.value + (task.id to task) }
    fun put(subtask: SubtaskEntity) { subtasks.value = subtasks.value + (subtask.id to subtask) }
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

    override suspend fun findList(id: String): TaskListEntity? = lists.value[id]

    override suspend fun findTask(id: String): TaskEntity? = tasks.value[id]

    override suspend fun findSubtask(id: String): SubtaskEntity? = subtasks.value[id]

    override suspend fun upsertList(list: TaskListEntity) = put(list)

    override suspend fun upsertTask(task: TaskEntity) = put(task)

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

    override fun observePendingCounts(): Flow<PendingCountsRow> = combine(lists, tasks, subtasks) { l, t, s ->
        val pendingTasks = t.values.filter { it.pending }
        val pendingSubtasks = s.values.filter { it.pending }
        fun parentIsList(sub: SubtaskEntity) = t[sub.taskId]?.listId != null
        fun parentIsBoard(sub: SubtaskEntity) = t[sub.taskId]?.columnId != null
        PendingCountsRow(
            lists = l.values.count { it.pending },
            listTasks = pendingTasks.count { it.listId != null },
            boardTasks = pendingTasks.count { it.columnId != null },
            listSubtasks = pendingSubtasks.count(::parentIsList),
            boardSubtasks = pendingSubtasks.count(::parentIsBoard),
        )
    }

    var wipedBusinessTables: MutableList<String> = mutableListOf()

    override suspend fun wipeBoards() { wipedBusinessTables += "boards" }

    override suspend fun wipeColumns() { wipedBusinessTables += "columns" }

    override suspend fun wipeTasks() { wipedBusinessTables += "tasks"; tasks.value = emptyMap() }

    override suspend fun wipeSubtasks() { wipedBusinessTables += "subtasks"; subtasks.value = emptyMap() }

    override suspend fun wipeMyDay() { wipedBusinessTables += "my_day_tasks" }

    override suspend fun wipeReminders() { wipedBusinessTables += "reminders" }

    override suspend fun wipeNotifications() { wipedBusinessTables += "app_notifications" }

    override suspend fun wipeLists() { wipedBusinessTables += "task_lists"; lists.value = emptyMap() }

    override suspend fun wipeQueue() { queueRows.value = emptyMap() }

    override suspend fun wipeBaseline() { baselines.value = emptyMap() }

    override suspend fun wipeMeta() { metas.value = emptyMap() }
}
