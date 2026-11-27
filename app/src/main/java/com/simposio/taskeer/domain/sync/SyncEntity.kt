package com.simposio.taskeer.domain.sync

// El orden de declaración es el de dependencias: un padre precede siempre a sus hijos.
// WORKSPACE y BOARD_ACCESS solo se leen (llegan en `changes`): nunca se encolan ni se envían.
enum class SyncEntity(val wireName: String) {
    WORKSPACE("workspace"),
    BOARD_ACCESS("boardAccess"),
    LIST("list"),
    BOARD("board"),
    COLUMN("column"),
    TASK("task"),
    SUBTASK("subtask"),
    MY_DAY("myDay"),
    REMINDER("reminder"),
    ;

    companion object {
        fun fromWire(name: String): SyncEntity? = entries.firstOrNull { it.wireName == name }
    }
}

enum class SyncOp { UPSERT, DELETE }
