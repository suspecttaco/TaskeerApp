package com.simposio.taskeer.ui.components

import androidx.compose.material3.SnackbarDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeTest {

    @Test
    fun duracion_derivaDelTipo() {
        assertEquals(SnackbarDuration.Short, noticeDuration(NoticeKind.INFO))
        assertEquals(SnackbarDuration.Long, noticeDuration(NoticeKind.ERROR))
        assertEquals(SnackbarDuration.Long, noticeDuration(NoticeKind.ACTION))
        assertEquals(SnackbarDuration.Indefinite, noticeDuration(NoticeKind.UNDO))
    }

    @Test
    fun cierre_soloEnErrorYAccion() {
        assertTrue(noticeDismissible(NoticeKind.ERROR))
        assertTrue(noticeDismissible(NoticeKind.ACTION))
        assertFalse(noticeDismissible(NoticeKind.INFO))
        assertFalse(noticeDismissible(NoticeKind.UNDO))
    }
}
