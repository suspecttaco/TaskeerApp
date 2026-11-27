package com.simposio.taskeer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.ui.auth.AccountStatus
import com.simposio.taskeer.ui.auth.accountSectionFor
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.InlineProgress
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun AccountSettingsSection(
    state: SessionState,
    onSignIn: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = accountSectionFor(state)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        SectionHeader(text = stringResource(R.string.auth_account_title), icon = Icons.Filled.AccountCircle)
        when (model.status) {
            AccountStatus.SIGNED_OUT -> ListItem(
                headlineContent = { Text(stringResource(R.string.auth_login_action)) },
                supportingContent = { Text(stringResource(R.string.auth_signed_out_support)) },
                trailingContent = { DecorativeIcon(Icons.Filled.ChevronRight) },
                modifier = Modifier.heightIn(min = Spacing.MinTouchTarget).clickable(onClick = onSignIn),
            )
            AccountStatus.RESUMING -> ListItem(
                headlineContent = { Text(stringResource(R.string.auth_resuming)) },
                leadingContent = { InlineProgress(modifier = Modifier.size(Spacing.ActionIconSize)) },
            )
            AccountStatus.SIGNED_IN, AccountStatus.SIGNED_IN_OFFLINE -> ListItem(
                headlineContent = { Text(stringResource(R.string.settings_account_row)) },
                supportingContent = model.email?.let { email -> { Text(email) } },
                trailingContent = { DecorativeIcon(Icons.Filled.ChevronRight) },
                modifier = Modifier.heightIn(min = Spacing.MinTouchTarget).clickable(onClick = onOpenAccount),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountSettingsSectionPreview() {
    TaskeerTheme {
        AccountSettingsSection(
            state = SessionState.SignedIn(AccountSummary("ana@example.com", "Ana", emailVerified = false), offline = true),
            onSignIn = {},
            onOpenAccount = {},
        )
    }
}
