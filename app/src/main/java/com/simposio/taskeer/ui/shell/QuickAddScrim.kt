package com.simposio.taskeer.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled

// Velo a medida: el de ModalBottomSheet/Dialog trae ventana propia y robaría el foco y el teclado de la píldora.
// Es pantalla completa a propósito (no un control de 48 dp) y no guarda estado ni registra Atrás.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddScrim(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.pill_scrim_description)
    val animated = rememberAnimationsEnabled()
    AnimatedVisibility(
        visible = visible,
        enter = if (animated) fadeIn() else EnterTransition.None,
        exit = if (animated) fadeOut() else ExitTransition.None,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BottomSheetDefaults.ScrimColor)
                .clickable(interactionSource = null, indication = null, onClickLabel = label, onClick = onDismiss),
        )
    }
}
