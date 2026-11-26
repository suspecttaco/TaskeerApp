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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.ui.auth.AccountAction
import com.simposio.taskeer.ui.auth.AccountSectionModel
import com.simposio.taskeer.ui.auth.AccountStatus
import com.simposio.taskeer.ui.auth.EmailVerification
import com.simposio.taskeer.ui.auth.accountSectionFor
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import com.simposio.taskeer.ui.verifyemail.ResendMessage
import com.simposio.taskeer.ui.verifyemail.ResendVerificationUiState

internal data class AccountSectionActions(
    val onSignIn: () -> Unit,
    val onRetry: () -> Unit,
    val onSignOut: () -> Unit,
    val onResendVerification: () -> Unit,
    val onHaveVerificationLink: () -> Unit,
)

@Composable
internal fun AccountSettingsSection(
    state: SessionState,
    resend: ResendVerificationUiState,
    actions: AccountSectionActions,
    modifier: Modifier = Modifier,
) {
    val model = accountSectionFor(state)
    var confirmingSignOut by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        SectionHeader(text = stringResource(R.string.auth_account_title), icon = Icons.Filled.AccountCircle)
        AccountStatusRow(model = model, onSignIn = actions.onSignIn)
        model.verification?.let { VerificationRows(it, resend, actions) }
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
private fun VerificationRows(
    verification: EmailVerification,
    resend: ResendVerificationUiState,
    actions: AccountSectionActions,
) {
    when (verification) {
        EmailVerification.VERIFIED -> ListItem(headlineContent = { Text(stringResource(R.string.verify_account_verified)) })
        EmailVerification.UNVERIFIED -> {
            ListItem(headlineContent = { Text(stringResource(R.string.verify_account_unverified)) })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
                TextButton(onClick = actions.onResendVerification, enabled = resend.canResend) {
                    if (resend.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(Spacing.IconSize), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.verify_resend_action))
                    }
                }
                TextButton(onClick = actions.onHaveVerificationLink) {
                    Text(stringResource(R.string.verify_have_link))
                }
            }
            resend.message?.let { message ->
                Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        text = resendMessageText(message, resend.retryMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (message == ResendMessage.NEEDS_SIGN_IN) {
                        TextButton(onClick = actions.onSignIn) {
                            Text(stringResource(R.string.verify_error_needs_sign_in_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun resendMessageText(message: ResendMessage, retryMinutes: Int?): String = when (message) {
    ResendMessage.SENT -> stringResource(R.string.verify_resend_sent)
    ResendMessage.ALREADY_VERIFIED -> stringResource(R.string.verify_resend_already)
    ResendMessage.DISABLED -> stringResource(R.string.verify_error_disabled)
    ResendMessage.NEEDS_SIGN_IN -> stringResource(R.string.verify_error_needs_sign_in)
    ResendMessage.RATE_LIMITED ->
        if (retryMinutes != null) {
            stringResource(R.string.verify_error_rate_limited_minutes, retryMinutes)
        } else {
            stringResource(R.string.verify_error_rate_limited)
        }
    ResendMessage.NETWORK -> stringResource(R.string.verify_error_network)
    ResendMessage.SERVER -> stringResource(R.string.verify_error_server)
    ResendMessage.NOT_SUPPORTED -> stringResource(R.string.auth_not_supported_message)
    ResendMessage.UNEXPECTED -> stringResource(R.string.verify_error_unexpected)
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
            state = SessionState.SignedIn(AccountSummary("ana@example.com", "Ana", emailVerified = false), offline = true),
            resend = ResendVerificationUiState(),
            actions = AccountSectionActions({}, {}, {}, {}, {}),
        )
    }
}
