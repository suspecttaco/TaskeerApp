package com.simposio.taskeer.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarPermissionPolicyTest {

    @Test
    fun accessOf_exponeLecturaYEscrituraPorSeparado() {
        assertEquals(CalendarAccess(read = true, write = false), CalendarPermissionPolicy.accessOf(true, false))
        assertEquals(CalendarAccess(read = false, write = true), CalendarPermissionPolicy.accessOf(false, true))
    }

    @Test
    fun noticeFor_sinPermisoNiRespuesta_pideAcceso() {
        assertEquals(CalendarNotice.ASK, CalendarPermissionPolicy.noticeFor(granted = false, answeredThisTransition = false))
    }

    @Test
    fun noticeFor_trasResponderSinConceder_estaDenegado() {
        assertEquals(CalendarNotice.DENIED, CalendarPermissionPolicy.noticeFor(granted = false, answeredThisTransition = true))
    }

    @Test
    fun noticeFor_concedido_noMuestraAviso() {
        assertNull(CalendarPermissionPolicy.noticeFor(granted = true, answeredThisTransition = false))
        assertNull(CalendarPermissionPolicy.noticeFor(granted = true, answeredThisTransition = true))
    }
}
