package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.ui.components.TaskRowUi
import com.simposio.taskeer.ui.lists.ListTaskUi
import com.simposio.taskeer.ui.myday.MyDaySuggestionUi
import com.simposio.taskeer.ui.myday.MyDayTaskUi
import com.simposio.taskeer.ui.upcoming.PlannedTaskUi

fun MyDayTaskUi.toRow(): TaskRowUi = TaskRowUi(
    id = id,
    title = title,
    isCompleted = isCompleted,
    priority = priority,
    due = due,
    boardName = boardName,
    listName = listName,
    showBoard = true,
    isOverdue = isOverdue,
    isMarked = isMarked,
    isAssigned = isAssigned,
    isInbox = isInbox,
    isRepeating = isRepeating,
    showMyDayStar = false,
)

// Toda tarea de una lista es de bandeja: mostrar el tablero sería ruido.
fun ListTaskUi.toRow(): TaskRowUi = TaskRowUi(
    id = id,
    title = title,
    isCompleted = isCompleted,
    priority = priority,
    due = due,
    boardName = null,
    showBoard = false,
    isOverdue = isOverdue,
    isMarked = isMarked,
    isAssigned = false,
    isInbox = true,
    isRepeating = isRepeating,
    showMyDayStar = isMarked,
)

// La sección ya da el día: la fila solo añade la hora.
fun PlannedTaskUi.toRow(): TaskRowUi = TaskRowUi(
    id = id,
    title = title,
    isCompleted = isCompleted,
    priority = priority,
    due = time.ifEmpty { null },
    boardName = boardName,
    listName = listName,
    showBoard = true,
    isOverdue = isOverdue,
    isMarked = false,
    isAssigned = false,
    isInbox = isInbox,
    isRepeating = isRepeating,
    showMyDayStar = false,
)

fun MyDaySuggestionUi.toRow(): TaskRowUi = TaskRowUi(
    id = id,
    title = title,
    isCompleted = false,
    priority = priority,
    due = due,
    boardName = boardName,
    listName = listName,
    showBoard = true,
    isOverdue = isOverdue,
    isMarked = false,
    isAssigned = false,
    isInbox = boardName == null,
    isRepeating = isRepeating,
    showMyDayStar = false,
)
