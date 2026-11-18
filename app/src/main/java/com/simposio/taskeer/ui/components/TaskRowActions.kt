package com.simposio.taskeer.ui.components

enum class RowContext { MY_DAY, INBOX, PLANNED, SUGGESTION }

enum class RowAction {
    COMPLETE,
    REOPEN,
    ADD_TO_MY_DAY,
    REMOVE_FROM_MY_DAY,
    KEEP_TODAY,
    DISMISS,
    RESCHEDULE_TOMORROW,
    RESCHEDULE_NEXT_WEEK,
    RESCHEDULE_SOMEDAY,
    PICK_DATE,
    ASSIGN_TO_BOARD,
}

data class RowActions(val swipeEnd: RowAction?, val swipeStart: RowAction?, val sheet: List<RowAction>)

private val RESCHEDULE_ACTIONS = listOf(
    RowAction.RESCHEDULE_TOMORROW,
    RowAction.RESCHEDULE_NEXT_WEEK,
    RowAction.RESCHEDULE_SOMEDAY,
    RowAction.PICK_DATE,
)

// Las tareas con repetición no se reprograman desde la fila: moverlas reanclaría la regla de repetición.
fun rowActions(
    context: RowContext,
    isCompleted: Boolean,
    isMarked: Boolean,
    isInbox: Boolean,
    isRepeating: Boolean,
): RowActions {
    val complete = if (isCompleted) RowAction.REOPEN else RowAction.COMPLETE
    val reschedule = if (isRepeating) emptyList() else RESCHEDULE_ACTIONS
    return when (context) {
        RowContext.MY_DAY -> {
            val remove = RowAction.REMOVE_FROM_MY_DAY.takeIf { isMarked }
            RowActions(
                swipeEnd = complete,
                swipeStart = remove,
                sheet = listOfNotNull(complete, remove) + reschedule +
                    listOfNotNull(RowAction.ASSIGN_TO_BOARD.takeIf { isInbox }),
            )
        }
        RowContext.INBOX -> {
            val toggleMyDay = if (isMarked) RowAction.REMOVE_FROM_MY_DAY else RowAction.ADD_TO_MY_DAY
            RowActions(
                swipeEnd = complete,
                swipeStart = toggleMyDay,
                sheet = listOf(complete, toggleMyDay) + reschedule +
                    listOfNotNull(RowAction.ASSIGN_TO_BOARD.takeIf { isInbox }),
            )
        }
        RowContext.PLANNED -> RowActions(
            swipeEnd = RowAction.COMPLETE,
            swipeStart = RowAction.RESCHEDULE_TOMORROW.takeIf { !isRepeating },
            sheet = listOf(RowAction.COMPLETE) + reschedule,
        )
        RowContext.SUGGESTION -> RowActions(
            swipeEnd = RowAction.KEEP_TODAY,
            swipeStart = RowAction.DISMISS,
            sheet = listOfNotNull(
                RowAction.KEEP_TODAY,
                RowAction.RESCHEDULE_TOMORROW.takeIf { !isRepeating },
                RowAction.COMPLETE,
                RowAction.DISMISS,
            ),
        )
    }
}

fun TaskRowUi.actionsIn(context: RowContext): RowActions =
    rowActions(context, isCompleted, isMarked, isInbox, isRepeating)
