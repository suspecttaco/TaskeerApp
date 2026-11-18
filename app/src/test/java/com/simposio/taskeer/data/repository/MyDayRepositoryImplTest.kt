package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnCountRow
import com.simposio.taskeer.data.local.ListCountRow
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.MyDayDao
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.MyDayRow
import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.date.dayWindowAt
import com.simposio.taskeer.testing.UnusedReminderDao
import com.simposio.taskeer.testing.UnusedTaskListDao
import com.simposio.taskeer.testing.UnusedSubtaskDao
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.TaskDraft
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyDayRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")
    private val later: Instant = now.plusSeconds(60)

    @Test
    fun setMarked_true_creaFilaActivaPendienteConAddedAtAhora() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")

        val result = fixture.repository.setMarked("t1", true)

        assertEquals(DomainResult.Success(Unit), result)
        assertEquals(
            MyDayEntryEntity("t1", now.toEpochMilli(), now.toEpochMilli(), pending = true, deletedAt = null),
            fixture.dao.entries.getValue("t1"),
        )
    }

    @Test
    fun setMarked_true_dosVeces_noDuplicaNiCambiaAddedAt() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.clock = later

        val result = fixture.repository.setMarked("t1", true)

        assertEquals(DomainResult.Success(Unit), result)
        assertEquals(1, fixture.dao.entries.size)
        assertEquals(now.toEpochMilli(), fixture.dao.entries.getValue("t1").addedAt)
        assertEquals(1, fixture.dao.writeCalls)
    }

    @Test
    fun setMarked_false_haceSoftDeleteYMarcaPendiente() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.clock = later

        val result = fixture.repository.setMarked("t1", false)

        assertEquals(DomainResult.Success(Unit), result)
        val entry = fixture.dao.entries.getValue("t1")
        assertEquals(later.toEpochMilli(), entry.deletedAt)
        assertEquals(later.toEpochMilli(), entry.updatedAt)
        assertTrue(entry.pending)
    }

    @Test
    fun setMarked_false_sobreTareaNoMarcada_esExitoSinEscribir() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")

        val result = fixture.repository.setMarked("t1", false)

        assertEquals(DomainResult.Success(Unit), result)
        assertEquals(0, fixture.dao.writeCalls)
    }

    @Test
    fun setMarked_false_dosVeces_noReescribeLaFilaBorrada() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.repository.setMarked("t1", false)
        val writes = fixture.dao.writeCalls

        fixture.repository.setMarked("t1", false)

        assertEquals(writes, fixture.dao.writeCalls)
    }

    @Test
    fun setMarked_remarcarUnaDesmarcada_reactivaLaFilaConAddedAtNuevo() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.repository.setMarked("t1", false)
        fixture.clock = later

        fixture.repository.setMarked("t1", true)

        assertEquals(1, fixture.dao.entries.size)
        val entry = fixture.dao.entries.getValue("t1")
        assertNull(entry.deletedAt)
        assertEquals(later.toEpochMilli(), entry.addedAt)
    }

    @Test
    fun setMarked_tareaInexistenteOBorrada_devuelveTaskNotFoundSinEscribir() = runTest {
        val fixture = fixture()
        fixture.addTask("borrada", deletedAt = now.toEpochMilli())

        listOf("no-existe", "borrada").forEach { id ->
            val error = (fixture.repository.setMarked(id, true) as DomainResult.Failure).error
            assertTrue(error is DomainError.NotFound)
            assertEquals("TASK_NOT_FOUND", error.code)
        }
        assertEquals(0, fixture.dao.writeCalls)
    }

    @Test
    fun observeMarkedTaskIds_emiteAlMarcarYAlDesmarcar() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.addTask("t2")
        assertEquals(emptySet<String>(), fixture.repository.observeMarkedTaskIds().first())

        fixture.repository.setMarked("t1", true)
        fixture.repository.setMarked("t2", true)
        assertEquals(setOf("t1", "t2"), fixture.repository.observeMarkedTaskIds().first())

        fixture.repository.setMarked("t1", false)
        assertEquals(setOf("t2"), fixture.repository.observeMarkedTaskIds().first())
    }

    @Test
    fun pinned_marcadaEnColumnaNormal_apareceConTableroYAddedAt() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)

        val item = fixture.repository.observeMyDay().first().pinned.single()

        assertEquals("t1", item.task.id)
        assertEquals("b1", item.boardId)
        assertEquals("Tablero b1", item.boardName)
        assertEquals(now, item.addedAt)
    }

    @Test
    fun pinned_marcadaEnColumnaCompletada_apareceSiSeCompletoHoyYConservaLaMarca() = runTest {
        val fixture = fixture()
        fixture.addTask("hoy", columnId = "done", completedAt = now.toEpochMilli())
        fixture.addTask("ayer", columnId = "done", completedAt = fixture.dayStart - 3_600_000)
        fixture.repository.setMarked("hoy", true)
        fixture.repository.setMarked("ayer", true)

        assertEquals(listOf("hoy"), fixture.repository.observeMyDay().first().pinned.map { it.task.id })
        assertEquals(setOf("hoy", "ayer"), fixture.repository.observeMarkedTaskIds().first())
        assertNull(fixture.dao.entries.getValue("ayer").deletedAt)
    }

    @Test
    fun pinned_tareaColumnaOTableroBorrados_noAparece() = runTest {
        val fixture = fixture()
        fixture.addTask("tarea-borrada")
        fixture.addTask("columna-borrada", columnId = "c-borrada")
        fixture.addTask("tablero-borrado", columnId = "c-tablero-borrado")
        listOf("tarea-borrada", "columna-borrada", "tablero-borrado").forEach {
            fixture.repository.setMarked(it, true)
        }

        fixture.dao.tasks.value = fixture.dao.tasks.value +
            ("tarea-borrada" to fixture.dao.tasks.value.getValue("tarea-borrada").copy(deletedAt = 1L))

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
    }

    @Test
    fun pinned_desmarcada_noAparece() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        fixture.repository.setMarked("t1", false)

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
    }

    @Test
    fun pinned_ordenaPorAddedAtDescendenteConDesempatePorId() = runTest {
        val fixture = fixture()
        listOf("a", "b", "c", "d").forEach { fixture.addTask(it) }
        fixture.repository.setMarked("a", true)
        fixture.clock = later
        fixture.repository.setMarked("c", true)
        fixture.repository.setMarked("b", true)
        fixture.clock = later.plusSeconds(60)
        fixture.repository.setMarked("d", true)

        val ids = fixture.repository.observeMyDay().first().pinned.map { it.task.id }

        assertEquals(listOf("d", "b", "c", "a"), ids)
    }

    @Test
    fun assigned_soloAsignadasAlUsuarioLocalConFecha() = runTest {
        val fixture = fixture()
        val due = now.plusSeconds(3600).toEpochMilli()
        fixture.addTask("sin-asignado", dueAt = due)
        fixture.addTask("otro", dueAt = due, assigneeId = "otro-usuario")
        fixture.addTask("sin-fecha", assigneeId = LocalUser.ID)
        fixture.addTask("ok", dueAt = due, assigneeId = LocalUser.ID)

        val ids = fixture.repository.observeMyDay().first().assigned.map { it.task.id }

        assertEquals(listOf("ok"), ids)
    }

    @Test
    fun assigned_incluyeVencidasYDeHoy_yExcluyeFechasFuturas() = runTest {
        val fixture = fixture()
        fixture.addTask("vencida", dueAt = fixture.dayStart - 86_400_000, assigneeId = LocalUser.ID)
        fixture.addTask("hoy", dueAt = now.plusSeconds(3600).toEpochMilli(), assigneeId = LocalUser.ID)
        fixture.addTask("manana", dueAt = fixture.dayEnd + 1000, assigneeId = LocalUser.ID)

        val items = fixture.repository.observeMyDay().first().assigned

        assertEquals(listOf("vencida", "hoy"), items.map { it.task.id })
        assertNull(items.first().addedAt)
    }

    @Test
    fun assigned_dueAtEnElInstanteFinDeHoy_noApareceHoy() = runTest {
        val fixture = fixture()
        fixture.addTask("fin", dueAt = fixture.dayEnd, assigneeId = LocalUser.ID)
        fixture.addTask("justo-antes", dueAt = fixture.dayEnd - 1, assigneeId = LocalUser.ID)

        assertEquals(listOf("justo-antes"), fixture.repository.observeMyDay().first().assigned.map { it.task.id })
    }

    @Test
    fun assigned_tareaDeBandejaConFechaDeHoy_aparece() = runTest {
        val fixture = fixture()
        fixture.addTask("pan", columnId = null, dueAt = now.plusSeconds(60).toEpochMilli(), assigneeId = LocalUser.ID)

        val item = fixture.repository.observeMyDay().first().assigned.single()

        assertEquals("pan", item.task.id)
        assertNull(item.boardName)
    }

    @Test
    fun assigned_completadaHoyConFechaHastaHoy_aparece() = runTest {
        val fixture = fixture()
        fixture.addTask(
            "hecha",
            columnId = "done",
            dueAt = now.minusSeconds(60).toEpochMilli(),
            assigneeId = LocalUser.ID,
            completedAt = now.toEpochMilli(),
        )

        val item = fixture.repository.observeMyDay().first().assigned.single()

        assertEquals("hecha", item.task.id)
        assertTrue(item.task.isCompleted)
    }

    @Test
    fun assigned_completadaConFechaFutura_noAparece() = runTest {
        val fixture = fixture()
        fixture.addTask(
            "hecha-futura",
            dueAt = fixture.dayEnd + 1000,
            assigneeId = LocalUser.ID,
            completedAt = now.toEpochMilli(),
        )

        assertTrue(fixture.repository.observeMyDay().first().assigned.isEmpty())
    }

    @Test
    fun assigned_completadaAyer_noAparece() = runTest {
        val fixture = fixture()
        fixture.addTask(
            "hecha-ayer",
            dueAt = fixture.dayStart - 1000,
            assigneeId = LocalUser.ID,
            completedAt = fixture.dayStart - 1000,
        )

        assertTrue(fixture.repository.observeMyDay().first().assigned.isEmpty())
    }

    @Test
    fun assigned_ordenaPorDueAtAscendenteConDesempatePorId() = runTest {
        val fixture = fixture()
        val base = now.toEpochMilli()
        fixture.addTask("b", dueAt = base + 2000, assigneeId = LocalUser.ID)
        fixture.addTask("z", dueAt = base + 1000, assigneeId = LocalUser.ID)
        fixture.addTask("a", dueAt = base + 2000, assigneeId = LocalUser.ID)

        val ids = fixture.repository.observeMyDay().first().assigned.map { it.task.id }

        assertEquals(listOf("z", "a", "b"), ids)
    }

    @Test
    fun tareaMarcadaYAsignada_apareceEnAmbosBloques() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", dueAt = now.toEpochMilli() + 1000, assigneeId = LocalUser.ID)
        fixture.repository.setMarked("t1", true)

        val myDay = fixture.repository.observeMyDay().first()

        assertEquals(listOf("t1"), myDay.pinned.map { it.task.id })
        assertEquals(listOf("t1"), myDay.assigned.map { it.task.id })
    }

    @Test
    fun moverAHechoHoy_laMantieneEnMiDia() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", dueAt = now.toEpochMilli() + 1000, assigneeId = LocalUser.ID)
        fixture.repository.setMarked("t1", true)

        fixture.moveTask("t1", "done", completedAt = now.toEpochMilli())
        val inDone = fixture.repository.observeMyDay().first()
        assertTrue(inDone.pinned.single().task.isCompleted)
        assertTrue(inDone.assigned.single().task.isCompleted)
        assertNull(fixture.dao.entries.getValue("t1").deletedAt)

        fixture.moveTask("t1", "c1", completedAt = null)
        val back = fixture.repository.observeMyDay().first()
        assertEquals(now, back.pinned.single().addedAt)
        assertFalse(back.pinned.single().task.isCompleted)
    }

    @Test
    fun desmarcarCompletada_vuelveAPendiente() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", completedAt = now.toEpochMilli())
        fixture.repository.setMarked("t1", true)
        assertTrue(fixture.repository.observeMyDay().first().pinned.single().task.isCompleted)

        fixture.setCompleted("t1", null)

        assertFalse(fixture.repository.observeMyDay().first().pinned.single().task.isCompleted)
    }

    @Test
    fun overdueCount_esExactoSinTopeParaCeroCincoSeisYDoscientosCincuenta() = runTest {
        listOf(0, 5, 6, 250).forEach { total ->
            val fixture = fixture()
            repeat(total) { fixture.addTask("v$it", dueAt = now.toEpochMilli() - 1, assigneeId = LocalUser.ID) }

            assertEquals(total, fixture.repository.observeOverdueCount().first())
        }
    }

    @Test
    fun overdueCount_noCuentaCompletadasDeHoyAunqueSigaVisible() = runTest {
        val fixture = fixture()
        fixture.addTask(
            "hecha",
            dueAt = now.minusSeconds(60).toEpochMilli(),
            assigneeId = LocalUser.ID,
            completedAt = now.toEpochMilli(),
        )

        assertEquals(listOf("hecha"), fixture.repository.observeMyDay().first().assigned.map { it.task.id })
        assertEquals(0, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun overdueCount_dueAtIgualAAhora_noCuenta() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", dueAt = now.toEpochMilli(), assigneeId = LocalUser.ID)

        assertEquals(0, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun overdueCount_noCuentaSinAsignadoSinFechaFuturasOtroAsignadoCompletadasNiBorradas() = runTest {
        val fixture = fixture()
        val past = now.toEpochMilli() - 1000
        fixture.addTask("sin-asignado", dueAt = past)
        fixture.addTask("sin-fecha", assigneeId = LocalUser.ID)
        fixture.addTask("futura", dueAt = now.toEpochMilli() + 1000, assigneeId = LocalUser.ID)
        fixture.addTask("otro", dueAt = past, assigneeId = "otro-usuario")
        fixture.addTask("hecha", columnId = "done", dueAt = past, assigneeId = LocalUser.ID, completedAt = now.toEpochMilli())
        fixture.addTask("borrada", dueAt = past, assigneeId = LocalUser.ID, deletedAt = 1L)
        fixture.addTask("cuenta", dueAt = past, assigneeId = LocalUser.ID)

        assertEquals(1, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun overdueCount_seReemiteAlCambiarLasTareas() = runTest {
        val fixture = fixture()
        val seen = mutableListOf<Int>()
        val count = fixture.repository.observeOverdueCount()
        seen += count.first()

        fixture.addTask("v1", dueAt = now.toEpochMilli() - 1, assigneeId = LocalUser.ID)
        seen += count.first()

        assertEquals(listOf(0, 1), seen)
    }

    @Test
    fun overdueCount_usaAhoraFrescoEnCadaEmision() = runTest {
        val fixture = fixture()
        val seen = mutableListOf<Int>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.repository.observeOverdueCount().toList(seen)
        }
        fixture.addTask("t1", dueAt = now.toEpochMilli() + 1000, assigneeId = LocalUser.ID)

        fixture.clock = now.plusSeconds(60)
        fixture.addTask("otra", dueAt = now.toEpochMilli() + 5_000_000, assigneeId = LocalUser.ID)
        collector.cancel()

        assertEquals(listOf(0, 1), seen.distinct())
    }

    @Test
    fun tareaCreadaSinAsignado_aparecenEnAsignadasYVencidas() = runTest {
        val fixture = fixture()
        val columns = object : ColumnDao() {
        override fun observeAllActive(): Flow<List<ColumnEntity>> = emptyFlow()

            override fun observeByBoard(boardId: String): Flow<List<ColumnEntity>> = error("no se usa")
            override suspend fun findActiveByBoard(boardId: String): List<ColumnEntity> = error("no se usa")
            override suspend fun findById(id: String): ColumnEntity? = ColumnEntity(
                id = id, boardId = "b1", name = "Por hacer", position = 0, wipLimit = null,
                isDoneColumn = false, createdAt = 0L, updatedAt = 0L, pending = false, deletedAt = null,
            )
            override suspend fun upsert(column: ColumnEntity) = Unit
            override suspend fun upsertAll(columns: List<ColumnEntity>) = Unit
            override suspend fun insertBoard(board: BoardEntity) = Unit
        }
        val taskRepository = TaskRepositoryImpl(WritingTaskDao(fixture.dao), columns, UnusedSubtaskDao(), UnusedReminderDao(), UnusedTaskListDao(), { error("no se usa") }, now = { now })

        taskRepository.createTask("c1", TaskDraft(title = "Vencida", dueAt = now.minusSeconds(60)))

        assertEquals(1, fixture.repository.observeMyDay().first().assigned.size)
        assertEquals(1, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun observeMyDay_incluyeTareaDeBandejaMarcadaSinTablero() = runTest {
        val fixture = fixture()
        fixture.addTask("pan", columnId = null)
        fixture.repository.setMarked("pan", true)

        val pinned = fixture.repository.observeMyDay().first().pinned.single()

        assertEquals("pan", pinned.task.id)
        assertNull(pinned.boardName)
    }

    @Test
    fun observeMyDay_incluyeTareaDeBandejaAsignadaConFechaYLaCuentaComoVencida() = runTest {
        val fixture = fixture()
        fixture.addTask("pan", columnId = null, dueAt = now.minusSeconds(60).toEpochMilli(), assigneeId = LocalUser.ID)

        assertEquals(listOf("pan"), fixture.repository.observeMyDay().first().assigned.map { it.task.id })
        assertEquals(1, fixture.repository.observeOverdueCount().first())
    }

    @Test
    fun observeMyDay_conColumnaInexistente_sigueExcluida() = runTest {
        val fixture = fixture()
        fixture.addTask("huerfana", columnId = "c-x")
        fixture.repository.setMarked("huerfana", true)

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
    }

    @Test
    fun pinned_soloMarcasDeHoy() = runTest {
        val fixture = fixture()
        fixture.addTask("hoy")
        fixture.addTask("ayer")
        fixture.putEntry("hoy", now)
        fixture.putEntry("ayer", now.minusSeconds(86_400))

        assertEquals(listOf("hoy"), fixture.repository.observeMyDay().first().pinned.map { it.task.id })
    }

    @Test
    fun pinned_marcaDeAyer_noApareceYConservaLaEntrada() = runTest {
        val fixture = fixture()
        fixture.addTask("ayer")
        fixture.putEntry("ayer", now.minusSeconds(86_400))

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
        assertNull(fixture.dao.entries.getValue("ayer").deletedAt)
    }

    @Test
    fun pinned_marcaALas0000_esDeHoy() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.putEntry("t1", Instant.ofEpochMilli(fixture.dayStart))

        assertEquals(listOf("t1"), fixture.repository.observeMyDay().first().pinned.map { it.task.id })
    }

    @Test
    fun pinned_marcaAlas2359_esDeAyer() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.putEntry("t1", Instant.ofEpochMilli(fixture.dayStart).minusSeconds(60))

        val myDay = fixture.repository.observeMyDay().first()

        assertTrue(myDay.pinned.isEmpty())
        assertEquals(listOf("t1"), myDay.suggestions.map { it.task.id })
    }

    @Test
    fun pinned_completadaHoy_aparece() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", completedAt = now.toEpochMilli())
        fixture.repository.setMarked("t1", true)

        assertTrue(fixture.repository.observeMyDay().first().pinned.single().task.isCompleted)
    }

    @Test
    fun pinned_completadaAyer_noApareceAunqueEstéMarcadaHoy() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", completedAt = fixture.dayStart - 1000)
        fixture.repository.setMarked("t1", true)

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
    }

    @Test
    fun alCambiarElDia_laMarcaDeAyerSaleDeLaListaPeroLaEntradaSigueActiva() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        val seen = mutableListOf<MyDay>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.repository.observeMyDay().toList(seen)
        }
        assertEquals(listOf("t1"), seen.last().pinned.map { it.task.id })

        fixture.advanceTo(now.plusSeconds(86_400))
        collector.cancel()

        assertTrue(seen.last().pinned.isEmpty())
        assertEquals(listOf("t1"), seen.last().suggestions.map { it.task.id })
        assertNull(fixture.dao.entries.getValue("t1").deletedAt)
    }

    @Test
    fun alCambiarElDia_noEscribeEnElDao() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        val writes = fixture.dao.writeCalls
        val entry = fixture.dao.entries.getValue("t1")

        fixture.advanceTo(now.plusSeconds(86_400))
        fixture.repository.observeMyDay().first()
        fixture.repository.observeOverdueCount().first()

        assertEquals(writes, fixture.dao.writeCalls)
        assertEquals(entry, fixture.dao.entries.getValue("t1"))
    }

    @Test
    fun alCambiarElDia_laCompletadaDeAyerSale() = runTest {
        val fixture = fixture()
        fixture.addTask("t1", completedAt = now.toEpochMilli())
        fixture.repository.setMarked("t1", true)
        assertEquals(1, fixture.repository.observeMyDay().first().pinned.size)

        fixture.advanceTo(now.plusSeconds(86_400))

        assertTrue(fixture.repository.observeMyDay().first().pinned.isEmpty())
    }

    @Test
    fun suggestions_incluyeMarcasAnterioresPendientes() = runTest {
        val fixture = fixture()
        fixture.addTask("ayer")
        fixture.putEntry("ayer", now.minusSeconds(86_400))

        assertEquals(listOf("ayer"), fixture.repository.observeMyDay().first().suggestions.map { it.task.id })
    }

    @Test
    fun suggestions_excluyeCompletadasBorradasYMarcasDeHoy() = runTest {
        val fixture = fixture()
        val yesterday = now.minusSeconds(86_400)
        fixture.addTask("ok")
        fixture.addTask("completada", completedAt = now.minusSeconds(3600).toEpochMilli())
        fixture.addTask("tarea-borrada", deletedAt = 1L)
        fixture.addTask("columna-borrada", columnId = "c-borrada")
        fixture.addTask("marca-borrada")
        fixture.addTask("hoy")
        listOf("ok", "completada", "tarea-borrada", "columna-borrada").forEach { fixture.putEntry(it, yesterday) }
        fixture.putEntry("marca-borrada", yesterday, deletedAt = yesterday.toEpochMilli())
        fixture.putEntry("hoy", now)

        assertEquals(listOf("ok"), fixture.repository.observeMyDay().first().suggestions.map { it.task.id })
    }

    @Test
    fun suggestions_excluyeLasQueYaEstanEnAsignadasDeHoy() = runTest {
        val fixture = fixture()
        fixture.addTask("vencida", dueAt = now.minusSeconds(60).toEpochMilli(), assigneeId = LocalUser.ID)
        fixture.addTask("solo-marca")
        fixture.putEntry("vencida", now.minusSeconds(86_400))
        fixture.putEntry("solo-marca", now.minusSeconds(86_400))

        val myDay = fixture.repository.observeMyDay().first()

        assertEquals(listOf("vencida"), myDay.assigned.map { it.task.id })
        assertEquals(listOf("solo-marca"), myDay.suggestions.map { it.task.id })
    }

    @Test
    fun suggestions_marcaDeHaceTresDias_sigueSugerida() = runTest {
        val fixture = fixture()
        fixture.addTask("vieja")
        fixture.putEntry("vieja", now.minusSeconds(3 * 86_400))

        assertEquals(listOf("vieja"), fixture.repository.observeMyDay().first().suggestions.map { it.task.id })
    }

    @Test
    fun suggestions_ordenadasPorAddedAtDesc() = runTest {
        val fixture = fixture()
        listOf("a", "b", "c").forEach { fixture.addTask(it) }
        fixture.putEntry("a", now.minusSeconds(3 * 86_400))
        fixture.putEntry("b", now.minusSeconds(86_400))
        fixture.putEntry("c", now.minusSeconds(86_400))

        assertEquals(listOf("b", "c", "a"), fixture.repository.observeMyDay().first().suggestions.map { it.task.id })
    }

    @Test
    fun setMarked_conMarcaAnterior_renuevaAddedAt() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.putEntry("t1", now.minusSeconds(86_400))

        val result = fixture.repository.setMarked("t1", true)

        assertEquals(DomainResult.Success(Unit), result)
        assertEquals(
            MyDayEntryEntity("t1", now.toEpochMilli(), now.toEpochMilli(), pending = true, deletedAt = null),
            fixture.dao.entries.getValue("t1"),
        )
        assertEquals(1, fixture.dao.entries.size)
        val myDay = fixture.repository.observeMyDay().first()
        assertEquals(listOf("t1"), myDay.pinned.map { it.task.id })
        assertTrue(myDay.suggestions.isEmpty())
    }

    @Test
    fun setMarked_conMarcaDeHoy_sigueSiendoIdempotente() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.putEntry("t1", now.minusSeconds(3600))
        val entry = fixture.dao.entries.getValue("t1")

        val result = fixture.repository.setMarked("t1", true)

        assertEquals(DomainResult.Success(Unit), result)
        assertEquals(0, fixture.dao.writeCalls)
        assertEquals(entry, fixture.dao.entries.getValue("t1"))
    }

    @Test
    fun setMarked_false_conMarcaAnterior_laDejaBorradaLogicamente() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.putEntry("t1", now.minusSeconds(86_400))

        val result = fixture.repository.setMarked("t1", false)

        assertEquals(DomainResult.Success(Unit), result)
        val entry = fixture.dao.entries.getValue("t1")
        assertEquals(now.toEpochMilli(), entry.deletedAt)
        assertTrue(entry.pending)
        assertTrue(fixture.repository.observeMyDay().first().suggestions.isEmpty())
    }

    @Test
    fun observeMarkedTaskIds_soloMarcasDeHoy() = runTest {
        val fixture = fixture()
        fixture.addTask("hoy")
        fixture.addTask("ayer")
        fixture.putEntry("hoy", now)
        fixture.putEntry("ayer", now.minusSeconds(86_400))

        assertEquals(setOf("hoy"), fixture.repository.observeMarkedTaskIds().first())
    }

    @Test
    fun observeMarkedTaskIds_alCambiarElDia_seVaciaLaMarcaDeAyer() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.repository.setMarked("t1", true)
        val seen = mutableListOf<Set<String>>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.repository.observeMarkedTaskIds().toList(seen)
        }
        assertEquals(setOf("t1"), seen.last())

        fixture.advanceTo(now.plusSeconds(86_400))
        collector.cancel()

        assertEquals(emptySet<String>(), seen.last())
    }

    @Test
    fun repetida_originalCompletadaHoyAparece_yLaSucesoraDeHoyTambien() = runTest {
        val fixture = fixture()
        val due = now.plusSeconds(3600).toEpochMilli()
        fixture.addTask("original", dueAt = due, assigneeId = LocalUser.ID, completedAt = now.toEpochMilli())
        fixture.addTask("sucesora", dueAt = due + 600_000, assigneeId = LocalUser.ID)
        fixture.repository.setMarked("original", true)

        val myDay = fixture.repository.observeMyDay().first()

        assertEquals(listOf("original"), myDay.pinned.map { it.task.id })
        assertEquals(listOf("original", "sucesora"), myDay.assigned.map { it.task.id })
        assertTrue(myDay.assigned.first().task.isCompleted)
    }

    @Test
    fun repetida_sucesoraFutura_noApareceEnMiDia() = runTest {
        val fixture = fixture()
        fixture.addTask("original", dueAt = now.toEpochMilli(), assigneeId = LocalUser.ID, completedAt = now.toEpochMilli())
        fixture.addTask("sucesora", dueAt = fixture.dayEnd + 3_600_000, assigneeId = LocalUser.ID)

        val myDay = fixture.repository.observeMyDay().first()

        assertEquals(listOf("original"), myDay.assigned.map { it.task.id })
        assertTrue(myDay.pinned.isEmpty())
    }

    @Test
    fun alCambiarLaZona_reclasificaLasMarcasSinEscribir() = runTest {
        val fixture = fixture()
        fixture.addTask("t1")
        fixture.putEntry("t1", now.minusSeconds(3600))
        assertEquals(listOf("t1"), fixture.repository.observeMyDay().first().pinned.map { it.task.id })
        val entry = fixture.dao.entries.getValue("t1")

        fixture.changeZone(ZoneId.of("Pacific/Kiritimati"))
        val myDay = fixture.repository.observeMyDay().first()

        assertTrue(myDay.pinned.isEmpty())
        assertEquals(listOf("t1"), myDay.suggestions.map { it.task.id })
        assertEquals(0, fixture.dao.writeCalls)
        assertEquals(entry, fixture.dao.entries.getValue("t1"))
    }

    private fun fixture(): Fixture = Fixture(now)

    private inner class Fixture(var clock: Instant) {
        private var zone: ZoneId = ZoneId.of("UTC")
        private val windows = MutableStateFlow(dayWindowAt(clock, zone))
        val dao = FakeMyDayDao()
        val repository = MyDayRepositoryImpl(
            dao,
            FakeTaskDao(dao),
            clock = { clock },
            timeZone = { zone },
            dayWindows = { windows.asStateFlow() },
        )

        val dayStart: Long get() = windows.value.start.toEpochMilli()
        val dayEnd: Long get() = windows.value.end.toEpochMilli()

        fun advanceTo(instant: Instant) {
            clock = instant
            windows.value = dayWindowAt(instant, zone)
        }

        fun changeZone(newZone: ZoneId) {
            zone = newZone
            windows.value = dayWindowAt(clock, zone)
        }

        fun putEntry(id: String, addedAt: Instant, deletedAt: Long? = null) {
            dao.putEntry(MyDayEntryEntity(id, addedAt.toEpochMilli(), addedAt.toEpochMilli(), false, deletedAt))
        }

        fun addTask(
            id: String,
            columnId: String? = "c1",
            dueAt: Long? = null,
            assigneeId: String? = null,
            deletedAt: Long? = null,
            completedAt: Long? = null,
        ) {
            dao.tasks.value = dao.tasks.value + (
                id to TaskEntity(
                    id = id,
                    columnId = columnId,
                    title = "Tarea $id",
                    description = null,
                    priority = "MEDIUM",
                    dueAt = dueAt,
                    assigneeId = assigneeId,
                    position = 0,
                    createdAt = 0L,
                    updatedAt = 0L,
                    pending = false,
                    completedAt = completedAt,
                    deletedAt = deletedAt,
                )
                )
        }

        fun moveTask(id: String, columnId: String, completedAt: Long?) {
            dao.tasks.value = dao.tasks.value +
                (id to dao.tasks.value.getValue(id).copy(columnId = columnId, completedAt = completedAt))
        }

        fun setCompleted(id: String, completedAt: Long?) {
            dao.tasks.value = dao.tasks.value + (id to dao.tasks.value.getValue(id).copy(completedAt = completedAt))
        }
    }

    private data class ColumnInfo(val boardId: String, val isDone: Boolean, val deleted: Boolean = false)

    private data class BoardInfo(val name: String, val deleted: Boolean = false)

    private class FakeMyDayDao : MyDayDao() {
        val tasks = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())
        private val entriesState = MutableStateFlow<Map<String, MyDayEntryEntity>>(emptyMap())
        private val columns = mapOf(
            "c1" to ColumnInfo("b1", isDone = false),
            "done" to ColumnInfo("b1", isDone = true),
            "c-borrada" to ColumnInfo("b1", isDone = false, deleted = true),
            "c-tablero-borrado" to ColumnInfo("b-borrado", isDone = false),
        )
        private val boards = mapOf("b1" to BoardInfo("Tablero b1"), "b-borrado" to BoardInfo("Borrado", deleted = true))

        var writeCalls: Int = 0
            private set

        val entries: Map<String, MyDayEntryEntity> get() = entriesState.value

        fun putEntry(entry: MyDayEntryEntity) {
            entriesState.value = entriesState.value + (entry.id to entry)
        }

        override fun observeMarkedIds(dayStart: Long): Flow<List<String>> = entriesState.map { all ->
            all.values.filter { it.deletedAt == null && it.addedAt >= dayStart }.map { it.id }
        }

        override suspend fun findEntry(taskId: String): MyDayEntryEntity? = entriesState.value[taskId]

        override suspend fun upsert(entry: MyDayEntryEntity) {
            writeCalls++
            entriesState.value = entriesState.value + (entry.id to entry)
        }

        override fun observePinned(dayStart: Long): Flow<List<MyDayRow>> = combine(tasks, entriesState) { allTasks, allEntries ->
            allEntries.values
                .filter { it.deletedAt == null && it.addedAt >= dayStart }
                .mapNotNull { entry -> visible(allTasks[entry.id])?.takeIf { openOrDoneToday(it, dayStart) }?.let { it to entry } }
                .sortedWith(compareBy<Pair<MyDayRow, MyDayEntryEntity>>({ -it.second.addedAt }, { it.first.task.id }))
                .map { (row, entry) -> row.copy(addedAt = entry.addedAt) }
        }

        override fun observeAssigned(assigneeId: String, dayStart: Long, dayEnd: Long): Flow<List<MyDayRow>> =
            tasks.map { all ->
                all.values
                    .filter { it.assigneeId == assigneeId && it.dueAt != null && it.dueAt < dayEnd }
                    .mapNotNull(::visible)
                    .filter { openOrDoneToday(it, dayStart) }
                    .sortedWith(compareBy({ it.task.dueAt }, { it.task.id }))
            }

        override fun observeSuggestions(dayStart: Long): Flow<List<MyDayRow>> = combine(tasks, entriesState) { allTasks, allEntries ->
            allEntries.values
                .filter { it.deletedAt == null && it.addedAt < dayStart }
                .mapNotNull { entry ->
                    visible(allTasks[entry.id])?.takeIf { it.task.completedAt == null }?.let { it to entry }
                }
                .sortedWith(compareBy<Pair<MyDayRow, MyDayEntryEntity>>({ -it.second.addedAt }, { it.first.task.id }))
                .map { (row, entry) -> row.copy(addedAt = entry.addedAt) }
        }

        override fun observeOpen(assigneeId: String): Flow<List<MyDayRow>> = tasks.map { all ->
            all.values
                .filter { it.assigneeId == assigneeId && it.completedAt == null }
                .mapNotNull(::visible)
                .sortedWith(compareBy({ it.task.dueAt }, { it.task.id }))
        }

        override fun observeOverdueCount(assigneeId: String, now: Long): Flow<Int> = tasks.map { all ->
            all.values.count {
                it.assigneeId == assigneeId && it.dueAt != null && it.dueAt < now &&
                    it.completedAt == null && visible(it) != null
            }
        }

        private fun openOrDoneToday(row: MyDayRow, dayStart: Long): Boolean =
            row.task.completedAt.let { it == null || it >= dayStart }

        private fun visible(task: TaskEntity?): MyDayRow? {
            if (task == null || task.deletedAt != null) return null
            if (task.columnId == null) return MyDayRow(task, boardId = null, boardName = null, addedAt = null)
            val column = columns[task.columnId] ?: return null
            val board = boards[column.boardId] ?: return null
            if (column.deleted || board.deleted) return null
            return MyDayRow(task, column.boardId, board.name, addedAt = null)
        }
    }

    private class WritingTaskDao(private val myDayDao: FakeMyDayDao) : TaskDao() {
        override suspend fun findActiveByList(listId: String): List<TaskEntity> = error("no se usa")

        override suspend fun restoreSubtasksOfTask(taskId: String, deletedAt: Long, now: Long) = error("no se usa")

        override suspend fun restoreRemindersOfTask(taskId: String, deletedAt: Long, now: Long): Int = error("no se usa")
        override fun observeByList(listId: String): Flow<List<TaskEntity>> = emptyFlow()

        override fun observeActiveCountsByList(): Flow<List<ListCountRow>> = emptyFlow()

        override suspend fun countInList(listId: String): Int = 0

        override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = emptyFlow()

        override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) = Unit

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = error("no se usa")
        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()
        override suspend fun findById(id: String): TaskEntity? = myDayDao.tasks.value[id]
        override suspend fun countByColumn(columnId: String): Int = 0
        override suspend fun upsert(task: TaskEntity) {
            myDayDao.tasks.value = myDayDao.tasks.value + (task.id to task)
        }
        override suspend fun upsertAll(tasks: List<TaskEntity>) = Unit
        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override fun observeCompletedCount(): Flow<Int> = emptyFlow()

        override suspend fun findCompleted(): List<TaskEntity> = emptyList()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = null

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = Unit

        override suspend fun insertReminder(reminder: ReminderEntity) = Unit
    }

    private class FakeTaskDao(private val myDayDao: FakeMyDayDao) : TaskDao() {
        override suspend fun findActiveByList(listId: String): List<TaskEntity> = error("no se usa")

        override suspend fun restoreSubtasksOfTask(taskId: String, deletedAt: Long, now: Long) = error("no se usa")

        override suspend fun restoreRemindersOfTask(taskId: String, deletedAt: Long, now: Long): Int = error("no se usa")
        override fun observeByList(listId: String): Flow<List<TaskEntity>> = emptyFlow()

        override fun observeActiveCountsByList(): Flow<List<ListCountRow>> = emptyFlow()

        override suspend fun countInList(listId: String): Int = 0

        override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = emptyFlow()

        override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) = Unit

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = myDayDao.tasks.map { it.values.toList() }

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()

        override suspend fun findById(id: String): TaskEntity? = myDayDao.tasks.value[id]

        override suspend fun countByColumn(columnId: String): Int = 0

        override suspend fun upsert(task: TaskEntity) = Unit

        override suspend fun upsertAll(tasks: List<TaskEntity>) = Unit

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override fun observeCompletedCount(): Flow<Int> = emptyFlow()

        override suspend fun findCompleted(): List<TaskEntity> = emptyList()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = null

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = Unit

        override suspend fun insertReminder(reminder: ReminderEntity) = Unit
    }
}
