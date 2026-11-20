package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.DayLabel
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.listSummary
import com.simposio.taskeer.domain.task.QuickDateChoice
import com.simposio.taskeer.domain.task.QuickDateShortcut
import com.simposio.taskeer.ui.quickadd.QuickAddContext
import com.simposio.taskeer.ui.quickadd.QuickAddListOption
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
        assertEquals(listOf<String?>(SEEDED_TASK_LIST_ID), tasks.creationListIds)
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
        val viewModel = QuickAddViewModel(gated, lists, madrid, clock = { NOW })
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
        assertEquals(listOf<String?>(SEEDED_TASK_LIST_ID), tasks.creationListIds)
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

        assertEquals(listOf<String?>(SEEDED_TASK_LIST_ID), tasks.creationListIds)
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

    @Test
    fun add_mananaPorLaManana_guardaMananaALas0900EnUtc() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.selectShortcut(QuickDateShortcut.TOMORROW_MORNING)

        viewModel.add()

        assertEquals(Instant.parse("2026-11-18T08:00:00Z"), tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun add_proximaSemana_guardaHoyMasSiete() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)

        viewModel.add()

        assertEquals(Instant.parse("2026-11-24T08:00:00Z"), tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun add_algunDia_guardaSinFecha() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.selectShortcut(QuickDateShortcut.SOMEDAY)

        viewModel.add()

        assertNull(tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun add_atajoSeResuelveAlEnviar_noAlTocar() = runTest {
        var now = NOW
        val viewModel = viewModel(clock = { now })
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.selectShortcut(QuickDateShortcut.TOMORROW_MORNING)
        now = Instant.parse("2026-11-18T12:00:00Z")

        viewModel.add()

        assertEquals(Instant.parse("2026-11-19T08:00:00Z"), tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun add_enOtraZonaHoraria_usaSuMedianoche() = runTest {
        val auckland = DateTimeConverter(TimeZoneProvider { ZoneId.of("Pacific/Auckland") })
        val viewModel = viewModel(converter = auckland)
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.selectShortcut(QuickDateShortcut.TOMORROW_MORNING)

        viewModel.add()

        assertEquals(Instant.parse("2026-11-18T20:00:00Z"), tasks.creations.single().second.task.dueAt)
    }

    @Test
    fun selectShortcut_fijaLaEleccion() = runTest {
        val viewModel = viewModel()
        viewModel.open()

        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)

        assertEquals(QuickDateChoice.Shortcut(QuickDateShortcut.NEXT_WEEK), viewModel.uiState.value.dateChoice)
    }

    @Test
    fun selectShortcut_otroAtajoSustituye() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)

        viewModel.selectShortcut(QuickDateShortcut.SOMEDAY)

        assertEquals(QuickDateChoice.Shortcut(QuickDateShortcut.SOMEDAY), viewModel.uiState.value.dateChoice)
    }

    @Test
    fun selectShortcut_elMismoAtajoDeselecciona() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectShortcut(QuickDateShortcut.SOMEDAY)

        viewModel.selectShortcut(QuickDateShortcut.SOMEDAY)

        assertEquals(QuickDateChoice.None, viewModel.uiState.value.dateChoice)
    }

    @Test
    fun selectShortcut_sustituyeLaFechaDelMas() = runTest {
        val viewModel = viewModel()
        viewModel.openWithDate(LocalDate.of(2026, 12, 1))

        viewModel.selectShortcut(QuickDateShortcut.TOMORROW_MORNING)

        assertEquals(LocalDate.of(2026, 11, 18), viewModel.uiState.value.dueDate)
    }

    @Test
    fun clearDate_vuelveASinFecha() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)

        viewModel.clearDate()

        assertEquals(QuickDateChoice.None, viewModel.uiState.value.dateChoice)
        assertNull(viewModel.uiState.value.dueDate)
    }

    @Test
    fun uiState_dueLabel_atajoMananaEsTomorrow() = runTest {
        val viewModel = viewModel()
        viewModel.open()

        viewModel.selectShortcut(QuickDateShortcut.TOMORROW_MORNING)

        assertEquals(DayLabel.Tomorrow, viewModel.uiState.value.dueLabel)
    }

    @Test
    fun uiState_dueLabel_algunDia_noTieneEtiquetaDeDia() = runTest {
        val viewModel = viewModel()
        viewModel.open()

        viewModel.selectShortcut(QuickDateShortcut.SOMEDAY)

        assertNull(viewModel.uiState.value.dueLabel)
        assertNull(viewModel.uiState.value.dueDate)
    }

    @Test
    fun openDatePicker_exponeElDialogo() = runTest {
        val viewModel = viewModel()
        viewModel.open()

        viewModel.openDatePicker()

        assertTrue(viewModel.uiState.value.isDatePickerOpen)
    }

    @Test
    fun pickDate_fijaPickedYCierraElDialogo() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.openDatePicker()

        viewModel.pickDate(LocalDate.of(2026, 12, 25))

        val state = viewModel.uiState.value
        assertEquals(QuickDateChoice.Picked(LocalDate.of(2026, 12, 25)), state.dateChoice)
        assertFalse(state.isDatePickerOpen)
    }

    @Test
    fun dismissDatePicker_noCambiaLaEleccion() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)
        viewModel.openDatePicker()

        viewModel.dismissDatePicker()

        val state = viewModel.uiState.value
        assertEquals(QuickDateChoice.Shortcut(QuickDateShortcut.NEXT_WEEK), state.dateChoice)
        assertFalse(state.isDatePickerOpen)
    }

    @Test
    fun pickDate_conservaElTitulo() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.openDatePicker()

        viewModel.pickDate(LocalDate.of(2026, 12, 25))

        assertEquals("Informe", viewModel.uiState.value.title)
        assertTrue(viewModel.uiState.value.isOpen)
    }

    @Test
    fun uiState_pickedOtraFecha_exponeLaFecha() = runTest {
        val viewModel = viewModel()
        viewModel.open()

        viewModel.pickDate(LocalDate.of(2026, 11, 10))

        assertEquals(LocalDate.of(2026, 11, 10), viewModel.uiState.value.dueDate)
        assertEquals(DayLabel.Weekday(LocalDate.of(2026, 11, 10)), viewModel.uiState.value.dueLabel)
    }

    @Test
    fun add_conAtajo_noCreaRecordatorio() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.selectShortcut(QuickDateShortcut.TOMORROW_MORNING)

        viewModel.add()

        assertNull(tasks.creations.single().second.remindAt)
    }

    @Test
    fun add_conAtajo_noFijaRepeticionNiAsignado() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")
        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)

        viewModel.add()

        val task = tasks.creations.single().second.task
        assertNull(task.assigneeId)
        assertEquals(TaskDraft(title = task.title, priority = TaskPriority.MEDIUM, dueAt = task.dueAt), task)
    }

    @Test
    fun add_creaConPrioridadMediaSinAsignadoNiRepeticion() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Informe")

        viewModel.add()

        val creation = tasks.creations.single().second
        assertEquals(TaskDraft(title = "Informe", priority = TaskPriority.MEDIUM), creation.task)
        assertNull(creation.remindAt)
    }

    @Test
    fun toggleListPanel_abreYCierra() = runTest {
        val viewModel = viewModel()
        viewModel.open()

        viewModel.toggleListPanel()
        assertTrue(viewModel.uiState.value.isListPanelOpen)
        viewModel.toggleListPanel()

        assertFalse(viewModel.uiState.value.isListPanelOpen)
    }

    @Test
    fun uiState_expone_nombreDeLaListaSeleccionada() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        assertEquals("Tareas", viewModel.uiState.value.selectedListName)

        viewModel.selectList("compras")

        assertEquals("Compras", viewModel.uiState.value.selectedListName)
    }

    @Test
    fun uiState_listOptions_unaFilaPorListaConSuConteo() = runTest {
        val viewModel = viewModel()

        assertEquals(
            listOf(
                QuickAddListOption(SEEDED_TASK_LIST_ID, "Tareas", 3, selected = true),
                QuickAddListOption("compras", "Compras", 2, selected = false),
            ),
            viewModel.uiState.value.listOptions,
        )
    }

    @Test
    fun uiState_listOptions_respetaElOrdenDeLasListas() = runTest {
        val viewModel = viewModel()

        lists.lists.value = listOf(
            listSummary("compras", "Compras", 2, position = 0),
            listSummary(SEEDED_TASK_LIST_ID, "Tareas", 3, position = 1),
        )

        assertEquals(listOf("compras", SEEDED_TASK_LIST_ID), viewModel.uiState.value.listOptions.map { it.id })
    }

    @Test
    fun uiState_listOptions_conteoSeActualiza() = runTest {
        val viewModel = viewModel()

        lists.lists.value = listOf(
            listSummary(SEEDED_TASK_LIST_ID, "Tareas", 4),
            listSummary("compras", "Compras", 2, position = 1),
        )

        assertEquals(4, viewModel.uiState.value.listOptions.first().taskCount)
    }

    @Test
    fun uiState_listOptions_marcaSoloLaEfectiva() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectList("compras")

        assertEquals(listOf("compras"), viewModel.uiState.value.listOptions.filter { it.selected }.map { it.id })
    }

    @Test
    fun uiState_alAbrir_laListaPorDefectoVieneSeleccionada() = runTest {
        val viewModel = viewModel()
        viewModel.setContext(QuickAddContext.MyDay)

        viewModel.open()

        assertEquals(SEEDED_TASK_LIST_ID, viewModel.uiState.value.listOptions.single { it.selected }.id)
    }

    @Test
    fun uiState_enListaAbierta_vienePreseleccionadaEsaLista() = runTest {
        val viewModel = viewModel()
        viewModel.setContext(QuickAddContext.InList("compras"))

        viewModel.open()

        assertEquals("compras", viewModel.uiState.value.listOptions.single { it.selected }.id)
    }

    @Test
    fun selectList_cambiaLaEfectivaYCierraElPanel() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.toggleListPanel()

        viewModel.selectList("compras")

        val state = viewModel.uiState.value
        assertEquals("compras", state.listOptions.single { it.selected }.id)
        assertFalse(state.isListPanelOpen)
    }

    @Test
    fun add_conListaElegida_creaEnEsaLista() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectList("compras")
        viewModel.onTitleChange("Leche")

        viewModel.add()

        assertEquals(listOf<String?>("compras"), tasks.creationListIds)
    }

    @Test
    fun add_sinElegir_creaEnLaPorDefectoConIdExplicito() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Leche")

        viewModel.add()

        assertEquals(listOf<String?>(SEEDED_TASK_LIST_ID), tasks.creationListIds)
    }

    @Test
    fun add_enMiDiaConOtraLista_marcaMiDiaYUsaLaLista() = runTest {
        val viewModel = viewModel()
        viewModel.setContext(QuickAddContext.MyDay)
        viewModel.open()
        viewModel.selectList("compras")
        viewModel.onTitleChange("Leche")

        viewModel.add()

        assertTrue(tasks.creations.single().second.addToMyDay)
        assertEquals(listOf<String?>("compras"), tasks.creationListIds)
    }

    @Test
    fun add_listaElegidaBorrada_caeALaDelContexto() = runTest {
        val viewModel = viewModel()
        viewModel.setContext(QuickAddContext.InList(SEEDED_TASK_LIST_ID))
        viewModel.open()
        viewModel.selectList("compras")
        lists.lists.value = lists.lists.value.filter { it.list.id != "compras" }
        viewModel.onTitleChange("Leche")

        viewModel.add()

        assertEquals(listOf<String?>(SEEDED_TASK_LIST_ID), tasks.creationListIds)
    }

    @Test
    fun add_listaDelContextoBorrada_caeALaPorDefecto() = runTest {
        val viewModel = viewModel()
        viewModel.setContext(QuickAddContext.InList("compras"))
        viewModel.open()
        lists.lists.value = lists.lists.value.filter { it.list.id != "compras" }
        viewModel.onTitleChange("Leche")

        viewModel.add()

        assertEquals(listOf<String?>(SEEDED_TASK_LIST_ID), tasks.creationListIds)
    }

    @Test
    fun add_sinListasCargadas_usaListIdNulo() = runTest {
        val viewModel = viewModel(listRepository = FakeTaskListRepository(emptyList()))
        viewModel.open()
        viewModel.onTitleChange("Leche")

        viewModel.add()

        assertEquals(listOf<String?>(null), tasks.creationListIds)
    }

    @Test
    fun selectList_listaInexistente_seIgnora() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.toggleListPanel()

        viewModel.selectList("fantasma")

        val state = viewModel.uiState.value
        assertEquals(SEEDED_TASK_LIST_ID, state.listOptions.single { it.selected }.id)
        assertTrue(state.isListPanelOpen)
    }

    @Test
    fun add_exito_conservaFechaYListaElegidas() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)
        viewModel.selectList("compras")
        viewModel.onTitleChange("Leche")

        viewModel.add()

        val state = viewModel.uiState.value
        assertEquals(QuickDateChoice.Shortcut(QuickDateShortcut.NEXT_WEEK), state.dateChoice)
        assertEquals("compras", state.listOptions.single { it.selected }.id)
    }

    @Test
    fun close_olvidaFechaYListaElegidas() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)
        viewModel.selectList("compras")

        viewModel.close()
        viewModel.open()

        val state = viewModel.uiState.value
        assertEquals(QuickDateChoice.None, state.dateChoice)
        assertEquals(SEEDED_TASK_LIST_ID, state.listOptions.single { it.selected }.id)
    }

    @Test
    fun open_empiezaSinFechaNiListaElegida() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectShortcut(QuickDateShortcut.SOMEDAY)
        viewModel.selectList("compras")

        viewModel.open()

        val state = viewModel.uiState.value
        assertEquals(QuickDateChoice.None, state.dateChoice)
        assertEquals(SEEDED_TASK_LIST_ID, state.listOptions.single { it.selected }.id)
    }

    @Test
    fun openWithDate_fijaPickedYNoListaElegida() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectList("compras")

        viewModel.openWithDate(LocalDate.of(2026, 12, 1))

        val state = viewModel.uiState.value
        assertEquals(QuickDateChoice.Picked(LocalDate.of(2026, 12, 1)), state.dateChoice)
        assertEquals(SEEDED_TASK_LIST_ID, state.listOptions.single { it.selected }.id)
    }

    @Test
    fun add_falla_conservaFechaYListaElegidas() = runTest {
        tasks.createFailure = DomainError.Validation("TASK_TITLE_TOO_LONG", "Demasiado largo")
        val viewModel = viewModel()
        viewModel.open()
        viewModel.selectShortcut(QuickDateShortcut.NEXT_WEEK)
        viewModel.selectList("compras")
        viewModel.onTitleChange("Leche")

        viewModel.add()

        val state = viewModel.uiState.value
        assertEquals(QuickDateChoice.Shortcut(QuickDateShortcut.NEXT_WEEK), state.dateChoice)
        assertEquals("compras", state.listOptions.single { it.selected }.id)
        assertEquals("Leche", state.title)
        assertEquals("TASK_TITLE_TOO_LONG", state.error?.code)
    }

    @Test
    fun close_descartaElTituloLaFechaYLaLista() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Borrador")
        viewModel.selectShortcut(QuickDateShortcut.TOMORROW_MORNING)
        viewModel.selectList("compras")

        viewModel.close()

        val state = viewModel.uiState.value
        assertFalse(state.isOpen)
        assertEquals("", state.title)
        assertEquals(QuickDateChoice.None, state.dateChoice)
        assertEquals(SEEDED_TASK_LIST_ID, state.listOptions.single { it.selected }.id)
    }

    @Test
    fun close_cierraPanelYSelectorDeFecha() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.toggleListPanel()
        viewModel.openDatePicker()

        viewModel.close()

        val state = viewModel.uiState.value
        assertFalse(state.isListPanelOpen)
        assertFalse(state.isDatePickerOpen)
    }

    @Test
    fun onBack_conPanelAbierto_cierraSoloElPanel() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onTitleChange("Leche")
        viewModel.toggleListPanel()

        viewModel.onBack()

        val state = viewModel.uiState.value
        assertFalse(state.isListPanelOpen)
        assertTrue(state.isOpen)
        assertEquals("Leche", state.title)
    }

    @Test
    fun onBack_sinPanel_cierraLaPildora() = runTest {
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onBack()

        assertFalse(viewModel.uiState.value.isOpen)
    }

    @Test
    fun onBack_conSelectorDeFecha_noCierraLaPildora() = runTest {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.openDatePicker()

        viewModel.onBack()

        assertTrue(viewModel.uiState.value.isOpen)
        assertFalse(viewModel.uiState.value.isDatePickerOpen)
    }

    private fun TestScope.viewModel(
        clock: () -> Instant = { NOW },
        converter: DateTimeConverter = madrid,
        listRepository: FakeTaskListRepository = lists,
    ): QuickAddViewModel {
        val viewModel = QuickAddViewModel(tasks, listRepository, converter, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-17T12:00:00Z")
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
