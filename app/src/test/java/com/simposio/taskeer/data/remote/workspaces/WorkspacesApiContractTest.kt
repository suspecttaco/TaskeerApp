package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.domain.workspace.WorkspaceRole
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacesApiContractTest {

    private fun entry(
        id: String = "11111111-1111-4111-8111-111111111111",
        name: String = "Equipo",
        extra: String = "",
        role: String = "\"myRole\":\"OWNER\",",
        createdAt: String = "2026-10-01T10:00:00.000Z",
    ) = """{"id":"$id","name":"$name","slug":"equipo","ownerId":"u1",$role"createdAt":"$createdAt","updatedAt":"2026-11-01T10:00:00.000Z"$extra}"""

    private fun list(vararg entries: String) = """{"workspaces":[${entries.joinToString(",")}]}"""

    @Test
    fun parseList_valida_devuelveTodas() {
        val result = WorkspacesApiContract.parseList(list(entry(name = "Uno"), entry(id = "22222222-2222-4222-8222-222222222222", name = "Dos")))!!

        assertEquals(listOf("Uno", "Dos"), result.map { it.name })
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), result[0].updatedAt)
        assertEquals("equipo", result[0].slug)
    }

    @Test
    fun parseList_vacia_devuelveListaVacia() {
        assertEquals(emptyList<Any>(), WorkspacesApiContract.parseList("""{"workspaces":[]}"""))
    }

    @Test
    fun parseList_entradaRotaEntreBuenas_laDescarta() {
        val body = list(entry(name = "Uno"), """{"id":"x"}""", "42", entry(id = "22222222-2222-4222-8222-222222222222", name = "Tres"))

        assertEquals(listOf("Uno", "Tres"), WorkspacesApiContract.parseList(body)!!.map { it.name })
    }

    @Test
    fun parseList_rolDesconocido_esUnknown() {
        val result = WorkspacesApiContract.parseList(list(entry(role = "\"myRole\":\"SUPERUSER\",")))!!

        assertEquals(WorkspaceRole.UNKNOWN, result.single().myRole)
    }

    @Test
    fun parseList_rolAusente_esUnknown() {
        assertEquals(WorkspaceRole.UNKNOWN, WorkspacesApiContract.parseList(list(entry(role = "")))!!.single().myRole)
    }

    @Test
    fun parseList_rolValido_seConserva() {
        assertEquals(WorkspaceRole.VIEWER, WorkspacesApiContract.parseList(list(entry(role = "\"myRole\":\"VIEWER\",")))!!.single().myRole)
    }

    @Test
    fun parseList_isPersonalAusente_esFalse() {
        assertFalse(WorkspacesApiContract.parseList(list(entry()))!!.single().isPersonal)
    }

    @Test
    fun parseList_isPersonalTrue_seConserva() {
        assertTrue(WorkspacesApiContract.parseList(list(entry(extra = ",\"isPersonal\":true")))!!.single().isPersonal)
    }

    @Test
    fun parseList_fechaMalFormada_descartaLaEntrada() {
        assertEquals(emptyList<Any>(), WorkspacesApiContract.parseList(list(entry(createdAt = "ayer"))))
    }

    @Test
    fun parseList_ignoraCamposDesconocidos() {
        val result = WorkspacesApiContract.parseList(list(entry(extra = ",\"memberCount\":4,\"nuevo\":{\"a\":1}")))!!

        assertEquals(1, result.size)
    }

    @Test
    fun parseList_cuerpoIlegible_devuelveNull() {
        assertNull(WorkspacesApiContract.parseList("<html>"))
        assertNull(WorkspacesApiContract.parseList("""{"otra":[]}"""))
    }

    @Test
    fun parseOne_conRolDado_loAsigna() {
        val result = WorkspacesApiContract.parseOne("""{"workspace":${entry(role = "")}}""", WorkspaceRole.OWNER)!!

        assertEquals(WorkspaceRole.OWNER, result.myRole)
    }

    @Test
    fun parseOne_cuerpoSinWorkspace_devuelveNull() {
        assertNull(WorkspacesApiContract.parseOne("{}", WorkspaceRole.OWNER))
    }

    @Test
    fun nameBody_escapaComillasYSaltos() {
        val name = "Dos \"comillas\"\ny salto"

        assertEquals(name, JSONObject(WorkspacesApiContract.nameBody(name)).getString("name"))
        assertEquals(1, JSONObject(WorkspacesApiContract.nameBody(name)).length())
    }

    @Test
    fun pathConIdNoUuid_esInvalido() {
        assertTrue(WorkspacesApiContract.isValidId("11111111-1111-4111-8111-111111111111"))
        assertFalse(WorkspacesApiContract.isValidId("../me"))
        assertFalse(WorkspacesApiContract.isValidId("1/members"))
    }

    @Test
    fun itemPath_componeLaRuta() {
        assertEquals("workspaces/abc", WorkspacesApiContract.itemPath("abc"))
    }
}
