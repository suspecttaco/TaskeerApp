package com.simposio.taskeer.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

internal enum class NoticeKind { INFO, ERROR, ACTION, UNDO }

internal fun noticeDuration(kind: NoticeKind): SnackbarDuration = when (kind) {
    NoticeKind.INFO -> SnackbarDuration.Short
    NoticeKind.ERROR, NoticeKind.ACTION -> SnackbarDuration.Long
    NoticeKind.UNDO -> SnackbarDuration.Indefinite
}

// INFO es breve y UNDO lo cierra el ViewModel al vencer su ventana: ninguno necesita botón de cierre.
internal fun noticeDismissible(kind: NoticeKind): Boolean = kind != NoticeKind.INFO && kind != NoticeKind.UNDO

internal suspend fun SnackbarHostState.showNotice(
    message: String,
    kind: NoticeKind,
    actionLabel: String? = null,
): SnackbarResult = showSnackbar(
    message = message,
    actionLabel = actionLabel,
    withDismissAction = noticeDismissible(kind),
    duration = noticeDuration(kind),
)
