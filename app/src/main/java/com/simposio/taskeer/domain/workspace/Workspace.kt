package com.simposio.taskeer.domain.workspace

import java.time.Instant

enum class WorkspaceRole { OWNER, ADMIN, EDITOR, VIEWER, UNKNOWN }

data class Workspace(
    val id: String,
    val name: String,
    val slug: String,
    val ownerId: String,
    val myRole: WorkspaceRole,
    val isPersonal: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    // El nombre es un dato de usuario (y de otros miembros): no debe acabar en logs ni mensajes.
    override fun toString(): String = "Workspace(id=$id, myRole=$myRole, isPersonal=$isPersonal)"
}
