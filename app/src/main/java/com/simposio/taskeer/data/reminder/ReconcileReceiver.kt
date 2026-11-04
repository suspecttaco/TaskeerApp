package com.simposio.taskeer.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import com.simposio.taskeer.TaskeerApplication
import com.simposio.taskeer.domain.model.ReminderMechanism
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun BroadcastReceiver.reconcileAsync(
    context: Context,
    silence: Set<ReminderMechanism> = emptySet(),
) {
    val container = (context.applicationContext as TaskeerApplication).container
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            container.reminderReconciler.reconcile(silence)
        } finally {
            pendingResult.finish()
        }
    }
}
