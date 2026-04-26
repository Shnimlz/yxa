package com.shni.yxa.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shni.yxa.Screen
import com.shni.yxa.component.CpuLegend
import com.shni.yxa.component.GraficaMultiLine
import com.shni.yxa.component.GraficaSencilla
import com.shni.yxa.monitor.CpuMonitor
import com.shni.yxa.monitor.GpuMonitor
import com.shni.yxa.monitor.parseMemInfo
import com.shni.yxa.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onNavigate: (Screen) -> Unit) {
    var cpuHistory by remember { mutableStateOf<List<List<Float>>>(emptyList()) }
    var ramHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var gpuHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    val cpuMonitor = remember { CpuMonitor() }
    val gpuMonitor = remember { GpuMonitor() }
    var gpuAvailable by remember { mutableStateOf(true) }
    var topProcess by remember { mutableStateOf("Ninguno") }
    val maxHistory = 40

    // Card entrance animation
    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); cardsVisible = true }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { gpuMonitor.readGpuLoad() }
        if (!gpuMonitor.isAvailable()) gpuAvailable = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val (cpuCores, ram, gpuLoad) = withContext(Dispatchers.IO) {
                    val lines = com.shni.yxa.util.Shell.su("cat /proc/stat /proc/meminfo", silentLog = true)?.lines() ?: emptyList()
                    val cores = if (lines.isNotEmpty()) cpuMonitor.parseStat(lines.filter { it.startsWith("cpu") }) else emptyList()
                    val ramVal = if (lines.isNotEmpty()) parseMemInfo(lines.filter { it.contains(":") }) else 0f
                    val gpu = if (gpuAvailable) try { gpuMonitor.readGpuLoad() } catch (_: Exception) { null } else null
                    Triple(cores, ramVal, gpu)
                }
                val topProc = withContext(Dispatchers.IO) {
                    val topOutput = com.shni.yxa.util.Shell.su("${com.shni.yxa.util.Shell.BUSYBOX} top -b -n 1 -m 5", silentLog = true)
                    var parsedTop = "Ninguno"
                    if (topOutput != null) {
                        val processLine = topOutput.lines().firstOrNull { it.trim().firstOrNull()?.isDigit() == true && !it.contains("root") }
                        if (processLine != null) {
                            val parts = processLine.trim().split("\\s+".toRegex())
                            if (parts.size >= 4) {
                                val name = parts.last()
                                val cpu = parts.firstOrNull { it.contains("%") } ?: parts.getOrNull(parts.size - 2) ?: ""
                                parsedTop = if (cpu.contains("%")) "$name $cpu" else "$name ($cpu%)"
                            }
                        }
                    }
                    parsedTop
                }
                if (cpuCores.isNotEmpty()) cpuHistory = (cpuHistory + listOf(cpuCores)).takeLast(maxHistory)
                ramHistory = (ramHistory + ram).takeLast(maxHistory)
                if (gpuLoad != null) gpuHistory = (gpuHistory + gpuLoad).takeLast(maxHistory)
                topProcess = topProc
            } catch (_: Exception) { }
            delay(750)
        }
    }

    val coloresCores = remember {
        listOf(ChartCore0, ChartCore1, ChartCore2, ChartCore3, ChartCore4, ChartCore5, ChartCore6, ChartCore7, ChartCore8, ChartCore9, ChartCore10, ChartCore11)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            val lastCores = cpuHistory.lastOrNull() ?: emptyList()
            val avgCpu = if (lastCores.isNotEmpty()) lastCores.average().toInt() else 0
            val ramPercent = ramHistory.lastOrNull()?.toInt() ?: 0
            val gpuPercent = gpuHistory.lastOrNull()?.toInt() ?: 0

            // CPU Card
            AnimatedCard(visible = cardsVisible, delay = 0) {
                Card(
                    onClick = { onNavigate(Screen.CpuDetail) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("CPU", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            if (lastCores.isNotEmpty()) PulsingDot(MaterialTheme.colorScheme.primary)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text("Promedio", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AnimatedCounter(avgCpu, MaterialTheme.colorScheme.primary)
                            }
                            AnimatedProgressBar(avgCpu / 100f, MaterialTheme.colorScheme.primary)
                            Text("Proceso más pesado: $topProcess", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (lastCores.isNotEmpty()) CpuLegend(numCores = lastCores.size, colores = coloresCores)
                        GraficaMultiLine(datos = cpuHistory, colores = coloresCores)
                    }
                }
            }

            // RAM Card
            AnimatedCard(visible = cardsVisible, delay = 80) {
                Card(
                    onClick = { onNavigate(Screen.RamDetail) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("RAM", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            if (ramHistory.isNotEmpty()) PulsingDot(MaterialTheme.colorScheme.tertiary)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text("Uso", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AnimatedCounter(ramPercent, MaterialTheme.colorScheme.tertiary)
                            }
                            AnimatedProgressBar(ramPercent / 100f, MaterialTheme.colorScheme.tertiary)
                        }
                        GraficaSencilla(datos = ramHistory, colorLinea = RamCyan)
                    }
                }
            }

            // GPU Card
            AnimatedCard(visible = cardsVisible, delay = 160) {
                Card(
                    onClick = { if (gpuAvailable) onNavigate(Screen.GpuDetail) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("GPU", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            if (gpuAvailable && gpuHistory.isNotEmpty()) PulsingDot(GpuAmber)
                        }
                        if (gpuAvailable) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                    Text("Carga", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    AnimatedCounter(gpuPercent, GpuAmber)
                                }
                                AnimatedProgressBar(gpuPercent / 100f, GpuAmber)
                            }
                            GraficaSencilla(datos = gpuHistory, colorLinea = GpuAmber)
                        } else {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("GPU no disponible", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(110.dp))
        }
    }

// ── Animated counter that smoothly transitions between values ──
@Composable
private fun AnimatedCounter(value: Int, color: Color) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "counter"
    )
    Text(
        "$animatedValue%",
        style = MaterialTheme.typography.headlineSmall,
        color = color,
        fontWeight = FontWeight.Bold
    )
}

// ── Animated progress bar with smooth transitions ──
@Composable
private fun AnimatedProgressBar(progress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress"
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

// ── Staggered card entrance animation ──
@Composable
private fun AnimatedCard(visible: Boolean, delay: Int, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = delay, easing = FastOutSlowInEasing),
        label = "cardAlpha"
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(durationMillis = 400, delayMillis = delay, easing = FastOutSlowInEasing),
        label = "cardSlide"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
        }
    ) { content() }
}

@Composable
fun PulsingDot(color: Color) {
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Box(Modifier.size(8.dp).background(color.copy(alpha = pulseAlpha), CircleShape))
}
