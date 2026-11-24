package com.simposio.taskeer.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAccessTrackerTest {

    private var read = false
    private var write = false
    private val tracker = CalendarAccessTracker({ read }, { write })

    @Test
    fun alCrearse_leeElPermisoActual() {
        read = true

        assertEquals(CalendarAccess(read = true, write = false), CalendarAccessTracker({ read }, { write }).access.value)
    }

    @Test
    fun refresh_sigueAlPermisoConcedidoEnAjustes() {
        assertEquals(CalendarAccess(read = false, write = false), tracker.access.value)

        read = true
        write = true
        tracker.refresh()

        assertEquals(CalendarAccess(read = true, write = true), tracker.access.value)
    }

    @Test
    fun refresh_noMarcaComoRespondido() {
        tracker.refresh()

        assertFalse(tracker.answered.value)
    }

    @Test
    fun onResult_marcaRespondidoYRelee() {
        read = true

        tracker.onResult()

        assertTrue(tracker.answered.value)
        assertTrue(tracker.access.value.read)
    }

    @Test
    fun onResult_sinConceder_dejaElAccesoDenegado() {
        tracker.onResult()

        assertTrue(tracker.answered.value)
        assertEquals(CalendarAccess(read = false, write = false), tracker.access.value)
    }
}
