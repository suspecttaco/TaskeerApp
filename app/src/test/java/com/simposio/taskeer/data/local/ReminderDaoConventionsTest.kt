package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDaoConventionsTest {

    private val source: String = File(resolveLocalRoot(), "ReminderDao.kt").readText()

    @Test
    fun observePendingByTask_devuelveFlow() {
        assertEquals(Flow::class.java, method("observePendingByTask").returnType)
    }

    @Test
    fun markSent_soloCambiaFilasPendientes() {
        val query = querySource("markSent")

        assertTrue(query.contains("WHERE id = :id AND status = 'PENDING'"))
        assertTrue(query.contains("SET status = 'SENT'"))
        assertTrue(query.contains("pending = 1"))
    }

    @Test
    fun markDismissed_soloCambiaFilasPendientes() {
        val query = querySource("markDismissed")

        assertTrue(query.contains("WHERE id = :id AND status = 'PENDING'"))
        assertTrue(query.contains("SET status = 'DISMISSED'"))
        assertTrue(query.contains("pending = 1"))
    }

    @Test
    fun consultasDePendientes_filtranPorEstado() {
        listOf(
            "observePendingByTask",
            "observeAllPending",
            "findPendingByTask",
            "findPendingUpTo",
            "minPendingAfter",
        ).forEach {
            assertTrue(it, querySource(it).contains("status = 'PENDING'"))
        }
    }

    @Test
    fun findPendingUpTo_filtraPorLimiteYOrdenaConDesempatePorId() {
        val query = querySource("findPendingUpTo")

        assertTrue(query.contains("remindAt <= :limit"))
        assertTrue(query.contains("ORDER BY remindAt ASC, id ASC"))
    }

    @Test
    fun minPendingAfter_buscaElMinimoEstrictamentePosterior() {
        val query = querySource("minPendingAfter")

        assertTrue(query.contains("MIN(remindAt)"))
        assertTrue(query.contains("remindAt > :after"))
    }

    @Test
    fun observeAllPending_devuelveFlow() {
        assertEquals(Flow::class.java, method("observeAllPending").returnType)
    }

    @Test
    fun findAllPending_yaNoExiste() {
        assertTrue(ReminderDao::class.java.methods.none { it.name == "findAllPending" })
    }

    @Test
    fun reminderDao_noBorraFisicamente() {
        assertFalse(source.contains("@Delete"))
        assertFalse(source.contains("DELETE FROM"))
        assertTrue(source.contains("OnConflictStrategy.REPLACE"))
    }

    @Test
    fun taskDao_descartaRecordatoriosPendientesDeLaTarea() {
        val taskSource = File(resolveLocalRoot(), "TaskDao.kt").readText()

        assertTrue(taskSource.contains("UPDATE reminders SET status = 'DISMISSED'"))
        assertTrue(taskSource.contains("WHERE taskId = :taskId AND status = 'PENDING'"))
        assertTrue(taskSource.contains("dismissRemindersOfTask(task.id, deletedAt)"))
    }

    private fun querySource(name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("falta $name", start >= 0)
        val annotation = source.lastIndexOf("@Query(", start)
        return source.substring(annotation, start)
    }

    private fun method(name: String): Method =
        ReminderDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("ReminderDao no declara $name")

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
