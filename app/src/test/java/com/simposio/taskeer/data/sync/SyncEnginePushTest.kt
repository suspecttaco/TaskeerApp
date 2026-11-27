package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.SyncDao
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.testing.DirectTransportSource
import com.simposio.taskeer.testing.FakeSession
import com.simposio.taskeer.testing.FakeSyncBackend
import com.simposio.taskeer.testing.FakeSyncDao
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncSubtask
import com.simposio.taskeer.testing.syncTask
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEnginePushTest {

    private val dao = FakeSyncDao()
    private val backend = FakeSyncBackend()
    private val scheduled = mutableListOf<Duration>()
    private var clockNow = Instant.parse("2026-11-26T18:30:00Z")
    private var ids = 0
    private val scopeHolder = SyncScopeHolder()
    private val store = RoomSyncLocalStore(dao, scopeHolder, newMutationId = { "m${++ids}" })

    private fun TestScope.engine(
        session: FakeSession = FakeSession(),
        source: TransportSource = DirectTransportSource(backend),
    ): SyncEngine {
        val triggers = SyncTriggers(emptyFlow(), emptyFlow(), CoroutineScope(StandardTestDispatcher(testScheduler)))
        return SyncEngine(
            store = store,
            source = source,
            gate = SyncSessionGate(store),
            wiper = LocalDataWiper(dao) { clockNow },
            session = session.state,
            scopeHolder = scopeHolder,
            triggers = triggers,
            retry = { scheduled += it },
            clock = { clockNow },
        )
    }

    private val localList = syncList("list-1", pending = false)

    init {
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
    }

    @Test
    fun sinConexion_acumulaYAlRecuperarEnvia() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        dao.put(syncTask("t2"))
        val engine = engine()
        backend.transportFailures += TransportResult.Retry(null, RetryKind.NETWORK)

        engine.runCycle(manual = false)

        assertEquals(2, dao.queue().size)
        assertTrue(dao.tasks.value.values.all { it.pending })
        assertNull(backend.task("t1"))

        engine.runCycle(manual = true)

        assertEquals(0, dao.queue().size)
        assertNotEquals(null, backend.task("t1"))
        assertNotEquals(null, backend.task("t2"))
    }

    @Test
    fun applied_limpiaPendingYGuardaBaseline() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1", updatedAt = 4_321L))
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertFalse(row.pending)
        assertEquals(4_321L, row.updatedAt)
        assertEquals(backend.serverNow.toEpochMilli(), dao.baseline("task", "t1")!!.serverUpdatedAt)
        assertEquals(0, dao.queue().size)
    }

    @Test
    fun applied_conEdicionEnVuelo_conservaPending() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1", updatedAt = 1_000L))
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        backend.onProcessing = { dao.put(syncTask("t1", title = "editada mientras viajaba", updatedAt = 2_000L)) }
        val engine = engine()

        engine.runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertTrue(row.pending)
        assertEquals("editada mientras viajaba", row.title)
        assertEquals(backend.serverNow.toEpochMilli(), dao.baseline("task", "t1")!!.serverUpdatedAt)
        assertEquals(0, dao.queue().size)

        backend.onProcessing = {}
        engine.runCycle(manual = false)

        assertFalse(dao.tasks.value.getValue("t1").pending)
    }

    @Test
    fun applied_adoptaPositionYListDelRecord() = runTest {
        dao.put(localList)
        dao.put(syncList("list-2", name = "Otra", pending = false))
        dao.baseline("list", "list-1", 1L)
        dao.baseline("list", "list-2", 1L)
        dao.put(syncTask("t1", position = 9, updatedAt = 4_321L))
        backend.seed(SyncEntity.LIST, "list-2", mapOf("name" to "Otra"), Instant.EPOCH)
        backend.serverRewrite = { _, sent -> sent + mapOf("position" to 2, "listId" to "list-2") }

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertEquals(2, row.position)
        assertEquals("list-2", row.listId)
        assertFalse(row.pending)
        assertEquals(4_321L, row.updatedAt)
        assertEquals(backend.serverNow.toEpochMilli(), dao.baseline("task", "t1")!!.serverUpdatedAt)
    }

    @Test
    fun applied_conRecordParcial_adoptaLoQueTrae() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1", title = "local", position = 9))
        backend.serverRewrite = { _, _ -> mapOf("position" to 3) }

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertEquals(3, row.position)
        assertEquals("local", row.title)
        assertEquals("list-1", row.listId)
        assertFalse(row.pending)
    }

    @Test
    fun applied_conEdicionEnVuelo_noPisaLaEdicionLocal() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1", position = 9, updatedAt = 1_000L))
        backend.serverRewrite = { _, sent -> sent + mapOf("position" to 2) }
        backend.onProcessing = { dao.put(syncTask("t1", title = "editada mientras viajaba", position = 9, updatedAt = 2_000L)) }

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertTrue(row.pending)
        assertEquals("editada mientras viajaba", row.title)
        assertEquals(9, row.position)
        assertEquals(backend.serverNow.toEpochMilli(), dao.baseline("task", "t1")!!.serverUpdatedAt)
    }

    @Test
    fun applied_deleteSinRegistro_confirmaYDesencola() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1", pending = true, deletedAt = 3_000L, updatedAt = 3_000L))
        backend.appliedWithoutRecordIds += "t1"

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertFalse(row.pending)
        assertEquals(3_000L, row.deletedAt)
        assertEquals(0, dao.queue().size)
    }

    @Test
    fun applied_upsertSinRegistro_noBloqueaElRestoDelLote() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1", pending = true, updatedAt = 3_000L))
        dao.put(syncTask("t2", pending = true, deletedAt = 4_000L, updatedAt = 4_000L))
        dao.baseline("task", "t2", 1L)
        backend.appliedWithoutRecordIds += "t1"

        engine().runCycle(manual = false)

        val left = dao.queue().single()
        assertEquals("t1", left.entityId)
        assertEquals(1, left.failures)
        assertTrue(dao.tasks.value.getValue("t1").pending)
        assertFalse(dao.tasks.value.getValue("t2").pending)
    }

    @Test
    fun applied_deleteConfirmado_noCambiaLaFilaLocal() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1", pending = true, deletedAt = 3_000L, updatedAt = 3_000L))
        dao.baseline("task", "t1", Instant.parse("2026-11-20T00:00:00Z").toEpochMilli())
        backend.seed(SyncEntity.TASK, "t1", taskFields("servidor"), Instant.parse("2026-11-20T00:00:00Z"))

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertEquals(3_000L, row.deletedAt)
        assertEquals("Comprar leche", row.title)
        assertFalse(row.pending)
        assertEquals(0, dao.queue().size)
    }

    @Test
    fun nombreTomado_enRenombrado_conservaLaListaLocal() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncList("list-2", name = "Tareas", pending = true))
        dao.baseline("list", "list-2", 1L)
        dao.put(syncTask("t2", listId = "list-2", pending = false))
        backend.seed(SyncEntity.LIST, "list-2", mapOf("name" to "Compras"), Instant.EPOCH)
        backend.rejectIds["list-2"] = FakeSyncBackend.Rejection("LIST_NAME_TAKEN", withRecord = true, recordId = "list-1")

        engine().runCycle(manual = false)

        val list = dao.lists.value.getValue("list-2")
        assertEquals("Tareas", list.name)
        assertTrue(list.pending)
        assertNull(list.deletedAt)
        assertEquals("list-2", dao.tasks.value.getValue("t2").listId)
        assertEquals("LIST_NAME_TAKEN", dao.queue().single().lastErrorCode)
    }

    @Test
    fun nombreTomado_enAlta_adoptaListaDelServidor() = runTest {
        dao.put(syncList("list-nueva", name = "Tareas", pending = true))
        dao.put(syncTask("t3", listId = "list-nueva", pending = false))
        backend.rejectIds["list-nueva"] = FakeSyncBackend.Rejection("LIST_NAME_TAKEN", withRecord = true, recordId = "list-1")

        engine().runCycle(manual = false)

        assertNull(dao.lists.value["list-nueva"]?.takeIf { it.deletedAt == null })
        assertFalse(dao.lists.value.getValue("list-1").pending)
        assertEquals("list-1", dao.tasks.value.getValue("t3").listId)
        assertEquals(0, dao.queue().count { it.entityId == "list-nueva" })
    }

    @Test
    fun mismoClientMutationIdEnReintento() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        backend.transportFailures += TransportResult.Retry(null, RetryKind.NETWORK)
        val engine = engine()

        engine.runCycle(manual = false)
        engine.runCycle(manual = true)

        val sent = backend.batches.flatten().filter { it.id == "t1" }.map { it.clientMutationId }
        assertEquals(2, sent.size)
        assertEquals(sent[0], sent[1])
    }

    @Test
    fun reenvioConResultadoGuardado_noDuplica() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        backend.dropNextResponse = true
        val engine = engine()

        engine.runCycle(manual = false)
        backend.serverNow = backend.serverNow.plusSeconds(60)
        engine.runCycle(manual = true)

        assertEquals(1, backend.records.keys.count { it.first == SyncEntity.TASK })
        assertEquals(Instant.parse("2026-11-26T18:30:00Z").toEpochMilli(), dao.baseline("task", "t1")!!.serverUpdatedAt)
        assertFalse(dao.tasks.value.getValue("t1").pending)
    }

    @Test
    fun red429_5xx_reprogramaConRetroceso() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        val engine = engine()
        backend.transportFailures += TransportResult.Retry(Duration.ofSeconds(30), RetryKind.RATE_LIMITED)

        engine.runCycle(manual = false)

        val entry = dao.queue().single()
        assertEquals(1, entry.attempts)
        assertEquals(0, entry.failures)
        assertTrue(entry.nextAttemptAt!! >= clockNow.plusSeconds(30).toEpochMilli())
        assertTrue(scheduled.single() >= Duration.ofSeconds(30))
        assertTrue(dao.tasks.value.getValue("t1").pending)

        clockNow = clockNow.plusSeconds(3600)
        backend.transportFailures += TransportResult.Retry(null, RetryKind.SERVER)
        engine.runCycle(manual = false)

        assertEquals(2, dao.queue().single().attempts)
        assertEquals(0, dao.queue().single().failures)
    }

    @Test
    fun rejected_permisoRevierteAdoptandoRecord() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1", title = "servidor", pending = false, updatedAt = 500L))
        dao.baseline("task", "t1", 500L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        backend.seed(SyncEntity.TASK, "t1", mapOf("title" to "servidor", "listId" to "list-1"), Instant.parse("2026-11-20T00:00:00Z"))
        dao.put(syncTask("t1", title = "local", updatedAt = 9_000L))
        backend.rejectIds["t1"] = FakeSyncBackend.Rejection("FORBIDDEN", withRecord = true)
        val engine = engine()
        val notices = mutableListOf<com.simposio.taskeer.domain.sync.SyncNotice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.notices.collect { notices += it } }

        engine.runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertEquals("servidor", row.title)
        assertFalse(row.pending)
        assertEquals(0, dao.queue().size)
        assertEquals(1, notices.single().total)
    }

    @Test
    fun rejected_altaSeDescarta() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        backend.rejectIds["t1"] = FakeSyncBackend.Rejection("FORBIDDEN")

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertFalse(row.pending)
        assertEquals(clockNow.toEpochMilli(), row.deletedAt)
        assertEquals(0, dao.queue().size)
    }

    @Test
    fun rejected_noBloqueaElResto() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        dao.put(syncTask("t1"))
        dao.put(syncTask("t2"))
        backend.rejectIds["t1"] = FakeSyncBackend.Rejection("INVALID_MUTATION")

        engine().runCycle(manual = false)

        assertNotEquals(null, backend.task("t2"))
        assertNull(backend.task("t1"))
        assertFalse(dao.tasks.value.getValue("t2").pending)
    }

    @Test
    fun conflict_veredictoDelServidorEsDefinitivo_adoptaRegistro() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        val serverUpdated = Instant.parse("2026-11-26T12:00:00Z")
        backend.seed(SyncEntity.TASK, "t1", taskFields("servidor"), serverUpdated)
        dao.baseline("task", "t1", Instant.parse("2026-11-26T10:00:00Z").toEpochMilli())
        dao.put(syncTask("t1", title = "local viejo", updatedAt = Instant.parse("2026-11-26T11:00:00Z").toEpochMilli()))
        val engine = engine()

        engine.runCycle(manual = false)
        engine.runCycle(manual = true)

        val row = dao.tasks.value.getValue("t1")
        assertEquals("servidor", row.title)
        assertFalse(row.pending)
        assertEquals(serverUpdated.toEpochMilli(), dao.baseline("task", "t1")!!.serverUpdatedAt)
        assertEquals(1, backend.batches.flatten().count { it.id == "t1" })
    }

    @Test
    fun conflict_tombstone_borraLocal() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        val deleted = Instant.parse("2026-11-26T12:00:00Z")
        backend.seed(SyncEntity.TASK, "t1", taskFields("x"), deleted, deletedAt = deleted)
        dao.baseline("task", "t1", Instant.parse("2026-11-26T10:00:00Z").toEpochMilli())
        dao.put(syncTask("t1", title = "editada", updatedAt = Instant.parse("2026-11-26T18:00:00Z").toEpochMilli()))

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertEquals(deleted.toEpochMilli(), row.deletedAt)
        assertFalse(row.pending)
    }

    @Test
    fun batchTooLarge_divideElLote() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        (1..5).forEach { dao.put(syncTask("t$it")) }
        backend.maxBatch = 2

        engine().runCycle(manual = false)

        assertEquals(5, backend.records.keys.count { it.first == SyncEntity.TASK })
        assertTrue(backend.batches.last().size <= 2)
        assertEquals(0, dao.queue().size)
    }

    @Test
    fun dependencyNotFound_reintentaUnaVezTrasElPadre() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        dao.put(syncTask("t1"))
        dao.put(syncSubtask("s1", "t1"))
        backend.rejectIds["s1"] = FakeSyncBackend.Rejection("DEPENDENCY_NOT_FOUND", once = true)

        engine().runCycle(manual = false)

        assertEquals(2, backend.batches.flatten().count { it.id == "s1" })
        assertFalse(dao.subtasks.value.getValue("s1").pending)
        assertEquals(null, dao.subtasks.value.getValue("s1").deletedAt)
    }

    @Test
    fun dependencyNotFound_segundaVezEsPermanente() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        dao.put(syncTask("t1"))
        dao.put(syncSubtask("s1", "t1"))
        backend.rejectIds["s1"] = FakeSyncBackend.Rejection("DEPENDENCY_NOT_FOUND")

        engine().runCycle(manual = false)

        assertEquals(2, backend.batches.flatten().count { it.id == "s1" })
        assertNotEquals(null, dao.subtasks.value.getValue("s1").deletedAt)
        assertEquals(0, dao.queue().size)
    }

    @Test
    fun rejectedPorValidacion_noBorraLaFilaLocal() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        backend.rejectIds["t1"] = FakeSyncBackend.Rejection(SyncCodes.VALIDATION_ERROR)

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertNull(row.deletedAt)
        assertTrue(row.pending)
        val entry = dao.queue().single()
        assertEquals(SyncCodes.VALIDATION_ERROR, entry.lastErrorCode)
        assertEquals(1, entry.failures)
    }

    @Test
    fun codigoDesconocido_rechazoPermanente() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        backend.rejectIds["t1"] = FakeSyncBackend.Rejection("CODIGO_DE_UN_FUTURO_1_1")

        engine().runCycle(manual = false)

        assertNotEquals(null, dao.tasks.value.getValue("t1").deletedAt)
        assertEquals(0, dao.queue().size)
    }

    @Test
    fun error5xx_noSeTrataComoRejected() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        backend.transportFailures += TransportResult.Retry(null, RetryKind.SERVER)

        engine().runCycle(manual = false)

        val row = dao.tasks.value.getValue("t1")
        assertNull(row.deletedAt)
        assertTrue(row.pending)
        assertEquals(1, dao.queue().size)
    }

    @Test
    fun lotesDeCien() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        (1..250).forEach { dao.put(syncTask("t$it")) }

        engine().runCycle(manual = false)

        assertEquals(listOf(100, 100, 50), backend.batches.map { it.size })
    }

    @Test
    fun unSoloCicloALaVez() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        dao.put(syncTask("t1"))
        val gate = CompletableDeferred<Unit>()
        backend.onProcessing = { gate.await() }
        val engine = engine()

        val first = async { engine.runCycle(manual = false) }
        val second = async { engine.runCycle(manual = true) }
        advanceUntilIdle()
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, backend.maxConcurrent)
        assertEquals(1, backend.batches.flatten().count { it.id == "t1" })
    }

    @Test
    fun cuerpoInvalido_unaEntradaVenenosa_noBloqueaElResto() = runTest {
        dao.put(localList)
        dao.baseline("list", "list-1", 1L)
        backend.seed(SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        (1..4).forEach { dao.put(syncTask("t$it")) }
        backend.poisonedIds += "t3"
        val engine = engine()

        engine.runCycle(manual = false)

        assertEquals(setOf("t1", "t2", "t4"), backend.records.keys.filter { it.first == SyncEntity.TASK }.map { it.second }.toSet())
        val stuck = dao.queue().single()
        assertEquals("t3", stuck.entityId)
        assertEquals(1, stuck.failures)

        repeat(4) { engine.runCycle(manual = true) }

        assertEquals(1, store.observeStuckCount().first())
        val sentBeforeStuck = backend.batches.size
        engine.runCycle(manual = true)
        assertEquals(sentBeforeStuck, backend.batches.size)
    }

    private fun taskFields(title: String): Map<String, Any?> = mapOf(
        "listId" to "list-1", "columnId" to null, "title" to title, "description" to null, "priority" to "MEDIUM",
        "dueAt" to null, "assigneeId" to null, "position" to 0, "completedAt" to null, "repeatUnit" to null,
        "repeatInterval" to null, "repeatWeekdays" to null, "repeatAnchorDay" to null, "repeatSuccessorId" to null,
    )
}
