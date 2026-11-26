package com.simposio.taskeer.di

import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.CalendarRepository
import com.simposio.taskeer.data.repository.DayWindowSource
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.NotificationHistoryRepository
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.SearchRepository
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.FirstDayOfWeekProvider
import com.simposio.taskeer.domain.date.SystemFirstDayOfWeekProvider
import com.simposio.taskeer.domain.date.TimeZoneProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class AppContainer(
    val preferencesRepository: PreferencesRepository,
    val timeZoneProvider: TimeZoneProvider,
    val boardRepository: BoardRepository,
    val columnRepository: ColumnRepository,
    val taskRepository: TaskRepository,
    val taskListRepository: TaskListRepository,
    val subtaskRepository: SubtaskRepository,
    val myDayRepository: MyDayRepository,
    val plannedRepository: PlannedRepository,
    val reminderRepository: ReminderRepository,
    val reminderReconciler: ReminderReconciler,
    val notificationHistoryRepository: NotificationHistoryRepository,
    val calendarRepository: CalendarRepository,
    val dayWindowSource: DayWindowSource,
    val searchRepository: SearchRepository,
    val authRepository: AuthRepository,
    val zoneChanges: Flow<Unit> = emptyFlow(),
    val firstDayOfWeek: FirstDayOfWeekProvider = SystemFirstDayOfWeekProvider(),
    val calendarReadGranted: () -> Boolean = { false },
    val calendarWriteGranted: () -> Boolean = { false },
    val notificationsEnabled: () -> Boolean = { true },
    val sdkInt: Int = 0,
    val animationScale: () -> Float = { 1f },
) {
    val dateTimeConverter: DateTimeConverter = DateTimeConverter(timeZoneProvider)
}
