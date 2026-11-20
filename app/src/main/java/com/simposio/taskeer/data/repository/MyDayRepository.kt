package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import kotlinx.coroutines.flow.Flow

interface MyDayRepository {
    fun observeMyDay(): Flow<MyDay>

    fun observeSuggestions(): Flow<List<MyDayTask>>

    fun observeSuggestionCount(): Flow<Int>

    fun observeMarkedTaskIds(): Flow<Set<String>>

    fun observeOverdueCount(): Flow<Int>

    suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit>

    suspend fun dismissSuggestion(taskId: String): DomainResult<Unit>
}
