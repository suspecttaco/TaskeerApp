package com.simposio.taskeer.data.reminder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.simposio.taskeer.MainActivity
import com.simposio.taskeer.R
import com.simposio.taskeer.data.repository.ReminderNotifier
import com.simposio.taskeer.domain.model.NotificationEvent

class NotificationReminderNotifier(private val context: Context) : ReminderNotifier {

    // El permiso se comprueba con areNotificationsEnabled(), que también cubre canal y ajustes
    // del sistema; el linter no lo reconoce como comprobación de POST_NOTIFICATIONS.
    @SuppressLint("MissingPermission")
    override fun notify(event: NotificationEvent): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return false
        }
        createChannel()
        val (id, title, text) = content(event)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
        return true
    }

    private fun content(event: NotificationEvent): Triple<Int, String, String> = when (event) {
        is NotificationEvent.Deadline -> Triple(
            event.taskId.hashCode(),
            event.taskTitle,
            context.getString(R.string.deadline_notification_text),
        )
        is NotificationEvent.Manual -> Triple(
            event.reminderId.hashCode(),
            event.taskTitle,
            context.getString(R.string.reminder_notification_text),
        )
        is NotificationEvent.Digest -> Triple(
            DIGEST_NOTIFICATION_ID,
            context.getString(R.string.digest_notification_title),
            digestText(event.activeTaskCount),
        )
    }

    private fun digestText(count: Int): String =
        if (count == 0) {
            context.getString(R.string.digest_notification_none)
        } else {
            context.resources.getQuantityString(R.plurals.digest_notification_count, count, count)
        }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val DIGEST_NOTIFICATION_ID = 1
    }
}
