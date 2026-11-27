package com.simposio.taskeer.ui.workspaces

sealed interface ServerBoardRoute {
    // El tablero ya tiene copia local: se abre su kanban.
    data class OpenKanban(val boardId: String) : ServerBoardRoute

    // Aún no se ha traído: se pide sincronizar antes de abrirlo.
    data object SyncFirst : ServerBoardRoute

    // Sin sesión no hay tableros del servidor que abrir.
    data object NotOffered : ServerBoardRoute
}

object ServerBoardRoutingRules {
    fun route(signedIn: Boolean, boardId: String, localBoardIds: Set<String>): ServerBoardRoute = when {
        !signedIn -> ServerBoardRoute.NotOffered
        boardId in localBoardIds -> ServerBoardRoute.OpenKanban(boardId)
        else -> ServerBoardRoute.SyncFirst
    }
}
