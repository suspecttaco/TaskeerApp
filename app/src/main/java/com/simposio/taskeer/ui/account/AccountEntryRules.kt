package com.simposio.taskeer.ui.account

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.avatarColorIndex
import com.simposio.taskeer.domain.auth.avatarInitials

sealed interface AccountEntry {
    data object SignIn : AccountEntry

    data object Resuming : AccountEntry

    // `displayName` solo alimenta la descripción de accesibilidad; el `toString` no lo incluye para que un registro accidental no lo filtre.
    data class Avatar(
        val initials: String?,
        val colorIndex: Int,
        val displayName: String?,
        val offline: Boolean,
    ) : AccountEntry {
        override fun toString(): String = "Avatar(offline=$offline)"
    }
}

fun accountEntryFor(state: SessionState): AccountEntry = when (state) {
    is SessionState.SignedOut -> AccountEntry.SignIn
    SessionState.Resuming -> AccountEntry.Resuming
    is SessionState.SignedIn -> {
        val account = state.account
        if (account == null) {
            AccountEntry.Avatar(initials = null, colorIndex = 0, displayName = null, offline = state.offline)
        } else {
            AccountEntry.Avatar(
                initials = avatarInitials(account.displayName, account.email),
                colorIndex = avatarColorIndex(account.displayName, account.email),
                displayName = visibleNameOf(account.displayName, account.email),
                offline = state.offline,
            )
        }
    }
}

internal fun visibleNameOf(displayName: String, email: String): String? =
    displayName.trim().ifEmpty { email.substringBefore('@').trim() }.takeIf { it.isNotEmpty() }
