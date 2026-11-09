package com.simposio.taskeer.domain.model

import com.simposio.taskeer.domain.column.checkWipLimit

data class ColumnDestination(val column: Column, val activeCount: Int) {
    val isFull: Boolean get() = checkWipLimit(column, activeCount) != null
}

data class BoardDestination(val board: Board, val columns: List<ColumnDestination>)
