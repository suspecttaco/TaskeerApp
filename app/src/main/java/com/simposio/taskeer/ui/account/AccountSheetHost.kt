package com.simposio.taskeer.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.ui.auth.SessionViewModel
import com.simposio.taskeer.ui.navigation.AppDestination
import com.simposio.taskeer.ui.verifyemail.ResendVerificationViewModel

@Composable
fun AccountSheetHost(
    session: SessionViewModel,
    viewModelFactory: ViewModelProvider.Factory,
    requested: Boolean,
    onDismiss: () -> Unit,
    navigate: (String) -> Unit,
) {
    val resendViewModel: ResendVerificationViewModel = viewModel(factory = viewModelFactory)
    val resendState by resendViewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    // Si la sesión cae con la hoja pedida, se baja la bandera: al volver a iniciar sesión la hoja no reaparece sola.
    LaunchedEffect(requested, sessionState) {
        if (requested && !sheetVisible(requested, sessionState)) onDismiss()
    }
    if (!sheetVisible(requested, sessionState)) return
    val model = accountSheetFor(sessionState) ?: return
    fun leaveTo(destination: AppDestination) {
        onDismiss()
        navigate(destination.route)
    }
    AccountSheet(
        model = model,
        resend = resendState,
        actions = AccountSectionActions(
            onSignIn = { leaveTo(AppDestination.LOGIN) },
            onRetry = session::retryResume,
            onSignOut = {
                session.logout()
                onDismiss()
            },
            onResendVerification = resendViewModel::resend,
            onHaveVerificationLink = { leaveTo(AppDestination.VERIFY_EMAIL) },
            onChangePassword = { leaveTo(AppDestination.CHANGE_PASSWORD) },
            onRegenerateCodes = { leaveTo(AppDestination.REGENERATE_CODES) },
        ),
        onDismiss = onDismiss,
    )
}
