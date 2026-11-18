package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.ColumnCountRow
import com.simposio.taskeer.data.local.ListCountRow
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.testing.FakeTaskListDao
import com.simposio.taskeer.testing.FakeTaskStore
import com.simposio.taskeer.testing.listEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-11-17T10:00:00Z")
    private val store = FakeTaskStore()
    private val listDao = FakeTaskListDao(store)
    private val repository = TaskListRepositoryImpl(listDao, CountingTaskDao(store), now = { now })

    private fun task(
        id: String,
        listId: String?,
        position: Int = 0,
        completedAt: Long? = null,
        deletedAt: Long? = null,
        columnId: String? = null,
    ) = TaskEntity(
        id = id,
        columnId = columnId,
        listId = listId,
        title = "Tarea $id",
        description = null,
        priority = "MEDIUM",
        dueAt = null,
        assigneeId = null,
        position = position,
        createdAt = 0L,
        completedAt = completedAt,
        updatedAt = 0L,
        pending = false,
        deletedAt = deletedAt,
    )

    private fun failureOf(result: DomainResult<*>): DomainError = (result as DomainResult.Failure).error

    @Test
    fun observeLists_ordenaPorPosicionCreatedAtId() = runTest {
        listDao.put(listEntity("viajes", "Viajes", position = 2))
        listDao.put(listEntity("compras", "Compras", position = 1))
        listDao.put(listEntity(SEEDED_TASK_LIST_ID, "Tareas", position = 0))
        listDao.put(listEntity("a-compras", "Otra", position = 1, createdAt = 5L))

        val names = repository.observeLists().first().map { it.list.name }

        assertEquals(listOf("Tareas", "Compras", "Otra", "Viajes"), names)
    }

    @Test
    fun observeLists_cuentaSoloPendientesNoBorradas() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        store.put(task("a", "compras"))
        store.put(task("b", "compras"))
        store.put(task("hecha", "compras", completedAt = 1L))
        store.put(task("borrada", "compras", deletedAt = 1L))
        store.put(task("de-tareas", SEEDED_TASK_LIST_ID))
        store.put(task("de-tablero", null, columnId = "columna-1"))

        val counts = repository.observeLists().first().associate { it.list.id to it.activeCount }

        assertEquals(2, counts.getValue("compras"))
        assertEquals(1, counts.getValue(SEEDED_TASK_LIST_ID))
    }

    @Test
    fun observeLists_listaVacia_cuentaCero() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))

        val summary = repository.observeLists().first().first { it.list.id == "compras" }

        assertEquals(0, summary.activeCount)
    }

    @Test
    fun observeLists_omiteLasBorradas() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1, deletedAt = 5L))

        assertEquals(listOf(SEEDED_TASK_LIST_ID), repository.observeLists().first().map { it.list.id })
    }

    @Test
    fun createList_valida_escribeConMetadatos() = runTest {
        val created = (repository.createList("  Compras ") as DomainResult.Success).value

        val row = listDao.stored.getValue(created.id)
        assertEquals("Compras", row.name)
        assertTrue(row.pending)
        assertNull(row.deletedAt)
        assertEquals(now.toEpochMilli(), row.createdAt)
        assertEquals(now.toEpochMilli(), row.updatedAt)
        assertEquals(36, created.id.length)
        assertEquals(1, listDao.writeCalls)
        assertEquals(created, repository.observeLists().first().map { it.list }.first { it.id == created.id })
    }

    @Test
    fun createList_asignaPosicionMaxMasUno() = runTest {
        val first = (repository.createList("Compras") as DomainResult.Success).value
        val second = (repository.createList("Viajes") as DomainResult.Success).value
        listDao.put(listEntity("borrada", "Borrada", position = 9, deletedAt = 1L))
        val third = (repository.createList("Casa") as DomainResult.Success).value

        assertEquals(listOf(1, 2, 3), listOf(first.position, second.position, third.position))
    }

    @Test
    fun createList_conHuecosDePosicion_usaMaxMasUno() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 7))
        listDao.put(listEntity("viajes", "Viajes", position = 3, deletedAt = 1L))
        listDao.put(listEntity("grande", "Grande", position = 20, deletedAt = 1L))

        val created = (repository.createList("Casa") as DomainResult.Success).value

        assertEquals(8, created.position)
    }

    @Test
    fun createList_nombreInvalido_noEscribe() = runTest {
        val error = failureOf(repository.createList("   "))

        assertTrue(error is DomainError.Validation)
        assertEquals("LIST_NAME_REQUIRED", error.code)
        assertEquals(0, listDao.writeCalls)
        assertEquals(1, listDao.stored.size)
    }

    @Test
    fun createList_nombreDuplicado_noEscribe() = runTest {
        repository.createList("Compras")
        val writesBefore = listDao.writeCalls

        val error = failureOf(repository.createList("  compras "))

        assertTrue(error is DomainError.Conflict)
        assertEquals("LIST_NAME_TAKEN", error.code)
        assertEquals(writesBefore, listDao.writeCalls)
        assertEquals(failureOf(repository.createList("tareas")).code, "LIST_NAME_TAKEN")
    }

    @Test
    fun renameList_valida_actualizaYMarcaPendiente() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1, updatedAt = 1L, pending = false))
        val later = now

        val renamed = (repository.renameList("compras", " Super ") as DomainResult.Success).value

        val row = listDao.stored.getValue("compras")
        assertEquals("Super", row.name)
        assertEquals("Super", renamed.name)
        assertEquals(later.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
    }

    @Test
    fun renameList_inexistente_devuelveNotFound() = runTest {
        listDao.put(listEntity("borrada", "Borrada", deletedAt = 1L))

        listOf("no-existe", "borrada").forEach { id ->
            val error = failureOf(repository.renameList(id, "Nuevo"))

            assertTrue(error is DomainError.NotFound)
            assertEquals("LIST_NOT_FOUND", error.code)
        }
        assertEquals(0, listDao.writeCalls)
    }

    @Test
    fun renameList_tareasSembrada_seRenombra() = runTest {
        val renamed = (repository.renameList(SEEDED_TASK_LIST_ID, "Pendientes") as DomainResult.Success).value

        assertEquals("Pendientes", renamed.name)
        assertEquals("Pendientes", listDao.stored.getValue(SEEDED_TASK_LIST_ID).name)
        assertTrue(listDao.stored.getValue(SEEDED_TASK_LIST_ID).pending)
    }

    @Test
    fun renameList_noCambiaLaPosicionNiLaDefault() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        val defaultBefore = repository.defaultListId()

        repository.renameList(SEEDED_TASK_LIST_ID, "Zeta")
        repository.renameList("compras", "Alfa")

        assertEquals(0, listDao.stored.getValue(SEEDED_TASK_LIST_ID).position)
        assertEquals(1, listDao.stored.getValue("compras").position)
        assertEquals(defaultBefore, repository.defaultListId())
    }

    @Test
    fun renameList_nombreDuplicado_noEscribe() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        listDao.put(listEntity("viajes", "Viajes", position = 2))

        val error = failureOf(repository.renameList("viajes", "COMPRAS"))

        assertEquals("LIST_NAME_TAKEN", error.code)
        assertEquals("Viajes", listDao.stored.getValue("viajes").name)
        assertEquals(0, listDao.writeCalls)
        assertTrue(repository.renameList("viajes", "Viajes") is DomainResult.Success)
    }

    @Test
    fun deleteList_marcaDeletedAtYPending() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1, updatedAt = 1L))

        assertTrue(repository.deleteList("compras") is DomainResult.Success)

        val row = listDao.stored.getValue("compras")
        assertEquals(now.toEpochMilli(), row.deletedAt)
        assertEquals(now.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
    }

    @Test
    fun deleteList_muevenSusTareasALaDefaultResultante() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        store.put(task("a", "compras", position = 0))
        store.put(task("b", "compras", position = 1))
        store.put(task("otra", "viajes", position = 0))

        repository.deleteList("compras")

        assertEquals(SEEDED_TASK_LIST_ID, store.rows.value.getValue("a").listId)
        assertEquals(SEEDED_TASK_LIST_ID, store.rows.value.getValue("b").listId)
        assertEquals("viajes", store.rows.value.getValue("otra").listId)
        assertTrue(store.rows.value.getValue("a").pending)
        assertEquals(now.toEpochMilli(), store.rows.value.getValue("a").updatedAt)
    }

    @Test
    fun deleteList_incluyeLasCompletadas() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        store.put(task("hecha", "compras", completedAt = 7L))

        repository.deleteList("compras")

        val moved = store.rows.value.getValue("hecha")
        assertEquals(SEEDED_TASK_LIST_ID, moved.listId)
        assertEquals(7L, moved.completedAt)
    }

    @Test
    fun deleteList_posicionaLasMovidasAlFinalDeLaDestino() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        store.put(task("t0", SEEDED_TASK_LIST_ID, position = 0))
        store.put(task("t1", SEEDED_TASK_LIST_ID, position = 1))
        store.put(task("b", "compras", position = 5))
        store.put(task("a", "compras", position = 2))

        repository.deleteList("compras")

        assertEquals(2, store.rows.value.getValue("a").position)
        assertEquals(3, store.rows.value.getValue("b").position)
        assertEquals(0, store.rows.value.getValue("t0").position)
    }

    @Test
    fun deleteList_noTocaLasTareasBorradas() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        val tombstone = task("borrada", "compras", deletedAt = 3L)
        store.put(tombstone)
        store.put(task("viva", "compras"))

        repository.deleteList("compras")

        assertEquals(tombstone, store.rows.value.getValue("borrada"))
    }

    @Test
    fun deleteList_conTareas_escribeEnUnaSolaLlamadaTransaccional() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        store.put(task("a", "compras"))

        repository.deleteList("compras")

        assertEquals(1, listDao.transactionalCalls)
        assertEquals(0, listDao.writeCalls)
    }

    @Test
    fun deleteList_inexistente_devuelveNotFound() = runTest {
        listDao.put(listEntity("borrada", "Borrada", deletedAt = 1L))

        listOf("no-existe", "borrada").forEach { id ->
            val error = failureOf(repository.deleteList(id))

            assertTrue(error is DomainError.NotFound)
            assertEquals("LIST_NOT_FOUND", error.code)
        }
        assertEquals(0, listDao.transactionalCalls)
    }

    @Test
    fun deleteList_borrarTareasConOtrasListas_pasanALaOtra() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        store.put(task("a", SEEDED_TASK_LIST_ID))

        val result = repository.deleteList(SEEDED_TASK_LIST_ID)

        assertTrue(result is DomainResult.Success)
        assertEquals(now.toEpochMilli(), listDao.stored.getValue(SEEDED_TASK_LIST_ID).deletedAt)
        assertEquals("compras", store.rows.value.getValue("a").listId)
    }

    @Test
    fun deleteList_borrandoLaDefault_laResultanteEsLaSiguientePorPosition() = runTest {
        listDao.put(listEntity("viajes", "Viajes", position = 5))
        listDao.put(listEntity("compras", "Compras", position = 2))
        store.put(task("a", SEEDED_TASK_LIST_ID))

        repository.deleteList(SEEDED_TASK_LIST_ID)

        assertEquals("compras", repository.defaultListId())
        assertEquals("compras", store.rows.value.getValue("a").listId)
    }

    @Test
    fun deleteList_nuncaMueveALaQueSeBorra() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        store.put(task("a", "compras"))
        store.put(task("b", SEEDED_TASK_LIST_ID))

        repository.deleteList("compras")

        assertEquals(SEEDED_TASK_LIST_ID, store.rows.value.getValue("a").listId)
        assertTrue(store.rows.value.values.none { it.listId == "compras" })
    }

    @Test
    fun borrarUltimaLista_devuelveLastProtectedSinEscribir() = runTest {
        store.put(task("a", SEEDED_TASK_LIST_ID))

        val error = failureOf(repository.deleteList(SEEDED_TASK_LIST_ID))

        assertTrue(error is DomainError.Conflict)
        assertEquals("LIST_LAST_PROTECTED", error.code)
        assertNull(listDao.stored.getValue(SEEDED_TASK_LIST_ID).deletedAt)
        assertEquals(0, listDao.transactionalCalls)
        assertEquals(0, listDao.writeCalls)
        assertEquals(SEEDED_TASK_LIST_ID, store.rows.value.getValue("a").listId)
    }

    @Test
    fun defaultListId_esLaDeMenorPosition() = runTest {
        assertEquals(SEEDED_TASK_LIST_ID, repository.defaultListId())
        listDao.put(listEntity("antes", "Antes", position = -1))
        listDao.put(listEntity("borrada", "Borrada", position = -5, deletedAt = 1L))

        assertEquals("antes", repository.defaultListId())
    }

    @Test
    fun observeDefaultList_cambiaAlBorrarLaDefault() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))
        assertEquals(SEEDED_TASK_LIST_ID, repository.observeDefaultList().first().id)

        repository.deleteList(SEEDED_TASK_LIST_ID)

        assertEquals("compras", repository.observeDefaultList().first().id)
    }

    @Test
    fun listaPorDefecto_trasRenombrarTareas_sigueSiendoTareas() = runTest {
        listDao.put(listEntity("compras", "Compras", position = 1))

        repository.renameList(SEEDED_TASK_LIST_ID, "Hoy")

        val default = repository.observeDefaultList().first()
        assertEquals(SEEDED_TASK_LIST_ID, default.id)
        assertEquals("Hoy", default.name)
    }

    // Solo el conteo por lista lo usa el repositorio de listas; el resto de la API del DAO no se toca.
    private class CountingTaskDao(private val store: FakeTaskStore) : TaskDao() {
        override suspend fun findActiveByList(listId: String): List<TaskEntity> = error("no se usa")

        override suspend fun restoreSubtasksOfTask(taskId: String, deletedAt: Long, now: Long) = error("no se usa")

        override suspend fun restoreRemindersOfTask(taskId: String, deletedAt: Long, now: Long): Int = error("no se usa")
        override fun observeActiveCountsByList(): Flow<List<ListCountRow>> = store.rows.map { rows ->
            rows.values
                .filter { it.deletedAt == null && it.completedAt == null && it.listId != null }
                .groupBy { requireNotNull(it.listId) }
                .map { (listId, tasks) -> ListCountRow(listId, tasks.size) }
        }

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = unused()

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = unused()

        override fun observeByList(listId: String): Flow<List<TaskEntity>> = unused()

        override suspend fun countInList(listId: String): Int = unused()

        override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = unused()

        override suspend fun findById(id: String): TaskEntity? = unused()

        override suspend fun countByColumn(columnId: String): Int = unused()

        override suspend fun upsert(task: TaskEntity) = unused()

        override suspend fun upsertAll(tasks: List<TaskEntity>) = unused()

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = unused()

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = unused()

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = unused()

        override fun observeCompletedCount(): Flow<Int> = unused()

        override suspend fun findCompleted(): List<TaskEntity> = unused()

        override suspend fun countActive(): Int = unused()

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = unused()

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = unused()

        override suspend fun insertReminder(reminder: ReminderEntity) = unused()

        override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) = unused()

        private fun unused(): Nothing = error("este DAO no se usa en el test")
    }
}
