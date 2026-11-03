package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDaoConventionsTest {

    @Test
    fun observeByBoard_devuelveFlow() {
        val method = method("observeByBoard")

        assertEquals(Flow::class.java, method.returnType)
        assertFalse(method.isSuspend())
        assertEquals(String::class.java, method.parameterTypes.first())
    }

    @Test
    fun observeByBoard_filtraBorradasYColumnasBorradas() {
        val source = taskDaoSource()

        assertTrue("debe unir con las columnas del tablero", source.contains("INNER JOIN columns ON tasks.columnId = columns.id"))
        assertTrue("debe filtrar por tablero", source.contains("columns.boardId = :boardId"))
        assertTrue("debe excluir las columnas borradas", source.contains("columns.deletedAt IS NULL"))
        assertTrue("debe excluir las tareas borradas", source.contains("tasks.deletedAt IS NULL"))
    }

    @Test
    fun observeByBoard_ordenaPorPositionEstable() {
        assertTrue(
            "debe ordenar por position con desempate por id",
            taskDaoSource().contains("ORDER BY tasks.position ASC, tasks.id ASC"),
        )
    }

    @Test
    fun countByColumn_cuentaTambienLasBorradas() {
        val source = taskDaoSource()
        val countQuery = Regex("SELECT COUNT\\(\\*\\) FROM tasks WHERE columnId = :columnId").find(source)

        assertTrue("debe contar las tareas de la columna", countQuery != null)
        assertFalse(
            "el conteo no debe filtrar deletedAt: las borradas lógicas también cuentan",
            countQuery?.value?.contains("deletedAt") == true,
        )
    }

    @Test
    fun lecturasYEscrituras_sonSuspendYUsanReplace() {
        val source = taskDaoSource()

        listOf("findActiveByColumn", "findById", "countByColumn", "upsert").forEach { name ->
            assertTrue("$name debe ser suspend", method(name).isSuspend())
        }
        assertTrue("las escrituras deben usar OnConflictStrategy.REPLACE", source.contains("OnConflictStrategy.REPLACE"))
        assertFalse("no debe haber borrado físico", source.contains("@Delete"))
        assertFalse("no debe haber borrado físico", source.contains("DELETE FROM"))
    }

    @Test
    fun findActiveByColumn_filtraLasBorradasYOrdena() {
        val source = taskDaoSource()

        assertTrue(
            "debe excluir las tareas borradas lógicamente",
            source.contains("WHERE columnId = :columnId AND deletedAt IS NULL ORDER BY position ASC, id ASC"),
        )
    }

    @Test
    fun upsertDeletedWithSubtasks_esTransaccional() {
        val method = method("upsertDeletedWithSubtasks")

        assertTrue(
            "la cascada debe ejecutarse en una única transacción de Room",
            Regex("@Transaction\\s+open suspend fun upsertDeletedWithSubtasks").containsMatchIn(taskDaoSource()),
        )
        assertTrue(method.isSuspend())
    }

    @Test
    fun softDeleteSubtasksOfTask_soloActualizaLasActivasDeLaTarea() {
        val source = taskDaoSource()

        assertTrue(
            "debe filtrar por tarea y por subtareas activas",
            source.contains("WHERE taskId = :taskId AND deletedAt IS NULL"),
        )
        assertTrue(
            "debe marcar la subtarea como pendiente de sincronizar",
            source.contains("UPDATE subtasks SET deletedAt = :deletedAt, updatedAt = :deletedAt, pending = 1"),
        )
    }

    @Test
    fun elDaoExponeLasTareasDeLasColumnasDelTablero() = runTest {
        val columnDao = FakeColumnDao().apply {
            put(columnEntity("columna-1", boardId = "tablero-1"))
            put(columnEntity("columna-2", boardId = "tablero-2"))
            put(columnEntity("columna-borrada", boardId = "tablero-1", deleted = true))
        }
        val dao = FakeTaskDao(columnDao)
        dao.put(taskEntity("t1", columnId = "columna-1", position = 1))
        dao.put(taskEntity("t2", columnId = "columna-1", position = 0))
        dao.put(taskEntity("t3", columnId = "columna-2", position = 0))
        dao.put(taskEntity("t4", columnId = "columna-borrada", position = 0))

        val rows = dao.observeByBoard("tablero-1").first()

        assertEquals(listOf("t2", "t1"), rows.map { it.id })
        assertEquals(2, dao.countByColumn("columna-1"))
    }

    private fun taskDaoSource(): String = File(resolveLocalRoot(), "TaskDao.kt").readText()

    private fun method(name: String): Method =
        TaskDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("TaskDao no declara $name")

    private fun Method.isSuspend(): Boolean =
        parameterTypes.lastOrNull()?.name == "kotlin.coroutines.Continuation"

    private class FakeTaskDao(private val columnDao: FakeColumnDao) : TaskDao() {
        private val rows = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

        fun put(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = rows.map { stored ->
            val activeColumns = columnDao.activeColumnIds(boardId)
            stored.values
                .filter { it.columnId in activeColumns && it.deletedAt == null }
                .sortedWith(compareBy({ it.position }, { it.id }))
        }

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> =
            rows.value.values
                .filter { it.columnId == columnId && it.deletedAt == null }
                .sortedWith(compareBy({ it.position }, { it.id }))

        override suspend fun findById(id: String): TaskEntity? = rows.value[id]

        override suspend fun countByColumn(columnId: String): Int =
            rows.value.values.count { it.columnId == columnId }

        override suspend fun upsert(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit
    }

    private class FakeColumnDao : ColumnDao() {
        private val rows = MutableStateFlow<Map<String, ColumnEntity>>(emptyMap())

        fun put(column: ColumnEntity) {
            rows.value = rows.value + (column.id to column)
        }

        fun activeColumnIds(boardId: String): Set<String> =
            rows.value.values
                .filter { it.boardId == boardId && it.deletedAt == null }
                .map { it.id }
                .toSet()

        override fun observeByBoard(boardId: String): Flow<List<ColumnEntity>> = rows.map { stored ->
            stored.values.filter { it.boardId == boardId && it.deletedAt == null }
        }

        override suspend fun findActiveByBoard(boardId: String): List<ColumnEntity> =
            rows.value.values.filter { it.boardId == boardId && it.deletedAt == null }

        override suspend fun findById(id: String): ColumnEntity? = rows.value[id]

        override suspend fun upsert(column: ColumnEntity) {
            rows.value = rows.value + (column.id to column)
        }

        override suspend fun upsertAll(columns: List<ColumnEntity>) {
            rows.value = rows.value + columns.associateBy { it.id }
        }

        override suspend fun insertBoard(board: BoardEntity) = Unit
    }

    private fun columnEntity(id: String, boardId: String, deleted: Boolean = false): ColumnEntity = ColumnEntity(
        id = id,
        boardId = boardId,
        name = "Columna $id",
        position = 0,
        wipLimit = null,
        isDoneColumn = false,
        createdAt = 0L,
        updatedAt = 0L,
        pending = true,
        deletedAt = if (deleted) 1L else null,
    )

    private fun taskEntity(
        id: String,
        columnId: String,
        position: Int,
        deletedAt: Long? = null,
    ): TaskEntity = TaskEntity(
        id = id,
        columnId = columnId,
        title = "Tarea $id",
        description = null,
        priority = "MEDIUM",
        dueAt = null,
        assigneeId = null,
        position = position,
        createdAt = 0L,
        updatedAt = 0L,
        pending = true,
        deletedAt = deletedAt,
    )

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
