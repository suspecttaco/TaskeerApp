package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.inbox.InboxViewModel
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")
    private val madrid = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })
    private val tasks = FakeDestinationTaskRepository()
    private val myDay = FakeMarkedRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun items_ordenaPorCreacionDescendente() = runTest {
        tasks.inbox.value = listOf(task("nueva"), task("vieja"))

        val viewModel = viewModel()

        assertEquals(listOf("nueva", "vieja"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun sinTareas_estadoVacio() = runTest {
        assertTrue(viewModel().uiState.value.items.isEmpty())
    }

    @Test
    fun estrella_reflejaLasMarcadasDeMiDia() = runTest {
        tasks.inbox.value = listOf(task("a"), task("b"))
        val viewModel = viewModel()

        myDay.marked.value = setOf("b")

        assertEquals(listOf(false, true), viewModel.uiState.value.items.map { it.isMarked })
    }

    @Test
    fun toggleMarked_delegaEnMyDayRepository() = runTest {
        tasks.inbox.value = listOf(task("a"))
        val viewModel = viewModel()

        viewModel.toggleMarked("a")

        assertEquals(listOf("a" to true), myDay.calls)
    }

    @Test
    fun fechaVencida_seMarcaVencida() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = now.minusSeconds(60)), task("b", dueAt = now.plusSeconds(60)))

        val items = viewModel().uiState.value.items

        assertTrue(items[0].isOverdue)
        assertFalse(items[1].isOverdue)
    }

    private fun task(id: String, dueAt: Instant? = null) = Task(
        columnId = null,
        title = "Tarea $id",
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
    )

    private fun TestScope.viewModel(): InboxViewModel {
        val viewModel = InboxViewModel(tasks, myDay, madrid) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private class FakeMarkedRepository : MyDayRepository {
        val marked = MutableStateFlow<Set<String>>(emptySet())
        val calls = mutableListOf<Pair<String, Boolean>>()

        override fun observeMyDay(): Flow<MyDay> = emptyFlow()

        override fun observeMarkedTaskIds(): Flow<Set<String>> = marked

        override fun observeOverdueCount(): Flow<Int> = emptyFlow()

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
            calls += taskId to marked
            return DomainResult.Success(Unit)
        }
    }
}
