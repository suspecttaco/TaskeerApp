package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnDaoConventionsTest {

    @Test
    fun observeByBoard_devuelveFlow() {
        val method = method("observeByBoard")

        assertEquals(Flow::class.java, method.returnType)
        assertFalse(method.isSuspend())
        assertEquals(String::class.java, method.parameterTypes.first())
    }

    @Test
    fun observeByBoard_filtraTableroYBorradas() {
        val source = columnDaoSource()

        assertTrue("debe filtrar por tablero", source.contains("boardId = :boardId"))
        assertTrue("debe excluir las filas borradas", source.contains("deletedAt IS NULL"))
    }

    @Test
    fun observeAllActive_filtraBorradasYOrdena() {
        val query = columnDaoSource()

        assertTrue(query.contains("WHERE deletedAt IS NULL ORDER BY boardId ASC, position ASC, id ASC"))
        assertEquals(Flow::class.java, method("observeAllActive").returnType)
    }

    @Test
    fun observeByBoard_ordenaPorPositionEstable() {
        assertTrue(
            "debe ordenar por position con desempate por id",
            columnDaoSource().contains("ORDER BY position ASC, id ASC"),
        )
    }

    @Test
    fun lecturasYEscrituras_sonSuspendYUsanReplace() {
        val source = columnDaoSource()

        listOf("findActiveByBoard", "findById", "upsert", "upsertAll").forEach { name ->
            assertTrue("$name debe ser suspend", method(name).isSuspend())
        }
        assertTrue("las escrituras deben usar OnConflictStrategy.REPLACE", source.contains("OnConflictStrategy.REPLACE"))
    }

    @Test
    fun insertBoardWithSeed_esTransaccionalEInvocaAmbasEscrituras() = runTest {
        assertTrue("la siembra debe ser transaccional", columnDaoSource().contains("@Transaction"))

        val dao = RecordingColumnDao()
        val board = BoardEntity(
            id = "tablero-1",
            name = "Tablero",
            createdAt = 1L,
            updatedAt = 1L,
            pending = true,
            deletedAt = null,
        )
        val columns = listOf(
            ColumnEntity(
                id = "columna-1",
                boardId = "tablero-1",
                name = "Por hacer",
                position = 0,
                wipLimit = null,
                isDoneColumn = false,
                createdAt = 0L,
                updatedAt = 1L,
                pending = true,
                deletedAt = null,
            ),
        )

        dao.insertBoardWithSeed(board, columns)

        assertEquals(listOf(board), dao.insertedBoards)
        assertEquals(listOf(columns), dao.insertedBatches)
    }

    private fun columnDaoSource(): String = File(resolveLocalRoot(), "ColumnDao.kt").readText()

    private fun method(name: String): Method =
        ColumnDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("ColumnDao no declara $name")

    private fun Method.isSuspend(): Boolean =
        parameterTypes.lastOrNull()?.name == "kotlin.coroutines.Continuation"

    private class RecordingColumnDao : ColumnDao() {
        override fun observeAllActive(): Flow<List<ColumnEntity>> = emptyFlow()

        val insertedBoards = mutableListOf<BoardEntity>()
        val insertedBatches = mutableListOf<List<ColumnEntity>>()

        override fun observeByBoard(boardId: String): Flow<List<ColumnEntity>> = MutableStateFlow(emptyList())

        override suspend fun findActiveByBoard(boardId: String): List<ColumnEntity> = emptyList()

        override suspend fun findById(id: String): ColumnEntity? = null

        override suspend fun upsert(column: ColumnEntity) = Unit

        override suspend fun upsertAll(columns: List<ColumnEntity>) {
            insertedBatches += columns
        }

        override suspend fun insertBoard(board: BoardEntity) {
            insertedBoards += board
        }
    }

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
