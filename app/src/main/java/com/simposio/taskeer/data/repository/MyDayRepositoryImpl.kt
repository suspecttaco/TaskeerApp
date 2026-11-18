package com.simposio.taskeer.data.repository

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
import com.simposio.taskeer.domain.myday.suggestionsFor
import com.simposio.taskeer.domain.task.TASK_NOT_FOUND
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        val start = window.start.toEpochMilli()
        combine(
            myDayDao.observePinned(start),
            myDayDao.observeAssigned(LocalUser.ID, start, window.end.toEpochMilli()),
            myDayDao.observeSuggestions(start),
        ) { pinned, assigned, suggestions ->
            MyDay(
                pinned = pinned.map { it.toMyDayTask() },
                assigned = assigned.map { it.toMyDayTask() },
                suggestions = suggestions.map { it.toMyDayTask() },
            ).let { it.copy(suggestions = suggestionsFor(it)) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMarkedTaskIds(): Flow<Set<String>> = dayWindows.observe().flatMapLatest { window ->
        myDayDao.observeMarkedIds(window.start.toEpochMilli()).map { it.toSet() }
    }

    // Re-suscribir el conteo en cada emisión de las asignadas renueva el «ahora»; sin ello
    // una fecha editada a pasado se contaría con el instante de la primera suscripción.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOverdueCount(): Flow<Int> = dayWindows.observe().flatMapLatest { window ->
        myDayDao.observeAssigned(LocalUser.ID, window.start.toEpochMilli(), window.end.toEpochMilli())
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
}
