package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.sync.CycleOutcome
import com.simposio.taskeer.domain.sync.SyncNotice
import com.simposio.taskeer.domain.sync.SyncSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

interface SyncRepository {
    val status: Flow<SyncSnapshot>

    // Resumen de lo que se perdió en un ciclo; solo cantidades.
    val notices: Flow<SyncNotice>

    // Resultado de cada ciclo pedido a mano; los automáticos no pasan por aquí.
    val manualOutcomes: Flow<CycleOutcome>

    // Verdadero mientras la app espera que el usuario decida qué hacer con una cuenta distinta a la vinculada.
    val accountSwitchPrompt: StateFlow<Boolean>

    fun requestSync()

    fun notifyForeground()

    suspend fun confirmAccountWipe()

    fun cancelAccountSwitch()
}

object UnavailableSyncRepository : SyncRepository {
    override val status: Flow<SyncSnapshot> = flowOf(SyncSnapshot(signedIn = false))
    override val notices: Flow<SyncNotice> = emptyFlow()
    override val manualOutcomes: Flow<CycleOutcome> = emptyFlow()
    override val accountSwitchPrompt: StateFlow<Boolean> = MutableStateFlow(false)

    override fun requestSync() = Unit

    override fun notifyForeground() = Unit

    override suspend fun confirmAccountWipe() = Unit

    override fun cancelAccountSwitch() = Unit
}
