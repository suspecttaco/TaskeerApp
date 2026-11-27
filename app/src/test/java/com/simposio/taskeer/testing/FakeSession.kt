package com.simposio.taskeer.testing

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import kotlinx.coroutines.flow.MutableStateFlow

fun signedIn(offline: Boolean = false): SessionState =
    SessionState.SignedIn(AccountSummary("ana@example.com", "Ana"), offline)

class FakeSession(initial: SessionState = signedIn()) {
    val state = MutableStateFlow(initial)
}
