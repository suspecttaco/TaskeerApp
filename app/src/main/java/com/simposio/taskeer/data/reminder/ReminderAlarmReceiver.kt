package com.simposio.taskeer.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) {
            reconcileAsync(context)
        }
    }

    companion object {
        const val ACTION = "com.simposio.taskeer.action.REMINDER_DUE"
    }
}
