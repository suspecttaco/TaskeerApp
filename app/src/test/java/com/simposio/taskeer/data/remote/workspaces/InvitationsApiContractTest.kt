package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.domain.workspace.InvitationStatus
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.testing.uuid
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationsApiContractTest {

    private fun entry(
        n: Int,
        role: String? = "EDITOR",
        status: String? = "PENDING",
        expiresAt: String = "2026-12-20T10:00:00Z",
        extra: String = "",
    ): String {
        val roleJson = role?.let { "\"role\":\"$it\"," }.orEmpty()
        val statusJson = status?.let { "\"status\":\"$it\"," }.orEmpty()
        return """{"id":"${uuid(n)}","workspaceId":"${uuid(100)}","email":"i$n@example.com",$roleJson$statusJson"invitedById":"${uuid(1)}","expiresAt":"$expiresAt","createdAt":"2026-11-20T10:00:00Z"$extra}"""
    }

    private fun list(vararg entries: String) = """{"invitations":[${entries.joinToString(",")}]}"""

    private fun mine(n: Int, name: String? = "Equipo") =
        """{"id":"${uuid(n)}","workspaceId":"${uuid(100)}",${name?.let { "\"workspaceName\":\"$it\"," }.orEmpty()}"role":"VIEWER","status":"PENDING","expiresAt":"2026-12-20T10:00:00Z","createdAt":"2026-11-20T10:00:00Z"}"""

    @Test
    fun parseList_valida_devuelveTodos() {
        val result = InvitationsApiContract.parseList(list(entry(1), entry(2, "ADMIN")))!!

        assertEquals(listOf(uuid(1), uuid(2)), result.map { it.id })
        assertEquals(WorkspaceRole.ADMIN, result[1].role)
        assertEquals(Instant.parse("2026-12-20T10:00:00Z"), result[0].expiresAt)
    }

    @Test
    fun parseList_vacia_devuelveListaVacia() {
        assertEquals(emptyList<Any>(), InvitationsApiContract.parseList(list()))
    }

    @Test
    fun parseList_entradaRotaEntreBuenas_laDescarta() {
        val result = InvitationsApiContract.parseList(list(entry(1), """{"id":"x"}""", "7", entry(3)))!!

        assertEquals(listOf(uuid(1), uuid(3)), result.map { it.id })
    }

    @Test
    fun parseList_rolDesconocido_esUnknown() {
        val result = InvitationsApiContract.parseList(list(entry(1, "SUPERUSER"), entry(2, null), entry(3, "OWNER")))!!

        assertEquals(listOf(WorkspaceRole.UNKNOWN, WorkspaceRole.UNKNOWN, WorkspaceRole.UNKNOWN), result.map { it.role })
    }

    @Test
    fun parseList_statusDesconocido_esUnknown() {
        val result = InvitationsApiContract.parseList(list(entry(1, status = "WHATEVER"), entry(2, status = null)))!!

        assertEquals(listOf(InvitationStatus.UNKNOWN, InvitationStatus.UNKNOWN), result.map { it.status })
    }

    @Test
    fun parseList_fechaMalFormada_descartaLaEntrada() {
        val result = InvitationsApiContract.parseList(list(entry(1, expiresAt = "mañana"), entry(2)))!!

        assertEquals(listOf(uuid(2)), result.map { it.id })
    }

    @Test
    fun parseList_ignoraCamposDesconocidos() {
        val result = InvitationsApiContract.parseList(list(entry(1, extra = ""","nuevo":{"a":1}""")))!!

        assertEquals(1, result.size)
    }

    @Test
    fun parseList_cuerpoIlegible_devuelveNull() {
        assertNull(InvitationsApiContract.parseList("no es json"))
        assertNull(InvitationsApiContract.parseList("""{"otra":[]}"""))
    }

    @Test
    fun parseOne_valido_devuelveLaInvitacion() {
        val result = InvitationsApiContract.parseOne("""{"invitation":${entry(4)}}""")!!

        assertEquals(uuid(4), result.id)
        assertEquals("i4@example.com", result.email)
        assertNull(InvitationsApiContract.parseOne("""{"invitation":1}"""))
    }

    @Test
    fun parseMine_valida_incluyeWorkspaceName() {
        val result = InvitationsApiContract.parseMine("""{"invitations":[${mine(1, "Diseño")}]}""")!!

        assertEquals("Diseño", result.single().workspaceName)
        assertEquals(WorkspaceRole.VIEWER, result.single().role)
    }

    @Test
    fun parseMine_sinWorkspaceName_descartaLaEntrada() {
        val result = InvitationsApiContract.parseMine("""{"invitations":[${mine(1, null)},${mine(2)}]}""")!!

        assertEquals(listOf(uuid(2)), result.map { it.id })
    }

    @Test
    fun parseMine_cuerpoIlegible_devuelveNull() {
        assertNull(InvitationsApiContract.parseMine("["))
    }

    @Test
    fun parseAccepted_conNombre_devuelveNombreYAlreadyMember() {
        val result = InvitationsApiContract.parseAccepted("""{"workspace":{"name":"Equipo"},"alreadyMember":true}""")

        assertEquals("Equipo", result.name)
        assertTrue(result.alreadyMember)
    }

    @Test
    fun parseAccepted_sinNombre_toleraNombreVacio() {
        assertEquals("", InvitationsApiContract.parseAccepted("""{"workspace":{}}""").name)
        assertEquals("", InvitationsApiContract.parseAccepted("basura").name)
        assertFalse(InvitationsApiContract.parseAccepted("basura").alreadyMember)
    }

    @Test
    fun inviteBody_serializaCorreoYRol() {
        val json = JSONObject(InvitationsApiContract.inviteBody("Ana@Example.com", WorkspaceRole.VIEWER))

        assertEquals("Ana@Example.com", json.getString("email"))
        assertEquals("VIEWER", json.getString("role"))
    }

    @Test
    fun inviteBody_owner_noSeSerializa() {
        val failure = runCatching { InvitationsApiContract.inviteBody("a@example.com", WorkspaceRole.OWNER) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun inviteBody_escapaCaracteresEspeciales() {
        val json = JSONObject(InvitationsApiContract.inviteBody("a\"b@example.com", WorkspaceRole.EDITOR))

        assertEquals("a\"b@example.com", json.getString("email"))
    }

    @Test
    fun pathConIdNoUuid_esInvalido() {
        assertFalse(InvitationsApiContract.isValidId("../x"))
        assertFalse(InvitationsApiContract.isValidId("1/accept"))
        assertTrue(InvitationsApiContract.isValidId(uuid(1)))
    }

    @Test
    fun rutas_sonLasDelContrato() {
        assertEquals("workspaces/${uuid(1)}/invitations", InvitationsApiContract.listPath(uuid(1)))
        assertEquals("workspaces/${uuid(1)}/invitations/${uuid(2)}", InvitationsApiContract.invitationPath(uuid(1), uuid(2)))
        assertEquals("invitations/${uuid(2)}/accept", InvitationsApiContract.acceptPath(uuid(2)))
        assertEquals("invitations/mine", InvitationsApiContract.MINE_PATH)
        assertEquals("invitations/accept", InvitationsApiContract.ACCEPT_WITH_TOKEN_PATH)
    }
}
