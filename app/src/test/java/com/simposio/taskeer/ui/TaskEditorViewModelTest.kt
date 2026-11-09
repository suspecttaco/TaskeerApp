package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.TaskRepository
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
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.domain.task.newTaskWithExtras
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.ui.tasks.SubtaskDraftItem
import com.simposio.taskeer.ui.tasks.TaskEditorUiState
import com.simposio.taskeer.ui.tasks.TaskEditorViewModel
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
    fun delete_conBorradoEnCurso_noLanzaUnSegundoBorrado() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe"))
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.delete()
        viewModel.delete()
        gate.complete(Unit)

        assertEquals(1, repository.deleteCalls)
        assertFalse(viewModel.uiState.value.isOpen)
    }

    @Test
    fun delete_conFallo_muestraElErrorYPermiteReintentar() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe"))
        repository.deleteError = DomainError.Unknown("UNKNOWN", "Fallo")
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.delete()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
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
        assertFalse(viewModel.uiState.value.isOpen)
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

    private fun viewModel(repository: TaskRepository) =
        TaskEditorViewModel(
            repository,
            DateTimeConverter { ZoneOffset.UTC },
            preferences,
            { ZoneOffset.UTC },
        ) { now }

    private fun task(id: String, title: String) = Task(
        columnId = "c1",
        title = title,
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = null,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata(clientId = id, updatedAt = Instant.EPOCH, pending = false, deletedAt = null),
    )

    private class FakeTaskRepository(vararg tasks: Task) : TaskRepository {
        override fun observeInbox(): Flow<List<Task>> = emptyFlow()

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

        override fun observeTasks(boardId: String): Flow<List<Task>> = emptyFlow()

        override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> = emptyFlow()

        override suspend fun reminderCandidates(): List<ReminderCandidate> = emptyList()

        override suspend fun activeTaskCount(): Int = 0

        override suspend fun getTask(id: String): DomainResult<Task> =
            stored[id]?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(DomainError.NotFound("TASK_NOT_FOUND", "No existe"))

        override suspend fun createTask(columnId: String?, draft: TaskDraft): DomainResult<Task> =
            createError?.let { DomainResult.Failure(it) }
                ?: newTask(columnId, draft, position = 0, now = Instant.EPOCH)
                    .also { result -> lastCreated = (result as? DomainResult.Success)?.value }

        override suspend fun createTaskWithExtras(
            columnId: String?,
            creation: TaskCreation,
        ): DomainResult<Task> {
            extrasCalls++
            lastCreation = creation
            return createError?.let { DomainResult.Failure(it) }
                ?: newTaskWithExtras(columnId, creation, position = 0, now = Instant.EPOCH)
                    .let { result ->
                        when (result) {
                            is DomainResult.Failure -> result
                            is DomainResult.Success -> DomainResult.Success(result.value.task)
                                .also { lastCreated = it.value }
                        }
                    }
        }

        override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> {
            updateCalls++
            return stored[id]?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(DomainError.NotFound("TASK_NOT_FOUND", "No existe"))
        }

        override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> =
            error("no se usa")

        override suspend fun deleteTask(id: String): DomainResult<Unit> {
            deleteCalls++
            deleteGate?.await()
            return deleteError?.let { DomainResult.Failure(it) } ?: DomainResult.Success(Unit)
        }
    }
}
