package com.simposio.taskeer.domain.model

enum class TaskPriority {
    LOW, MEDIUM, HIGH, URGENT;

    companion object {
        // Una prioridad que añada el servidor más adelante no puede tumbar la lectura
        // de la tarea: degrada a MEDIUM.
        fun fromStorage(raw: String): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}
