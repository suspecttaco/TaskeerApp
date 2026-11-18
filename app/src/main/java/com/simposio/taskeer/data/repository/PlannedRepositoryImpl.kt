package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.MyDayDao
import com.simposio.taskeer.data.local.toMyDayTask
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.PlannedView
import com.simposio.taskeer.domain.planned.groupPlanned
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class PlannedRepositoryImpl(
    private val myDayDao: MyDayDao,
    private val timeZone: TimeZoneProvider,
    private val dayWindows: DayWindowSource = ClockDayWindowSource(Instant::now, timeZone),
) : PlannedRepository {

    override fun observePlanned(): Flow<PlannedView> = combine(
        dayWindows.observe(),
        myDayDao.observeDated(LocalUser.ID),
    ) { window, rows ->
        val items = rows.map { row -> row.toMyDayTask().let { PlannedItem(it.task, it.boardId, it.boardName) } }
        groupPlanned(items, window, timeZone.current())
    }
}
