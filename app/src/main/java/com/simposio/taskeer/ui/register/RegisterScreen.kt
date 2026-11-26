package com.simposio.taskeer.ui.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.navigation.LocalBackGuard
import com.simposio.taskeer.ui.theme.Spacing
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private enum class CodesNotice { COPIED, SAVED, SAVE_FAILED }

@Composable
fun RegisterScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onFinished: (email: String?) -> Unit,
    onHaveAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RegisterViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase == RegisterPhase.FINISHED) onFinished(state.finishedEmail)
    }

    when (state.phase) {
        RegisterPhase.FORM ->
            if (state.blocked == RegisterBlock.NOT_SUPPORTED) {
                StatePage(
                    icon = Icons.Filled.Block,
                    title = stringResource(R.string.auth_not_supported_title),
                    message = stringResource(R.string.auth_not_supported_message),
                    modifier = modifier,
                    action = StatePageAction(
                        stringResource(R.string.auth_not_supported_back),
                        viewModel::dismissNotSupported,
                    ),
                )
            } else {
                RegisterFormContent(viewModel, state, onHaveAccount, modifier)
            }
        RegisterPhase.CODES -> CodesContent(viewModel, state, modifier)
        RegisterPhase.CODES_LOST ->
            StatePage(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.register_codes_lost_title),
                message = stringResource(R.string.register_codes_lost_message),
                modifier = modifier,
                action = StatePageAction(
                    stringResource(R.string.register_go_to_login),
                    viewModel::acknowledgeLostOrMissingCodes,
                ),
            )
        RegisterPhase.NO_CODES ->
            StatePage(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.register_no_codes_title),
                message = stringResource(R.string.register_no_codes_message),
                modifier = modifier,
                action = StatePageAction(
                    stringResource(R.string.register_go_to_login),
                    viewModel::acknowledgeLostOrMissingCodes,
                ),
            )
        RegisterPhase.FINISHED -> Unit
    }
}

@Composable
private fun RegisterFormContent(
    viewModel: RegisterViewModel,
    state: RegisterUiState,
    onHaveAccount: () -> Unit,
    modifier: Modifier,
) {
    // Estado sin guardado: persistir el texto en el Bundle sacaría las contraseñas de esta pantalla.
    val passwordState = remember { TextFieldState() }
    val confirmationState = remember { TextFieldState() }

    LaunchedEffect(state.passwordResetCount) {
        passwordState.clearText()
        confirmationState.clearText()
    }
    LaunchedEffect(passwordState, confirmationState) {
        snapshotFlow { passwordState.text.length + confirmationState.text.length }
            .drop(1)
            .filter { it > 0 }
            .collect { viewModel.onPasswordEdited() }
    }
    DisposableEffect(passwordState, confirmationState) {
        onDispose {
            passwordState.clearText()
            confirmationState.clearText()
        }
    }

    val submit = {
        val password = passwordState.text
        val confirmation = confirmationState.text
        viewModel.submit(
            state.displayName,
            state.email,
            CharArray(password.length) { password[it] },
            CharArray(confirmation.length) { confirmation[it] },
        )
    }
    RegisterForm(
        state = state,
        passwordState = passwordState,
        confirmationState = confirmationState,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onSubmit = submit,
        onHaveAccount = onHaveAccount,
        modifier = modifier,
    )
}

@Composable
private fun RegisterForm(
    state: RegisterUiState,
    passwordState: TextFieldState,
    confirmationState: TextFieldState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onHaveAccount: () -> Unit,
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
    val passwordInvalid = state.error == RegisterError.PASSWORD_TOO_SHORT ||
        state.error == RegisterError.PASSWORD_TOO_LONG
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
            label = { Text(stringResource(R.string.register_name_label)) },
            singleLine = true,
            isError = state.error == RegisterError.NAME_INVALID,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.NewUsername + ContentType.EmailAddress },
            label = { Text(stringResource(R.string.register_email_label)) },
            singleLine = true,
            isError = state.error == RegisterError.EMAIL_INVALID,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
        )
        OutlinedSecureTextField(
            state = passwordState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
            label = { Text(stringResource(R.string.register_password_label)) },
            supportingText = { Text(stringResource(R.string.register_password_hint)) },
            isError = passwordInvalid,
            textObfuscationMode = obfuscation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = toggle,
        )
        OutlinedSecureTextField(
            state = confirmationState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
            label = { Text(stringResource(R.string.register_confirm_label)) },
            isError = state.error == RegisterError.PASSWORD_MISMATCH,
            textObfuscationMode = obfuscation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onKeyboardAction = KeyboardActionHandler { if (state.canSubmit) onSubmit() },
            trailingIcon = toggle,
        )
        state.error?.let { error ->
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    text = registerErrorText(error, state.retryMinutes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (error == RegisterError.EMAIL_TAKEN) {
                    TextButton(onClick = onHaveAccount) {
                        Text(stringResource(R.string.register_error_email_taken_action))
                    }
                }
            }
        }
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(Spacing.IconSize), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.register_action))
            }
        }
        TextButton(
            onClick = onHaveAccount,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) { Text(stringResource(R.string.register_have_account)) }
    }
}

@Composable
private fun CodesContent(viewModel: RegisterViewModel, state: RegisterUiState, modifier: Modifier) {
    val codes by viewModel.recoveryCodes.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val backGuard = LocalBackGuard.current
    var showExit by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<CodesNotice?>(null) }

    SecureWindowEffect(enabled = true)
    BackHandler(enabled = true) { showExit = true }
    DisposableEffect(backGuard) {
        backGuard.install { showExit = true }
        onDispose { backGuard.clear() }
    }
    val save = rememberSaveCodesToFile(
        codesProvider = { viewModel.recoveryCodes.value },
        onResult = { notice = if (it == SaveResult.SAVED) CodesNotice.SAVED else CodesNotice.SAVE_FAILED },
    )

    // El portapapeles se vacía antes de pedir la salida: al finalizar, la navegación cancela este ámbito.
    fun leave(action: () -> Unit) {
        scope.launch {
            if (viewModel.uiState.value.copied) {
                try {
                    clipboard.clearCopied()
                } catch (_: SecurityException) {
                    // Sin acceso al portapapeles no hay nada más que hacer.
                }
            }
            action()
        }
    }

    val current = codes
    if (current != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.ScreenPadding),
        ) {
            RecoveryCodesPanel(
                codes = current,
                savedChecked = state.savedChecked,
                canContinue = state.canContinue,
                notice = when (notice) {
                    CodesNotice.COPIED -> stringResource(R.string.register_codes_copied)
                    CodesNotice.SAVED -> stringResource(R.string.register_codes_saved)
                    CodesNotice.SAVE_FAILED -> stringResource(R.string.register_codes_save_failed)
                    null -> null
                },
                onCopy = {
                    scope.launch {
                        clipboard.copySensitive(RecoveryCodesExport.clipboardText(current))
                        viewModel.onCodesCopied()
                        notice = CodesNotice.COPIED
                    }
                },
                onSave = save,
                onSavedCheckedChange = viewModel::onSavedCheckedChange,
                onContinue = { leave(viewModel::confirmCodesSaved) },
            )
        }
    }
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text(stringResource(R.string.register_exit_title)) },
            text = { Text(stringResource(R.string.register_exit_message)) },
            confirmButton = {
                TextButton(onClick = { showExit = false }) { Text(stringResource(R.string.register_exit_stay)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExit = false
                        leave(viewModel::leaveWithoutSaving)
                    },
                ) { Text(stringResource(R.string.register_exit_leave)) }
            },
        )
    }
}

@Composable
private fun registerErrorText(error: RegisterError, retryMinutes: Int?): String = when (error) {
    RegisterError.NAME_INVALID -> stringResource(R.string.register_error_name_invalid)
    RegisterError.EMAIL_INVALID -> stringResource(R.string.register_error_email_invalid)
    RegisterError.PASSWORD_TOO_SHORT -> stringResource(R.string.register_error_password_too_short)
    RegisterError.PASSWORD_TOO_LONG -> stringResource(R.string.register_error_password_too_long)
    RegisterError.PASSWORD_MISMATCH -> stringResource(R.string.register_error_password_mismatch)
    RegisterError.EMAIL_TAKEN -> stringResource(R.string.register_error_email_taken)
    RegisterError.RATE_LIMITED ->
        if (retryMinutes != null) {
            stringResource(R.string.register_error_rate_limited_minutes, retryMinutes)
        } else {
            stringResource(R.string.register_error_rate_limited)
        }
    RegisterError.NETWORK -> stringResource(R.string.register_error_network)
    RegisterError.SERVER -> stringResource(R.string.register_error_server)
    RegisterError.VALIDATION -> stringResource(R.string.register_error_validation)
    RegisterError.UNEXPECTED -> stringResource(R.string.register_error_unexpected)
}
