package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.SearchRepository
import com.simposio.taskeer.domain.search.SearchIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class FakeSearchRepository(initial: SearchIndex = SearchIndex(emptyList(), emptyList(), emptyList(), emptyList())) :
    SearchRepository {
    val index = MutableStateFlow(initial)
    var subscriptions = 0
        private set

    var activeCollectors = 0
        private set

    override fun observeIndex(): Flow<SearchIndex> {
        subscriptions++
        return index.onStart { activeCollectors++ }.onCompletion { activeCollectors-- }
    }
}
