package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.ReminderMechanism
import java.time.Instant

interface ReminderCheckpointStore {
    suspend fun read(mechanism: ReminderMechanism): Instant?

    suspend fun write(mechanism: ReminderMechanism, at: Instant)
}
