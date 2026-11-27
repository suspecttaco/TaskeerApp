package com.simposio.taskeer.domain.workspace

import java.time.Instant

data class BoardMemberOverride(
    val id: String,
    val userId: String,
    val role: WorkspaceRole,
    val updatedAt: Instant,
) {
    override fun toString(): String = "BoardMemberOverride(userId redactado, role=$role)"
}

data class BoardAccessState(
    val workspaceId: String? = null,
    val boardId: String? = null,
    val boardName: String? = null,
    val effectiveRole: WorkspaceRole? = null,
    val overrides: List<BoardMemberOverride> = emptyList(),
    val overridesLoaded: Boolean = false,
) {
    override fun toString(): String = "BoardAccessState(effectiveRole=$effectiveRole, overridesLoaded=$overridesLoaded)"
}

data class BoardOverrideConfirmedOps(
    val upserted: Map<String, BoardMemberOverride> = emptyMap(),
    val removedUserIds: Set<String> = emptySet(),
)

enum class BoardCapability { VIEW, EDIT_TASKS, MANAGE_COLUMNS }

data class OverrideRow(val member: WorkspaceMember, val override: BoardMemberOverride?) {
    val boardRole: WorkspaceRole get() = override?.role ?: member.role

    override fun toString(): String = "OverrideRow(role=${member.role}, override=${override?.role})"
}
