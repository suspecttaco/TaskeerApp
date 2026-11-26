package com.simposio.taskeer.data.auth

import android.content.Context
import com.simposio.taskeer.data.remote.auth.RemoteAuthRepository
import com.simposio.taskeer.data.remote.createApiClient
import com.simposio.taskeer.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope

// Único puente hacia data.remote: di/ compone esta función sin conocer el cliente HTTP.
fun createAuthRepository(context: Context, scope: CoroutineScope): AuthRepository {
    val remote = createApiClient(context)
    return RemoteAuthRepository(remote.client, remote.session, scope)
}
