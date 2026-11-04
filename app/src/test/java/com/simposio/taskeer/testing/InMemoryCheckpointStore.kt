package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.ReminderCheckpointStore
import com.simposio.taskeer.domain.model.ReminderMechanism
import java.time.Instant

class InMemoryCheckpointStore : ReminderCheckpointStore {

    private val values = mutableMapOf<ReminderMechanism, Instant>()

    val writes: MutableList<Pair<ReminderMechanism, Instant>> = mutableListOf()

    var onWrite: () -> Unit = {}

    override suspend fun read(mechanism: ReminderMechanism): Instant? = values[mechanism]

    override suspend fun write(mechanism: ReminderMechanism, at: Instant) {
        onWrite()
        writes += mechanism to at
        values[mechanism] = at
    }
}
