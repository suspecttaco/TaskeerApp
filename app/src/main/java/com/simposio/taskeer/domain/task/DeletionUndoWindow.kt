package com.simposio.taskeer.domain.task

import java.time.Duration
import java.time.Instant

val UNDO_WINDOW: Duration = Duration.ofSeconds(3)

// Cubre el retraso entre el cierre del snackbar y la siguiente pasada de sincronización.
val UNDO_SYNC_MARGIN: Duration = Duration.ofSeconds(5)

fun deletionSyncReleaseAt(deletedAt: Instant): Instant = deletedAt + UNDO_WINDOW + UNDO_SYNC_MARGIN
