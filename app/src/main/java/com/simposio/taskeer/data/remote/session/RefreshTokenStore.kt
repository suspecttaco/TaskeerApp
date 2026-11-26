package com.simposio.taskeer.data.remote.session

interface RefreshTokenStore {
    suspend fun read(): String?

    suspend fun write(token: String)

    suspend fun clear()
}
