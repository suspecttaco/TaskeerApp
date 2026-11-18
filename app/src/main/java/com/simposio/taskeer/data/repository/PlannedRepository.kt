package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.planned.PlannedView
import kotlinx.coroutines.flow.Flow

interface PlannedRepository {
    fun observePlanned(): Flow<PlannedView>
}
