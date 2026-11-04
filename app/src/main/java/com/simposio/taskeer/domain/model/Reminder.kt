package com.simposio.taskeer.domain.model

import java.time.Instant

enum class ReminderStatus { PENDING, SENT, DISMISSED }

data class Reminder(
    val taskId: String,
    val remindAt: Instant,
    val status: ReminderStatus,
    val sentAt: Instant?,
    val createdAt: Instant,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isPending: Boolean get() = status == ReminderStatus.PENDING
}

data class DueReminder(val reminder: Reminder, val taskTitle: String)

data class ClaimResult(
    val toNotify: List<DueReminder>,
    val missed: List<Reminder>,
    val discarded: List<Reminder>,
) {
    companion object {
        val Empty = ClaimResult(emptyList(), emptyList(), emptyList())
    }
}
