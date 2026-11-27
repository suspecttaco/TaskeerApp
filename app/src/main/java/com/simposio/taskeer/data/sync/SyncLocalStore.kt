package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.QueuedOp
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncLoss
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class PreparedOp(val op: QueuedOp, val mutation: PushMutation, val sentVersion: Long) {
    override fun toString(): String = "PreparedOp(op=$op)"
}

enum class OutcomeEffect(val loss: SyncLoss?) {
    APPLIED(null),
    REPLACED_BY_SERVER(SyncLoss.REPLACED_BY_SERVER),
    REVERTED(SyncLoss.REJECTED),
    LIST_ADOPTED(null),
    RETRY_AFTER_PARENT(null),
    KEPT_FOR_RETRY(null),
}

// Qué hace applyPage con el cursor al terminar la página.
sealed interface PageCommit {
    // Incremental: el cursor avanza con la página, en la misma transacción.
    data object SaveCursor : PageCommit

    // Snapshot a medias: un corte lo reinicia desde cero, así que el cursor aún no se guarda.
    data object KeepCursor : PageCommit

    // Última página de un snapshot completo: guarda el cursor y reconcilia lo que no apareció en toda la carga.
    class FinishSnapshot(val seen: Set<Pair<SyncEntity, String>>) : PageCommit {
        override fun toString(): String = "FinishSnapshot(seen=${seen.size})"
    }
}

// `remindersChanged`: la página trajo recordatorios nuevos o cambiados; el motor avisa al reconciliador de alarmas.
class PageEffect(val losses: List<SyncLoss>, val remindersChanged: Boolean = false)

object SyncMetaKeys {
    const val BOUND_USER_ID = "boundUserId"
    const val CURSOR = "cursor"
    const val LAST_SYNCED_AT = "lastSyncedAt"
    const val MARKED_SCOPE = "markedScope"
}

// La cola de envío y su estado persisten en Room; el motor solo conoce este contrato.
interface SyncLocalStore {
    // Idempotente: pasa lo pendiente de las tablas de negocio a la cola, con una entrada por entidad.
    suspend fun collect(now: Instant)

    // Entradas que pueden enviarse ya: en alcance, sin retroceso vigente y con la ventana de «Deshacer» vencida.
    suspend fun eligible(now: Instant, ignoreBackoff: Boolean = false): List<QueuedOp>

    // Estado completo desde la fila vigente. Null si la fila ya no existe o dejó de ser enviable.
    suspend fun payloadFor(op: QueuedOp, me: String): PreparedOp?

    suspend fun applyOutcome(
        prepared: PreparedOp,
        outcome: PushOutcome,
        me: String,
        now: Instant,
        retryDependency: Boolean,
    ): OutcomeEffect

    // Una transacción: filas, versiones base y cursor. Idempotente: una versión ya conocida se omite.
    suspend fun applyPage(page: PullPage, me: String, now: Instant, commit: PageCommit): PageEffect

    suspend fun recordTransientFailure(op: QueuedOp, nextAttemptAt: Instant)

    suspend fun recordPermanentFailure(op: QueuedOp, code: String)

    // Próximo instante en que habrá algo elegible (retroceso o fin de la ventana de «Deshacer»).
    suspend fun nextWake(now: Instant): Instant?

    suspend fun markAllUnsyncedPending()

    // Id del workspace personal si ya llegó por sincronización: los tableros nuevos suben a él.
    suspend fun personalWorkspaceId(): String?

    suspend fun rekeyList(oldId: String, newId: String)

    suspend fun meta(key: String): String?

    suspend fun setMeta(key: String, value: String?)

    fun observePendingCount(): Flow<Int>

    fun observeStuckCount(): Flow<Int>
}
