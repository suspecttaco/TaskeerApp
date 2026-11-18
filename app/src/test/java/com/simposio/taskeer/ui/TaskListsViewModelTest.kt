package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.listSummary
import com.simposio.taskeer.ui.lists.ListDialog
import com.simposio.taskeer.ui.lists.ListSelection
import com.simposio.taskeer.ui.lists.TaskListsViewModel
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
class TaskListsViewModelTest {

    private val repository = FakeTaskListRepository(
        listOf(
            listSummary(SEEDED_TASK_LIST_ID, "Tareas", activeCount = 3),
            listSummary("compras", "Compras", activeCount = 2, position = 1),
        ),
    )
    private val selection = ListSelection()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun estadoInicial_seleccionaLaListaPorDefecto() = runTest {
        val state = viewModel().uiState.value

        assertEquals(SEEDED_TASK_LIST_ID, state.selectedListId)
        assertEquals(listOf("Tareas", "Compras"), state.lists.map { it.list.name })
        assertEquals(3, state.selected?.activeCount)
        assertEquals(ListDialog.None, state.dialog)
    }

    @Test
    fun abrirDialogo_creaConNombreValido_cierraYSeleccionaLaNueva() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()
        assertEquals(ListDialog.Create, viewModel.uiState.value.dialog)

        viewModel.submitName("  Viajes ")

        val created = repository.lists.value.single { it.list.name == "Viajes" }.list
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
        assertEquals(created.id, viewModel.uiState.value.selectedListId)
        assertEquals(created.id, selection.selectedListId.value)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun crear_nombreInvalido_mantieneElDialogoConError() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()

        viewModel.submitName("   ")

        assertEquals(ListDialog.Create, viewModel.uiState.value.dialog)
        assertEquals("LIST_NAME_REQUIRED", viewModel.uiState.value.error?.code)
        assertEquals(2, repository.lists.value.size)
    }

    @Test
    fun crear_nombreDuplicado_muestraError() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()

        viewModel.submitName("compras")

        assertEquals(ListDialog.Create, viewModel.uiState.value.dialog)
        assertEquals("LIST_NAME_TAKEN", viewModel.uiState.value.error?.code)
        assertEquals(SEEDED_TASK_LIST_ID, viewModel.uiState.value.selectedListId)
    }

    @Test
    fun errorDeCrear_seLimpiaAlReabrirElDialogo() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()
        viewModel.submitName("")

        viewModel.dismiss()
        viewModel.openCreate()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun listaSeleccionadaBorrada_vuelveALaPorDefectoResultante() = runTest {
        val viewModel = viewModel()
        viewModel.select("compras")
        assertEquals("compras", viewModel.uiState.value.selectedListId)

        repository.lists.value = repository.lists.value.filter { it.list.id != "compras" }

        assertEquals(SEEDED_TASK_LIST_ID, viewModel.uiState.value.selectedListId)
    }

    @Test
    fun renombrar_valida_actualizaElNombre() = runTest {
        val viewModel = viewModel()
        viewModel.openRename("compras")
        assertEquals(ListDialog.Rename("compras", "Compras"), viewModel.uiState.value.dialog)

        viewModel.submitName("Super")

        assertEquals("Super", repository.lists.value.single { it.list.id == "compras" }.list.name)
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun eliminar_pideConfirmacionConElNumeroDeTareasYElNombreDeLaDestino() = runTest {
        val viewModel = viewModel()

        viewModel.openDelete("compras")

        assertEquals(
            ListDialog.ConfirmDelete("compras", "Compras", taskCount = 2, targetName = "Tareas"),
            viewModel.uiState.value.dialog,
        )
        assertTrue("sin confirmar no borra", repository.deleted.isEmpty())
    }

    @Test
    fun eliminar_confirmado_llamaADeleteList() = runTest {
        val viewModel = viewModel()
        viewModel.select("compras")
        viewModel.openDelete("compras")

        viewModel.confirmDelete()

        assertEquals(listOf("compras"), repository.deleted)
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
        assertEquals(SEEDED_TASK_LIST_ID, viewModel.uiState.value.selectedListId)
    }

    @Test
    fun borrarUltimaLista_conFallo_exponeError() = runTest {
        repository.deleteFailure = DomainError.Conflict("LIST_LAST_PROTECTED", "No se puede eliminar la última lista")
        val viewModel = viewModel()
        viewModel.openDelete("compras")

        viewModel.confirmDelete()

        assertTrue(viewModel.uiState.value.dialog is ListDialog.ConfirmDelete)
        assertEquals("LIST_LAST_PROTECTED", viewModel.uiState.value.error?.code)
    }

    @Test
    fun renombrarTareas_esPosible() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.canRenameSelected)

        viewModel.openRename(SEEDED_TASK_LIST_ID)
        viewModel.submitName("Pendientes")

        assertEquals("Pendientes", repository.lists.value.single { it.list.id == SEEDED_TASK_LIST_ID }.list.name)
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun eliminarTareas_conOtrasListas_seOfrece() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.canDeleteSelected)
        viewModel.openDelete(SEEDED_TASK_LIST_ID)

        assertEquals(
            ListDialog.ConfirmDelete(SEEDED_TASK_LIST_ID, "Tareas", taskCount = 3, targetName = "Compras"),
            viewModel.uiState.value.dialog,
        )
    }

    @Test
    fun soloUnaLista_noOfreceEliminar() = runTest {
        repository.lists.value = repository.lists.value.take(1)
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.canDeleteSelected)
        assertTrue(viewModel.uiState.value.canRenameSelected)
        viewModel.openDelete(SEEDED_TASK_LIST_ID)
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
    }

    private fun TestScope.viewModel(): TaskListsViewModel {
        val viewModel = TaskListsViewModel(repository, selection)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }
}
