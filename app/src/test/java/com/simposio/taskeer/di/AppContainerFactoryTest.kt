package com.simposio.taskeer.di

import com.simposio.taskeer.data.local.ColumnCountRow
import com.simposio.taskeer.data.local.ListCountRow
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.SearchTaskRow
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.region.RegionPreferences
import com.simposio.taskeer.testing.FakeAuthRepository
import com.simposio.taskeer.testing.FakeCalendarRepository
import com.simposio.taskeer.testing.FakeDayWindowSource
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakeSearchRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import com.simposio.taskeer.testing.UnusedColumnDao
import com.simposio.taskeer.testing.UnusedReminderDao
import com.simposio.taskeer.testing.UnusedSubtaskDao
import com.simposio.taskeer.testing.UnusedTaskListDao
import com.simposio.taskeer.ui.myday.MyDayViewModel
import com.simposio.taskeer.ui.tasks.RemindersViewModel
import com.simposio.taskeer.ui.tasks.SubtasksViewModel
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppContainerFactoryTest {

    @Test
    fun elConteoDeTareasPorColumna_usaElDaoDeTareas() = runTest {
        val dao = FakeTaskDao()
        dao.put(taskEntity("t1", columnId = "columna-1"))
        dao.put(taskEntity("t2", columnId = "columna-1"))
        dao.put(taskEntity("t3", columnId = "columna-2"))
        val count = taskCountByColumn(dao)

        assertEquals(2, count("columna-1"))
        assertEquals(1, count("columna-2"))
        assertEquals(0, count("columna-3"))
        assertEquals(listOf("columna-1", "columna-2", "columna-3"), dao.countedColumns)
    }

    @Test
    fun taskRepository_recibeElProveedorDeZonaHoraria() {
        val zone = TimeZoneProvider { java.time.ZoneOffset.UTC }

        val repository = createTaskRepository(
            FakeTaskDao(),
            UnusedColumnDao(),
            UnusedSubtaskDao(),
            UnusedReminderDao(),
            UnusedTaskListDao(),
            { "lista" },
            zone,
        )

        val field = repository.javaClass.getDeclaredField("timeZone").apply { isAccessible = true }
        assertSame(zone, field.get(repository))
        assertTrue(
            "el contenedor debe cablear el proveedor de zona elegida",
            factorySource().contains("timeZone = timeZoneProvider"),
        )
    }

    @Test
    fun taskListRepository_seConstruyeConElDao() {
        val listDao = UnusedTaskListDao()
        val taskDao = FakeTaskDao()

        val repository = createTaskListRepository(listDao, taskDao)

        val listField = repository.javaClass.getDeclaredField("listDao").apply { isAccessible = true }
        val taskField = repository.javaClass.getDeclaredField("taskDao").apply { isAccessible = true }
        assertSame(listDao, listField.get(repository))
        assertSame(taskDao, taskField.get(repository))
        assertTrue(
            "el contenedor debe construir el repositorio de listas con los DAOs de la base",
            factorySource().contains("createTaskListRepository(database.taskListDao(), database.taskDao())"),
        )
    }

    @Test
    fun unaSolaInstanciaDeZonaEnTodosLosConsumidores() {
        val source = factorySource()
        val body = source.substringAfter("fun createAppContainer(").substringBefore("// La lectura inicial")

        assertEquals(1, Regex("createPreferredTimeZoneProvider\\(").findAll(body).count())
        assertTrue(!body.contains("SystemTimeZoneProvider"))
        listOf(
            "timeZoneProvider = timeZoneProvider,",
            "PlannedRepositoryImpl(database.myDayDao(), timeZoneProvider, dayWindowSource)",
            "ClockDayWindowSource(Instant::now, timeZoneProvider,",
        ).forEach { assertTrue("falta $it", body.contains(it)) }
        assertEquals(4, Regex("timeZone = timeZoneProvider,").findAll(body).count())
        assertTrue(body.contains("zoneChanges = timeZoneProvider.changes"))
    }

    @Test
    fun proveedorDeZona_leeLaEleccionInicialYSigueLosCambios() = runTest {
        val preferences = FakePreferencesRepository(region = RegionPreferences(null, "Asia/Tokyo"))

        val provider = createPreferredTimeZoneProvider(
            preferences,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
        )
        assertEquals(java.time.ZoneId.of("Asia/Tokyo"), provider.current())

        preferences.setTimeZone("America/Bogota")
        assertEquals(java.time.ZoneId.of("America/Bogota"), provider.current())
    }

    private fun factorySource(): String {
        val relative = "app/src/main/java/com/simposio/taskeer/di/AppContainerFactory.kt"
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, relative) }
            .first { it.isFile }
            .readText()
    }

    @Test
    fun calendario_comparteLaVentanaDeDiaYLeeLosPermisosConContextCompat() {
        val source = factorySource()

        assertTrue(source.contains("val dayWindowSource = ClockDayWindowSource(Instant::now, timeZoneProvider, zoneChanges = timeZoneProvider.changes)"))
        assertTrue(source.contains("PlannedRepositoryImpl(database.myDayDao(), timeZoneProvider, dayWindowSource)"))
        assertTrue(source.contains("dayWindowSource = dayWindowSource"))
        assertTrue(source.contains("ContextCompat.checkSelfPermission(context, permission)"))
        assertTrue(source.contains("Manifest.permission.READ_CALENDAR") && source.contains("Manifest.permission.WRITE_CALENDAR"))
        assertTrue(source.contains("hasRead = calendarReadGranted") && source.contains("hasWrite = calendarWriteGranted"))
    }

    @Test
    fun factory_construyeSubtasksViewModel() {
        val container = AppContainer(
            preferencesRepository = stub<PreferencesRepository>(),
            timeZoneProvider = { java.time.ZoneOffset.UTC },
            boardRepository = stub<BoardRepository>(),
            columnRepository = stub<ColumnRepository>(),
            taskRepository = stub<TaskRepository>(),
            taskListRepository = FakeTaskListRepository(),
            subtaskRepository = stub<SubtaskRepository>(),
            myDayRepository = stub<MyDayRepository>(),
            plannedRepository = stub<PlannedRepository>(),
            reminderRepository = stub<ReminderRepository>(),
            reminderReconciler = reconciler(),
            notificationHistoryRepository = FakeNotificationHistoryRepository(),
            calendarRepository = FakeCalendarRepository(),
            dayWindowSource = FakeDayWindowSource(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
            searchRepository = FakeSearchRepository(),
            authRepository = FakeAuthRepository(),
        )

        val viewModel = AppViewModelFactory(container).create(SubtasksViewModel::class.java)

        assertEquals(SubtasksViewModel::class.java, viewModel.javaClass)
    }

    @Test
    fun factory_construyeMyDayViewModelConElRepositorioDeTareas() {
        val tasks = FakeDestinationTaskRepository()
        val myDay = Proxy.newProxyInstance(
            MyDayRepository::class.java.classLoader,
            arrayOf(MyDayRepository::class.java),
        ) { _, method, _ ->
            if (method.returnType == Flow::class.java) emptyFlow<Any>() else error("${method.name} no se usa")
        } as MyDayRepository
        val container = AppContainer(
            preferencesRepository = stub<PreferencesRepository>(),
            timeZoneProvider = { java.time.ZoneOffset.UTC },
            boardRepository = stub<BoardRepository>(),
            columnRepository = stub<ColumnRepository>(),
            taskRepository = tasks,
            taskListRepository = FakeTaskListRepository(),
            subtaskRepository = stub<SubtaskRepository>(),
            myDayRepository = myDay,
            plannedRepository = stub<PlannedRepository>(),
            reminderRepository = stub<ReminderRepository>(),
            reminderReconciler = reconciler(),
            notificationHistoryRepository = FakeNotificationHistoryRepository(),
            calendarRepository = FakeCalendarRepository(),
            dayWindowSource = FakeDayWindowSource(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
            searchRepository = FakeSearchRepository(),
            authRepository = FakeAuthRepository(),
        )

        val viewModel = AppViewModelFactory(container).create(MyDayViewModel::class.java)

        val field = MyDayViewModel::class.java.getDeclaredField("taskRepository").apply { isAccessible = true }
        assertSame(tasks, field.get(viewModel))
    }

    @Test
    fun plannedRepository_estaCableado() {
        val planned = Proxy.newProxyInstance(
            PlannedRepository::class.java.classLoader,
            arrayOf(PlannedRepository::class.java),
        ) { _, method, _ -> error("${method.name} no se usa") } as PlannedRepository
        val container = AppContainer(
            preferencesRepository = stub<PreferencesRepository>(),
            timeZoneProvider = { java.time.ZoneOffset.UTC },
            boardRepository = stub<BoardRepository>(),
            columnRepository = stub<ColumnRepository>(),
            taskRepository = stub<TaskRepository>(),
            taskListRepository = FakeTaskListRepository(),
            subtaskRepository = stub<SubtaskRepository>(),
            myDayRepository = stub<MyDayRepository>(),
            plannedRepository = planned,
            reminderRepository = stub<ReminderRepository>(),
            reminderReconciler = reconciler(),
            notificationHistoryRepository = FakeNotificationHistoryRepository(),
            calendarRepository = FakeCalendarRepository(),
            dayWindowSource = FakeDayWindowSource(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
            searchRepository = FakeSearchRepository(),
            authRepository = FakeAuthRepository(),
        )

        assertSame(planned, container.plannedRepository)
        val source = factorySource()
        assertTrue(source.contains("plannedRepository = PlannedRepositoryImpl("))
        assertTrue(source.contains("MyDayRepositoryImpl("))
    }

    @Test
    fun factory_construyeRemindersViewModel() {
        val container = AppContainer(
            preferencesRepository = stub<PreferencesRepository>(),
            timeZoneProvider = { java.time.ZoneOffset.UTC },
            boardRepository = stub<BoardRepository>(),
            columnRepository = stub<ColumnRepository>(),
            taskRepository = stub<TaskRepository>(),
            taskListRepository = FakeTaskListRepository(),
            subtaskRepository = stub<SubtaskRepository>(),
            myDayRepository = stub<MyDayRepository>(),
            plannedRepository = stub<PlannedRepository>(),
            reminderRepository = stub<ReminderRepository>(),
            reminderReconciler = reconciler(),
            notificationHistoryRepository = FakeNotificationHistoryRepository(),
            calendarRepository = FakeCalendarRepository(),
            dayWindowSource = FakeDayWindowSource(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
            searchRepository = FakeSearchRepository(),
            authRepository = FakeAuthRepository(),
        )

        val viewModel = AppViewModelFactory(container).create(RemindersViewModel::class.java)

        assertEquals(RemindersViewModel::class.java, viewModel.javaClass)
    }

    private fun reconciler() = ReminderReconciler(
        tasks = stub<TaskRepository>(),
        reminders = stub<ReminderRepository>(),
        preferences = stub<PreferencesRepository>(),
        checkpoints = InMemoryCheckpointStore(),
        scheduler = RecordingReminderScheduler(),
        notifier = RecordingNotifier(),
        timeZone = { java.time.ZoneOffset.UTC },
        clock = { java.time.Instant.EPOCH },
    )

    private inline fun <reified T : Any> stub(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("${method.name} no se usa en este test") } as T

    private class FakeTaskDao : TaskDao() {
        override fun observeSearchable(): Flow<List<SearchTaskRow>> = emptyFlow()

        override suspend fun findActiveByList(listId: String): List<TaskEntity> = error("no se usa")

        override suspend fun restoreSubtasksOfTask(taskId: String, deletedAt: Long, now: Long) = error("no se usa")

        override suspend fun restoreRemindersOfTask(taskId: String, deletedAt: Long, now: Long): Int = error("no se usa")
        override fun observeByList(listId: String): Flow<List<TaskEntity>> = emptyFlow()

        override fun observeActiveCountsByList(): Flow<List<ListCountRow>> = emptyFlow()

        override suspend fun countInList(listId: String): Int = 0

        override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = emptyFlow()

        override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) = Unit

        private val rows = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

        val countedColumns: MutableList<String> = mutableListOf()

        fun put(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = MutableStateFlow(emptyList())

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()

        override suspend fun findById(id: String): TaskEntity? = rows.value[id]

        override suspend fun countByColumn(columnId: String): Int {
            countedColumns += columnId
            return rows.value.values.count { it.columnId == columnId }
        }

        override suspend fun upsert(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override suspend fun upsertAll(tasks: List<TaskEntity>) {
            rows.value = rows.value + tasks.associateBy { it.id }
        }

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override fun observeCompletedCount(): Flow<Int> = emptyFlow()

        override suspend fun findCompleted(): List<TaskEntity> = emptyList()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = null

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = Unit

        override suspend fun insertReminder(reminder: ReminderEntity) = Unit
    }

    private fun taskEntity(id: String, columnId: String): TaskEntity = TaskEntity(
        id = id,
        columnId = columnId,
        title = "Tarea $id",
        description = null,
        priority = "MEDIUM",
        dueAt = null,
        assigneeId = null,
        position = 0,
        createdAt = 0L,
        updatedAt = 0L,
        pending = true,
        completedAt = null,
        deletedAt = null,
    )

    @Test
    fun createAppContainer_construyeAuthRepositoryPorElPuenteDataAuth() {
        val body = factorySource().substringAfter("fun createAppContainer(").substringBefore("// La lectura inicial")

        assertTrue(factorySource().contains("import com.simposio.taskeer.data.auth.createAuthRepository"))
        assertEquals(1, Regex("createAuthRepository\\(").findAll(body).count())
        assertTrue(body.contains("CoroutineScope(SupervisorJob() + Dispatchers.Default)"))
        assertTrue(!factorySource().contains("data.remote"))
    }

    @Test
    fun createAppContainer_noHaceTrabajoDeRedNiBloqueanteParaAuth() {
        val body = factorySource().substringAfter("fun createAppContainer(").substringBefore("// La lectura inicial")
        val authLine = body.lines().first { it.contains("createAuthRepository(") }

        assertTrue(!authLine.contains("runBlocking"))
        assertTrue(!body.contains("resume("))
        assertEquals(0, Regex("runBlocking").findAll(body).count())
    }
}
