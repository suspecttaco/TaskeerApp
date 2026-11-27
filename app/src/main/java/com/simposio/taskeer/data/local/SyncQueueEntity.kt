package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Infraestructura del motor de sincronización, no una entidad sincronizable: no lleva SyncableEntity.
// Solo guarda ids, tipo y metadatos de reintento; el contenido se construye al enviar desde la fila vigente.
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["entity", "entityId"], unique = true), Index(value = ["state", "nextAttemptAt"])],
)
data class SyncQueueEntity(
    @PrimaryKey val mutationId: String,
    val entity: String,
    val entityId: String,
    val op: String,
    val enqueuedAt: Long,
    // updatedAt de la fila cuando se encoló o refrescó la entrada: detecta ediciones posteriores a un envío.
    val payloadVersion: Long,
    // Fallos transitorios (red, 429, 5xx): mueven el retroceso y nunca revierten.
    val attempts: Int,
    // Fallos que ningún reintento arregla (cuerpo inválido): a partir del umbral la entrada queda atascada.
    val failures: Int,
    val nextAttemptAt: Long?,
    val lastErrorCode: String?,
    val state: String,
)

const val QUEUE_STATE_QUEUED: String = "QUEUED"
const val QUEUE_STATE_STUCK: String = "STUCK"
