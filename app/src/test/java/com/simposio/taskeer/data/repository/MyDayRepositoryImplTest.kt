package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnCountRow
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.MyDayDao
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.MyDayRow
import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.TaskDraft
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyDayRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")
    private val later: Instant = now.plusSeconds(60)

    @Test
    fun setMarked_true_creaFilaActivaPendienteConAddedAtAhora() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")

        val result = fixture.repository.setMarked("t1", true)

        assertEquals(DomainResult.Success(Unit), result)
        assertEquals(
            MyDayEntryEntity("t1", now.toEpochMilli(), now.toEpochMilli(), pending = true, deletedAt = null),
            fixture.dao.entries.getValue("t1"),
        )
    }

    @Test
    fun setMarked_true_dosVeces_noDuplicaNiCambiaAddedAt() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.clock = later

        val result = fixture.repository.setMarked("t1", true)

        assertEquals(DomainResult.Success(Unit), result)
        assertEquals(1, fixture.dao.entries.size)
        assertEquals(now.toEpochMilli(), fixture.dao.entries.getValue("t1").addedAt)
        assertEquals(1, fixture.dao.writeCalls)
    }

    @Test
    fun setMarked_false_haceSoftDeleteYMarcaPendiente() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.clock = later

        val result = fixture.repository.setMarked("t1", false)

        assertEquals(DomainResult.Success(Unit), result)
        val entry = fixture.dao.entries.getValue("t1")
        assertEquals(later.toEpochMilli(), entry.deletedAt)
        assertEquals(later.toEpochMilli(), entry.updatedAt)
        assertTrue(entry.pending)
    }

    @Test
    fun setMarked_false_sobreTareaNoMarcada_esExitoSinEscribir() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")

        val result = fixture.repository.setMarked("t1", false)

        assertEquals(DomainResult.Success(Unit), result)
        assertEquals(0, fixture.dao.writeCalls)
    }

    @Test
    fun setMarked_false_dosVeces_noReescribeLaFilaBorrada() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.repository.setMarked("t1", false)
        val writes = fixture.dao.writeCalls

        fixture.repository.setMarked("t1", false)

        assertEquals(writes, fixture.dao.writeCalls)
    }

    @Test
    fun setMarked_remarcarUnaDesmarcada_reactivaLaFilaConAddedAtNuevo() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.repository.setMarked("t1", false)
        fixture.clock = later

        fixture.repository.setMarked("t1", true)

        assertEquals(1, fixture.dao.entries.size)
        val entry = fixture.dao.entries.getValue("t1")
        assertNull(entry.deletedAt)
        assertEquals(later.toEpochMilli(), entry.addedAt)
    }

    @Test
    fun setMarked_tareaInexistenteOBorrada_devuelveTaskNotFoundSinEscribir() = runTest {
        val fixture = fixture()
        fixture.addTask("borrada", deletedAt = now.toEpochMilli())

        listOf("no-existe", "borrada").forEach { id ->
            val error = (fixture.repository.setMarked(id, true) as DomainResult.Failure).error
            assertTrue(error is DomainError.NotFound)
            assertEquals("TASK_NOT_FOUND", error.code)
        }
        assertEquals(0, fixture.dao.writeCalls)
    }

    @Test
    fun observeMarkedTaskIds_emiteAlMarcarYAlDesmarcar() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.addTask("t2")
        assertEquals(emptySet<String>(), fixture.repository.observeMarkedTaskIds().first())

        fixture.repository.setMarked("t1", true)
        fixture.repository.setMarked("t2", true)
        assertEquals(setOf("t1", "t2"), fixture.repository.observeMarkedTaskIds().first())

        fixture.repository.setMarked("t1", false)
        assertEquals(setOf("t2"), fixture.repository.observeMarkedTaskIds().first())
    }

    @Test
    fun pinned_marcadaEnColumnaNormal_apareceConTableroYAddedAt() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)

        val item = fixture.repository.observeMyDay().first().pinned.single()

        assertEquals("t1", item.task.id)
        assertEquals("b1", item.boardId)
        assertEquals("Tablero b1", item.boardName)
        assertEquals(now, item.addedAt)
    }

    @Test
    fun pinned_marcadaEnColumnaCompletada_noApareceYConservaLaMarca() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", columnId = "done")
        fixture.repository.setMarked("t1", true)

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
        assertEquals(setOf("t1"), fixture.repository.observeMarkedTaskIds().first())
    }

    @Test
    fun pinned_tareaColumnaOTableroBorrados_noAparece() = runTest {
        val fixture = fixture()
        fixture.addTask("tarea-borrada")
        fixture.addTask("columna-borrada", columnId = "c-borrada")
        fixture.addTask("tablero-borrado", columnId = "c-tablero-borrado")
        listOf("tarea-borrada", "columna-borrada", "tablero-borrado").forEach {
            fixture.repository.setMarked(it, true)
        }

        fixture.dao.tasks.value = fixture.dao.tasks.value +
            ("tarea-borrada" to fixture.dao.tasks.value.getValue("tarea-borrada").copy(deletedAt = 1L))

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
    }

    @Test
    fun pinned_desmarcada_noAparece() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.repository.setMarked("t1", false)

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
    }

    @Test
    fun pinned_ordenaPorAddedAtDescendenteConDesempatePorId() = runTest {
        val fixture = fixture()
        listOf("a", "b", "c", "d").forEach { fixture.addTask(it) }
        fixture.repository.setMarked("a", true)
        fixture.clock = later
        fixture.repository.setMarked("c", true)
        fixture.repository.setMarked("b", true)
        fixture.clock = later.plusSeconds(60)
        fixture.repository.setMarked("d", true)

        val ids = fixture.repository.observeMyDay().first().pinned.map { it.task.id }

        assertEquals(listOf("d", "b", "c", "a"), ids)
    }

    @Test
    fun assigned_soloAsignadasAlUsuarioLocalConFecha() = runTest {
        val fixture = fixture()
        val due = now.plusSeconds(3600).toEpochMilli()
        fixture.addTask("sin-asignado", dueAt = due)
        fixture.addTask("otro", dueAt = due, assigneeId = "otro-usuario")
        fixture.addTask("sin-fecha", assigneeId = LocalUser.ID)
        fixture.addTask("ok", dueAt = due, assigneeId = LocalUser.ID)

        val ids = fixture.repository.observeMyDay().first().assigned.map { it.task.id }

        assertEquals(listOf("ok"), ids)
    }

    @Test
    fun assigned_incluyeVencidasYExcluyeColumnaCompletada() = runTest {
        val fixture = fixture()
        fixture.addTask("vencida", dueAt = now.minusSeconds(60).toEpochMilli(), assigneeId = LocalUser.ID)
        fixture.addTask(
            "hecha",
            columnId = "done",
            dueAt = now.minusSeconds(60).toEpochMilli(),
            assigneeId = LocalUser.ID,
        )

        val items = fixture.repository.observeMyDay().first().assigned

        assertEquals(listOf("vencida"), items.map { it.task.id })
        assertNull(items.single().addedAt)
    }

    @Test
    fun assigned_ordenaPorDueAtAscendenteConDesempatePorId() = runTest {
        val fixture = fixture()
        val base = now.toEpochMilli()
        fixture.addTask("b", dueAt = base + 2000, assigneeId = LocalUser.ID)
        fixture.addTask("z", dueAt = base + 1000, assigneeId = LocalUser.ID)
        fixture.addTask("a", dueAt = base + 2000, assigneeId = LocalUser.ID)

        val ids = fixture.repository.observeMyDay().first().assigned.map { it.task.id }

        assertEquals(listOf("z", "a", "b"), ids)
    }

    @Test
    fun tareaMarcadaYAsignada_apareceEnAmbosBloques() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", dueAt = now.toEpochMilli() + 1000, assigneeId = LocalUser.ID)
        fixture.repository.setMarked("t1", true)

        val myDay = fixture.repository.observeMyDay().first()

        assertEquals(listOf("t1"), myDay.pinned.map { it.task.id })
        assertEquals(listOf("t1"), myDay.assigned.map { it.task.id })
    }

    @Test
    fun moverAHechoYVolver_saleDeMyDaySinBorrarLaEntradaYReaparecConSuAddedAt() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", dueAt = now.toEpochMilli() + 1000, assigneeId = LocalUser.ID)
        fixture.repository.setMarked("t1", true)

        fixture.moveTask("t1", "done")
        val inDone = fixture.repository.observeMyDay().first()
        assertTrue(inDone.pinned.isEmpty())
        assertTrue(inDone.assigned.isEmpty())
        assertNull(fixture.dao.entries.getValue("t1").deletedAt)

        fixture.moveTask("t1", "c1")
        val back = fixture.repository.observeMyDay().first()
        assertEquals(now, back.pinned.single().addedAt)
    }

    @Test
    fun overdueCount_esExactoSinTopeParaCeroCincoSeisYDoscientosCincuenta() = runTest {
        listOf(0, 5, 6, 250).forEach { total ->
            val fixture = fixture()
            repeat(total) { fixture.addTask("v$it", dueAt = now.toEpochMilli() - 1, assigneeId = LocalUser.ID) }

            assertEquals(total, fixture.repository.observeOverdueCount().first())
        }
    }

    @Test
    fun overdueCount_dueAtIgualAAhora_noCuenta() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", dueAt = now.toEpochMilli(), assigneeId = LocalUser.ID)

        assertEquals(0, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun overdueCount_noCuentaSinAsignadoSinFechaFuturasOtroAsignadoCompletadasNiBorradas() = runTest {
        val fixture = fixture()
        val past = now.toEpochMilli() - 1000
        fixture.addTask("sin-asignado", dueAt = past)
        fixture.addTask("sin-fecha", assigneeId = LocalUser.ID)
        fixture.addTask("futura", dueAt = now.toEpochMilli() + 1000, assigneeId = LocalUser.ID)
        fixture.addTask("otro", dueAt = past, assigneeId = "otro-usuario")
        fixture.addTask("hecha", columnId = "done", dueAt = past, assigneeId = LocalUser.ID)
        fixture.addTask("borrada", dueAt = past, assigneeId = LocalUser.ID, deletedAt = 1L)
        fixture.addTask("cuenta", dueAt = past, assigneeId = LocalUser.ID)

        assertEquals(1, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun overdueCount_seReemiteAlCambiarLasTareas() = runTest {
        val fixture = fixture()
        val seen = mutableListOf<Int>()
        val count = fixture.repository.observeOverdueCount()
        seen += count.first()

        fixture.addTask("v1", dueAt = now.toEpochMilli() - 1, assigneeId = LocalUser.ID)
        seen += count.first()

        assertEquals(listOf(0, 1), seen)
    }

    @Test
    fun overdueCount_usaAhoraFrescoEnCadaEmision() = runTest {
        val fixture = fixture()
        val seen = mutableListOf<Int>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.repository.observeOverdueCount().toList(seen)
        }
        fixture.addTask("t1", dueAt = now.toEpochMilli() + 1000, assigneeId = LocalUser.ID)

        fixture.clock = now.plusSeconds(60)
        fixture.addTask("otra", dueAt = now.toEpochMilli() + 5_000_000, assigneeId = LocalUser.ID)
        collector.cancel()

        assertEquals(listOf(0, 1), seen.distinct())
    }

    @Test
    fun tareaCreadaSinAsignado_aparecenEnAsignadasYVencidas() = runTest {
        val fixture = fixture()
        val columns = object : ColumnDao() {
        override fun observeAllActive(): Flow<List<ColumnEntity>> = emptyFlow()

            override fun observeByBoard(boardId: String): Flow<List<ColumnEntity>> = error("no se usa")
            override suspend fun findActiveByBoard(boardId: String): List<ColumnEntity> = error("no se usa")
            override suspend fun findById(id: String): ColumnEntity? = ColumnEntity(
                id = id, boardId = "b1", name = "Por hacer", position = 0, wipLimit = null,
                isDoneColumn = false, createdAt = 0L, updatedAt = 0L, pending = false, deletedAt = null,
            )
            override suspend fun upsert(column: ColumnEntity) = Unit
            override suspend fun upsertAll(columns: List<ColumnEntity>) = Unit
            override suspend fun insertBoard(board: BoardEntity) = Unit
        }
        val taskRepository = TaskRepositoryImpl(WritingTaskDao(fixture.dao), columns, now = { now })

        taskRepository.createTask("c1", TaskDraft(title = "Vencida", dueAt = now.minusSeconds(60)))

        assertEquals(1, fixture.repository.observeMyDay().first().assigned.size)
        assertEquals(1, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun observeMyDay_incluyeTareaDeBandejaMarcadaSinTablero() = runTest {
        val fixture = fixture()
        fixture.addTask("pan", columnId = null)
        fixture.repository.setMarked("pan", true)

        val pinned = fixture.repository.observeMyDay().first().pinned.single()

        assertEquals("pan", pinned.task.id)
        assertNull(pinned.boardName)
    }

    @Test
    fun observeMyDay_incluyeTareaDeBandejaAsignadaConFechaYLaCuentaComoVencida() = runTest {
        val fixture = fixture()
        fixture.addTask("pan", columnId = null, dueAt = now.minusSeconds(60).toEpochMilli(), assigneeId = LocalUser.ID)

        assertEquals(listOf("pan"), fixture.repository.observeMyDay().first().assigned.map { it.task.id })
        assertEquals(1, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun observeMyDay_conColumnaInexistente_sigueExcluida() = runTest {
        val fixture = fixture()
        fixture.addTask("huerfana", columnId = "c-x")
        fixture.repository.setMarked("huerfana", true)

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
    }

    private fun fixture(): Fixture = Fixture(now)

    private inner class Fixture(var clock: Instant) {
        val dao = FakeMyDayDao()
        val repository = MyDayRepositoryImpl(dao, FakeTaskDao(dao), clock = { clock })

        fun addTask(
            id: String,
            columnId: String? = "c1",
            dueAt: Long? = null,
            assigneeId: String? = null,
            deletedAt: Long? = null,
        ) {
            dao.tasks.value = dao.tasks.value + (
                id to TaskEntity(
                    id = id,
                    columnId = columnId,
                    title = "Tarea $id",
                    description = null,
                    priority = "MEDIUM",
                    dueAt = dueAt,
                    assigneeId = assigneeId,
                    position = 0,
                    createdAt = 0L,
                    updatedAt = 0L,
                    pending = false,
                    deletedAt = deletedAt,
                )
                )
        }

        fun moveTask(id: String, columnId: String) {
            dao.tasks.value = dao.tasks.value + (id to dao.tasks.value.getValue(id).copy(columnId = columnId))
        }
    }

    private data class ColumnInfo(val boardId: String, val isDone: Boolean, val deleted: Boolean = false)

    private data class BoardInfo(val name: String, val deleted: Boolean = false)

    private class FakeMyDayDao : MyDayDao() {
        val tasks = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())
        private val entriesState = MutableStateFlow<Map<String, MyDayEntryEntity>>(emptyMap())
        private val columns = mapOf(
            "c1" to ColumnInfo("b1", isDone = false),
            "done" to ColumnInfo("b1", isDone = true),
            "c-borrada" to ColumnInfo("b1", isDone = false, deleted = true),
            "c-tablero-borrado" to ColumnInfo("b-borrado", isDone = false),
        )
        private val boards = mapOf("b1" to BoardInfo("Tablero b1"), "b-borrado" to BoardInfo("Borrado", deleted = true))

        var writeCalls: Int = 0
            private set

        val entries: Map<String, MyDayEntryEntity> get() = entriesState.value

        override fun observeMarkedIds(): Flow<List<String>> =
            entriesState.map { all -> all.values.filter { it.deletedAt == null }.map { it.id } }

        override suspend fun findEntry(taskId: String): MyDayEntryEntity? = entriesState.value[taskId]

        override suspend fun upsert(entry: MyDayEntryEntity) {
            writeCalls++
            entriesState.value = entriesState.value + (entry.id to entry)
        }

        override fun observePinned(): Flow<List<MyDayRow>> = combine(tasks, entriesState) { allTasks, allEntries ->
            allEntries.values
                .filter { it.deletedAt == null }
                .mapNotNull { entry -> visible(allTasks[entry.id])?.let { it to entry } }
                .sortedWith(compareBy<Pair<MyDayRow, MyDayEntryEntity>>({ -it.second.addedAt }, { it.first.task.id }))
                .map { (row, entry) -> row.copy(addedAt = entry.addedAt) }
        }

        override fun observeAssigned(assigneeId: String): Flow<List<MyDayRow>> = tasks.map { all ->
            all.values
                .filter { it.assigneeId == assigneeId && it.dueAt != null }
                .mapNotNull(::visible)
                .sortedWith(compareBy({ it.task.dueAt }, { it.task.id }))
        }

        override fun observeOverdueCount(assigneeId: String, now: Long): Flow<Int> = tasks.map { all ->
            all.values.count { it.assigneeId == assigneeId && it.dueAt != null && it.dueAt < now && visible(it) != null }
        }

        private fun visible(task: TaskEntity?): MyDayRow? {
            if (task == null || task.deletedAt != null) return null
            if (task.columnId == null) return MyDayRow(task, boardId = null, boardName = null, addedAt = null)
            val column = columns[task.columnId] ?: return null
            val board = boards[column.boardId] ?: return null
            if (column.deleted || column.isDone || board.deleted) return null
            return MyDayRow(task, column.boardId, board.name, addedAt = null)
        }
    }

    private class WritingTaskDao(private val myDayDao: FakeMyDayDao) : TaskDao() {
        override fun observeInbox(): Flow<List<TaskEntity>> = emptyFlow()

        override suspend fun countInbox(): Int = 0

        override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = emptyFlow()

        override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) = Unit

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = error("no se usa")
        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()
        override suspend fun findById(id: String): TaskEntity? = myDayDao.tasks.value[id]
        override suspend fun countByColumn(columnId: String): Int = 0
        override suspend fun upsert(task: TaskEntity) {
            myDayDao.tasks.value = myDayDao.tasks.value + (task.id to task)
        }
        override suspend fun upsertAll(tasks: List<TaskEntity>) = Unit
        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = null

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = Unit

        override suspend fun insertReminder(reminder: ReminderEntity) = Unit
    }

    private class FakeTaskDao(private val myDayDao: FakeMyDayDao) : TaskDao() {
        override fun observeInbox(): Flow<List<TaskEntity>> = emptyFlow()

        override suspend fun countInbox(): Int = 0

        override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = emptyFlow()

        override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) = Unit

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = myDayDao.tasks.map { it.values.toList() }

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()

        override suspend fun findById(id: String): TaskEntity? = myDayDao.tasks.value[id]

        override suspend fun countByColumn(columnId: String): Int = 0

        override suspend fun upsert(task: TaskEntity) = Unit

        override suspend fun upsertAll(tasks: List<TaskEntity>) = Unit

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = null

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = Unit

        override suspend fun insertReminder(reminder: ReminderEntity) = Unit
    }
}
