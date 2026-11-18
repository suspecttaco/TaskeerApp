package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.domain.task.dueForQuickAdd
import com.simposio.taskeer.domain.task.rescheduledDue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

const val TASK_REPEAT_NOT_RESCHEDULABLE = "TASK_REPEAT_NOT_RESCHEDULABLE"

class TaskRescheduler(
    private val taskRepository: TaskRepository,
    private val converter: DateTimeConverter,
    private val today: () -> LocalDate = { converter.toLocalDateTime(Instant.now()).toLocalDate() },
) {

    suspend fun reschedule(taskId: String, target: RescheduleTarget): DomainResult<Unit> =
        update(taskId) { task ->
            rescheduledDue(task.dueAt?.let(converter::toLocalDateTime), target, today())
        }

    suspend fun rescheduleTo(taskId: String, date: LocalDate): DomainResult<Unit> =
        update(taskId) { task ->
            dueForQuickAdd(date, task.dueAt?.let(converter::toLocalDateTime)?.toLocalTime())
        }

    private suspend fun update(taskId: String, newDue: (Task) -> LocalDateTime?): DomainResult<Unit> {
        val task = when (val found = taskRepository.getTask(taskId)) {
            is DomainResult.Failure -> return found
            is DomainResult.Success -> found.value
        }
        if (task.repeat != null) {
            return DomainResult.Failure(
                DomainError.Validation(
                    TASK_REPEAT_NOT_RESCHEDULABLE,
                    "Una tarea que se repite no se puede reprogramar desde la lista",
                ),
            )
        }
        val draft = TaskDraft(
            title = task.title,
            description = task.description,
            priority = task.priority,
            dueAt = newDue(task)?.let(converter::toInstant),
            assigneeId = task.assigneeId,
            repeat = task.repeat,
        )
        return when (val result = taskRepository.updateTask(taskId, draft)) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> DomainResult.Success(Unit)
        }
    }
}
