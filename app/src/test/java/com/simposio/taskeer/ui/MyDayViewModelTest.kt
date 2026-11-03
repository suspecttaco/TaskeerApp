package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.myday.MyDayFilter
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.ui.myday.MyDayUiState
import com.simposio.taskeer.ui.myday.MyDayViewModel
import java.time.Instant
import java.time.ZoneId
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
class MyDayViewModelTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")
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
    fun items_soloMarcada_unElementoMarcado() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList())))

        val card = viewModel.uiState.value.items.single()

        assertTrue(card.isMarked)
        assertFalse(card.isAssigned)
    }

    @Test
    fun items_soloAsignada_unElementoAsignado() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList(), listOf(item("a1", dueAt = now)))))

        val card = viewModel.uiState.value.items.single()

        assertFalse(card.isMarked)
        assertTrue(card.isAssigned)
    }

    @Test
    fun items_marcadaYAsignada_unSoloElementoConAmbosOrigenes() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("t1", addedAt = now)), listOf(item("t1", dueAt = now.plusSeconds(5)))),
        )
        val viewModel = viewModel(repository)

        val state = viewModel.uiState.value

        assertEquals(1, state.items.size)
        assertTrue(state.items.single().isMarked && state.items.single().isAssigned)
        assertEquals(1, state.totalCount)
    }

    @Test
    fun items_vacio_listaVaciaYTotalCero() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList(), emptyList())))

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(0, viewModel.uiState.value.totalCount)
    }

    @Test
    fun tarjeta_exponeTableroPrioridadYFechaEnZonaDelUsuario() = runTest {
        val due = Instant.parse("2026-11-03T10:30:00Z")
        val repository = FakeMyDayRepository(
            MyDay(emptyList(), listOf(item("a1", priority = TaskPriority.URGENT, dueAt = due))),
        )
        val viewModel = viewModel(repository)

        val card = viewModel.uiState.value.items.single()

        assertEquals("Tablero", card.boardName)
        assertEquals(TaskPriority.URGENT, card.priority)
        assertTrue(card.due.orEmpty().contains("11:30"))
    }

    @Test
    fun tarjeta_isOverdue_dependeDelRelojInyectado() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                emptyList(),
                listOf(
                    item("pasada", dueAt = now.minusSeconds(1)),
                    item("igual", dueAt = now),
                    item("futura", dueAt = now.plusSeconds(1)),
                ),
            ),
        )
        val viewModel = viewModel(repository)

        val overdue = viewModel.uiState.value.items.associate { it.id to it.isOverdue }

        assertEquals(mapOf("pasada" to true, "igual" to false, "futura" to false), overdue)
    }

    @Test
    fun tarjetaSinFecha_noTieneDueNiVencida() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList())))

        val card = viewModel.uiState.value.items.single()

        assertNull(card.due)
        assertFalse(card.isOverdue)
    }

    @Test
    fun estadoInicial_ordenFechaLimiteYFiltroTodas() = runTest {
        val state = viewModel(FakeMyDayRepository(MyDay(emptyList(), emptyList()))).uiState.value

        assertEquals(MyDaySort.DUE_DATE, state.sort)
        assertEquals(MyDayFilter.ALL, state.filter)
    }

    @Test
    fun setSort_reordenaLaListaYConservaLaSeleccion() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                emptyList(),
                listOf(
                    item("pronto-baja", TaskPriority.LOW, dueAt = now.plusSeconds(1)),
                    item("tarde-urgente", TaskPriority.URGENT, dueAt = now.plusSeconds(99)),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        assertEquals(listOf("pronto-baja", "tarde-urgente"), viewModel.uiState.value.items.map { it.id })

        viewModel.setSort(MyDaySort.PRIORITY)

        assertEquals(MyDaySort.PRIORITY, viewModel.uiState.value.sort)
        assertEquals(listOf("tarde-urgente", "pronto-baja"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun setFilter_muestraSoloLosQueLoCumplenYTotalNoCambia() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                pinned = listOf(item("m", addedAt = now)),
                assigned = listOf(item("a", dueAt = now.plusSeconds(10))),
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.setFilter(MyDayFilter.MARKED)
        assertEquals(listOf("m"), viewModel.uiState.value.items.map { it.id })
        viewModel.setFilter(MyDayFilter.ASSIGNED)

        assertEquals(listOf("a"), viewModel.uiState.value.items.map { it.id })
        assertEquals(2, viewModel.uiState.value.totalCount)
    }

    @Test
    fun setFilter_overdue_listaCoincideConElConteo() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                emptyList(),
                listOf(item("v1", dueAt = now.minusSeconds(5)), item("v2", dueAt = now.minusSeconds(9)), item("f", dueAt = now.plusSeconds(9))),
            ),
        )
        repository.overdue.value = 2
        val viewModel = viewModel(repository)

        viewModel.setFilter(MyDayFilter.OVERDUE)

        assertEquals(viewModel.uiState.value.overdueCount, viewModel.uiState.value.items.size)
    }

    @Test
    fun overdueCount_seExponeCompletoSinAbreviar() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList()))
        repository.overdue.value = 250
        val viewModel = viewModel(repository)

        assertEquals(250, viewModel.uiState.value.overdueCount)
    }

    @Test
    fun overdueCount_seExponeConCero() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList(), emptyList())))

        assertEquals(0, viewModel.uiState.value.overdueCount)
    }

    @Test
    fun toggleMarked_marcada_delegaSetMarkedFalse() = runTest {
        val repository = FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList()))
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(listOf("p1" to false), repository.calls)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun toggleMarked_noMarcada_delegaSetMarkedTrueYLaHaceMarcada() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), listOf(item("a1", dueAt = now.plusSeconds(5)))))
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("a1")

        assertEquals(listOf("a1" to true), repository.calls)
        assertTrue(viewModel.uiState.value.items.single().isMarked)
    }

    @Test
    fun toggleMarked_soloMarcada_laTarjetaDesaparece() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("p1", addedAt = now), item("p2", addedAt = now)), emptyList()),
        )
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(listOf("p2"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun toggleMarked_marcadaYAsignada_permaneceSinOrigenMarcada() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("t1", addedAt = now)), listOf(item("t1", dueAt = now.plusSeconds(5)))),
        )
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("t1")

        val card = viewModel.uiState.value.items.single()
        assertFalse(card.isMarked)
        assertTrue(card.isAssigned)
    }

    @Test
    fun toggleMarked_fallo_sePropagaAError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        val repository = FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList()))
            .apply { this.failure = failure }
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun toggleMarked_exito_limpiaElErrorPrevio() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("p1", addedAt = now), item("p2", addedAt = now)), emptyList()),
        )
        val viewModel = viewModel(repository)
        repository.failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        viewModel.toggleMarked("p1")
        repository.failure = null

        viewModel.toggleMarked("p2")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun estado_soloExponeItemsOrdenFiltroConteosYError() {
        val properties = MyDayUiState::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") || it.name == "Companion" }
            .map { it.name }
            .toSet()

        assertEquals(setOf("items", "sort", "filter", "overdueCount", "totalCount", "error"), properties)
    }

    private fun TestScope.viewModel(repository: FakeMyDayRepository): MyDayViewModel {
        val viewModel = MyDayViewModel(repository, madrid) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun item(
        id: String,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Instant? = null,
        addedAt: Instant? = null,
    ): MyDayTask = MyDayTask(
        task = Task(
            columnId = "c1",
            title = "Tarea $id",
            description = null,
            priority = priority,
            dueAt = dueAt,
            assigneeId = null,
            position = 0,
            createdAt = Instant.EPOCH,
            sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
        ),
        boardId = "b1",
        boardName = "Tablero",
        addedAt = addedAt,
    )

    private class FakeMyDayRepository(initial: MyDay) : MyDayRepository {
        private val myDay = MutableStateFlow(initial)
        val overdue = MutableStateFlow(0)
        val calls: MutableList<Pair<String, Boolean>> = mutableListOf()
        var failure: DomainError? = null

        override fun observeMyDay(): Flow<MyDay> = myDay

        override fun observeMarkedTaskIds(): Flow<Set<String>> = error("no se usa")

        override fun observeOverdueCount(): Flow<Int> = overdue

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
            calls += taskId to marked
            failure?.let { return DomainResult.Failure(it) }
            val current = myDay.value
            val known = (current.pinned + current.assigned).first { it.task.id == taskId }
            val pinned = current.pinned.filter { it.task.id != taskId }
            myDay.value = current.copy(pinned = if (marked) pinned + known.copy(addedAt = Instant.EPOCH) else pinned)
            return DomainResult.Success(Unit)
        }
    }
}
