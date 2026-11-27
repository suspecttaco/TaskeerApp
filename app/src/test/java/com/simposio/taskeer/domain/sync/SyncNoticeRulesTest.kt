package com.simposio.taskeer.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncNoticeRulesTest {

    @Test
    fun agrupaCantidades_sinDatosDeTarea() {
        val notice = SyncNoticeRules.summarize(
            listOf(SyncLoss.REJECTED, SyncLoss.REJECTED, SyncLoss.REPLACED_BY_SERVER),
        )!!

        assertEquals(mapOf(SyncLoss.REJECTED to 2, SyncLoss.REPLACED_BY_SERVER to 1), notice.counts)
        assertEquals(
            "Sincronización: 2 cambios no se aceptaron en el servidor; 1 cambio se sustituyó por una versión más reciente",
            SyncNoticeRules.message(notice),
        )
    }

    @Test
    fun sinPerdidas_noHayAviso() {
        assertNull(SyncNoticeRules.summarize(emptyList()))
    }

    @Test
    fun toString_soloCantidades() {
        val notice = SyncNoticeRules.summarize(listOf(SyncLoss.ACCESS_LOST))!!

        assertFalse(notice.toString().contains("ACCESS_LOST"))
        assertTrue(notice.toString().contains("total=1"))
    }

    @Test
    fun perdidaDeAcceso_hablaDeTableros() {
        assertTrue(SyncNoticeRules.message(SyncNoticeRules.summarize(listOf(SyncLoss.ACCESS_LOST))!!).contains("acceso a 1 tablero"))
        val two = SyncNoticeRules.summarize(listOf(SyncLoss.ACCESS_LOST, SyncLoss.ACCESS_LOST))!!
        assertTrue(SyncNoticeRules.message(two).contains("acceso a 2 tableros"))
    }
}
