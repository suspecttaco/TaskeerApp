package com.simposio.taskeer.ui

import androidx.lifecycle.SavedStateHandle
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.column.COLUMN_NAME_MAX_LENGTH
import com.simposio.taskeer.domain.column.COLUMN_NAME_REQUIRED
import com.simposio.taskeer.domain.column.COLUMN_NAME_TOO_LONG
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.board.NewColumnUiState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-21T10:00:00Z")
    }

    private val madrid = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_ordenaColumnasYTarjetasAunqueLaFuenteLleguesDesordenada() = runTest {
        val columns = MutableStateFlow(listOf(column("c2", 1), column("c1", 0)))
        val tasks = MutableStateFlow(
            listOf(task("b", "c1", 1), task("z", "c1", 0), task("a", "c1", 0), task("x", "c2", 0)),
        )
        val viewModel = viewModel(columns, tasks)
        collect(viewModel)

        val state = viewModel.uiState.value

        assertEquals(listOf("c1", "c2"), state.columns.map { it.id })
        assertEquals(listOf("a", "z", "b"), state.columns[0].cards.map { it.id })
    }

    @Test
    fun uiState_columnaSinTareas_seMuestraVacia() = runTest {
        val viewModel = viewModel(MutableStateFlow(listOf(column("c1", 0))), MutableStateFlow(emptyList()))
        collect(viewModel)

        assertEquals(1, viewModel.uiState.value.columns.size)
        assertTrue(viewModel.uiState.value.columns.single().cards.isEmpty())
    }

    @Test
    fun uiState_reflejaCambiosDelRepositorioSinRecargar() = runTest {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0)))
        val viewModel = viewModel(MutableStateFlow(listOf(column("c1", 0))), tasks)
        collect(viewModel)

        tasks.value = tasks.value + task("b", "c1", 1)

        assertEquals(listOf("a", "b"), viewModel.uiState.value.columns.single().cards.map { it.id })
    }

    @Test
    fun tarjeta_exponePrioridadFechaEnZonaDelUsuarioYAsignado() = runTest {
        val due = Instant.parse("2026-10-22T10:30:00Z")
        val tasks = MutableStateFlow(
            listOf(task("a", "c1", 0, priority = TaskPriority.URGENT, dueAt = due, assigneeId = "otro-usuario")),
        )
        val viewModel = viewModel(MutableStateFlow(listOf(column("c1", 0))), tasks)
        collect(viewModel)

        val card = viewModel.uiState.value.columns.single().cards.single()

        assertEquals(TaskPriority.URGENT, card.priority)
        assertEquals(due, card.dueAt)
        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 30), card.dueAtLocal)
        assertEquals("otro-usuario", card.assigneeId)
        assertFalse(card.assigneeIsMe)
    }

    @Test
    fun tarjeta_sinFechaNiAsignado_losDejaNulos() = runTest {
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0))),
            MutableStateFlow(listOf(task("a", "c1", 0))),
        )
        collect(viewModel)

        val card = viewModel.uiState.value.columns.single().cards.single()

        assertNull(card.dueAtLocal)
        assertNull(card.assigneeId)
    }

    @Test
    fun tarjeta_localUser_seMarcaComoYo() = runTest {
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0))),
            MutableStateFlow(listOf(task("a", "c1", 0, assigneeId = LocalUser.ID))),
        )
        collect(viewModel)

        assertTrue(viewModel.uiState.value.columns.single().cards.single().assigneeIsMe)
    }

    @Test
    fun tarjeta_estrellaSoloSiElIdEstaEnMyDayTaskIds() = runTest {
        val myDay = MutableStateFlow(setOf("b"))
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0))),
            MutableStateFlow(listOf(task("a", "c1", 0), task("b", "c1", 1))),
            myDay = myDay,
        )
        collect(viewModel)

        assertEquals(
            listOf("a" to false, "b" to true),
            viewModel.uiState.value.columns.single().cards.map { it.id to it.inMyDay },
        )
    }

    @Test
    fun tarjeta_sinCosturaDeMyDay_ningunaTienEstrella() = runTest {
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0))),
            MutableStateFlow(listOf(task("a", "c1", 0))),
        )
        collect(viewModel)

        assertFalse(viewModel.uiState.value.columns.single().cards.single().inMyDay)
    }

    @Test
    fun moveTask_muestraLaTarjetaEnDestinoMientrasSePersisteYLlamaAlRepositorio() = runTest {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0), task("b", "c1", 1), task("x", "c2", 0)))
        val repository = FakeTaskRepository(tasks).apply { gate = CompletableDeferred() }
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0), column("c2", 1))),
            tasks,
            repository,
        )
        collect(viewModel)

        viewModel.moveTask("a", "c2", 1)

        assertEquals(listOf("b"), viewModel.uiState.value.columns[0].cards.map { it.id })
        assertEquals(listOf("x", "a"), viewModel.uiState.value.columns[1].cards.map { it.id })
        assertEquals(listOf(Triple("a", "c2", 1)), repository.moves)
    }

    @Test
    fun moveTask_enElMismoSitio_noLlamaAlRepositorio() = runTest {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0), task("b", "c1", 1)))
        val repository = FakeTaskRepository(tasks)
        val viewModel = viewModel(MutableStateFlow(listOf(column("c1", 0))), tasks, repository)
        collect(viewModel)

        viewModel.moveTask("b", "c1", 1)

        assertTrue(repository.moves.isEmpty())
    }

    @Test
    fun moveTask_conExito_conservaElOrdenPersistido() = runTest {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0), task("b", "c1", 1)))
        val repository = FakeTaskRepository(tasks)
        val viewModel = viewModel(MutableStateFlow(listOf(column("c1", 0))), tasks, repository)
        collect(viewModel)

        viewModel.moveTask("b", "c1", 0)

        assertEquals(listOf("b", "a"), viewModel.uiState.value.columns.single().cards.map { it.id })
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun moveTask_conFallo_revierteAlOrdenPersistidoYExponeElError() = runTest {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0), task("b", "c1", 1), task("x", "c2", 0)))
        val error = DomainError.NotFound("COLUMN_NOT_FOUND", "La columna no existe")
        val repository = FakeTaskRepository(tasks).apply { failure = error }
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0), column("c2", 1))),
            tasks,
            repository,
        )
        collect(viewModel)

        viewModel.moveTask("a", "c2", 0)

        assertEquals(listOf("a", "b"), viewModel.uiState.value.columns[0].cards.map { it.id })
        assertEquals(listOf("x"), viewModel.uiState.value.columns[1].cards.map { it.id })
        assertEquals(error, viewModel.uiState.value.error)
    }

    @Test
    fun moveTask_correctoTrasUnFallo_limpiaElError() = runTest {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0), task("b", "c1", 1)))
        val repository = FakeTaskRepository(tasks).apply {
            failure = DomainError.Validation("TASK_INDEX_OUT_OF_BOUNDS", "fuera de rango")
        }
        val viewModel = viewModel(MutableStateFlow(listOf(column("c1", 0))), tasks, repository)
        collect(viewModel)
        viewModel.moveTask("a", "c1", 1)

        repository.failure = null
        viewModel.moveTask("a", "c1", 1)

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun saveNewColumn_success_closesDialogAndAddsColumnAtEnd() = runTest {
        val columns = MutableStateFlow(listOf(column("c1", 0)))
        val repository = FakeColumnRepository(columns, columns)
        val viewModel = viewModel(columns, MutableStateFlow(emptyList()), columnRepository = repository)
        collect(viewModel)

        viewModel.openNewColumn()
        viewModel.onNewColumnNameChange("Revisión")
        viewModel.saveNewColumn()

        assertEquals(listOf("tablero-1" to "Revisión"), repository.created)
        assertEquals(NewColumnUiState(), viewModel.uiState.value.newColumn)
        assertEquals(listOf("Columna c1", "Revisión"), viewModel.uiState.value.columns.map { it.column.name })
    }

    @Test
    fun saveNewColumn_blankName_keepsDialogOpenWithDomainError() = runTest {
        val columns = MutableStateFlow(listOf(column("c1", 0)))
        val viewModel = viewModel(columns, MutableStateFlow(emptyList()), columnRepository = FakeColumnRepository(columns, columns))
        collect(viewModel)

        viewModel.openNewColumn()
        viewModel.onNewColumnNameChange("   ")
        viewModel.saveNewColumn()

        val newColumn = viewModel.uiState.value.newColumn
        assertTrue(newColumn.isOpen)
        assertEquals("   ", newColumn.name)
        assertEquals(COLUMN_NAME_REQUIRED, (newColumn.error as DomainError.Validation).code)
    }

    @Test
    fun saveNewColumn_tooLongName_keepsDialogOpenWithSpanishMessage() = runTest {
        val columns = MutableStateFlow(listOf(column("c1", 0)))
        val viewModel = viewModel(columns, MutableStateFlow(emptyList()), columnRepository = FakeColumnRepository(columns, columns))
        collect(viewModel)

        viewModel.openNewColumn()
        viewModel.onNewColumnNameChange("x".repeat(COLUMN_NAME_MAX_LENGTH + 1))
        viewModel.saveNewColumn()

        val newColumn = viewModel.uiState.value.newColumn
        assertTrue(newColumn.isOpen)
        assertEquals("El nombre de la columna no puede superar los 100 caracteres", newColumn.error?.message)
    }

    @Test
    fun onNewColumnNameChange_clearsError() = runTest {
        val columns = MutableStateFlow(listOf(column("c1", 0)))
        val viewModel = viewModel(columns, MutableStateFlow(emptyList()), columnRepository = FakeColumnRepository(columns, columns))
        collect(viewModel)
        viewModel.openNewColumn()
        viewModel.onNewColumnNameChange(" ")
        viewModel.saveNewColumn()

        viewModel.onNewColumnNameChange("Hecho")

        assertNull(viewModel.uiState.value.newColumn.error)
        assertEquals("Hecho", viewModel.uiState.value.newColumn.name)
    }

    @Test
    fun dismissNewColumn_resetsState() = runTest {
        val columns = MutableStateFlow(listOf(column("c1", 0)))
        val viewModel = viewModel(columns, MutableStateFlow(emptyList()), columnRepository = FakeColumnRepository(columns, columns))
        collect(viewModel)
        viewModel.openNewColumn()
        viewModel.onNewColumnNameChange(" ")
        viewModel.saveNewColumn()

        viewModel.dismissNewColumn()

        assertEquals(NewColumnUiState(), viewModel.uiState.value.newColumn)
    }

    @Test
    fun saveNewColumn_failure_doesNotAffectMoveError() = runTest {
        val columns = MutableStateFlow(listOf(column("c1", 0)))
        val viewModel = viewModel(columns, MutableStateFlow(emptyList()), columnRepository = FakeColumnRepository(columns, columns))
        collect(viewModel)
        viewModel.openNewColumn()
        viewModel.onNewColumnNameChange(" ")

        viewModel.saveNewColumn()

        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.newColumn.error != null)
    }

    @Test
    fun moveTask_wipLimitReached_revertsAndExposesError() = runTest {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0), task("x", "c2", 0)))
        val error = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "La columna alcanzó su límite")
        val repository = FakeTaskRepository(tasks).apply { failure = error }
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0), column("c2", 1))),
            tasks,
            repository,
        )
        collect(viewModel)

        viewModel.moveTask("a", "c2", 0)

        assertEquals(listOf("a"), viewModel.uiState.value.columns[0].cards.map { it.id })
        assertEquals(listOf("x"), viewModel.uiState.value.columns[1].cards.map { it.id })
        assertEquals(error, viewModel.uiState.value.error)
    }

    @Test
    fun overdue_enColumnaNormal_esTrue() = runTest {
        assertEquals(true, overdueOf(NOW.minusSeconds(1), isDone = false))
    }

    @Test
    fun overdue_mismaFechaEnColumnaDeCompletadas_esFalse() = runTest {
        assertEquals(false, overdueOf(NOW.minusSeconds(1), isDone = true))
    }

    @Test
    fun overdue_fechaFutura_esFalse() = runTest {
        assertEquals(false, overdueOf(NOW.plusSeconds(1), isDone = false))
    }

    @Test
    fun overdue_sinFecha_esFalse() = runTest {
        assertEquals(false, overdueOf(null, isDone = false))
    }

    @Test
    fun overdue_fechaIgualAAhora_esFalse() = runTest {
        assertEquals(false, overdueOf(NOW, isDone = false))
    }

    @Test
    fun overdue_tarjetaArrastradaDeFormaOptimistaACompletadas_esFalse() = runTest {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0, dueAt = NOW.minusSeconds(60))))
        val repository = FakeTaskRepository(tasks).apply { gate = CompletableDeferred() }
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0), column("c2", 1, isDoneColumn = true))),
            tasks,
            repository,
        )
        collect(viewModel)
        assertTrue(viewModel.uiState.value.columns[0].cards.single().isOverdue)

        viewModel.moveTask("a", "c2", 0)

        assertFalse(viewModel.uiState.value.columns[1].cards.single().isOverdue)
    }

    @Test
    fun doneColumnFlag_isExposedPerColumn() = runTest {
        val tasks = MutableStateFlow(emptyList<Task>())
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0), column("c2", 1, isDoneColumn = true))),
            tasks,
        )
        collect(viewModel)

        assertEquals(listOf(false, true), viewModel.uiState.value.columns.map { it.column.isDoneColumn })
    }

    @Test
    fun toggleMyDay_tarjetaSinMarca_delegaConMarcadoTrue() = runTest {
        val myDayRepository = FakeMyDayRepository()
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0))),
            MutableStateFlow(listOf(task("a", "c1", 0))),
            myDay = MutableStateFlow(emptySet()),
            myDayRepository = myDayRepository,
        )
        collect(viewModel)

        viewModel.toggleMyDay("a")

        assertEquals(listOf("a" to true), myDayRepository.calls)
    }

    @Test
    fun toggleMyDay_tarjetaMarcada_delegaConMarcadoFalse() = runTest {
        val myDayRepository = FakeMyDayRepository()
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0))),
            MutableStateFlow(listOf(task("a", "c1", 0))),
            myDay = MutableStateFlow(setOf("a")),
            myDayRepository = myDayRepository,
        )
        collect(viewModel)

        viewModel.toggleMyDay("a")

        assertEquals(listOf("a" to false), myDayRepository.calls)
    }

    @Test
    fun toggleMyDay_fallo_sePropagaAError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        val myDayRepository = FakeMyDayRepository().apply { this.failure = failure }
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0))),
            MutableStateFlow(listOf(task("a", "c1", 0))),
            myDayRepository = myDayRepository,
        )
        collect(viewModel)

        viewModel.toggleMyDay("a")

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun tarjeta_estrellaSigueElFlujoDelRepositorioDeMyDay() = runTest {
        val myDayRepository = FakeMyDayRepository()
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0))),
            MutableStateFlow(listOf(task("a", "c1", 0))),
            myDayRepository = myDayRepository,
        )
        collect(viewModel)
        assertFalse(viewModel.uiState.value.columns.single().cards.single().inMyDay)

        myDayRepository.marked.value = setOf("a")

        assertTrue(viewModel.uiState.value.columns.single().cards.single().inMyDay)
    }

    @Test
    fun tarjeta_marcadaEnColumnaCompletada_conservaLaEstrella() = runTest {
        val viewModel = viewModel(
            MutableStateFlow(listOf(column("c1", 0, isDoneColumn = true))),
            MutableStateFlow(listOf(task("a", "c1", 0))),
            myDay = MutableStateFlow(setOf("a")),
        )
        collect(viewModel)

        assertTrue(viewModel.uiState.value.columns.single().cards.single().inMyDay)
    }

    private fun TestScope.overdueOf(dueAt: Instant?, isDone: Boolean): Boolean {
        val tasks = MutableStateFlow(listOf(task("a", "c1", 0, dueAt = dueAt)))
        val viewModel = viewModel(MutableStateFlow(listOf(column("c1", 0, isDone))), tasks)
        collect(viewModel)
        return viewModel.uiState.value.columns.single().cards.single().isOverdue
    }

    private fun TestScope.collect(viewModel: BoardViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun viewModel(
        columns: Flow<List<Column>>,
        tasks: MutableStateFlow<List<Task>>,
        repository: FakeTaskRepository = FakeTaskRepository(tasks),
        myDay: Flow<Set<String>>? = null,
        myDayRepository: FakeMyDayRepository = FakeMyDayRepository(),
        columnRepository: ColumnRepository = FakeColumnRepository(columns),
        now: () -> Instant = { NOW },
    ): BoardViewModel {
        val handle = SavedStateHandle(mapOf(BoardViewModel.BOARD_ID_ARG to "tablero-1"))
        return if (myDay == null) {
            BoardViewModel(columnRepository, repository, madrid, handle, myDayRepository, now = now)
        } else {
            BoardViewModel(columnRepository, repository, madrid, handle, myDayRepository, myDay, now)
        }
    }

    private fun column(id: String, position: Int, isDoneColumn: Boolean = false): Column = Column(
        boardId = "tablero-1",
        name = "Columna $id",
        position = position,
        wipLimit = null,
        isDoneColumn = isDoneColumn,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
    )

    private fun task(
        id: String,
        columnId: String,
        position: Int,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Instant? = null,
        assigneeId: String? = null,
    ): Task = Task(
        columnId = columnId,
        title = "Tarea $id",
        description = null,
        priority = priority,
        dueAt = dueAt,
        assigneeId = assigneeId,
        position = position,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
    )

    private class FakeColumnRepository(
        private val columns: Flow<List<Column>>,
        private val columnsState: MutableStateFlow<List<Column>>? = null,
    ) : ColumnRepository {
        val created = mutableListOf<Pair<String, String>>()

        override fun observeColumns(boardId: String): Flow<List<Column>> = columns

        override suspend fun createColumn(boardId: String, name: String): DomainResult<Column> {
            created += boardId to name
            val state = columnsState ?: unused()
            val trimmed = name.trim()
            nameFailure(trimmed)?.let { return it }
            val column = Column(
                boardId = boardId,
                name = trimmed,
                position = state.value.size,
                wipLimit = null,
                isDoneColumn = false,
                createdAt = Instant.EPOCH,
                sync = SyncMetadata("nueva-${state.value.size}", Instant.EPOCH, pending = false, deletedAt = null),
            )
            state.value = state.value + column
            return DomainResult.Success(column)
        }

        private fun nameFailure(trimmed: String): DomainResult.Failure? = when {
            trimmed.isEmpty() -> DomainResult.Failure(DomainError.Validation(COLUMN_NAME_REQUIRED, "El nombre de la columna es obligatorio"))
            trimmed.length > COLUMN_NAME_MAX_LENGTH -> DomainResult.Failure(
                DomainError.Validation(
                    COLUMN_NAME_TOO_LONG,
                    "El nombre de la columna no puede superar los $COLUMN_NAME_MAX_LENGTH caracteres",
                ),
            )
            else -> null
        }

        override suspend fun renameColumn(id: String, name: String) = unused()

        override suspend fun setWipLimit(columnId: String, limit: Int?) = unused()

        override suspend fun moveColumn(id: String, toIndex: Int) = unused()

        override suspend fun deleteColumn(id: String): DomainResult<Unit> = unused()

        private fun unused(): Nothing = error("no se usa")
    }

    private class FakeTaskRepository(private val tasks: MutableStateFlow<List<Task>>) : TaskRepository {
        val moves: MutableList<Triple<String, String, Int>> = mutableListOf()
        var failure: DomainError? = null
        var gate: CompletableDeferred<Unit>? = null

        override fun observeTasks(boardId: String): Flow<List<Task>> = tasks

        override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> {
            moves += Triple(id, toColumnId, toIndex)
            gate?.await()
            failure?.let { return DomainResult.Failure(it) }
            val current = tasks.value.first { it.id == id }
            val destination = tasks.value.filter { it.columnId == toColumnId && it.id != id }
                .sortedBy { it.position }.toMutableList()
            val moved = current.copy(columnId = toColumnId)
            destination.add(toIndex, moved)
            val renumbered = destination.mapIndexed { index, task -> task.copy(position = index) }
            tasks.value = tasks.value.filter { it.columnId != toColumnId && it.id != id } + renumbered
            return DomainResult.Success(renumbered[toIndex])
        }

        override suspend fun getTask(id: String): DomainResult<Task> = error("no se usa")

        override suspend fun createTask(columnId: String, draft: TaskDraft): DomainResult<Task> = error("no se usa")

        override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> = error("no se usa")

        override suspend fun deleteTask(id: String): DomainResult<Unit> = error("no se usa")
    }

    class FakeMyDayRepository : MyDayRepository {
        val marked = MutableStateFlow<Set<String>>(emptySet())
        val calls: MutableList<Pair<String, Boolean>> = mutableListOf()
        var failure: DomainError? = null

        override fun observeMyDay(): Flow<MyDay> = error("no se usa")

        override fun observeMarkedTaskIds(): Flow<Set<String>> = marked

        override fun observeOverdueCount(): Flow<Int> = error("no se usa")

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
            calls += taskId to marked
            return failure?.let { DomainResult.Failure(it) } ?: DomainResult.Success(Unit)
        }
    }
}
