package com.simposio.taskeer.di

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.simposio.taskeer.data.local.AppDatabase
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ReminderDao
import com.simposio.taskeer.data.local.SubtaskDao
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskListDao
import com.simposio.taskeer.data.reminder.AlarmReminderScheduler
import com.simposio.taskeer.data.reminder.HistoryRecordingNotifier
import com.simposio.taskeer.data.reminder.NotificationReminderNotifier
import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.BoardRepositoryImpl
import com.simposio.taskeer.data.repository.ColumnRepositoryImpl
import com.simposio.taskeer.data.repository.MyDayRepositoryImpl
import com.simposio.taskeer.data.repository.NotificationHistoryRepositoryImpl
import com.simposio.taskeer.data.repository.PlannedRepositoryImpl
import com.simposio.taskeer.data.repository.PreferencesRepositoryImpl
import com.simposio.taskeer.data.repository.ReminderCheckpointStoreImpl
import com.simposio.taskeer.data.repository.ReminderRepositoryImpl
import com.simposio.taskeer.data.repository.SubtaskRepositoryImpl
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.data.repository.TaskListRepositoryImpl
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.data.repository.TaskRepositoryImpl
import com.simposio.taskeer.domain.date.TimeZoneProvider
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun createAppContainer(context: Context): AppContainer {
    val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = {
            context.preferencesDataStoreFile(PreferencesRepositoryImpl.DATA_STORE_NAME)
        },
    )
    val database = AppDatabase.create(context)
    val preferencesRepository = PreferencesRepositoryImpl(dataStore)
    val taskListRepository = createTaskListRepository(database.taskListDao(), database.taskDao())
    val taskRepository = createTaskRepository(
        taskDao = database.taskDao(),
        columnDao = database.columnDao(),
        subtaskDao = database.subtaskDao(),
        reminderDao = database.reminderDao(),
        taskListDao = database.taskListDao(),
        defaultListId = taskListRepository::defaultListId,
        timeZone = SystemTimeZoneProvider,
    )
    val reminderRepository = ReminderRepositoryImpl(database.reminderDao(), database.taskDao())
    val notificationHistoryRepository = NotificationHistoryRepositoryImpl(database.appNotificationDao())
    return AppContainer(
        preferencesRepository = preferencesRepository,
        timeZoneProvider = SystemTimeZoneProvider,
        boardRepository = BoardRepositoryImpl(database.boardDao(), database.columnDao()),
        columnRepository = ColumnRepositoryImpl(
            database.boardDao(),
            database.columnDao(),
            columnTaskCount = taskCountByColumn(database.taskDao()),
        ),
        taskRepository = taskRepository,
        taskListRepository = taskListRepository,
        subtaskRepository = SubtaskRepositoryImpl(database.subtaskDao(), database.taskDao()),
        myDayRepository = MyDayRepositoryImpl(
            database.myDayDao(),
            database.taskDao(),
            clock = Instant::now,
            timeZone = SystemTimeZoneProvider,
        ),
        plannedRepository = PlannedRepositoryImpl(database.myDayDao(), SystemTimeZoneProvider),
        reminderRepository = reminderRepository,
        reminderReconciler = ReminderReconciler(
            tasks = taskRepository,
            reminders = reminderRepository,
            preferences = preferencesRepository,
            checkpoints = ReminderCheckpointStoreImpl(dataStore),
            scheduler = AlarmReminderScheduler(context),
            notifier = HistoryRecordingNotifier(
                delegate = NotificationReminderNotifier(context),
                history = notificationHistoryRepository,
            ),
            timeZone = SystemTimeZoneProvider,
            clock = Instant::now,
        ),
        notificationHistoryRepository = notificationHistoryRepository,
        notificationsEnabled = { NotificationManagerCompat.from(context).areNotificationsEnabled() },
        sdkInt = Build.VERSION.SDK_INT,
        animationScale = {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        },
    )
}

// Función aparte y no una línea en createAppContainer para poder probarla en un test JVM.
fun taskCountByColumn(taskDao: TaskDao): suspend (String) -> Int =
    { columnId -> taskDao.countByColumn(columnId) }

fun createTaskRepository(
    taskDao: TaskDao,
    columnDao: ColumnDao,
    subtaskDao: SubtaskDao,
    reminderDao: ReminderDao,
    taskListDao: TaskListDao,
    defaultListId: suspend () -> String,
    timeZone: TimeZoneProvider,
): TaskRepository =
    TaskRepositoryImpl(taskDao, columnDao, subtaskDao, reminderDao, taskListDao, defaultListId, timeZone = timeZone)

fun createTaskListRepository(taskListDao: TaskListDao, taskDao: TaskDao): TaskListRepository =
    TaskListRepositoryImpl(taskListDao, taskDao)
