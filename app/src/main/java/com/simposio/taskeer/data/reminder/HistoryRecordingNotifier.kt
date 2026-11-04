package com.simposio.taskeer.data.reminder

import com.simposio.taskeer.data.repository.NotificationHistoryRepository
import com.simposio.taskeer.data.repository.ReminderNotifier
import com.simposio.taskeer.domain.model.NotificationEvent
import kotlin.coroutines.cancellation.CancellationException

class HistoryRecordingNotifier(
    private val delegate: ReminderNotifier,
    private val history: NotificationHistoryRepository,
) : ReminderNotifier {

    // Se notifica antes de registrar porque la entrada necesita saber si el sistema la mostró.
    override suspend fun notify(event: NotificationEvent): Boolean {
        val delivered = delegate.notify(event)
        try {
            history.record(event, delivered)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // La notificación ya salió: un fallo del historial no debe alterar lo que sabe el conciliador.
        }
        return delivered
    }
}
