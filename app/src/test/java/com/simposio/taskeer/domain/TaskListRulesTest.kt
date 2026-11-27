package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.list.LIST_LAST_PROTECTED
import com.simposio.taskeer.domain.list.LIST_NAME_REQUIRED
import com.simposio.taskeer.domain.list.LIST_NAME_TAKEN
import com.simposio.taskeer.domain.list.LIST_NAME_TOO_LONG
import com.simposio.taskeer.domain.list.checkNameAvailable
import com.simposio.taskeer.domain.list.checkNotLast
import com.simposio.taskeer.domain.list.defaultListOf
import com.simposio.taskeer.domain.list.movedToDefault
import com.simposio.taskeer.domain.list.nextListPosition
import com.simposio.taskeer.domain.list.newTaskList
import com.simposio.taskeer.domain.list.renamed
import com.simposio.taskeer.domain.list.softDeleted
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListRulesTest {

    private val now = Instant.parse("2026-11-17T10:00:00Z")
    private val later = now.plusSeconds(60)

    private fun list(
        id: String,
        name: String,
        position: Int = 0,
        deleted: Boolean = false,
        createdAt: Instant = now,
    ) = TaskList(
        name = name,
        position = position,
        createdAt = createdAt,
        sync = SyncMetadata(id, now, pending = false, deletedAt = if (deleted) now else null),
    )

    private fun resolveMainRoot(): java.io.File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = java.io.File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { java.io.File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }

    private fun failureOf(result: DomainResult<TaskList>): DomainError = (result as DomainResult.Failure).error

    private fun task(id: String, position: Int, listId: String? = "lista-1") = Task(
        columnId = null,
        listId = listId,
        title = id,
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = null,
        assigneeId = null,
        position = position,
        createdAt = now,
        completedAt = null,
        sync = SyncMetadata(id, now, pending = false, deletedAt = null),
    )

    @Test
    fun taskList_exponeIdYEstadoDesdeLosMetadatos() {
        val deleted = list("lista-1", "Compras", deleted = true)

        assertEquals("lista-1", deleted.id)
        assertTrue(deleted.isDeleted)
    }

    @Test
    fun newTaskList_nombreVacio_fallaRequired() {
        val error = failureOf(newTaskList("", 1, now))

        assertTrue(error is DomainError.Validation)
        assertEquals(LIST_NAME_REQUIRED, error.code)
    }

    @Test
    fun newTaskList_nombreSoloEspacios_fallaRequired() {
        assertEquals(LIST_NAME_REQUIRED, failureOf(newTaskList("   ", 1, now)).code)
    }

    @Test
    fun newTaskList_nombreDe101_fallaTooLong() {
        val error = failureOf(newTaskList("a".repeat(101), 1, now))

        assertTrue(error is DomainError.Validation)
        assertEquals(LIST_NAME_TOO_LONG, error.code)
    }

    @Test
    fun newTaskList_nombreDe100_esValido() {
        val created = (newTaskList("a".repeat(100), 1, now) as DomainResult.Success).value

        assertEquals(100, created.name.length)
    }

    @Test
    fun newTaskList_recortaEspacios() {
        val created = (newTaskList("  Compras  ", 3, now, clientId = "lista-1") as DomainResult.Success).value

        assertEquals("Compras", created.name)
        assertEquals(3, created.position)
        assertEquals(now, created.createdAt)
        assertEquals("lista-1", created.id)
        assertTrue(created.sync.pending)
        assertNull(created.sync.deletedAt)
    }

    @Test
    fun renamed_aplicaLaMismaValidacion() {
        val current = list("lista-1", "Compras")

        assertEquals(LIST_NAME_REQUIRED, failureOf(current.renamed("  ", later)).code)
        assertEquals(LIST_NAME_TOO_LONG, failureOf(current.renamed("a".repeat(101), later)).code)
    }

    @Test
    fun renamed_actualizaNombreYMarcaPendiente() {
        val renamed = (list("lista-1", "Compras").renamed("  Super ", later) as DomainResult.Success).value

        assertEquals("Super", renamed.name)
        assertEquals(later, renamed.sync.updatedAt)
        assertTrue(renamed.sync.pending)
    }

    @Test
    fun softDeleted_fijaDeletedAtYMarcaPendiente() {
        val deleted = list("lista-1", "Compras").softDeleted(later)

        assertEquals(later, deleted.sync.deletedAt)
        assertTrue(deleted.sync.pending)
    }

    @Test
    fun checkNameAvailable_duplicadoExacto_devuelveConflict() {
        val error = checkNameAvailable("Compras", listOf(list("a", "Compras")))

        assertTrue(error is DomainError.Conflict)
        assertEquals(LIST_NAME_TAKEN, error?.code)
    }

    @Test
    fun checkNameAvailable_mayusculasYEspacios_cuentanComoDuplicado() {
        assertEquals(LIST_NAME_TAKEN, checkNameAvailable("  compras ", listOf(list("a", "COMPRAS")))?.code)
    }

    @Test
    fun checkNameAvailable_laPropiaListaAlRenombrar_noCuenta() {
        assertNull(checkNameAvailable("Compras", listOf(list("a", "Compras")), ignoringId = "a"))
    }

    @Test
    fun checkNameAvailable_listaBorrada_noReservaElNombre() {
        assertNull(checkNameAvailable("Compras", listOf(list("a", "Compras", deleted = true))))
    }

    @Test
    fun checkNameAvailable_tareas_estaReservado() {
        val tareas = list("tareas-id", "Tareas")

        assertEquals(LIST_NAME_TAKEN, checkNameAvailable("tareas", listOf(tareas))?.code)
    }

    @Test
    fun seededTaskListId_esElUuidDeterminista() {
        val derived = UUID.nameUUIDFromBytes("taskeer:list:default".toByteArray(Charsets.UTF_8)).toString()

        assertEquals(derived, SEEDED_TASK_LIST_ID)
        assertEquals("e1882473-b35a-3d7f-a6fa-43e4f940dc11", SEEDED_TASK_LIST_ID)
    }

    @Test
    fun seededTaskListId_soloSeUsaEnLaSiembra() {
        val usages = resolveMainRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "TaskListRules.kt" }
            .flatMap { file -> file.readLines().filter { it.contains("SEEDED_TASK_LIST_ID") }.map { file.name to it } }
            .toList()

        // El motor de sincronización la reemplaza por el id derivado del usuario al vincular y la resiembra al vaciar.
        val syncFiles = setOf("SyncSessionGate.kt", "LocalDataWiper.kt")
        assertTrue("fuera de la siembra solo puede citarse en comentarios: $usages", usages.all { (file, line) ->
            (file == "AppDatabase.kt" && line.trimStart().startsWith("//")) || file in syncFiles
        })
    }

    @Test
    fun defaultListOf_menorPosition() {
        val lists = listOf(list("b", "B", position = 2), list("a", "A", position = 1), list("c", "C", position = 3))

        assertEquals("a", defaultListOf(lists)?.id)
    }

    @Test
    fun defaultListOf_empateDePosition_desempataPorCreatedAtYId() {
        val old = list("z", "Z", position = 1, createdAt = now)
        val newer = list("a", "A", position = 1, createdAt = later)
        val sameInstantA = list("a", "A", position = 1, createdAt = now)

        assertEquals("z", defaultListOf(listOf(newer, old))?.id)
        assertEquals("a", defaultListOf(listOf(old, sameInstantA))?.id)
    }

    @Test
    fun defaultListOf_ignoraBorradas() {
        val lists = listOf(list("a", "A", position = 0, deleted = true), list("b", "B", position = 5))

        assertEquals("b", defaultListOf(lists)?.id)
    }

    @Test
    fun defaultListOf_unaSola_esEsa() {
        assertEquals("b", defaultListOf(listOf(list("b", "B", position = 9)))?.id)
    }

    @Test
    fun defaultListOf_sinListas_devuelveNull() {
        assertNull(defaultListOf(emptyList()))
        assertNull(defaultListOf(listOf(list("a", "A", deleted = true))))
    }

    @Test
    fun nextListPosition_maxMasUno() {
        assertEquals(0, nextListPosition(emptyList()))
        assertEquals(6, nextListPosition(listOf(list("a", "A", position = 0), list("b", "B", position = 5))))
        assertEquals(1, nextListPosition(listOf(list("a", "A", position = 0), list("x", "X", position = 9, deleted = true))))
    }

    @Test
    fun checkNotLast_unaSola_esConflict() {
        val only = list("a", "A")

        val error = checkNotLast(listOf(only), "a")

        assertTrue(error is DomainError.Conflict)
        assertEquals(LIST_LAST_PROTECTED, error?.code)
        assertEquals(LIST_LAST_PROTECTED, checkNotLast(listOf(only, list("b", "B", deleted = true)), "a")?.code)
    }

    @Test
    fun checkNotLast_conOtraActiva_esNull() {
        assertNull(checkNotLast(listOf(list("a", "A"), list("b", "B")), "a"))
    }

    @Test
    fun movedToDefault_asignaPosicionesAContinuacion() {
        val moved = movedToDefault(listOf(task("b", 5), task("a", 2)), "destino", startPosition = 3, now = later)

        assertEquals(listOf("a", "b"), moved.map { it.id })
        assertEquals(listOf(3, 4), moved.map { it.position })
        assertEquals(listOf("destino", "destino"), moved.map { it.listId })
        assertTrue(moved.all { it.sync.pending && it.sync.updatedAt == later })
    }
}
