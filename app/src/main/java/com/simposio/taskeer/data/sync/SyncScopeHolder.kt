package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Alcance efectivo (app ∩ supportedEntities). Antes del primer sondeo se asume el de la app; el motor lo ajusta en cuanto lo sabe.
class SyncScopeHolder(initial: Set<SyncEntity> = SyncScope.APP_LISTS_AND_TASKS) {
    private val mutable = MutableStateFlow(initial)

    val scope: StateFlow<Set<SyncEntity>> = mutable.asStateFlow()

    fun update(effective: Set<SyncEntity>) {
        mutable.value = effective
    }
}
