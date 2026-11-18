package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListDaoConventionsTest {

    private val source: String = File(resolveLocalRoot(), "TaskListDao.kt").readText()

    @Test
    fun observeActive_filtraBorradasYOrdenaPorPositionCreatedAtId() {
        val query = querySource("observeActive")

        assertTrue(query.contains("deletedAt IS NULL"))
        assertTrue(query.contains("ORDER BY position ASC, createdAt ASC, id ASC"))
        assertFalse("la por defecto no tiene trato especial en la consulta", query.contains("defaultId"))
        assertEquals(Flow::class.java, method("observeActive").returnType)
        assertFalse(method("observeActive").isSuspend())
    }

    @Test
    fun softDeleteMovingTasks_esTransaccional() {
        val body = source.substring(source.indexOf("open suspend fun softDeleteMovingTasks"))

        assertTrue(source.substring(0, source.indexOf("open suspend fun softDeleteMovingTasks")).trimEnd().endsWith("@Transaction"))
        assertTrue(body.contains("upsert(deleted)"))
        assertTrue(body.contains("upsertTasks(moved)"))
    }

    @Test
    fun sinDeleteFisico() {
        assertFalse(source.contains("@Delete"))
        assertFalse(source.contains("DELETE FROM"))
    }

    @Test
    fun lecturasYEscrituras_sonSuspendYUsanReplace() {
        listOf("findActive", "findById", "upsert", "findActiveTasks", "countTasks", "upsertTasks").forEach {
            assertTrue("$it debe ser suspend", method(it).isSuspend())
        }
        assertEquals(2, Regex("OnConflictStrategy.REPLACE").findAll(source).count())
        assertEquals(TaskListEntity::class.java, method("upsert").parameterTypes.first())
    }

    private fun querySource(name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("falta $name", start >= 0)
        return source.substring(source.lastIndexOf("@Query(", start), start)
    }

    private fun method(name: String): Method =
        TaskListDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("TaskListDao no declara $name")

    private fun Method.isSuspend(): Boolean =
        parameterTypes.lastOrNull()?.name == "kotlin.coroutines.Continuation"

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
