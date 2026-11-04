package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.AppNotificationDao
import com.simposio.taskeer.data.local.AppNotificationEntity
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.domain.model.NotificationEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryRepositoryImplTest {

    private var now: Instant = Instant.parse("2026-11-04T10:00:00Z")
    private val dao = FakeAppNotificationDao()
    private val repository = NotificationHistoryRepositoryImpl(dao, clock = { now })

    private fun tick(seconds: Long = 1) {
        now = now.plusSeconds(seconds)
    }

    @Test
    fun record_entregada_guardaDeliveredTrueNoLeidaYPendiente() = runTest {
        repository.record(NotificationEvent.Deadline("t1", "Entregar"), delivered = true)

        val row = dao.rows.value.values.single()
        assertEquals("DEADLINE", row.kind)
        assertEquals("Entregar", row.taskTitle)
        assertTrue(row.delivered)
        assertNull(row.readAt)
        assertTrue(row.pending)
        assertNull(row.deletedAt)
        assertEquals(now.toEpochMilli(), row.createdAt)
    }

    @Test
    fun record_noEntregada_guardaDeliveredFalse() = runTest {
        repository.record(NotificationEvent.Manual("r1", "t1", "Llamar"), delivered = false)

        assertFalse(dao.rows.value.values.single().delivered)
    }

    @Test
    fun record_resumenConCero_guardaConteoCero() = runTest {
        repository.record(NotificationEvent.Digest(0), delivered = true)

        assertEquals(0, dao.rows.value.values.single().activeTaskCount)
    }

    @Test
    fun observeAll_ordenaDeMasRecienteAAntigua() = runTest {
        repository.record(NotificationEvent.Digest(1), true)
        tick()
        repository.record(NotificationEvent.Digest(2), true)
        tick()
        repository.record(NotificationEvent.Digest(3), true)

        assertEquals(listOf(3, 2, 1), repository.observeAll().first().map { it.activeTaskCount })
    }

    @Test
    fun observeAll_conMismoInstante_desempataPorIdDescendente() = runTest {
        listOf("a", "c", "b").forEach { id ->
            dao.insert(entity(id, createdAt = now.toEpochMilli()))
        }

        assertEquals(listOf("c", "b", "a"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun observeUnreadCount_cuentaLasNoLeidasVisibles() = runTest {
        repository.record(NotificationEvent.Digest(1), true)
        tick()
        repository.record(NotificationEvent.Digest(2), true)
        val first = dao.rows.value.values.minBy { it.createdAt }

        repository.markRead(first.id)

        assertEquals(1, repository.observeUnreadCount().first())
    }

    @Test
    fun markRead_fijaReadAtYEsIdempotente() = runTest {
        repository.record(NotificationEvent.Digest(1), true)
        val id = dao.rows.value.keys.single()
        tick()
        repository.markRead(id)
        val readAt = dao.rows.value.getValue(id).readAt
        tick()

        repository.markRead(id)

        assertEquals(Instant.parse("2026-11-04T10:00:01Z").toEpochMilli(), readAt)
        assertEquals(readAt, dao.rows.value.getValue(id).readAt)
    }

    @Test
    fun markAllRead_dejaElContadorACero() = runTest {
        repeat(3) {
            repository.record(NotificationEvent.Digest(it), true)
            tick()
        }

        repository.markAllRead()

        assertEquals(0, repository.observeUnreadCount().first())
        assertTrue(dao.rows.value.values.all { it.readAt != null })
    }

    @Test
    fun delete_ocultaLaEntradaSinBorrarlaFisicamente() = runTest {
        repository.record(NotificationEvent.Digest(1), true)
        val id = dao.rows.value.keys.single()
        tick()

        repository.delete(id)

        assertTrue(repository.observeAll().first().isEmpty())
        val row = dao.rows.value.getValue(id)
        assertEquals(now.toEpochMilli(), row.deletedAt)
        assertTrue(row.pending)
        assertEquals(0, repository.observeUnreadCount().first())
    }

    @Test
    fun deleteAll_dejaCeroVisiblesYContadorACero() = runTest {
        repeat(3) {
            repository.record(NotificationEvent.Digest(it), true)
            tick()
        }

        repository.deleteAll()

        assertTrue(repository.observeAll().first().isEmpty())
        assertEquals(0, repository.observeUnreadCount().first())
        assertEquals(3, dao.rows.value.size)
    }

    @Test
    fun deleteAll_sinEntradas_noFalla() = runTest {
        repository.deleteAll()

        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun operacionesSobreIdInexistente_noFallanNiCambianNada() = runTest {
        repository.record(NotificationEvent.Digest(1), true)
        val before = dao.rows.value

        repository.markRead("no-existe")
        repository.delete("no-existe")

        assertEquals(before, dao.rows.value)
    }

    @Test
    fun record_conMasDe200Visibles_ocultaLasMasAntiguasSinOcultarLaNueva() = runTest {
        repeat(201) {
            repository.record(NotificationEvent.Digest(it), true)
            tick()
        }

        val visible = repository.observeAll().first()
        assertEquals(200, visible.size)
        assertEquals(200, visible.first().activeTaskCount)
        assertEquals(1, visible.last().activeTaskCount)
        val hidden = dao.rows.value.values.single { it.deletedAt != null }
        assertEquals(0, hidden.activeTaskCount)
        assertNotNull(hidden.deletedAt)
        assertEquals(201, dao.rows.value.size)
    }

    @Test
    fun record_conRelojRetrocedido_noOcultaLaRecienGuardadaYDejaAlMas200() = runTest {
        repeat(200) {
            repository.record(NotificationEvent.Digest(it), true)
            tick()
        }
        now = now.minusSeconds(100_000)

        repository.record(NotificationEvent.Digest(999), true)

        val visible = repository.observeAll().first()
        assertEquals(200, visible.size)
        assertTrue(visible.any { it.activeTaskCount == 999 })
        assertEquals(1, dao.rows.value.values.count { it.deletedAt != null })
    }

    @Test
    fun record_conExactamente200_noOcultaNada() = runTest {
        repeat(200) {
            repository.record(NotificationEvent.Digest(it), true)
            tick()
        }

        assertEquals(200, repository.observeAll().first().size)
        assertTrue(dao.rows.value.values.all { it.deletedAt == null })
    }

    @Test
    fun record_ignoraEntradasYaOcultasAlContarElTope() = runTest {
        repeat(200) {
            repository.record(NotificationEvent.Digest(it), true)
            tick()
        }
        repository.deleteAll()
        tick()

        repository.record(NotificationEvent.Digest(999), true)

        assertEquals(listOf(999), repository.observeAll().first().map { it.activeTaskCount })
    }

    @Test
    fun observeAll_conTipoDesconocido_omiteLaFila() = runTest {
        dao.insert(entity("raro", kind = "FUTURO"))
        repository.record(NotificationEvent.Digest(1), true)

        val kinds = repository.observeAll().first().map { it.kind }

        assertEquals(listOf(AppNotificationKind.DIGEST), kinds)
    }

    private fun entity(id: String, createdAt: Long = 0L, kind: String = "DIGEST") = AppNotificationEntity(
        id = id,
        kind = kind,
        taskId = null,
        taskTitle = null,
        activeTaskCount = 1,
        delivered = true,
        createdAt = createdAt,
        readAt = null,
        updatedAt = createdAt,
        pending = true,
        deletedAt = null,
    )

    private class FakeAppNotificationDao : AppNotificationDao() {
        val rows = MutableStateFlow<Map<String, AppNotificationEntity>>(emptyMap())

        private val visibleSorted: Flow<List<AppNotificationEntity>> = rows.map { all ->
            all.values.filter { it.deletedAt == null }
                .sortedWith(compareByDescending<AppNotificationEntity> { it.createdAt }.thenByDescending { it.id })
        }

        override fun observeVisible(): Flow<List<AppNotificationEntity>> = visibleSorted

        override fun observeUnreadCount(): Flow<Int> = visibleSorted.map { list -> list.count { it.readAt == null } }

        override suspend fun insert(entity: AppNotificationEntity) {
            rows.value = rows.value + (entity.id to entity)
        }

        override suspend fun markRead(id: String, now: Long): Int =
            change({ it.id == id && it.readAt == null && it.deletedAt == null }) {
                it.copy(readAt = now, updatedAt = now, pending = true)
            }

        override suspend fun markAllRead(now: Long): Int =
            change({ it.readAt == null && it.deletedAt == null }) {
                it.copy(readAt = now, updatedAt = now, pending = true)
            }

        override suspend fun softDelete(id: String, now: Long): Int =
            change({ it.id == id && it.deletedAt == null }) {
                it.copy(deletedAt = now, updatedAt = now, pending = true)
            }

        override suspend fun softDeleteAll(now: Long): Int =
            change({ it.deletedAt == null }) { it.copy(deletedAt = now, updatedAt = now, pending = true) }

        override suspend fun trimTo(keepId: String, keepOthers: Int, now: Long): Int {
            val kept = rows.value.values.filter { it.deletedAt == null && it.id != keepId }
                .sortedWith(compareByDescending<AppNotificationEntity> { it.createdAt }.thenByDescending { it.id })
                .take(keepOthers)
                .map { it.id }
                .toSet()
            return change({ it.deletedAt == null && it.id != keepId && it.id !in kept }) {
                it.copy(deletedAt = now, updatedAt = now, pending = true)
            }
        }

        private fun change(
            matches: (AppNotificationEntity) -> Boolean,
            transform: (AppNotificationEntity) -> AppNotificationEntity,
        ): Int {
            var count = 0
            rows.value = rows.value.mapValues { (_, row) ->
                if (matches(row)) {
                    count++
                    transform(row)
                } else {
                    row
                }
            }
            return count
        }
    }
}
