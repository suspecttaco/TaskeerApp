package com.simposio.taskeer.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.theme.Spacing
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

@Composable
fun LoginScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onSignedIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onHaveVerificationLink: () -> Unit,
    onRecoverAccess: () -> Unit,
    modifier: Modifier = Modifier,
    prefill: LoginHandoff? = null,
    onPrefillConsumed: () -> Unit = {},
) {
    val viewModel: LoginViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Estado sin guardado: persistir el texto en el Bundle sacaría la contraseña de esta pantalla.
    val passwordState = remember { TextFieldState() }

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }
    LaunchedEffect(prefill) {
        if (prefill != null) {
            viewModel.prefill(prefill.email, prefill.notice)
            onPrefillConsumed()
        }
    }
    LaunchedEffect(state.passwordResetCount) { passwordState.clearText() }
    LaunchedEffect(passwordState) {
        snapshotFlow { passwordState.text.length }
            .drop(1)
            .filter { it > 0 }
            .collect { viewModel.onPasswordEdited() }
    }
    DisposableEffect(passwordState) { onDispose { passwordState.clearText() } }

    if (state.blocked == LoginBlock.NOT_SUPPORTED) {
        StatePage(
            icon = Icons.Filled.Block,
            title = stringResource(R.string.auth_not_supported_title),
            message = stringResource(R.string.auth_not_supported_message),
            modifier = modifier,
            action = StatePageAction(stringResource(R.string.auth_not_supported_back), viewModel::dismissNotSupported),
        )
        return
    }

    when (state.phase) {
        LoginPhase.CODES -> {
            GoogleCodesContent(viewModel, state, modifier)
            return
        }
        LoginPhase.CODES_LOST -> {
            StatePage(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.google_codes_lost_title),
                message = stringResource(R.string.google_codes_lost_message),
                modifier = modifier,
                action = StatePageAction(stringResource(R.string.google_codes_lost_action), viewModel::acknowledgeLostCodes),
            )
            return
        }
        LoginPhase.FORM -> Unit
    }

    val activity = LocalActivity.current
    val submit = {
        val text = passwordState.text
        viewModel.submit(state.email, CharArray(text.length) { text[it] })
    }
    LoginForm(
        state = state,
        passwordState = passwordState,
        onEmailChange = viewModel::onEmailChange,
        onSubmit = submit,
        onGoogle = { activity?.let(viewModel::signInWithGoogle) },
        onCreateAccount = onCreateAccount,
        onHaveVerificationLink = onHaveVerificationLink,
        onRecoverAccess = onRecoverAccess,
        modifier = modifier,
    )
}

@Composable
private fun LoginForm(
    state: LoginUiState,
    passwordState: TextFieldState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogle: () -> Unit,
    onCreateAccount: () -> Unit,
    onHaveVerificationLink: () -> Unit,
    onRecoverAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val emailInvalid = state.error == LoginError.EMAIL_INVALID
    val passwordInvalid = state.error == LoginError.PASSWORD_REQUIRED || state.error == LoginError.PASSWORD_TOO_LONG
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Username },
            label = { Text(stringResource(R.string.auth_email_label)) },
            singleLine = true,
            isError = emailInvalid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
        )
        OutlinedSecureTextField(
            state = passwordState,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
            label = { Text(stringResource(R.string.auth_password_label)) },
            isError = passwordInvalid,
            textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onKeyboardAction = KeyboardActionHandler { if (state.canSubmit) onSubmit() },
            trailingIcon = {
                ActionIcon(
                    icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (passwordVisible) R.string.auth_password_hide else R.string.auth_password_show,
                    ),
                    onClick = { passwordVisible = !passwordVisible },
                )
            },
        )
        state.notice?.let { notice ->
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                when (notice) {
                    LoginNotice.ACCOUNT_CREATED ->
                        Text(
                            text = stringResource(R.string.register_account_created_notice),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    LoginNotice.PASSWORD_RESET -> {
                        Text(
                            text = stringResource(R.string.reset_login_notice),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.reset_login_notice_codes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        state.error?.let { error ->
            Text(
                text = loginErrorText(error, state.retryMinutes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.MinTouchTarget),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(Spacing.IconSize), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.auth_login_action))
            }
        }
        if (state.googleAvailable) {
            GoogleSignInSection(state, onGoogle)
        }
        TextButton(
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) { Text(stringResource(R.string.register_link_action)) }
        TextButton(
            onClick = onRecoverAccess,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) { Text(stringResource(R.string.reset_login_action)) }
        TextButton(
            onClick = onHaveVerificationLink,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) { Text(stringResource(R.string.verify_have_link)) }
        Text(
            text = stringResource(R.string.auth_login_optional_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoogleSignInSection(state: LoginUiState, onGoogle: () -> Unit) {
    val busy = stringResource(R.string.google_in_progress)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.google_divider),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
    OutlinedButton(
        onClick = onGoogle,
        enabled = state.canUseGoogle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .semantics { if (state.googleInProgress) stateDescription = busy },
    ) {
        if (state.googleInProgress) {
            CircularProgressIndicator(modifier = Modifier.size(Spacing.IconSize), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.google_continue_action))
        }
    }
}

@Composable
private fun loginErrorText(error: LoginError, retryMinutes: Int?): String = when (error) {
    LoginError.EMAIL_INVALID -> stringResource(R.string.auth_error_email_invalid)
    LoginError.PASSWORD_REQUIRED -> stringResource(R.string.auth_error_password_required)
    LoginError.PASSWORD_TOO_LONG -> stringResource(R.string.auth_error_password_too_long)
    LoginError.INVALID_CREDENTIALS -> stringResource(R.string.auth_error_invalid_credentials)
    LoginError.RATE_LIMITED ->
        if (retryMinutes != null) {
            stringResource(R.string.auth_error_rate_limited_minutes, retryMinutes)
        } else {
            stringResource(R.string.auth_error_rate_limited)
        }
    LoginError.NETWORK -> stringResource(R.string.auth_error_network)
    LoginError.SERVER -> stringResource(R.string.auth_error_server)
    LoginError.VALIDATION -> stringResource(R.string.auth_error_validation)
    LoginError.UNEXPECTED -> stringResource(R.string.auth_error_unexpected)
    LoginError.NO_LOCAL_PASSWORD -> stringResource(R.string.google_error_no_local_password)
    LoginError.GOOGLE_NO_ACCOUNT -> stringResource(R.string.google_error_no_account)
    LoginError.GOOGLE_UNAVAILABLE -> stringResource(R.string.google_error_unavailable)
    LoginError.GOOGLE_FAILED -> stringResource(R.string.google_error_failed)
    LoginError.GOOGLE_TOKEN_INVALID -> stringResource(R.string.google_error_token_invalid)
    LoginError.GOOGLE_EMAIL_NOT_VERIFIED -> stringResource(R.string.google_error_email_not_verified)
    LoginError.LOCAL_ACCOUNT_NOT_VERIFIED -> stringResource(R.string.google_error_local_account_not_verified)
    LoginError.GOOGLE_REJECTED -> stringResource(R.string.google_error_rejected)
    LoginError.GOOGLE_UNEXPECTED -> stringResource(R.string.google_error_unexpected)
}
