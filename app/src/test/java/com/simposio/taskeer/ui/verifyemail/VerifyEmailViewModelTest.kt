package com.simposio.taskeer.ui.verifyemail

import com.simposio.taskeer.domain.auth.VerifyEmailOutcome
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.testing.FakeAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailViewModelTest {

    private val auth = FakeAuthRepository()
    private lateinit var viewModel: VerifyEmailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = VerifyEmailViewModel(auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun failWith(error: DomainError, retryAfter: Long? = null) {
        auth.verifyResult = VerifyEmailOutcome.Failed(error, retryAfter)
    }

    @Test
    fun submit_exitoso_pasaAVerified() = runTest {
        viewModel.submit("abc123")
        runCurrent()

        assertEquals(VerifyEmailPhase.VERIFIED, state.phase)
        assertEquals(listOf("abc123"), auth.verifyTokens)
    }

    @Test
    fun submit_conEnlace_enviaSoloElToken() = runTest {
        viewModel.submit("https://web.test/verificar-correo?token=abc123")
        runCurrent()

        assertEquals(listOf("abc123"), auth.verifyTokens)
    }

    @Test
    fun submit_entradaInvalida_noLlamaAlRepositorio() = runTest {
        viewModel.submit("   ")
        runCurrent()

        assertEquals(0, auth.verifyCalls)
        assertEquals(VerifyEmailError.INPUT_INVALID, state.error)
        assertEquals(VerifyEmailPhase.INPUT, state.phase)
    }

    @Test
    fun submit_enVuelo_seIgnora() = runTest {
        auth.verifyGate = CompletableDeferred()
        viewModel.submit("abc")
        runCurrent()
        viewModel.submit("abc")
        runCurrent()

        assertEquals(1, auth.verifyCalls)
        assertTrue(state.isSubmitting)
        auth.verifyGate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun submit_fueraDeInput_seIgnora() = runTest {
        viewModel.submit("abc")
        runCurrent()
        viewModel.submit("abc")
        runCurrent()

        assertEquals(1, auth.verifyCalls)
    }

    @Test
    fun submit_bloqueadoPor429_seIgnora() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 60)
        viewModel.submit("abc")
        runCurrent()
        viewModel.submit("abc")
        runCurrent()

        assertEquals(1, auth.verifyCalls)
        assertEquals(VerifyEmailBlock.RATE_LIMITED, state.blocked)
    }

    @Test
    fun submit_con429_bloqueaYDesbloqueaConTiempoVirtual() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 90)
        viewModel.submit("abc")
        runCurrent()

        assertEquals(2, state.retryMinutes)
        assertFalse(state.canSubmit)
        advanceTimeBy(89_000)
        assertEquals(VerifyEmailBlock.RATE_LIMITED, state.blocked)
        advanceTimeBy(1_001)
        assertNull(state.blocked)
    }

    @Test
    fun submit_con400_noMarcaVerificadoYPermiteReintentar() = runTest {
        failWith(DomainError.Validation("INVALID_OR_EXPIRED_TOKEN", "x"))
        viewModel.submit("abc")
        runCurrent()

        assertEquals(VerifyEmailError.INVALID_OR_EXPIRED, state.error)
        assertEquals(VerifyEmailPhase.INPUT, state.phase)
        assertTrue(state.canSubmit)
    }

    @Test
    fun submit_sinRed_muestraNetwork() = runTest {
        failWith(DomainError.Network("NETWORK_TIMEOUT", "x"))
        viewModel.submit("abc")
        runCurrent()

        assertEquals(VerifyEmailError.NETWORK, state.error)
    }

    @Test
    fun onInputEdited_limpiaElError() = runTest {
        viewModel.submit("")
        assertEquals(VerifyEmailError.INPUT_INVALID, state.error)

        viewModel.onInputEdited()

        assertNull(state.error)
    }

    @Test
    fun uiState_noTieneCamposSecretos() {
        val forbidden = listOf("token", "code", "link", "input", "text", "raw")
        val fields = VerifyEmailUiState::class.java.declaredFields.map { it.name.lowercase() }

        assertTrue(fields.none { name -> forbidden.any { name.contains(it) } })
    }
}
