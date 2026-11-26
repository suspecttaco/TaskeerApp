package com.simposio.taskeer.ui.register

import android.content.ClipData
import android.content.Context
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import com.simposio.taskeer.domain.auth.RecoveryCodes
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Literal: `ClipDescription.EXTRA_IS_SENSITIVE` es API 33 y minSdk es 24; en versiones antiguas la clave se ignora.
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

internal suspend fun Clipboard.copySensitive(text: String) {
    val clip = ClipData.newPlainText("", text)
    clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    setClipEntry(ClipEntry(clip))
}

// Mejor esfuerzo: solo funciona con la app en primer plano y puede borrar algo que el usuario copió después.
internal suspend fun Clipboard.clearCopied() {
    setClipEntry(null)
}

@Composable
internal fun SecureWindowEffect(enabled: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

internal enum class SaveResult { SAVED, FAILED }

// Storage Access Framework: el usuario elige el destino, la app no necesita permisos ni deja copias propias.
@Composable
internal fun rememberSaveCodesToFile(
    codesProvider: () -> RecoveryCodes?,
    onResult: (SaveResult) -> Unit,
): () -> Unit {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val codes = codesProvider()
        if (uri != null && codes != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        val bytes = RecoveryCodesExport.fileText(codes).toByteArray(Charsets.UTF_8)
                        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } != null
                    } catch (_: IOException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    }
                }
                onResult(if (saved) SaveResult.SAVED else SaveResult.FAILED)
            }
        }
    }
    return { launcher.launch(RecoveryCodesExport.FILE_NAME) }
}
