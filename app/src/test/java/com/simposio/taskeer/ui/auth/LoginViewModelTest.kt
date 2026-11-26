package com.simposio.taskeer.ui.auth

import androidx.lifecycle.SavedStateHandle
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.GoogleCredentialResult
import com.simposio.taskeer.domain.auth.GoogleSignInConfig
import com.simposio.taskeer.domain.auth.GoogleSignInOutcome
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.testing.FakeGoogleCredentialSource
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val auth = FakeAuthRepository()
    private val google = FakeGoogleCredentialSource()
    private var savedState = SavedStateHandle()
    private lateinit var viewModel: LoginViewModel

    private fun newViewModel(
        config: GoogleSignInConfig = GoogleSignInConfig("web-client"),
        handle: SavedStateHandle = SavedStateHandle(),
    ): LoginViewModel {
        savedState = handle
        return LoginViewModel(auth, google, config, handle)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = newViewModel()
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
        newViewModel().also { it.submit("ana@example.com", failure) }
        runCurrent()
        assertTrue(failure.all { it == '\u0000' })

        val blockedViewModel = newViewModel()
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

    @Test
    fun prefill_conAvisoDeCuentaCreada_fijaCorreoYAviso() = runTest {
        viewModel.prefill("ana@example.com")

        assertEquals("ana@example.com", viewModel.uiState.value.email)
        assertEquals(LoginNotice.ACCOUNT_CREATED, viewModel.uiState.value.notice)
    }

    @Test
    fun prefill_seApagaAlEditar() = runTest {
        viewModel.prefill("ana@example.com")
        viewModel.onEmailChange("otra@example.com")

        assertNull(viewModel.uiState.value.notice)

        viewModel.prefill("ana@example.com")
        viewModel.onPasswordEdited()

        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun prefill_seApagaAlEnviar() = runTest {
        viewModel.prefill("ana@example.com")

        viewModel.submit("ana@example.com", password())
        runCurrent()

        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun prefill_conAvisoDeReset_fijaCorreoYAviso() = runTest {
        viewModel.prefill("ana@example.com", LoginNotice.PASSWORD_RESET)

        assertEquals("ana@example.com", viewModel.uiState.value.email)
        assertEquals(LoginNotice.PASSWORD_RESET, viewModel.uiState.value.notice)
    }

    @Test
    fun prefill_conAvisoDeReset_seApagaAlEditarLaContrasena() = runTest {
        viewModel.prefill("ana@example.com", LoginNotice.PASSWORD_RESET)

        viewModel.onPasswordEdited()

        assertNull(viewModel.uiState.value.notice)
    }

    private val host = Any()
    private val ana = AccountSummary("ana@example.com", "Ana")
    private val codes = RecoveryCodes(List(RecoveryCodes.EXPECTED_COUNT) { "CODE-%04d".format(it) })

    private fun googleFailed(error: DomainError, retryAfter: Long? = null) {
        auth.googleResult = GoogleSignInOutcome.Failed(error, retryAfter)
    }

    @Test
    fun google_exitoso_emiteSignedIn() = runTest {
        viewModel.signInWithGoogle(host)
        runCurrent()

        assertTrue(viewModel.uiState.value.signedIn)
        assertEquals(1, auth.googleCalls)
        assertEquals("id-token-secreto", auth.googleCredentials.single().idToken)
        assertSame(host, google.hosts.single())
    }

    @Test
    fun google_enVuelo_ignoraUnSegundoToque() = runTest {
        google.gate = CompletableDeferred()

        viewModel.signInWithGoogle(host)
        runCurrent()
        assertTrue(viewModel.uiState.value.googleInProgress)
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.signInWithGoogle(host)
        runCurrent()

        assertEquals(1, google.calls)
    }

    @Test
    fun google_enVuelo_deshabilitaElLoginDeCorreo() = runTest {
        google.gate = CompletableDeferred()
        viewModel.signInWithGoogle(host)
        runCurrent()

        viewModel.submit("ana@example.com", password())
        runCurrent()

        assertTrue(auth.loginEmails.isEmpty())
    }

    @Test
    fun google_enVuelo_laPeticionAlRepositorioIgnoraUnSegundoToque() = runTest {
        auth.googleGate = CompletableDeferred()

        viewModel.signInWithGoogle(host)
        runCurrent()
        viewModel.signInWithGoogle(host)
        runCurrent()

        assertEquals(1, auth.googleCalls)
    }

    @Test
    fun google_cancelado_vuelveAReposoSinError() = runTest {
        google.result = GoogleCredentialResult.Cancelled

        viewModel.signInWithGoogle(host)
        runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertFalse(state.googleInProgress)
        assertFalse(state.signedIn)
        assertEquals(0, auth.googleCalls)
    }

    @Test
    fun google_sinCuentaNoDisponibleOFallo_muestraSuError() = runTest {
        val expected = mapOf(
            GoogleCredentialResult.NoAccount to LoginError.GOOGLE_NO_ACCOUNT,
            GoogleCredentialResult.Unavailable to LoginError.GOOGLE_UNAVAILABLE,
            GoogleCredentialResult.Failed to LoginError.GOOGLE_FAILED,
        )
        expected.forEach { (result, error) ->
            google.result = result
            viewModel.signInWithGoogle(host)
            runCurrent()

            assertEquals(error, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.googleInProgress)
        }
        assertEquals(0, auth.googleCalls)
    }

    @Test
    fun google_fallo_noEmiteSignedIn() = runTest {
        googleFailed(DomainError.Unauthenticated("INVALID_GOOGLE_TOKEN", "x"))

        viewModel.signInWithGoogle(host)
        runCurrent()

        assertFalse(viewModel.uiState.value.signedIn)
        assertEquals(LoginError.GOOGLE_TOKEN_INVALID, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.googleInProgress)
    }

    @Test
    fun google_cuentaLocalSinVerificar_muestraSuMensaje() = runTest {
        googleFailed(DomainError.Conflict("LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED", "x"))

        viewModel.signInWithGoogle(host)
        runCurrent()

        assertEquals(LoginError.LOCAL_ACCOUNT_NOT_VERIFIED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.signedIn)
    }

    @Test
    fun google_bloqueadoPor429_deshabilitaYLiberaTrasElRetryAfter() = runTest {
        googleFailed(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfter = 90)

        viewModel.signInWithGoogle(host)
        runCurrent()
        assertEquals(LoginBlock.RATE_LIMITED, viewModel.uiState.value.blocked)
        assertFalse(viewModel.uiState.value.canUseGoogle)
        viewModel.signInWithGoogle(host)
        runCurrent()
        assertEquals(1, google.calls)

        advanceTimeBy(90_001)
        runCurrent()

        assertNull(viewModel.uiState.value.blocked)
        assertTrue(viewModel.uiState.value.canUseGoogle)
    }

    @Test
    fun google_versionNoCompatible_bloqueaConPaginaDeEstado() = runTest {
        googleFailed(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x"))

        viewModel.signInWithGoogle(host)
        runCurrent()

        assertEquals(LoginBlock.NOT_SUPPORTED, viewModel.uiState.value.blocked)
    }

    @Test
    fun google_sinClientId_noSeOfrece() = runTest {
        val unavailable = newViewModel(config = GoogleSignInConfig(""))

        unavailable.signInWithGoogle(host)
        runCurrent()

        assertFalse(unavailable.uiState.value.googleAvailable)
        assertEquals(0, google.calls)
    }

    @Test
    fun google_conClientId_seOfrece() {
        assertTrue(viewModel.uiState.value.googleAvailable)
    }

    @Test
    fun google_conCodigos_noEmiteSignedInHastaConfirmar() = runTest {
        auth.googleResult = GoogleSignInOutcome.SignedIn(ana, codes)
        viewModel.signInWithGoogle(host)
        runCurrent()
        auth.state.value = SessionState.SignedIn(ana, offline = false)
        runCurrent()

        assertEquals(LoginPhase.CODES, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.signedIn)
        assertSame(codes, viewModel.recoveryCodes.value)
        assertEquals(true, savedState.get<Boolean>(LoginViewModel.KEY_CODES_PENDING))

        viewModel.confirmCodesSaved()
        assertFalse(viewModel.uiState.value.signedIn)
        viewModel.onSavedCheckedChange(true)
        viewModel.confirmCodesSaved()

        assertTrue(viewModel.uiState.value.signedIn)
        assertNull(viewModel.recoveryCodes.value)
        assertNull(savedState.get<Boolean>(LoginViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun google_conCodigos_descartaAlSalir() = runTest {
        auth.googleResult = GoogleSignInOutcome.SignedIn(ana, codes)
        viewModel.signInWithGoogle(host)
        runCurrent()

        viewModel.leaveWithoutSaving()

        assertTrue(viewModel.uiState.value.signedIn)
        assertNull(viewModel.recoveryCodes.value)
        assertNull(savedState.get<Boolean>(LoginViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun google_conCodigosPendientesTrasRecrear_pasaACodesLost() = runTest {
        auth.state.value = SessionState.SignedIn(ana, offline = false)
        val restored = newViewModel(handle = SavedStateHandle(mapOf(LoginViewModel.KEY_CODES_PENDING to true)))
        runCurrent()

        assertEquals(LoginPhase.CODES_LOST, restored.uiState.value.phase)
        assertFalse(restored.uiState.value.signedIn)
        assertNull(restored.recoveryCodes.value)

        restored.acknowledgeLostCodes()

        assertEquals(LoginPhase.FORM, restored.uiState.value.phase)
        assertTrue(restored.uiState.value.signedIn)
        assertNull(savedState.get<Boolean>(LoginViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun google_alLimpiar_vaciaLosCodigos() = runTest {
        auth.googleResult = GoogleSignInOutcome.SignedIn(ana, codes)
        viewModel.signInWithGoogle(host)
        runCurrent()

        viewModel.onCleared()

        assertNull(viewModel.recoveryCodes.value)
    }

    @Test
    fun google_noGuardaTokenNiCodigosEnElEstado() = runTest {
        auth.googleResult = GoogleSignInOutcome.SignedIn(ana, codes)
        viewModel.signInWithGoogle(host)
        runCurrent()

        val rendered = viewModel.uiState.value.toString()

        assertFalse(rendered.contains("id-token-secreto"))
        assertFalse(rendered.contains("nonce-secreto"))
        assertFalse(rendered.contains("CODE-0001"))
    }

    @Test
    fun google_conSesionYaIniciadaMientrasEspera_noNavegaHastaTerminar() = runTest {
        google.gate = CompletableDeferred()
        viewModel.signInWithGoogle(host)
        runCurrent()

        auth.state.value = SessionState.SignedIn(ana, offline = false)
        runCurrent()

        assertFalse(viewModel.uiState.value.signedIn)
    }
}
