package com.simposio.taskeer.ui.upcoming

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.domain.task.isOverdue
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.tasks.CompletionSettler
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Las dos vistas por fecha comparten las acciones de fila; solo cambia cómo agrupan.
class PlannedRowHandler(
    private val scope: CoroutineScope,
    private val taskRepository: TaskRepository,
    private val rescheduler: TaskRescheduler,
    private val error: MutableStateFlow<DomainError?>,
    settleMillis: () -> Long = { 0L },
) {
    private val settler = CompletionSettler(scope, settleMillis)

    val settling: StateFlow<Set<String>> = settler.settling

    // PICK_DATE abre el selector de la pantalla y no pasa por aquí.
    fun onRowAction(taskId: String, action: RowAction) {
        when (action) {
            RowAction.COMPLETE -> {
                settler.begin(taskId)
                execute { taskRepository.setCompleted(taskId, true) }
            }
            RowAction.RESCHEDULE_TOMORROW -> reschedule(taskId, RescheduleTarget.TOMORROW)
            RowAction.RESCHEDULE_NEXT_WEEK -> reschedule(taskId, RescheduleTarget.NEXT_WEEK)
            RowAction.RESCHEDULE_SOMEDAY -> reschedule(taskId, RescheduleTarget.SOMEDAY)
            else -> Unit
        }
    }

    fun reschedule(taskId: String, target: RescheduleTarget) = execute { rescheduler.reschedule(taskId, target) }

    fun rescheduleTo(taskId: String, date: LocalDate) = execute { rescheduler.rescheduleTo(taskId, date) }

    private fun execute(action: suspend () -> DomainResult<*>) {
        scope.launch {
            when (val result = action()) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun PlannedItem.toPlannedTaskUi(converter: DateTimeConverter, now: Instant): PlannedTaskUi = PlannedTaskUi(
    id = task.id,
    title = task.title,
    priority = task.priority,
    boardName = boardName,
    listName = listName,
    time = task.dueAt?.let { converter.toLocalDateTime(it).format(TIME_FORMATTER) }.orEmpty(),
    isOverdue = isOverdue(task.dueAt, task.isCompleted, now),
    isRepeating = task.repeat != null,
)
