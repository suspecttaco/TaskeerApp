package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.SyncDao
import com.simposio.taskeer.data.local.TaskListEntity
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_NAME
import java.time.Instant

interface AccountWiper {
    // Vacía los datos de negocio, la cola, las versiones base y el vínculo, y resiembra la lista «Tareas».
    suspend fun wipe()
}

class LocalDataWiper(private val dao: SyncDao, private val clock: () -> Instant = Instant::now) : AccountWiper {
    override suspend fun wipe() {
        val now = clock().toEpochMilli()
        dao.wipe(
            TaskListEntity(
                id = SEEDED_TASK_LIST_ID,
                name = SEEDED_TASK_LIST_NAME,
                position = 0,
                createdAt = now,
                updatedAt = now,
                pending = true,
                deletedAt = null,
            ),
        )
    }
}
