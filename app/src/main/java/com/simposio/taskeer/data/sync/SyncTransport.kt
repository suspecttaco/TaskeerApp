package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import java.time.Duration
import java.time.Instant

// Puerto del motor hacia el servidor, con tipos neutros: ni HTTP ni JSON. Lo implementan RemoteSyncTransport (contrato 1.0)
// y, solo en la variante de depuración, un puente sobre las rutas REST que existan mientras llega cada hito del backend.
interface SyncTransport {
    suspend fun push(batch: List<PushMutation>): TransportResult<List<PushOutcome>>

    // Lee la cuenta con una página mínima de cambios, sin aplicarla.
    suspend fun identify(): TransportResult<Identity>
}

// Los `data` viajan completos y con los nulos explícitos (contrato 1.0, sección 5.2). Los valores son neutros:
// String, Boolean, Int, Long, null y listas o mapas de ellos.
class PushMutation(
    val clientMutationId: String,
    val entity: SyncEntity,
    val op: SyncOp,
    val id: String,
    val baseUpdatedAt: Instant?,
    val clientUpdatedAt: Instant,
    val data: Map<String, Any?>?,
) {
    override fun toString(): String = "PushMutation(entity=$entity, op=$op)"
}

class ServerRecord(
    val entity: SyncEntity,
    val id: String,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val fields: Map<String, Any?>,
) {
    val isDeleted: Boolean get() = deletedAt != null

    override fun toString(): String = "ServerRecord(entity=$entity, deleted=$isDeleted)"
}

sealed interface PushOutcome {
    val clientMutationId: String

    class Applied(override val clientMutationId: String, val record: ServerRecord) : PushOutcome {
        override fun toString(): String = "Applied"
    }

    class Conflict(override val clientMutationId: String, val record: ServerRecord) : PushOutcome {
        override fun toString(): String = "Conflict"
    }

    class Rejected(override val clientMutationId: String, val code: String, val record: ServerRecord?) : PushOutcome {
        override fun toString(): String = "Rejected($code)"
    }
}

// VALIDATION_ERROR describe una petición mal formada, no una decisión del servidor sobre el dato del usuario.
object SyncCodes {
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
}

class Identity(
    val userId: String,
    val contractVersion: String,
    val supportedEntities: Set<SyncEntity>,
) {
    override fun toString(): String = "Identity(contractVersion=$contractVersion, supported=$supportedEntities)"
}

enum class RetryKind { NETWORK, RATE_LIMITED, SERVER }

sealed interface TransportResult<out T> {
    class Ok<T>(val value: T) : TransportResult<T>

    // Red, 429 o 5xx: la entrada se conserva y nunca se revierte.
    class Retry(val after: Duration?, val kind: RetryKind) : TransportResult<Nothing>

    // 400/413 de la petición entera: el cuerpo, no el servidor, es el problema.
    class Invalid(val code: String) : TransportResult<Nothing>

    data object Unauthorized : TransportResult<Nothing>

    data object UpdateRequired : TransportResult<Nothing>

    // 404/405/501: la ruta sync/* aún no existe en este servidor.
    data object NotAvailable : TransportResult<Nothing>
}
