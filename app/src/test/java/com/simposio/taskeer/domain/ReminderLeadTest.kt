package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.ReminderLead
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderLeadTest {

    @Test
    fun entries_sonExactamenteLasCuatroAntelaciones() {
        assertEquals(
            listOf("AT_DUE", "FIFTEEN_MINUTES", "ONE_HOUR", "ONE_DAY"),
            ReminderLead.entries.map { it.name },
        )
    }

    @Test
    fun offsets_correspondenALaAntelacion() {
        assertEquals(Duration.ZERO, ReminderLead.AT_DUE.offset)
        assertEquals(Duration.ofMinutes(15), ReminderLead.FIFTEEN_MINUTES.offset)
        assertEquals(Duration.ofHours(1), ReminderLead.ONE_HOUR.offset)
        assertEquals(Duration.ofDays(1), ReminderLead.ONE_DAY.offset)
    }

    @Test
    fun fromStorage_conValorDesconocidoONulo_devuelveUnaHora() {
        assertEquals(ReminderLead.ONE_HOUR, ReminderLead.fromStorage("INEXISTENTE"))
        assertEquals(ReminderLead.ONE_HOUR, ReminderLead.fromStorage(null))
        assertEquals(ReminderLead.ONE_DAY, ReminderLead.fromStorage("ONE_DAY"))
    }
}
