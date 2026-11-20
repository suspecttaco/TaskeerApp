package com.simposio.taskeer.ui.myday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.myday.isFirstForegroundOfDay
import com.simposio.taskeer.domain.task.isOverdue
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

const val SUGGESTIONS_LOAD_TIMEOUT: Long = 1_500

data class SuggestionsBadge(val count: Int = 0, val loaded: Boolean = false)

data class MySuggestionUi(
    val id: String,
    val title: String,
    val boardName: String?,
    val listName: String?,
    val due: String?,
    val isOverdue: Boolean,
)

class MySuggestionsViewModel(
    private val repository: MyDayRepository,
    private val preferences: PreferencesRepository,
    private val clock: () -> Instant,
    private val timeZone: TimeZoneProvider,
    private val dateTimeConverter: DateTimeConverter,
) : ViewModel() {

    private val failure = MutableStateFlow<DomainError?>(null)
    private val claimLock = Mutex()

    val error: StateFlow<DomainError?> = failure

    val badge: StateFlow<SuggestionsBadge> = repository.observeSuggestionCount()
        .map { SuggestionsBadge(count = it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SuggestionsBadge())

    // Solo se suscribe con la hoja abierta: la lista completa no se materializa para pintar la insignia.
    val items: StateFlow<List<MySuggestionUi>> = repository.observeSuggestions()
        .map { tasks ->
            val instant = clock()
            tasks.map { it.toUi(instant) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun clearError() {
        failure.value = null
    }

    fun addToMyDay(taskId: String) {
        viewModelScope.launch { publish(repository.setMarked(taskId, true)) }
    }

    fun dismiss(taskId: String) {
        viewModelScope.launch { publish(repository.dismissSuggestion(taskId)) }
    }

    // Persiste hoy antes de decidir si se abre la hoja: el primer primer plano del día consume el día
    // aunque no se abra, y la escritura fallida no reclama para no abrir sin poder recordarlo.
    suspend fun claimFirstForegroundOfDay(): Boolean = claimLock.withLock {
        val today = LocalDate.ofInstant(clock(), timeZone.current())
        if (!isFirstForegroundOfDay(today, preferences.suggestionsLastShownDay.first())) return@withLock false
        try {
            preferences.setSuggestionsLastShownDay(today)
            true
        } catch (_: IOException) {
            false
        }
    }

    suspend fun awaitFirstCount(timeoutMillis: Long = SUGGESTIONS_LOAD_TIMEOUT): Int =
        withTimeoutOrNull(timeoutMillis) { badge.first { it.loaded }.count } ?: 0

    private fun publish(result: DomainResult<Unit>) {
        failure.value = (result as? DomainResult.Failure)?.error
    }

    private fun MyDayTask.toUi(now: Instant): MySuggestionUi = MySuggestionUi(
        id = task.id,
        title = task.title,
        boardName = boardName,
        listName = listName,
        due = task.dueAt?.let { dateTimeConverter.toLocalDateTime(it).format(DUE_DATE_TIME_FORMATTER) },
        isOverdue = isOverdue(task.dueAt, task.isCompleted, now),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
