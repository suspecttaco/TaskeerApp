package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.DayLabel
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.listSummary
import com.simposio.taskeer.ui.quickadd.QuickAddContext
import com.simposio.taskeer.ui.quickadd.QuickAddViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class QuickAddViewModelTest {

    private val tasks = FakeDestinationTaskRepository()
    private val lists = FakeTaskListRepository(
        listOf(listSummary(SEEDED_TASK_LIST_ID, "Tareas", 3), listSummary("compras", "Compras", 2, position = 1)),
    )
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
    fun add_tituloValido_creaEnLaListaPorDefecto() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Comprar pan")

        viewModel.add()

        val (columnId, creation) = tasks.creations.single()
        assertNull(columnId)
        assertEquals("Comprar pan", creation.task.title)
        assertEquals(listOf<String?>(null), tasks.creationListIds)
        assertFalse(creation.addToMyDay)
    }

    @Test
    fun add_tituloVacioOEnBlanco_noEscribe() = runTest {
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onTitleChange("")
        viewModel.add()
        viewModel.onTitleChange("   ")
        viewModel.add()

        assertTrue(tasks.creations.isEmpty())
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun add_recortaElTitulo() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("  Comprar pan  ")

        viewModel.add()

        assertEquals("Comprar pan", tasks.creations.single().second.task.title)
    }

    @Test
    fun add_exito_vaciaElTituloYSigueAbierta() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")

        viewModel.add()

        val state = viewModel.uiState.value
        assertEquals("", state.title)
        assertTrue(state.isOpen)
        assertNull(state.error)
        assertFalse(state.isSaving)
    }

    @Test
    fun add_falla_conservaTextoYExponeError() = runTest {
        val error = DomainError.Validation("TASK_TITLE_TOO_LONG", "Demasiado largo")
        tasks.createFailure = error
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")

        viewModel.add()

        val state = viewModel.uiState.value
        assertEquals(error, state.error)
        assertEquals("Informe", state.title)
        assertTrue(state.isOpen)
        assertFalse(state.isSaving)
    }

    @Test
    fun add_prioridadPorDefectoEsMedia() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals(TaskPriority.MEDIUM, tasks.creations.single().second.task.priority)
    }

    @Test
    fun add_mientrasGuarda_ignoraSegundoEnvio() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gated = GatedTaskRepository(tasks, gate)
        val viewModel = QuickAddViewModel(gated, lists, madrid) { LocalDate.of(2026, 11, 17) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.open()
        viewModel.onTitleChange("Informe")

        viewModel.add()
        viewModel.add()
        gate.complete(Unit)

        assertEquals(1, tasks.creations.size)
    }

    @Test
    fun onBack_cierraYDescartaElTexto() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Comprar pan")

        viewModel.onBack()

        assertFalse(viewModel.uiState.value.isOpen)
        assertEquals("", viewModel.uiState.value.title)
    }

    @Test
    fun open_sinFecha_noFijaVencimiento() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertNull(viewModel.uiState.value.dueDate)
        assertNull(tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun add_enMiDia_marcaLaTarea() = runTest {
        val viewModel = viewModel()
        viewModel.setContext(QuickAddContext.MyDay)
        viewModel.open()
        viewModel.onTitleChange("Comprar pan")

        viewModel.add()

        val creation = tasks.creations.single().second
        assertTrue(creation.addToMyDay)
        assertEquals(listOf<String?>(null), tasks.creationListIds)
    }

    @Test
    fun add_enListaAbierta_usaEsaLista() = runTest {
        val viewModel = viewModel()
        viewModel.setContext(QuickAddContext.InList("compras"))
        viewModel.open()
        viewModel.onTitleChange("Leche")

        viewModel.add()

        assertEquals(listOf<String?>("compras"), tasks.creationListIds)
        assertFalse(tasks.creations.single().second.addToMyDay)
    }

    @Test
    fun add_enListaBorrada_vaALaPorDefecto() = runTest {
        val viewModel = viewModel()
        viewModel.setContext(QuickAddContext.InList("compras"))
        viewModel.open()
        lists.lists.value = lists.lists.value.filter { it.list.id != "compras" }
        viewModel.onTitleChange("Leche")

        viewModel.add()

        assertEquals(listOf<String?>(null), tasks.creationListIds)
    }

    @Test
    fun add_conFechaDeSeccion_guardaVencimientoConHoraPorDefecto() = runTest {
        val viewModel = viewModel()
        viewModel.openWithDate(LocalDate.of(2026, 11, 18))
        viewModel.onTitleChange("Pagar")

        viewModel.add()

        assertEquals(Instant.parse("2026-11-18T08:00:00Z"), tasks.creations.single().second.task.dueAt)
        assertEquals(LocalDate.of(2026, 11, 18), viewModel.uiState.value.dueDate)
    }

    @Test
    fun quitarFecha_dejaLaTareaSinFecha() = runTest {
        val viewModel = viewModel()
        viewModel.openWithDate(LocalDate.of(2026, 11, 18))
        viewModel.onTitleChange("Pagar")

        viewModel.clearDate()
        viewModel.add()

        assertNull(viewModel.uiState.value.dueDate)
        assertNull(tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun cerrar_olvidaLaFechaDeSeccion() = runTest {
        val viewModel = viewModel()
        viewModel.openWithDate(LocalDate.of(2026, 11, 18))

        viewModel.close()
        viewModel.open()

        assertNull(viewModel.uiState.value.dueDate)
    }

    @Test
    fun openWithDate_exponeLaEtiquetaRelativaAHoy() = runTest {
        val viewModel = viewModel()

        viewModel.openWithDate(LocalDate.of(2026, 11, 18))

        assertEquals(DayLabel.Tomorrow, viewModel.uiState.value.dueLabel)
    }

    @Test
    fun onTitleChange_limpiaElError() = runTest {
        tasks.createFailure = DomainError.Validation("TASK_TITLE_TOO_LONG", "Demasiado largo")
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.add()
        assertEquals("TASK_TITLE_TOO_LONG", viewModel.uiState.value.error?.code)

        viewModel.onTitleChange("Informe corto")

        assertNull(viewModel.uiState.value.error)
    }

    private fun TestScope.viewModel(): QuickAddViewModel {
        val viewModel = QuickAddViewModel(tasks, lists, madrid) { LocalDate.of(2026, 11, 17) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private class GatedTaskRepository(
        private val delegate: FakeDestinationTaskRepository,
        private val gate: CompletableDeferred<Unit>,
    ) : TaskRepository by delegate {
        override suspend fun createTaskWithExtras(
            columnId: String?,
            creation: TaskCreation,
            listId: String?,
        ): DomainResult<Task> {
            gate.await()
            return delegate.createTaskWithExtras(columnId, creation, listId)
        }
    }
}
