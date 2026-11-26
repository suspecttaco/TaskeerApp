package com.simposio.taskeer.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecoveryCodesTest {

    @Test
    fun toString_noIncluyeLosValores() {
        val codes = RecoveryCodes(listOf("CTPQ-Q479", "ABCD-EFGH"))

        assertEquals("RecoveryCodes(count=2)", codes.toString())
        assertFalse(codes.toString().contains("CTPQ"))
    }

    @Test
    fun count_esElNumeroDeCodigos() {
        assertEquals(3, RecoveryCodes(listOf("A", "B", "C")).count)
    }

    @Test
    fun expectedCount_esDiez() {
        assertEquals(10, RecoveryCodes.EXPECTED_COUNT)
    }
}
