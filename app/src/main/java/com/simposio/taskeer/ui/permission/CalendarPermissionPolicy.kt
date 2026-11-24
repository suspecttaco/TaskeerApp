package com.simposio.taskeer.ui.permission

data class CalendarAccess(val read: Boolean, val write: Boolean)

enum class CalendarNotice { ASK, DENIED }

object CalendarPermissionPolicy {
    fun accessOf(read: Boolean, write: Boolean): CalendarAccess = CalendarAccess(read, write)

    // El diálogo del sistema solo sale al pulsar «Permitir acceso»; tras responder sin conceder, el único camino son los ajustes.
    fun noticeFor(granted: Boolean, answeredThisTransition: Boolean): CalendarNotice? = when {
        granted -> null
        answeredThisTransition -> CalendarNotice.DENIED
        else -> CalendarNotice.ASK
    }
}
