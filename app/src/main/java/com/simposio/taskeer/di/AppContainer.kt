package com.simposio.taskeer.di

import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider

class AppContainer(
    val preferencesRepository: PreferencesRepository,
    val timeZoneProvider: TimeZoneProvider,
    val boardRepository: BoardRepository,
    val columnRepository: ColumnRepository,
    val taskRepository: TaskRepository,
    val subtaskRepository: SubtaskRepository,
    val myDayRepository: MyDayRepository,
    val reminderRepository: ReminderRepository,
    val reminderReconciler: ReminderReconciler,
    val notificationsEnabled: () -> Boolean = { true },
    val sdkInt: Int = 0,
) {
    val dateTimeConverter: DateTimeConverter = DateTimeConverter(timeZoneProvider)
}
