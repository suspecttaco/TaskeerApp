package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.search.SearchIndex
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    fun observeIndex(): Flow<SearchIndex>
}
