package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.NotificationInboxRepository
import com.simposio.taskeer.domain.notification.InboxOutcome
import com.simposio.taskeer.domain.notification.InboxRules
import com.simposio.taskeer.domain.notification.InboxState
import com.simposio.taskeer.domain.notification.ServerNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakeNotificationInboxRepository(initial: InboxState = InboxState()) : NotificationInboxRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<InboxState> = mutableState

    val mutableLiveArrivals = MutableSharedFlow<ServerNotification>(extraBufferCapacity = 16)
    override val liveArrivals: SharedFlow<ServerNotification> = mutableLiveArrivals

    var result: InboxOutcome = InboxOutcome.Done
    var refreshItems: List<ServerNotification>? = null
    var refreshUnreadCalls = 0
    var refreshCalls = 0
    var catchUpCalls = 0
    var onCatchUp: () -> Unit = {}
    val liveApplied = mutableListOf<ServerNotification>()
    val markedRead = mutableListOf<String>()
    var markAllReadCalls = 0
    val deleted = mutableListOf<String>()
    var deleteReadCalls = 0

    override suspend fun refreshUnread(): InboxOutcome {
        refreshUnreadCalls++
        return result
    }

    override suspend fun catchUp(): InboxOutcome {
        catchUpCalls++
        onCatchUp()
        return result
    }

    override fun applyLive(notification: ServerNotification) {
        liveApplied += notification
        val current = mutableState.value
        if (current.items.any { it.id == notification.id }) return
        mutableLiveArrivals.tryEmit(notification)
        mutableState.value = if (current.listLoaded) {
            val items = InboxRules.mergeLive(current.items, listOf(notification))
            current.copy(items = items, unreadCount = InboxRules.unreadCount(items))
        } else {
            current.copy(unreadCount = current.unreadCount + if (notification.isUnread) 1 else 0)
        }
    }

    override suspend fun refresh(): InboxOutcome {
        refreshCalls++
        refreshItems?.let { items ->
            val sorted = InboxRules.sorted(items)
            mutableState.value = InboxState(sorted, InboxRules.unreadCount(sorted), listLoaded = true)
        }
        return result
    }

    override suspend fun markRead(id: String): InboxOutcome {
        markedRead += id
        return result
    }

    override suspend fun markAllRead(): InboxOutcome {
        markAllReadCalls++
        return result
    }

    override suspend fun delete(id: String): InboxOutcome {
        deleted += id
        return result
    }

    override suspend fun deleteRead(): InboxOutcome {
        deleteReadCalls++
        return result
    }
}
