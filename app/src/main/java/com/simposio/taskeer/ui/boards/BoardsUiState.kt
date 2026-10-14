package com.simposio.taskeer.ui.boards

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainError

data class BoardsUiState(
    val boards: List<Board> = emptyList(),
    val error: DomainError? = null,
)
