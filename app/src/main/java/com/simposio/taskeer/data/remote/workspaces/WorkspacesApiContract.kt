package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.domain.workspace.Workspace
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONException
import org.json.JSONObject

// Contrato provisional: rutas y claves salen de openapi.yaml y no se han contrastado con respuestas reales.
// Es el único sitio que conoce la forma JSON de los workspaces.
internal object WorkspacesApiContract {
    const val LIST_PATH = "workspaces"

    // El id se interpola en la ruta: solo se acepta un UUID para no colar segmentos de ruta.
    fun isValidId(id: String): Boolean = WorkspaceRules.isValidId(id)

    fun itemPath(id: String): String = "workspaces/$id"

    fun nameBody(name: String): String = JSONObject().put("name", name).toString()

    // null = cuerpo no usable. Una entrada rota se descarta sin tirar el resto.
    fun parseList(body: String): List<Workspace>? = try {
        JSONObject(body).optJSONArray("workspaces")?.let { list ->
            (0 until list.length()).mapNotNull { parseEntry(list.opt(it), roleOf(list.optJSONObject(it))) }
        }
    } catch (_: JSONException) {
        null
    }

    // create y rename no devuelven `myRole`: lo aporta quien llama.
    fun parseOne(body: String, myRole: WorkspaceRole): Workspace? = try {
        parseEntry(JSONObject(body).opt("workspace"), myRole)
    } catch (_: JSONException) {
        null
    }

    private fun roleOf(json: JSONObject?): WorkspaceRole = WorkspaceRules.roleOf(json?.opt("myRole") as? String)

    private fun parseEntry(raw: Any?, myRole: WorkspaceRole): Workspace? {
        val json = raw as? JSONObject ?: return null
        val id = (json.opt("id") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val name = (json.opt("name") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val createdAt = instantOf(json.opt("createdAt")) ?: return null
        val updatedAt = instantOf(json.opt("updatedAt")) ?: return null
        return Workspace(
            id = id,
            name = name,
            slug = json.opt("slug") as? String ?: "",
            ownerId = json.opt("ownerId") as? String ?: "",
            myRole = myRole,
            isPersonal = json.opt("isPersonal") as? Boolean ?: false,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun instantOf(value: Any?): Instant? = try {
        (value as? String)?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }
}
