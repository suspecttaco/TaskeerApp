package com.simposio.taskeer.ui.accountsecurity

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
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
import com.simposio.taskeer.ui.register.RecoveryCodesExport
import com.simposio.taskeer.ui.register.RecoveryCodesPanel
import com.simposio.taskeer.ui.register.SaveResult
import com.simposio.taskeer.ui.register.SecureWindowEffect
import com.simposio.taskeer.ui.register.clearCopied
import com.simposio.taskeer.ui.register.copySensitive
import com.simposio.taskeer.ui.register.rememberSaveCodesToFile
import com.simposio.taskeer.ui.theme.Spacing
import kotlinx.coroutines.launch

private enum class CodesNotice { COPIED, SAVED, SAVE_FAILED }

@Composable
fun RegenerateCodesScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onFinished: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RegenerateCodesViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase == RegenerateCodesPhase.FINISHED) onFinished()
    }

    when (state.phase) {
        RegenerateCodesPhase.PASSWORD -> PasswordContent(viewModel, state, onSignIn, modifier)
        RegenerateCodesPhase.CODES -> CodesContent(viewModel, state, modifier)
        RegenerateCodesPhase.CODES_LOST ->
            StatePage(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.regen_codes_lost_title),
                message = stringResource(R.string.regen_codes_lost_message),
                modifier = modifier,
                action = StatePageAction(
                    stringResource(R.string.regen_back_action),
                    viewModel::acknowledgeLostOrMissingCodes,
                ),
            )
        RegenerateCodesPhase.NO_CODES ->
            StatePage(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.regen_no_codes_title),
                message = stringResource(R.string.regen_no_codes_message),
                modifier = modifier,
                action = StatePageAction(
                    stringResource(R.string.regen_back_action),
                    viewModel::acknowledgeLostOrMissingCodes,
                ),
            )
        RegenerateCodesPhase.FINISHED -> Unit
    }
}

@Composable
private fun PasswordContent(
    viewModel: RegenerateCodesViewModel,
    state: RegenerateCodesUiState,
    onSignIn: () -> Unit,
    modifier: Modifier,
) {
    // Estado sin guardado: persistir el texto en el Bundle sacaría la contraseña de esta pantalla.
    val passwordState = remember { TextFieldState() }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.secretsWiped) { passwordState.clearText() }
    LaunchedEffect(passwordState) {
        var previous = passwordState.text.length
        snapshotFlow { passwordState.text.length }.collect { now ->
            // Vaciar el campo por código no es una edición del usuario.
            val edited = now != previous && now > 0
            previous = now
            if (edited) viewModel.onFieldEdited()
        }
    }
    DisposableEffect(passwordState) { onDispose { passwordState.clearText() } }

    val submit = {
        val text = passwordState.text
        viewModel.submit(CharArray(text.length) { text[it] })
    }
    val currentInvalid = state.error == RegenerateCodesError.CURRENT_INVALID ||
        state.error == RegenerateCodesError.CURRENT_INCORRECT
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        Text(
            text = stringResource(R.string.regen_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedSecureTextField(
            state = passwordState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            label = { Text(stringResource(R.string.regen_current_label)) },
            isError = currentInvalid,
            textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onKeyboardAction = KeyboardActionHandler { if (state.canSubmit) submit() },
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
        state.error?.let { error ->
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    text = regenerateErrorText(error, state.retryMinutes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (error == RegenerateCodesError.NEEDS_SIGN_IN) {
                    TextButton(onClick = onSignIn) { Text(stringResource(R.string.regen_error_needs_sign_in_action)) }
                }
            }
        }
        Button(
            onClick = submit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(Spacing.IconSize), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.regen_action))
            }
        }
    }
}

@Composable
private fun CodesContent(viewModel: RegenerateCodesViewModel, state: RegenerateCodesUiState, modifier: Modifier) {
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
private fun regenerateErrorText(error: RegenerateCodesError, retryMinutes: Int?): String = when (error) {
    RegenerateCodesError.CURRENT_INVALID -> stringResource(R.string.regen_error_current_invalid)
    RegenerateCodesError.CURRENT_INCORRECT -> stringResource(R.string.regen_error_current_incorrect)
    RegenerateCodesError.NEEDS_SIGN_IN -> stringResource(R.string.regen_error_needs_sign_in)
    RegenerateCodesError.NO_LOCAL_PASSWORD -> stringResource(R.string.regen_error_no_local_password)
    RegenerateCodesError.CHECK_DATA -> stringResource(R.string.regen_error_check_data)
    RegenerateCodesError.RATE_LIMITED ->
        if (retryMinutes != null) {
            stringResource(R.string.regen_error_rate_limited_minutes, retryMinutes)
        } else {
            stringResource(R.string.regen_error_rate_limited)
        }
    RegenerateCodesError.NETWORK -> stringResource(R.string.regen_error_network)
    RegenerateCodesError.SERVER -> stringResource(R.string.regen_error_server)
    RegenerateCodesError.UNEXPECTED -> stringResource(R.string.regen_error_unexpected)
}
