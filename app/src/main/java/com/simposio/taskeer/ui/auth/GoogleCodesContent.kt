package com.simposio.taskeer.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simposio.taskeer.R
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

// La sesión ya está iniciada cuando llegan los códigos: se muestran aquí, con la misma custodia que en el registro,
// y el login no navega hasta confirmar o salir sin guardar.
@Composable
internal fun GoogleCodesContent(viewModel: LoginViewModel, state: LoginUiState, modifier: Modifier = Modifier) {
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
