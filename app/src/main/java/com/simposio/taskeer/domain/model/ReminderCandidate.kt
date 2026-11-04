package com.simposio.taskeer.domain.model

import java.time.Instant

data class ReminderCandidate(val taskId: String, val title: String, val dueAt: Instant)

enum class ReminderMechanism { DEADLINE, DIGEST }
