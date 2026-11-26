package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.domain.workspace.MemberRules
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONException
import org.json.JSONObject

// Contrato provisional: rutas y claves salen de openapi.yaml y no se han contrastado con respuestas reales.
// Es el único sitio que conoce la forma JSON de los miembros.
internal object WorkspaceMembersApiContract {

    fun listPath(workspaceId: String): String = "workspaces/$workspaceId/members"

    fun memberPath(workspaceId: String, userId: String): String = "workspaces/$workspaceId/members/$userId"

    fun transferPath(workspaceId: String): String = "workspaces/$workspaceId/transfer-ownership"

    // Los ids se interpolan en la ruta: solo se acepta un UUID para no colar segmentos de ruta.
    fun isValidId(id: String): Boolean = WorkspacesApiContract.isValidId(id)

    // El servidor no admite OWNER por esta vía: solo se serializan los roles asignables.
    fun roleBody(role: WorkspaceRole): String {
        require(role in MemberRules.ASSIGNABLE) { "Rol no asignable" }
        return JSONObject().put("role", role.name).toString()
    }

    fun transferBody(newOwnerUserId: String): String = JSONObject().put("newOwnerUserId", newOwnerUserId).toString()

    // null = cuerpo no usable. Una entrada rota se descarta sin tirar el resto.
    fun parseList(body: String): List<WorkspaceMember>? = try {
        JSONObject(body).optJSONArray("members")?.let { list ->
            (0 until list.length()).mapNotNull { parseEntry(list.opt(it)) }
        }
    } catch (_: JSONException) {
        null
    }

    fun parseOne(body: String): WorkspaceMember? = try {
        parseEntry(JSONObject(body).opt("member"))
    } catch (_: JSONException) {
        null
    }

    private fun parseEntry(raw: Any?): WorkspaceMember? {
        val json = raw as? JSONObject ?: return null
        val userId = (json.opt("userId") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val email = (json.opt("email") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val createdAt = instantOf(json.opt("createdAt")) ?: return null
        val displayName = when (val name = json.opt("displayName")) {
            null, JSONObject.NULL -> email
            is String -> name.takeIf { it.isNotBlank() } ?: email
            else -> return null
        }
        return WorkspaceMember(
            userId = userId,
            email = email,
            displayName = displayName,
            role = WorkspaceRules.roleOf(json.opt("role") as? String),
            createdAt = createdAt,
        )
    }

    private fun instantOf(value: Any?): Instant? = try {
        (value as? String)?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }
}
