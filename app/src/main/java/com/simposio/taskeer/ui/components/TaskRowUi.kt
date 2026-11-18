package com.simposio.taskeer.ui.components

import com.simposio.taskeer.domain.model.TaskPriority

data class TaskRowUi(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val priority: TaskPriority,
    val due: String?,
    val boardName: String?,
    val showBoard: Boolean,
    val isOverdue: Boolean,
    val isMarked: Boolean,
    val isAssigned: Boolean,
    val isInbox: Boolean,
    val isRepeating: Boolean,
    val showMyDayStar: Boolean,
)
