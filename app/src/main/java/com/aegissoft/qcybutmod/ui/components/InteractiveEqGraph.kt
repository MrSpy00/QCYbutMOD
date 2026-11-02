package com.aegissoft.qcybutmod.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aegissoft.qcybutmod.core.audio.EqBand
import com.aegissoft.qcybutmod.core.audio.ParametricEq
import com.aegissoft.qcybutmod.ui.theme.DarkBorder
import com.aegissoft.qcybutmod.ui.theme.DarkSurfaceVariant
import com.aegissoft.qcybutmod.ui.theme.LocalAppThemeColor
import kotlin.math.log10

@Composable
fun InteractiveEqGraph(
    bands: List<EqBand>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 160.dp
) {
    val theme = LocalAppThemeColor.current
    val curvePoints = remember(bands) {
        ParametricEq.generateCurvePoints(bands, pointCount = 70)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val width = size.width
            val h = size.height
            val midY = h / 2f

            val minDb = -12f
            val maxDb = 12f
            val rangeDb = maxDb - minDb

            // Horizontal reference grid lines (-6dB, 0dB, +6dB)
            val dbSteps = listOf(-6f, 0f, 6f)
            for (db in dbSteps) {
                val y = h - ((db - minDb) / rangeDb) * h
                drawLine(
                    color = if (db == 0f) DarkBorder.copy(alpha = 0.8f) else DarkBorder.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f
                )
            }

            // Map curve points to canvas coordinates
            val minLog = log10(20f)
            val maxLog = log10(20000f)

            val screenCoords = curvePoints.map { (freq, db) ->
                val normX = (log10(freq) - minLog) / (maxLog - minLog)
                val normY = (db.coerceIn(minDb, maxDb) - minDb) / rangeDb
                Offset(normX * width, h - (normY * h))
            }

            if (screenCoords.size >= 2) {
                // Build filled area under the curve
                val fillPath = Path().apply {
                    moveTo(screenCoords.first().x, midY)
                    lineTo(screenCoords.first().x, screenCoords.first().y)
                    for (i in 1 until screenCoords.size) {
                        val prev = screenCoords[i - 1]
                        val curr = screenCoords[i]
                        val cx = (prev.x + curr.x) / 2f
                        quadraticBezierTo(prev.x, prev.y, cx, (prev.y + curr.y) / 2f)
                    }
                    lineTo(screenCoords.last().x, screenCoords.last().y)
                    lineTo(screenCoords.last().x, midY)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            theme.primary.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h
                    )
                )

                // Draw smoothed line
                val linePath = Path().apply {
                    moveTo(screenCoords.first().x, screenCoords.first().y)
                    for (i in 1 until screenCoords.size) {
                        val prev = screenCoords[i - 1]
                        val curr = screenCoords[i]
                        val cx = (prev.x + curr.x) / 2f
                        quadraticBezierTo(prev.x, prev.y, cx, (prev.y + curr.y) / 2f)
                    }
                    lineTo(screenCoords.last().x, screenCoords.last().y)
                }

                drawPath(
                    path = linePath,
                    color = theme.primary,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
