package com.simposio.taskeer.domain.notification

import java.time.Duration
import java.time.Instant

// Operaciones locales aún no confirmadas por el servidor, para no deshacerlas con un refresco que las cruza.
data class PendingOps(
    val readIds: Set<String> = emptySet(),
    val deletedIds: Set<String> = emptySet(),
    val allRead: Boolean = false,
    val readDeleted: Boolean = false,
) {
    val isEmpty: Boolean get() = readIds.isEmpty() && deletedIds.isEmpty() && !allRead && !readDeleted
}

object InboxRules {
    const val MIN_REFRESH_INTERVAL_SECONDS = 30L

    fun sorted(items: List<ServerNotification>): List<ServerNotification> =
        items.sortedWith(compareByDescending<ServerNotification> { it.createdAt }.thenBy { it.id })

    fun unreadCount(items: List<ServerNotification>): Int = items.count { it.isUnread }

    fun totalUnread(local: Int, server: Int): Int = maxOf(0, local) + maxOf(0, server)

    fun shouldRefresh(lastAttempt: Instant?, now: Instant): Boolean =
        lastAttempt == null || Duration.between(lastAttempt, now).seconds >= MIN_REFRESH_INTERVAL_SECONDS

    fun mergeUnread(current: List<ServerNotification>, unread: List<ServerNotification>): List<ServerNotification> {
        val known = current.mapTo(HashSet()) { it.id }
        return sorted(current + unread.filter { it.id !in known })
    }

    // `readAt` solo se usa para las entradas que el usuario marcó leídas y el servidor aún devuelve sin leer.
    fun reapplyPending(
        fresh: List<ServerNotification>,
        pending: PendingOps,
        readAt: Instant,
    ): List<ServerNotification> {
        if (pending.isEmpty) return fresh
        return fresh
            .filterNot { it.id in pending.deletedIds }
            .map { if (it.isUnread && (pending.allRead || it.id in pending.readIds)) it.copy(readAt = readAt) else it }
            .filterNot { pending.readDeleted && !it.isUnread }
    }
}
