package com.simposio.taskeer.domain.sync

import com.simposio.taskeer.domain.task.UNDO_WINDOW
import java.time.Duration
import java.time.Instant

// El borrado de una tarea no sale hasta que vence la ventana de «Deshacer»; el instante es el deletedAt local,
// así que la regla sobrevive a un reinicio.
object DeletionGraceRules {
    fun eligibleAt(deletedAt: Instant, grace: Duration = UNDO_WINDOW): Instant = deletedAt + grace

    fun isEligible(deletedAt: Instant, now: Instant, grace: Duration = UNDO_WINDOW): Boolean =
        !now.isBefore(eligibleAt(deletedAt, grace))
}
