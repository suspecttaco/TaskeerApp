package com.simposio.taskeer.ui.register

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.domain.auth.RegisterOutcome
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
class RegisterViewModelTest {

    private val auth = FakeAuthRepository()
    private var savedState = SavedStateHandle()
    private lateinit var viewModel: RegisterViewModel

    private val codes = RecoveryCodes(List(10) { "ABCD-000$it" })
    private val registered = RegisterOutcome.Registered(AccountSummary("ana@example.com", "Ana"), codes)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        auth.registerResult = registered
        viewModel = RegisterViewModel(auth, savedState)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun secret(value: String = "secreta1") = value.toCharArray()

    private fun submit(
        name: String = "Ana",
        email: String = "ana@example.com",
        password: CharArray = secret(),
        confirmation: CharArray = secret(),
    ) = viewModel.submit(name, email, password, confirmation)

    private fun failWith(error: DomainError, retryAfter: Long? = null) {
        auth.registerResult = RegisterOutcome.Failed(error, retryAfter)
    }

    @Test
    fun submit_exitoso_llamaAlRepositorioConLosDatos() = runTest {
        submit(name = "Ana", email = "ana@example.com", password = secret("clave-123"), confirmation = secret("clave-123"))
        runCurrent()

        assertEquals(listOf("Ana"), auth.registerNames)
        assertEquals(listOf("ana@example.com"), auth.registerEmails)
        assertEquals("clave-123", auth.registerPasswordAtCall)
    }

    @Test
    fun submit_enVuelo_seIgnora() = runTest {
        auth.registerGate = CompletableDeferred()

        submit()
        runCurrent()
        submit()
        runCurrent()

        assertEquals(1, auth.registerCalls)
        assertTrue(state.isSubmitting)
        auth.registerGate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun submit_fueraDeLaFaseForm_seIgnora() = runTest {
        submit()
        runCurrent()
        submit()
        runCurrent()

        assertEquals(1, auth.registerCalls)
    }

    @Test
    fun submit_bloqueadoPor429_seIgnora() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 60)
        submit()
        runCurrent()
        submit()
        runCurrent()

        assertEquals(1, auth.registerCalls)
        assertEquals(RegisterBlock.RATE_LIMITED, state.blocked)
    }

    @Test
    fun submit_nombreInvalido_noLlamaAlRepositorio() = runTest {
        submit(name = "A")
        runCurrent()

        assertEquals(0, auth.registerCalls)
        assertEquals(RegisterError.NAME_INVALID, state.error)
    }

    @Test
    fun submit_passwordsDistintas_esPasswordMismatchYNoLlama() = runTest {
        submit(password = secret("secreta1"), confirmation = secret("secreta2"))
        runCurrent()

        assertEquals(0, auth.registerCalls)
        assertEquals(RegisterError.PASSWORD_MISMATCH, state.error)
    }

    @Test
    fun submit_conErrorRecuperable_conservaNombreYCorreo() = runTest {
        failWith(DomainError.Network("NETWORK_TIMEOUT", "x"))
        submit(name = "Ana Pérez", email = "ana@example.com")
        runCurrent()

        assertEquals("Ana Pérez", state.displayName)
        assertEquals("ana@example.com", state.email)
        assertEquals(RegisterError.NETWORK, state.error)
        assertEquals(RegisterPhase.FORM, state.phase)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun submit_con429_bloqueaYDesbloqueaConTiempoVirtual() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 90)
        submit()
        runCurrent()

        assertEquals(RegisterBlock.RATE_LIMITED, state.blocked)
        assertEquals(2, state.retryMinutes)
        assertFalse(state.canSubmit)
        advanceTimeBy(89_000)
        assertEquals(RegisterBlock.RATE_LIMITED, state.blocked)
        advanceTimeBy(1_001)
        assertNull(state.blocked)
    }

    @Test
    fun submit_mobileClientNotSupported_bloqueaConPaginaDeEstado() = runTest {
        failWith(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x"))
        submit()
        runCurrent()

        assertEquals(RegisterBlock.NOT_SUPPORTED, state.blocked)
        assertNull(state.error)
    }

    @Test
    fun volverDeNoCompatible_regresaAlFormulario() = runTest {
        failWith(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x"))
        submit(name = "Ana", email = "ana@example.com")
        runCurrent()

        viewModel.dismissNotSupported()

        assertNull(state.blocked)
        assertEquals("ana@example.com", state.email)
    }

    @Test
    fun submit_conEmailTaken_muestraElMensajeNeutro() = runTest {
        failWith(DomainError.Conflict("EMAIL_ALREADY_REGISTERED", "x"))
        submit()
        runCurrent()

        assertEquals(RegisterError.EMAIL_TAKEN, state.error)
    }

    @Test
    fun vaciaLasDosPasswordsTrasCadaCamino() = runTest {
        val outcomes = listOf<RegisterOutcome>(
            registered,
            RegisterOutcome.RegisteredWithoutCodes("ana@example.com"),
            RegisterOutcome.Failed(DomainError.Conflict("EMAIL_ALREADY_REGISTERED", "x")),
            RegisterOutcome.Failed(DomainError.Network("N", "x")),
            RegisterOutcome.Failed(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x")),
        )
        outcomes.forEach { outcome ->
            val vm = RegisterViewModel(FakeAuthRepository().also { it.registerResult = outcome }, SavedStateHandle())
            val password = secret()
            val confirmation = secret()
            vm.submit("Ana", "ana@example.com", password, confirmation)
            runCurrent()
            assertTrue(password.all { it == '\u0000' })
            assertTrue(confirmation.all { it == '\u0000' })
        }
        val invalidPassword = secret("corta")
        val invalidConfirmation = secret("corta")
        viewModel.submit("Ana", "ana@example.com", invalidPassword, invalidConfirmation)
        val mismatchPassword = secret("secreta1")
        val mismatchConfirmation = secret("secreta2")
        viewModel.submit("Ana", "ana@example.com", mismatchPassword, mismatchConfirmation)
        runCurrent()

        assertTrue(invalidPassword.all { it == '\u0000' } && invalidConfirmation.all { it == '\u0000' })
        assertTrue(mismatchPassword.all { it == '\u0000' } && mismatchConfirmation.all { it == '\u0000' })
    }

    @Test
    fun passwordNoApareceEnElEstado() = runTest {
        submit(password = secret("clave-secreta-9"), confirmation = secret("clave-secreta-9"))
        runCurrent()

        assertFalse(state.toString().contains("clave-secreta-9"))
    }

    @Test
    fun uiState_noTieneCampoDeCodigos() {
        val fields = RegisterUiState::class.java.declaredFields.map { it.name to it.type }

        assertTrue(fields.none { (_, type) -> type == RecoveryCodes::class.java || type == List::class.java })
    }

    @Test
    fun onPasswordEdited_limpiaElError() = runTest {
        failWith(DomainError.Network("N", "x"))
        submit()
        runCurrent()

        viewModel.onPasswordEdited()

        assertNull(state.error)
    }

    @Test
    fun registroExitoso_pasaAFaseCodesConLosCodigos() = runTest {
        submit()
        runCurrent()

        assertEquals(RegisterPhase.CODES, state.phase)
        assertEquals(codes.values, viewModel.recoveryCodes.value?.values)
    }

    @Test
    fun registroExitoso_pideVaciarLosCampos() = runTest {
        submit()
        runCurrent()

        assertEquals(1, state.passwordResetCount)
    }

    @Test
    fun registroSinCodigos_pasaAFaseNoCodes() = runTest {
        auth.registerResult = RegisterOutcome.RegisteredWithoutCodes("ana@example.com")
        submit()
        runCurrent()

        assertEquals(RegisterPhase.NO_CODES, state.phase)
        assertNull(viewModel.recoveryCodes.value)
    }

    @Test
    fun sinCodigosTrasConfirmar() = runTest {
        submit()
        runCurrent()
        viewModel.onSavedCheckedChange(true)

        viewModel.confirmCodesSaved()

        assertNull(viewModel.recoveryCodes.value)
    }

    @Test
    fun sinCodigosTrasSalidaExplicita() = runTest {
        submit()
        runCurrent()

        viewModel.leaveWithoutSaving()

        assertNull(viewModel.recoveryCodes.value)
    }

    @Test
    fun onCleared_descartaLosCodigos() = runTest {
        val store = ViewModelStore()
        val owned = ViewModelProvider.create(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            },
        )[RegisterViewModel::class.java]
        owned.submit("Ana", "ana@example.com", secret(), secret())
        runCurrent()
        assertEquals(10, owned.recoveryCodes.value?.count)

        store.clear()

        assertNull(owned.recoveryCodes.value)
    }

    @Test
    fun sinCodigosNoHayForma_deVolverAVerlos() = runTest {
        submit()
        runCurrent()
        viewModel.leaveWithoutSaving()

        viewModel.onCodesCopied()
        viewModel.onSavedCheckedChange(true)
        viewModel.confirmCodesSaved()

        assertNull(viewModel.recoveryCodes.value)
        assertEquals(RegisterPhase.FINISHED, state.phase)
        assertFalse(state.copied)
    }

    @Test
    fun continuar_sinCasilla_noHaceNada() = runTest {
        submit()
        runCurrent()

        viewModel.confirmCodesSaved()

        assertEquals(RegisterPhase.CODES, state.phase)
        assertEquals(10, viewModel.recoveryCodes.value?.count)
    }

    @Test
    fun continuar_conCasilla_descartaLosCodigosYFinaliza() = runTest {
        submit()
        runCurrent()
        viewModel.onSavedCheckedChange(true)

        viewModel.confirmCodesSaved()

        assertEquals(RegisterPhase.FINISHED, state.phase)
        assertNull(viewModel.recoveryCodes.value)
    }

    @Test
    fun salidaExplicita_descartaLosCodigosYFinaliza() = runTest {
        submit()
        runCurrent()

        viewModel.leaveWithoutSaving()

        assertEquals(RegisterPhase.FINISHED, state.phase)
        assertNull(viewModel.recoveryCodes.value)
    }

    @Test
    fun salidaExplicita_fueraDeCodes_noHaceNada() = runTest {
        viewModel.leaveWithoutSaving()

        assertEquals(RegisterPhase.FORM, state.phase)
    }

    @Test
    fun onCodesCopied_marcaCopiado() = runTest {
        submit()
        runCurrent()

        viewModel.onCodesCopied()

        assertTrue(state.copied)
    }

    @Test
    fun finalizar_exponeElCorreoParaElLogin() = runTest {
        submit(email = "  ana@example.com ")
        runCurrent()
        viewModel.onSavedCheckedChange(true)
        viewModel.confirmCodesSaved()

        assertEquals("ana@example.com", state.finishedEmail)
    }

    @Test
    fun codesPending_seGuardaSoloAlMostrarCodigos() = runTest {
        assertNull(savedState.get<Boolean>(RegisterViewModel.KEY_CODES_PENDING))

        submit()
        runCurrent()

        assertEquals(true, savedState.get<Boolean>(RegisterViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun codesPending_noGuardaMasQueUnBooleano() = runTest {
        submit()
        runCurrent()
        viewModel.onCodesCopied()
        viewModel.onSavedCheckedChange(true)

        assertEquals(setOf("codes_pending"), savedState.keys())
        assertTrue(savedState.get<Any>("codes_pending") is Boolean)
    }

    @Test
    fun codesPending_seLimpiaAlConfirmar() = runTest {
        submit()
        runCurrent()
        viewModel.onSavedCheckedChange(true)

        viewModel.confirmCodesSaved()

        assertFalse(savedState.contains(RegisterViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun codesPending_seLimpiaAlSalirExplicitamente() = runTest {
        submit()
        runCurrent()

        viewModel.leaveWithoutSaving()

        assertFalse(savedState.contains(RegisterViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun conCodesPending_yViewModelNuevo_pasaACodesLost() {
        val restored = RegisterViewModel(auth, SavedStateHandle(mapOf(RegisterViewModel.KEY_CODES_PENDING to true)))

        assertEquals(RegisterPhase.CODES_LOST, restored.uiState.value.phase)
        assertNull(restored.recoveryCodes.value)
    }

    @Test
    fun codesLost_acusar_finalizaSinCorreo() {
        val handle = SavedStateHandle(mapOf(RegisterViewModel.KEY_CODES_PENDING to true))
        val restored = RegisterViewModel(auth, handle)

        restored.acknowledgeLostOrMissingCodes()

        assertEquals(RegisterPhase.FINISHED, restored.uiState.value.phase)
        assertNull(restored.uiState.value.finishedEmail)
        assertFalse(handle.contains(RegisterViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun rotacion_conservaCodigosYCasilla() = runTest {
        submit()
        runCurrent()
        viewModel.onSavedCheckedChange(true)

        assertEquals(10, viewModel.recoveryCodes.value?.count)
        assertTrue(state.savedChecked)
        assertEquals(RegisterPhase.CODES, state.phase)
    }

    @Test
    fun sinCodesPending_arrancaEnForm() {
        assertEquals(RegisterPhase.FORM, state.phase)
    }
}
