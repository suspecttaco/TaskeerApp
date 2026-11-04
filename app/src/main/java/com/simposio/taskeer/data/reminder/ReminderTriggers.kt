package com.simposio.taskeer.data.reminder

import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.ReminderMechanism
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ReminderTriggers(
    private val candidates: Flow<List<ReminderCandidate>>,
    private val deadline: Flow<DeadlineReminderSettings>,
    private val digest: Flow<DigestSettings>,
    private val pending: Flow<List<Reminder>>,
    private val reconcile: suspend (Set<ReminderMechanism>) -> Unit,
) {
    // Solo cuentan los campos que cambian cuándo avisa cada mecanismo: editar un título no debe
    // silenciar un aviso vencido.
    private data class Snapshot(
        val dueDates: Set<Pair<String, Instant>>,
        val deadline: DeadlineReminderSettings,
        val digest: DigestSettings,
        val pending: Set<Pair<String, Instant>>,
    )

    suspend fun run() {
        var previous: Snapshot? = null
        combine(candidates, deadline, digest, pending) { tasks, deadlineSettings, digestSettings, due ->
            Snapshot(
                dueDates = tasks.map { it.taskId to it.dueAt }.toSet(),
                deadline = deadlineSettings,
                digest = digestSettings,
                pending = due.map { it.id to it.remindAt }.toSet(),
            )
        }.collect { snapshot ->
            val before = previous
            previous = snapshot
            if (before == null) {
                reconcile(emptySet())
            } else if (before != snapshot) {
                reconcile(silenceFor(before, snapshot))
            }
        }
    }

    private fun silenceFor(before: Snapshot, after: Snapshot): Set<ReminderMechanism> = buildSet {
        if (before.dueDates != after.dueDates || before.deadline != after.deadline) {
            add(ReminderMechanism.DEADLINE)
        }
        if (before.digest != after.digest) {
            add(ReminderMechanism.DIGEST)
        }
    }
}
