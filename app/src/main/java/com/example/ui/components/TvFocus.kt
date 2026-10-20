package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.GoldPrimary

/**
 * Modificador universal para elementos interactivos en Android TV / TV Box:
 * - Enfocable y activable con OK/Enter/DPAD_CENTER mediante Modifier.clickable
 * - Borde dorado #CFA65C de 3dp al tener foco
 * - Ligera ampliación (scale 1.05) con animación suave
 * - Eleva zIndex para no ser cubierto por vecinos en cuadrículas o listas
 * - Soporta asignación de FocusRequester para foco inicial
 * - Compatible al 100% con clics táctiles y ratón
 */
@Composable
fun Modifier.tvFocusable(
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    scaleOnFocus: Float = 1.05f,
    borderWidth: Dp = 3.dp,
    borderColor: Color = GoldPrimary,
    enabled: Boolean = true,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1.0f,
        label = "tv_focus_scale"
    )

    return this
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { state ->
            isFocused = state.isFocused
            onFocusChanged?.invoke(state.isFocused)
        }
        .scale(scale)
        .then(if (isFocused) Modifier.zIndex(10f) else Modifier)
        .then(
            if (isFocused) Modifier.border(borderWidth, borderColor, shape)
            else Modifier
        )
        .clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
}
