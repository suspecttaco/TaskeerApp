package com.simposio.taskeer.domain.sync

// Qué forma tiene la tarea (o la tarea padre de una subtarea) para decidir si su entidad está en alcance.
data class ScopeRow(val taskInList: Boolean = false, val taskInBoard: Boolean = false)

object SyncScope {
    // S1 y S2 cubren listas, tareas y subtareas; cada fase posterior añade entidades.
    val APP_LISTS_AND_TASKS: Set<SyncEntity> = setOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK)

    // S3 añade tableros y columnas (y con ellos workspaces y acceso, que llegan junto a `board`).
    val APP_WITH_BOARDS: Set<SyncEntity> = APP_LISTS_AND_TASKS + setOf(SyncEntity.BOARD, SyncEntity.COLUMN)

    // S4 añade las marcas de Mi día y los recordatorios, que cuelgan de una tarea.
    val APP_FULL: Set<SyncEntity> = APP_WITH_BOARDS + setOf(SyncEntity.MY_DAY, SyncEntity.REMINDER)

    fun effective(app: Set<SyncEntity>, supported: Set<SyncEntity>): Set<SyncEntity> = app intersect supported

    fun allows(entity: SyncEntity, row: ScopeRow, scope: Set<SyncEntity>): Boolean = when (entity) {
        SyncEntity.TASK -> SyncEntity.TASK in scope && (
            (row.taskInList && SyncEntity.LIST in scope) ||
                (row.taskInBoard && SyncEntity.BOARD in scope && SyncEntity.COLUMN in scope)
            )
        SyncEntity.SUBTASK, SyncEntity.MY_DAY, SyncEntity.REMINDER -> entity in scope && allows(SyncEntity.TASK, row, scope)
        else -> entity in scope
    }
}
