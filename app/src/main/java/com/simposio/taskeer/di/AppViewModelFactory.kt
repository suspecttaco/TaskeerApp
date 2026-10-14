package com.simposio.taskeer.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.simposio.taskeer.ui.app.AppViewModel
import com.simposio.taskeer.ui.boards.BoardsViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AppViewModel::class.java) -> AppViewModel(container.preferencesRepository) as T
        modelClass.isAssignableFrom(BoardsViewModel::class.java) -> BoardsViewModel(container.boardRepository) as T
        else -> error("AppViewModelFactory no sabe construir ${modelClass.name}")
    }
}
