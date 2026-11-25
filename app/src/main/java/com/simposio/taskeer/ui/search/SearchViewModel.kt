package com.simposio.taskeer.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.SearchRepository
import com.simposio.taskeer.domain.search.SearchResults
import com.simposio.taskeer.domain.search.SearchRules
import com.simposio.taskeer.domain.search.search
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    repository: SearchRepository,
    private val savedStateHandle: SavedStateHandle,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val query: StateFlow<String> = savedStateHandle.getStateFlow(QUERY_KEY, "")

    private val computed: Flow<Computed> = query
        .debounce { if (it.isBlank()) 0L else SearchRules.DEBOUNCE_MILLIS }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isBlank()) {
                flowOf(Computed("", SearchResults.Empty))
            } else {
                repository.observeIndex().map { Computed(text, search(text, it)) }.flowOn(computeDispatcher)
            }
        }

    // Mientras el debounce no ha producido nada para la consulta nueva, se mantiene lo calculado antes;
    // por eso la primera pulsación sigue en Idle en lugar de parpadear «Sin resultados».
    val uiState: StateFlow<SearchUiState> = combine(query, computed) { text, computed ->
        when {
            text.isBlank() || computed.query.isBlank() -> SearchUiState(query = text)
            computed.results.isEmpty -> SearchUiState(text, SearchStatus.NoResults)
            else -> SearchUiState(text, SearchStatus.Results, computed.results)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState(query.value))

    private class Computed(val query: String, val results: SearchResults)

    fun onQueryChange(value: String) {
        savedStateHandle[QUERY_KEY] = value
    }

    fun clear() = onQueryChange("")

    companion object {
        const val QUERY_KEY = "query"
        private const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
