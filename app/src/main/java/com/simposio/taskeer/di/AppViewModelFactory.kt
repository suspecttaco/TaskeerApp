package com.simposio.taskeer.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.simposio.taskeer.ui.app.AppViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
            "AppViewModelFactory no sabe construir ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(container.preferencesRepository) as T
    }
}
