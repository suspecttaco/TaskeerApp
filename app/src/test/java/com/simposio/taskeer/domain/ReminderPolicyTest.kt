package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.reminder.digestOccurrences
import com.simposio.taskeer.domain.reminder.dueDeadlineNotifications
import com.simposio.taskeer.domain.reminder.dueDigest
import com.simposio.taskeer.domain.reminder.fireAt
import com.simposio.taskeer.domain.reminder.isWithinTolerance
import com.simposio.taskeer.domain.reminder.nextDeadlineFireAt
import com.simposio.taskeer.domain.reminder.nextDigestAt
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {

    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z")
    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val mexico: ZoneId = ZoneId.of("America/Mexico_City")

    private fun candidate(id: String, dueAt: Instant) = ReminderCandidate(id, "Tarea $id", dueAt)

    private fun digest(
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        time: LocalTime = LocalTime.of(9, 0),
        enabled: Boolean = true,
    ) = DigestSettings(enabled, days, time)

    @Test
    fun isWithinTolerance_conUnaHoraExacta_noEntra() {
        assertFalse(isWithinTolerance(now.minus(Duration.ofHours(1)), now))
    }

    @Test
    fun isWithinTolerance_conCincuentaYNueveMinutosYCincuentaYNueveSegundos_entra() {
        assertTrue(isWithinTolerance(now.minusSeconds(59 * 60 + 59), now))
    }

    @Test
    fun fireAt_restaLaAntelacionALaFechaLimite() {
        val due = Instant.parse("2026-11-04T12:00:00Z")

        assertEquals(due, fireAt(due, ReminderLead.AT_DUE))
        assertEquals(Instant.parse("2026-11-04T11:45:00Z"), fireAt(due, ReminderLead.FIFTEEN_MINUTES))
        assertEquals(Instant.parse("2026-11-04T11:00:00Z"), fireAt(due, ReminderLead.ONE_HOUR))
        assertEquals(Instant.parse("2026-11-03T12:00:00Z"), fireAt(due, ReminderLead.ONE_DAY))
    }

    @Test
    fun dueDeadlineNotifications_incluyeSoloElIntervaloAbiertoPorLaIzquierda() {
        val checkpoint = now.minusSeconds(600)
        val candidates = listOf(
            candidate("en-punto-de-control", checkpoint),
            candidate("dentro", now.minusSeconds(300)),
            candidate("ahora", now),
            candidate("futura", now.plusSeconds(1)),
        )

        val due = dueDeadlineNotifications(candidates, ReminderLead.AT_DUE, checkpoint, now)

        assertEquals(listOf("dentro", "ahora"), due.map { it.taskId })
    }

    @Test
    fun dueDeadlineNotifications_conAntelacion_avisaPorFechaLimiteMenosAntelacion() {
        val candidates = listOf(candidate("t1", now.plus(Duration.ofMinutes(10))))

        val due = dueDeadlineNotifications(
            candidates,
            ReminderLead.FIFTEEN_MINUTES,
            checkpoint = now.minusSeconds(600),
            now = now,
        )

        assertEquals(listOf("t1"), due.map { it.taskId })
    }

    @Test
    fun dueDeadlineNotifications_excluyeVencidoHaceUnaHoraYIncluyeCincuentaYNueveMinutosCincuentaYNueve() {
        val checkpoint = now.minus(Duration.ofDays(1))
        val candidates = listOf(
            candidate("una-hora", now.minus(Duration.ofHours(1))),
            candidate("casi", now.minusSeconds(59 * 60 + 59)),
        )

        val due = dueDeadlineNotifications(candidates, ReminderLead.AT_DUE, checkpoint, now)

        assertEquals(listOf("casi"), due.map { it.taskId })
    }

    @Test
    fun nextDeadlineFireAt_devuelveElMenorFuturoEstricto() {
        val candidates = listOf(
            candidate("pasada", now.minusSeconds(10)),
            candidate("ahora", now),
            candidate("lejos", now.plusSeconds(900)),
            candidate("cerca", now.plusSeconds(60)),
        )

        assertEquals(now.plusSeconds(60), nextDeadlineFireAt(candidates, ReminderLead.AT_DUE, now))
    }

    @Test
    fun nextDeadlineFireAt_aplicaLaAntelacionYSinCandidatasDevuelveNull() {
        val candidates = listOf(candidate("t1", now.plus(Duration.ofHours(2))))

        assertEquals(
            now.plus(Duration.ofHours(1)),
            nextDeadlineFireAt(candidates, ReminderLead.ONE_HOUR, now),
        )
        assertNull(nextDeadlineFireAt(emptyList(), ReminderLead.ONE_HOUR, now))
    }

    @Test
    fun digestOccurrences_unLunesALas0900EnMadridEsLas0800Utc() {
        val after = Instant.parse("2026-11-01T00:00:00Z")
        val upTo = Instant.parse("2026-11-09T00:00:00Z")

        val occurrences = digestOccurrences(digest(), madrid, after, upTo)

        assertEquals(listOf(Instant.parse("2026-11-02T08:00:00Z")), occurrences)
    }

    @Test
    fun digestOccurrences_laMismaHoraLocalEnMexicoEsOtroInstante() {
        val after = Instant.parse("2026-11-01T00:00:00Z")
        val upTo = Instant.parse("2026-11-04T00:00:00Z")

        val occurrences = digestOccurrences(digest(), mexico, after, upTo)

        assertEquals(listOf(Instant.parse("2026-11-02T15:00:00Z")), occurrences)
    }

    @Test
    fun digestOccurrences_conConjuntoVacioODesactivado_noDevuelveNada() {
        val after = Instant.parse("2026-11-01T00:00:00Z")
        val upTo = Instant.parse("2026-11-30T00:00:00Z")

        assertTrue(digestOccurrences(digest(days = emptySet()), madrid, after, upTo).isEmpty())
        assertTrue(digestOccurrences(digest(enabled = false), madrid, after, upTo).isEmpty())
    }

    @Test
    fun digestOccurrences_variosDiasDevuelveUnaPorDiaEnOrden() {
        val settings = digest(days = DigestSettings.WEEKDAYS)

        val occurrences = digestOccurrences(
            settings,
            madrid,
            Instant.parse("2026-11-01T00:00:00Z"),
            Instant.parse("2026-11-07T00:00:00Z"),
        )

        assertEquals(5, occurrences.size)
        assertEquals(occurrences.sorted(), occurrences)
    }

    @Test
    fun digestOccurrences_horaInexistenteEnSaltoDeVerano_usaElInstantePosterior() {
        val settings = digest(days = setOf(DayOfWeek.SUNDAY), time = LocalTime.of(2, 30))

        val occurrences = digestOccurrences(
            settings,
            madrid,
            Instant.parse("2026-03-28T00:00:00Z"),
            Instant.parse("2026-03-30T00:00:00Z"),
        )

        assertEquals(listOf(Instant.parse("2026-03-29T01:30:00Z")), occurrences)
    }

    @Test
    fun digestOccurrences_horaExistenteDosVeces_usaElPrimero() {
        val settings = digest(days = setOf(DayOfWeek.SUNDAY), time = LocalTime.of(2, 30))

        val occurrences = digestOccurrences(
            settings,
            madrid,
            Instant.parse("2026-10-24T00:00:00Z"),
            Instant.parse("2026-10-26T00:00:00Z"),
        )

        assertEquals(listOf(Instant.parse("2026-10-25T00:30:00Z")), occurrences)
    }

    @Test
    fun dueDigest_conOcurrenciaDentroDeLaToleranciaYElPuntoDeControlAnterior_laDevuelve() {
        val monday = Instant.parse("2026-11-02T08:00:00Z")

        val due = dueDigest(digest(), madrid, checkpoint = monday.minusSeconds(60), now = monday.plusSeconds(600))

        assertEquals(listOf(monday), due)
    }

    @Test
    fun dueDigest_conOcurrenciaVencidaHaceUnaHora_noLaDevuelve() {
        val monday = Instant.parse("2026-11-02T08:00:00Z")

        val due = dueDigest(
            digest(),
            madrid,
            checkpoint = monday.minusSeconds(60),
            now = monday.plus(Duration.ofHours(1)),
        )

        assertTrue(due.isEmpty())
    }

    @Test
    fun dueDigest_conPuntoDeControlPosteriorALaOcurrencia_noLaRepite() {
        val monday = Instant.parse("2026-11-02T08:00:00Z")

        val due = dueDigest(digest(), madrid, checkpoint = monday, now = monday.plusSeconds(600))

        assertTrue(due.isEmpty())
    }

    @Test
    fun nextDigestAt_devuelveLaSiguienteOcurrenciaFutura() {
        val sunday = Instant.parse("2026-11-01T12:00:00Z")

        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), nextDigestAt(digest(), madrid, sunday))
    }

    @Test
    fun nextDigestAt_conLaOcurrenciaDeHoyYaPasada_saltaALaSemanaSiguiente() {
        val mondayAfter = Instant.parse("2026-11-02T08:00:01Z")

        assertEquals(Instant.parse("2026-11-09T08:00:00Z"), nextDigestAt(digest(), madrid, mondayAfter))
    }

    @Test
    fun nextDigestAt_sinDiasDevuelveNull() {
        assertNull(nextDigestAt(digest(days = emptySet()), madrid, now))
    }
}
