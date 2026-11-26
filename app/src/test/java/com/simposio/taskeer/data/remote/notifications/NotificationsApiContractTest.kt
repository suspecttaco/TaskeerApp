package com.simposio.taskeer.data.remote.notifications

import com.simposio.taskeer.domain.notification.ServerNotificationType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsApiContractTest {

    private fun entry(
        id: String = "11111111-1111-4111-8111-111111111111",
        type: String = "TASK_ASSIGNED",
        title: String = "\"Tarea nueva\"",
        body: String = "\"Ana te asignó una tarea\"",
        taskId: String = "null",
        readAt: String = "null",
        createdAt: String = "\"2026-11-26T10:00:00.000Z\"",
    ) = """{"id":"$id","userId":"u","type":"$type","title":$title,"body":$body,"taskId":$taskId,"readAt":$readAt,"createdAt":$createdAt}"""

    private fun list(vararg entries: String) = """{"notifications":[${entries.joinToString(",")}]}"""

    @Test
    fun parseList_valida_devuelveTodas() {
        val result = NotificationsApiContract.parseList(
            list(
                entry(),
                entry(id = "22222222-2222-4222-8222-222222222222", readAt = "\"2026-11-26T11:00:00Z\"", taskId = "\"t1\""),
            ),
        )

        assertEquals(2, result?.size)
        assertEquals(ServerNotificationType.TASK_ASSIGNED, result?.first()?.type)
        assertEquals(Instant.parse("2026-11-26T11:00:00Z"), result?.get(1)?.readAt)
        assertEquals("t1", result?.get(1)?.taskId)
    }

    @Test
    fun parseList_vacia_devuelveListaVacia() {
        assertEquals(emptyList<Any>(), NotificationsApiContract.parseList(list()))
    }

    @Test
    fun parseList_entradaRotaEntreBuenas_laDescarta() {
        val result = NotificationsApiContract.parseList(
            list(entry(), """{"id":"x"}""", "7", entry(id = "33333333-3333-4333-8333-333333333333", title = "null")),
        )

        assertEquals(1, result?.size)
    }

    @Test
    fun parseList_tipoDesconocido_esUnknown() {
        val result = NotificationsApiContract.parseList(list(entry(type = "BILLING")))

        assertEquals(ServerNotificationType.UNKNOWN, result?.single()?.type)
    }

    @Test
    fun parseList_bodyYTaskIdNulos_sonValidos() {
        val result = NotificationsApiContract.parseList(list(entry(body = "null")))

        assertNull(result?.single()?.body)
        assertNull(result?.single()?.taskId)
    }

    @Test
    fun parseList_readAtMalFormado_descartaLaEntrada() {
        val result = NotificationsApiContract.parseList(list(entry(readAt = "\"ayer\""), entry(readAt = "12")))

        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun parseList_cuerpoIlegible_devuelveNull() {
        assertNull(NotificationsApiContract.parseList("<html>"))
        assertNull(NotificationsApiContract.parseList("""{"items":[]}"""))
        assertNull(NotificationsApiContract.parseList("[]"))
    }

    @Test
    fun parseList_ignoraCamposDesconocidos() {
        val body = """{"notifications":[${entry().dropLast(1)},"extra":{"a":1}}],"page":2}"""

        assertNotNull(NotificationsApiContract.parseList(body)?.singleOrNull())
    }

    @Test
    fun readPath_conIdNoUuid_esInvalido() {
        assertFalse(NotificationsApiContract.isValidId("../auth/logout"))
        assertFalse(NotificationsApiContract.isValidId("1/read"))
        assertTrue(NotificationsApiContract.isValidId("11111111-1111-4111-8111-111111111111"))
    }

    @Test
    fun rutas_componenSegunElContrato() {
        val id = "11111111-1111-4111-8111-111111111111"

        assertEquals("notifications/$id/read", NotificationsApiContract.readPath(id))
        assertEquals("notifications/$id", NotificationsApiContract.deletePath(id))
        assertEquals("notifications/read", NotificationsApiContract.DELETE_READ_PATH)
    }
}
