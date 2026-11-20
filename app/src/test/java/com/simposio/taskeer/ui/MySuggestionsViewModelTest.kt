package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.ui.myday.MySuggestionsViewModel
import com.simposio.taskeer.ui.myday.SUGGESTIONS_LOAD_TIMEOUT
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class MySuggestionsViewModelTest {

    private val now: Instant = Instant.parse("2026-11-20T10:00:00Z")
    private val today: LocalDate = LocalDate.of(2026, 11, 20)
    private val utc = TimeZoneProvider { ZoneOffset.UTC }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun insignia_cuentaLoEmitidoYMarcaCargada() = runTest {
        listOf(0, 5, 99, 100, 250).forEach { count ->
            val repository = FakeSuggestionsRepository().apply { counts.tryEmit(count) }
            val viewModel = viewModel(repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.badge.collect {} }

            assertEquals(count, viewModel.badge.value.count)
            assertTrue(viewModel.badge.value.loaded)
        }
    }

    @Test
    fun insignia_antesDeLaPrimeraEmision_noEstaCargada() = runTest {
        val repository = FakeSuggestionsRepository()
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.badge.collect {} }

        assertFalse(viewModel.badge.value.loaded)
    }

    @Test
    fun anadir_marca() = runTest {
        val repository = FakeSuggestionsRepository()
        val viewModel = viewModel(repository)

        viewModel.addToMyDay("t1")

        assertEquals(listOf("t1" to true), repository.marked)
    }

    @Test
    fun descartar_llamaADismissSuggestion() = runTest {
        val repository = FakeSuggestionsRepository()
        val viewModel = viewModel(repository)

        viewModel.dismiss("t1")

        assertEquals(listOf("t1"), repository.dismissed)
    }

    @Test
    fun fallo_exponeError() = runTest {
        val repository = FakeSuggestionsRepository().apply { failure = DomainError.NotFound("TASK_NOT_FOUND", "Tarea no encontrada") }
        val viewModel = viewModel(repository)

        viewModel.dismiss("t1")

        assertNotNull(viewModel.error.value)
    }

    @Test
    fun clearError_limpiaElErrorPrevio() = runTest {
        val repository = FakeSuggestionsRepository().apply { failure = DomainError.NotFound("TASK_NOT_FOUND", "Tarea no encontrada") }
        val viewModel = viewModel(repository)
        viewModel.dismiss("t1")

        viewModel.clearError()

        assertNull(viewModel.error.value)
    }

    @Test
    fun items_exponenLaFilaConFechaVencida() = runTest {
        val repository = FakeSuggestionsRepository().apply {
            list.value = listOf(suggestion("t1", dueAt = now.minusSeconds(3_600)))
        }
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.items.collect {} }

        assertEquals(listOf("t1"), viewModel.items.value.map { it.id })
        assertTrue(viewModel.items.value.single().isOverdue)
    }

    @Test
    fun primerPrimerPlanoDelDia_consumeElDiaYDevuelveVerdadero() = runTest {
        val preferences = FakePreferencesRepository(lastShownDay = today.minusDays(1))
        val viewModel = viewModel(FakeSuggestionsRepository(), preferences)

        assertTrue(viewModel.claimFirstForegroundOfDay())
        assertEquals(today, preferencesDay(preferences))
    }

    @Test
    fun segundoPrimerPlanoDelMismoDia_noReclama() = runTest {
        val preferences = FakePreferencesRepository(lastShownDay = null)
        val viewModel = viewModel(FakeSuggestionsRepository(), preferences)

        assertTrue(viewModel.claimFirstForegroundOfDay())
        assertFalse(viewModel.claimFirstForegroundOfDay())
    }

    @Test
    fun primerPrimerPlanoDelDia_consumeElDiaAunqueNoSeAbra() = runTest {
        val preferences = FakePreferencesRepository(lastShownDay = null)
        val viewModel = viewModel(FakeSuggestionsRepository().apply { counts.tryEmit(0) }, preferences)

        viewModel.claimFirstForegroundOfDay()
        viewModel.awaitFirstCount()

        assertEquals(today, preferencesDay(preferences))
        assertFalse(viewModel.claimFirstForegroundOfDay())
    }

    @Test
    fun relojQueRetrocede_noReclama() = runTest {
        val preferences = FakePreferencesRepository(lastShownDay = today.plusDays(1))
        val viewModel = viewModel(FakeSuggestionsRepository(), preferences)

        assertFalse(viewModel.claimFirstForegroundOfDay())
        assertEquals(today.plusDays(1), preferencesDay(preferences))
    }

    @Test
    fun fallaLaEscritura_noReclamaElDia() = runTest {
        val preferences = FakePreferencesRepository(lastShownDay = null).apply { failOnSetLastShownDay = true }
        val viewModel = viewModel(FakeSuggestionsRepository(), preferences)

        assertFalse(viewModel.claimFirstForegroundOfDay())
        assertNull(preferencesDay(preferences))
    }

    @Test
    fun esperaDelConteo_sinEmisionTrasElTope_cuentaCero() = runTest {
        val repository = FakeSuggestionsRepository()
        val viewModel = viewModel(repository)
        var result: Int? = null

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { result = viewModel.awaitFirstCount() }
        advanceTimeBy(SUGGESTIONS_LOAD_TIMEOUT + 1)

        assertEquals(0, result)
    }

    @Test
    fun esperaDelConteo_conEmisionAntesDelTope_devuelveElConteo() = runTest {
        val repository = FakeSuggestionsRepository()
        val viewModel = viewModel(repository)
        var result: Int? = null

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { result = viewModel.awaitFirstCount() }
        advanceTimeBy(SUGGESTIONS_LOAD_TIMEOUT / 2)
        repository.counts.tryEmit(7)
        advanceTimeBy(1)

        assertEquals(7, result)
    }

    private fun viewModel(
        repository: MyDayRepository,
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
    ) = MySuggestionsViewModel(repository, preferences, { now }, utc, DateTimeConverter(utc))

    private suspend fun preferencesDay(preferences: FakePreferencesRepository): LocalDate? =
        preferences.suggestionsLastShownDay.first()

    private fun suggestion(id: String, dueAt: Instant? = null): MyDayTask = MyDayTask(
        task = Task(
            columnId = "c1",
            title = "Tarea $id",
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            assigneeId = null,
            position = 0,
            createdAt = Instant.EPOCH,
            completedAt = null,
            sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
        ),
        boardId = "b1",
        boardName = "Tablero",
        addedAt = null,
    )

    private class FakeSuggestionsRepository : MyDayRepository {
        val counts = MutableSharedFlow<Int>(replay = 1)
        val list = MutableStateFlow<List<MyDayTask>>(emptyList())
        val marked: MutableList<Pair<String, Boolean>> = mutableListOf()
        val dismissed: MutableList<String> = mutableListOf()
        var failure: DomainError? = null

        override fun observeMyDay(): Flow<MyDay> = emptyFlow()

        override fun observeSuggestions(): Flow<List<MyDayTask>> = list

        override fun observeSuggestionCount(): Flow<Int> = counts

        override fun observeMarkedTaskIds(): Flow<Set<String>> = emptyFlow()

        override fun observeOverdueCount(): Flow<Int> = emptyFlow()

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
            this.marked += taskId to marked
            return result()
        }

        override suspend fun dismissSuggestion(taskId: String): DomainResult<Unit> {
            dismissed += taskId
            return result()
        }

        private fun result(): DomainResult<Unit> =
            failure?.let { DomainResult.Failure(it) } ?: DomainResult.Success(Unit)
    }
}
