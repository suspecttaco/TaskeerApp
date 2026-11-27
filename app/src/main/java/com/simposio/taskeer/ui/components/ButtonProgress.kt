package com.simposio.taskeer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

// La etiqueta sigue en el árbol (transparente) para que el botón no cambie de ancho ni pierda su nombre para TalkBack.
@Composable
internal fun ButtonProgress(
    loading: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    loadingDescription: String = stringResource(R.string.common_in_progress),
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = label, modifier = Modifier.alpha(if (loading) 0f else 1f))
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(Spacing.IconSize)
                    .semantics { stateDescription = loadingDescription },
                color = LocalContentColor.current,
                strokeWidth = Spacing.SpinnerStroke,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ButtonProgressPreview() {
    TaskeerTheme {
        Button(onClick = {}) { ButtonProgress(loading = true, label = "Entrar") }
    }
}
