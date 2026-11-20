package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.DISMISSED_ADDED_AT
import com.simposio.taskeer.data.local.MyDayDao
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.toMyDayTask
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.date.dayWindowAt
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.task.TASK_NOT_FOUND
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class MyDayRepositoryImpl(
    private val myDayDao: MyDayDao,
    private val taskDao: TaskDao,
    private val clock: () -> Instant = Instant::now,
    private val timeZone: TimeZoneProvider,
    private val dayWindows: DayWindowSource = ClockDayWindowSource(clock, timeZone),
) : MyDayRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyDay(): Flow<MyDay> = dayWindows.observe().flatMapLatest { window ->
        myDayDao.observePinned(window.start.toEpochMilli()).map { rows ->
            MyDay(pinned = rows.map { it.toMyDayTask() })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSuggestions(): Flow<List<MyDayTask>> = dayWindows.observe().flatMapLatest { window ->
        myDayDao.observeSuggestions(LocalUser.ID, window.start.toEpochMilli(), window.end.toEpochMilli())
            .map { rows -> rows.map { it.toMyDayTask() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSuggestionCount(): Flow<Int> = dayWindows.observe().flatMapLatest { window ->
        myDayDao.observeSuggestionCount(LocalUser.ID, window.start.toEpochMilli())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMarkedTaskIds(): Flow<Set<String>> = dayWindows.observe().flatMapLatest { window ->
        myDayDao.observeMarkedIds(window.start.toEpochMilli()).map { it.toSet() }
    }

    // Re-suscribir el conteo en cada emisión de las abiertas asignadas renueva el «ahora»; sin ello
    // una fecha editada a pasado se contaría con el instante de la primera suscripción.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOverdueCount(): Flow<Int> = dayWindows.observe().flatMapLatest { window ->
        myDayDao.observeOpen(LocalUser.ID)
            .flatMapLatest { myDayDao.observeOverdueCount(LocalUser.ID, clock().toEpochMilli()) }
    }

    override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
        if (taskDao.findById(taskId)?.let { it.deletedAt == null } != true) {
            return DomainResult.Failure(DomainError.NotFound(TASK_NOT_FOUND, "La tarea no existe"))
        }
        val nowInstant = clock()
        val now = nowInstant.toEpochMilli()
        val dayStart = dayWindowAt(nowInstant, timeZone.current()).start.toEpochMilli()
        val entry = myDayDao.findEntry(taskId)
        val isActive = entry != null && entry.deletedAt == null
        val isToday = isActive && requireNotNull(entry).addedAt >= dayStart
        when {
            marked && !isToday -> myDayDao.upsert(
                entry?.copy(addedAt = now, updatedAt = now, pending = true, deletedAt = null)
                    ?: MyDayEntryEntity(taskId, addedAt = now, updatedAt = now, pending = true, deletedAt = null),
            )
            !marked && isActive -> myDayDao.upsert(
                requireNotNull(entry).copy(updatedAt = now, pending = true, deletedAt = now),
            )
        }
        return DomainResult.Success(Unit)
    }

    // La descartada lleva el centinela en addedAt; la quitada conserva el suyo y vuelve a la hoja al instante.
    override suspend fun dismissSuggestion(taskId: String): DomainResult<Unit> {
        if (taskDao.findById(taskId)?.let { it.deletedAt == null } != true) {
            return DomainResult.Failure(DomainError.NotFound(TASK_NOT_FOUND, "La tarea no existe"))
        }
        val nowInstant = clock()
        val now = nowInstant.toEpochMilli()
        val dayStart = dayWindowAt(nowInstant, timeZone.current()).start.toEpochMilli()
        val entry = myDayDao.findEntry(taskId)
        val alreadyDismissed = entry != null && entry.addedAt == DISMISSED_ADDED_AT &&
            entry.deletedAt?.let { it >= dayStart } == true
        if (!alreadyDismissed) {
            myDayDao.upsert(
                entry?.copy(addedAt = DISMISSED_ADDED_AT, updatedAt = now, pending = true, deletedAt = now)
                    ?: MyDayEntryEntity(
                        taskId,
                        addedAt = DISMISSED_ADDED_AT,
                        updatedAt = now,
                        pending = true,
                        deletedAt = now,
                    ),
            )
        }
        return DomainResult.Success(Unit)
    }
}
