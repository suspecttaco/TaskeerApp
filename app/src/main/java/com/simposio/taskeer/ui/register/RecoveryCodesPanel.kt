package com.simposio.taskeer.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.ui.theme.Spacing

// Sin estado propio: lo reutilizan otras pantallas que muestren códigos de recuperación.
@Composable
fun RecoveryCodesPanel(
    codes: RecoveryCodes,
    savedChecked: Boolean,
    canContinue: Boolean,
    notice: String?,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onSavedCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap)) {
        Text(
            text = stringResource(R.string.register_codes_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.register_codes_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap)) {
            numbered(codes).forEach { (index, code) ->
                val spoken = spokenForm(index, codes.count, code)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { contentDescription = spoken },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
                ) {
                    Text(
                        text = "$index.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = code,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) { Text(stringResource(R.string.register_codes_copy)) }
        OutlinedButton(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) { Text(stringResource(R.string.register_codes_save_file)) }
        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.MinTouchTarget)
                .toggleable(value = savedChecked, role = Role.Checkbox, onValueChange = onSavedCheckedChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
        ) {
            Checkbox(checked = savedChecked, onCheckedChange = null)
            Text(
                text = stringResource(R.string.register_codes_saved_check),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Spacing.InlineGap),
            )
        }
        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
        ) { Text(stringResource(R.string.register_codes_continue)) }
    }
}
