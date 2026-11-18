package com.simposio.taskeer.ui.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Las filas en asentamiento conservan su índice anterior: el reordenado visual espera, la escritura no.
// Con asCompleted, una fila en asentamiento que ya salió de `next` (vistas por fecha) se retiene
// marcada como completada; sin él, ausente significa borrada y no se retiene.
fun <T> settleOrder(
    previous: List<T>,
    next: List<T>,
    settling: Set<String>,
    id: (T) -> String,
    asCompleted: ((T) -> T)? = null,
): List<T> {
    if (settling.isEmpty()) return next
    val previousIndex = previous.withIndex().associate { id(it.value) to it.index }
    val nextById = next.associateBy(id)
    val held = settling
        .filter { it in previousIndex }
        .mapNotNull { key ->
            val element = nextById[key] ?: asCompleted?.invoke(previous[previousIndex.getValue(key)])
            element?.let { key to it }
        }
        .sortedBy { previousIndex.getValue(it.first) }
    if (held.isEmpty()) return next
    val heldIds = held.map { it.first }.toSet()
    val result = next.filter { id(it) !in heldIds }.toMutableList()
    held.forEach { (key, element) -> result.add(previousIndex.getValue(key).coerceAtMost(result.size), element) }
    return result
}

class CompletionSettler(
    private val scope: CoroutineScope,
    private val settleMillis: () -> Long,
) {
    private val _settling = MutableStateFlow<Set<String>>(emptySet())
    val settling: StateFlow<Set<String>> = _settling.asStateFlow()

    private val timers = mutableMapOf<String, Job>()

    fun begin(id: String) {
        val millis = settleMillis()
        if (millis <= 0L) return
        timers.remove(id)?.cancel()
        _settling.update { it + id }
        timers[id] = scope.launch {
            delay(millis)
            _settling.update { it - id }
            timers.remove(id)
        }
    }
}
