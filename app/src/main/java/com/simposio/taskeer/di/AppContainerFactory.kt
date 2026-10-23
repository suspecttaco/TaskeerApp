package com.simposio.taskeer.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.simposio.taskeer.data.local.AppDatabase
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.repository.BoardRepositoryImpl
import com.simposio.taskeer.data.repository.ColumnRepositoryImpl
import com.simposio.taskeer.data.repository.PreferencesRepositoryImpl
import com.simposio.taskeer.data.repository.TaskRepositoryImpl
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
    return AppContainer(
        preferencesRepository = PreferencesRepositoryImpl(dataStore),
        timeZoneProvider = SystemTimeZoneProvider,
        boardRepository = BoardRepositoryImpl(database.boardDao(), database.columnDao()),
        columnRepository = ColumnRepositoryImpl(
            database.boardDao(),
            database.columnDao(),
            columnTaskCount = taskCountByColumn(database.taskDao()),
        ),
        taskRepository = TaskRepositoryImpl(database.taskDao(), database.columnDao()),
    )
}

// Función aparte y no una línea en createAppContainer para poder probarla en un test JVM.
fun taskCountByColumn(taskDao: TaskDao): suspend (String) -> Int =
    { columnId -> taskDao.countByColumn(columnId) }
