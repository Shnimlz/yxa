package com.shni.yxa.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import com.shni.yxa.util.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.shni.yxa.data.GameProfileRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSettingsScreen(packageName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GameProfileRepository(context) }
    val profile = remember { repo.get(packageName) } ?: run { onBack(); return }

    var perfMode by remember { mutableIntStateOf(profile.perfMode) }
    var refreshRate by remember { mutableIntStateOf(profile.refreshRate) }
    var resolution by remember { mutableIntStateOf(profile.resolution) }
    var touchSensitivity by remember { mutableStateOf(profile.touchSensitivity) }
    var wifiLowLatency by remember { mutableStateOf(profile.wifiLowLatency) }
    var gpuBoost by remember { mutableStateOf(profile.gpuBoost) }
    var dndEnabled by remember { mutableStateOf(profile.dndEnabled) }
    var graphicsApi by remember { mutableStateOf(profile.graphicsApi) }
    var tcpCongestion by remember { mutableStateOf(profile.tcpCongestion) }
    var advancedNet by remember { mutableStateOf(profile.advancedNet) }
    var aggressiveClear by remember { mutableStateOf(profile.aggressiveClear) }
    var disableZram by remember { mutableStateOf(profile.disableZram) }
    var killBackground by remember { mutableStateOf(profile.killBackground) }
    var angleSupported by remember { mutableStateOf(false) }
    var availableTcpAlgos by remember { mutableStateOf(listOf("cubic", "bbr", "reno")) }
    var saved by remember { mutableStateOf(false) }

    fun save() {
        repo.save(profile.copy(perfMode = perfMode, refreshRate = refreshRate, resolution = resolution,
            touchSensitivity = touchSensitivity, wifiLowLatency = wifiLowLatency, gpuBoost = gpuBoost, dndEnabled = dndEnabled, graphicsApi = graphicsApi, tcpCongestion = tcpCongestion, advancedNet = advancedNet, aggressiveClear = aggressiveClear, disableZram = disableZram, killBackground = killBackground))
        saved = true
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val prop = Shell.su("getprop ro.gfx.angle.supported", silentLog = true)?.trim()
            val list = Shell.su("settings get global angle_gl_driver_selection_pkgs", silentLog = true)
            angleSupported = prop == "true" || list != null
            
            val tcpAlgosStr = Shell.su("cat /proc/sys/net/ipv4/tcp_available_congestion_control", silentLog = true)?.trim()
            if (!tcpAlgosStr.isNullOrBlank()) {
                val algos = tcpAlgosStr.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val supportedTargets = listOf("cubic", "bbr", "reno").filter { it in algos }
                if (supportedTargets.isNotEmpty()) {
                    availableTcpAlgos = supportedTargets
                    // Si el algoritmo guardado ya no es válido, usa el primero disponible
                    if (tcpCongestion !in supportedTargets) {
                        tcpCongestion = supportedTargets.first()
                        save()
                    }
                }
            }
        }
    }

    val perfModes = listOf("Ahorro", "Equilibrado", "Turbo")
    val apiModes = listOf("default" to "Sistema", "native" to "OpenGL", "angle" to "Vulkan (ANGLE)")
    val wm = remember { context.getSystemService(WindowManager::class.java) }
    val rates = remember { wm?.defaultDisplay?.supportedModes?.map { it.refreshRate.toInt() }?.distinct()?.sorted() ?: listOf(60) }
    val resolutions = listOf(100 to "Original", 75 to "75%", 50 to "50%")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = { IconButton(onClick = { save(); onBack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.primary, navigationIconContentColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            if (saved) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Configuración guardada", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Performance mode
            SettingSection("Modo de rendimiento", Icons.Default.BatterySaver) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    perfModes.forEachIndexed { i, label ->
                        SegmentedButton(selected = perfMode == i, onClick = { perfMode = i; save() }, shape = SegmentedButtonDefaults.itemShape(i, perfModes.size)) { Text(label, fontSize = 13.sp) }
                    }
                }
            }

            // Refresh rate
            SettingSection("Frecuencia de pantalla", Icons.Default.Smartphone) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Text("${refreshRate} Hz", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    rates.forEachIndexed { i, hz ->
                        SegmentedButton(selected = refreshRate == hz, onClick = { refreshRate = hz; save() }, shape = SegmentedButtonDefaults.itemShape(i, rates.size)) { Text("${hz}", fontSize = 13.sp) }
                    }
                }
            }

            // Resolution
            SettingSection("Resolución de pantalla", Icons.Default.AspectRatio) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    resolutions.forEachIndexed { i, (pct, label) ->
                        SegmentedButton(selected = resolution == pct, onClick = { resolution = pct; save() }, shape = SegmentedButtonDefaults.itemShape(i, resolutions.size)) { Text(label, fontSize = 13.sp) }
                    }
                }
            }

            // Graphics API
            SettingSection("API Gráfica", Icons.Default.GraphicEq) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    apiModes.forEachIndexed { i, (api, label) ->
                        SegmentedButton(
                            selected = graphicsApi == api, 
                            onClick = { graphicsApi = api; save() }, 
                            shape = SegmentedButtonDefaults.itemShape(i, apiModes.size),
                            enabled = angleSupported
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }
                if (!angleSupported) {
                    Text("Tu dispositivo no soporta el cambio forzado de API gráfica.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // Toggles
            ToggleSetting("Sensibilidad al tacto", "Mejora respuesta táctil", Icons.Default.TouchApp, touchSensitivity) { touchSensitivity = it; save() }
            // Latencia Wi-Fi Expandible
            var wifiExpanded by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Latencia Wi-Fi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("TCP low latency y ajustes avanzados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = wifiLowLatency, onCheckedChange = { wifiLowLatency = it; save() })
                        IconButton(onClick = { wifiExpanded = !wifiExpanded }) {
                            Icon(if (wifiExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Avanzado")
                        }
                    }
                    AnimatedVisibility(visible = wifiExpanded) {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Text("Algoritmo de Congestión TCP", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                val labels = mapOf("cubic" to "Cubic", "bbr" to "BBR", "reno" to "Reno")
                                availableTcpAlgos.forEachIndexed { i, algo ->
                                    val label = labels[algo] ?: algo.replaceFirstChar { it.uppercase() }
                                    SegmentedButton(selected = tcpCongestion == algo, onClick = { tcpCongestion = algo; save() }, shape = SegmentedButtonDefaults.itemShape(i, availableTcpAlgos.size)) {
                                        Text(label, fontSize = 12.sp)
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Reducción de Buffer (Extreme Ping)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("Previene lag spikes (Bloatbuffer) reduciendo memoria TCP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
                                }
                                Switch(checked = advancedNet, onCheckedChange = { advancedNet = it; save() })
                            }
                        }
                    }
                }
            }
            ToggleSetting("GPU Boost", "Bloquear GPU a máxima frecuencia", Icons.Default.Speed, gpuBoost) { gpuBoost = it; save() }
            ToggleSetting("No molestar", "Bloquear notificaciones", Icons.Default.NotificationsOff, dndEnabled) { dndEnabled = it; save() }

            // Extreme Memory Management
            SettingSection("Gestión Extrema de Memoria", Icons.Default.Memory) {
                ToggleSetting("Limpieza Agresiva", "Limpiar caché temporal (Drop Caches)", Icons.Default.CleaningServices, aggressiveClear) { aggressiveClear = it; save() }
                ToggleSetting("Deshabilitar ZRAM", "Evitar compresión RAM al jugar (Swappiness=10)", Icons.Default.Memory, disableZram) { disableZram = it; save() }
                ToggleSetting("Auto-Kill Apps", "Cerrar procesos ocultos para liberar RAM", Icons.Default.Close, killBackground) { killBackground = it; save() }
            }
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun SettingSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun ToggleSetting(title: String, desc: String, icon: ImageVector, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
        }
    }
}
