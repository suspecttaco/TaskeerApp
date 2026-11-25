package com.simposio.taskeer.ui.search

import com.simposio.taskeer.domain.search.SearchResults

enum class SearchStatus { Idle, NoResults, Results }

data class SearchUiState(
    val query: String = "",
    val status: SearchStatus = SearchStatus.Idle,
    val results: SearchResults = SearchResults.Empty,
)
