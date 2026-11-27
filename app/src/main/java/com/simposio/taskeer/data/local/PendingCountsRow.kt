package com.simposio.taskeer.data.local

// Las tareas y subtareas se separan por forma (lista o tablero) porque cada una exige entidades distintas en el alcance.
data class PendingCountsRow(
    val lists: Int,
    val listTasks: Int,
    val boardTasks: Int,
    val listSubtasks: Int,
    val boardSubtasks: Int,
)
