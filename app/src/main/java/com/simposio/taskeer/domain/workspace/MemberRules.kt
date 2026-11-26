package com.simposio.taskeer.domain.workspace

data class MemberConfirmedOps(
    val removedIds: Set<String> = emptySet(),
    val roles: Map<String, WorkspaceRole> = emptyMap(),
)

object MemberRules {
    val ASSIGNABLE: List<WorkspaceRole> = listOf(WorkspaceRole.ADMIN, WorkspaceRole.EDITOR, WorkspaceRole.VIEWER)

    private val ROLE_ORDER = listOf(
        WorkspaceRole.OWNER,
        WorkspaceRole.ADMIN,
        WorkspaceRole.EDITOR,
        WorkspaceRole.VIEWER,
        WorkspaceRole.UNKNOWN,
    )

    fun sorted(items: List<WorkspaceMember>): List<WorkspaceMember> =
        items.sortedWith(
            compareBy<WorkspaceMember> { ROLE_ORDER.indexOf(it.role) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                .thenBy { it.userId },
        )

    // AccountSummary no trae userId: el correo es la única forma de reconocer la propia entrada.
    fun selfOf(items: List<WorkspaceMember>, selfEmail: String?): WorkspaceMember? {
        val email = selfEmail?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return items.firstOrNull { it.email.trim().equals(email, ignoreCase = true) }
    }

    fun effectiveRole(workspace: Workspace, items: List<WorkspaceMember>, selfEmail: String?): WorkspaceRole =
        selfOf(items, selfEmail)?.role ?: workspace.myRole

    fun canManageMembers(actor: WorkspaceRole): Boolean = actor == WorkspaceRole.OWNER || actor == WorkspaceRole.ADMIN

    fun canChangeRole(actor: WorkspaceRole, target: WorkspaceMember, self: WorkspaceMember?): Boolean =
        canManageMembers(actor) && target.role in ASSIGNABLE && !isSelf(target, self)

    fun assignableRoles(target: WorkspaceMember): List<WorkspaceRole> = ASSIGNABLE.filter { it != target.role }

    fun canRemove(actor: WorkspaceRole, target: WorkspaceMember, self: WorkspaceMember?): Boolean =
        canManageMembers(actor) && target.role in ASSIGNABLE && !isSelf(target, self)

    fun canLeave(actor: WorkspaceRole, isPersonal: Boolean, self: WorkspaceMember?): Boolean =
        self != null && actor in ASSIGNABLE && !isPersonal

    fun canTransferOwnership(
        actor: WorkspaceRole,
        target: WorkspaceMember,
        self: WorkspaceMember?,
        isPersonal: Boolean,
    ): Boolean = actor == WorkspaceRole.OWNER && target.role in ASSIGNABLE && !isSelf(target, self) && !isPersonal

    // Un refresco que empezó antes de una mutación confirmada trae datos previos a ella.
    fun reapply(fresh: List<WorkspaceMember>, confirmed: MemberConfirmedOps): List<WorkspaceMember> =
        sorted(
            fresh
                .filter { it.userId !in confirmed.removedIds }
                .map { member -> confirmed.roles[member.userId]?.let { member.copy(role = it) } ?: member },
        )

    private fun isSelf(target: WorkspaceMember, self: WorkspaceMember?): Boolean = self?.userId == target.userId
}
