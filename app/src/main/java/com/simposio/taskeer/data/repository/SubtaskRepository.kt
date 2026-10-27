package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SubtaskDraft
import kotlinx.coroutines.flow.Flow

interface SubtaskRepository {
    fun observeSubtasks(taskId: String): Flow<List<Subtask>>

    suspend fun createSubtask(taskId: String, draft: SubtaskDraft): DomainResult<Subtask>

    suspend fun updateSubtask(id: String, draft: SubtaskDraft): DomainResult<Subtask>

    suspend fun setSubtaskCompleted(id: String, completed: Boolean): DomainResult<Subtask>

    suspend fun deleteSubtask(id: String): DomainResult<Unit>
}
