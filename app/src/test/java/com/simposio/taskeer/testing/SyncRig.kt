package com.simposio.taskeer.testing

import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.sync.LocalDataWiper
import com.simposio.taskeer.data.sync.PullPage
import com.simposio.taskeer.data.sync.RoomSyncLocalStore
import com.simposio.taskeer.data.sync.ServerRecord
import com.simposio.taskeer.data.sync.SyncEngine
import com.simposio.taskeer.data.sync.SyncMetaKeys
import com.simposio.taskeer.data.sync.SyncScopeHolder
import com.simposio.taskeer.data.sync.SyncSessionGate
import com.simposio.taskeer.data.sync.SyncTriggers
import com.simposio.taskeer.domain.sync.SeededListId
import com.simposio.taskeer.domain.sync.SyncEntity
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

// Motor, almacén en memoria y servidor falso ya cableados: las pruebas de traída solo describen estado y comprueban.
@OptIn(ExperimentalCoroutinesApi::class)
class SyncRig(var now: Instant = Instant.parse("2026-11-26T18:30:00Z")) {
    val dao = FakeSyncDao()
    val backend = FakeSyncBackend()
    val scheduled = mutableListOf<Duration>()
    val scopeHolder = SyncScopeHolder()
    private var ids = 0
    val store = RoomSyncLocalStore(dao, scopeHolder, newMutationId = { "m${++ids}" })

    // La cuenta ya está vinculada: la primera sincronización tiene sus propias pruebas.
    val listId: String = SeededListId.forUser(USER_A)

    fun bound() {
        dao.metas.value = dao.metas.value + (SyncMetaKeys.BOUND_USER_ID to USER_A) + (SyncMetaKeys.MARKED_SCOPE to "list,task,subtask")
        dao.put(syncList(listId, pending = false))
        dao.baseline("list", listId, 1L)
        backend.seed(SyncEntity.LIST, listId, mapOf("name" to "Tareas", "position" to 0), Instant.EPOCH)
    }

    // La cuenta vinculada con Mi día y recordatorios ya en el alcance de un servidor que los soporta (hitos H3 y H4).
    fun boundWithPersonalData() {
        bound()
        backend.supported = backend.supported + setOf(SyncEntity.MY_DAY, SyncEntity.REMINDER)
        dao.metas.value = dao.metas.value + (SyncMetaKeys.MARKED_SCOPE to "list,task,subtask,myDay,reminder")
    }

    fun TestScope.engine(
        session: FakeSession = FakeSession(),
        onRemindersChanged: suspend () -> Unit = {},
    ): SyncEngine = SyncEngine(
        store = store,
        source = DirectTransportSource(backend),
        gate = SyncSessionGate(store),
        wiper = LocalDataWiper(dao) { now },
        session = session.state,
        scopeHolder = scopeHolder,
        triggers = SyncTriggers(emptyFlow(), emptyFlow(), CoroutineScope(StandardTestDispatcher(testScheduler))),
        retry = { scheduled += it },
        clock = { now },
        onRemindersChanged = onRemindersChanged,
    )

    fun taskFields(title: String, listId: String = this.listId): Map<String, Any?> = mapOf(
        "listId" to listId, "columnId" to null, "title" to title, "description" to null, "priority" to "MEDIUM",
        "dueAt" to null, "assigneeId" to null, "position" to 0, "completedAt" to null, "repeatUnit" to null,
        "repeatInterval" to null, "repeatWeekdays" to null, "repeatAnchorDay" to null, "repeatSuccessorId" to null,
    )

    fun seedTask(id: String, title: String, updatedAt: Instant, deletedAt: Instant? = null) =
        backend.seed(SyncEntity.TASK, id, taskFields(title), updatedAt, deletedAt)

    fun taskRecord(id: String, title: String, updatedAt: Instant, deletedAt: Instant? = null) =
        ServerRecord(SyncEntity.TASK, id, updatedAt, deletedAt, taskFields(title))

    fun page(
        records: List<ServerRecord>,
        hasMore: Boolean = false,
        isSnapshot: Boolean = false,
        next: String = "i:1:0:",
        supported: Set<SyncEntity> = backend.supported,
    ) = PullPage(USER_A, "1.0", supported, now, isSnapshot, records, next, hasMore)

    // Una tarea que el servidor ya conoció: sin cambios pendientes y con versión base.
    fun syncedTask(id: String, columnId: String? = null) {
        dao.put(syncTask(id, listId = if (columnId == null) listId else null, columnId = columnId, pending = false, updatedAt = 5L))
        dao.baseline("task", id, 5L)
    }

    // Además de la copia local sincronizada, el servidor la conoce: las marcas y recordatorios exigen la tarea (DEPENDENCY_NOT_FOUND).
    fun syncedTaskOnServer(id: String) {
        syncedTask(id)
        seedTask(id, "Tarea $id", Instant.parse("2026-11-26T09:00:00Z"))
        dao.baseline("task", id, Instant.parse("2026-11-26T09:00:00Z").toEpochMilli())
        dao.put(syncTask(id, listId = listId, pending = false, updatedAt = Instant.parse("2026-11-26T09:00:00Z").toEpochMilli(), title = "Tarea $id"))
    }

    fun syncedSubtask(id: String, taskId: String) {
        dao.put(syncSubtask(id, taskId, pending = false, updatedAt = 5L))
        dao.baseline("subtask", id, 5L)
    }

    fun taskRow(id: String): TaskEntity? = dao.tasks.value[id]
}

fun at(text: String): Instant = Instant.parse(text)
