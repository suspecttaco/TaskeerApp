package com.simposio.taskeer.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.ui.auth.AccountAction
import com.simposio.taskeer.ui.auth.AccountSecurityAction
import com.simposio.taskeer.ui.auth.AccountStatus
import com.simposio.taskeer.ui.auth.EmailVerification
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.ButtonProgress
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.shell.TrackOpenSheet
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
    val onChangePassword: () -> Unit,
    val onRegenerateCodes: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSheet(
    model: AccountSheetModel,
    resend: ResendVerificationUiState,
    actions: AccountSectionActions,
    onDismiss: () -> Unit,
) {
    var confirmingSignOut by remember { mutableStateOf(false) }
    val title = stringResource(R.string.auth_account_title)
    val dragDescription = stringResource(R.string.quick_add_drag_handle)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            BottomSheetDefaults.DragHandle(modifier = Modifier.semantics { contentDescription = dragDescription })
        },
    ) {
        TrackOpenSheet()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Spacing.SectionGap)
                .semantics { paneTitle = title },
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ActionIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.quick_add_close),
                    onClick = onDismiss,
                )
            }
            IdentityHeader(model)
            if (AccountAction.RETRY in model.section.actions) {
                TextButton(
                    onClick = actions.onRetry,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenPadding),
                ) { Text(stringResource(R.string.auth_retry)) }
            }
            model.section.verification?.let { VerificationRows(it, resend, actions) }
            model.section.security.forEach { SecurityRow(it, actions) }
            if (AccountAction.SIGN_OUT in model.section.actions) {
                OutlinedButton(
                    onClick = { confirmingSignOut = true },
                    modifier = Modifier
                        .padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap)
                        .heightIn(min = Spacing.MinTouchTarget),
                ) { Text(stringResource(R.string.auth_logout_action)) }
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
private fun IdentityHeader(model: AccountSheetModel) {
    val status = stringResource(
        if (model.section.status == AccountStatus.SIGNED_IN_OFFLINE) R.string.auth_signed_in_offline else R.string.auth_signed_in,
    )
    val identity = model.identity
    if (identity == null) {
        ListItem(headlineContent = { Text(status) })
        return
    }
    ListItem(
        leadingContent = {
            AccountAvatar(identity.initials, identity.colorIndex, size = Spacing.AvatarSizeLarge)
        },
        headlineContent = {
            Text(text = identity.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                if (identity.email.isNotEmpty()) {
                    Text(text = identity.email, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(text = status)
            }
        },
    )
}

@Composable
private fun SecurityRow(action: AccountSecurityAction, actions: AccountSectionActions) {
    val (label, onClick) = when (action) {
        AccountSecurityAction.CHANGE_PASSWORD -> R.string.chpw_settings_row to actions.onChangePassword
        AccountSecurityAction.REGENERATE_CODES -> R.string.regen_settings_row to actions.onRegenerateCodes
    }
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        trailingContent = { DecorativeIcon(Icons.Filled.ChevronRight) },
        modifier = Modifier.heightIn(min = Spacing.MinTouchTarget).clickable(onClick = onClick),
    )
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
            Row(
                modifier = Modifier.padding(horizontal = Spacing.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
            ) {
                TextButton(onClick = actions.onResendVerification, enabled = resend.canResend) {
                    ButtonProgress(loading = resend.isSending, label = stringResource(R.string.verify_resend_action))
                }
                TextButton(onClick = actions.onHaveVerificationLink) {
                    Text(stringResource(R.string.verify_have_link))
                }
            }
            resend.message?.let { message ->
                Column(
                    modifier = Modifier
                        .padding(horizontal = Spacing.ScreenPadding)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
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

@Preview(showBackground = true)
@Composable
private fun AccountSheetPreview() {
    TaskeerTheme {
        AccountSheet(
            model = accountSheetFor(
                SessionState.SignedIn(AccountSummary("ana@example.com", "Ana López", emailVerified = false), offline = true),
            )!!,
            resend = ResendVerificationUiState(),
            actions = AccountSectionActions({}, {}, {}, {}, {}, {}, {}),
            onDismiss = {},
        )
    }
}
