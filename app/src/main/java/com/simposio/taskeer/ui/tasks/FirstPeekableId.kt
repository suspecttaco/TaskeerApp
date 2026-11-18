package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.TaskRowUi
import com.simposio.taskeer.ui.components.actionsIn

// El asomo revela la acción de la izquierda: solo tiene sentido en una fila que la tiene.
fun firstPeekableId(rows: List<TaskRowUi>, context: RowContext): String? =
    rows.firstOrNull { it.actionsIn(context).swipeStart != null }?.id
