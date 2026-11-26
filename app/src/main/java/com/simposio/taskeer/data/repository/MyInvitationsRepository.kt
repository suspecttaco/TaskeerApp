package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.workspace.AcceptOutcome
import com.simposio.taskeer.domain.workspace.MyInvitationsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import kotlinx.coroutines.flow.StateFlow

interface MyInvitationsRepository {
    val state: StateFlow<MyInvitationsState>

    suspend fun refresh(): WorkspaceOutcome

    // La membresía la crea el servidor: aquí solo se quita la invitación de la bandeja.
    suspend fun accept(invitationId: String): AcceptOutcome

    // El token es un secreto del enlace del correo: viaja en la query y no se guarda.
    suspend fun acceptWithToken(token: String): AcceptOutcome
}
