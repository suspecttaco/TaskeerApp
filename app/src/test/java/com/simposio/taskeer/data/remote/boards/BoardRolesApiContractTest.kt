package com.simposio.taskeer.data.remote.boards

import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.testing.uuid
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRolesApiContractTest {

    private val ws = uuid(100)

    private fun boardJson(n: Int, name: String = "Tablero $n") =
        """{"id":"${uuid(n)}","workspaceId":"$ws","name":"$name","slug":"t$n","createdAt":"2026-10-01T10:00:00Z","updatedAt":"2026-10-01T10:00:00Z"}"""

    private fun boards(vararg entries: String) = """{"boards":[${entries.joinToString(",")}]}"""

    private fun overrideJson(userN: Int, role: String? = "VIEWER", id: String? = uuid(900 + userN)): String {
        val parts = listOfNotNull(
            id?.let { "\"id\":\"$it\"" },
            "\"boardId\":\"${uuid(1)}\"",
            "\"userId\":\"${uuid(userN)}\"",
            role?.let { "\"role\":\"$it\"" },
            "\"updatedAt\":\"2026-11-02T08:00:00Z\"",
        )
        return "{${parts.joinToString(",")}}"
    }

    private fun overrides(vararg entries: String) = """{"overrides":[${entries.joinToString(",")}]}"""

    @Test
    fun parseBoards_valida_devuelveTodos() {
        val result = BoardRolesApiContract.parseBoards(boards(boardJson(1), boardJson(2)))

        assertEquals(listOf(uuid(1), uuid(2)), result?.map { it.id })
        assertEquals(listOf("Tablero 1", "Tablero 2"), result?.map { it.name })
    }

    @Test
    fun parseBoards_vacia_devuelveListaVacia() {
        assertEquals(emptyList<Any>(), BoardRolesApiContract.parseBoards(boards()))
    }

    @Test
    fun parseBoards_entradaRotaEntreBuenas_laDescarta() {
        val result = BoardRolesApiContract.parseBoards(boards(boardJson(1), "42", "{\"name\":\"x\"}", boardJson(2)))

        assertEquals(listOf(uuid(1), uuid(2)), result?.map { it.id })
    }

    @Test
    fun parseBoards_nombreVacio_laDescarta() {
        val result = BoardRolesApiContract.parseBoards(boards(boardJson(1, name = "  "), boardJson(2)))

        assertEquals(listOf(uuid(2)), result?.map { it.id })
    }

    @Test
    fun parseBoards_idNoUuid_laDescarta() {
        val broken = """{"id":"no-es-uuid","name":"X"}"""

        assertEquals(listOf(uuid(2)), BoardRolesApiContract.parseBoards(boards(broken, boardJson(2)))?.map { it.id })
    }

    @Test
    fun parseBoards_ignoraCamposDesconocidos() {
        val extra = """{"id":"${uuid(1)}","name":"A","futuro":{"x":1}}"""

        assertEquals(listOf("A"), BoardRolesApiContract.parseBoards(boards(extra))?.map { it.name })
    }

    @Test
    fun parseBoards_cuerpoIlegible_devuelveNull() {
        assertNull(BoardRolesApiContract.parseBoards("no es json"))
        assertNull(BoardRolesApiContract.parseBoards("{\"otra\":[]}"))
        assertNull(BoardRolesApiContract.parseBoards("[]"))
    }

    @Test
    fun parseBoardAccess_leeNombreYRol() {
        val body = """{"board":{"id":"${uuid(1)}","name":"Mi tablero","viewerEffectiveRole":"EDITOR"}}"""

        assertEquals("Mi tablero" to WorkspaceRole.EDITOR, BoardRolesApiContract.parseBoardAccess(body))
    }

    @Test
    fun parseBoardAccess_ignoraColumnasYTareas() {
        val body = """{"board":{"name":"B","viewerEffectiveRole":"ADMIN","columns":[{"id":"c","tasks":[{"id":"t"}]},7]}}"""

        assertEquals("B" to WorkspaceRole.ADMIN, BoardRolesApiContract.parseBoardAccess(body))
    }

    @Test
    fun parseBoardAccess_rolAusenteODesconocido_esUnknown() {
        assertEquals(WorkspaceRole.UNKNOWN, BoardRolesApiContract.parseBoardAccess("""{"board":{"name":"B"}}""")?.second)
        assertEquals(
            WorkspaceRole.UNKNOWN,
            BoardRolesApiContract.parseBoardAccess("""{"board":{"viewerEffectiveRole":"SUPERUSER"}}""")?.second,
        )
    }

    @Test
    fun parseBoardAccess_sinBoard_devuelveNull() {
        assertNull(BoardRolesApiContract.parseBoardAccess("{}"))
        assertNull(BoardRolesApiContract.parseBoardAccess("basura"))
    }

    @Test
    fun parseOverrides_valida_devuelveTodos() {
        val result = BoardRolesApiContract.parseOverrides(overrides(overrideJson(3, "ADMIN"), overrideJson(4, "EDITOR")))

        assertEquals(listOf(uuid(3), uuid(4)), result?.map { it.userId })
        assertEquals(listOf(WorkspaceRole.ADMIN, WorkspaceRole.EDITOR), result?.map { it.role })
    }

    @Test
    fun parseOverrides_entradaRota_laDescarta() {
        val noUser = """{"id":"${uuid(9)}","role":"VIEWER"}"""
        val noId = overrideJson(5, id = null)

        val result = BoardRolesApiContract.parseOverrides(overrides(overrideJson(3), noUser, noId, "null"))

        assertEquals(listOf(uuid(3)), result?.map { it.userId })
    }

    @Test
    fun parseOverrides_rolDesconocido_esUnknown() {
        val result = BoardRolesApiContract.parseOverrides(overrides(overrideJson(3, "BOSS"), overrideJson(4, null)))

        assertEquals(listOf(WorkspaceRole.UNKNOWN, WorkspaceRole.UNKNOWN), result?.map { it.role })
    }

    @Test
    fun parseOverrides_rolOwner_seConserva() {
        val result = BoardRolesApiContract.parseOverrides(overrides(overrideJson(3, "OWNER")))

        assertEquals(WorkspaceRole.OWNER, result?.single()?.role)
    }

    @Test
    fun parseOverrides_cuerpoIlegible_devuelveNull() {
        assertNull(BoardRolesApiContract.parseOverrides("xx"))
        assertNull(BoardRolesApiContract.parseOverrides("{\"boards\":[]}"))
    }

    @Test
    fun parseOverride_valido_devuelveElOverride() {
        val result = BoardRolesApiContract.parseOverride("""{"override":${overrideJson(3, "EDITOR")}}""")

        assertNotNull(result)
        assertEquals(uuid(3), result?.userId)
        assertEquals(WorkspaceRole.EDITOR, result?.role)
    }

    @Test
    fun parseOverride_sinOverride_devuelveNull() {
        assertNull(BoardRolesApiContract.parseOverride("{}"))
        assertNull(BoardRolesApiContract.parseOverride("{\"override\":3}"))
    }

    @Test
    fun roleBody_serializaElRol() {
        val body = JSONObject(BoardRolesApiContract.roleBody(WorkspaceRole.EDITOR))

        assertEquals("EDITOR", body.getString("role"))
        assertEquals(1, body.length())
    }

    @Test
    fun roleBody_owner_noSeSerializa() {
        assertThrows(IllegalArgumentException::class.java) { BoardRolesApiContract.roleBody(WorkspaceRole.OWNER) }
    }

    @Test
    fun roleBody_unknown_noSeSerializa() {
        assertThrows(IllegalArgumentException::class.java) { BoardRolesApiContract.roleBody(WorkspaceRole.UNKNOWN) }
    }

    @Test
    fun pathConIdNoUuid_esInvalido() {
        assertTrue(BoardRolesApiContract.isValidId(uuid(1)))
        assertFalse(BoardRolesApiContract.isValidId("../etc"))
        assertFalse(BoardRolesApiContract.isValidId("${uuid(1)}/x"))
        assertFalse(BoardRolesApiContract.isValidId(""))
    }

    @Test
    fun rutas_componenLosSegmentosDelServidor() {
        assertEquals("workspaces/${uuid(1)}/boards", BoardRolesApiContract.boardsPath(uuid(1)))
        assertEquals("workspaces/${uuid(1)}/boards/${uuid(2)}", BoardRolesApiContract.boardPath(uuid(1), uuid(2)))
        assertEquals(
            "workspaces/${uuid(1)}/boards/${uuid(2)}/member-overrides/${uuid(3)}",
            BoardRolesApiContract.overridePath(uuid(1), uuid(2), uuid(3)),
        )
    }
}
