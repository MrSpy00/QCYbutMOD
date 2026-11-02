package com.aegissoft.qcybutmod.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aegissoft.qcybutmod.ui.theme.DarkBorder
import com.aegissoft.qcybutmod.ui.theme.DarkSurface
import com.aegissoft.qcybutmod.ui.theme.LocalAppThemeColor

@Composable
fun AegisCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = DarkSurface,
    borderColor: Color = DarkBorder,
    borderWidth: Dp = 1.dp,
    hasGlow: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAppThemeColor.current
    val effectiveBorderColor = if (hasGlow) theme.primary.copy(alpha = 0.6f) else borderColor

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "cardScale"
    )

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.scale(scale),
            shape = shape,
            color = backgroundColor,
            border = BorderStroke(borderWidth, effectiveBorderColor),
            interactionSource = interactionSource
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            border = BorderStroke(borderWidth, effectiveBorderColor)
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}
