package com.simposio.taskeer.ui.accountsecurity

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.ChangePasswordOutcome
import com.simposio.taskeer.domain.auth.SessionState
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
class ChangePasswordViewModelTest {

    private val signedIn = SessionState.SignedIn(AccountSummary("ana@example.com", "Ana"), offline = false)
    private val auth = FakeAuthRepository(signedIn)
    private lateinit var viewModel: ChangePasswordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = ChangePasswordViewModel(auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun secret(value: String) = value.toCharArray()

    private fun submit(
        current: CharArray = secret("vieja-clave"),
        newPassword: CharArray = secret("nueva-clave"),
        confirmation: CharArray = secret("nueva-clave"),
    ) = viewModel.submit(current, newPassword, confirmation)

    private fun failWith(error: DomainError, retryAfter: Long? = null) {
        auth.changeResult = ChangePasswordOutcome.Failed(error, retryAfter)
    }

    @Test
    fun submit_exitoso_marcaDoneChanged() = runTest {
        submit()
        runCurrent()

        assertEquals(ChangePasswordDone.CHANGED, state.done)
        assertEquals(1, auth.changeCalls)
    }

    @Test
    fun submit_changedSignedOut_marcaDoneConAvisoDeReinicio() = runTest {
        auth.changeResult = ChangePasswordOutcome.ChangedSignedOut

        submit()
        runCurrent()

        assertEquals(ChangePasswordDone.SIGNED_OUT, state.done)
    }

    @Test
    fun submit_conInvalidCredentials_noMarcaDoneYConservaLaSesion() = runTest {
        failWith(DomainError.Unauthenticated("INVALID_CREDENTIALS", "x"))

        submit()
        runCurrent()

        assertEquals(ChangePasswordError.CURRENT_INCORRECT, state.error)
        assertNull(state.done)
        assertEquals(signedIn, auth.state.value)
        assertTrue(state.canSubmit)
    }

    @Test
    fun submit_enVuelo_seIgnora() = runTest {
        auth.changeGate = CompletableDeferred()

        submit()
        runCurrent()
        submit()
        runCurrent()

        assertTrue(state.isSubmitting)
        assertEquals(1, auth.changeCalls)
        auth.changeGate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun submit_con429_bloqueaYDesbloquea() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 60L)
        submit()
        runCurrent()
        assertTrue(state.blocked)
        assertEquals(1, state.retryMinutes)

        submit()
        runCurrent()
        assertEquals(1, auth.changeCalls)
        advanceTimeBy(61_000)
        runCurrent()

        assertFalse(state.blocked)
    }

    @Test
    fun submit_validacionLocal_noLlama() = runTest {
        submit(current = charArrayOf())
        assertEquals(ChangePasswordError.CURRENT_INVALID, state.error)
        submit(newPassword = secret("corta"), confirmation = secret("corta"))
        assertEquals(ChangePasswordError.NEW_INVALID, state.error)
        submit(confirmation = secret("otra-clave"))
        assertEquals(ChangePasswordError.MISMATCH, state.error)
        runCurrent()

        assertEquals(0, auth.changeCalls)
    }

    @Test
    fun submit_rellenaDeCerosLasTresContrasenas() = runTest {
        val current = secret("vieja-clave")
        val new = secret("nueva-clave")
        val confirmation = secret("nueva-clave")

        submit(current, new, confirmation)
        runCurrent()

        assertTrue((current + new + confirmation).all { it == '\u0000' })
    }

    @Test
    fun uiState_noTieneCamposSecretos() {
        val offenders = ChangePasswordUiState::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .filter { it.contains("password", ignoreCase = true) || it.contains("code", ignoreCase = true) }

        assertTrue("campos secretos: $offenders", offenders.isEmpty())
    }
}
