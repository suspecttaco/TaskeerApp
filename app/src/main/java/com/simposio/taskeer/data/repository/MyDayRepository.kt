package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import kotlinx.coroutines.flow.Flow

interface MyDayRepository {
    fun observeMyDay(): Flow<MyDay>

    fun observeMarkedTaskIds(): Flow<Set<String>>

    fun observeOverdueCount(): Flow<Int>

    suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit>
}
