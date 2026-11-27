package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.simposio.taskeer.domain.sync.SyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SyncDao {

    @Query("SELECT * FROM sync_queue ORDER BY enqueuedAt ASC, mutationId ASC")
    abstract suspend fun queue(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE entity = :entity AND entityId = :entityId")
    abstract suspend fun queueEntry(entity: String, entityId: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertQueue(entry: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE mutationId = :mutationId")
    abstract suspend fun removeQueue(mutationId: String)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE state = '$QUEUE_STATE_STUCK'")
    abstract fun observeStuckCount(): Flow<Int>

    @Query("SELECT * FROM sync_baseline WHERE entity = :entity AND entityId = :entityId")
    abstract suspend fun baseline(entity: String, entityId: String): SyncBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBaseline(baseline: SyncBaselineEntity)

    @Query("DELETE FROM sync_baseline WHERE entity = :entity AND entityId = :entityId")
    abstract suspend fun removeBaseline(entity: String, entityId: String)

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMeta(meta: SyncMetaEntity)

    @Query("DELETE FROM sync_meta WHERE `key` = :key")
    abstract suspend fun removeMeta(key: String)

    @Query("SELECT * FROM task_lists WHERE pending = 1")
    abstract suspend fun pendingLists(): List<TaskListEntity>

    @Query("SELECT * FROM tasks WHERE pending = 1")
    abstract suspend fun pendingTasks(): List<TaskEntity>

    @Query("SELECT * FROM subtasks WHERE pending = 1")
    abstract suspend fun pendingSubtasks(): List<SubtaskEntity>

    @Query("SELECT * FROM boards WHERE pending = 1")
    abstract suspend fun pendingBoards(): List<BoardEntity>

    @Query("SELECT * FROM columns WHERE pending = 1")
    abstract suspend fun pendingColumns(): List<ColumnEntity>

    @Query("SELECT * FROM my_day_tasks WHERE pending = 1")
    abstract suspend fun pendingMyDay(): List<MyDayEntryEntity>

    @Query("SELECT * FROM reminders WHERE pending = 1")
    abstract suspend fun pendingReminders(): List<ReminderEntity>

    @Query("SELECT * FROM my_day_tasks WHERE deletedAt IS NULL")
    abstract suspend fun liveMyDay(): List<MyDayEntryEntity>

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL")
    abstract suspend fun liveReminders(): List<ReminderEntity>

    @Query("SELECT * FROM my_day_tasks WHERE id = :id")
    abstract suspend fun findMyDay(id: String): MyDayEntryEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    abstract suspend fun findReminder(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMyDay(entry: MyDayEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertReminder(reminder: ReminderEntity)

    @Query("UPDATE my_day_tasks SET pending = 0 WHERE id = :id AND updatedAt = :version")
    abstract suspend fun clearPendingMyDay(id: String, version: Long): Int

    @Query("UPDATE reminders SET pending = 0 WHERE id = :id AND updatedAt = :version")
    abstract suspend fun clearPendingReminder(id: String, version: Long): Int

    @Query(
        "UPDATE my_day_tasks SET pending = 1 WHERE deletedAt IS NULL AND id NOT IN " +
            "(SELECT entityId FROM sync_baseline WHERE entity = 'myDay')",
    )
    abstract suspend fun markUnsyncedMyDayPending()

    @Query(
        "UPDATE reminders SET pending = 1 WHERE deletedAt IS NULL AND id NOT IN " +
            "(SELECT entityId FROM sync_baseline WHERE entity = 'reminder')",
    )
    abstract suspend fun markUnsyncedRemindersPending()

    @Query("SELECT * FROM boards WHERE deletedAt IS NULL")
    abstract suspend fun liveBoards(): List<BoardEntity>

    @Query("SELECT * FROM columns WHERE deletedAt IS NULL")
    abstract suspend fun liveColumns(): List<ColumnEntity>

    @Query("SELECT * FROM boards WHERE id = :id")
    abstract suspend fun findBoard(id: String): BoardEntity?

    @Query("SELECT * FROM columns WHERE id = :id")
    abstract suspend fun findColumn(id: String): ColumnEntity?

    @Query("SELECT * FROM columns WHERE boardId = :boardId AND deletedAt IS NULL ORDER BY position ASC, id ASC")
    abstract suspend fun liveColumnsOf(boardId: String): List<ColumnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBoard(board: BoardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertColumn(column: ColumnEntity)

    @Query("UPDATE boards SET pending = 0 WHERE id = :id AND updatedAt = :version")
    abstract suspend fun clearPendingBoard(id: String, version: Long): Int

    @Query("UPDATE columns SET pending = 0 WHERE id = :id AND updatedAt = :version")
    abstract suspend fun clearPendingColumn(id: String, version: Long): Int

    @Query(
        "UPDATE boards SET pending = 1 WHERE deletedAt IS NULL AND id NOT IN " +
            "(SELECT entityId FROM sync_baseline WHERE entity = 'board')",
    )
    abstract suspend fun markUnsyncedBoardsPending()

    @Query(
        "UPDATE columns SET pending = 1 WHERE deletedAt IS NULL AND id NOT IN " +
            "(SELECT entityId FROM sync_baseline WHERE entity = 'column')",
    )
    abstract suspend fun markUnsyncedColumnsPending()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertWorkspace(workspace: SyncWorkspaceEntity)

    @Query("SELECT * FROM sync_workspaces WHERE id = :id")
    abstract suspend fun findWorkspace(id: String): SyncWorkspaceEntity?

    @Query("SELECT id FROM sync_workspaces WHERE isPersonal = 1 AND deletedAt IS NULL ORDER BY id ASC LIMIT 1")
    abstract suspend fun personalWorkspaceId(): String?

    @Query("SELECT id FROM boards WHERE workspaceId = :workspaceId")
    abstract suspend fun boardIdsOfWorkspace(workspaceId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAccess(access: BoardAccessEntity)

    @Query("SELECT * FROM board_access WHERE boardId = :boardId")
    abstract suspend fun findAccess(boardId: String): BoardAccessEntity?

    @Query("DELETE FROM board_access WHERE boardId = :boardId")
    abstract suspend fun removeAccess(boardId: String)

    @Query("UPDATE boards SET workspaceId = :workspaceId WHERE id = :id AND workspaceId IS NULL")
    abstract suspend fun assignWorkspace(id: String, workspaceId: String)

    @Query("SELECT * FROM task_lists WHERE deletedAt IS NULL")
    abstract suspend fun liveLists(): List<TaskListEntity>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    abstract suspend fun liveTasks(): List<TaskEntity>

    @Query("SELECT * FROM subtasks WHERE deletedAt IS NULL")
    abstract suspend fun liveSubtasks(): List<SubtaskEntity>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    abstract suspend fun findList(id: String): TaskListEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun findTask(id: String): TaskEntity?

    @Query("SELECT * FROM subtasks WHERE id = :id")
    abstract suspend fun findSubtask(id: String): SubtaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertList(list: TaskListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSubtask(subtask: SubtaskEntity)

    // updatedAt = version: si el usuario editó mientras viajaba el envío, la fila sigue pendiente.
    @Query("UPDATE task_lists SET pending = 0 WHERE id = :id AND updatedAt = :version")
    abstract suspend fun clearPendingList(id: String, version: Long): Int

    @Query("UPDATE tasks SET pending = 0 WHERE id = :id AND updatedAt = :version")
    abstract suspend fun clearPendingTask(id: String, version: Long): Int

    @Query("UPDATE subtasks SET pending = 0 WHERE id = :id AND updatedAt = :version")
    abstract suspend fun clearPendingSubtask(id: String, version: Long): Int

    @Query(
        "UPDATE task_lists SET pending = 1 WHERE deletedAt IS NULL AND id NOT IN " +
            "(SELECT entityId FROM sync_baseline WHERE entity = 'list')",
    )
    abstract suspend fun markUnsyncedListsPending()

    @Query(
        "UPDATE tasks SET pending = 1 WHERE deletedAt IS NULL AND id NOT IN " +
            "(SELECT entityId FROM sync_baseline WHERE entity = 'task')",
    )
    abstract suspend fun markUnsyncedTasksPending()

    @Query(
        "UPDATE subtasks SET pending = 1 WHERE deletedAt IS NULL AND id NOT IN " +
            "(SELECT entityId FROM sync_baseline WHERE entity = 'subtask')",
    )
    abstract suspend fun markUnsyncedSubtasksPending()

    @Query("UPDATE tasks SET listId = :newId WHERE listId = :oldId")
    abstract suspend fun repointTasks(oldId: String, newId: String)

    @Query("UPDATE tasks SET listId = :newId, pending = 1 WHERE listId = :oldId")
    abstract suspend fun moveTasksToList(oldId: String, newId: String)

    @Query("UPDATE task_lists SET id = :newId WHERE id = :oldId")
    abstract suspend fun renameList(oldId: String, newId: String)

    @Query("UPDATE sync_queue SET entityId = :newId WHERE entity = 'list' AND entityId = :oldId")
    abstract suspend fun renameListInQueue(oldId: String, newId: String)

    @Query("UPDATE sync_baseline SET entityId = :newId WHERE entity = 'list' AND entityId = :oldId")
    abstract suspend fun renameListInBaseline(oldId: String, newId: String)

    @Query("DELETE FROM task_lists WHERE id = :id")
    abstract suspend fun purgeList(id: String)

    @Query(
        "SELECT " +
            "(SELECT COUNT(*) FROM task_lists WHERE pending = 1) AS lists, " +
            "(SELECT COUNT(*) FROM tasks WHERE pending = 1 AND listId IS NOT NULL) AS listTasks, " +
            "(SELECT COUNT(*) FROM tasks WHERE pending = 1 AND columnId IS NOT NULL) AS boardTasks, " +
            "(SELECT COUNT(*) FROM subtasks s WHERE s.pending = 1 AND EXISTS " +
            "(SELECT 1 FROM tasks t WHERE t.id = s.taskId AND t.listId IS NOT NULL)) AS listSubtasks, " +
            "(SELECT COUNT(*) FROM subtasks s WHERE s.pending = 1 AND EXISTS " +
            "(SELECT 1 FROM tasks t WHERE t.id = s.taskId AND t.columnId IS NOT NULL)) AS boardSubtasks, " +
            "(SELECT COUNT(*) FROM boards WHERE pending = 1) AS boards, " +
            "(SELECT COUNT(*) FROM columns WHERE pending = 1) AS columns, " +
            "(SELECT COUNT(*) FROM my_day_tasks m WHERE m.pending = 1 AND m.addedAt <> $DISMISSED_ADDED_AT AND EXISTS " +
            "(SELECT 1 FROM tasks t WHERE t.id = m.id AND t.listId IS NOT NULL)) AS listMyDay, " +
            "(SELECT COUNT(*) FROM my_day_tasks m WHERE m.pending = 1 AND m.addedAt <> $DISMISSED_ADDED_AT AND EXISTS " +
            "(SELECT 1 FROM tasks t WHERE t.id = m.id AND t.columnId IS NOT NULL)) AS boardMyDay, " +
            "(SELECT COUNT(*) FROM reminders r WHERE r.pending = 1 AND EXISTS " +
            "(SELECT 1 FROM tasks t WHERE t.id = r.taskId AND t.listId IS NOT NULL)) AS listReminders, " +
            "(SELECT COUNT(*) FROM reminders r WHERE r.pending = 1 AND EXISTS " +
            "(SELECT 1 FROM tasks t WHERE t.id = r.taskId AND t.columnId IS NOT NULL)) AS boardReminders",
    )
    abstract fun observePendingCounts(): Flow<PendingCountsRow>

    @Query("DELETE FROM boards")
    abstract suspend fun wipeBoards()

    @Query("DELETE FROM columns")
    abstract suspend fun wipeColumns()

    @Query("DELETE FROM tasks")
    abstract suspend fun wipeTasks()

    @Query("DELETE FROM subtasks")
    abstract suspend fun wipeSubtasks()

    @Query("DELETE FROM my_day_tasks")
    abstract suspend fun wipeMyDay()

    @Query("DELETE FROM reminders")
    abstract suspend fun wipeReminders()

    @Query("DELETE FROM app_notifications")
    abstract suspend fun wipeNotifications()

    @Query("DELETE FROM task_lists")
    abstract suspend fun wipeLists()

    @Query("DELETE FROM board_access")
    abstract suspend fun wipeBoardAccess()

    @Query("DELETE FROM sync_workspaces")
    abstract suspend fun wipeWorkspaces()

    @Query("DELETE FROM sync_queue")
    abstract suspend fun wipeQueue()

    @Query("DELETE FROM sync_baseline")
    abstract suspend fun wipeBaseline()

    @Query("DELETE FROM sync_meta")
    abstract suspend fun wipeMeta()

    open suspend fun clearPending(entity: String, id: String, version: Long): Int = when (entity) {
        SyncEntity.LIST.wireName -> clearPendingList(id, version)
        SyncEntity.TASK.wireName -> clearPendingTask(id, version)
        SyncEntity.SUBTASK.wireName -> clearPendingSubtask(id, version)
        SyncEntity.BOARD.wireName -> clearPendingBoard(id, version)
        SyncEntity.COLUMN.wireName -> clearPendingColumn(id, version)
        SyncEntity.MY_DAY.wireName -> clearPendingMyDay(id, version)
        SyncEntity.REMINDER.wireName -> clearPendingReminder(id, version)
        else -> 0
    }

    @Transaction
    open suspend fun commitApplied(entity: String, id: String, serverUpdatedAt: Long, sentVersion: Long, mutationId: String) {
        upsertBaseline(SyncBaselineEntity(entity, id, serverUpdatedAt))
        clearPending(entity, id, sentVersion)
        removeQueue(mutationId)
    }

    // Sin sentVersion: la fila adoptada o descartada sustituye a la local aunque haya cambiado.
    @Transaction
    open suspend fun commitList(list: TaskListEntity, baseline: Long?, mutationId: String?) {
        upsertList(list)
        baseline?.let { upsertBaseline(SyncBaselineEntity(SyncEntity.LIST.wireName, list.id, it)) }
        mutationId?.let { removeQueue(it) }
    }

    @Transaction
    open suspend fun commitTask(task: TaskEntity, baseline: Long?, mutationId: String?) {
        upsertTask(task)
        baseline?.let { upsertBaseline(SyncBaselineEntity(SyncEntity.TASK.wireName, task.id, it)) }
        mutationId?.let { removeQueue(it) }
    }

    @Transaction
    open suspend fun commitSubtask(subtask: SubtaskEntity, baseline: Long?, mutationId: String?) {
        upsertSubtask(subtask)
        baseline?.let { upsertBaseline(SyncBaselineEntity(SyncEntity.SUBTASK.wireName, subtask.id, it)) }
        mutationId?.let { removeQueue(it) }
    }

    @Transaction
    open suspend fun commitBoard(board: BoardEntity, baseline: Long?, mutationId: String?) {
        upsertBoard(board)
        baseline?.let { upsertBaseline(SyncBaselineEntity(SyncEntity.BOARD.wireName, board.id, it)) }
        mutationId?.let { removeQueue(it) }
    }

    @Transaction
    open suspend fun commitColumn(column: ColumnEntity, baseline: Long?, mutationId: String?) {
        upsertColumn(column)
        baseline?.let { upsertBaseline(SyncBaselineEntity(SyncEntity.COLUMN.wireName, column.id, it)) }
        mutationId?.let { removeQueue(it) }
    }

    @Transaction
    open suspend fun commitMyDay(entry: MyDayEntryEntity, baseline: Long?, mutationId: String?) {
        upsertMyDay(entry)
        baseline?.let { upsertBaseline(SyncBaselineEntity(SyncEntity.MY_DAY.wireName, entry.id, it)) }
        mutationId?.let { removeQueue(it) }
    }

    @Transaction
    open suspend fun commitReminder(reminder: ReminderEntity, baseline: Long?, mutationId: String?) {
        upsertReminder(reminder)
        baseline?.let { upsertBaseline(SyncBaselineEntity(SyncEntity.REMINDER.wireName, reminder.id, it)) }
        mutationId?.let { removeQueue(it) }
    }

    // Lectura, decisión y escritura de una página de cambios en un solo bloque: nada puede editarse entre medias.
    @Transaction
    open suspend fun inTransaction(block: suspend () -> Unit) {
        block()
    }

    @Transaction
    open suspend fun markAllUnsyncedPending() {
        markUnsyncedListsPending()
        markUnsyncedTasksPending()
        markUnsyncedSubtasksPending()
        markUnsyncedBoardsPending()
        markUnsyncedColumnsPending()
        markUnsyncedMyDayPending()
        markUnsyncedRemindersPending()
    }

    // Una lista local pasa a ser la del servidor: sus tareas la siguen y la fila local desaparece sin enviar un borrado.
    @Transaction
    open suspend fun adoptList(localId: String, server: TaskListEntity, serverUpdatedAt: Long, mutationId: String?) {
        if (localId != server.id) {
            moveTasksToList(localId, server.id)
            purgeList(localId)
            removeBaseline(SyncEntity.LIST.wireName, localId)
        }
        upsertList(server)
        upsertBaseline(SyncBaselineEntity(SyncEntity.LIST.wireName, server.id, serverUpdatedAt))
        mutationId?.let { removeQueue(it) }
    }

    // Si el id nuevo ya existe (otro dispositivo o una pasada anterior), las tareas se funden en esa fila.
    @Transaction
    open suspend fun rekeyList(oldId: String, newId: String) {
        if (oldId == newId || findList(oldId) == null) return
        repointTasks(oldId, newId)
        if (findList(newId) == null) {
            renameList(oldId, newId)
            if (queueEntry(SyncEntity.LIST.wireName, newId) == null) renameListInQueue(oldId, newId)
            if (baseline(SyncEntity.LIST.wireName, newId) == null) renameListInBaseline(oldId, newId)
        } else {
            purgeList(oldId)
        }
        queueEntry(SyncEntity.LIST.wireName, oldId)?.let { removeQueue(it.mutationId) }
        removeBaseline(SyncEntity.LIST.wireName, oldId)
    }

    // Cambio de cuenta confirmado: ningún dato de la cuenta anterior sobrevive. Resiembra «Tareas» con el id fijo.
    @Transaction
    open suspend fun wipe(seed: TaskListEntity) {
        wipeSubtasks()
        wipeReminders()
        wipeMyDay()
        wipeTasks()
        wipeColumns()
        wipeBoards()
        wipeBoardAccess()
        wipeWorkspaces()
        wipeLists()
        wipeNotifications()
        wipeQueue()
        wipeBaseline()
        wipeMeta()
        upsertList(seed)
    }

    // Retirada de un tablero que el usuario ya no ve. Cada consulta cuelga del tablero por subconsultas (columnas, tareas,
    // subtareas) para no depender del tamaño del tablero ni del límite de variables de SQLite; el orden va de las hojas
    // a la raíz porque las subconsultas leen filas que aún deben existir.
    @Query(
        "DELETE FROM sync_queue WHERE " +
            "(entity = 'subtask' AND entityId IN (SELECT s.id FROM subtasks s WHERE s.taskId IN " +
            "(SELECT t.id FROM tasks t WHERE t.columnId IN (SELECT c.id FROM columns c WHERE c.boardId = :boardId)))) OR " +
            "(entity = 'task' AND entityId IN (SELECT t.id FROM tasks t WHERE t.columnId IN " +
            "(SELECT c.id FROM columns c WHERE c.boardId = :boardId))) OR " +
            "(entity = 'myDay' AND entityId IN (SELECT t.id FROM tasks t WHERE t.columnId IN " +
            "(SELECT c.id FROM columns c WHERE c.boardId = :boardId))) OR " +
            "(entity = 'reminder' AND entityId IN (SELECT r.id FROM reminders r WHERE r.taskId IN " +
            "(SELECT t.id FROM tasks t WHERE t.columnId IN (SELECT c.id FROM columns c WHERE c.boardId = :boardId)))) OR " +
            "(entity = 'column' AND entityId IN (SELECT c.id FROM columns c WHERE c.boardId = :boardId)) OR " +
            "(entity = 'board' AND entityId = :boardId)",
    )
    abstract suspend fun purgeQueueOfBoard(boardId: String)

    @Query(
        "DELETE FROM sync_baseline WHERE " +
            "(entity = 'subtask' AND entityId IN (SELECT s.id FROM subtasks s WHERE s.taskId IN " +
            "(SELECT t.id FROM tasks t WHERE t.columnId IN (SELECT c.id FROM columns c WHERE c.boardId = :boardId)))) OR " +
            "(entity = 'task' AND entityId IN (SELECT t.id FROM tasks t WHERE t.columnId IN " +
            "(SELECT c.id FROM columns c WHERE c.boardId = :boardId))) OR " +
            "(entity = 'myDay' AND entityId IN (SELECT t.id FROM tasks t WHERE t.columnId IN " +
            "(SELECT c.id FROM columns c WHERE c.boardId = :boardId))) OR " +
            "(entity = 'reminder' AND entityId IN (SELECT r.id FROM reminders r WHERE r.taskId IN " +
            "(SELECT t.id FROM tasks t WHERE t.columnId IN (SELECT c.id FROM columns c WHERE c.boardId = :boardId)))) OR " +
            "(entity = 'column' AND entityId IN (SELECT c.id FROM columns c WHERE c.boardId = :boardId)) OR " +
            "(entity = 'board' AND entityId = :boardId)",
    )
    abstract suspend fun purgeBaselineOfBoard(boardId: String)

    @Query(
        "DELETE FROM subtasks WHERE taskId IN (SELECT t.id FROM tasks t WHERE t.columnId IN " +
            "(SELECT c.id FROM columns c WHERE c.boardId = :boardId))",
    )
    abstract suspend fun purgeSubtasksOfBoard(boardId: String)

    @Query(
        "DELETE FROM my_day_tasks WHERE id IN (SELECT t.id FROM tasks t WHERE t.columnId IN " +
            "(SELECT c.id FROM columns c WHERE c.boardId = :boardId))",
    )
    abstract suspend fun purgeMyDayOfBoard(boardId: String)

    @Query(
        "DELETE FROM reminders WHERE taskId IN (SELECT t.id FROM tasks t WHERE t.columnId IN " +
            "(SELECT c.id FROM columns c WHERE c.boardId = :boardId))",
    )
    abstract suspend fun purgeRemindersOfBoard(boardId: String)

    @Query("DELETE FROM tasks WHERE columnId IN (SELECT c.id FROM columns c WHERE c.boardId = :boardId)")
    abstract suspend fun purgeTasksOfBoard(boardId: String)

    @Query("DELETE FROM columns WHERE boardId = :boardId")
    abstract suspend fun purgeColumnsOfBoard(boardId: String)

    @Query("DELETE FROM boards WHERE id = :boardId")
    abstract suspend fun purgeBoardRow(boardId: String)

    // Devuelve si el tablero existía: solo entonces hay algo que avisar al usuario.
    @Transaction
    open suspend fun purgeBoard(boardId: String): Boolean {
        val existed = findBoard(boardId) != null
        purgeQueueOfBoard(boardId)
        purgeBaselineOfBoard(boardId)
        purgeRemindersOfBoard(boardId)
        purgeMyDayOfBoard(boardId)
        purgeSubtasksOfBoard(boardId)
        purgeTasksOfBoard(boardId)
        purgeColumnsOfBoard(boardId)
        purgeBoardRow(boardId)
        removeAccess(boardId)
        return existed
    }
}
