package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.ClaimResult
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Reminder
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeReminder(taskId: String): Flow<Reminder?>

    fun observePending(): Flow<List<Reminder>>

    suspend fun createReminder(taskId: String, remindAt: Instant): DomainResult<Reminder>

    suspend fun cancelReminder(id: String): DomainResult<Unit>

    suspend fun claimDue(now: Instant): ClaimResult

    suspend fun nextPendingAt(now: Instant): Instant?
}
