package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.TaskRepeat
import com.simposio.taskeer.domain.task.RepeatPreset
import com.simposio.taskeer.domain.task.RepeatPresets
import com.simposio.taskeer.domain.task.TASK_REPEAT_INVALID
import com.simposio.taskeer.domain.task.nextDueAt
import com.simposio.taskeer.domain.task.presetOf
import com.simposio.taskeer.domain.task.successor
import com.simposio.taskeer.domain.task.successorIdFor
import com.simposio.taskeer.domain.task.successorReminder
import com.simposio.taskeer.domain.task.successorSubtasks
import com.simposio.taskeer.domain.task.validateRepeat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepeatRulesTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val mexico: ZoneId = ZoneId.of("America/Mexico_City")

    private fun at(text: String): Instant = Instant.parse(text)

    private fun local(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    private fun repeat(
        unit: RepeatUnit,
        interval: Int = 1,
        weekdays: Set<DayOfWeek> = emptySet(),
        anchorDay: Int = 1,
    ) = TaskRepeat(unit, interval, weekdays, anchorDay)

    @Test
    fun validate_reglaValida_devuelveNull() {
        assertNull(validateRepeat(repeat(RepeatUnit.WEEK, 2, setOf(DayOfWeek.MONDAY), 15)))
    }

    @Test
    fun validate_intervaloCero_esInvalido() {
        assertInvalid(validateRepeat(repeat(RepeatUnit.DAY, interval = 0)))
    }

    @Test
    fun validate_intervalo1000_esInvalido() {
        assertInvalid(validateRepeat(repeat(RepeatUnit.DAY, interval = 1000)))
        assertNull(validateRepeat(repeat(RepeatUnit.DAY, interval = 999)))
    }

    @Test
    fun validate_diasConUnidadDistintaDeWeek_esInvalido() {
        assertInvalid(validateRepeat(repeat(RepeatUnit.MONTH, weekdays = setOf(DayOfWeek.MONDAY))))
    }

    @Test
    fun validate_ancla0y32_sonInvalidas() {
        assertInvalid(validateRepeat(repeat(RepeatUnit.MONTH, anchorDay = 0)))
        assertInvalid(validateRepeat(repeat(RepeatUnit.MONTH, anchorDay = 32)))
        assertNull(validateRepeat(repeat(RepeatUnit.MONTH, anchorDay = 31)))
    }

    private fun assertInvalid(error: DomainError?) {
        assertTrue(error is DomainError.Validation)
        assertEquals(TASK_REPEAT_INVALID, error?.code)
    }

    @Test
    fun presets_devuelvenLasReglasEsperadas() {
        assertEquals(TaskRepeat(RepeatUnit.DAY, 1, emptySet(), 9), RepeatPresets.daily(9))
        assertEquals(
            TaskRepeat(
                RepeatUnit.WEEK,
                1,
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                9,
            ),
            RepeatPresets.weekdays(9),
        )
        assertEquals(TaskRepeat(RepeatUnit.WEEK, 1, emptySet(), 9), RepeatPresets.weekly(9))
        assertEquals(TaskRepeat(RepeatUnit.MONTH, 1, emptySet(), 9), RepeatPresets.monthly(9))
        assertEquals(TaskRepeat(RepeatUnit.YEAR, 1, emptySet(), 9), RepeatPresets.yearly(9))
        assertNull(RepeatPresets.of(RepeatPreset.NONE, 9))
        assertNull(RepeatPresets.of(RepeatPreset.CUSTOM, 9))
    }

    @Test
    fun presetDe_reconoceLasReglasEstandarYElRestoEsPersonalizada() {
        assertEquals(RepeatPreset.NONE, presetOf(null))
        assertEquals(RepeatPreset.DAILY, presetOf(RepeatPresets.daily(3)))
        assertEquals(RepeatPreset.WEEKDAYS, presetOf(RepeatPresets.weekdays(3)))
        assertEquals(RepeatPreset.WEEKLY, presetOf(RepeatPresets.weekly(3)))
        assertEquals(RepeatPreset.MONTHLY, presetOf(RepeatPresets.monthly(3)))
        assertEquals(RepeatPreset.YEARLY, presetOf(RepeatPresets.yearly(3)))
        assertEquals(RepeatPreset.CUSTOM, presetOf(repeat(RepeatUnit.DAY, interval = 2)))
        assertEquals(RepeatPreset.CUSTOM, presetOf(repeat(RepeatUnit.WEEK, weekdays = setOf(DayOfWeek.MONDAY))))
    }

    @Test
    fun diario_sumaUnDiaConservandoLaHora() {
        val result = nextDueAt(repeat(RepeatUnit.DAY), at("2026-11-10T09:00:00Z"), at("2026-11-10T12:00:00Z"), utc)

        assertEquals(at("2026-11-11T09:00:00Z"), result)
    }

    @Test
    fun diario_conIntervaloSumaNDias() {
        val result = nextDueAt(
            repeat(RepeatUnit.DAY, interval = 3),
            at("2026-11-10T09:00:00Z"),
            at("2026-11-10T12:00:00Z"),
            utc,
        )

        assertEquals(at("2026-11-13T09:00:00Z"), result)
    }

    @Test
    fun entreSemana_desdeViernes_devuelveLunes() {
        val result = nextDueAt(
            RepeatPresets.weekdays(13),
            at("2026-11-13T09:00:00Z"),
            at("2026-11-13T12:00:00Z"),
            utc,
        )

        assertEquals(at("2026-11-16T09:00:00Z"), result)
        assertEquals(DayOfWeek.MONDAY, result.atZone(utc).dayOfWeek)
    }

    @Test
    fun semanal_sinDias_mantieneElDiaDeLaSemana() {
        val result = nextDueAt(
            RepeatPresets.weekly(10),
            at("2026-11-10T09:00:00Z"),
            at("2026-11-10T10:00:00Z"),
            utc,
        )

        assertEquals(at("2026-11-17T09:00:00Z"), result)
    }

    @Test
    fun semanalConDiasEIntervalo_dentroDeLaMismaSemanaElige_elSiguienteDia() {
        val rule = repeat(RepeatUnit.WEEK, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        val result = nextDueAt(rule, at("2026-11-09T09:00:00Z"), at("2026-11-09T10:00:00Z"), utc)

        assertEquals(at("2026-11-12T09:00:00Z"), result)
    }

    @Test
    fun semanalConDiasEIntervalo_alAcabarLaSemanaSaltaNSemanas() {
        val rule = repeat(RepeatUnit.WEEK, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        val result = nextDueAt(rule, at("2026-11-12T09:00:00Z"), at("2026-11-12T10:00:00Z"), utc)

        assertEquals(at("2026-11-23T09:00:00Z"), result)
    }

    @Test
    fun mensualDia31_recortaYRecuperaElAncla() {
        val rule = repeat(RepeatUnit.MONTH, anchorDay = 31)

        val february = nextDueAt(rule, at("2027-01-31T09:00:00Z"), at("2027-01-31T10:00:00Z"), utc)
        val march = nextDueAt(rule, february, february, utc)

        assertEquals(at("2027-02-28T09:00:00Z"), february)
        assertEquals(at("2027-03-31T09:00:00Z"), march)
    }

    @Test
    fun mensualConIntervalo_saltaNMesesYRecortaAlFinal() {
        val quarterly = repeat(RepeatUnit.MONTH, interval = 3, anchorDay = 30)
        val bimonthly = repeat(RepeatUnit.MONTH, interval = 2, anchorDay = 31)

        assertEquals(
            at("2027-02-28T09:00:00Z"),
            nextDueAt(quarterly, at("2026-11-30T09:00:00Z"), at("2026-11-30T10:00:00Z"), utc),
        )
        assertEquals(
            at("2027-03-31T09:00:00Z"),
            nextDueAt(bimonthly, at("2027-01-31T09:00:00Z"), at("2027-01-31T10:00:00Z"), utc),
        )
    }

    @Test
    fun anual29Feb_enAnioNoBisiesto_usa28() {
        val result = nextDueAt(
            repeat(RepeatUnit.YEAR, anchorDay = 29),
            at("2028-02-29T09:00:00Z"),
            at("2028-02-29T10:00:00Z"),
            utc,
        )

        assertEquals(at("2029-02-28T09:00:00Z"), result)
    }

    @Test
    fun anual29Feb_enBisiesto_usa29() {
        val rule = repeat(RepeatUnit.YEAR, interval = 4, anchorDay = 29)

        val result = nextDueAt(rule, at("2024-02-29T09:00:00Z"), at("2024-02-29T10:00:00Z"), utc)

        assertEquals(at("2028-02-29T09:00:00Z"), result)
    }

    @Test
    fun vencidaHaceDias_laSiguienteEsHoyOPosterior() {
        val result = nextDueAt(
            repeat(RepeatUnit.DAY),
            at("2026-11-05T09:00:00Z"),
            at("2026-11-10T15:00:00Z"),
            utc,
        )

        assertEquals(at("2026-11-10T09:00:00Z"), result)
    }

    @Test
    fun completadaPorAdelantado_sigueDespuesDeLaFechaLimite() {
        val result = nextDueAt(
            repeat(RepeatUnit.DAY),
            at("2026-11-11T09:00:00Z"),
            at("2026-11-10T15:00:00Z"),
            utc,
        )

        assertEquals(at("2026-11-12T09:00:00Z"), result)
    }

    @Test
    fun conservaLaHoraLocal() {
        val dueAt = local(madrid, 2026, 11, 10, 8, 30)

        val result = nextDueAt(repeat(RepeatUnit.DAY), dueAt, dueAt, madrid)

        assertEquals(local(madrid, 2026, 11, 11, 8, 30), result)
    }

    @Test
    fun mismaDueAt_enDosZonas_cambiaElDiaLocal() {
        val dueAt = at("2026-11-13T02:00:00Z")
        val now = at("2026-11-12T00:00:00Z")
        val rule = RepeatPresets.weekdays(13)

        val inUtc = nextDueAt(rule, dueAt, now, utc)
        val inMexico = nextDueAt(rule, dueAt, now, mexico)

        assertEquals(DayOfWeek.MONDAY, inUtc.atZone(utc).dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, inMexico.atZone(mexico).dayOfWeek)
        assertNotEquals(inUtc, inMexico)
    }

    @Test
    fun saltoDeHorarioDeVerano_ajustaLaHoraInexistente() {
        val dueAt = local(madrid, 2027, 3, 27, 2, 30)

        val result = nextDueAt(repeat(RepeatUnit.DAY), dueAt, dueAt, madrid)

        assertEquals(at("2027-03-28T01:30:00Z"), result)
        assertEquals(LocalDateTime.of(2027, 3, 28, 3, 30), result.atZone(madrid).toLocalDateTime())
    }

    @Test
    fun finDeHorarioDeVerano_usaLaPrimeraHora() {
        val dueAt = local(madrid, 2026, 10, 24, 2, 30)

        val result = nextDueAt(repeat(RepeatUnit.DAY), dueAt, dueAt, madrid)

        assertEquals(at("2026-10-25T00:30:00Z"), result)
    }

    @Test
    fun successorIdFor_esDeterminista() {
        assertEquals(successorIdFor("tarea-1"), successorIdFor("tarea-1"))
        assertNotEquals(successorIdFor("tarea-1"), successorIdFor("tarea-2"))
        assertEquals(successorIdFor("tarea-1"), UUID.fromString(successorIdFor("tarea-1")).toString())
    }

    @Test
    fun nextTask_copiaCamposYReiniciaEstado() {
        val now = at("2026-11-10T10:00:00Z")
        val original = task(dueAt = at("2026-11-10T09:00:00Z"), repeat = RepeatPresets.daily(10))
            .copy(completedAt = now, repeatSuccessorId = "otra", description = "Detalle", assigneeId = "yo")

        val next = requireNotNull(original.successor(now, utc, columnId = "columna-2", position = 4))

        assertEquals(successorIdFor("tarea-1"), next.id)
        assertEquals("Tarea", next.title)
        assertEquals("Detalle", next.description)
        assertEquals(TaskPriority.HIGH, next.priority)
        assertEquals("yo", next.assigneeId)
        assertEquals(original.repeat, next.repeat)
        assertEquals(at("2026-11-11T09:00:00Z"), next.dueAt)
        assertNull(next.completedAt)
        assertNull(next.repeatSuccessorId)
        assertEquals(now, next.createdAt)
        assertTrue(next.sync.pending)
        assertEquals("columna-2", next.columnId)
        assertEquals(4, next.position)
    }

    @Test
    fun nextTask_sinReglaOSinFecha_devuelveNull() {
        val now = at("2026-11-10T10:00:00Z")

        assertNull(task(dueAt = at("2026-11-10T09:00:00Z"), repeat = null).successor(now, utc, null, 0))
        assertNull(task(dueAt = null, repeat = RepeatPresets.daily(10)).successor(now, utc, null, 0))
    }

    @Test
    fun nextSubtasks_vuelvenSinCompletarYDesplazanLaFecha() {
        val now = at("2026-11-10T10:00:00Z")
        val original = task(dueAt = at("2026-11-10T09:00:00Z"), repeat = RepeatPresets.weekly(10))
        val next = requireNotNull(original.successor(now, utc, "columna-1", 0))
        val done = subtask("s1", position = 0, completed = true, dueAt = at("2026-11-11T18:00:00Z"))
        val open = subtask("s2", position = 1, completed = false, dueAt = null)
        val deleted = subtask("s3", position = 2, completed = false, dueAt = null, deleted = true)

        val copies = original.successorSubtasks(next, listOf(done, open, deleted), utc, now)

        assertEquals(listOf("Subtarea s1", "Subtarea s2"), copies.map { it.title })
        assertEquals(listOf(false, false), copies.map { it.completed })
        assertEquals(listOf(0, 1), copies.map { it.position })
        assertEquals(at("2026-11-18T18:00:00Z"), copies[0].dueAt)
        assertNull(copies[1].dueAt)
        assertTrue(copies.all { it.taskId == next.id })
        assertFalse(copies.any { it.id == "s1" || it.id == "s2" })
    }

    @Test
    fun nextReminder_conservaElDesfaseRespectoALaFecha() {
        val now = at("2026-11-10T10:00:00Z")
        val original = task(dueAt = at("2026-11-10T09:00:00Z"), repeat = RepeatPresets.daily(10))
        val next = requireNotNull(original.successor(now, utc, "columna-1", 0))
        val reminder = reminder(remindAt = at("2026-11-10T08:00:00Z"), status = ReminderStatus.SENT)

        val created = requireNotNull(original.successorReminder(next, reminder, now))

        assertEquals(next.id, created.taskId)
        assertEquals(at("2026-11-11T08:00:00Z"), created.remindAt)
        assertEquals(Duration.ofHours(-1), Duration.between(requireNotNull(next.dueAt), created.remindAt))
        assertEquals(ReminderStatus.PENDING, created.status)
    }

    @Test
    fun nextReminder_pasado_noSeCrea() {
        val now = at("2026-11-10T10:00:00Z")
        val original = task(dueAt = at("2026-11-10T09:00:00Z"), repeat = RepeatPresets.daily(10))
        val next = requireNotNull(original.successor(now, utc, "columna-1", 0))
        val farBefore = reminder(remindAt = at("2026-11-08T09:00:00Z"), status = ReminderStatus.PENDING)

        assertNull(original.successorReminder(next, farBefore, now))
        assertNull(original.successorReminder(next, null, now))
    }

    private fun task(dueAt: Instant?, repeat: TaskRepeat?) = Task(
        columnId = "columna-1",
        title = "Tarea",
        description = null,
        priority = TaskPriority.HIGH,
        dueAt = dueAt,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        completedAt = null,
        repeat = repeat,
        sync = SyncMetadata("tarea-1", Instant.EPOCH, pending = false, deletedAt = null),
    )

    private fun subtask(
        id: String,
        position: Int,
        completed: Boolean,
        dueAt: Instant?,
        deleted: Boolean = false,
    ) = Subtask(
        taskId = "tarea-1",
        title = "Subtarea $id",
        completed = completed,
        position = position,
        dueAt = dueAt,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = if (deleted) Instant.EPOCH else null),
    )

    private fun reminder(remindAt: Instant, status: ReminderStatus) = Reminder(
        taskId = "tarea-1",
        remindAt = remindAt,
        status = status,
        sentAt = null,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata("rec-1", Instant.EPOCH, pending = false, deletedAt = null),
    )
}
