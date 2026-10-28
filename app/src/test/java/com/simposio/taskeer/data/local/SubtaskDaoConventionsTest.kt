package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskDaoConventionsTest {

    @Test
    fun observeByTask_devuelveFlow() {
        val method = method("observeByTask")

        assertEquals(Flow::class.java, method.returnType)
        assertFalse(method.parameterTypes.last().name == "kotlin.coroutines.Continuation")
        assertEquals(String::class.java, method.parameterTypes.first())
    }

    @Test
    fun observeByTask_filtraBorradasYOrdenaPorPositionEstable() {
        val source = daoSource()

        assertTrue(
            "debe filtrar por tarea y excluir las borradas",
            source.contains("WHERE taskId = :taskId AND deletedAt IS NULL"),
        )
        assertTrue(
            "debe ordenar por position con desempate por id",
            source.contains("ORDER BY position ASC, id ASC"),
        )
    }

    @Test
    fun subtaskDao_noBorraFisicamente() {
        val source = daoSource()

        assertFalse(source.contains("@Delete"))
        assertFalse(source.contains("DELETE FROM"))
        assertTrue(source.contains("OnConflictStrategy.REPLACE"))
    }

    private fun daoSource(): String = File(resolveLocalRoot(), "SubtaskDao.kt").readText()

    private fun method(name: String): Method =
        SubtaskDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("SubtaskDao no declara $name")

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
