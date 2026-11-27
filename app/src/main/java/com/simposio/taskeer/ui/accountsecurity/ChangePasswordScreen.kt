package com.simposio.taskeer.ui.accountsecurity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedSecureTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
fun ChangePasswordScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onDone: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChangePasswordViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Estado sin guardado: persistir el texto en el Bundle sacaría las contraseñas de esta pantalla.
    val currentState = remember { TextFieldState() }
    val newState = remember { TextFieldState() }
    val confirmationState = remember { TextFieldState() }

    LaunchedEffect(state.secretsWiped) {
        currentState.clearText()
        newState.clearText()
        confirmationState.clearText()
    }
    LaunchedEffect(currentState, newState, confirmationState) {
        fun lengths() = listOf(currentState.text.length, newState.text.length, confirmationState.text.length)
        var previous = lengths()
        snapshotFlow { lengths() }.collect { now ->
            // Vaciar un campo por código no es una edición del usuario.
            val edited = now.indices.any { now[it] != previous[it] && now[it] > 0 }
            previous = now
            if (edited) viewModel.onFieldEdited()
        }
    }
    DisposableEffect(currentState, newState, confirmationState) {
        onDispose {
            currentState.clearText()
            newState.clearText()
            confirmationState.clearText()
        }
    }

    state.done?.let { done ->
        StatePage(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.chpw_title),
            message = stringResource(
                if (done == ChangePasswordDone.CHANGED) R.string.chpw_done_changed else R.string.chpw_done_signed_out,
            ),
            modifier = modifier,
            action = StatePageAction(stringResource(R.string.chpw_done_action), onDone),
        )
        return
    }

    val submit = {
        val current = currentState.text
        val new = newState.text
        val confirmation = confirmationState.text
        viewModel.submit(
            CharArray(current.length) { current[it] },
            CharArray(new.length) { new[it] },
            CharArray(confirmation.length) { confirmation[it] },
        )
    }
    ChangePasswordForm(state, currentState, newState, confirmationState, submit, onSignIn, modifier)
}

@Composable
private fun ChangePasswordForm(
    state: ChangePasswordUiState,
    currentState: TextFieldState,
    newState: TextFieldState,
    confirmationState: TextFieldState,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
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
    val currentInvalid = state.error == ChangePasswordError.CURRENT_INVALID ||
        state.error == ChangePasswordError.CURRENT_INCORRECT
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        OutlinedSecureTextField(
            state = currentState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            label = { Text(stringResource(R.string.chpw_current_label)) },
            isError = currentInvalid,
            textObfuscationMode = obfuscation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = toggle,
        )
        OutlinedSecureTextField(
            state = newState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
            label = { Text(stringResource(R.string.chpw_new_label)) },
            supportingText = { Text(stringResource(R.string.chpw_new_hint)) },
            isError = state.error == ChangePasswordError.NEW_INVALID,
            textObfuscationMode = obfuscation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = toggle,
        )
        OutlinedSecureTextField(
            state = confirmationState,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
            label = { Text(stringResource(R.string.chpw_confirm_label)) },
            isError = state.error == ChangePasswordError.MISMATCH,
            textObfuscationMode = obfuscation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onKeyboardAction = KeyboardActionHandler { if (state.canSubmit) onSubmit() },
            trailingIcon = toggle,
        )
        state.error?.let { error ->
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                InlineError(
                    message = changeErrorText(error, state.retryMinutes),
                )
                if (error == ChangePasswordError.NEEDS_SIGN_IN) {
                    TextButton(onClick = onSignIn) { Text(stringResource(R.string.chpw_error_needs_sign_in_action)) }
                }
            }
        }
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) {
            ButtonProgress(loading = state.isSubmitting, label = stringResource(R.string.chpw_action))
        }
    }
}

@Composable
private fun changeErrorText(error: ChangePasswordError, retryMinutes: Int?): String = when (error) {
    ChangePasswordError.CURRENT_INVALID -> stringResource(R.string.chpw_error_current_invalid)
    ChangePasswordError.NEW_INVALID -> stringResource(R.string.chpw_error_new_invalid)
    ChangePasswordError.MISMATCH -> stringResource(R.string.chpw_error_mismatch)
    ChangePasswordError.CURRENT_INCORRECT -> stringResource(R.string.chpw_error_current_incorrect)
    ChangePasswordError.NEEDS_SIGN_IN -> stringResource(R.string.chpw_error_needs_sign_in)
    ChangePasswordError.NO_LOCAL_PASSWORD -> stringResource(R.string.chpw_error_no_local_password)
    ChangePasswordError.CONCURRENT_CHANGE -> stringResource(R.string.chpw_error_concurrent)
    ChangePasswordError.CHECK_DATA -> stringResource(R.string.chpw_error_check_data)
    ChangePasswordError.RATE_LIMITED ->
        if (retryMinutes != null) {
            stringResource(R.string.chpw_error_rate_limited_minutes, retryMinutes)
        } else {
            stringResource(R.string.chpw_error_rate_limited)
        }
    ChangePasswordError.NETWORK -> stringResource(R.string.chpw_error_network)
    ChangePasswordError.SERVER -> stringResource(R.string.chpw_error_server)
    ChangePasswordError.UNEXPECTED -> stringResource(R.string.chpw_error_unexpected)
}
