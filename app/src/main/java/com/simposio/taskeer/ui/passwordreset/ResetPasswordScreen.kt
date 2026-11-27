package com.simposio.taskeer.ui.passwordreset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.ButtonProgress
import com.simposio.taskeer.ui.components.InlineError
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.theme.Spacing

@Composable
fun ResetPasswordScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onReset: (email: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ResetPasswordViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Estado sin guardado: persistir el texto en el Bundle sacaría contraseñas y código de esta pantalla.
    val emailState = remember { TextFieldState() }
    val codeState = remember { TextFieldState() }
    val passwordState = remember { TextFieldState() }
    val confirmationState = remember { TextFieldState() }

    LaunchedEffect(state.done) {
        if (state.done) onReset(emailState.text.toString().trim())
    }
    LaunchedEffect(state.secretsWiped) {
        passwordState.clearText()
        confirmationState.clearText()
    }
    LaunchedEffect(state.incorrectCount) {
        if (state.incorrectCount > 0) codeState.clearText()
    }
    LaunchedEffect(emailState, codeState, passwordState, confirmationState) {
        fun lengths() = listOf(
            emailState.text.length,
            codeState.text.length,
            passwordState.text.length,
            confirmationState.text.length,
        )
        var previous = lengths()
        snapshotFlow { lengths() }.collect { now ->
            // Vaciar un campo por código no es una edición del usuario.
            val edited = now.indices.any { now[it] != previous[it] && now[it] > 0 }
            previous = now
            if (edited) viewModel.onFieldEdited()
        }
    }
    DisposableEffect(codeState, passwordState, confirmationState) {
        onDispose {
            codeState.clearText()
            passwordState.clearText()
            confirmationState.clearText()
        }
    }

    if (state.block == ResetPasswordBlock.NOT_SUPPORTED) {
        StatePage(
            icon = Icons.Filled.Block,
            title = stringResource(R.string.auth_not_supported_title),
            message = stringResource(R.string.auth_not_supported_message),
            modifier = modifier,
            action = StatePageAction(stringResource(R.string.auth_not_supported_back), viewModel::dismissNotSupported),
        )
        return
    }

    val submit = {
        val password = passwordState.text
        val confirmation = confirmationState.text
        viewModel.submit(
            email = emailState.text.toString(),
            code = codeState.text.toString(),
            newPassword = CharArray(password.length) { password[it] },
            confirmation = CharArray(confirmation.length) { confirmation[it] },
        )
    }
    ResetPasswordForm(
        state = state,
        emailState = emailState,
        codeState = codeState,
        passwordState = passwordState,
        confirmationState = confirmationState,
        onSubmit = submit,
        modifier = modifier,
    )
}

@Composable
private fun ResetPasswordForm(
    state: ResetPasswordUiState,
    emailState: TextFieldState,
    codeState: TextFieldState,
    passwordState: TextFieldState,
    confirmationState: TextFieldState,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val obfuscation = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden
    val toggle: @Composable () -> Unit = {
        ActionIcon(
            icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = stringResource(
                if (passwordVisible) R.string.auth_password_hide else R.string.auth_password_show,
            ),
            onClick = { passwordVisible = !passwordVisible },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        OutlinedTextField(
            state = emailState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
            label = { Text(stringResource(R.string.reset_email_label)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = state.error == ResetPasswordError.EMAIL_INVALID,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
        )
        OutlinedTextField(
            state = codeState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.reset_code_label)) },
            supportingText = { Text(stringResource(R.string.reset_code_hint)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = state.error == ResetPasswordError.CODE_INVALID || state.error == ResetPasswordError.INCORRECT,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
        )
        OutlinedSecureTextField(
            state = passwordState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
            label = { Text(stringResource(R.string.reset_password_label)) },
            supportingText = { Text(stringResource(R.string.reset_password_hint)) },
            isError = state.error == ResetPasswordError.PASSWORD_INVALID,
            textObfuscationMode = obfuscation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = toggle,
        )
        OutlinedSecureTextField(
            state = confirmationState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
            label = { Text(stringResource(R.string.reset_confirm_label)) },
            isError = state.error == ResetPasswordError.PASSWORD_MISMATCH,
            textObfuscationMode = obfuscation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onKeyboardAction = KeyboardActionHandler { if (state.canSubmit) onSubmit() },
            trailingIcon = toggle,
        )
        state.error?.let { error ->
            InlineError(
                message = resetErrorText(error, state.retryMinutes),
            )
        }
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) {
            ButtonProgress(loading = state.isSubmitting, label = stringResource(R.string.reset_action))
        }
    }
}

@Composable
private fun resetErrorText(error: ResetPasswordError, retryMinutes: Int?): String = when (error) {
    ResetPasswordError.EMAIL_INVALID -> stringResource(R.string.reset_error_email_invalid)
    ResetPasswordError.CODE_INVALID -> stringResource(R.string.reset_error_code_invalid)
    ResetPasswordError.PASSWORD_INVALID -> stringResource(R.string.reset_error_password_invalid)
    ResetPasswordError.PASSWORD_MISMATCH -> stringResource(R.string.reset_error_password_mismatch)
    ResetPasswordError.INCORRECT -> stringResource(R.string.reset_error_incorrect)
    ResetPasswordError.RATE_LIMITED ->
        if (retryMinutes != null) {
            stringResource(R.string.reset_error_rate_limited_minutes, retryMinutes)
        } else {
            stringResource(R.string.reset_error_rate_limited)
        }
    ResetPasswordError.NETWORK -> stringResource(R.string.reset_error_network)
    ResetPasswordError.SERVER -> stringResource(R.string.reset_error_server)
    ResetPasswordError.UNEXPECTED -> stringResource(R.string.reset_error_unexpected)
}
