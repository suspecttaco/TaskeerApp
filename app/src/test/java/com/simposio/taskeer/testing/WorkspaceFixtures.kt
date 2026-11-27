package com.simposio.taskeer.testing

import com.simposio.taskeer.domain.workspace.BoardMemberOverride
import com.simposio.taskeer.domain.workspace.ServerBoard
import com.simposio.taskeer.domain.workspace.Workspace
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import java.time.Instant

fun workspace(
    id: String = "11111111-1111-4111-8111-111111111111",
    name: String = "Equipo",
    role: WorkspaceRole = WorkspaceRole.OWNER,
    isPersonal: Boolean = false,
    slug: String = "equipo",
    updatedAt: Instant = Instant.parse("2026-11-01T10:00:00Z"),
): Workspace = Workspace(
    id = id,
    name = name,
    slug = slug,
    ownerId = "22222222-2222-4222-8222-222222222222",
    myRole = role,
    isPersonal = isPersonal,
    createdAt = Instant.parse("2026-10-01T10:00:00Z"),
    updatedAt = updatedAt,
)

fun uuid(n: Int): String = "%08d-0000-4000-8000-000000000000".format(n)

fun member(
    n: Int,
    role: WorkspaceRole = WorkspaceRole.EDITOR,
    name: String = "Miembro $n",
    email: String = "m$n@example.com",
): WorkspaceMember = WorkspaceMember(
    userId = uuid(n),
    email = email,
    displayName = name,
    role = role,
    createdAt = Instant.parse("2026-10-01T10:00:00Z"),
)

fun serverBoard(n: Int, name: String = "Tablero $n"): ServerBoard = ServerBoard(uuid(1000 + n), name)

fun boardOverride(memberN: Int, role: WorkspaceRole = WorkspaceRole.VIEWER): BoardMemberOverride = BoardMemberOverride(
    id = uuid(2000 + memberN),
    userId = uuid(memberN),
    role = role,
    updatedAt = Instant.parse("2026-11-01T10:00:00Z"),
)
