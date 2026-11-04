package com.simposio.taskeer.domain.reminder

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant

const val REMINDER_NOT_FOUND = "REMINDER_NOT_FOUND"
const val REMINDER_NOT_PENDING = "REMINDER_NOT_PENDING"
const val VALIDATION_ERROR = "VALIDATION_ERROR"

// Código local: la API admite varios recordatorios por tarea; la app solo permite uno pendiente.
const val REMINDER_ALREADY_PENDING = "REMINDER_ALREADY_PENDING"

fun validateRemindAt(remindAt: Instant, now: Instant): DomainError? =
    if (remindAt.isAfter(now)) {
        null
    } else {
        DomainError.Validation(VALIDATION_ERROR, "La hora del recordatorio debe ser futura")
    }

fun newReminder(
    taskId: String,
    remindAt: Instant,
    now: Instant,
    clientId: String = newClientId(),
): Reminder = Reminder(
    taskId = taskId,
    remindAt = remindAt,
    status = ReminderStatus.PENDING,
    sentAt = null,
    createdAt = now,
    sync = newSyncMetadata(now, clientId),
)

fun Reminder.dismissed(now: Instant): DomainResult<Reminder> =
    if (isPending) {
        DomainResult.Success(copy(status = ReminderStatus.DISMISSED, sync = sync.afterLocalChange(now)))
    } else {
        DomainResult.Failure(
            DomainError.Validation(REMINDER_NOT_PENDING, "El recordatorio ya no está pendiente"),
        )
    }
