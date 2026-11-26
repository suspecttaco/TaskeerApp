package com.simposio.taskeer.di

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.simposio.taskeer.BuildConfig
import com.simposio.taskeer.data.auth.createRemoteRepositories
import com.simposio.taskeer.data.auth.google.createGoogleCredentialSource
import com.simposio.taskeer.data.calendar.DeviceCalendarRepository
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
import com.simposio.taskeer.data.repository.ClockDayWindowSource
import com.simposio.taskeer.data.repository.ColumnRepositoryImpl
import com.simposio.taskeer.data.repository.LiveRoleChangeSignals
import com.simposio.taskeer.data.repository.MyDayRepositoryImpl
import com.simposio.taskeer.data.repository.NotificationHistoryRepositoryImpl
import com.simposio.taskeer.data.repository.PlannedRepositoryImpl
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.PreferencesRepositoryImpl
import com.simposio.taskeer.data.repository.ReminderCheckpointStoreImpl
import com.simposio.taskeer.data.repository.ReminderRepositoryImpl
import com.simposio.taskeer.data.repository.SearchRepositoryImpl
import com.simposio.taskeer.data.repository.SubtaskRepositoryImpl
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.data.repository.TaskListRepositoryImpl
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.data.repository.TaskRepositoryImpl
import com.simposio.taskeer.domain.auth.GoogleSignInConfig
import com.simposio.taskeer.domain.date.TimeZoneProvider
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun createAppContainer(context: Context): AppContainer {
    val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = {
            context.preferencesDataStoreFile(PreferencesRepositoryImpl.DATA_STORE_NAME)
        },
    )
    val database = AppDatabase.create(context)
    val preferencesRepository = PreferencesRepositoryImpl(dataStore)
    val timeZoneProvider = createPreferredTimeZoneProvider(
        preferencesRepository,
        CoroutineScope(Dispatchers.IO + SupervisorJob()),
    )
    val taskListRepository = createTaskListRepository(database.taskListDao(), database.taskDao())
    val taskRepository = createTaskRepository(
        taskDao = database.taskDao(),
        columnDao = database.columnDao(),
        subtaskDao = database.subtaskDao(),
        reminderDao = database.reminderDao(),
        taskListDao = database.taskListDao(),
        defaultListId = taskListRepository::defaultListId,
        timeZone = timeZoneProvider,
    )
    val reminderRepository = ReminderRepositoryImpl(database.reminderDao(), database.taskDao())
    val notificationHistoryRepository = NotificationHistoryRepositoryImpl(database.appNotificationDao())
    val dayWindowSource = ClockDayWindowSource(Instant::now, timeZoneProvider, zoneChanges = timeZoneProvider.changes)
    val calendarReadGranted = { isGranted(context, Manifest.permission.READ_CALENDAR) }
    val calendarWriteGranted = { isGranted(context, Manifest.permission.WRITE_CALENDAR) }
    val googleSignInConfig = GoogleSignInConfig(BuildConfig.GOOGLE_WEB_CLIENT_ID)
    val remote = createRemoteRepositories(context, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    return AppContainer(
        preferencesRepository = preferencesRepository,
        timeZoneProvider = timeZoneProvider,
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
            timeZone = timeZoneProvider,
        ),
        plannedRepository = PlannedRepositoryImpl(database.myDayDao(), timeZoneProvider, dayWindowSource),
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
            timeZone = timeZoneProvider,
            clock = Instant::now,
        ),
        notificationHistoryRepository = notificationHistoryRepository,
        calendarRepository = DeviceCalendarRepository(
            resolver = context.contentResolver,
            timeZone = timeZoneProvider,
            hasRead = calendarReadGranted,
            hasWrite = calendarWriteGranted,
        ),
        dayWindowSource = dayWindowSource,
        searchRepository = SearchRepositoryImpl(
            database.boardDao(),
            database.columnDao(),
            database.taskDao(),
            database.taskListDao(),
        ),
        authRepository = remote.auth,
        notificationInboxRepository = remote.inbox,
        notificationLiveChannel = remote.live,
        workspaceRepository = remote.workspaces,
        workspaceMembersRepository = remote.workspaceMembers,
        workspaceInvitationsRepository = remote.workspaceInvitations,
        myInvitationsRepository = remote.myInvitations,
        serverBoardsRepository = remote.serverBoards,
        boardAccessRepository = remote.boardAccess,
        roleChangeSignals = LiveRoleChangeSignals(remote.inbox, remote.live),
        googleCredentialSource = createGoogleCredentialSource(googleSignInConfig),
        googleSignInConfig = googleSignInConfig,
        zoneChanges = timeZoneProvider.changes,
        calendarReadGranted = calendarReadGranted,
        calendarWriteGranted = calendarWriteGranted,
        notificationsEnabled = { NotificationManagerCompat.from(context).areNotificationsEnabled() },
        sdkInt = Build.VERSION.SDK_INT,
        animationScale = {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        },
    )
}

// La lectura inicial es bloqueante para que el primer frame y la primera conciliación usen ya la zona elegida.
fun createPreferredTimeZoneProvider(
    preferences: PreferencesRepository,
    scope: CoroutineScope,
): PreferredTimeZoneProvider {
    val initial = runBlocking { preferences.region.first().timeZoneIana }
    val provider = PreferredTimeZoneProvider(SystemTimeZoneProvider, initial)
    scope.launch { preferences.region.collect { provider.update(it.timeZoneIana) } }
    return provider
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

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
