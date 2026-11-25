package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardDao
import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.SearchTaskRow
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.search.SearchIndex
import com.simposio.taskeer.domain.search.search
import com.simposio.taskeer.testing.FakeTaskListDao
import com.simposio.taskeer.testing.UnusedColumnDao
import com.simposio.taskeer.testing.UnusedTaskDao
import com.simposio.taskeer.testing.listEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryImplTest {

    private val store = Store()
    private val repository = SearchRepositoryImpl(store.boardDao, store.columnDao, store.taskDao, store.listDao)

    private suspend fun taskIds(query: String): List<String> =
        search(query, repository.observeIndex().first()).tasks.map { it.id }

    @Test
    fun incluyeTareasDeTablerosListasYBandeja() = runTest {
        store.board("b1", "Casa")
        store.column("c1", "b1", "Por hacer")
        store.task("t-tablero", "informe", columnId = "c1")
        store.task("t-lista", "informe", listId = "l1", listName = "Compras")
        store.task("t-bandeja", "informe", listId = "l0", listName = "Tareas")

        assertEquals(listOf("t-bandeja", "t-lista", "t-tablero"), taskIds("informe"))
    }

    @Test
    fun incluyeCompletadas() = runTest {
        store.task("t1", "informe", listId = "l0", listName = "Tareas", completedAt = 5L)

        assertEquals(listOf("t1"), taskIds("informe"))
    }

    @Test
    fun excluyeTableroBorrado() = runTest {
        store.board("b1", "Casa", deletedAt = 9L)

        assertTrue(repository.observeIndex().first().boards.isEmpty())
    }

    @Test
    fun excluyeColumnasDeTableroBorrado() = runTest {
        store.board("b1", "Casa", deletedAt = 9L)
        store.column("c1", "b1", "Por hacer")

        assertTrue(search("hacer", repository.observeIndex().first()).isEmpty)
    }

    @Test
    fun excluyeColumnaBorrada() = runTest {
        store.board("b1", "Casa")
        store.column("c1", "b1", "Por hacer", deletedAt = 9L)

        assertTrue(repository.observeIndex().first().columns.isEmpty())
    }

    @Test
    fun excluyeTareaBorrada() = runTest {
        store.task("t1", "informe", listId = "l0", listName = "Tareas", deletedAt = 9L)

        assertTrue(taskIds("informe").isEmpty())
    }

    @Test
    fun excluyeTareasDeColumnaOTableroBorrados() = runTest {
        store.board("b1", "Casa")
        store.board("b2", "Viejo", deletedAt = 9L)
        store.column("c1", "b1", "Borrada", deletedAt = 9L)
        store.column("c2", "b2", "Activa")
        store.task("t1", "informe", columnId = "c1")
        store.task("t2", "informe", columnId = "c2")

        assertTrue(taskIds("informe").isEmpty())
    }

    @Test
    fun cruzaVariosTableros() = runTest {
        store.board("b1", "Casa")
        store.board("b2", "Trabajo")
        store.column("c1", "b1", "Por hacer")
        store.column("c2", "b2", "Por hacer")
        store.task("t1", "informe", columnId = "c1")
        store.task("t2", "informe", columnId = "c2")

        val results = search("hacer", repository.observeIndex().first())

        assertEquals(listOf("c1", "c2"), results.columns.map { it.id })
        assertEquals(listOf("t1", "t2"), taskIds("informe"))
    }

    @Test
    fun seReemiteAlCambiarLosDatos() = runTest {
        store.board("b1", "Casa")
        val first = repository.observeIndex().first()

        store.board("b2", "Trabajo")
        val second: SearchIndex = repository.observeIndex().first()

        assertEquals(listOf("b1"), first.boards.map { it.id })
        assertEquals(listOf("b1", "b2"), second.boards.map { it.id })
    }

    @Test
    fun incluyeListasActivasYLaSembrada() = runTest {
        store.listDao.put(listEntity("l1", "Compras", position = 1))

        val lists = repository.observeIndex().first().lists

        assertEquals(listOf(SEEDED_TASK_LIST_ID, "l1"), lists.map { it.id })
        assertEquals(listOf("Tareas", "Compras"), lists.map { it.name })
    }

    @Test
    fun excluyeListaBorrada() = runTest {
        store.listDao.put(listEntity("l1", "Compras", deletedAt = 9L))

        assertEquals(listOf(SEEDED_TASK_LIST_ID), repository.observeIndex().first().lists.map { it.id })
    }

    @Test
    fun seReemiteAlRenombrarUnaLista() = runTest {
        store.listDao.put(listEntity("l1", "Compras"))
        val first = repository.observeIndex().first().lists.single { it.id == "l1" }

        store.listDao.put(listEntity("l1", "Mercado"))
        val second = repository.observeIndex().first().lists.single { it.id == "l1" }

        assertEquals("Compras", first.name)
        assertEquals("Mercado", second.name)
    }

    // Reproduce la visibilidad de las consultas Room (deletedAt IS NULL y contenedores existentes y activos).
    private class Store {
        private val boards = MutableStateFlow<List<BoardEntity>>(emptyList())
        private val columns = MutableStateFlow<List<ColumnEntity>>(emptyList())
        private val tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
        private val listNames = mutableMapOf<String, String>()

        fun board(id: String, name: String, deletedAt: Long? = null) {
            boards.value = boards.value + BoardEntity(id, name, 0L, 0L, false, deletedAt)
        }

        fun column(id: String, boardId: String, name: String, deletedAt: Long? = null) {
            columns.value = columns.value + ColumnEntity(id, boardId, name, 0, null, false, 0L, 0L, false, deletedAt)
        }

        fun task(
            id: String,
            title: String,
            columnId: String? = null,
            listId: String? = null,
            listName: String? = null,
            completedAt: Long? = null,
            deletedAt: Long? = null,
        ) {
            listId?.let { listNames[it] = listName.orEmpty() }
            tasks.value = tasks.value + TaskEntity(
                id = id,
                columnId = columnId,
                listId = listId,
                title = title,
                description = null,
                priority = "MEDIUM",
                dueAt = null,
                assigneeId = null,
                position = 0,
                createdAt = 0L,
                updatedAt = 0L,
                pending = false,
                completedAt = completedAt,
                deletedAt = deletedAt,
            )
        }

        val listDao = FakeTaskListDao()

        val boardDao = object : BoardDao {
            override fun observeActive(): Flow<List<BoardEntity>> =
                boards.map { all -> all.filter { it.deletedAt == null } }

            override suspend fun findById(id: String): BoardEntity? = error("no se usa")

            override suspend fun upsert(board: BoardEntity) = error("no se usa")
        }

        val columnDao = object : UnusedColumnDao() {
            override fun observeAllActive(): Flow<List<ColumnEntity>> =
                columns.map { all -> all.filter { it.deletedAt == null } }
        }

        val taskDao = object : UnusedTaskDao() {
            override fun observeSearchable(): Flow<List<SearchTaskRow>> =
                combine(tasks, columns, boards) { allTasks, allColumns, allBoards ->
                    allTasks.filter { it.deletedAt == null }.mapNotNull { task ->
                        val column = allColumns.firstOrNull { it.id == task.columnId }
                        val board = column?.let { c -> allBoards.firstOrNull { it.id == c.boardId } }
                        val visible = task.columnId == null ||
                            (column != null && column.deletedAt == null && board != null && board.deletedAt == null)
                        if (!visible) return@mapNotNull null
                        SearchTaskRow(task, board?.id, board?.name, column?.name, task.listId?.let(listNames::get))
                    }.sortedBy { it.task.id }
                }
        }
    }
}
