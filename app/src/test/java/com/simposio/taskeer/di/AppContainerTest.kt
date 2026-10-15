package com.simposio.taskeer.di

import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ThemeMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {

    private val auckland = ZoneId.of("Pacific/Auckland")

    @Test
    fun entregaLaImplementacionInyectada() {
        val repository = FakePreferencesRepository()

        val container = container(preferencesRepository = repository)

        assertSame(repository, container.preferencesRepository)
        assertEquals(auckland, container.timeZoneProvider.current())
    }

    @Test
    fun entregaElRepositorioDeTablerosInyectado() {
        val boards = FakeBoardRepository()

        val container = container(boardRepository = boards)

        assertSame(boards, container.boardRepository)
    }

    @Test
    fun entregaElRepositorioDeColumnasInyectado() {
        val columns = FakeColumnRepository()

        val container = container(columnRepository = columns)

        assertSame(columns, container.columnRepository)
    }

    @Test
    fun conversorUsaLaZonaEntregada() {
        val container = container()

        val local = container.dateTimeConverter.toLocalDateTime(Instant.parse("2026-10-12T23:30:00Z"))

        assertEquals(LocalDateTime.of(2026, 10, 13, 12, 30), local)
    }

    private fun container(
        preferencesRepository: PreferencesRepository = FakePreferencesRepository(),
        boardRepository: BoardRepository = FakeBoardRepository(),
        columnRepository: ColumnRepository = FakeColumnRepository(),
    ): AppContainer = AppContainer(
        preferencesRepository = preferencesRepository,
        timeZoneProvider = TimeZoneProvider { auckland },
        boardRepository = boardRepository,
        columnRepository = columnRepository,
    )

    private class FakePreferencesRepository : PreferencesRepository {
        private val mode = MutableStateFlow(ThemeMode.AUTO)
        private val dynamic = MutableStateFlow(true)

        override val themeMode: Flow<ThemeMode> = mode
        override val dynamicColor: Flow<Boolean> = dynamic

        override suspend fun setThemeMode(mode: ThemeMode) {
            this.mode.value = mode
        }

        override suspend fun setDynamicColor(enabled: Boolean) {
            dynamic.value = enabled
        }
    }

    private class FakeBoardRepository : BoardRepository {
        override fun observeBoards(): Flow<List<Board>> = MutableStateFlow(emptyList())

        override suspend fun createBoard(name: String): DomainResult<Board> = unused()

        override suspend fun renameBoard(id: String, name: String): DomainResult<Board> = unused()

        override suspend fun deleteBoard(id: String): DomainResult<Unit> = unused()

        private fun unused(): DomainResult.Failure =
            DomainResult.Failure(DomainError.Unknown("UNUSED", "doble de test"))
    }

    private class FakeColumnRepository : ColumnRepository {
        override fun observeColumns(boardId: String): Flow<List<Column>> = MutableStateFlow(emptyList())

        override suspend fun createColumn(boardId: String, name: String): DomainResult<Column> = unused()

        override suspend fun renameColumn(id: String, name: String): DomainResult<Column> = unused()

        override suspend fun moveColumn(id: String, toIndex: Int): DomainResult<Column> = unused()

        override suspend fun deleteColumn(id: String): DomainResult<Unit> = unused()

        private fun unused(): DomainResult.Failure =
            DomainResult.Failure(DomainError.Unknown("UNUSED", "doble de test"))
    }
}
