package com.shni.yxa.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.*
import com.patrykandpatrick.vico.compose.cartesian.data.*
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

// Smooth spring animation for chart data transitions
val chartAnimationSpec: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)

@Composable
fun GraficaMultiLine(datos: List<List<Float>>, colores: List<Color>) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(datos) {
        if (datos.isNotEmpty() && datos[0].isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    val numCores = datos[0].size
                    for (i in 0 until numCores) {
                        series(datos.map { it.getOrElse(i) { 0f } })
                    }
                }
            }
        }
    }

    if (datos.isNotEmpty()) {
        val numCores = datos[0].size
        val lineStyles = (0 until numCores).map { i ->
            val coreColor = colores[i % colores.size]
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(coreColor)),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp),
                areaFill = LineCartesianLayer.AreaFill.single(
                    Fill(coreColor.copy(alpha = 0.08f))
                ),
                interpolator = LineCartesianLayer.Interpolator.cubic(curvature = 0.4f)
            )
        }

        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(lineStyles),
                    rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0)
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberTextComponent(
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberTextComponent(
                        style = TextStyle(color = Color.Transparent)
                    )
                ),
            ),
            modelProducer = modelProducer,
            animationSpec = chartAnimationSpec,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
}

@Composable
fun GraficaSencilla(datos: List<Float>, colorLinea: Color) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(datos) {
        if (datos.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries { series(datos) }
            }
        }
    }

    if (datos.isNotEmpty()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(colorLinea)),
                            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                Fill(colorLinea.copy(alpha = 0.15f))
                            ),
                            interpolator = LineCartesianLayer.Interpolator.cubic(curvature = 0.5f)
                        )
                    ),
                    rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0)
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberTextComponent(
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberTextComponent(
                        style = TextStyle(color = Color.Transparent)
                    )
                ),
            ),
            modelProducer = modelProducer,
            animationSpec = chartAnimationSpec,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
}
