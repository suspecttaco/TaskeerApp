package com.simposio.taskeer.data.reminder

import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ReminderMechanism
import com.simposio.taskeer.domain.reminder.newReminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderTriggersTest {

    private val due: Instant = Instant.parse("2026-11-05T12:00:00Z")
    private val candidates = MutableStateFlow(listOf(ReminderCandidate("t1", "Informe", due)))
    private val deadline = MutableStateFlow(DeadlineReminderSettings(true, ReminderLead.ONE_HOUR))
    private val digest = MutableStateFlow(DigestSettings(true, setOf(DayOfWeek.MONDAY), LocalTime.of(9, 0)))
    private val pending = MutableStateFlow<List<Reminder>>(emptyList())
    private val calls = mutableListOf<Set<ReminderMechanism>>()

    private fun kotlinx.coroutines.test.TestScope.startTriggers() {
        val triggers = ReminderTriggers(candidates, deadline, digest, pending) { calls += it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { triggers.run() }
    }

    @Test
    fun primeraEmision_concilia_sinSilencio() = runTest {
        startTriggers()

        assertEquals(listOf(emptySet<ReminderMechanism>()), calls)
    }

    @Test
    fun cambioDeFechaLimite_silenciaElMecanismoDeFechaLimite() = runTest {
        startTriggers()

        candidates.value = listOf(ReminderCandidate("t1", "Informe", due.plusSeconds(60)))

        assertEquals(setOf(ReminderMechanism.DEADLINE), calls.last())
    }

    @Test
    fun cambioDeAntelacion_silenciaElMecanismoDeFechaLimite() = runTest {
        startTriggers()

        deadline.value = DeadlineReminderSettings(true, ReminderLead.ONE_DAY)

        assertEquals(setOf(ReminderMechanism.DEADLINE), calls.last())
    }

    @Test
    fun cambioDeDiasOHoraDelResumen_silenciaElResumen() = runTest {
        startTriggers()

        digest.value = digest.value.copy(days = setOf(DayOfWeek.TUESDAY))
        assertEquals(setOf(ReminderMechanism.DIGEST), calls.last())

        digest.value = digest.value.copy(time = LocalTime.of(10, 0))
        assertEquals(setOf(ReminderMechanism.DIGEST), calls.last())
    }

    @Test
    fun cambioDeAmbosMecanismosEnEmisionesSucesivas_silenciaCadaUnoPorSeparado() = runTest {
        startTriggers()

        candidates.value = emptyList()
        digest.value = digest.value.copy(enabled = false)

        assertEquals(setOf(ReminderMechanism.DEADLINE), calls[1])
        assertEquals(setOf(ReminderMechanism.DIGEST), calls[2])
    }

    @Test
    fun soloCambioEnPendientesManuales_concilia_sinSilencio() = runTest {
        startTriggers()

        pending.value = listOf(newReminder("t1", due, due.minusSeconds(3_600), clientId = "r1"))

        assertEquals(listOf(emptySet<ReminderMechanism>(), emptySet()), calls)
    }

    @Test
    fun emisionIdentica_noConcilia() = runTest {
        startTriggers()

        candidates.value = listOf(ReminderCandidate("t1", "Informe", due))
        deadline.value = DeadlineReminderSettings(true, ReminderLead.ONE_HOUR)

        assertEquals(1, calls.size)
    }

    @Test
    fun cambioSoloDeTituloDeLaTarea_noSilenciaNiConcilia() = runTest {
        startTriggers()

        candidates.value = listOf(ReminderCandidate("t1", "Otro título", due))

        assertEquals(1, calls.size)
    }
}
