package com.simposio.taskeer.data.reminder

import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ReminderMechanism
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.reminder.newReminder
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeReminderRepository
import com.simposio.taskeer.testing.FakeReminderTaskRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReconcilerTest {

    private val wednesdayDigest: Instant = Instant.parse("2026-11-04T08:00:00Z")
    private var now: Instant = Instant.parse("2026-11-04T08:10:00Z")
    private var zone: ZoneId = ZoneId.of("Europe/Madrid")

    private val tasks = FakeReminderTaskRepository()
    private val checkpoints = InMemoryCheckpointStore()
    private val scheduler = RecordingReminderScheduler()
    private val notifier = RecordingNotifier()

    private fun deadlineOn(lead: ReminderLead = ReminderLead.AT_DUE) = DeadlineReminderSettings(true, lead)

    private fun digestOn(
        days: Set<DayOfWeek> = setOf(DayOfWeek.WEDNESDAY),
        time: LocalTime = LocalTime.of(9, 0),
    ) = DigestSettings(true, days, time)

    private fun reconciler(
        deadline: DeadlineReminderSettings = DeadlineReminderSettings(),
        digest: DigestSettings = DigestSettings(),
        reminders: FakeReminderRepository = FakeReminderRepository(),
    ) = ReminderReconciler(
        tasks = tasks,
        reminders = reminders,
        preferences = FakePreferencesRepository(deadline, digest),
        checkpoints = checkpoints,
        scheduler = scheduler,
        notifier = notifier,
        timeZone = TimeZoneProvider { zone },
        clock = { now },
    )

    private fun candidate(id: String, dueAt: Instant) = ReminderCandidate(id, "Tarea $id", dueAt)

    private fun reminders(vararg pending: Pair<String, Instant>) = FakeReminderRepository(
        now = now,
        taskTitles = mapOf("t1" to "Escribir el informe", "t2" to "Otra tarea"),
    ).apply {
        pending.forEach { (id, at) -> put(newReminder("t1", at, now.minusSeconds(86_400), clientId = id)) }
    }

    @Test
    fun deadline_desactivado_noAvisaNiEscribePuntoDeControl() = runTest {
        tasks.candidates.value = listOf(candidate("t1", now.minusSeconds(60)))

        reconciler().reconcile()

        assertTrue(notifier.events.isEmpty())
        assertNull(checkpoints.read(ReminderMechanism.DEADLINE))
    }

    @Test
    fun deadline_activoConFechaEnElIntervalo_avisaConElTituloDeLaTarea() = runTest {
        checkpoints.write(ReminderMechanism.DEADLINE, now.minusSeconds(600))
        tasks.candidates.value = listOf(candidate("t1", now.minusSeconds(60)))

        reconciler(deadline = deadlineOn()).reconcile()

        assertEquals(listOf<Any>(NotificationEvent.Deadline("t1", "Tarea t1")), notifier.events)
    }

    @Test
    fun deadline_conAntelacion_avisaCuandoLlegaFechaMenosAntelacion() = runTest {
        checkpoints.write(ReminderMechanism.DEADLINE, now.minusSeconds(600))
        tasks.candidates.value = listOf(candidate("t1", now.plus(Duration.ofMinutes(10))))

        reconciler(deadline = deadlineOn(ReminderLead.FIFTEEN_MINUTES)).reconcile()

        assertEquals(1, notifier.events.size)
    }

    @Test
    fun deadline_silenciado_noAvisaYAvanzaSuPuntoDeControlAAhora() = runTest {
        checkpoints.write(ReminderMechanism.DEADLINE, now.minusSeconds(600))
        tasks.candidates.value = listOf(candidate("t1", now.minusSeconds(60)))

        reconciler(deadline = deadlineOn()).reconcile(setOf(ReminderMechanism.DEADLINE))

        assertTrue(notifier.events.isEmpty())
        assertEquals(now, checkpoints.read(ReminderMechanism.DEADLINE))
    }

    @Test
    fun deadline_vencidoHaceUnaHoraOMas_noAvisa() = runTest {
        checkpoints.write(ReminderMechanism.DEADLINE, now.minus(Duration.ofHours(5)))
        tasks.candidates.value = listOf(candidate("t1", now.minus(Duration.ofHours(1))))

        reconciler(deadline = deadlineOn()).reconcile()

        assertTrue(notifier.events.isEmpty())
    }

    @Test
    fun deadline_tareaNoCandidata_noAvisa() = runTest {
        checkpoints.write(ReminderMechanism.DEADLINE, now.minusSeconds(600))
        tasks.candidates.value = emptyList()

        reconciler(deadline = deadlineOn()).reconcile()

        assertTrue(notifier.events.isEmpty())
    }

    @Test
    fun deadline_cambiarLaFecha_programaLaAlarmaAlNuevoFireAt() = runTest {
        val reconciler = reconciler(deadline = deadlineOn())
        tasks.candidates.value = listOf(candidate("t1", now.plusSeconds(600)))
        reconciler.reconcile()
        assertEquals(now.plusSeconds(600), scheduler.current)

        tasks.candidates.value = listOf(candidate("t1", now.plusSeconds(1_800)))
        reconciler.reconcile(setOf(ReminderMechanism.DEADLINE))

        assertEquals(now.plusSeconds(1_800), scheduler.current)
    }

    @Test
    fun deadline_completarOBorrarLaTarea_mueveLaAlarmaALaSiguiente() = runTest {
        val reconciler = reconciler(deadline = deadlineOn())
        tasks.candidates.value = listOf(
            candidate("t1", now.plusSeconds(600)),
            candidate("t2", now.plusSeconds(1_200)),
        )
        reconciler.reconcile()
        assertEquals(now.plusSeconds(600), scheduler.current)

        tasks.candidates.value = listOf(candidate("t2", now.plusSeconds(1_200)))
        reconciler.reconcile(setOf(ReminderMechanism.DEADLINE))

        assertEquals(now.plusSeconds(1_200), scheduler.current)
    }

    @Test
    fun digest_ocurrenciaDebida_avisaConElNumeroDeTareasActivas() = runTest {
        tasks.activeCount = 7
        checkpoints.write(ReminderMechanism.DIGEST, wednesdayDigest.minusSeconds(3_600))

        reconciler(digest = digestOn()).reconcile()

        assertEquals(listOf<Any>(NotificationEvent.Digest(7)), notifier.events)
    }

    @Test
    fun digest_sinTareasActivas_avisaIgualConCeroYAvanzaElPuntoDeControl() = runTest {
        tasks.activeCount = 0
        checkpoints.write(ReminderMechanism.DIGEST, wednesdayDigest.minusSeconds(3_600))

        reconciler(digest = digestOn()).reconcile()

        assertEquals(listOf<Any>(NotificationEvent.Digest(0)), notifier.events)
        assertEquals(now, checkpoints.read(ReminderMechanism.DIGEST))
    }

    @Test
    fun digest_sinDiasODesactivado_noAvisa() = runTest {
        checkpoints.write(ReminderMechanism.DIGEST, wednesdayDigest.minusSeconds(3_600))

        reconciler(digest = digestOn(days = emptySet())).reconcile()
        reconciler(digest = digestOn().copy(enabled = false)).reconcile()

        assertTrue(notifier.events.isEmpty())
    }

    @Test
    fun digest_silenciado_noAvisaYAvanzaSuPuntoDeControl() = runTest {
        checkpoints.write(ReminderMechanism.DIGEST, wednesdayDigest.minusSeconds(3_600))

        reconciler(digest = digestOn()).reconcile(setOf(ReminderMechanism.DIGEST))

        assertTrue(notifier.events.isEmpty())
        assertEquals(now, checkpoints.read(ReminderMechanism.DIGEST))
    }

    @Test
    fun digest_vencidoHaceUnaHoraOMas_noAvisa() = runTest {
        now = wednesdayDigest.plus(Duration.ofHours(1))
        checkpoints.write(ReminderMechanism.DIGEST, wednesdayDigest.minusSeconds(3_600))

        reconciler(digest = digestOn()).reconcile()

        assertTrue(notifier.events.isEmpty())
    }

    @Test
    fun digest_conVariosDiasAtrasadosDesdeElPuntoDeControl_avisaUnaSolaVez() = runTest {
        checkpoints.write(ReminderMechanism.DIGEST, wednesdayDigest.minus(Duration.ofDays(6)))
        val allWeek = digestOn(days = DayOfWeek.entries.toSet())

        reconciler(digest = allWeek).reconcile()

        assertEquals(1, notifier.events.filterIsInstance<NotificationEvent.Digest>().size)
    }

    @Test
    fun digest_cambioDeZona_recalculaLaAlarmaConLaZonaVigente() = runTest {
        val reconciler = reconciler(digest = digestOn())
        reconciler.reconcile()
        val madridNext = scheduler.current

        zone = ZoneId.of("America/Mexico_City")
        reconciler.reconcile(setOf(ReminderMechanism.DIGEST))

        assertEquals(Instant.parse("2026-11-11T08:00:00Z"), madridNext)
        assertEquals(Instant.parse("2026-11-04T15:00:00Z"), scheduler.current)
    }

    @Test
    fun manual_vencidoHaceMenosDeUnaHora_avisaConElTituloUnaSolaVez() = runTest {
        val reminders = reminders("r1" to now.minusSeconds(60))
        val reconciler = reconciler(reminders = reminders)

        reconciler.reconcile()
        reconciler.reconcile()

        assertEquals(
            listOf<Any>(NotificationEvent.Manual("r1", "t1", "Escribir el informe")),
            notifier.events,
        )
        assertEquals(ReminderStatus.SENT, reminders.statusOf("r1"))
    }

    @Test
    fun manual_cancelado_noAvisa() = runTest {
        val reminders = reminders("r1" to now.minusSeconds(60))
        reminders.cancelReminder("r1")

        reconciler(reminders = reminders).reconcile()

        assertTrue(notifier.events.isEmpty())
    }

    @Test
    fun manual_vencidoHaceUnaHoraOMas_noAvisaYQuedaEnSent() = runTest {
        val reminders = reminders("r1" to now.minus(Duration.ofHours(1)))

        reconciler(reminders = reminders).reconcile()

        assertTrue(notifier.events.isEmpty())
        assertEquals(ReminderStatus.SENT, reminders.statusOf("r1"))
    }

    @Test
    fun manual_deTareaCompletadaOBorrada_noAvisaYQuedaEnDismissed() = runTest {
        val reminders = FakeReminderRepository(
            now = now,
            taskTitles = mapOf("t1" to "A", "t2" to "B"),
            completedTasks = setOf("t1"),
            deletedTasks = setOf("t2"),
        ).apply {
            put(newReminder("t1", now.minusSeconds(60), now, clientId = "completada"))
            put(newReminder("t2", now.minusSeconds(60), now, clientId = "borrada"))
        }

        reconciler(reminders = reminders).reconcile()

        assertTrue(notifier.events.isEmpty())
        assertEquals(ReminderStatus.DISMISSED, reminders.statusOf("completada"))
        assertEquals(ReminderStatus.DISMISSED, reminders.statusOf("borrada"))
    }

    @Test
    fun alarma_esLaMenorEntreFechaLimiteResumenYManual() = runTest {
        tasks.candidates.value = listOf(candidate("t1", now.plusSeconds(7_200)))
        val reminders = reminders("r1" to now.plusSeconds(3_600))
        val reconciler = reconciler(deadlineOn(), digestOn(), reminders)

        reconciler.reconcile()
        assertEquals(now.plusSeconds(3_600), scheduler.current)

        reminders.cancelReminder("r1")
        reconciler.reconcile()
        assertEquals(now.plusSeconds(7_200), scheduler.current)

        tasks.candidates.value = emptyList()
        reconciler.reconcile()
        assertEquals(Instant.parse("2026-11-11T08:00:00Z"), scheduler.current)
    }

    @Test
    fun silenciarUnMecanismo_noDescartaLoPerdidoDeLosOtros() = runTest {
        checkpoints.write(ReminderMechanism.DEADLINE, now.minusSeconds(600))
        checkpoints.write(ReminderMechanism.DIGEST, wednesdayDigest.minusSeconds(3_600))
        tasks.candidates.value = listOf(candidate("t1", now.minusSeconds(60)))
        val reminders = reminders("r1" to now.minusSeconds(60))

        reconciler(deadlineOn(), digestOn(), reminders).reconcile(setOf(ReminderMechanism.DEADLINE))

        assertEquals(
            setOf("Digest", "Manual"),
            notifier.events.map { it::class.simpleName }.toSet(),
        )
    }

    @Test
    fun puntosDeControl_seEscribenAntesDelPrimerAviso() = runTest {
        checkpoints.write(ReminderMechanism.DEADLINE, now.minusSeconds(600))
        checkpoints.write(ReminderMechanism.DIGEST, wednesdayDigest.minusSeconds(3_600))
        tasks.candidates.value = listOf(candidate("t1", now.minusSeconds(60)))
        val order = mutableListOf<String>()
        checkpoints.onWrite = { order += "write" }
        notifier.onNotify = { order += "notify" }

        reconciler(deadlineOn(), digestOn()).reconcile()

        assertEquals(listOf("write", "write", "notify", "notify"), order)
    }

    @Test
    fun onEnabled_soloFijaElPuntoDeControlDelMecanismoActivado() = runTest {
        val old = now.minus(Duration.ofDays(3))
        checkpoints.write(ReminderMechanism.DEADLINE, old)
        checkpoints.write(ReminderMechanism.DIGEST, old)

        reconciler(deadlineOn(), DigestSettings(false)).onEnabled(ReminderMechanism.DEADLINE)

        assertEquals(now, checkpoints.read(ReminderMechanism.DEADLINE))
        assertEquals(old, checkpoints.read(ReminderMechanism.DIGEST))
    }

    @Test
    fun onEnabled_noAvisaDeInstantesPasados() = runTest {
        checkpoints.write(ReminderMechanism.DEADLINE, now.minus(Duration.ofDays(3)))
        tasks.candidates.value = listOf(candidate("t1", now.minusSeconds(60)))

        reconciler(deadline = deadlineOn()).onEnabled(ReminderMechanism.DEADLINE)

        assertTrue(notifier.events.isEmpty())
    }

    @Test
    fun puntosDeControl_noRetroceden() = runTest {
        val future = now.plusSeconds(500)
        checkpoints.write(ReminderMechanism.DEADLINE, future)
        checkpoints.write(ReminderMechanism.DIGEST, future)

        reconciler(deadlineOn(), digestOn()).reconcile()

        assertEquals(future, checkpoints.read(ReminderMechanism.DEADLINE))
        assertEquals(future, checkpoints.read(ReminderMechanism.DIGEST))
    }

    @Test
    fun todoDesactivadoYSinPendientes_cancelaLaAlarma() = runTest {
        scheduler.scheduleAt(now.plusSeconds(60))

        reconciler().reconcile()

        assertEquals(1, scheduler.cancelCalls)
        assertNull(scheduler.current)
    }

    @Test
    fun elNotificadorQueDevuelveFalse_noAlteraElFlujo() = runTest {
        notifier.shown = false
        val reminders = reminders("r1" to now.minusSeconds(60), "r2" to now.plusSeconds(900))

        reconciler(reminders = reminders).reconcile()

        assertEquals(1, notifier.events.size)
        assertEquals(ReminderStatus.SENT, reminders.statusOf("r1"))
        assertEquals(now.plusSeconds(900), scheduler.current)
    }

    @Test
    fun missedYDiscarded_nuncaGeneranNotificationEvent() = runTest {
        val reminders = reminders("r1" to now.minus(Duration.ofHours(3)))

        reconciler(reminders = reminders).reconcile()

        assertTrue(notifier.events.none { it is NotificationEvent.Manual })
    }
}
