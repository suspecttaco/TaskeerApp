package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.sync.SeededListId

sealed interface GateDecision {
    data object Bound : GateDecision

    data object NeedsBinding : GateDecision

    class Mismatch(val newUserId: String) : GateDecision {
        override fun toString(): String = "Mismatch"
    }
}

// Los datos locales pertenecen a una sola cuenta: nunca se suben a otra (R18).
class SyncSessionGate(private val store: SyncLocalStore) {

    suspend fun evaluate(identity: Identity): GateDecision {
        val bound = store.meta(SyncMetaKeys.BOUND_USER_ID) ?: return GateDecision.NeedsBinding
        return if (bound == identity.userId) GateDecision.Bound else GateDecision.Mismatch(identity.userId)
    }

    // El vínculo se escribe al final: si el proceso muere a mitad, la siguiente pasada repite pasos idempotentes.
    suspend fun bind(userId: String) {
        store.rekeyList(SEEDED_TASK_LIST_ID, SeededListId.forUser(userId))
        store.markAllUnsyncedPending()
        store.setMeta(SyncMetaKeys.BOUND_USER_ID, userId)
    }
}
