package com.simposio.taskeer.data.remote.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AuthSession(
    private val store: RefreshTokenStore,
    private val refresher: SessionRefresher,
) {
    private val mutex = Mutex()
    private val _expirations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var accessToken: String? = null

    // Solo si el Keystore no pudo guardar el par nuevo: el servidor ya revocó el anterior.
    @Volatile
    private var memoryRefreshToken: String? = null

    @Volatile
    private var generation = 0
    private var lastFailure: RenewOutcome.Failed? = null

    val expirations: SharedFlow<Unit> = _expirations.asSharedFlow()

    fun currentAccessToken(): String? = accessToken

    suspend fun start(accessToken: String, refreshToken: String) {
        mutex.withLock {
            lastFailure = null
            memoryRefreshToken = null
            persistOrKeepInMemory(refreshToken)
            this.accessToken = accessToken
        }
    }

    suspend fun hasStoredSession(): Boolean = memoryRefreshToken != null || store.read() != null

    suspend fun renew(rejectedAccessToken: String?): RenewOutcome {
        val enteredAt = generation
        return mutex.withLock {
            lastFailure?.takeIf { generation != enteredAt }?.let { return@withLock it }
            val current = accessToken
            if (current != null && current != rejectedAccessToken) {
                return@withLock RenewOutcome.Renewed(current)
            }
            val refreshToken = memoryRefreshToken ?: store.read()
            if (refreshToken == null) {
                accessToken = null
                return@withLock RenewOutcome.SessionExpired
            }
            val outcome = refresher.refresh(refreshToken)
            generation++
            when (outcome) {
                is RefreshOutcome.Renewed -> {
                    persistOrKeepInMemory(outcome.refreshToken)
                    accessToken = outcome.accessToken
                    lastFailure = null
                    RenewOutcome.Renewed(outcome.accessToken)
                }
                is RefreshOutcome.Rejected -> {
                    memoryRefreshToken = null
                    accessToken = null
                    lastFailure = null
                    clearStoreQuietly()
                    _expirations.emit(Unit)
                    RenewOutcome.SessionExpired
                }
                is RefreshOutcome.Unavailable -> {
                    val failed = RenewOutcome.Failed(outcome.error, outcome.retryAfterSeconds)
                    lastFailure = failed
                    failed
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            accessToken = null
            memoryRefreshToken = null
            lastFailure = null
            clearStoreQuietly()
        }
    }

    // Si el almacén no se pudo borrar, el token revocado queda en disco pero la sesión en memoria ya está
    // cerrada: el siguiente refresh lo rechazará el servidor y volverá a limpiar. Fallar aquí dejaría
    // viva una sesión que el servidor ya dio por terminada y ocultaría la expiración a la UI.
    private suspend fun clearStoreQuietly() {
        withContext(NonCancellable) {
            try {
                store.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun persistOrKeepInMemory(refreshToken: String) {
        val persisted = withContext(NonCancellable) {
            try {
                store.write(refreshToken)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
        if (persisted) {
            memoryRefreshToken = null
        } else {
            clearStoreQuietly()
            memoryRefreshToken = refreshToken
        }
    }
}
