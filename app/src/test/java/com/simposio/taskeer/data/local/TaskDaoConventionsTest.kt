package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    fun upsertAll_esUnaSolaSentenciaInsertReplace() {
        assertTrue(method("upsertAll").isSuspend())
        assertTrue(
            "debe ser un único @Insert(REPLACE) para ser atómico",
            Regex("@Insert\\(onConflict = OnConflictStrategy.REPLACE\\)\\s+abstract suspend fun upsertAll\\(tasks: List<TaskEntity>\\)")
                .containsMatchIn(taskDaoSource()),
        )
    }

    @Test
    fun softDeleteCompleted_esTransaccional() {
        val method = method("softDeleteCompleted")

        assertTrue(method.isSuspend())
        assertTrue(
            Regex("@Transaction\\s+open suspend fun softDeleteCompleted").containsMatchIn(taskDaoSource()),
        )
        assertTrue(taskDaoSource().contains("upsertDeletedWithSubtasks(task.copy("))
        assertTrue(querySource("findCompleted").contains("completedAt IS NOT NULL AND deletedAt IS NULL"))
        assertTrue(querySource("observeCompletedCount").contains("completedAt IS NOT NULL AND deletedAt IS NULL"))
    }

    @Test
    fun laRestauracionEsUnaTransaccionDelDao() {
        val source = taskDaoSource()
        val body = source.substring(source.indexOf("open suspend fun upsertRestoredWithSubtasks"))

        assertTrue(
            Regex("@Transaction\\s+open suspend fun upsertRestoredWithSubtasks").containsMatchIn(source),
        )
        assertTrue(body.contains("upsertAll(tasks)"))
        assertTrue(body.contains("restoreSubtasksOfTask(task.id, deletedAt, now)"))
        assertTrue(body.contains("restoreRemindersOfTask(task.id, deletedAt, now)"))
        assertTrue(method("upsertRestoredWithSubtasks").isSuspend())
    }

    @Test
    fun restaurar_aislaSubtareasYRecordatoriosDelMismoBorrado() {
        assertTrue(querySource("restoreSubtasksOfTask").contains("WHERE taskId = :taskId AND deletedAt = :deletedAt"))
        val reminders = querySource("restoreRemindersOfTask")
        assertTrue(reminders.contains("status = 'DISMISSED' AND updatedAt = :deletedAt AND remindAt > :now"))
    }

    @Test
    fun softDeleteCompleted_devuelveLosIdsBorrados() {
        assertTrue(taskDaoSource().contains("open suspend fun softDeleteCompleted(deletedAt: Long): List<String>"))
    }

    @Test
    fun sinDeleteFisico() {
        assertFalse(taskDaoSource().contains("DELETE FROM"))
        assertFalse(taskDaoSource().contains("@Delete"))
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
    fun insertTaskWithExtras_esTransaccionalYUsaLosInsertsDeHijos() {
        val source = taskDaoSource()
        val body = source.substring(source.indexOf("open suspend fun insertTaskWithExtras"))

        assertTrue(
            "la creación completa debe ejecutarse en una única transacción de Room",
            Regex("@Transaction\\s+open suspend fun insertTaskWithExtras").containsMatchIn(source),
        )
        assertTrue(body.contains("upsert(task)"))
        assertTrue(body.contains("insertSubtasks(subtasks)"))
        assertTrue(body.contains("insertReminder(it)"))
        assertTrue(method("insertTaskWithExtras").isSuspend())
    }

    @Test
    fun completeWithSuccessor_esTransaccional() {
        val source = taskDaoSource()
        val body = source.substring(source.indexOf("open suspend fun upsertAllWithSuccessor"))

        assertTrue(
            "original, renumeradas y sucesora deben ir en una única transacción de Room",
            Regex("@Transaction\\s+open suspend fun upsertAllWithSuccessor").containsMatchIn(source),
        )
        assertTrue(body.contains("upsertAll(tasks)"))
        assertTrue(body.contains("upsert(successor)"))
        assertTrue(body.contains("insertSubtasks(subtasks)"))
        assertTrue(body.contains("insertReminder(it)"))
        assertTrue(method("upsertAllWithSuccessor").isSuspend())
    }

    @Test
    fun insertTaskWithExtras_incluyeLaEntradaDeMiDia() {
        val source = taskDaoSource()
        val body = source.substring(source.indexOf("open suspend fun insertTaskWithExtras"))

        assertTrue(Regex("@Transaction\\s+open suspend fun insertTaskWithExtras").containsMatchIn(source))
        assertTrue(body.contains("myDayEntry: MyDayEntryEntity?"))
        assertTrue(body.contains("insertMyDayEntry(it)"))
        assertTrue(
            Regex(
                "@Insert\\(onConflict = OnConflictStrategy.REPLACE, entity = MyDayEntryEntity::class\\)" +
                    "\\s+abstract suspend fun insertMyDayEntry\\(entry: MyDayEntryEntity\\)",
            ).containsMatchIn(source),
        )
    }

    @Test
    fun insertsDeHijos_usanReplaceYNoBorranFisicamente() {
        val source = taskDaoSource()

        assertTrue(
            Regex(
                "@Insert\\(onConflict = OnConflictStrategy.REPLACE, entity = SubtaskEntity::class\\)" +
                    "\\s+abstract suspend fun insertSubtasks\\(subtasks: List<SubtaskEntity>\\)",
            ).containsMatchIn(source),
        )
        assertTrue(
            Regex(
                "@Insert\\(onConflict = OnConflictStrategy.REPLACE, entity = ReminderEntity::class\\)" +
                    "\\s+abstract suspend fun insertReminder\\(reminder: ReminderEntity\\)",
            ).containsMatchIn(source),
        )
        assertFalse("no debe haber borrado físico", source.contains("@Delete"))
        assertFalse("no debe haber borrado físico", source.contains("DELETE FROM"))
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

    @Test
    fun observeReminderCandidates_exigeFechaNoBorradaYNoCompletada() {
        val query = querySource("observeReminderCandidates")

        assertTrue(query.contains("tasks.dueAt IS NOT NULL"))
        assertTrue(query.contains("tasks.deletedAt IS NULL"))
        assertTrue(query.contains("tasks.completedAt IS NULL"))
        assertEquals(Flow::class.java, method("observeReminderCandidates").returnType)
    }

    @Test
    fun consultasDeCandidatosYConteo_usanCompletedAt() {
        listOf("observeReminderCandidates", "countActive").forEach { name ->
            val query = querySource(name)
            assertTrue("$name filtra por completedAt", query.contains("tasks.completedAt IS NULL"))
            assertFalse("$name ya no usa isDoneColumn", query.contains("isDoneColumn"))
            assertFalse("$name ya no necesita JOIN", query.contains("JOIN"))
        }
    }

    @Test
    fun findReminderState_completadaSaleDeCompletedAt() {
        val query = querySource("findReminderState")

        assertTrue(query.contains("tasks.completedAt IS NOT NULL AS completed"))
        assertFalse(query.contains("isDoneColumn"))
    }

    @Test
    fun observeByList_filtraPorListIdYNoBorradas() {
        val query = querySource("observeByList")

        assertTrue(query.contains("WHERE listId = :listId AND deletedAt IS NULL"))
        assertEquals(Flow::class.java, method("observeByList").returnType)
        assertFalse("observeInbox desaparece", taskDaoSource().contains("observeInbox"))
    }

    @Test
    fun observeByList_ordenaPorCreacionDescendente() {
        assertTrue(querySource("observeByList").contains("ORDER BY createdAt DESC, id ASC"))
    }

    @Test
    fun countInList_cuentaLasNoBorradasDeLaLista() {
        val query = querySource("countInList")

        assertTrue(query.contains("WHERE listId = :listId AND deletedAt IS NULL"))
        assertTrue(method("countInList").isSuspend())
        assertFalse("countInbox desaparece", taskDaoSource().contains("countInbox"))
    }

    @Test
    fun observeActiveCountsByList_cuentaPendientesNoBorradas() {
        val query = querySource("observeActiveCountsByList")

        assertTrue(query.contains("deletedAt IS NULL"))
        assertTrue("solo pendientes", query.contains("completedAt IS NULL"))
        assertTrue(query.contains("listId IS NOT NULL"))
        assertTrue(query.contains("GROUP BY listId"))
        assertEquals(Flow::class.java, method("observeActiveCountsByList").returnType)
    }

    @Test
    fun observeCountsByColumn_excluyeTareasDeLista() {
        val query = querySource("observeCountsByColumn")

        assertTrue(query.contains("columnId IS NOT NULL"))
        assertFalse(query.contains("listId"))
    }

    @Test
    fun countByColumn_noCuentaTareasDeLista() {
        val query = querySource("countByColumn")

        assertTrue(query.contains("WHERE columnId = :columnId"))
        assertFalse(query.contains("listId"))
    }

    @Test
    fun observeReminderCandidates_incluyeTareasDeLista() {
        val query = querySource("observeReminderCandidates")

        assertFalse("no depende de la ubicación", query.contains("columnId"))
        assertFalse(query.contains("listId"))
        assertTrue(query.contains("tasks.dueAt IS NOT NULL AND tasks.deletedAt IS NULL"))
    }

    @Test
    fun observeByBoard_sigueUsandoJoinInterno() {
        val query = querySource("observeByBoard")

        assertTrue(query.contains("INNER JOIN columns ON tasks.columnId = columns.id"))
        assertFalse(query.contains("LEFT JOIN"))
    }

    @Test
    fun countByColumn_filtraPorColumnaYNoCuentaBandeja() {
        val query = querySource("countByColumn")

        assertTrue(query.contains("WHERE columnId = :columnId"))
        assertFalse("no debe contar la bandeja", query.contains("IS NULL"))
    }

    @Test
    fun observeCountsByColumn_excluyeBandejaYBorradas() {
        val query = querySource("observeCountsByColumn")

        assertTrue(query.contains("deletedAt IS NULL AND columnId IS NOT NULL GROUP BY columnId"))
        assertEquals(Flow::class.java, method("observeCountsByColumn").returnType)
    }

    @Test
    fun observeCountsByColumn_noFiltraCompletadas() {
        assertFalse(querySource("observeCountsByColumn").contains("completedAt"))
    }

    @Test
    fun countActive_cuentaConYSinFechaPeroNoBorradasNiCompletadas() {
        val query = querySource("countActive")

        assertTrue(query.contains("tasks.deletedAt IS NULL"))
        assertTrue(query.contains("tasks.completedAt IS NULL"))
        assertFalse("no debe filtrar por fecha límite", query.contains("dueAt"))
        assertTrue(method("countActive").isSuspend())
    }

    @Test
    fun findReminderState_leeBorradoYCompletadaSinJoin() {
        val query = querySource("findReminderState")

        assertTrue(query.contains("tasks.deletedAt IS NOT NULL AS deleted"))
        assertTrue(method("findReminderState").isSuspend())
    }

    @Test
    fun dismissRemindersOfTask_vaDentroDeLaTransaccionDelBorrado() {
        val source = taskDaoSource()
        val body = source.substring(source.indexOf("open suspend fun upsertDeletedWithSubtasks"))

        assertTrue(body.contains("dismissRemindersOfTask(task.id, deletedAt)"))
    }

    private fun querySource(name: String): String {
        val source = taskDaoSource()
        val start = source.indexOf("fun $name(")
        assertTrue("falta $name", start >= 0)
        return source.substring(source.lastIndexOf("@Query(", start), start)
    }

    private fun taskDaoSource(): String = File(resolveLocalRoot(), "TaskDao.kt").readText()

    private fun method(name: String): Method =
        TaskDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("TaskDao no declara $name")

    private fun Method.isSuspend(): Boolean =
        parameterTypes.lastOrNull()?.name == "kotlin.coroutines.Continuation"

    private class FakeTaskDao(private val columnDao: FakeColumnDao) : TaskDao() {
        override suspend fun findActiveByList(listId: String): List<TaskEntity> = error("no se usa")

        override suspend fun restoreSubtasksOfTask(taskId: String, deletedAt: Long, now: Long) = error("no se usa")

        override suspend fun restoreRemindersOfTask(taskId: String, deletedAt: Long, now: Long): Int = error("no se usa")
        override fun observeByList(listId: String): Flow<List<TaskEntity>> = emptyFlow()

        override fun observeActiveCountsByList(): Flow<List<ListCountRow>> = emptyFlow()

        override suspend fun countInList(listId: String): Int = 0

        override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = emptyFlow()

        override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) = Unit

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

        override suspend fun upsertAll(tasks: List<TaskEntity>) {
            rows.value = rows.value + tasks.associateBy { it.id }
        }

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override fun observeCompletedCount(): Flow<Int> = emptyFlow()

        override suspend fun findCompleted(): List<TaskEntity> = emptyList()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = null

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = Unit

        override suspend fun insertReminder(reminder: ReminderEntity) = Unit
    }

    private class FakeColumnDao : ColumnDao() {
        override fun observeAllActive(): Flow<List<ColumnEntity>> = emptyFlow()

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
        completedAt = null,
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
