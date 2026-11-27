package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.sync.SyncEngine
import com.simposio.taskeer.data.sync.SyncTriggers
import com.simposio.taskeer.domain.sync.SyncNotice
import com.simposio.taskeer.domain.sync.SyncSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class SyncRepositoryImpl(
    private val engine: SyncEngine,
    private val triggers: SyncTriggers,
) : SyncRepository {

    override val status: Flow<SyncSnapshot> = engine.snapshots
    override val notices: Flow<SyncNotice> = engine.notices
    override val accountSwitchPrompt: StateFlow<Boolean> = engine.accountSwitchPrompt

    override fun requestSync() = triggers.manual()

    override fun notifyForeground() = triggers.foreground()

    override suspend fun confirmAccountWipe() = engine.confirmAccountWipe()

    override fun cancelAccountSwitch() = engine.cancelAccountSwitch()

    suspend fun run() = engine.run()
}
