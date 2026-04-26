package com.shni.yxa.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class CurvedBottomBarShape(
    private val radius: Dp = 28.dp,
    private val fabMargin: Dp = 8.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val cutoutRadius = with(density) { (radius + fabMargin).toPx() }
            val center = size.width / 2f
            
            val curveStart = center - cutoutRadius * 1.8f
            val curveEnd = center + cutoutRadius * 1.8f
            
            moveTo(0f, 0f)
            lineTo(curveStart, 0f)
            
            // Suave curva descendente
            cubicTo(
                center - cutoutRadius, 0f,
                center - cutoutRadius * 1.2f, cutoutRadius * 1.1f,
                center, cutoutRadius * 1.1f
            )
            
            // Suave curva ascendente
            cubicTo(
                center + cutoutRadius * 1.2f, cutoutRadius * 1.1f,
                center + cutoutRadius, 0f,
                curveEnd, 0f
            )
            
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            
            close()
        })
    }
}
