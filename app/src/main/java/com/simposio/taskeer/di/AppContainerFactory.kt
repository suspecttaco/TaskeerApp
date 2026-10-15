package com.simposio.taskeer.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.simposio.taskeer.data.local.AppDatabase
import com.simposio.taskeer.data.repository.BoardRepositoryImpl
import com.simposio.taskeer.data.repository.ColumnRepositoryImpl
import com.simposio.taskeer.data.repository.PreferencesRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun createAppContainer(context: Context): AppContainer {
    val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile(PreferencesRepositoryImpl.DATA_STORE_NAME) },
    )
    val database = AppDatabase.create(context)
    return AppContainer(
        preferencesRepository = PreferencesRepositoryImpl(dataStore),
        timeZoneProvider = SystemTimeZoneProvider,
        boardRepository = BoardRepositoryImpl(database.boardDao(), database.columnDao()),
        columnRepository = ColumnRepositoryImpl(database.boardDao(), database.columnDao()),
    )
}
