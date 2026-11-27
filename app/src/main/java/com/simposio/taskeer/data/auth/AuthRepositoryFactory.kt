package com.simposio.taskeer.data.auth

import android.content.Context
import com.simposio.taskeer.data.remote.auth.RemoteAuthRepository
import com.simposio.taskeer.data.remote.boards.RemoteBoardAccessRepository
import com.simposio.taskeer.data.remote.boards.RemoteServerBoardsRepository
import com.simposio.taskeer.data.remote.createApiClient
import com.simposio.taskeer.data.remote.notifications.RemoteNotificationInboxRepository
import com.simposio.taskeer.data.remote.notifications.RemoteNotificationLiveChannel
import com.simposio.taskeer.data.remote.workspaces.RemoteMyInvitationsRepository
import com.simposio.taskeer.data.remote.workspaces.RemoteWorkspaceInvitationsRepository
import com.simposio.taskeer.data.remote.workspaces.RemoteWorkspaceMembersRepository
import com.simposio.taskeer.data.remote.workspaces.RemoteWorkspaceRepository
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.data.repository.BoardAccessRepository
import com.simposio.taskeer.data.repository.MyInvitationsRepository
import com.simposio.taskeer.data.repository.NotificationInboxRepository
import com.simposio.taskeer.data.repository.NotificationLiveChannel
import com.simposio.taskeer.data.repository.ServerBoardsRepository
import com.simposio.taskeer.data.repository.WorkspaceInvitationsRepository
import com.simposio.taskeer.data.repository.WorkspaceMembersRepository
import com.simposio.taskeer.data.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope

class RemoteRepositories(
    val auth: AuthRepository,
    val inbox: NotificationInboxRepository,
    val live: NotificationLiveChannel,
    val workspaces: WorkspaceRepository,
    val workspaceMembers: WorkspaceMembersRepository,
    val workspaceInvitations: WorkspaceInvitationsRepository,
    val myInvitations: MyInvitationsRepository,
    val serverBoards: ServerBoardsRepository,
    val boardAccess: BoardAccessRepository,
)

// Único puente hacia data.remote: di/ compone esta función sin conocer el cliente HTTP.
// Los repositorios comparten un solo cliente y una sola sesión: dos sesiones no compartirían el access token ni su renovación.
fun createRemoteRepositories(context: Context, scope: CoroutineScope): RemoteRepositories {
    val remote = createApiClient(context)
    val auth = RemoteAuthRepository(remote.client, remote.session, scope)
    val inbox = RemoteNotificationInboxRepository(remote.client, auth.sessionState, scope)
    val live = RemoteNotificationLiveChannel(remote.client, remote.streamTransport, inbox, auth.sessionState, remote.config)
    val workspaces = RemoteWorkspaceRepository(remote.client, auth.sessionState, scope)
    val workspaceMembers = RemoteWorkspaceMembersRepository(remote.client, auth.sessionState, scope)
    val workspaceInvitations = RemoteWorkspaceInvitationsRepository(remote.client, auth.sessionState, scope)
    val myInvitations = RemoteMyInvitationsRepository(remote.client, auth.sessionState, scope)
    val serverBoards = RemoteServerBoardsRepository(remote.client, auth.sessionState, scope)
    val boardAccess = RemoteBoardAccessRepository(remote.client, auth.sessionState, scope)
    return RemoteRepositories(
        auth, inbox, live, workspaces, workspaceMembers, workspaceInvitations, myInvitations, serverBoards, boardAccess,
    )
}
