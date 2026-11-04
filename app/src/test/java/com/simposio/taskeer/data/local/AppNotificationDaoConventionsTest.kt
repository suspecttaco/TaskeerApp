package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNotificationDaoConventionsTest {

    private val source: String = File(resolveLocalRoot(), "AppNotificationDao.kt").readText()

    @Test
    fun dao_noBorraFisicamente() {
        assertFalse(source.contains("@Delete"))
        assertFalse(source.contains("DELETE FROM"))
    }

    @Test
    fun lecturas_devuelvenFlow() {
        assertEquals(Flow::class.java, method("observeVisible").returnType)
        assertEquals(Flow::class.java, method("observeUnreadCount").returnType)
    }

    @Test
    fun observeVisible_ordenaDeMasRecienteAAntiguaConDesempatePorId() {
        val query = querySource("observeVisible")

        assertTrue(query.contains("WHERE deletedAt IS NULL"))
        assertTrue(query.contains("ORDER BY createdAt DESC, id DESC"))
    }

    @Test
    fun observeUnreadCount_cuentaSoloVisiblesNoLeidas() {
        val query = querySource("observeUnreadCount")

        assertTrue(query.contains("COUNT(*)"))
        assertTrue(query.contains("deletedAt IS NULL AND readAt IS NULL"))
    }

    @Test
    fun markRead_esIdempotente() {
        val query = querySource("markRead")

        assertTrue(query.contains("WHERE id = :id AND readAt IS NULL AND deletedAt IS NULL"))
        assertTrue(query.contains("SET readAt = :now"))
        assertTrue(query.contains("pending = 1"))
    }

    @Test
    fun markAllRead_soloTocaLasVisiblesNoLeidas() {
        val query = querySource("markAllRead")

        assertTrue(query.contains("WHERE readAt IS NULL AND deletedAt IS NULL"))
    }

    @Test
    fun borrados_sonLogicosYMarcanPendiente() {
        listOf("softDelete", "softDeleteAll", "trimTo").forEach { name ->
            val query = querySource(name)
            assertTrue(name, query.contains("SET deletedAt = :now"))
            assertTrue(name, query.contains("pending = 1"))
            assertTrue(name, query.contains("UPDATE app_notifications"))
        }
    }

    @Test
    fun trimTo_conservaLasMasRecientes() {
        val query = querySource("trimTo")

        assertTrue(query.contains("id != :keepId AND id NOT IN (SELECT id FROM app_notifications WHERE deletedAt IS NULL"))
        assertTrue(query.contains("ORDER BY createdAt DESC, id DESC LIMIT :keepOthers"))
    }

    @Test
    fun insertAndTrim_correEnUnaTransaccion() {
        assertTrue(source.contains("@Transaction\n    open suspend fun insertAndTrim("))
        assertTrue(source.contains("trimTo(entity.id, keep - 1, now)"))
    }

    private fun querySource(name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("falta $name", start >= 0)
        val annotation = source.lastIndexOf("@Query(", start)
        return source.substring(annotation, start).replace(Regex("\"\\s*\\+\\s*\""), "")
    }

    private fun method(name: String): Method =
        AppNotificationDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("AppNotificationDao no declara $name")

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
