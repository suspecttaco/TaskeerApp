package com.simposio.taskeer.ui.boards

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainError

sealed interface BoardDialog {
    data object None : BoardDialog

    data object Create : BoardDialog

    data class Rename(val boardId: String, val currentName: String) : BoardDialog

    data class ConfirmDelete(val boardId: String, val name: String) : BoardDialog
}

data class BoardsUiState(
    val boards: List<Board> = emptyList(),
    val dialog: BoardDialog = BoardDialog.None,
    val error: DomainError? = null,
    val deletedBoardId: String? = null,
)
