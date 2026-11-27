package com.simposio.taskeer.ui.account

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.avatarColorIndex
import com.simposio.taskeer.domain.auth.avatarInitials
import com.simposio.taskeer.ui.auth.AccountSectionModel
import com.simposio.taskeer.ui.auth.accountSectionFor

data class AccountIdentity(
    val displayName: String,
    val email: String,
    val initials: String?,
    val colorIndex: Int,
) {
    override fun toString(): String = "AccountIdentity(redacted)"
}

data class AccountSheetModel(val identity: AccountIdentity?, val section: AccountSectionModel) {
    override fun toString(): String = "AccountSheetModel(status=${section.status})"
}

fun accountSheetFor(state: SessionState): AccountSheetModel? {
    if (state !is SessionState.SignedIn) return null
    val account = state.account
    val identity = account?.let {
        AccountIdentity(
            displayName = visibleNameOf(it.displayName, it.email).orEmpty(),
            email = it.email.takeIf { email -> email.isNotBlank() }.orEmpty(),
            initials = avatarInitials(it.displayName, it.email),
            colorIndex = avatarColorIndex(it.displayName, it.email),
        )
    }
    return AccountSheetModel(identity, accountSectionFor(state))
}

fun sheetVisible(requested: Boolean, state: SessionState): Boolean = requested && state is SessionState.SignedIn
