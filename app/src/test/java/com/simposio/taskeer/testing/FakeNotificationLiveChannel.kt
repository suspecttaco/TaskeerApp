package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.NotificationLiveChannel
import com.simposio.taskeer.domain.notification.LiveChannelState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeNotificationLiveChannel : NotificationLiveChannel {
    val mutableState = MutableStateFlow(LiveChannelState.STOPPED)
    override val state: StateFlow<LiveChannelState> = mutableState

    var runCalls = 0
    val release = CompletableDeferred<Unit>()

    override suspend fun run() {
        runCalls++
        release.await()
    }
}
