package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.sync.BackgroundDecision
import com.simposio.taskeer.domain.sync.BackgroundKind
import com.simposio.taskeer.domain.sync.CycleOutcome
import com.simposio.taskeer.testing.signedIn
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundSyncRunnerTest {

    private val now = Instant.parse("2026-11-27T12:00:00Z")
    private val session = MutableStateFlow<SessionState>(signedIn())
    private var enabled = true
    private var pending = 1
    private var lastSynced: Instant? = null
    private var cycles = 0
    private var outcome: () -> CycleOutcome = { CycleOutcome.COMPLETED }

    private val runner = BackgroundSyncRunner(
        session = session,
        enabled = { enabled },
        pendingCount = { pending },
        lastSyncedAt = { lastSynced },
        cycle = {
            cycles++
            outcome()
        },
        clock = { now },
    )

    @Test
    fun ajusteApagado_exitoSinCiclo() = runTest {
        enabled = false

        assertEquals(BackgroundDecision.SUCCESS, runner.run(BackgroundKind.PUSH, 0))
        assertEquals(0, cycles)
    }

    @Test
    fun sesionCerrada_exitoSinCiclo() = runTest {
        session.value = SessionState.SignedOut(expired = true)

        assertEquals(BackgroundDecision.SUCCESS, runner.run(BackgroundKind.PUSH, 0))
        assertEquals(0, cycles)
    }

    @Test
    fun resumingMasDeTreintaSegundos_reintentaSegunElIntento() = runTest {
        session.value = SessionState.Resuming

        val decisions = listOf(0, 2, 3).map { runner.run(BackgroundKind.PUSH, it) }

        assertEquals(listOf(BackgroundDecision.RETRY, BackgroundDecision.RETRY, BackgroundDecision.SUCCESS), decisions)
        assertEquals(0, cycles)
    }

    @Test
    fun resumingEsperaExactamenteTreintaSegundos() = runTest {
        session.value = SessionState.Resuming

        runner.run(BackgroundKind.PUSH, 0)

        assertEquals(30_000L, currentTime)
    }

    @Test
    fun sesionQueSaleDeResumingALosDiezSegundos_ejecutaElCiclo() = runTest {
        session.value = SessionState.Resuming
        val result = async { runner.run(BackgroundKind.PUSH, 0) }
        advanceTimeBy(10_000)
        session.value = signedIn()

        assertEquals(BackgroundDecision.SUCCESS, result.await())
        assertEquals(1, cycles)
        assertEquals(10_000L, currentTime)
    }

    @Test
    fun envioYReintentoSinPendientes_omitenElCiclo() = runTest {
        pending = 0

        assertEquals(BackgroundDecision.SUCCESS, runner.run(BackgroundKind.PUSH, 0))
        assertEquals(BackgroundDecision.SUCCESS, runner.run(BackgroundKind.RETRY, 0))
        assertEquals(0, cycles)
    }

    @Test
    fun traidaHaceCuatroMinutos_omiteElCiclo() = runTest {
        lastSynced = now - Duration.ofMinutes(4)

        runner.run(BackgroundKind.PULL, 0)

        assertEquals(0, cycles)
    }

    @Test
    fun traidaHaceSeisMinutos_ejecutaElCiclo() = runTest {
        lastSynced = now - Duration.ofMinutes(6)

        runner.run(BackgroundKind.PULL, 0)

        assertEquals(1, cycles)
    }

    @Test
    fun pausaRetrocesoYSinSesionDelMotor_sonExito() = runTest {
        listOf(CycleOutcome.PAUSED, CycleOutcome.DEFERRED, CycleOutcome.NO_SESSION).forEach { result ->
            outcome = { result }

            assertEquals("$result", BackgroundDecision.SUCCESS, runner.run(BackgroundKind.PUSH, 0))
        }
    }

    @Test
    fun excepcionInesperada_esFallo() = runTest {
        outcome = { throw IllegalStateException("detalle que no debe salir") }

        assertEquals(BackgroundDecision.FAILURE, runner.run(BackgroundKind.PUSH, 0))
    }

    @Test
    fun cancelacion_sePropaga() = runTest {
        outcome = { throw CancellationException("parado por el sistema") }

        try {
            runner.run(BackgroundKind.PUSH, 0)
            fail("la cancelación debe propagarse")
        } catch (expected: CancellationException) {
            assertEquals("parado por el sistema", expected.message)
        }
    }
}
