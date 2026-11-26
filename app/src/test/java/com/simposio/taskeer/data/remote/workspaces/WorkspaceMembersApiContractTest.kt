package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.testing.uuid
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceMembersApiContractTest {

    private fun entry(
        n: Int,
        role: String? = "EDITOR",
        name: String? = "Miembro $n",
        email: String? = "m$n@example.com",
        createdAt: String? = "2026-10-01T10:00:00Z",
        extra: String = "",
    ): String {
        val fields = buildList {
            add("\"userId\":\"${uuid(n)}\"")
            email?.let { add("\"email\":\"$it\"") }
            name?.let { add("\"displayName\":\"$it\"") }
            role?.let { add("\"role\":\"$it\"") }
            createdAt?.let { add("\"createdAt\":\"$it\"") }
            if (extra.isNotEmpty()) add(extra)
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun list(vararg entries: String) = """{"members":[${entries.joinToString(",")}]}"""

    @Test
    fun parseList_valida_devuelveTodos() {
        val result = WorkspaceMembersApiContract.parseList(list(entry(1, "OWNER"), entry(2, "VIEWER")))!!

        assertEquals(listOf(uuid(1), uuid(2)), result.map { it.userId })
        assertEquals(listOf(WorkspaceRole.OWNER, WorkspaceRole.VIEWER), result.map { it.role })
        assertEquals("m1@example.com", result[0].email)
    }

    @Test
    fun parseList_vacia_devuelveListaVacia() {
        assertEquals(emptyList<Any>(), WorkspaceMembersApiContract.parseList(list()))
    }

    @Test
    fun parseList_entradaRotaEntreBuenas_laDescarta() {
        val body = list(entry(1), """{"email":"sin-id@example.com"}""", "7", entry(3, email = null), entry(4))

        assertEquals(listOf(uuid(1), uuid(4)), WorkspaceMembersApiContract.parseList(body)!!.map { it.userId })
    }

    @Test
    fun parseList_rolDesconocido_esUnknown() {
        assertEquals(WorkspaceRole.UNKNOWN, WorkspaceMembersApiContract.parseList(list(entry(1, "SUPERADMIN")))!!.single().role)
    }

    @Test
    fun parseList_rolAusente_esUnknown() {
        assertEquals(WorkspaceRole.UNKNOWN, WorkspaceMembersApiContract.parseList(list(entry(1, role = null)))!!.single().role)
    }

    @Test
    fun parseList_displayNameVacio_usaElCorreo() {
        val result = WorkspaceMembersApiContract.parseList(list(entry(1, name = ""), entry(2, name = null)))!!

        assertEquals(listOf("m1@example.com", "m2@example.com"), result.map { it.displayName })
    }

    @Test
    fun parseList_displayNameNoTextual_descartaLaEntrada() {
        val body = """{"members":[{"userId":"${uuid(1)}","email":"a@example.com","displayName":5,"createdAt":"2026-10-01T10:00:00Z"}]}"""

        assertEquals(emptyList<Any>(), WorkspaceMembersApiContract.parseList(body))
    }

    @Test
    fun parseList_fechaMalFormada_descartaLaEntrada() {
        val body = list(entry(1, createdAt = "ayer"), entry(2, createdAt = null), entry(3))

        assertEquals(listOf(uuid(3)), WorkspaceMembersApiContract.parseList(body)!!.map { it.userId })
    }

    @Test
    fun parseList_ignoraCamposDesconocidos() {
        val body = list(entry(1, extra = "\"avatarUrl\":\"x\",\"nuevo\":{\"a\":1}"))

        assertEquals(1, WorkspaceMembersApiContract.parseList(body)!!.size)
    }

    @Test
    fun parseList_cuerpoIlegible_devuelveNull() {
        assertNull(WorkspaceMembersApiContract.parseList("<html>"))
        assertNull(WorkspaceMembersApiContract.parseList("[]"))
        assertNull(WorkspaceMembersApiContract.parseList("""{"workspaces":[]}"""))
    }

    @Test
    fun parseOne_valido_devuelveElMiembro() {
        val member = WorkspaceMembersApiContract.parseOne("""{"member":${entry(2, "ADMIN")}}""")

        assertEquals(WorkspaceRole.ADMIN, member?.role)
        assertEquals(uuid(2), member?.userId)
    }

    @Test
    fun parseOne_sinMiembro_devuelveNull() {
        assertNull(WorkspaceMembersApiContract.parseOne("{}"))
        assertNull(WorkspaceMembersApiContract.parseOne("nada"))
    }

    @Test
    fun roleBody_serializaElRol() {
        val body = JSONObject(WorkspaceMembersApiContract.roleBody(WorkspaceRole.VIEWER))

        assertEquals("VIEWER", body.getString("role"))
        assertEquals(1, body.length())
    }

    @Test
    fun roleBody_owner_noSeSerializa() {
        assertThrows(IllegalArgumentException::class.java) { WorkspaceMembersApiContract.roleBody(WorkspaceRole.OWNER) }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceMembersApiContract.roleBody(WorkspaceRole.UNKNOWN) }
    }

    @Test
    fun transferBody_serializaElIdSinEscapesRaros() {
        val body = WorkspaceMembersApiContract.transferBody(uuid(3))

        assertEquals(uuid(3), JSONObject(body).getString("newOwnerUserId"))
        assertTrue(!body.contains("\\"))
    }

    @Test
    fun rutas_seComponenConLosIds() {
        assertEquals("workspaces/${uuid(1)}/members", WorkspaceMembersApiContract.listPath(uuid(1)))
        assertEquals("workspaces/${uuid(1)}/members/${uuid(2)}", WorkspaceMembersApiContract.memberPath(uuid(1), uuid(2)))
        assertEquals("workspaces/${uuid(1)}/transfer-ownership", WorkspaceMembersApiContract.transferPath(uuid(1)))
    }

    @Test
    fun pathConIdNoUuid_esInvalido() {
        assertTrue(WorkspaceMembersApiContract.isValidId(uuid(1)))
        assertFalse(WorkspaceMembersApiContract.isValidId("../etc"))
        assertFalse(WorkspaceMembersApiContract.isValidId("${uuid(1)}/members"))
    }
}
