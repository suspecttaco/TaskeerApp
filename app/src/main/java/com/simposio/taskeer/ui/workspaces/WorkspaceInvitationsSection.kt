package com.simposio.taskeer.ui.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.EmptyHint
import com.simposio.taskeer.ui.components.InlineError
import com.simposio.taskeer.ui.components.LoadingBody
import com.simposio.taskeer.ui.components.rowItemModifier
import com.simposio.taskeer.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun LazyListScope.invitationsSection(
    state: WorkspaceInvitationsUiState,
    onInvite: () -> Unit,
    onRevoke: (String) -> Unit,
    onRetry: () -> Unit,
) {
    if (state.section == SectionVisibility.HIDDEN) return
    item(key = "invitations-header") { InvitationsHeader(onInvite) }
    when (state.section) {
        SectionVisibility.LOADING -> item(key = "invitations-loading") { InvitationsLoading() }
        SectionVisibility.ERROR -> item(key = "invitations-error") {
            InvitationsError((state.error ?: WorkspaceInvitationErrorMessage.Generic).text(), onRetry)
        }
        SectionVisibility.EMPTY -> item(key = "invitations-empty") {
            EmptyHint(
                text = stringResource(R.string.workspace_invitations_section_empty),
                modifier = Modifier.padding(Spacing.ScreenPadding),
            )
        }
        SectionVisibility.LIST, SectionVisibility.HIDDEN -> items(state.rows, key = { "invitation-${it.id}" }) { row ->
            Column(modifier = rowItemModifier()) {
                InvitationRow(row, onRevoke)
                AppDivider()
            }
        }
    }
}

@Composable
private fun InvitationsHeader(onInvite: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.ScreenPadding, end = Spacing.InlineGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.workspace_invitations_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        TextButton(onClick = onInvite) { Text(stringResource(R.string.workspace_invitations_invite)) }
    }
}

@Composable
private fun InvitationsLoading() {
    val description = stringResource(R.string.workspace_invitations_loading)
    LoadingBody(description, Modifier.fillMaxWidth().padding(Spacing.ScreenPadding))
}

@Composable
private fun InvitationsError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.ScreenPadding, end = Spacing.InlineGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        InlineError(
            message = message,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.workspace_invitations_retry)) }
    }
}

@Composable
private fun InvitationRow(row: InvitationRowUi, onRevoke: (String) -> Unit) {
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        leadingContent = { DecorativeIcon(if (row.expired) Icons.Filled.Schedule else Icons.Filled.Mail) },
        headlineContent = { Text(row.email) },
        supportingContent = {
            Text(
                text = stringResource(R.string.workspace_invitations_row_summary, row.role.text(), expiryText(row.expired, row.expiresAt)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (InvitationAction.REVOKE in row.actions) {
                TextButton(onClick = { onRevoke(row.id) }) { Text(stringResource(R.string.workspace_invitations_revoke)) }
            }
        },
    )
}

@Composable
internal fun expiryText(expired: Boolean, expiresAt: Instant): String {
    if (expired) return stringResource(R.string.workspace_invitations_expired)
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    return stringResource(R.string.workspace_invitations_expires_on, formatter.format(expiresAt.atZone(ZoneId.systemDefault())))
}
