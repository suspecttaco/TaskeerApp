package com.simposio.taskeer

import android.app.Application
import com.simposio.taskeer.di.AppContainer
import com.simposio.taskeer.di.createAppContainer

class TaskeerApplication : Application() {

    val container: AppContainer by lazy { createAppContainer(this) }
}
