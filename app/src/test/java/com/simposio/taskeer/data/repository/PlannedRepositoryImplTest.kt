package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.MyDayDao
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.MyDayRow
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.date.dayWindowAt
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.UpcomingDay
import java.time.Instant
import java.time.LocalDate
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
    fun observeUpcoming_agrupaEnSieteDias() = runTest {
        dao.add("ayer", "2026-11-16T09:00:00Z")
        dao.add("hoy", "2026-11-17T18:00:00Z")
        dao.add("manana", "2026-11-18T09:00:00Z")
        dao.add("lejos", "2026-11-25T09:00:00Z")

        val days = repository.observeUpcoming().first()

        assertEquals(7, days.size)
        assertEquals(listOf("ayer", "hoy"), days[0].items.ids())
        assertEquals(listOf("manana"), days[1].items.ids())
        assertTrue(days.drop(2).all { it.items.isEmpty() })
        assertEquals("Tablero b1", days[0].items.last().boardName)
    }

    @Test
    fun observeUpcoming_cambiaAlCruzarLaMedianoche() = runTest {
        dao.add("t1", "2026-11-18T09:00:00Z")
        val seen = mutableListOf<List<UpcomingDay>>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeUpcoming().toList(seen)
        }
        assertEquals(listOf("t1"), seen.last()[1].items.ids())

        windows.value = dayWindowAt(Instant.parse("2026-11-18T10:00:00Z"), zone)
        collector.cancel()

        assertEquals(listOf("t1"), seen.last()[0].items.ids())
        assertEquals(LocalDate.of(2026, 11, 18), seen.last()[0].date)
    }

    @Test
    fun observeAllTasks_incluyeSinFecha() = runTest {
        dao.add("sin-fecha", null)
        dao.add("pan", "2026-11-18T09:00:00Z", columnId = null)

        val view = repository.observeAllTasks().first()

        assertEquals(listOf("sin-fecha"), view.someday.ids())
        assertNull(view.tomorrow.single().boardName)
    }

    @Test
    fun observeAllTasks_excluyeCompletadas() = runTest {
        dao.add("ok", "2026-11-17T18:00:00Z")
        dao.add("completada", "2026-11-17T18:00:00Z", completedAt = now.toEpochMilli())
        dao.add("borrada", "2026-11-17T18:00:00Z", deletedAt = 1L)
        dao.add("otro", "2026-11-17T18:00:00Z", assigneeId = "otro-usuario")

        assertEquals(listOf("ok"), repository.observeAllTasks().first().today.ids())
    }

    @Test
    fun observeAllTasks_incluyeTareasDeListasYTableros() = runTest {
        dao.add("tablero", "2026-11-17T18:00:00Z", columnId = "c1")
        dao.add("lista", "2026-11-17T19:00:00Z", columnId = null)

        assertEquals(listOf("tablero", "lista"), repository.observeAllTasks().first().today.ids())
    }

    @Test
    fun observeAllTasks_excluyeTareasDeTablerosBorrados() = runTest {
        dao.add("ok", "2026-11-17T18:00:00Z")
        dao.add("columna-borrada", "2026-11-17T18:00:00Z", columnId = "c-borrada")

        assertEquals(listOf("ok"), repository.observeAllTasks().first().today.ids())
    }

    @Test
    fun observeDatedTasks_incluyeSoloLasAbiertasConFecha() = runTest {
        dao.add("con-fecha", "2026-11-17T18:00:00Z")
        dao.add("sin-fecha", null)
        dao.add("completada", "2026-11-17T18:00:00Z", completedAt = now.toEpochMilli())
        dao.add("borrada", "2026-11-17T18:00:00Z", deletedAt = 1L)
        dao.add("lejana", "2027-03-01T08:00:00Z")

        val due = repository.observeDatedTasks().first()

        assertEquals(listOf("con-fecha", "lejana"), due.map { it.taskId })
        assertEquals(Instant.parse("2026-11-17T18:00:00Z"), due.first().dueAt)
    }

    @Test
    fun observeDatedTasks_usaElNombreDelTableroComoLugar() = runTest {
        dao.add("tablero", "2026-11-17T18:00:00Z", columnId = "c1")
        dao.add("lista", "2026-11-17T19:00:00Z", columnId = null)

        val due = repository.observeDatedTasks().first().associate { it.taskId to it.place }

        assertEquals("Tablero b1", due["tablero"])
        assertNull(due["lista"])
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

        override fun observeOpen(assigneeId: String): Flow<List<MyDayRow>> = tasks.map { all ->
            all.filter {
                it.assigneeId == assigneeId && it.completedAt == null &&
                    it.deletedAt == null && it.columnId !in deletedColumns
            }.map {
                MyDayRow(it, boardId = it.columnId?.let { "b1" }, boardName = it.columnId?.let { "Tablero b1" }, addedAt = null)
            }
        }

        override fun observeMarkedIds(dayStart: Long): Flow<List<String>> = error("no se usa")
        override suspend fun findEntry(taskId: String): MyDayEntryEntity? = error("no se usa")
        override suspend fun upsert(entry: MyDayEntryEntity) = error("no se usa")
        override fun observePinned(dayStart: Long): Flow<List<MyDayRow>> = error("no se usa")
        override fun observeSuggestions(assigneeId: String, dayStart: Long, dayEnd: Long): Flow<List<MyDayRow>> =
            error("no se usa")
        override fun observeSuggestionCount(assigneeId: String, dayStart: Long): Flow<Int> = error("no se usa")
        override fun observeOverdueCount(assigneeId: String, now: Long): Flow<Int> = error("no se usa")
    }
}
