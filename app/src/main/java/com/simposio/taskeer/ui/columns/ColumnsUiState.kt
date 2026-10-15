package com.simposio.taskeer.ui.columns

import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError

data class ColumnsUiState(
    val columns: List<Column> = emptyList(),
    val error: DomainError? = null,
)
