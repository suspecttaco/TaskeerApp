package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.domain.auth.SessionState

enum class AccountStatus { SIGNED_OUT, RESUMING, SIGNED_IN, SIGNED_IN_OFFLINE }

enum class AccountAction { SIGN_IN, RETRY, SIGN_OUT }

enum class EmailVerification { UNVERIFIED, VERIFIED }

data class AccountSectionModel(
    val status: AccountStatus,
    val email: String?,
    val actions: List<AccountAction>,
    val verification: EmailVerification? = null,
)

fun accountSectionFor(state: SessionState): AccountSectionModel = when (state) {
    is SessionState.SignedOut -> AccountSectionModel(AccountStatus.SIGNED_OUT, null, listOf(AccountAction.SIGN_IN))
    SessionState.Resuming -> AccountSectionModel(AccountStatus.RESUMING, null, emptyList())
    is SessionState.SignedIn -> AccountSectionModel(
        status = if (state.offline) AccountStatus.SIGNED_IN_OFFLINE else AccountStatus.SIGNED_IN,
        email = state.account?.email?.takeIf { it.isNotBlank() },
        actions = if (state.offline) listOf(AccountAction.RETRY, AccountAction.SIGN_OUT) else listOf(AccountAction.SIGN_OUT),
        verification = when (state.account?.emailVerified) {
            true -> EmailVerification.VERIFIED
            false -> EmailVerification.UNVERIFIED
            null -> null
        },
    )
}
