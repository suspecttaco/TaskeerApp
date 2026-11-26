package com.simposio.taskeer.ui.accountsecurity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.simposio.taskeer.domain.auth.RegenerateCodesOutcome
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
class RegenerateCodesViewModelTest {

    private val auth = FakeAuthRepository()
    private val savedState = SavedStateHandle()
    private lateinit var viewModel: RegenerateCodesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = RegenerateCodesViewModel(auth, savedState)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun submit(password: CharArray = "vieja-clave".toCharArray()) = viewModel.submit(password)

    private fun failWith(error: DomainError, retryAfter: Long? = null) {
        auth.regenerateResult = RegenerateCodesOutcome.Failed(error, retryAfter)
    }

    @Test
    fun submit_exitoso_pasaACodesConLosCodigos() = runTest {
        submit()
        runCurrent()

        assertEquals(RegenerateCodesPhase.CODES, state.phase)
        assertEquals(10, viewModel.recoveryCodes.value?.count)
        assertEquals(true, savedState.get<Boolean>(RegenerateCodesViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun submit_sinContrasena_noLlama() = runTest {
        submit(charArrayOf())
        runCurrent()

        assertEquals(0, auth.regenerateCalls)
        assertEquals(RegenerateCodesError.CURRENT_INVALID, state.error)
    }

    @Test
    fun submit_enVuelo_seIgnora() = runTest {
        auth.regenerateGate = CompletableDeferred()

        submit()
        runCurrent()
        submit()
        runCurrent()

        assertTrue(state.isSubmitting)
        assertEquals(1, auth.regenerateCalls)
        auth.regenerateGate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun submit_con429_bloqueaYDesbloquea() = runTest {
        failWith(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 120L)
        submit()
        runCurrent()
        assertTrue(state.blocked)
        assertEquals(2, state.retryMinutes)

        submit()
        runCurrent()
        assertEquals(1, auth.regenerateCalls)
        advanceTimeBy(121_000)
        runCurrent()

        assertFalse(state.blocked)
    }

    @Test
    fun submit_sinCodigos_pasaANoCodes() = runTest {
        auth.regenerateResult = RegenerateCodesOutcome.RegeneratedWithoutCodes

        submit()
        runCurrent()

        assertEquals(RegenerateCodesPhase.NO_CODES, state.phase)
        assertNull(viewModel.recoveryCodes.value)
        assertNull(savedState.get<Boolean>(RegenerateCodesViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun submit_conInvalidCredentials_noPasaACodes() = runTest {
        failWith(DomainError.Unauthenticated("INVALID_CREDENTIALS", "x"))

        submit()
        runCurrent()

        assertEquals(RegenerateCodesError.CURRENT_INCORRECT, state.error)
        assertEquals(RegenerateCodesPhase.PASSWORD, state.phase)
        assertNull(viewModel.recoveryCodes.value)
    }

    @Test
    fun confirmCodesSaved_descartaLosCodigosYFinaliza() = runTest {
        submit()
        runCurrent()
        viewModel.onSavedCheckedChange(true)

        viewModel.confirmCodesSaved()

        assertEquals(RegenerateCodesPhase.FINISHED, state.phase)
        assertNull(viewModel.recoveryCodes.value)
        assertNull(savedState.get<Boolean>(RegenerateCodesViewModel.KEY_CODES_PENDING))
    }

    @Test
    fun confirmCodesSaved_sinCasilla_noHaceNada() = runTest {
        submit()
        runCurrent()

        viewModel.confirmCodesSaved()

        assertEquals(RegenerateCodesPhase.CODES, state.phase)
    }

    @Test
    fun leaveWithoutSaving_descarta() = runTest {
        submit()
        runCurrent()

        viewModel.leaveWithoutSaving()

        assertEquals(RegenerateCodesPhase.FINISHED, state.phase)
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
        )[RegenerateCodesViewModel::class.java]
        owned.submit("vieja-clave".toCharArray())
        runCurrent()
        assertEquals(10, owned.recoveryCodes.value?.count)

        store.clear()

        assertNull(owned.recoveryCodes.value)
    }

    @Test
    fun procesoRecreadoConCodesPending_muestraCodesLost() {
        val restored = RegenerateCodesViewModel(
            auth,
            SavedStateHandle(mapOf(RegenerateCodesViewModel.KEY_CODES_PENDING to true)),
        )

        assertEquals(RegenerateCodesPhase.CODES_LOST, restored.uiState.value.phase)
        assertNull(restored.recoveryCodes.value)
        restored.acknowledgeLostOrMissingCodes()
        assertEquals(RegenerateCodesPhase.FINISHED, restored.uiState.value.phase)
    }

    @Test
    fun savedStateGuardaSoloElBooleano() = runTest {
        submit()
        runCurrent()
        viewModel.onCodesCopied()
        viewModel.onSavedCheckedChange(true)

        assertEquals(setOf("codes_pending"), savedState.keys())
        assertTrue(savedState.get<Any>("codes_pending") is Boolean)
    }

    @Test
    fun onCodesCopied_marcaCopiado() = runTest {
        submit()
        runCurrent()

        viewModel.onCodesCopied()

        assertTrue(state.copied)
    }

    @Test
    fun submit_rellenaDeCerosLaContrasena() = runTest {
        val password = "vieja-clave".toCharArray()

        submit(password)
        runCurrent()

        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun uiState_noTieneCamposSecretos() {
        val offenders = RegenerateCodesUiState::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .filter { it.contains("password", ignoreCase = true) || it.contains("code", ignoreCase = true) }

        assertTrue("campos secretos: $offenders", offenders.isEmpty())
    }
}
