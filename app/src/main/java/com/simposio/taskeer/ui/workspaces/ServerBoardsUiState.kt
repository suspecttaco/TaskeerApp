package com.simposio.taskeer.ui.workspaces

enum class ServerBoardsPhase { LOADING, LIST, EMPTY, ERROR }

data class BoardRowUi(val id: String, val name: String) {
    // El nombre del tablero es un dato de usuario: no debe acabar en logs ni mensajes.
    override fun toString(): String = "BoardRowUi(redactada)"
}

data class ServerBoardsUiState(
    val phase: ServerBoardsPhase = ServerBoardsPhase.LOADING,
    val rows: List<BoardRowUi> = emptyList(),
    val refreshing: Boolean = false,
    val error: BoardRoleErrorMessage? = null,
)

sealed interface ServerBoardsEvent {
    data class ShowError(val message: BoardRoleErrorMessage) : ServerBoardsEvent

    data class OpenAccess(val boardId: String) : ServerBoardsEvent

    data object LeaveScreen : ServerBoardsEvent
}
