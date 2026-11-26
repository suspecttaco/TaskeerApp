package com.simposio.taskeer.data.remote.boards

import com.simposio.taskeer.domain.workspace.BoardMemberOverride
import com.simposio.taskeer.domain.workspace.MemberRules
import com.simposio.taskeer.domain.workspace.ServerBoard
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONException
import org.json.JSONObject

// Contrato provisional: rutas y claves salen de openapi.yaml y no se han contrastado con respuestas reales.
// Es el único sitio que conoce la forma JSON de los tableros del servidor y de sus overrides.
internal object BoardRolesApiContract {

    fun boardsPath(workspaceId: String): String = "workspaces/$workspaceId/boards"

    fun boardPath(workspaceId: String, boardId: String): String = "workspaces/$workspaceId/boards/$boardId"

    fun overridesPath(workspaceId: String, boardId: String): String = "${boardPath(workspaceId, boardId)}/member-overrides"

    fun overridePath(workspaceId: String, boardId: String, userId: String): String =
        "${overridesPath(workspaceId, boardId)}/$userId"

    // Los ids se interpolan en la ruta: solo se acepta un UUID para no colar segmentos de ruta.
    fun isValidId(id: String): Boolean = WorkspaceRules.isValidId(id)

    // El servidor no admite OWNER por esta vía: solo se serializan los roles asignables.
    fun roleBody(role: WorkspaceRole): String {
        require(role in MemberRules.ASSIGNABLE) { "Rol no asignable" }
        return JSONObject().put("role", role.name).toString()
    }

    // null = cuerpo no usable. Una entrada rota se descarta sin tirar el resto.
    fun parseBoards(body: String): List<ServerBoard>? = try {
        JSONObject(body).optJSONArray("boards")?.let { list ->
            (0 until list.length()).mapNotNull { parseBoard(list.opt(it)) }
        }
    } catch (_: JSONException) {
        null
    }

    // El detalle trae columnas y tareas completas: solo se leen el nombre y el rol efectivo.
    fun parseBoardAccess(body: String): Pair<String?, WorkspaceRole>? = try {
        JSONObject(body).optJSONObject("board")?.let { board ->
            val name = (board.opt("name") as? String)?.takeIf { it.isNotBlank() }
            name to WorkspaceRules.roleOf(board.opt("viewerEffectiveRole") as? String)
        }
    } catch (_: JSONException) {
        null
    }

    fun parseOverrides(body: String): List<BoardMemberOverride>? = try {
        JSONObject(body).optJSONArray("overrides")?.let { list ->
            (0 until list.length()).mapNotNull { parseEntry(list.opt(it)) }
        }
    } catch (_: JSONException) {
        null
    }

    fun parseOverride(body: String): BoardMemberOverride? = try {
        parseEntry(JSONObject(body).opt("override"))
    } catch (_: JSONException) {
        null
    }

    private fun parseBoard(raw: Any?): ServerBoard? {
        val json = raw as? JSONObject ?: return null
        val id = (json.opt("id") as? String)?.takeIf(::isValidId) ?: return null
        val name = (json.opt("name") as? String)?.takeIf { it.isNotBlank() } ?: return null
        return ServerBoard(id, name)
    }

    private fun parseEntry(raw: Any?): BoardMemberOverride? {
        val json = raw as? JSONObject ?: return null
        val id = (json.opt("id") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val userId = (json.opt("userId") as? String)?.takeIf(::isValidId) ?: return null
        return BoardMemberOverride(
            id = id,
            userId = userId,
            role = WorkspaceRules.roleOf(json.opt("role") as? String),
            updatedAt = instantOf(json.opt("updatedAt")) ?: Instant.EPOCH,
        )
    }

    private fun instantOf(value: Any?): Instant? = try {
        (value as? String)?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }
}
