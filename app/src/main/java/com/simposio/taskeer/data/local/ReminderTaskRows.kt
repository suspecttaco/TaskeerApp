package com.simposio.taskeer.data.local

data class ReminderCandidateRow(val id: String, val title: String, val dueAt: Long)

data class ReminderTaskStateRow(val title: String, val deleted: Boolean, val completed: Boolean)
