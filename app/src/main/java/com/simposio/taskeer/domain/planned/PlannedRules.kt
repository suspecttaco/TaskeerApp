package com.simposio.taskeer.domain.planned

import com.simposio.taskeer.domain.model.Task

data class PlannedItem(val task: Task, val boardId: String?, val boardName: String?, val listName: String? = null)
