package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.MyDayDao
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.MyDayRow
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.date.dayWindowAt
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.PlannedView
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlannedRepositoryImplTest {

    private val zone = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-11-17T10:00:00Z")
    private val windows = MutableStateFlow(dayWindowAt(now, zone))
    private val dao = FakeDatedDao()
    private val repository = PlannedRepositoryImpl(dao, { zone }, { windows.asStateFlow() })

    @Test
    fun observePlanned_agrupaLasTareasConFecha() = runTest {
        dao.add("ayer", "2026-11-16T09:00:00Z")
        dao.add("hoy", "2026-11-17T18:00:00Z")
        dao.add("manana", "2026-11-18T09:00:00Z")
        dao.add("lejos", "2026-11-25T09:00:00Z")

        val view = repository.observePlanned().first()

        assertEquals(listOf("ayer"), view.overdue.ids())
        assertEquals(listOf("hoy"), view.today.ids())
        assertEquals(listOf("manana"), view.tomorrow.ids())
        assertEquals(listOf("lejos"), view.upcoming.single().items.ids())
        assertEquals("Tablero b1", view.today.single().boardName)
    }

    @Test
    fun observePlanned_excluyeCompletadasBorradasYSinFecha() = runTest {
        dao.add("ok", "2026-11-17T18:00:00Z")
        dao.add("completada", "2026-11-17T18:00:00Z", completedAt = now.toEpochMilli())
        dao.add("borrada", "2026-11-17T18:00:00Z", deletedAt = 1L)
        dao.add("sin-fecha", null)
        dao.add("columna-borrada", "2026-11-17T18:00:00Z", columnId = "c-borrada")
        dao.add("otro", "2026-11-17T18:00:00Z", assigneeId = "otro-usuario")

        assertEquals(listOf("ok"), repository.observePlanned().first().today.ids())
    }

    @Test
    fun observePlanned_incluyeBandeja() = runTest {
        dao.add("pan", "2026-11-18T09:00:00Z", columnId = null)

        val item = repository.observePlanned().first().tomorrow.single()

        assertEquals("pan", item.task.id)
        assertNull(item.boardName)
    }

    @Test
    fun observePlanned_alCambiarElDia_reagrupa() = runTest {
        dao.add("t1", "2026-11-18T09:00:00Z")
        val seen = mutableListOf<PlannedView>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observePlanned().toList(seen)
        }
        assertEquals(listOf("t1"), seen.last().tomorrow.ids())

        windows.value = dayWindowAt(Instant.parse("2026-11-18T10:00:00Z"), zone)
        collector.cancel()

        assertEquals(listOf("t1"), seen.last().today.ids())
        assertTrue(seen.last().tomorrow.isEmpty())
    }

    @Test
    fun observePlanned_sucesoraFutura_apareceEnProximos() = runTest {
        dao.add("original", "2026-11-17T09:00:00Z", completedAt = now.toEpochMilli())
        dao.add("sucesora", "2026-11-24T09:00:00Z")

        val view = repository.observePlanned().first()

        assertTrue(view.today.isEmpty())
        assertEquals(listOf("sucesora"), view.upcoming.single().items.ids())
    }

    private fun List<PlannedItem>.ids(): List<String> = map { it.task.id }

    private class FakeDatedDao : MyDayDao() {
        private val tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
        private val deletedColumns = setOf("c-borrada")

        fun add(
            id: String,
            dueAt: String?,
            columnId: String? = "c1",
            assigneeId: String? = LocalUser.ID,
            deletedAt: Long? = null,
            completedAt: Long? = null,
        ) {
            tasks.value = tasks.value + TaskEntity(
                id = id,
                columnId = columnId,
                title = "Tarea $id",
                description = null,
                priority = "MEDIUM",
                dueAt = dueAt?.let { Instant.parse(it).toEpochMilli() },
                assigneeId = assigneeId,
                position = 0,
                createdAt = 0L,
                updatedAt = 0L,
                pending = false,
                completedAt = completedAt,
                deletedAt = deletedAt,
            )
        }

        override fun observeDated(assigneeId: String): Flow<List<MyDayRow>> = tasks.map { all ->
            all.filter {
                it.assigneeId == assigneeId && it.dueAt != null && it.completedAt == null &&
                    it.deletedAt == null && it.columnId !in deletedColumns
            }.map {
                MyDayRow(it, boardId = it.columnId?.let { "b1" }, boardName = it.columnId?.let { "Tablero b1" }, addedAt = null)
            }
        }

        override fun observeMarkedIds(dayStart: Long): Flow<List<String>> = error("no se usa")
        override suspend fun findEntry(taskId: String): MyDayEntryEntity? = error("no se usa")
        override suspend fun upsert(entry: MyDayEntryEntity) = error("no se usa")
        override fun observePinned(dayStart: Long): Flow<List<MyDayRow>> = error("no se usa")
        override fun observeAssigned(assigneeId: String, dayStart: Long, dayEnd: Long): Flow<List<MyDayRow>> =
            error("no se usa")
        override fun observeSuggestions(dayStart: Long): Flow<List<MyDayRow>> = error("no se usa")
        override fun observeOverdueCount(assigneeId: String, now: Long): Flow<Int> = error("no se usa")
    }
}
