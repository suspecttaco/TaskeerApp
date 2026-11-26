package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.SessionState
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
class LoginViewModelTest {

    private val auth = FakeAuthRepository()
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = LoginViewModel(auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun password(value: String = "secreta") = value.toCharArray()

    private fun failed(error: DomainError, retryAfter: Long? = null) {
        auth.loginResult = LoginOutcome.Failed(error, retryAfter)
    }

    @Test
    fun submit_validacionFalla_noLlamaAlRepositorioYMuestraElErrorDeCampo() = runTest {
        viewModel.submit("sin-arroba", password())
        runCurrent()

        assertTrue(auth.loginEmails.isEmpty())
        assertEquals(LoginError.EMAIL_INVALID, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun submit_exito_marcaSignedIn() = runTest {
        viewModel.submit("ana@example.com", password())
        runCurrent()

        assertTrue(viewModel.uiState.value.signedIn)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun submit_invalidCredentials_incrementaPasswordResetCount() = runTest {
        failed(DomainError.Unauthenticated("INVALID_CREDENTIALS", "x"))

        viewModel.submit("ana@example.com", password())
        runCurrent()

        assertEquals(1, viewModel.uiState.value.passwordResetCount)
        assertEquals(LoginError.INVALID_CREDENTIALS, viewModel.uiState.value.error)
    }

    @Test
    fun submit_network_noIncrementaPasswordResetCount() = runTest {
        failed(DomainError.Network("NETWORK_TIMEOUT", "x"))

        viewModel.submit("ana@example.com", password())
        runCurrent()

        assertEquals(0, viewModel.uiState.value.passwordResetCount)
        assertEquals(LoginError.NETWORK, viewModel.uiState.value.error)
    }

    @Test
    fun submit_enVuelo_seIgnora() = runTest {
        auth.loginGate = CompletableDeferred()

        viewModel.submit("ana@example.com", password())
        runCurrent()
        viewModel.submit("ana@example.com", password())
        runCurrent()

        assertEquals(1, auth.loginEmails.size)
    }

    @Test
    fun submit_marcaIsSubmittingYLoRetiraSiempre() = runTest {
        val gate = CompletableDeferred<Unit>().also { auth.loginGate = it }
        failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        viewModel.submit("ana@example.com", password())
        runCurrent()
        assertTrue(viewModel.uiState.value.isSubmitting)
        gate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun submit_rateLimited_bloqueaElBotonYLoLiberaTrasElRetryAfter() = runTest {
        failed(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfter = 90)

        viewModel.submit("ana@example.com", password())
        runCurrent()
        assertEquals(LoginBlock.RATE_LIMITED, viewModel.uiState.value.blocked)
        assertFalse(viewModel.uiState.value.canSubmit)
        assertEquals(2, viewModel.uiState.value.retryMinutes)

        advanceTimeBy(89_000)
        runCurrent()
        assertEquals(LoginBlock.RATE_LIMITED, viewModel.uiState.value.blocked)
        advanceTimeBy(1_001)
        runCurrent()

        assertNull(viewModel.uiState.value.blocked)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun submit_rateLimited_sinDato_noBloquea() = runTest {
        failed(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfter = null)

        viewModel.submit("ana@example.com", password())
        runCurrent()

        assertNull(viewModel.uiState.value.blocked)
        assertNull(viewModel.uiState.value.retryMinutes)
        assertEquals(LoginError.RATE_LIMITED, viewModel.uiState.value.error)
    }

    @Test
    fun submit_mobileClientNotSupported_bloqueaConPaginaDeEstado() = runTest {
        failed(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x"))

        viewModel.submit("ana@example.com", password())
        runCurrent()

        assertEquals(LoginBlock.NOT_SUPPORTED, viewModel.uiState.value.blocked)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun editarCorreoOPassword_limpiaElError_peroNoElBloqueo() = runTest {
        failed(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfter = 300)
        viewModel.submit("ana@example.com", password())
        runCurrent()

        viewModel.onPasswordEdited()
        assertNull(viewModel.uiState.value.error)
        assertEquals(LoginBlock.RATE_LIMITED, viewModel.uiState.value.blocked)

        failed(DomainError.Network("NETWORK_TIMEOUT", "x"))
        viewModel.onEmailChange("otra@example.com")
        assertNull(viewModel.uiState.value.error)
        assertEquals(LoginBlock.RATE_LIMITED, viewModel.uiState.value.blocked)
    }

    @Test
    fun volverDeNoCompatible_regresaAlFormularioConElCorreo() = runTest {
        failed(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x"))
        viewModel.submit("ana@example.com", password())
        runCurrent()

        viewModel.dismissNotSupported()

        assertNull(viewModel.uiState.value.blocked)
        assertEquals("ana@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun passwordNoApareceEnElEstado() {
        val offenders = LoginUiState::class.java.declaredFields
            .filter { it.name.contains("password", ignoreCase = true) || it.name.contains("token", ignoreCase = true) }
            .filterNot { it.name == "passwordResetCount" && it.type == Int::class.javaPrimitiveType }

        assertTrue("campos secretos en LoginUiState: ${offenders.map { it.name }}", offenders.isEmpty())
    }

    @Test
    fun vaciaLaPasswordDelViewModelTrasCadaCamino() = runTest {
        val invalid = password()
        viewModel.submit("sin-arroba", invalid)
        runCurrent()
        assertTrue(invalid.all { it == '\u0000' })

        val success = password()
        viewModel.submit("ana@example.com", success)
        runCurrent()
        assertTrue(success.all { it == '\u0000' })

        val repositoryArray = requireNotNull(auth.lastPasswordArray)
        assertEquals("secreta", auth.passwordAtCall)
        assertTrue(repositoryArray.all { it == '\u0000' })

        failed(DomainError.Network("NETWORK_TIMEOUT", "x"))
        val failure = password()
        LoginViewModel(auth).also { it.submit("ana@example.com", failure) }
        runCurrent()
        assertTrue(failure.all { it == '\u0000' })

        val blockedViewModel = LoginViewModel(auth)
        failed(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfter = 60)
        blockedViewModel.submit("ana@example.com", password())
        runCurrent()
        val ignored = password()
        blockedViewModel.submit("ana@example.com", ignored)
        assertTrue(ignored.all { it == '\u0000' })
    }

    @Test
    fun sesionIniciadaMientrasLaPantallaEstaAbierta_marcaSignedIn() = runTest {
        auth.state.value = SessionState.SignedIn(AccountSummary("ana@example.com", "Ana"), offline = false)
        runCurrent()

        assertTrue(viewModel.uiState.value.signedIn)
    }
}
