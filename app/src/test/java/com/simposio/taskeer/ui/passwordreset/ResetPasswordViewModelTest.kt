package com.simposio.taskeer.ui.passwordreset

import com.simposio.taskeer.domain.auth.ResetPasswordOutcome
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.testing.FakeAuthRepository
import java.lang.reflect.Modifier
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
class ResetPasswordViewModelTest {

    private val auth = FakeAuthRepository()
    private lateinit var viewModel: ResetPasswordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = ResetPasswordViewModel(auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun secret(value: String = "nueva-clave") = value.toCharArray()

    private fun submit(
        email: String = "ana@example.com",
        code: String = "ABCD-EF12",
        password: CharArray = secret(),
        confirmation: CharArray = secret(),
    ) = viewModel.submit(email, code, password, confirmation)

    private fun failWith(error: DomainError, retryAfter: Long? = null) {
        auth.resetResult = ResetPasswordOutcome.Failed(error, retryAfter)
    }

    @Test
    fun submit_exitoso_marcaDone() = runTest {
        submit()
        runCurrent()

        assertTrue(state.done)
        assertFalse(state.isSubmitting)
        assertEquals(1, auth.resetCalls)
    }

    @Test
    fun submit_conDatosInvalidos_noLlamaYMuestraErrorPorCampo() = runTest {
        submit(email = "sin-arroba")
        assertEquals(ResetPasswordError.EMAIL_INVALID, state.error)
        submit(code = "ABC")
        assertEquals(ResetPasswordError.CODE_INVALID, state.error)
        submit(password = secret("corta"), confirmation = secret("corta"))
        assertEquals(ResetPasswordError.PASSWORD_INVALID, state.error)
        submit(confirmation = secret("otra-clave"))
        assertEquals(ResetPasswordError.PASSWORD_MISMATCH, state.error)
        runCurrent()

        assertEquals(0, auth.resetCalls)
        assertFalse(state.done)
    }

    @Test
    fun submit_enVuelo_seIgnora() = runTest {
        auth.resetGate = CompletableDeferred()

        submit()
        runCurrent()
        submit()
        runCurrent()

        assertTrue(state.isSubmitting)
        assertEquals(1, auth.resetCalls)
        auth.resetGate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun submit_bloqueadoPor429_seIgnora() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 120L)
        submit()
        runCurrent()

        submit()
        runCurrent()

        assertEquals(1, auth.resetCalls)
        assertEquals(ResetPasswordBlock.RATE_LIMITED, state.block)
        assertFalse(state.canSubmit)
    }

    @Test
    fun submit_con429_bloqueaYDesbloqueaConTiempoVirtual() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 120L)
        submit()
        runCurrent()

        assertEquals(ResetPasswordError.RATE_LIMITED, state.error)
        assertEquals(2, state.retryMinutes)
        advanceTimeBy(119_000)
        runCurrent()
        assertEquals(ResetPasswordBlock.RATE_LIMITED, state.block)
        advanceTimeBy(2_000)
        runCurrent()

        assertNull(state.block)
        assertTrue(state.canSubmit)
    }

    @Test
    fun submit_conCorreoOCodigoInvalido_producenElMismoEstado() = runTest {
        failWith(DomainError.Validation("INVALID_RECOVERY_CODE", "correo inexistente"))
        submit(email = "nadie@example.com")
        runCurrent()
        val unknownEmail = state

        val other = ResetPasswordViewModel(auth)
        failWith(DomainError.Validation("INVALID_RECOVERY_CODE", "codigo usado"))
        other.submit("ana@example.com", "ZZZZ-9999", secret(), secret())
        runCurrent()

        assertEquals(ResetPasswordError.INCORRECT, unknownEmail.error)
        assertEquals(unknownEmail, other.uiState.value)
    }

    @Test
    fun reset_exitoso_noGuardaCodigosNiEstadoDeUso() = runTest {
        submit(code = "abcd-ef12")
        runCurrent()

        assertEquals(ResetPasswordUiState(done = true, secretsWiped = 1), state)
        assertEquals(1, auth.resetCalls)
    }

    @Test
    fun submit_envia_elCodigoNormalizado() = runTest {
        submit(email = "  ana@example.com ", code = " abcdef12 ")
        runCurrent()

        assertEquals("ABCD-EF12", auth.lastCodeSent)
        assertEquals(listOf("ana@example.com"), auth.resetEmails)
    }

    @Test
    fun submit_con400_vaciaElCodigoYSoloEllo() = runTest {
        failWith(DomainError.Validation("INVALID_RECOVERY_CODE", "x"))

        submit()
        runCurrent()

        assertEquals(1, state.incorrectCount)
        assertEquals(1, state.secretsWiped)
    }

    @Test
    fun submit_conErrorDeRed_noVaciaElCodigo() = runTest {
        failWith(DomainError.Network("NETWORK_TIMEOUT", "x"))

        submit()
        runCurrent()

        assertEquals(ResetPasswordError.NETWORK, state.error)
        assertEquals(0, state.incorrectCount)
    }

    @Test
    fun submit_rellenaDeCerosLasContrasenas() = runTest {
        val password = secret()
        val confirmation = secret()

        submit(password = password, confirmation = confirmation)
        runCurrent()

        assertTrue(password.all { it == '\u0000' })
        assertTrue(confirmation.all { it == '\u0000' })
    }

    @Test
    fun mobileClientNotSupported_bloqueaConNotSupportedYSeDescarta() = runTest {
        failWith(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x"))
        submit()
        runCurrent()

        assertEquals(ResetPasswordBlock.NOT_SUPPORTED, state.block)
        viewModel.dismissNotSupported()

        assertNull(state.block)
    }

    @Test
    fun onFieldEdited_limpiaElError() = runTest {
        failWith(DomainError.Network("NETWORK_TIMEOUT", "x"))
        submit()
        runCurrent()

        viewModel.onFieldEdited()

        assertNull(state.error)
    }

    @Test
    fun uiState_noTieneCamposSecretos() {
        val offenders = ResetPasswordUiState::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .filter { it.contains("password", ignoreCase = true) || it.contains("code", ignoreCase = true) }

        assertTrue("campos secretos: $offenders", offenders.isEmpty())
    }
}
