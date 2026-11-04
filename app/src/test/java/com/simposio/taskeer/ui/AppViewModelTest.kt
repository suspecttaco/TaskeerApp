package com.simposio.taskeer.ui

import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.ui.app.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AppViewModel(FakePreferencesRepository())

    @Test
    fun openViewerRequested_alInicio_esFalso() {
        assertFalse(viewModel().openViewerRequested.value)
    }

    @Test
    fun requestOpenViewer_seConsumeUnaVez() {
        val viewModel = viewModel()

        viewModel.requestOpenViewer()
        assertTrue(viewModel.openViewerRequested.value)

        viewModel.consumeOpenViewer()
        assertFalse(viewModel.openViewerRequested.value)
    }

    @Test
    fun requestOpenViewer_sobreviveHastaQueLaUiLaAtiende() {
        val viewModel = viewModel()

        viewModel.requestOpenViewer()
        viewModel.requestOpenViewer()

        assertTrue(viewModel.openViewerRequested.value)
        viewModel.consumeOpenViewer()
        assertFalse(viewModel.openViewerRequested.value)
    }
}
