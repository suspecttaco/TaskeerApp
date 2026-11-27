package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.QueuedOp

// Costura de depuración (solo builds debug, tag TaskeerSync). Las líneas pueden llevar contenido de tareas y del
// servidor, pero jamás tokens, cabeceras ni contraseñas: nada de eso llega a la capa de sincronización.
fun interface SyncDebugLog {
    fun line(message: String)

    object None : SyncDebugLog {
        override fun line(message: String) = Unit
    }
}

internal enum class RejectAction { KEPT_FOR_RETRY, RETRY_AFTER_PARENT, ADOPTED_SERVER_RECORD, DISCARDED, LIST_ADOPTED }

internal fun SyncDebugLog.rejected(op: QueuedOp, code: String, hadRecord: Boolean, action: RejectAction) {
    line("reject entity=${op.entity.wireName} op=${op.op.name.lowercase()} id=${op.entityId} code=$code record=$hadRecord action=$action")
}

internal fun describe(record: ServerRecord): String =
    "{id=${record.id}, updatedAt=${record.updatedAt}, deletedAt=${record.deletedAt}, fields=${record.fields}}"

internal fun describe(outcome: PushOutcome): String = when (outcome) {
    is PushOutcome.Applied -> "applied record=${outcome.record != null}"
    is PushOutcome.Conflict -> "conflict record=true"
    is PushOutcome.Rejected -> "rejected code=${outcome.code} record=${outcome.record != null}"
}

internal fun outcomeRecord(outcome: PushOutcome): ServerRecord? = when (outcome) {
    is PushOutcome.Applied -> outcome.record
    is PushOutcome.Conflict -> outcome.record
    is PushOutcome.Rejected -> outcome.record
}

internal fun describe(result: TransportResult<*>): String = when (result) {
    is TransportResult.Ok -> "ok"
    is TransportResult.Retry -> "retry after=${result.after} kind=${result.kind}"
    is TransportResult.Invalid -> "invalid code=${result.code}"
    TransportResult.Unauthorized -> "unauthorized"
    TransportResult.UpdateRequired -> "updateRequired"
    TransportResult.NotAvailable -> "notAvailable"
}
