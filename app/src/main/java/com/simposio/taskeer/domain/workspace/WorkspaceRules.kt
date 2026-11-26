package com.simposio.taskeer.domain.workspace

sealed interface NameCheck {
    data class Valid(val name: String) : NameCheck

    data object TooShort : NameCheck

    data object TooLong : NameCheck
}

data class ConfirmedOps(
    val deletedIds: Set<String> = emptySet(),
    val renamed: Map<String, String> = emptyMap(),
    val created: List<Workspace> = emptyList(),
)

object WorkspaceRules {
    const val NAME_MIN = 2
    const val NAME_MAX = 150

    fun validateName(raw: String): NameCheck {
        val name = raw.trim()
        return when {
            name.length < NAME_MIN -> NameCheck.TooShort
            name.length > NAME_MAX -> NameCheck.TooLong
            else -> NameCheck.Valid(name)
        }
    }

    fun sorted(items: List<Workspace>): List<Workspace> =
        items.sortedWith(
            compareByDescending<Workspace> { it.isPersonal }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.id },
        )

    fun canRename(role: WorkspaceRole): Boolean = role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN

    fun canDelete(role: WorkspaceRole, isPersonal: Boolean): Boolean = role == WorkspaceRole.OWNER && !isPersonal

    // Un refresco que empezó antes de una mutación confirmada trae datos previos a ella.
    fun reapply(fresh: List<Workspace>, confirmed: ConfirmedOps): List<Workspace> =
        sorted(
            (fresh + confirmed.created.filter { created -> fresh.none { it.id == created.id } })
                .filter { it.id !in confirmed.deletedIds }
                .map { workspace -> confirmed.renamed[workspace.id]?.let { workspace.copy(name = it) } ?: workspace },
        )

    fun roleOf(raw: String?): WorkspaceRole =
        WorkspaceRole.entries.firstOrNull { it != WorkspaceRole.UNKNOWN && it.name == raw } ?: WorkspaceRole.UNKNOWN
}
