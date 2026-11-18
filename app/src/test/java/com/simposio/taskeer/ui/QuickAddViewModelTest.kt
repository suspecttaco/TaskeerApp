package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.testing.FakeDestinationBoardRepository
import com.simposio.taskeer.testing.FakeDestinationColumnRepository
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.testBoard
import com.simposio.taskeer.testing.testColumn
import com.simposio.taskeer.ui.quickadd.BackResult
import com.simposio.taskeer.ui.quickadd.QuickAddContext
import com.simposio.taskeer.ui.quickadd.QuickAddDatePreset
import com.simposio.taskeer.ui.quickadd.QuickAddDestination
import com.simposio.taskeer.ui.quickadd.QuickAddPanel
import com.simposio.taskeer.ui.quickadd.QuickAddViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

    private val boards = FakeDestinationBoardRepository()
    private val columns = FakeDestinationColumnRepository()
    private val tasks = FakeDestinationTaskRepository()
    private val madrid = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        boards.boards.value = listOf(testBoard("b1"))
        columns.columns.value = listOf(
            testColumn("c1", "b1", position = 0, wipLimit = 1),
            testColumn("c2", "b1", position = 1),
            testColumn("hecho", "b1", position = 2, isDone = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun estadoInicial_cerradoDestinoBandejaYTituloVacio() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.isOpen)
        assertEquals(QuickAddDestination.Inbox, viewModel.uiState.value.destination)
        assertEquals("", viewModel.uiState.value.title)
        assertNull(viewModel.uiState.value.destinationLabel)
        assertNull(viewModel.uiState.value.panel)
    }

    @Test
    fun abrir_marcaElBottomSheetAbierto() = runTest {
        val viewModel = viewModel()

        viewModel.open(QuickAddContext.MyDay)

        assertTrue(viewModel.uiState.value.isOpen)
    }

    @Test
    fun cerrar_conservaElTitulo() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.onTitleChange("Comprar pan")

        viewModel.close()

        assertFalse(viewModel.uiState.value.isOpen)
        assertEquals("Comprar pan", viewModel.uiState.value.title)
    }

    @Test
    fun abrirPanel_cierraElAnterior() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.togglePanel(QuickAddPanel.DESTINATION)

        viewModel.togglePanel(QuickAddPanel.PRIORITY)

        assertEquals(QuickAddPanel.PRIORITY, viewModel.uiState.value.panel)
    }

    @Test
    fun abrirElMismoPanel_loPliega() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.togglePanel(QuickAddPanel.DATE)

        viewModel.togglePanel(QuickAddPanel.DATE)

        assertNull(viewModel.uiState.value.panel)
    }

    @Test
    fun atras_plieguaElPanelAntesDeCerrar() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.togglePanel(QuickAddPanel.TIME)

        val result = viewModel.onBack()

        assertEquals(BackResult.COLLAPSE_PANEL, result)
        assertNull(viewModel.uiState.value.panel)
        assertTrue(viewModel.uiState.value.isOpen)
    }

    @Test
    fun atras_sinPanel_cierra() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)

        val result = viewModel.onBack()

        assertEquals(BackResult.CLOSE, result)
        assertFalse(viewModel.uiState.value.isOpen)
    }

    @Test
    fun add_conTituloEnBlanco_noLlamaAlRepositorio() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.onTitleChange("   ")

        viewModel.add()

        assertTrue(tasks.creations.isEmpty())
    }

    @Test
    fun contextoMiDia_marcaEnMiDia() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.onTitleChange("Comprar pan")

        viewModel.add()

        val (columnId, creation) = tasks.creations.single()
        assertNull(columnId)
        assertEquals("Comprar pan", creation.task.title)
        assertTrue(creation.addToMyDay)
    }

    @Test
    fun contextoTareas_noMarcaEnMiDia() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Inbox)
        viewModel.onTitleChange("Comprar pan")

        viewModel.add()

        val (columnId, creation) = tasks.creations.single()
        assertNull(columnId)
        assertFalse(creation.addToMyDay)
    }

    @Test
    fun contextoTablero_eligePrimeraColumnaNoDeCierre() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Board("b1"))
        viewModel.onTitleChange("Informe")

        assertEquals(QuickAddDestination.InColumn("c1"), viewModel.uiState.value.destination)
        viewModel.add()

        val (columnId, creation) = tasks.creations.single()
        assertEquals("c1", columnId)
        assertFalse(creation.addToMyDay)
    }

    @Test
    fun contextoTablero_sinColumnas_caeEnBandeja() = runTest {
        columns.columns.value = emptyList()
        val viewModel = viewModel()

        viewModel.open(QuickAddContext.Board("b1"))

        assertEquals(QuickAddDestination.Inbox, viewModel.uiState.value.destination)
    }

    @Test
    fun destinoElegido_noLoPisaElContextoAlReabrir() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Board("b1"))
        viewModel.selectColumn("c2")
        viewModel.close()

        viewModel.open(QuickAddContext.Board("b1"))

        assertEquals(QuickAddDestination.InColumn("c2"), viewModel.uiState.value.destination)
    }

    @Test
    fun add_enColumnaElegida_creaEnEsaColumna() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.selectColumn("c1")
        viewModel.onTitleChange("Informe")

        viewModel.add()

        val (columnId, creation) = tasks.creations.single()
        assertEquals("c1", columnId)
        assertTrue(creation.addToMyDay)
    }

    @Test
    fun exito_cierraYVacia() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.selectColumn("c1")
        viewModel.selectPriority(TaskPriority.URGENT)
        viewModel.selectPreset(QuickAddDatePreset.TODAY)
        viewModel.onTitleChange("Informe")

        viewModel.add()

        val state = viewModel.uiState.value
        assertFalse(state.isOpen)
        assertEquals("", state.title)
        assertEquals(QuickAddDestination.Inbox, state.destination)
        assertEquals(TaskPriority.MEDIUM, state.priority)
        assertNull(state.dueDate)
        assertNull(state.error)
        assertFalse(state.isSaving)
    }

    @Test
    fun fallo_conservaTodoYPublicaError() = runTest {
        val error = DomainError.Validation("TASK_TITLE_TOO_LONG", "Demasiado largo")
        tasks.createFailure = error
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.selectColumn("c1")
        viewModel.selectPriority(TaskPriority.HIGH)
        viewModel.selectPreset(QuickAddDatePreset.TOMORROW)
        viewModel.onTitleChange("Informe")

        viewModel.add()

        val state = viewModel.uiState.value
        assertEquals(error, state.error)
        assertTrue(state.isOpen)
        assertEquals("Informe", state.title)
        assertEquals(QuickAddDestination.InColumn("c1"), state.destination)
        assertEquals(TaskPriority.HIGH, state.priority)
        assertEquals(LocalDate.of(2026, 11, 18), state.dueDate)
    }

    @Test
    fun columnaLlena_publicaColumnWipLimitReached() = runTest {
        tasks.createFailure = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Columna llena")
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.selectColumn("c1")
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals("COLUMN_WIP_LIMIT_REACHED", viewModel.uiState.value.error?.code)
    }

    @Test
    fun editarElTitulo_limpiaElError() = runTest {
        tasks.createFailure = DomainError.Validation("TASK_TITLE_TOO_LONG", "Demasiado largo")
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.onTitleChange("Informe")
        viewModel.add()
        assertEquals("TASK_TITLE_TOO_LONG", viewModel.uiState.value.error?.code)

        viewModel.onTitleChange("Informe corto")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun cambiarElDestino_limpiaElError() = runTest {
        tasks.createFailure = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Columna llena")
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.MyDay)
        viewModel.selectColumn("c1")
        viewModel.onTitleChange("Informe")
        viewModel.add()
        assertEquals("COLUMN_WIP_LIMIT_REACHED", viewModel.uiState.value.error?.code)

        viewModel.selectColumn("c2")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun destinoQueDesaparece_vuelveABandeja() = runTest {
        val viewModel = viewModel()
        viewModel.selectColumn("c1")

        columns.columns.value = emptyList()

        assertEquals(QuickAddDestination.Inbox, viewModel.uiState.value.destination)
        assertNull(viewModel.uiState.value.destinationLabel)
    }

    @Test
    fun etiquetaDeDestino_incluyeTableroYColumna() = runTest {
        val viewModel = viewModel()

        viewModel.selectColumn("c1")

        assertEquals("Tablero b1 › Columna c1", viewModel.uiState.value.destinationLabel)
    }

    @Test
    fun selector_noOfreceColumnasDeCierre() = runTest {
        val viewModel = viewModel()

        val offered = viewModel.uiState.value.destinations.single().columns.map { it.column.id }

        assertEquals(listOf("c1", "c2"), offered)
    }

    @Test
    fun elegirDestino_plieguaElPanel() = runTest {
        val viewModel = viewModel()
        viewModel.togglePanel(QuickAddPanel.DESTINATION)

        viewModel.selectInbox()

        assertNull(viewModel.uiState.value.panel)
    }

    @Test
    fun prioridadPorDefecto_esMedia() = runTest {
        assertEquals(TaskPriority.MEDIUM, viewModel().uiState.value.priority)
    }

    @Test
    fun prioridadElegida_seEnviaEnElDraft() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Inbox)
        viewModel.selectPriority(TaskPriority.URGENT)
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals(TaskPriority.URGENT, tasks.creations.single().second.task.priority)
    }

    @Test
    fun fechaHoy_creaConDueAtDeHoyA09() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Inbox)
        viewModel.selectPreset(QuickAddDatePreset.TODAY)
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals(Instant.parse("2026-11-17T08:00:00Z"), tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun fechaMañana_creaConDueAtDeMañana() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Inbox)
        viewModel.selectPreset(QuickAddDatePreset.TOMORROW)
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals(Instant.parse("2026-11-18T08:00:00Z"), tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun proximaSemana_creaConDueAtDeHoyMasSieteDias() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Inbox)
        viewModel.selectPreset(QuickAddDatePreset.NEXT_WEEK)
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals(Instant.parse("2026-11-24T08:00:00Z"), tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun sinFecha_creaSinDueAt() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Inbox)
        viewModel.selectPreset(QuickAddDatePreset.TODAY)
        viewModel.selectPreset(QuickAddDatePreset.NONE)
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertNull(tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun horaElegida_sustituyeA09() = runTest {
        val viewModel = viewModel()
        viewModel.open(QuickAddContext.Inbox)
        viewModel.selectDate(LocalDate.of(2026, 12, 1))
        viewModel.selectTime(LocalTime.of(17, 45))
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals(Instant.parse("2026-12-01T16:45:00Z"), tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun quitarFecha_olvidaLaHora() = runTest {
        val viewModel = viewModel()
        viewModel.selectDate(LocalDate.of(2026, 12, 1))
        viewModel.selectTime(LocalTime.of(17, 45))

        viewModel.selectDate(null)

        assertNull(viewModel.uiState.value.dueDate)
        assertNull(viewModel.uiState.value.dueTime)
    }

    private fun TestScope.viewModel(): QuickAddViewModel {
        val viewModel = QuickAddViewModel(tasks, boards, columns, madrid) { LocalDate.of(2026, 11, 17) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }
}
