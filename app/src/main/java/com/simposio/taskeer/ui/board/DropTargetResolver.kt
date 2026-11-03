package com.simposio.taskeer.ui.board

data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

data class CardBounds(val id: String, val bounds: Bounds)

data class DropTarget(val columnId: String, val index: Int)

object DropTargetResolver {

    // Bounds propio y no Rect de Compose para poder probarlo en JVM sin Android.
    fun resolve(
        x: Float,
        y: Float,
        columnBounds: Map<String, Bounds>,
        cardsByColumn: Map<String, List<CardBounds>>,
        draggedId: String,
    ): DropTarget? {
        val columnId = columnBounds.entries.firstOrNull { it.value.contains(x, y) }?.key ?: return null
        val index = cardsByColumn[columnId].orEmpty()
            .filter { it.id != draggedId }
            .count { it.bounds.centerY < y }
        return DropTarget(columnId, index)
    }
}
