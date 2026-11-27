package com.simposio.taskeer.domain.workspace

object BoardRoleRules {
    const val SIGNAL_REFRESH_PAUSE_MILLIS: Long = 1000L

    private val VIEWING = setOf(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.EDITOR, WorkspaceRole.VIEWER)
    private val EDITING = setOf(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.EDITOR)
    private val MANAGING = setOf(WorkspaceRole.OWNER, WorkspaceRole.ADMIN)

    // Un valor que la app no conoce no concede nada (como UNKNOWN en el resto de reglas).
    fun parseRole(raw: String): WorkspaceRole =
        WorkspaceRole.entries.firstOrNull { it.name == raw && it != WorkspaceRole.UNKNOWN } ?: WorkspaceRole.UNKNOWN

    fun sortedBoards(items: List<ServerBoard>): List<ServerBoard> =
        items.sortedWith(compareBy<ServerBoard, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })

    fun canView(role: WorkspaceRole?): Boolean = role in VIEWING

    fun canEditTasks(role: WorkspaceRole?): Boolean = role in EDITING

    fun canManageColumns(role: WorkspaceRole?): Boolean = role in MANAGING

    fun capabilities(role: WorkspaceRole?): List<BoardCapability> = buildList {
        if (canView(role)) add(BoardCapability.VIEW)
        if (canEditTasks(role)) add(BoardCapability.EDIT_TASKS)
        if (canManageColumns(role)) add(BoardCapability.MANAGE_COLUMNS)
    }

    // Gestionar overrides depende del rol crudo del workspace: con el efectivo, un override podría autoescalarse.
    fun canManageOverrides(workspaceRole: WorkspaceRole): Boolean = workspaceRole in MANAGING

    fun canSetOverride(workspaceRole: WorkspaceRole, target: WorkspaceMember, self: WorkspaceMember?): Boolean =
        canManageOverrides(workspaceRole) &&
            target.role != WorkspaceRole.OWNER &&
            target.role != WorkspaceRole.UNKNOWN &&
            !isSelf(target, self)

    fun canRemoveOverride(
        workspaceRole: WorkspaceRole,
        target: WorkspaceMember,
        self: WorkspaceMember?,
        override: BoardMemberOverride?,
    ): Boolean = canManageOverrides(workspaceRole) && override != null && !isSelf(target, self)

    fun assignableBoardRoles(currentBoardRole: WorkspaceRole): List<WorkspaceRole> =
        MemberRules.ASSIGNABLE.filter { it != currentBoardRole }

    fun rows(
        members: List<WorkspaceMember>,
        overrides: List<BoardMemberOverride>,
        self: WorkspaceMember?,
    ): List<OverrideRow> {
        val byUser = overrides.associateBy { it.userId }
        return MemberRules.sorted(members.filter { it.role != WorkspaceRole.OWNER && !isSelf(it, self) })
            .map { OverrideRow(it, byUser[it.userId]) }
    }

    // Un refresco que empezó antes de una mutación confirmada trae datos previos a ella.
    fun reapply(fresh: List<BoardMemberOverride>, confirmed: BoardOverrideConfirmedOps): List<BoardMemberOverride> {
        val kept = fresh
            .filter { it.userId !in confirmed.removedUserIds && it.userId !in confirmed.upserted }
        return (kept + confirmed.upserted.values).sortedBy { it.userId }
    }

    fun roleChangeNotice(previous: WorkspaceRole?, current: WorkspaceRole?): WorkspaceRole? =
        if (previous != null && current != null && previous != current) current else null

    private fun isSelf(target: WorkspaceMember, self: WorkspaceMember?): Boolean = self?.userId == target.userId
}
