package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardDaoConventionsTest {

    @Test
    fun observeActive_devuelveFlow() {
        val method = method("observeActive")

        assertEquals(Flow::class.java, method.returnType)
        assertFalse(method.isSuspend())
    }

    @Test
    fun lecturasPuntualesYEscrituras_sonSuspend() {
        val findById = method("findById")
        val upsert = method("upsert")

        assertTrue("findById debe ser suspend", findById.isSuspend())
        assertTrue("upsert debe ser suspend", upsert.isSuspend())
        assertEquals(String::class.java, findById.parameterTypes.first())
        assertEquals(BoardEntity::class.java, upsert.parameterTypes.first())
    }

    @Test
    fun upsert_usaConflictoReplace() {
        val source = boardDaoSource()

        assertTrue("upsert debe usar OnConflictStrategy.REPLACE", source.contains("OnConflictStrategy.REPLACE"))
    }

    @Test
    fun observeActive_excluyeLasFilasBorradas() {
        assertTrue("la lectura observable debe filtrar deletedAt IS NULL", boardDaoSource().contains("deletedAt IS NULL"))
    }

    private fun boardDaoSource(): String = File(resolveLocalRoot(), "BoardDao.kt").readText()

    private fun method(name: String): Method =
        BoardDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("BoardDao no declara $name")

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
