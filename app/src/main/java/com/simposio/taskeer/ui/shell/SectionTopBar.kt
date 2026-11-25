package com.simposio.taskeer.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.notification.badgeLabel
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.home.HomeSearchField
import com.simposio.taskeer.ui.navigation.MenuItem
import com.simposio.taskeer.ui.navigation.OverflowMenu
import com.simposio.taskeer.ui.navigation.TopBarKind
import com.simposio.taskeer.ui.theme.TaskeerTheme

class TopBarActions(
    val onOpenSearch: () -> Unit,
    val onNotifications: () -> Unit,
    val onSuggestions: () -> Unit,
    val onBack: () -> Unit,
    val onMenuItem: (MenuItem) -> Unit,
)

// Los composables no deciden nada de negocio: la decisión (chromeFor, menú filtrado) llega ya tomada para poder probarla en JVM.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellTopBar(
    kind: TopBarKind,
    title: String,
    unreadCount: Int,
    showSuggestions: Boolean,
    suggestionCount: Int,
    menu: OverflowMenu,
    clearEnabled: Boolean,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
    calendarsEnabled: Boolean = true,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            when (kind) {
                TopBarKind.SECTION, TopBarKind.HUB -> NotificationsAction(unreadCount, actions.onNotifications)
                TopBarKind.DETAIL, TopBarKind.DETAIL_WITH_MENU -> ActionIcon(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                    onClick = actions.onBack,
                )
            }
        },
        title = {
            if (kind == TopBarKind.HUB) {
                HomeSearchField(actions.onOpenSearch)
            } else {
                Text(text = title, modifier = Modifier.semantics { heading() })
            }
        },
        actions = {
            if (showSuggestions) {
                SuggestionsAction(suggestionCount, actions.onSuggestions)
            }
            if (kind != TopBarKind.HUB && kind != TopBarKind.DETAIL) {
                OverflowMenuButton(
                    menu = menu,
                    clearEnabled = clearEnabled,
                    onItem = actions.onMenuItem,
                    calendarsEnabled = calendarsEnabled,
                )
            }
        },
    )
}

@Composable
private fun NotificationsAction(unreadCount: Int, onClick: () -> Unit) {
    val label = badgeLabel(unreadCount)
    BadgedBox(
        badge = {
            if (label != null) {
                Badge { Text(label) }
            }
        },
    ) {
        ActionIcon(
            icon = Icons.Filled.Notifications,
            contentDescription = stringResource(R.string.nav_notifications),
            onClick = onClick,
        )
    }
}

@Composable
private fun SuggestionsAction(count: Int, onClick: () -> Unit) {
    val label = badgeLabel(count)
    BadgedBox(
        badge = {
            if (label != null) {
                Badge { Text(label) }
            }
        },
    ) {
        ActionIcon(
            icon = Icons.Filled.Lightbulb,
            contentDescription = if (label == null) {
                stringResource(R.string.my_day_suggestions_action_none)
            } else {
                stringResource(R.string.my_day_suggestions_action, label)
            },
            onClick = onClick,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShellTopBarPreview() {
    TaskeerTheme {
        ShellTopBar(
            kind = TopBarKind.SECTION,
            title = "Mi día",
            unreadCount = 2,
            showSuggestions = true,
            suggestionCount = 120,
            menu = OverflowMenu(emptyList(), emptyList()),
            clearEnabled = false,
            actions = TopBarActions({}, {}, {}, {}, {}),
        )
    }
}
