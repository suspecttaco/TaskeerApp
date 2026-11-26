package com.simposio.taskeer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.ui.auth.AccountAction
import com.simposio.taskeer.ui.auth.AccountSectionModel
import com.simposio.taskeer.ui.auth.AccountStatus
import com.simposio.taskeer.ui.auth.accountSectionFor
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

internal data class AccountSectionActions(
    val onSignIn: () -> Unit,
    val onRetry: () -> Unit,
    val onSignOut: () -> Unit,
)

@Composable
internal fun AccountSettingsSection(
    state: SessionState,
    actions: AccountSectionActions,
    modifier: Modifier = Modifier,
) {
    val model = accountSectionFor(state)
    var confirmingSignOut by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        SectionHeader(text = stringResource(R.string.auth_account_title), icon = Icons.Filled.AccountCircle)
        AccountStatusRow(model = model, onSignIn = actions.onSignIn)
        if (model.actions.any { it != AccountAction.SIGN_IN }) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
                if (AccountAction.RETRY in model.actions) {
                    TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.auth_retry)) }
                }
                if (AccountAction.SIGN_OUT in model.actions) {
                    OutlinedButton(onClick = { confirmingSignOut = true }) {
                        Text(stringResource(R.string.auth_logout_action))
                    }
                }
            }
        }
    }
    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text(stringResource(R.string.auth_logout_confirm_title)) },
            text = { Text(stringResource(R.string.auth_logout_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSignOut = false
                        actions.onSignOut()
                    },
                ) { Text(stringResource(R.string.auth_logout_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) {
                    Text(stringResource(R.string.auth_logout_cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountStatusRow(model: AccountSectionModel, onSignIn: () -> Unit) {
    when (model.status) {
        AccountStatus.SIGNED_OUT -> ListItem(
            headlineContent = { Text(stringResource(R.string.auth_login_action)) },
            supportingContent = { Text(stringResource(R.string.auth_signed_out_support)) },
            trailingContent = { DecorativeIcon(Icons.Filled.ChevronRight) },
            modifier = Modifier.clickable(onClick = onSignIn),
        )
        AccountStatus.RESUMING -> ListItem(
            headlineContent = { Text(stringResource(R.string.auth_resuming)) },
            leadingContent = { CircularProgressIndicator(modifier = Modifier.size(Spacing.ActionIconSize)) },
        )
        AccountStatus.SIGNED_IN -> ListItem(
            headlineContent = { Text(stringResource(R.string.auth_signed_in)) },
            supportingContent = model.email?.let { email -> { Text(email) } },
        )
        AccountStatus.SIGNED_IN_OFFLINE -> ListItem(
            headlineContent = { Text(stringResource(R.string.auth_signed_in_offline)) },
            supportingContent = model.email?.let { email -> { Text(email) } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountSettingsSectionPreview() {
    TaskeerTheme {
        AccountSettingsSection(
            state = SessionState.SignedIn(AccountSummary("ana@example.com", "Ana"), offline = true),
            actions = AccountSectionActions({}, {}, {}),
        )
    }
}
