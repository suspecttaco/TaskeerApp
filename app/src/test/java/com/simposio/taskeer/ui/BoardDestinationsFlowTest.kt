package com.simposio.taskeer.ui

import com.simposio.taskeer.testing.FakeDestinationBoardRepository
import com.simposio.taskeer.testing.FakeDestinationColumnRepository
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.testBoard
import com.simposio.taskeer.testing.testColumn
import com.simposio.taskeer.ui.tasks.observeBoardDestinations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardDestinationsFlowTest {

    private val boards = FakeDestinationBoardRepository()
    private val columns = FakeDestinationColumnRepository()
    private val tasks = FakeDestinationTaskRepository()

    @Test
    fun combinaTablerosColumnasYConteos() = runTest {
        boards.boards.value = listOf(testBoard("b1"))
        columns.columns.value = listOf(testColumn("c1", "b1", wipLimit = 2))
        tasks.counts.value = mapOf("c1" to 2)

        val destination = observeBoardDestinations(boards, columns, tasks, includeDoneColumns = true).first().single()

        assertEquals("b1", destination.board.id)
        assertEquals(2, destination.columns.single().activeCount)
        assertTrue(destination.columns.single().isFull)
    }

    @Test
    fun sinTableros_devuelveListaVacia() = runTest {
        assertTrue(observeBoardDestinations(boards, columns, tasks, includeDoneColumns = false).first().isEmpty())
    }

    @Test
    fun reaccionaAlCambioDeConteo() = runTest {
        boards.boards.value = listOf(testBoard("b1"))
        columns.columns.value = listOf(testColumn("c1", "b1", wipLimit = 1))
        val emissions = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeBoardDestinations(boards, columns, tasks, includeDoneColumns = true)
                .collect { emissions += it.single().columns.single().isFull }
        }

        tasks.counts.value = mapOf("c1" to 1)

        assertEquals(listOf(false, true), emissions)
    }
}
