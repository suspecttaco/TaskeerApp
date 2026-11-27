package com.simposio.taskeer

import android.app.Application
import com.simposio.taskeer.data.reminder.ReminderTriggers
import com.simposio.taskeer.di.AppContainer
import com.simposio.taskeer.di.createAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TaskeerApplication : Application() {

    val container: AppContainer by lazy { createAppContainer(this) }

    // El sistema borra las alarmas al forzar la detención de la app, sin reinicio del dispositivo:
    // la primera emisión del colector concilia y vuelve a registrar la alarma.
    override fun onCreate() {
        super.onCreate()
        val triggers = ReminderTriggers(
            candidates = container.taskRepository.observeReminderCandidates(),
            deadline = container.preferencesRepository.deadlineReminders,
            digest = container.preferencesRepository.digest,
            pending = container.reminderRepository.observePending(),
            reconcile = container.reminderReconciler::reconcile,
            zone = container.zoneChanges,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { triggers.run() }
        scope.launch { container.authRepository.resume() }
        scope.launch { container.runSync() }
    }
}
