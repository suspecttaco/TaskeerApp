package com.simposio.taskeer.domain.task

import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.NewTaskBundle
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.reminder.newReminder
import com.simposio.taskeer.domain.reminder.validateRemindAt
import com.simposio.taskeer.domain.subtask.newSubtask
import java.time.Instant

fun newTaskWithExtras(
    columnId: String?,
    creation: TaskCreation,
    position: Int,
    now: Instant,
    clientId: String = newClientId(),
): DomainResult<NewTaskBundle> {
    val task = when (val result = newTask(columnId, creation.task, position, now, clientId)) {
        is DomainResult.Failure -> return result
        is DomainResult.Success -> result.value
    }
    val subtasks = mutableListOf<Subtask>()
    creation.subtasks.forEachIndexed { index, draft ->
        when (val result = newSubtask(task.id, draft, index, now)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> subtasks += result.value
        }
    }
    val reminder = creation.remindAt?.let { remindAt ->
        validateRemindAt(remindAt, now)?.let { return DomainResult.Failure(it) }
        newReminder(task.id, remindAt, now)
    }
    return DomainResult.Success(NewTaskBundle(task, subtasks, reminder))
}
