package com.simposio.taskeer.data.remote.notifications

import com.simposio.taskeer.domain.notification.ServerNotification
import com.simposio.taskeer.domain.notification.ServerNotificationType
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Contrato provisional: rutas y claves salen de openapi.yaml y no se han contrastado con respuestas reales.
// Es el único sitio que conoce la forma JSON de las notificaciones.
internal object NotificationsApiContract {
    const val LIST_PATH = "notifications"
    const val READ_ALL_PATH = "notifications/read-all"
    const val DELETE_READ_PATH = "notifications/read"

    private val ID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    // El id se interpola en la ruta: solo se acepta un UUID para no colar segmentos de ruta.
    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

    fun readPath(id: String): String = "notifications/$id/read"

    fun deletePath(id: String): String = "notifications/$id"

    // null = cuerpo no usable. Una entrada rota se descarta sin tirar el resto.
    fun parseList(body: String): List<ServerNotification>? = try {
        val array: JSONArray? = JSONObject(body).optJSONArray("notifications")
        array?.let { list -> (0 until list.length()).mapNotNull { parseEntry(list.opt(it)) } }
    } catch (_: JSONException) {
        null
    }

    private fun parseEntry(raw: Any?): ServerNotification? {
        val json = raw as? JSONObject ?: return null
        val id = (json.opt("id") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val title = (json.opt("title") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val createdAt = instantOf(json.opt("createdAt")) ?: return null
        val readAt = when (val value = json.opt("readAt")) {
            null, JSONObject.NULL -> null
            else -> instantOf(value) ?: return null
        }
        return ServerNotification(
            id = id,
            type = typeOf(json.opt("type")),
            title = title,
            body = json.opt("body") as? String,
            taskId = (json.opt("taskId") as? String)?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
            readAt = readAt,
        )
    }

    private fun typeOf(value: Any?): ServerNotificationType =
        ServerNotificationType.entries.firstOrNull { it != ServerNotificationType.UNKNOWN && it.name == value }
            ?: ServerNotificationType.UNKNOWN

    private fun instantOf(value: Any?): Instant? = try {
        (value as? String)?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }
}
