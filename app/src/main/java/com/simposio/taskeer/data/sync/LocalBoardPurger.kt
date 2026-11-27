package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.SyncDao

// Retira de la base local un tablero que el usuario dejó de ver, con todo lo que cuelga de él (columnas, tareas, subtareas,
// marcas de Mi día y recordatorios), y su cola y versiones base. Es un borrado físico: el servidor ya no lo ve, así que no
// hay tombstone que enviar y nada de ello puede volver a subirse.
class LocalBoardPurger(private val dao: SyncDao) {
    // Verdadero si el tablero existía en local.
    suspend fun purge(boardId: String): Boolean = dao.purgeBoard(boardId)
}
