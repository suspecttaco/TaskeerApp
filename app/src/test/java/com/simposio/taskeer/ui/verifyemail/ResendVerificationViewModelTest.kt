package com.simposio.taskeer.ui.verifyemail

import com.simposio.taskeer.domain.auth.ResendVerificationOutcome
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
class ResendVerificationViewModelTest {

    private val auth = FakeAuthRepository()
    private lateinit var viewModel: ResendVerificationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = ResendVerificationViewModel(auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun failWith(error: DomainError, retryAfter: Long? = null) {
        auth.resendResult = ResendVerificationOutcome.Failed(error, retryAfter)
    }

    @Test
    fun resend_exitoso_muestraSent() = runTest {
        viewModel.resend()
        runCurrent()

        assertEquals(ResendMessage.SENT, state.message)
        assertFalse(state.isSending)
    }

    @Test
    fun resend_alreadyVerified_muestraAlreadyVerified() = runTest {
        auth.resendResult = ResendVerificationOutcome.AlreadyVerified
        viewModel.resend()
        runCurrent()

        assertEquals(ResendMessage.ALREADY_VERIFIED, state.message)
    }

    @Test
    fun resend_con503_muestraDisabled() = runTest {
        failWith(DomainError.Server("EMAIL_VERIFICATION_DISABLED", "x"))
        viewModel.resend()
        runCurrent()

        assertEquals(ResendMessage.DISABLED, state.message)
    }

    @Test
    fun resend_sinSesion_pideIniciarSesion() = runTest {
        failWith(DomainError.Unauthenticated("NOT_SIGNED_IN", "x"))
        viewModel.resend()
        runCurrent()

        assertEquals(ResendMessage.NEEDS_SIGN_IN, state.message)
    }

    @Test
    fun resend_enVuelo_seIgnora() = runTest {
        auth.resendGate = CompletableDeferred()
        viewModel.resend()
        runCurrent()
        viewModel.resend()
        runCurrent()

        assertEquals(1, auth.resendCalls)
        assertTrue(state.isSending)
        auth.resendGate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun resend_con429_bloqueaYDesbloquea() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 90)
        viewModel.resend()
        runCurrent()

        assertEquals(ResendMessage.RATE_LIMITED, state.message)
        assertEquals(2, state.retryMinutes)
        viewModel.resend()
        runCurrent()
        assertEquals(1, auth.resendCalls)
        advanceTimeBy(90_001)
        assertFalse(state.blocked)
    }

    @Test
    fun consumeMessage_limpiaElMensaje() = runTest {
        viewModel.resend()
        runCurrent()

        viewModel.consumeMessage()

        assertNull(state.message)
    }
}
