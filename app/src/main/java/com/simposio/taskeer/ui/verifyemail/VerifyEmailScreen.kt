package com.simposio.taskeer.ui.verifyemail

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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ButtonProgress
import com.simposio.taskeer.ui.components.InlineError
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.theme.Spacing
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private const val MAX_INPUT_LINES = 4

@Composable
fun VerifyEmailScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VerifyEmailViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Estado sin guardado: el enlace es un secreto efímero y no debe pasar por el Bundle.
    val inputState = remember { TextFieldState() }

    LaunchedEffect(inputState) {
        snapshotFlow { inputState.text.length }
            .drop(1)
            .filter { it > 0 }
            .collect { viewModel.onInputEdited() }
    }
    LaunchedEffect(state.phase) {
        if (state.phase == VerifyEmailPhase.VERIFIED) inputState.clearText()
    }
    DisposableEffect(inputState) { onDispose { inputState.clearText() } }

    if (state.phase == VerifyEmailPhase.VERIFIED) {
        StatePage(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.verify_done_title),
            message = stringResource(R.string.verify_done_message),
            modifier = modifier,
            action = StatePageAction(stringResource(R.string.verify_done_action), onDone),
        )
        return
    }

    val submit = { viewModel.submit(inputState.text.toString()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        OutlinedTextField(
            state = inputState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.verify_input_label)) },
            isError = state.error == VerifyEmailError.INPUT_INVALID,
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = MAX_INPUT_LINES),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            onKeyboardAction = KeyboardActionHandler { if (state.canSubmit) submit() },
        )
        state.error?.let { error ->
            InlineError(
                message = verifyErrorText(error, state.retryMinutes),
            )
        }
        Button(
            onClick = submit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) {
            ButtonProgress(loading = state.isSubmitting, label = stringResource(R.string.verify_action))
        }
    }
}

@Composable
private fun verifyErrorText(error: VerifyEmailError, retryMinutes: Int?): String = when (error) {
    VerifyEmailError.INPUT_INVALID -> stringResource(R.string.verify_error_input_invalid)
    VerifyEmailError.INVALID_OR_EXPIRED -> stringResource(R.string.verify_error_invalid_or_expired)
    VerifyEmailError.RATE_LIMITED ->
        if (retryMinutes != null) {
            stringResource(R.string.verify_error_rate_limited_minutes, retryMinutes)
        } else {
            stringResource(R.string.verify_error_rate_limited)
        }
    VerifyEmailError.NETWORK -> stringResource(R.string.verify_error_network)
    VerifyEmailError.SERVER -> stringResource(R.string.verify_error_server)
    VerifyEmailError.DISABLED -> stringResource(R.string.verify_error_disabled)
    VerifyEmailError.NEEDS_SIGN_IN,
    VerifyEmailError.NOT_SUPPORTED,
    VerifyEmailError.UNEXPECTED,
    -> stringResource(R.string.verify_error_unexpected)
}
