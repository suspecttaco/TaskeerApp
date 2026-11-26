package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.RoleChangeSignals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeRoleChangeSignals : RoleChangeSignals {
    val mutableSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val signals: Flow<Unit> = mutableSignals

    fun emit() {
        mutableSignals.tryEmit(Unit)
    }
}
