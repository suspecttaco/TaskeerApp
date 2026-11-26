package com.simposio.taskeer.ui.register

import com.simposio.taskeer.domain.auth.RecoveryCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryCodesFormatTest {

    @Test
    fun numeracion_empiezaEnUnoYSigueElOrden() {
        val numbered = numbered(RecoveryCodes(listOf("AAAA-BBBB", "CCCC-DDDD")))

        assertEquals(listOf(1 to "AAAA-BBBB", 2 to "CCCC-DDDD"), numbered)
    }

    @Test
    fun spokenForm_deletreaLosCaracteresYElGuion() {
        assertEquals("Código 3 de 10: C, T, P, Q, guion, Q, 4, 7, 9", spokenForm(3, 10, "CTPQ-Q479"))
    }

    @Test
    fun spokenForm_usaElTotalReal() {
        assertEquals("Código 1 de 7: A, B", spokenForm(1, 7, "AB"))
    }

    @Test
    fun spokenForm_conTotalDistintoDeDiez() {
        assertEquals("Código 2 de 3: X", spokenForm(2, 3, "X"))
    }
}
