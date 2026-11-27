package com.simposio.taskeer.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.InlineProgress
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun AccountEntryAction(
    entry: AccountEntry,
    onSignIn: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        AccountEntry.SignIn -> TextButton(
            onClick = onSignIn,
            modifier = modifier.heightIn(min = Spacing.MinTouchTarget),
        ) { Text(stringResource(R.string.auth_login_action)) }
        AccountEntry.Resuming -> {
            val description = stringResource(R.string.account_entry_resuming)
            Box(
                modifier = modifier
                    .size(Spacing.MinTouchTarget)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                InlineProgress(modifier = Modifier.size(Spacing.AvatarSize))
            }
        }
        is AccountEntry.Avatar -> {
            val description = if (entry.displayName != null) {
                stringResource(R.string.account_avatar_description, entry.displayName)
            } else {
                stringResource(R.string.account_avatar_description_unknown)
            }
            IconButton(
                onClick = onOpenAccount,
                modifier = modifier
                    .size(Spacing.MinTouchTarget)
                    .semantics { contentDescription = description },
            ) {
                AccountAvatar(initials = entry.initials, colorIndex = entry.colorIndex)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountEntryActionPreview() {
    TaskeerTheme {
        AccountEntryAction(AccountEntry.SignIn, onSignIn = {}, onOpenAccount = {})
    }
}
