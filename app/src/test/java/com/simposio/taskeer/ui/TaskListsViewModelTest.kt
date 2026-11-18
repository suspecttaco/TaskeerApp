package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.listSummary
import com.simposio.taskeer.ui.lists.ListDialog
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_exponeListasConContador() = runTest {
        val state = viewModel().uiState.value

        assertEquals(listOf("Tareas", "Compras"), state.lists.map { it.list.name })
        assertEquals(listOf(3, 2), state.lists.map { it.activeCount })
        assertEquals(ListDialog.None, state.dialog)
    }

    @Test
    fun openCreate_abreElDialogo() = runTest {
        val viewModel = viewModel()

        viewModel.openCreate()

        assertEquals(ListDialog.Create, viewModel.uiState.value.dialog)
    }

    @Test
    fun submitName_crea_cierraYNoNavega() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()

        viewModel.submitName("  Viajes ")

        assertEquals(listOf("Tareas", "Compras", "Viajes"), viewModel.uiState.value.lists.map { it.list.name })
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun submitName_nombreInvalido_muestraError() = runTest {
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
    fun submitName_renombra_actualizaElNombre() = runTest {
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
    fun confirmDelete_llamaADeleteList() = runTest {
        val viewModel = viewModel()
        viewModel.openDelete("compras")

        viewModel.confirmDelete()

        assertEquals(listOf("compras"), repository.deleted)
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun confirmDelete_conFallo_exponeError() = runTest {
        repository.deleteFailure = DomainError.Conflict("LIST_LAST_PROTECTED", "No se puede eliminar la última lista")
        val viewModel = viewModel()
        viewModel.openDelete("compras")

        viewModel.confirmDelete()

        assertTrue(viewModel.uiState.value.dialog is ListDialog.ConfirmDelete)
        assertEquals("LIST_LAST_PROTECTED", viewModel.uiState.value.error?.code)
    }

    @Test
    fun submitName_renombra_laListaPorDefecto() = runTest {
        val viewModel = viewModel()

        viewModel.openRename(SEEDED_TASK_LIST_ID)
        viewModel.submitName("Pendientes")

        assertEquals("Pendientes", repository.lists.value.single { it.list.id == SEEDED_TASK_LIST_ID }.list.name)
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun openDelete_listaPorDefecto_conOtrasListas_pideConfirmacion() = runTest {
        val viewModel = viewModel()

        viewModel.openDelete(SEEDED_TASK_LIST_ID)

        assertEquals(
            ListDialog.ConfirmDelete(SEEDED_TASK_LIST_ID, "Tareas", taskCount = 3, targetName = "Compras"),
            viewModel.uiState.value.dialog,
        )
    }

    @Test
    fun openDelete_soloUnaLista_noAbreElDialogo() = runTest {
        repository.lists.value = repository.lists.value.take(1)
        val viewModel = viewModel()

        viewModel.openDelete(SEEDED_TASK_LIST_ID)
        assertEquals(ListDialog.None, viewModel.uiState.value.dialog)
    }

    private fun TestScope.viewModel(): TaskListsViewModel {
        val viewModel = TaskListsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }
}
