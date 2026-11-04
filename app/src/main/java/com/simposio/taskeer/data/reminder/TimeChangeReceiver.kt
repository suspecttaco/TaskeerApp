package com.simposio.taskeer.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simposio.taskeer.domain.model.ReminderMechanism

class TimeChangeReceiver : BroadcastReceiver() {

    // El resumen se define en hora local: un cambio de zona o de reloj crearía instantes que
    // nunca existieron, así que se recalcula sin avisar de ellos.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) {
            reconcileAsync(context, setOf(ReminderMechanism.DIGEST))
        }
    }
}
