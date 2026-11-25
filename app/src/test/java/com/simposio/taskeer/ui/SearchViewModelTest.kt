package com.simposio.taskeer.ui

import androidx.lifecycle.SavedStateHandle
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.search.SearchIndex
import com.simposio.taskeer.domain.search.SearchRules
import com.simposio.taskeer.domain.search.SearchableTask
import com.simposio.taskeer.testing.FakeSearchRepository
import com.simposio.taskeer.ui.search.SearchStatus
import com.simposio.taskeer.ui.search.SearchTarget
import com.simposio.taskeer.ui.search.SearchViewModel
import com.simposio.taskeer.ui.search.targetOf
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSearchRepository(index())
    private val handle = SavedStateHandle()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sync(id: String) = SyncMetadata(id, now, pending = false, deletedAt = null)

    private fun searchableTask(id: String, title: String, completed: Boolean = false) = SearchableTask(
        task = Task(
            columnId = "c1",
            title = title,
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = null,
            assigneeId = null,
            position = 0,
            createdAt = now,
            completedAt = if (completed) now else null,
            sync = sync(id),
        ),
        boardId = "b1",
        boardName = "Casa",
        columnName = "Por hacer",
        listName = null,
    )

    private fun index(
        tasks: List<SearchableTask> = listOf(searchableTask("t1", "Pagar factura")),
        lists: List<TaskList> = emptyList(),
    ) = SearchIndex(
        boards = listOf(Board("Casa", now, sync("b1"))),
        lists = lists,
        columns = listOf(Column("b1", "Facturas", 0, null, false, now, sync("c1"))),
        tasks = tasks,
    )

    private fun TestScope.viewModel(): SearchViewModel {
        val viewModel = SearchViewModel(repository, handle, dispatcher)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        runCurrent()
        return viewModel
    }

    private fun TestScope.type(viewModel: SearchViewModel, text: String) {
        viewModel.onQueryChange(text)
        runCurrent()
    }

    private fun TestScope.settle() {
        advanceTimeBy(SearchRules.DEBOUNCE_MILLIS)
        runCurrent()
    }

    @Test
    fun query_seConservaEnSavedStateHandle() = runTest(dispatcher) {
        val viewModel = viewModel()

        type(viewModel, "casa")

        assertEquals("casa", handle.get<String>(SearchViewModel.QUERY_KEY))
        assertEquals("casa", SearchViewModel(repository, handle, dispatcher).uiState.value.query)
    }

    @Test
    fun clear_vaciaLaConsulta() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "casa")

        viewModel.clear()
        runCurrent()

        assertEquals("", viewModel.uiState.value.query)
        assertEquals("", handle.get<String>(SearchViewModel.QUERY_KEY))
    }

    @Test
    fun escribirRapido_soloCalculaLaUltimaConsulta() = runTest(dispatcher) {
        val viewModel = viewModel()

        type(viewModel, "f")
        advanceTimeBy(100)
        type(viewModel, "fa")
        advanceTimeBy(100)
        type(viewModel, "factura")
        settle()

        assertEquals(1, repository.subscriptions)
        assertEquals("factura", viewModel.uiState.value.query)
        assertEquals(SearchStatus.Results, viewModel.uiState.value.status)
    }

    @Test
    fun antesDelDebounce_noHayResultadosNuevos() = runTest(dispatcher) {
        val viewModel = viewModel()

        type(viewModel, "factura")
        advanceTimeBy(SearchRules.DEBOUNCE_MILLIS - 1)
        runCurrent()

        assertEquals(SearchStatus.Idle, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.results.isEmpty)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(SearchStatus.Results, viewModel.uiState.value.status)
    }

    @Test
    fun consultaVacia_pasaAInactivoSinEsperar() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "factura")
        settle()

        type(viewModel, "   ")

        assertEquals(SearchStatus.Idle, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.results.isEmpty)
    }

    @Test
    fun unCambioDeDatos_reactualizaLosResultados() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "factura")
        settle()
        assertEquals(1, viewModel.uiState.value.results.tasks.size)

        repository.index.value = index(listOf(searchableTask("t1", "Pagar factura"), searchableTask("t2", "Otra factura")))
        runCurrent()

        assertEquals(listOf("t1", "t2"), viewModel.uiState.value.results.tasks.map { it.id }.sorted())
    }

    @Test
    fun cambiarLaConsulta_cancelaLaObservacionAnterior() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "factura")
        settle()
        assertEquals(1, repository.activeCollectors)

        type(viewModel, "casa")
        settle()

        assertEquals(2, repository.subscriptions)
        assertEquals(1, repository.activeCollectors)
    }

    @Test
    fun sinConsulta_estadoInactivo() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertEquals(SearchStatus.Idle, viewModel.uiState.value.status)
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun consultaSinCoincidencias_estadoSinResultados() = runTest(dispatcher) {
        val viewModel = viewModel()

        type(viewModel, "zzzz")
        settle()

        assertEquals(SearchStatus.NoResults, viewModel.uiState.value.status)
        assertEquals("zzzz", viewModel.uiState.value.query)
    }

    @Test
    fun consultaConCoincidencias_estadoConResultados() = runTest(dispatcher) {
        val viewModel = viewModel()

        type(viewModel, "FACTURA")
        settle()

        val results = viewModel.uiState.value.results
        assertEquals(SearchStatus.Results, viewModel.uiState.value.status)
        assertEquals(listOf("c1"), results.columns.map { it.id })
        assertEquals(listOf("t1"), results.tasks.map { it.id })
    }

    @Test
    fun tablero_exponeSuId() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "casa")
        settle()

        val hit = viewModel.uiState.value.results.boards.single()

        assertEquals(SearchTarget.OpenBoard("b1"), targetOf(hit))
    }

    @Test
    fun columna_exponeElIdDelTablero() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "facturas")
        settle()

        val hit = viewModel.uiState.value.results.columns.single()

        assertEquals(SearchTarget.OpenBoard("b1"), targetOf(hit))
    }

    private fun taskList(id: String, name: String) = TaskList(name, 0, now, sync(id))

    @Test
    fun lista_exponeSuId() = runTest(dispatcher) {
        repository.index.value = index(lists = listOf(taskList("l1", "Compras")))
        val viewModel = viewModel()
        type(viewModel, "compras")
        settle()

        val hit = viewModel.uiState.value.results.lists.single()

        assertEquals(SearchTarget.OpenList("l1"), targetOf(hit))
    }

    @Test
    fun listaRenombrada_actualizaLosResultados() = runTest(dispatcher) {
        repository.index.value = index(lists = listOf(taskList("l1", "Compras")))
        val viewModel = viewModel()
        type(viewModel, "compras")
        settle()

        repository.index.value = index(lists = listOf(taskList("l1", "Mercado")))
        runCurrent()

        assertEquals(SearchStatus.NoResults, viewModel.uiState.value.status)
    }

    @Test
    fun listaBorrada_desapareceDeLosResultados() = runTest(dispatcher) {
        repository.index.value = index(lists = listOf(taskList("l1", "Compras")))
        val viewModel = viewModel()
        type(viewModel, "compras")
        settle()
        assertEquals(1, viewModel.uiState.value.results.lists.size)

        repository.index.value = index()
        runCurrent()

        assertTrue(viewModel.uiState.value.results.lists.isEmpty())
    }

    @Test
    fun tarea_exponeSuIdParaElEditor() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "pagar")
        settle()

        val hit = viewModel.uiState.value.results.tasks.single()

        assertEquals(SearchTarget.OpenTask("t1"), targetOf(hit))
    }

    @Test
    fun tareaEditada_actualizaLosResultados() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "pagar")
        settle()

        repository.index.value = index(listOf(searchableTask("t1", "Pagar factura", completed = true)))
        runCurrent()

        assertTrue(viewModel.uiState.value.results.tasks.single().task.task.isCompleted)

        repository.index.value = index(listOf(searchableTask("t1", "Cobrar factura")))
        runCurrent()

        assertEquals(SearchStatus.NoResults, viewModel.uiState.value.status)
    }

    @Test
    fun tareaBorrada_desapareceDeLosResultados() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "pagar")
        settle()

        repository.index.value = index(emptyList())
        runCurrent()

        assertTrue(viewModel.uiState.value.results.tasks.isEmpty())
        assertEquals(SearchStatus.NoResults, viewModel.uiState.value.status)
    }

    @Test
    fun sinResultados_borrarBusquedaVaciaLaConsulta() = runTest(dispatcher) {
        val viewModel = viewModel()
        type(viewModel, "zzzz")
        settle()
        assertEquals(SearchStatus.NoResults, viewModel.uiState.value.status)

        viewModel.clear()
        runCurrent()

        assertEquals("", viewModel.uiState.value.query)
        assertEquals(SearchStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun factory_construyeElViewModelConSuSavedStateHandle() {
        val relative = "app/src/main/java/com/simposio/taskeer/di/AppViewModelFactory.kt"
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val source = generateSequence(start) { it.parentFile }.map { File(it, relative) }.first { it.isFile }.readText()

        assertTrue(source.contains("SearchViewModel(container.searchRepository, extras.createSavedStateHandle())"))
    }
}
