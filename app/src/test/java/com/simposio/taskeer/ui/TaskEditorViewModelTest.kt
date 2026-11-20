package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.task.TASK_DESCRIPTION_TOO_LONG
import com.simposio.taskeer.ui.tasks.EditorPanel
import com.simposio.taskeer.ui.tasks.PickedDateTime
import com.simposio.taskeer.ui.tasks.PickerTarget
import com.simposio.taskeer.ui.tasks.parent
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.TaskRepeat
import com.simposio.taskeer.domain.task.RepeatPreset
import com.simposio.taskeer.domain.task.RepeatPresets
import com.simposio.taskeer.domain.task.TASK_REPEAT_INVALID
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.domain.task.newTaskWithExtras
import com.simposio.taskeer.testing.FakeDestinationBoardRepository
import com.simposio.taskeer.testing.FakeDestinationColumnRepository
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.listSummary
import com.simposio.taskeer.ui.tasks.SubtaskDraftItem
import com.simposio.taskeer.ui.tasks.TaskEditorUiState
import com.simposio.taskeer.ui.tasks.TaskEditorViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorViewModelTest {

    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z")
    private val preferences = FakePreferencesRepository()
    private val lists = FakeTaskListRepository(
        listOf(listSummary("l1", "Compras", 2), listSummary("l2", "Trabajo", 1, position = 1)),
    )
    private val myDay = FakeMarkedRepository()
    private val boards = FakeDestinationBoardRepository(MutableStateFlow(listOf(board("b1", "Proyecto"))))
    private val columns = FakeDestinationColumnRepository(
        MutableStateFlow(listOf(column("c1", "b1", "Por hacer"), column("c2", "b1", "Hecho", done = true))),
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
    fun openForEdit_conTareaInexistente_noAbreElEditor() = runTest {
        val viewModel = viewModel(FakeTaskRepository())

        viewModel.openForEdit("no-existe")

        assertFalse(viewModel.uiState.value.isOpen)
    }

    @Test
    fun openForEdit_conTareaExistente_abreElEditorConSusDatos() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))

        viewModel.openForEdit("t1")

        assertTrue(viewModel.uiState.value.isOpen)
        assertEquals("Informe", viewModel.uiState.value.title)
    }

    @Test
    fun nuevaTarea_asignadoInicialEsYo() = runTest {
        val viewModel = viewModel(FakeTaskRepository())

        viewModel.openForCreate("c1")

        assertEquals(LocalUser.ID, viewModel.uiState.value.assigneeId)
    }

    @Test
    fun abrirTareaSinAsignado_muestraYo() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))

        viewModel.openForEdit("t1")

        assertEquals(LocalUser.ID, viewModel.uiState.value.assigneeId)
    }

    @Test
    fun guardarNueva_sinTocarAsignado_persisteYo() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository)
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Nueva")

        viewModel.save()

        assertEquals(LocalUser.ID, repository.lastCreated?.assigneeId)
    }

    @Test
    fun close_marcaCierreYNoBorraEnElRepositorio() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe"))
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.close()

        assertTrue(viewModel.uiState.value.isClosing)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun save_conTituloDe201_exponeElErrorDeLongitudYNoQuedaGuardando() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("x".repeat(201))

        viewModel.save()

        assertEquals("TASK_TITLE_TOO_LONG", viewModel.uiState.value.error?.code)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isOpen)
    }

    @Test
    fun save_wipLimitReached_keepsEditorOpenWithError() = runTest {
        val repository = FakeTaskRepository()
        repository.createError = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Límite alcanzado")
        val viewModel = viewModel(repository)
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Nueva")

        viewModel.save()

        assertEquals("COLUMN_WIP_LIMIT_REACHED", viewModel.uiState.value.error?.code)
        assertTrue(viewModel.uiState.value.isOpen)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun addSubtaskDraft_anadeAlFinalYVaciaElCampo() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }

        viewModel.onNewSubtaskTitleChange("  Primera ")
        viewModel.addSubtaskDraft()
        viewModel.onNewSubtaskTitleChange("Segunda")
        viewModel.addSubtaskDraft()

        val state = viewModel.uiState.value
        assertEquals(listOf("Primera", "Segunda"), state.subtaskDrafts.map { it.title })
        assertEquals("", state.newSubtaskTitle)
        assertEquals(2, state.subtaskDrafts.map { it.id }.toSet().size)
    }

    @Test
    fun addSubtaskDraft_conTituloEnBlanco_noAnade() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }

        viewModel.onNewSubtaskTitleChange("   ")
        viewModel.addSubtaskDraft()

        assertTrue(viewModel.uiState.value.subtaskDrafts.isEmpty())
    }

    @Test
    fun editSubtaskDraft_actualizaTituloFechaYAsignado() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }
        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()
        val id = viewModel.uiState.value.subtaskDrafts.single().id
        val due = LocalDateTime.of(2026, 11, 10, 9, 30)

        viewModel.startEditSubtaskDraft(id)
        viewModel.onEditSubtaskTitleChange("Datos revisados")
        viewModel.onEditSubtaskDueAtChange(due)
        viewModel.onEditSubtaskAssigneeChange(LocalUser.ID)
        viewModel.saveSubtaskDraftEdit()

        val state = viewModel.uiState.value
        assertEquals(SubtaskDraftItem(id, "Datos revisados", due, LocalUser.ID), state.subtaskDrafts.single())
        assertNull(state.editingDraftId)
    }

    @Test
    fun removeSubtaskDraft_quitaSoloEse() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }
        listOf("A", "B", "C").forEach {
            viewModel.onNewSubtaskTitleChange(it)
            viewModel.addSubtaskDraft()
        }
        val middle = viewModel.uiState.value.subtaskDrafts[1].id

        viewModel.removeSubtaskDraft(middle)

        assertEquals(listOf("A", "C"), viewModel.uiState.value.subtaskDrafts.map { it.title })
    }

    @Test
    fun reminderTomorrow_guardaElInstanteDelAtajo() = runTest {
        preferences.setDefaultReminderTime(LocalTime.of(8, 30))
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }

        viewModel.reminderTomorrow()

        assertEquals(Instant.parse("2026-11-05T08:30:00Z"), viewModel.uiState.value.reminderDraft)
    }

    @Test
    fun reminderLaterToday_guardaAhoraMasCincoHoras() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }

        viewModel.reminderLaterToday()

        assertEquals(Instant.parse("2026-11-04T15:00:00Z"), viewModel.uiState.value.reminderDraft)
    }

    @Test
    fun reminderNextWeek_esHoyMasSieteDias() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }

        viewModel.reminderNextWeek()

        assertEquals(Instant.parse("2026-11-11T09:00:00Z"), viewModel.uiState.value.reminderDraft)
    }

    @Test
    fun reminderCustom_enElPasado_publicaReminderErrorYNoGuarda() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }

        viewModel.reminderCustom(LocalDateTime.of(2026, 11, 4, 9, 0))

        assertNull(viewModel.uiState.value.reminderDraft)
        assertEquals("La hora del recordatorio debe ser futura", viewModel.uiState.value.reminderError)
    }

    @Test
    fun clearReminderDraft_eliminaElBorrador() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }
        viewModel.reminderLaterToday()

        viewModel.clearReminderDraft()

        assertNull(viewModel.uiState.value.reminderDraft)
        assertNull(viewModel.uiState.value.reminderError)
    }

    @Test
    fun save_enCreacion_enviaTaskCreationConSubtareasDueAtUtcYRecordatorio() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository).apply { openForCreate("c1") }
        viewModel.onTitleChange("Informe")
        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()
        val id = viewModel.uiState.value.subtaskDrafts.single().id
        viewModel.startEditSubtaskDraft(id)
        viewModel.onEditSubtaskDueAtChange(LocalDateTime.of(2026, 11, 10, 9, 30))
        viewModel.onEditSubtaskAssigneeChange(LocalUser.ID)
        viewModel.saveSubtaskDraftEdit()
        viewModel.reminderLaterToday()

        viewModel.save()

        val creation = repository.lastCreation
        assertEquals("Informe", creation?.task?.title)
        assertEquals(
            listOf(
                SubtaskDraft("Datos", LocalUser.ID, Instant.parse("2026-11-10T09:30:00Z")),
            ),
            creation?.subtasks,
        )
        assertEquals(Instant.parse("2026-11-04T15:00:00Z"), creation?.remindAt)
        assertEquals(1, repository.extrasCalls)
        assertTrue(viewModel.uiState.value.isClosing)
    }

    @Test
    fun save_fallido_conservaBorradoresYError() = runTest {
        val repository = FakeTaskRepository()
        repository.createError = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Límite alcanzado")
        val viewModel = viewModel(repository).apply { openForCreate("c1") }
        viewModel.onTitleChange("Informe")
        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()
        viewModel.reminderLaterToday()

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals("COLUMN_WIP_LIMIT_REACHED", state.error?.code)
        assertFalse(state.isSaving)
        assertTrue(state.isOpen)
        assertEquals(listOf("Datos"), state.subtaskDrafts.map { it.title })
        assertEquals(Instant.parse("2026-11-04T15:00:00Z"), state.reminderDraft)
    }

    @Test
    fun dismiss_vaciaLosBorradores() = runTest {
        val viewModel = viewModel(FakeTaskRepository()).apply { openForCreate("c1") }
        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()
        viewModel.reminderLaterToday()

        viewModel.dismiss()

        assertEquals(TaskEditorUiState(), viewModel.uiState.value)
    }

    @Test
    fun openForCreate_partedeBorradoresVacios() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()
        viewModel.reminderLaterToday()

        viewModel.openForCreate("c2")

        val state = viewModel.uiState.value
        assertTrue(state.subtaskDrafts.isEmpty())
        assertNull(state.reminderDraft)
        assertEquals("c2", state.columnId)
    }

    @Test
    fun save_enEdicion_usaUpdateTaskYNuncaCreateTaskWithExtras() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe"))
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.save()

        assertEquals(1, repository.updateCalls)
        assertEquals(0, repository.extrasCalls)
        assertTrue(viewModel.uiState.value.subtaskDrafts.isEmpty())
    }

    @Test
    fun operacionesDeBorrador_nuncaEscribenEnElRepositorio() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository).apply { openForCreate("c1") }

        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()
        viewModel.reminderLaterToday()
        viewModel.clearReminderDraft()
        viewModel.dismiss()

        assertEquals(0, repository.extrasCalls)
        assertEquals(0, repository.updateCalls)
        assertEquals(0, repository.deleteCalls)
        assertNull(repository.lastCreated)
    }

    @Test
    fun editarTareaDeBandeja_llamaAUpdateTask() = runTest {
        val repository = FakeTaskRepository(task("pan", "Comprar pan").copy(columnId = null))
        val viewModel = viewModel(repository)

        viewModel.openForEdit("pan")
        viewModel.save()

        assertNull(viewModel.uiState.value.columnId)
        assertEquals(1, repository.updateCalls)
        assertEquals(0, repository.extrasCalls)
    }

    @Test
    fun crearConColumnaNula_llamaACreateTaskWithExtras() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository).apply { openForCreate(null) }
        viewModel.onTitleChange("Comprar pan")

        viewModel.save()

        assertEquals(1, repository.extrasCalls)
        assertNull(repository.lastCreated?.columnId)
        assertEquals("Comprar pan", repository.lastCreated?.title)
    }

    private val dueDate = LocalDateTime.of(2026, 12, 15, 9, 0)

    @Test
    fun repetir_seleccionarPreset_actualizaElEstado() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onDueAtChange(dueDate)

        viewModel.onRepeatPresetChange(RepeatPreset.WEEKDAYS)

        assertEquals(RepeatPresets.weekdays(15), viewModel.uiState.value.repeat)
        assertEquals(RepeatPreset.WEEKDAYS, viewModel.uiState.value.repeatPreset)
    }

    @Test
    fun repetir_personalizada_construyeLaRegla() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onDueAtChange(dueDate)

        viewModel.onRepeatPresetChange(RepeatPreset.CUSTOM)
        viewModel.onRepeatIntervalChange("3")
        viewModel.onRepeatUnitChange(RepeatUnit.WEEK)
        viewModel.onRepeatWeekdayToggle(DayOfWeek.MONDAY)
        viewModel.onRepeatWeekdayToggle(DayOfWeek.THURSDAY)
        viewModel.onRepeatWeekdayToggle(DayOfWeek.MONDAY)

        assertEquals(
            TaskRepeat(RepeatUnit.WEEK, 3, setOf(DayOfWeek.THURSDAY), 15),
            viewModel.uiState.value.repeat,
        )
        assertEquals(RepeatPreset.CUSTOM, viewModel.uiState.value.repeatPreset)
    }

    @Test
    fun guardar_conRepeticion_enviaLaReglaConAncla() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository)
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Pagar renta")
        viewModel.onDueAtChange(LocalDateTime.of(2026, 12, 31, 9, 0))
        viewModel.onRepeatPresetChange(RepeatPreset.MONTHLY)

        viewModel.save()

        assertEquals(RepeatPresets.monthly(31), repository.lastCreation?.task?.repeat)
    }

    @Test
    fun abrirParaEditar_recuperaLaRegla() = runTest {
        val rule = TaskRepeat(RepeatUnit.WEEK, 2, setOf(DayOfWeek.FRIDAY), 10)
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe", dueAt = now, repeat = rule)))

        viewModel.openForEdit("t1")

        assertEquals(rule, viewModel.uiState.value.repeat)
        assertEquals(RepeatPreset.CUSTOM, viewModel.uiState.value.repeatPreset)
        assertEquals("2", viewModel.uiState.value.repeatIntervalText)
    }

    @Test
    fun repetir_sinFecha_estaDeshabilitado() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")

        viewModel.onRepeatPresetChange(RepeatPreset.DAILY)

        assertFalse(viewModel.uiState.value.repeatEnabled)
        assertNull(viewModel.uiState.value.repeat)
    }

    @Test
    fun quitarFecha_reiniciaLaRepeticion() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onDueAtChange(dueDate)
        viewModel.onRepeatPresetChange(RepeatPreset.CUSTOM)

        viewModel.onDueAtChange(null)

        assertNull(viewModel.uiState.value.repeat)
        assertEquals(RepeatPreset.NONE, viewModel.uiState.value.repeatPreset)
    }

    @Test
    fun anclaSeCalculaDelDiaLocalDeLaFecha() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onDueAtChange(LocalDateTime.of(2027, 3, 5, 23, 30))
        viewModel.onRepeatPresetChange(RepeatPreset.MONTHLY)

        assertEquals(5, viewModel.uiState.value.repeat?.anchorDay)

        viewModel.onDueAtChange(LocalDateTime.of(2027, 3, 6, 23, 30))

        assertEquals(6, viewModel.uiState.value.repeat?.anchorDay)
    }

    @Test
    fun editarSinCambiarFecha_conservaElAncla() = runTest {
        val rule = TaskRepeat(RepeatUnit.MONTH, 1, emptySet(), 31)
        val dueAt = Instant.parse("2027-02-28T09:00:00Z")
        val repository = FakeTaskRepository(task("t1", "Renta", dueAt = dueAt, repeat = rule))
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.onRepeatPresetChange(RepeatPreset.MONTHLY)
        viewModel.onDueAtChange(LocalDateTime.of(2027, 3, 15, 9, 0))
        viewModel.onDueAtChange(LocalDateTime.of(2027, 2, 28, 18, 0))
        viewModel.save()

        assertEquals(31, repository.lastUpdateDraft?.repeat?.anchorDay)
    }

    @Test
    fun repetirConFallo_exponeError() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Tarea")
        viewModel.onDueAtChange(dueDate)
        viewModel.onRepeatPresetChange(RepeatPreset.CUSTOM)
        viewModel.onRepeatIntervalChange("0")

        viewModel.save()

        assertEquals(TASK_REPEAT_INVALID, viewModel.uiState.value.error?.code)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun isDirty_conTituloEditado_esVerdadero() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")

        viewModel.onTitleChange("Informe final")

        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun isDirty_trasRestaurarElValor_esFalso() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")

        viewModel.onTitleChange("Informe final")
        viewModel.onTitleChange("Informe ")

        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun isDirty_enAltaVacia_esFalso() = runTest {
        val viewModel = viewModel(FakeTaskRepository())

        viewModel.openForCreate("c1")

        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun isDirty_enAltaConSubtareaOMiDia_esVerdadero() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onToggleMyDay()
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.openForCreate("c1")
        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()

        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun isDirty_enTareaGuardada_ignoraMiDiaYLista() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe", columnId = null, listId = "l1"))
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        myDay.marked.value = setOf("t1")
        viewModel.onListChosen("l2")

        assertEquals("l2", viewModel.uiState.value.listId)
        assertTrue(viewModel.uiState.value.inMyDay)
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun panel_parent_deCadaPanel() {
        assertEquals(EditorPanel.DateTime(PickerTarget.TASK_DUE), EditorPanel.DateTime(PickerTarget.TASK_DUE, 5L).parent())
        assertEquals(EditorPanel.None, EditorPanel.DateTime(PickerTarget.REMINDER).parent())
        assertEquals(EditorPanel.DateTime(PickerTarget.REMINDER), EditorPanel.DateTime(PickerTarget.REMINDER, 5L).parent())
        assertEquals(EditorPanel.None, EditorPanel.DateTime(PickerTarget.TASK_DUE).parent())
        assertEquals(EditorPanel.None, EditorPanel.DateTime(PickerTarget.SUBTASK_DUE).parent())
        assertEquals(EditorPanel.None, EditorPanel.ListPicker.parent())
        assertEquals(EditorPanel.None, EditorPanel.DiscardConfirm.parent())
        assertEquals(EditorPanel.None, EditorPanel.None.parent())
    }

    @Test
    fun route_deTareaDeLista_muestraNombreDeLista() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe", columnId = null, listId = "l1")))

        viewModel.openForEdit("t1")

        val state = viewModel.uiState.value
        assertEquals("Compras", state.lists.first { it.list.id == state.listId }.list.name)
        assertFalse(state.isBoardTask)
    }

    @Test
    fun route_deTareaDeTablero_muestraTableroYColumna() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe", columnId = "c1")))

        viewModel.openForEdit("t1")

        val state = viewModel.uiState.value
        assertTrue(state.isBoardTask)
        assertEquals("Proyecto", state.boardName)
        assertEquals("Por hacer", state.columnName)
    }

    @Test
    fun onListChosen_enTareaGuardada_mueveALaLista() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe", columnId = null, listId = "l1"))
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")
        viewModel.openPanel(EditorPanel.ListPicker)

        viewModel.onListChosen("l2")

        assertEquals(listOf("t1" to "l2"), repository.moveCalls)
        assertEquals("l2", viewModel.uiState.value.listId)
        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
    }

    @Test
    fun onListChosen_conFallo_exponeElError() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe", columnId = null, listId = "l1"))
        repository.moveError = DomainError.Conflict("TASK_IN_BOARD", "La tarea está en un tablero")
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")
        viewModel.openPanel(EditorPanel.ListPicker)

        viewModel.onListChosen("l2")

        val state = viewModel.uiState.value
        assertEquals("TASK_IN_BOARD", state.error?.code)
        assertEquals("l1", state.listId)
        assertEquals(EditorPanel.ListPicker, state.panel)
    }

    @Test
    fun onListChosen_enAltaSinColumna_sePasaALaCreacion() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository)
        viewModel.openForCreate(null, "l1")
        viewModel.onTitleChange("Comprar pan")

        viewModel.onListChosen("l2")
        viewModel.save()

        assertEquals("l2", repository.lastListId)
        assertTrue(repository.moveCalls.isEmpty())
    }

    @Test
    fun onToggleCompleted_llamaASetCompleted() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe"))
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.onToggleCompleted()

        assertEquals(listOf("t1" to true), repository.completedCalls)
        assertTrue(viewModel.uiState.value.completed)

        viewModel.onToggleCompleted()

        assertEquals(listOf("t1" to true, "t1" to false), repository.completedCalls)
        assertFalse(viewModel.uiState.value.completed)
    }

    @Test
    fun onToggleCompleted_conFallo_exponeElErrorYNoCambia() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe"))
        repository.completeError = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Límite alcanzado")
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.onToggleCompleted()

        assertEquals("COLUMN_WIP_LIMIT_REACHED", viewModel.uiState.value.error?.code)
        assertFalse(viewModel.uiState.value.completed)
    }

    @Test
    fun onToggleCompleted_actualizaLaColumnaDelResultado() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe", columnId = "c1"))
        repository.completedColumnId = "c2"
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.onToggleCompleted()

        assertEquals("c2", viewModel.uiState.value.columnId)
        assertEquals("Hecho", viewModel.uiState.value.columnName)
    }

    @Test
    fun onToggleCompleted_enAltaNueva_noHaceNada() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository)
        viewModel.openForCreate("c1")

        viewModel.onToggleCompleted()

        assertTrue(repository.completedCalls.isEmpty())
        assertFalse(viewModel.uiState.value.completed)
    }

    @Test
    fun onToggleMyDay_enTareaGuardada_llamaASetMarked() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")

        viewModel.onToggleMyDay()

        assertEquals(listOf("t1" to true), myDay.setMarkedCalls)
    }

    @Test
    fun inMyDay_sigueAlRepositorio() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")
        assertFalse(viewModel.uiState.value.inMyDay)

        myDay.marked.value = setOf("t1")
        assertTrue(viewModel.uiState.value.inMyDay)

        myDay.marked.value = emptySet()
        assertFalse(viewModel.uiState.value.inMyDay)
    }

    @Test
    fun onToggleMyDay_enAltaNueva_seEnviaEnLaCreacion() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository)
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Nueva")

        viewModel.onToggleMyDay()
        viewModel.save()

        assertTrue(myDay.setMarkedCalls.isEmpty())
        assertEquals(true, repository.lastCreation?.addToMyDay)
    }

    @Test
    fun save_exitoso_marcaCierre() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")

        viewModel.save()

        assertTrue(viewModel.uiState.value.isClosing)
        assertTrue(viewModel.uiState.value.isOpen)
    }

    @Test
    fun save_enAltaConLista_pasaElListId() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = viewModel(repository)
        viewModel.openForCreate(null, "l1")
        viewModel.onTitleChange("Comprar pan")

        viewModel.save()

        assertEquals("l1", repository.lastListId)
    }

    @Test
    fun save_conNotasLargas_exponeElErrorDeLongitud() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Informe")
        viewModel.onDescriptionChange("x".repeat(5001))

        viewModel.save()

        assertEquals(TASK_DESCRIPTION_TOO_LONG, viewModel.uiState.value.error?.code)
        assertFalse(viewModel.uiState.value.isClosing)
    }

    @Test
    fun requestClose_sinCambios_cierra() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")

        viewModel.requestClose()

        assertTrue(viewModel.uiState.value.isClosing)
        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
    }

    @Test
    fun requestClose_conCambios_muestraLaConfirmacion() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")
        viewModel.onTitleChange("Otro")

        viewModel.requestClose()

        assertEquals(EditorPanel.DiscardConfirm, viewModel.uiState.value.panel)
        assertFalse(viewModel.uiState.value.isClosing)
    }

    @Test
    fun confirmDiscard_cierraYVaciaElEstado() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()
        viewModel.requestClose()

        viewModel.confirmDiscard()
        assertTrue(viewModel.uiState.value.isClosing)

        viewModel.dismiss()
        assertEquals(TaskEditorUiState(), viewModel.uiState.value)
    }

    @Test
    fun keepEditing_vuelveAlFormulario() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Informe")
        viewModel.requestClose()

        viewModel.keepEditing()

        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
        assertEquals("Informe", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.isClosing)
    }

    @Test
    fun onBack_desdePasoDeHora_vuelveAlPasoDeFecha() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.openPanel(EditorPanel.DateTime(PickerTarget.TASK_DUE))
        viewModel.onPickerDate(20_000L)

        viewModel.onBack()

        assertEquals(EditorPanel.DateTime(PickerTarget.TASK_DUE), viewModel.uiState.value.panel)
    }

    @Test
    fun onBack_desdeFechaDeRecordatorio_vuelveAlFormulario() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.openPanel(EditorPanel.DateTime(PickerTarget.REMINDER))

        viewModel.onBack()

        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
    }

    @Test
    fun onBack_desdePanel_vuelveAlFormulario() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.openPanel(EditorPanel.ListPicker)

        viewModel.onBack()

        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
        assertFalse(viewModel.uiState.value.isClosing)
    }

    @Test
    fun onBack_desdeFormularioSucio_pideConfirmar() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Informe")

        viewModel.onBack()

        assertEquals(EditorPanel.DiscardConfirm, viewModel.uiState.value.panel)
    }

    @Test
    fun onBack_desdeConfirmacion_vuelveAlFormulario() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("Informe")
        viewModel.onBack()

        viewModel.onBack()

        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
        assertEquals("Informe", viewModel.uiState.value.title)
    }

    @Test
    fun pickerDate_pasaAHora_yAlAceptarFijaLaFecha() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        val day = LocalDate.of(2026, 12, 15)

        viewModel.openPanel(EditorPanel.DateTime(PickerTarget.TASK_DUE))
        viewModel.onPickerDate(day.toEpochDay())
        assertEquals(EditorPanel.DateTime(PickerTarget.TASK_DUE, day.toEpochDay()), viewModel.uiState.value.panel)

        val delegated = viewModel.onPickerTime(LocalTime.of(9, 30))

        assertNull(delegated)
        assertEquals(LocalDateTime.of(2026, 12, 15, 9, 30), viewModel.uiState.value.dueAtLocal)
        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
    }

    @Test
    fun pickerDate_alQuitar_limpiaFechaYRepeticion() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onDueAtChange(dueDate)
        viewModel.onRepeatPresetChange(RepeatPreset.MONTHLY)
        viewModel.openPanel(EditorPanel.DateTime(PickerTarget.TASK_DUE))

        viewModel.onClearDue()

        assertNull(viewModel.uiState.value.dueAtLocal)
        assertNull(viewModel.uiState.value.repeat)
        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
    }

    @Test
    fun pickerDeSubtarea_aceptaYVuelveAlFormulario() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onNewSubtaskTitleChange("Datos")
        viewModel.addSubtaskDraft()
        val id = viewModel.uiState.value.subtaskDrafts.single().id
        viewModel.startEditSubtaskDraft(id)
        viewModel.openPanel(EditorPanel.DateTime(PickerTarget.SUBTASK_DUE))
        viewModel.onPickerDate(LocalDate.of(2026, 12, 15).toEpochDay())

        viewModel.onPickerTime(LocalTime.of(8, 0))

        val state = viewModel.uiState.value
        assertEquals(LocalDateTime.of(2026, 12, 15, 8, 0), state.editingDraftDueAtLocal)
        assertEquals(id, state.editingDraftId)
        assertEquals(EditorPanel.None, state.panel)
    }

    @Test
    fun pickerDeSubtarea_enTareaGuardada_devuelveElValorParaElHost() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")
        viewModel.openPanel(EditorPanel.DateTime(PickerTarget.SUBTASK_DUE))
        viewModel.onPickerDate(LocalDate.of(2026, 12, 15).toEpochDay())

        val delegated = viewModel.onPickerTime(LocalTime.of(8, 0))

        assertEquals(
            PickedDateTime(PickerTarget.SUBTASK_DUE, LocalDateTime.of(2026, 12, 15, 8, 0)),
            delegated,
        )
        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
    }

    @Test
    fun recordatorioPersonalizado_desdeElMenuFijaElBorrador() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")

        viewModel.openPanel(EditorPanel.DateTime(PickerTarget.REMINDER))
        viewModel.onPickerDate(LocalDate.of(2026, 11, 10).toEpochDay())
        viewModel.onPickerTime(LocalTime.of(9, 0))

        assertEquals(Instant.parse("2026-11-10T09:00:00Z"), viewModel.uiState.value.reminderDraft)
        assertEquals(EditorPanel.None, viewModel.uiState.value.panel)
    }

    @Test
    fun dismiss_cancelaLaObservacionDelContexto() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))
        viewModel.openForEdit("t1")

        viewModel.dismiss()
        myDay.marked.value = setOf("t1")

        assertEquals(TaskEditorUiState(), viewModel.uiState.value)
    }

    private fun viewModel(repository: TaskRepository) =
        TaskEditorViewModel(
            repository,
            DateTimeConverter { ZoneOffset.UTC },
            preferences,
            { ZoneOffset.UTC },
            lists,
            myDay,
            boards,
            columns,
        ) { now }

    private fun task(
        id: String,
        title: String,
        dueAt: Instant? = null,
        repeat: TaskRepeat? = null,
        columnId: String? = "c1",
        listId: String? = null,
    ) = Task(
        columnId = columnId,
        listId = listId,
        title = title,
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        completedAt = null,
        repeat = repeat,
        sync = SyncMetadata(clientId = id, updatedAt = Instant.EPOCH, pending = false, deletedAt = null),
    )

    private class FakeTaskRepository(vararg tasks: Task) : TaskRepository {
        override fun observeList(listId: String): Flow<List<Task>> = emptyFlow()

        override fun observeActiveCountsByColumn(): Flow<Map<String, Int>> = emptyFlow()

        override suspend fun assignToColumn(id: String, columnId: String): DomainResult<Task> =
            error("no se usa en este test")

        private val stored = tasks.associateBy { it.id }
        var deleteGate: CompletableDeferred<Unit>? = null
        var deleteError: DomainError? = null
        var deleteCalls = 0
        var createError: DomainError? = null
        var lastCreated: Task? = null
        var lastCreation: TaskCreation? = null
        var extrasCalls = 0
        var updateCalls = 0
        var lastListId: String? = null
        var completeError: DomainError? = null
        var completedColumnId: String? = null
        val completedCalls = mutableListOf<Pair<String, Boolean>>()
        var moveError: DomainError? = null
        val moveCalls = mutableListOf<Pair<String, String>>()

        override fun observeTasks(boardId: String): Flow<List<Task>> = emptyFlow()

        override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> = emptyFlow()

        override suspend fun reminderCandidates(): List<ReminderCandidate> = emptyList()

        override suspend fun activeTaskCount(): Int = 0

        override suspend fun getTask(id: String): DomainResult<Task> =
            stored[id]?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(DomainError.NotFound("TASK_NOT_FOUND", "No existe"))

        override suspend fun createTask(columnId: String?, draft: TaskDraft, listId: String?): DomainResult<Task> =
            createError?.let { DomainResult.Failure(it) }
                ?: newTask(columnId, draft, position = 0, now = Instant.EPOCH, listId = listId)
                    .also { result -> lastCreated = (result as? DomainResult.Success)?.value }

        override suspend fun createTaskWithExtras(
            columnId: String?,
            creation: TaskCreation,
            listId: String?,
        ): DomainResult<Task> {
            extrasCalls++
            lastCreation = creation
            lastListId = listId
            return createError?.let { DomainResult.Failure(it) }
                ?: newTaskWithExtras(columnId, creation, position = 0, now = Instant.EPOCH, listId = listId)
                    .let { result ->
                        when (result) {
                            is DomainResult.Failure -> result
                            is DomainResult.Success -> DomainResult.Success(result.value.task)
                                .also { lastCreated = it.value }
                        }
                    }
        }

        var lastUpdateDraft: TaskDraft? = null

        override suspend fun moveToList(id: String, listId: String): DomainResult<Task> {
            moveCalls += id to listId
            moveError?.let { return DomainResult.Failure(it) }
            return stored[id]?.let { DomainResult.Success(it.copy(columnId = null, listId = listId)) }
                ?: DomainResult.Failure(DomainError.NotFound("TASK_NOT_FOUND", "No existe"))
        }

        override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> {
            updateCalls++
            lastUpdateDraft = draft
            return stored[id]?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(DomainError.NotFound("TASK_NOT_FOUND", "No existe"))
        }

        override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> =
            error("no se usa")

        override suspend fun setCompleted(id: String, completed: Boolean): DomainResult<Task> {
            completedCalls += id to completed
            completeError?.let { return DomainResult.Failure(it) }
            val task = stored[id]
                ?: return DomainResult.Failure(DomainError.NotFound("TASK_NOT_FOUND", "No existe"))
            return DomainResult.Success(
                task.copy(
                    completedAt = Instant.EPOCH.takeIf { completed },
                    columnId = completedColumnId ?: task.columnId,
                ),
            )
        }

        override fun observeCompletedCount(): Flow<Int> = error("no se usa")

        override suspend fun deleteCompleted(): DomainResult<List<String>> = error("no se usa")

        override suspend fun restoreTasks(ids: List<String>): DomainResult<Unit> = error("no se usa")

        override suspend fun deleteTask(id: String): DomainResult<Unit> {
            deleteCalls++
            deleteGate?.await()
            return deleteError?.let { DomainResult.Failure(it) } ?: DomainResult.Success(Unit)
        }
    }

    private class FakeMarkedRepository : MyDayRepository {
        val marked = MutableStateFlow<Set<String>>(emptySet())
        val setMarkedCalls = mutableListOf<Pair<String, Boolean>>()
        var setMarkedError: DomainError? = null

        override fun observeMyDay(): Flow<MyDay> = emptyFlow()

        override fun observeMarkedTaskIds(): Flow<Set<String>> = marked

        override fun observeOverdueCount(): Flow<Int> = emptyFlow()

        override fun observeSuggestions(): Flow<List<MyDayTask>> = emptyFlow()

        override fun observeSuggestionCount(): Flow<Int> = emptyFlow()

        override suspend fun dismissSuggestion(taskId: String): DomainResult<Unit> = DomainResult.Success(Unit)

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
            setMarkedCalls += taskId to marked
            setMarkedError?.let { return DomainResult.Failure(it) }
            this.marked.value = if (marked) this.marked.value + taskId else this.marked.value - taskId
            return DomainResult.Success(Unit)
        }
    }

    private fun sync(id: String) = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null)

    private fun board(id: String, name: String) = Board(name, Instant.EPOCH, sync(id))

    private fun column(id: String, boardId: String, name: String, done: Boolean = false) =
        Column(boardId, name, 0, wipLimit = null, isDoneColumn = done, createdAt = Instant.EPOCH, sync = sync(id))
}
