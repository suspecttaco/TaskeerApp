package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.MyDayTask
import java.time.Instant

fun MyDayRow.toMyDayTask(): MyDayTask = MyDayTask(
    task = task.toTask(),
    boardId = boardId,
    boardName = boardName,
    addedAt = addedAt?.let(Instant::ofEpochMilli),
)
