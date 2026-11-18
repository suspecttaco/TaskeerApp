package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.TaskRepeat
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.domain.task.successorIdFor
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.listSummary
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.inbox.InboxViewModel
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")
    private val madrid = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })
    private val tasks = FakeDestinationTaskRepository()
    private val myDay = FakeMarkedRepository()
    private val lists = FakeTaskListRepository(
        listOf(listSummary(SEEDED_TASK_LIST_ID, "Tareas"), listSummary("compras", "Compras", position = 1)),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun items_ordenaPorCreacionDescendente() = runTest {
        tasks.inbox.value = listOf(task("nueva"), task("vieja"))

        val viewModel = viewModel()

        assertEquals(listOf("nueva", "vieja"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun sinTareas_estadoVacio() = runTest {
        assertTrue(viewModel().uiState.value.items.isEmpty())
    }

    @Test
    fun estrella_reflejaLasMarcadasDeMiDia() = runTest {
        tasks.inbox.value = listOf(task("a"), task("b"))
        val viewModel = viewModel()

        myDay.marked.value = setOf("b")

        assertEquals(listOf(false, true), viewModel.uiState.value.items.map { it.isMarked })
    }

    @Test
    fun estrella_marcaAnterior_apareceVacia() = runTest {
        tasks.inbox.value = listOf(task("ayer"), task("hoy"))
        val viewModel = viewModel()

        myDay.marked.value = setOf("hoy")

        assertEquals(mapOf("ayer" to false, "hoy" to true), viewModel.uiState.value.items.associate { it.id to it.isMarked })
        viewModel.toggleMarked("ayer")
        assertEquals(listOf("ayer" to true), myDay.calls)
    }

    @Test
    fun toggleMarked_delegaEnMyDayRepository() = runTest {
        tasks.inbox.value = listOf(task("a"))
        val viewModel = viewModel()

        viewModel.toggleMarked("a")

        assertEquals(listOf("a" to true), myDay.calls)
    }

    @Test
    fun fechaVencida_seMarcaVencida() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = now.minusSeconds(60)), task("b", dueAt = now.plusSeconds(60)))

        val items = viewModel().uiState.value.items

        assertTrue(items[0].isOverdue)
        assertFalse(items[1].isOverdue)
    }

    @Test
    fun toggleCompleted_llamaASetCompleted() = runTest {
        tasks.inbox.value = listOf(task("a"), task("b", completedAt = now))
        val viewModel = viewModel()

        viewModel.toggleCompleted("a")
        viewModel.toggleCompleted("b")

        assertEquals(listOf("a" to true, "b" to false), tasks.completions)
    }

    @Test
    fun toggleCompleted_tareaRepetida_apareceLaSiguiente() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = now).copy(repeat = TaskRepeat(RepeatUnit.DAY, 1, emptySet(), 2)))
        val viewModel = viewModel()

        viewModel.toggleCompleted("a")

        val items = viewModel.uiState.value.items
        assertEquals(listOf(successorIdFor("a"), "a"), items.map { it.id })
        assertEquals(listOf(false, true), items.map { it.isCompleted })
    }

    @Test
    fun tareas_pendientesYLuegoCompletadas() = runTest {
        tasks.inbox.value = listOf(task("hecha", completedAt = now), task("p1"), task("p2"))

        val items = viewModel().uiState.value.items

        assertEquals(listOf("p1", "p2", "hecha"), items.map { it.id })
        assertEquals(listOf(false, false, true), items.map { it.isCompleted })
    }

    @Test
    fun completada_noEsVencida() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = now.minusSeconds(60), completedAt = now))

        assertFalse(viewModel().uiState.value.items.single().isOverdue)
    }

    @Test
    fun toggleCompleted_conFallo_exponeError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        tasks.inbox.value = listOf(task("a"))
        tasks.completeFailure = failure
        val viewModel = viewModel()

        viewModel.toggleCompleted("a")

        assertEquals(failure, viewModel.uiState.value.error)
        assertEquals(listOf("a"), viewModel.uiState.value.items.map { it.id })
        assertFalse(viewModel.uiState.value.items.single().isCompleted)
    }

    @Test
    fun manana_soloReprograma() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = Instant.parse("2026-11-01T17:30:00Z")))
        myDay.marked.value = setOf("a")
        val viewModel = viewModel()

        viewModel.reschedule("a", RescheduleTarget.TOMORROW)

        assertEquals(Instant.parse("2026-11-03T17:30:00Z"), tasks.updates.single().second.dueAt)
        assertTrue(myDay.calls.isEmpty())
        assertTrue(viewModel.uiState.value.items.single().isMarked)
    }

    @Test
    fun rescheduleTo_fallo_exponeError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        tasks.inbox.value = listOf(task("a"))
        tasks.updateFailure = failure
        val viewModel = viewModel()

        viewModel.rescheduleTo("a", LocalDate.of(2026, 12, 1))

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun tareaRepetida_exponeIsRepeating() = runTest {
        tasks.inbox.value = listOf(task("a").copy(repeat = TaskRepeat(RepeatUnit.DAY, 1, emptySet(), 2)), task("b"))

        val items = viewModel().uiState.value.items

        assertEquals(mapOf("a" to true, "b" to false), items.associate { it.id to it.isRepeating })
    }

    @Test
    fun onRowAction_anadirYQuitarDeMiDia_alternaLaMarca() = runTest {
        tasks.inbox.value = listOf(task("a"))
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.ADD_TO_MY_DAY)

        assertEquals(listOf("a" to true), myDay.calls)
    }

    @Test
    fun onRowAction_proximaSemana_reprograma() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = Instant.parse("2026-11-01T17:30:00Z")))
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.RESCHEDULE_NEXT_WEEK)

        assertEquals(Instant.parse("2026-11-09T17:30:00Z"), tasks.updates.single().second.dueAt)
    }

    @Test
    fun estadoInicial_esLaListaPorDefecto() = runTest {
        tasks.inbox.value = listOf(task("a"), task("b", listId = "compras"))

        val state = viewModel().uiState.value

        assertEquals(SEEDED_TASK_LIST_ID, state.listId)
        assertEquals(listOf("a"), state.items.map { it.id })
    }

    @Test
    fun cambiarDeLista_observaLasTareasDeLaLista() = runTest {
        tasks.inbox.value = listOf(task("a"), task("b", listId = "compras"))
        val selected = MutableStateFlow<String?>(null)
        val viewModel = viewModel(selected)

        selected.value = "compras"

        assertEquals("compras", viewModel.uiState.value.listId)
        assertEquals(listOf("b"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun listaSeleccionadaBorrada_muestraLaPorDefectoResultante() = runTest {
        tasks.inbox.value = listOf(task("a"), task("b", listId = "compras"))
        val selected = MutableStateFlow<String?>("compras")
        val viewModel = viewModel(selected)
        assertEquals(listOf("b"), viewModel.uiState.value.items.map { it.id })

        lists.lists.value = lists.lists.value.filter { it.list.id != "compras" }

        assertEquals(SEEDED_TASK_LIST_ID, viewModel.uiState.value.listId)
        assertEquals(listOf("a"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun laListaPorDefectoEsLaDeMenorPosicion_noLaSembrada() = runTest {
        lists.lists.value = listOf(listSummary("compras", "Compras", position = 1))
        tasks.inbox.value = listOf(task("a"), task("b", listId = "compras"))

        val state = viewModel().uiState.value

        assertEquals("compras", state.listId)
        assertEquals(listOf("b"), state.items.map { it.id })
    }

    @Test
    fun moverALista_laTareaSaleDeLaLista() = runTest {
        tasks.inbox.value = listOf(task("a"), task("b"))
        val viewModel = viewModel()

        tasks.moveToList("a", "compras")

        assertEquals(listOf("b"), viewModel.uiState.value.items.map { it.id })
        assertEquals(listOf("a" to "compras"), tasks.listMoves)
    }

    private fun task(
        id: String,
        dueAt: Instant? = null,
        completedAt: Instant? = null,
        listId: String = SEEDED_TASK_LIST_ID,
    ) = Task(
        columnId = null,
        listId = listId,
        title = "Tarea $id",
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        completedAt = completedAt,
        sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
    )

    private fun TestScope.viewModel(selected: MutableStateFlow<String?> = MutableStateFlow(null)): InboxViewModel {
        val rescheduler = TaskRescheduler(tasks, madrid) { LocalDate.of(2026, 11, 2) }
        val viewModel = InboxViewModel(tasks, lists, myDay, madrid, rescheduler, { now }, selected)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private class FakeMarkedRepository : MyDayRepository {
        val marked = MutableStateFlow<Set<String>>(emptySet())
        val calls = mutableListOf<Pair<String, Boolean>>()

        override fun observeMyDay(): Flow<MyDay> = emptyFlow()

        override fun observeMarkedTaskIds(): Flow<Set<String>> = marked

        override fun observeOverdueCount(): Flow<Int> = emptyFlow()

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
            calls += taskId to marked
            return DomainResult.Success(Unit)
        }
    }
}
