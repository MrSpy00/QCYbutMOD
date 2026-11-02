package com.aegissoft.qcybutmod.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegissoft.qcybutmod.ui.theme.*

@Composable
fun BatteryRing(
    level: Int,
    isCharging: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 6.dp
) {
    val theme = LocalAppThemeColor.current
    val progress = (level.coerceIn(0, 100) / 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = if (level >= 0) progress else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "battProgress"
    )

    val ringColor = when {
        isCharging -> ChargingGold
        level < 0 -> TextTertiary
        level <= 20 -> DangerRed
        level <= 40 -> WarningAmber
        else -> theme.primary
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
                val canvasSize = this.size.minDimension
                val arcSize = Size(canvasSize, canvasSize)
                val topLeft = Offset(
                    (this.size.width - canvasSize) / 2f,
                    (this.size.height - canvasSize) / 2f
                )

                // Background track
                drawArc(
                    color = DarkBorder,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )

                // Active progress arc
                if (level >= 0) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (level >= 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$level",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "%",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    if (isCharging) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "Şarj",
                            tint = ChargingGold,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "--",
                        color = TextTertiary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
