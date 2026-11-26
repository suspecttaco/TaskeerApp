package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.testing.FakeAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val auth = FakeAuthRepository()
    private lateinit var viewModel: SessionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SessionViewModel(auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_reexponeElDelRepositorio() {
        assertSame(auth.sessionState, viewModel.state)
        auth.state.value = SessionState.Resuming
        assertEquals(SessionState.Resuming, viewModel.state.value)
    }

    @Test
    fun logout_llamaAlRepositorio() {
        viewModel.logout()

        assertEquals(1, auth.logoutCalls)
    }

    @Test
    fun logout_conServidorConfirmado_emiteLoggedOut() = runTest {
        val messages = mutableListOf<SessionMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.collect { messages += it } }

        viewModel.logout()

        assertEquals(listOf(SessionMessage.LOGGED_OUT), messages)
    }

    @Test
    fun logout_sinConfirmarServidor_emiteLoggedOutServerUnreachable() = runTest {
        auth.logoutResult = LogoutOutcome(serverRevoked = false)
        val messages = mutableListOf<SessionMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.collect { messages += it } }

        viewModel.logout()

        assertEquals(listOf(SessionMessage.LOGGED_OUT_SERVER_UNREACHABLE), messages)
    }

    @Test
    fun retryResume_llamaAResume() {
        viewModel.retryResume()

        assertEquals(1, auth.resumeCalls)
    }

    @Test
    fun acknowledgeExpiration_delegaEnElRepositorio() {
        viewModel.acknowledgeExpiration()

        assertEquals(1, auth.acknowledgeCalls)
    }

    @Test
    fun logout_dobleToque_seIgnora() {
        val gate = CompletableDeferred<Unit>().also { auth.logoutGate = it }

        viewModel.logout()
        viewModel.logout()
        gate.complete(Unit)

        assertEquals(1, auth.logoutCalls)
    }
}
