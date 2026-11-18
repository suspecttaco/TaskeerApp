package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.planned.AllTasksView
import com.simposio.taskeer.domain.planned.UpcomingDay
import kotlinx.coroutines.flow.Flow

interface PlannedRepository {
    fun observeUpcoming(): Flow<List<UpcomingDay>>

    fun observeAllTasks(): Flow<AllTasksView>
}
