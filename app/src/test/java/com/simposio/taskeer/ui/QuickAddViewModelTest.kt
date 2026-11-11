package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.testing.FakeDestinationBoardRepository
import com.simposio.taskeer.testing.FakeDestinationColumnRepository
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.testBoard
import com.simposio.taskeer.testing.testColumn
import com.simposio.taskeer.ui.myday.QuickAddDestination
import com.simposio.taskeer.ui.myday.QuickAddViewModel
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        boards.boards.value = listOf(testBoard("b1"))
        columns.columns.value = listOf(
            testColumn("c1", "b1", position = 0, wipLimit = 1),
            testColumn("hecho", "b1", position = 1, isDone = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun estadoInicial_destinoBandejaYTituloVacio() = runTest {
        val viewModel = viewModel()

        assertEquals(QuickAddDestination.Inbox, viewModel.uiState.value.destination)
        assertEquals("", viewModel.uiState.value.title)
        assertNull(viewModel.uiState.value.destinationLabel)
    }

    @Test
    fun add_conTituloEnBlanco_noLlamaAlRepositorio() = runTest {
        val viewModel = viewModel()
        viewModel.onTitleChange("   ")

        viewModel.add()

        assertTrue(tasks.creations.isEmpty())
    }

    @Test
    fun add_enBandeja_creaConColumnaNulaYAddToMyDay() = runTest {
        val viewModel = viewModel()
        viewModel.onTitleChange("Comprar pan")

        viewModel.add()

        val (columnId, creation) = tasks.creations.single()
        assertNull(columnId)
        assertEquals("Comprar pan", creation.task.title)
        assertTrue(creation.addToMyDay)
    }

    @Test
    fun add_enColumna_creaEnEsaColumnaConAddToMyDay() = runTest {
        val viewModel = viewModel()
        viewModel.selectColumn("c1")
        viewModel.onTitleChange("Informe")

        viewModel.add()

        val (columnId, creation) = tasks.creations.single()
        assertEquals("c1", columnId)
        assertTrue(creation.addToMyDay)
    }

    @Test
    fun exito_vaciaElTituloYConservaElDestino() = runTest {
        val viewModel = viewModel()
        viewModel.selectColumn("c1")
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals("", viewModel.uiState.value.title)
        assertEquals(QuickAddDestination.InColumn("c1"), viewModel.uiState.value.destination)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun fallo_conservaTituloYDestinoYPublicaError() = runTest {
        val error = DomainError.Validation("TASK_TITLE_TOO_LONG", "Demasiado largo")
        tasks.createFailure = error
        val viewModel = viewModel()
        viewModel.selectColumn("c1")
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals(error, viewModel.uiState.value.error)
        assertEquals("Informe", viewModel.uiState.value.title)
        assertEquals(QuickAddDestination.InColumn("c1"), viewModel.uiState.value.destination)
    }

    @Test
    fun columnaLlena_publicaColumnWipLimitReached() = runTest {
        tasks.createFailure = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Columna llena")
        val viewModel = viewModel()
        viewModel.selectColumn("c1")
        viewModel.onTitleChange("Informe")

        viewModel.add()

        assertEquals("COLUMN_WIP_LIMIT_REACHED", viewModel.uiState.value.error?.code)
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

        assertEquals(listOf("c1"), offered)
    }

    @Test
    fun selectorAbiertoSeCierraAlElegirDestino() = runTest {
        val viewModel = viewModel()
        viewModel.openPicker()
        assertTrue(viewModel.uiState.value.isPickerOpen)

        viewModel.selectInbox()

        assertFalse(viewModel.uiState.value.isPickerOpen)
    }

    private fun TestScope.viewModel(): QuickAddViewModel {
        val viewModel = QuickAddViewModel(tasks, boards, columns)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }
}
