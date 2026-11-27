package com.simposio.taskeer.domain.sync

import com.simposio.taskeer.domain.model.LocalUser

object AssigneeMapping {
    fun toServer(assigneeId: String?, me: String): String? = if (assigneeId == LocalUser.ID) me else assigneeId

    fun toLocal(assigneeId: String?, me: String): String? = if (assigneeId == me) LocalUser.ID else assigneeId
}
