package com.simposio.taskeer.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

private val CALENDAR_PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

// Lectura y escritura se piden juntas: un solo diálogo y un solo toque.
@Composable
fun rememberCalendarPermissionRequest(onResult: () -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        latest.value()
    }
    return { launcher.launch(CALENDAR_PERMISSIONS) }
}

// El usuario puede conceder desde los ajustes del sistema: al volver a primer plano se relee el permiso.
@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latest.value()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun CalendarPermissionNotice(
    notice: CalendarNotice,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    forWriting: Boolean = false,
) {
    val title = when {
        notice == CalendarNotice.DENIED -> R.string.calendar_permission_denied
        forWriting -> R.string.calendar_write_permission_title
        else -> R.string.calendar_permission_title
    }
    val body = when {
        notice == CalendarNotice.DENIED -> R.string.calendar_permission_denied_body
        forWriting -> R.string.calendar_write_permission_body
        else -> R.string.calendar_permission_body
    }
    val action = if (notice == CalendarNotice.DENIED) R.string.calendar_permission_settings else R.string.calendar_permission_allow
    Card(modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(
            modifier = Modifier.padding(Spacing.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
        ) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = if (notice == CalendarNotice.DENIED) onOpenSettings else onAllow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(action))
            }
        }
    }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return { openAppSettings(context) }
}

@Preview(showBackground = true)
@Composable
private fun CalendarPermissionNoticePreview() {
    TaskeerTheme { CalendarPermissionNotice(CalendarNotice.ASK, onAllow = {}, onOpenSettings = {}) }
}
