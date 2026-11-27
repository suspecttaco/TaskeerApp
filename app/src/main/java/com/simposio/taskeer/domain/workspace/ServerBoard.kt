package com.simposio.taskeer.domain.workspace

data class ServerBoard(val id: String, val name: String) {
    // El nombre del tablero es un dato de usuario: no debe acabar en logs ni mensajes.
    override fun toString(): String = "ServerBoard(id=$id)"
}

data class ServerBoardsState(
    val workspaceId: String? = null,
    val items: List<ServerBoard> = emptyList(),
    val loaded: Boolean = false,
)
