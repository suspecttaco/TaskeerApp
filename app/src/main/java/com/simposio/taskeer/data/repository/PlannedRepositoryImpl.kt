package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.MyDayDao
import com.simposio.taskeer.data.local.toMyDayTask
import com.simposio.taskeer.domain.date.DayWindow
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.planned.AllTasksView
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.UpcomingDay
import com.simposio.taskeer.domain.planned.groupAllTasks
import com.simposio.taskeer.domain.planned.groupUpcoming
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class PlannedRepositoryImpl(
    private val myDayDao: MyDayDao,
    private val timeZone: TimeZoneProvider,
    private val dayWindows: DayWindowSource = ClockDayWindowSource(Instant::now, timeZone),
) : PlannedRepository {

    override fun observeUpcoming(): Flow<List<UpcomingDay>> = observeItems { items, window ->
        groupUpcoming(items, window.date, timeZone.current())
    }

    override fun observeAllTasks(): Flow<AllTasksView> = observeItems { items, window ->
        groupAllTasks(items, window.date, timeZone.current())
    }

    private fun <T> observeItems(
        group: (List<PlannedItem>, DayWindow) -> T,
    ): Flow<T> = combine(dayWindows.observe(), myDayDao.observeOpen(LocalUser.ID)) { window, rows ->
        val items = rows.map { row ->
            row.toMyDayTask().let { PlannedItem(it.task, it.boardId, it.boardName, it.listName) }
        }
        group(items, window)
    }
}
