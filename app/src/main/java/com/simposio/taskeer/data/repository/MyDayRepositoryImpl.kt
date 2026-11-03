package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.MyDayDao
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.toMyDayTask
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.MyDay
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
) : MyDayRepository {

    override fun observeMyDay(): Flow<MyDay> = combine(
        myDayDao.observePinned(),
        myDayDao.observeAssigned(LocalUser.ID),
    ) { pinned, assigned ->
        MyDay(pinned.map { it.toMyDayTask() }, assigned.map { it.toMyDayTask() })
    }

    override fun observeMarkedTaskIds(): Flow<Set<String>> =
        myDayDao.observeMarkedIds().map { it.toSet() }

    // Re-suscribir el conteo en cada emisión de las asignadas renueva el «ahora»; sin ello
    // una fecha editada a pasado se contaría con el instante de la primera suscripción.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOverdueCount(): Flow<Int> =
        myDayDao.observeAssigned(LocalUser.ID).flatMapLatest {
            myDayDao.observeOverdueCount(LocalUser.ID, clock().toEpochMilli())
        }

    override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
        if (taskDao.findById(taskId)?.let { it.deletedAt == null } != true) {
            return DomainResult.Failure(DomainError.NotFound(TASK_NOT_FOUND, "La tarea no existe"))
        }
        val now = clock().toEpochMilli()
        val entry = myDayDao.findEntry(taskId)
        val isActive = entry != null && entry.deletedAt == null
        when {
            marked && !isActive -> myDayDao.upsert(
                MyDayEntryEntity(taskId, addedAt = now, updatedAt = now, pending = true, deletedAt = null),
            )
            !marked && isActive -> myDayDao.upsert(
                requireNotNull(entry).copy(updatedAt = now, pending = true, deletedAt = now),
            )
        }
        return DomainResult.Success(Unit)
    }
}
