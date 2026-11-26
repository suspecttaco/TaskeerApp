package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.domain.workspace.Invitation
import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.InvitationStatus
import com.simposio.taskeer.domain.workspace.MyInvitation
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONException
import org.json.JSONObject

internal class AcceptedWorkspace(val name: String, val alreadyMember: Boolean) {
    override fun toString(): String = "AcceptedWorkspace(alreadyMember=$alreadyMember)"
}

// Contrato provisional: rutas y claves salen de openapi.yaml y no se han contrastado con respuestas reales.
// Es el único sitio que conoce la forma JSON de las invitaciones.
internal object InvitationsApiContract {
    const val MINE_PATH = "invitations/mine"
    const val ACCEPT_WITH_TOKEN_PATH = "invitations/accept"
    const val TOKEN_QUERY = "token"

    fun listPath(workspaceId: String): String = "workspaces/$workspaceId/invitations"

    fun invitationPath(workspaceId: String, invitationId: String): String = "workspaces/$workspaceId/invitations/$invitationId"

    fun acceptPath(invitationId: String): String = "invitations/$invitationId/accept"

    // Los ids se interpolan en la ruta: solo se acepta un UUID para no colar segmentos de ruta.
    fun isValidId(id: String): Boolean = WorkspacesApiContract.isValidId(id)

    // El servidor no admite OWNER por esta vía: solo se serializan los roles invitables.
    fun inviteBody(email: String, role: WorkspaceRole): String {
        require(role in InvitationRules.INVITABLE) { "Rol no invitable" }
        return JSONObject().put("email", email).put("role", role.name).toString()
    }

    // null = cuerpo no usable. Una entrada rota se descarta sin tirar el resto.
    fun parseList(body: String): List<Invitation>? = try {
        JSONObject(body).optJSONArray("invitations")?.let { list ->
            (0 until list.length()).mapNotNull { parseEntry(list.opt(it)) }
        }
    } catch (_: JSONException) {
        null
    }

    fun parseOne(body: String): Invitation? = try {
        parseEntry(JSONObject(body).opt("invitation"))
    } catch (_: JSONException) {
        null
    }

    fun parseMine(body: String): List<MyInvitation>? = try {
        JSONObject(body).optJSONArray("invitations")?.let { list ->
            (0 until list.length()).mapNotNull { parseMineEntry(list.opt(it)) }
        }
    } catch (_: JSONException) {
        null
    }

    // Con un 200 la aceptación ya ocurrió: un cuerpo ilegible no debe convertirse en fallo.
    fun parseAccepted(body: String): AcceptedWorkspace = try {
        val json = JSONObject(body)
        val name = (json.optJSONObject("workspace")?.opt("name") as? String)?.takeIf { it.isNotBlank() }.orEmpty()
        AcceptedWorkspace(name, json.opt("alreadyMember") as? Boolean ?: false)
    } catch (_: JSONException) {
        AcceptedWorkspace("", false)
    }

    private fun parseEntry(raw: Any?): Invitation? {
        val json = raw as? JSONObject ?: return null
        val id = text(json, "id") ?: return null
        val workspaceId = text(json, "workspaceId") ?: return null
        val email = text(json, "email") ?: return null
        val expiresAt = instantOf(json.opt("expiresAt")) ?: return null
        val createdAt = instantOf(json.opt("createdAt")) ?: return null
        return Invitation(
            id = id,
            workspaceId = workspaceId,
            email = email,
            role = roleOf(json),
            status = statusOf(json),
            invitedById = json.opt("invitedById") as? String ?: "",
            expiresAt = expiresAt,
            createdAt = createdAt,
        )
    }

    private fun parseMineEntry(raw: Any?): MyInvitation? {
        val json = raw as? JSONObject ?: return null
        val id = text(json, "id") ?: return null
        val workspaceId = text(json, "workspaceId") ?: return null
        val workspaceName = text(json, "workspaceName") ?: return null
        val expiresAt = instantOf(json.opt("expiresAt")) ?: return null
        val createdAt = instantOf(json.opt("createdAt")) ?: return null
        return MyInvitation(
            id = id,
            workspaceId = workspaceId,
            workspaceName = workspaceName,
            role = roleOf(json),
            status = statusOf(json),
            expiresAt = expiresAt,
            createdAt = createdAt,
        )
    }

    private fun text(json: JSONObject, key: String): String? = (json.opt(key) as? String)?.takeIf { it.isNotBlank() }

    // Una invitación nunca concede OWNER: si llega, no se ofrecen acciones sobre ella.
    private fun roleOf(json: JSONObject): WorkspaceRole =
        WorkspaceRules.roleOf(json.opt("role") as? String).takeIf { it in InvitationRules.INVITABLE } ?: WorkspaceRole.UNKNOWN

    private fun statusOf(json: JSONObject): InvitationStatus =
        InvitationStatus.entries.firstOrNull { it != InvitationStatus.UNKNOWN && it.name == json.opt("status") }
            ?: InvitationStatus.UNKNOWN

    private fun instantOf(value: Any?): Instant? = try {
        (value as? String)?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }
}
