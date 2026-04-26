package com.shni.yxa.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.shni.yxa.util.BootScriptManager
import com.shni.yxa.util.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ClusterInfo(
    val name: String, val representativeCpu: Int, val cores: List<Int>,
    val availableFrequencies: List<Long>, val availableGovernors: List<String>,
    val currentMaxFreq: Long, val currentMinFreq: Long, val currentGovernor: String,
)

// ── CPU Profiles ──
private data class CpuProfile(
    val name: String, val icon: ImageVector, val desc: String,
    val littleFreqRatio: Float, val bigFreqRatio: Float, val governor: String,
)

private val basicProfiles = listOf(
    CpuProfile("Ahorro", Icons.Default.BatterySaver, "Frecuencias mínimas, governor powersave", 0f, 0f, "powersave"),
    CpuProfile("Equilibrado", Icons.Default.Balance, "Frecuencias medias, governor schedutil", 0.5f, 0.5f, "schedutil"),
    CpuProfile("Rendimiento", Icons.Default.RocketLaunch, "Frecuencias máximas, governor performance", 1f, 1f, "performance"),
)
private val intermediateProfiles = listOf(
    CpuProfile("Gaming", Icons.Default.SportsEsports, "Little medio, Big/Prime al máximo", 0.5f, 1f, "performance"),
    CpuProfile("Multimedia", Icons.Default.PhotoCamera, "Little bajo, Big alto, schedutil", 0.25f, 0.75f, "schedutil"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuDetailScreen(onBack: () -> Unit) {
    var clusters by remember { mutableStateOf<List<ClusterInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    // Advanced toggles
    var cpuBoostAvailable by remember { mutableStateOf(false) }
    var cpuBoostEnabled by remember { mutableStateOf(false) }
    var boostMs by remember { mutableIntStateOf(0) }
    var thermalAvailable by remember { mutableStateOf(false) }
    var thermalEnabled by remember { mutableStateOf(false) }
    var appliedProfile by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var applyOnBoot by remember { mutableStateOf(BootScriptManager.isCpuEnabled(context)) }
    val tabs = listOf("Básico", "Intermedio", "Avanzado")

    LaunchedEffect(Unit) {
        try {
            val (c, boost, boostEn, bMs, thermal, thermalEn) = withContext(Dispatchers.IO) {
                val cl = detectClusters()
                val bAvail = Shell.su("cat /sys/module/cpu_boost/parameters/input_boost_enabled", silentLog = true) != null
                val bEn = Shell.su("cat /sys/module/cpu_boost/parameters/input_boost_enabled", silentLog = true)?.trim() == "1"
                val bm = Shell.su("cat /sys/module/cpu_boost/parameters/input_boost_ms", silentLog = true)?.trim()?.toIntOrNull() ?: 0
                val tAvail = Shell.su("cat /sys/module/msm_thermal/parameters/enabled", silentLog = true) != null
                val tEn = Shell.su("cat /sys/module/msm_thermal/parameters/enabled", silentLog = true)?.trim()?.let { it == "Y" || it == "1" } ?: false
                SixTuple(cl, bAvail, bEn, bm, tAvail, tEn)
            }
            clusters = c; cpuBoostAvailable = boost; cpuBoostEnabled = boostEn
            boostMs = bMs; thermalAvailable = thermal; thermalEnabled = thermalEn
        } catch (e: Exception) { errorMessage = "Error: ${e.message}" }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CPU", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Leyendo configuración CPU…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                errorMessage?.let { msg ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                        Text(msg, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                ApplyOnBootToggle(
                    checked = applyOnBoot,
                    onCheckedChange = {
                        applyOnBoot = it
                        BootScriptManager.setCpuEnabled(context, it)
                    }
                )

                // Tab selector
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(label, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                appliedProfile?.let { name ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                        Text("✓ Perfil \"$name\" aplicado", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }

                when (selectedTab) {
                    0 -> BasicTab(basicProfiles, clusters, scope, onApplied = { name ->
                        appliedProfile = name
                        scope.launch { clusters = withContext(Dispatchers.IO) { detectClusters() } }
                    })
                    1 -> IntermediateTab(intermediateProfiles, clusters, scope, onApplied = { name ->
                        appliedProfile = name
                        scope.launch { clusters = withContext(Dispatchers.IO) { detectClusters() } }
                    })
                    2 -> AdvancedTab(cpuBoostAvailable, cpuBoostEnabled, boostMs, thermalAvailable, thermalEnabled, clusters, scope, context,
                        onBoostToggle = { en ->
                            cpuBoostEnabled = en
                            scope.launch(Dispatchers.IO) { 
                                val cmd = "echo ${if (en) "1" else "0"} > /sys/module/cpu_boost/parameters/input_boost_enabled"
                                Shell.su(cmd)
                                BootScriptManager.putCpuCommand(context, "cpu_boost_enabled", cmd)
                            }
                        },
                        onBoostMsChange = { ms ->
                            boostMs = ms
                            scope.launch(Dispatchers.IO) { 
                                val cmd = "echo $ms > /sys/module/cpu_boost/parameters/input_boost_ms"
                                Shell.su(cmd)
                                BootScriptManager.putCpuCommand(context, "cpu_boost_ms", cmd)
                            }
                        },
                        onThermalToggle = { en ->
                            thermalEnabled = en
                            scope.launch(Dispatchers.IO) { 
                                val cmd = "echo ${if (en) "Y" else "N"} > /sys/module/msm_thermal/parameters/enabled"
                                Shell.su(cmd)
                                BootScriptManager.putCpuCommand(context, "msm_thermal_enabled", cmd)
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun BasicTab(profiles: List<CpuProfile>, clusters: List<ClusterInfo>, scope: kotlinx.coroutines.CoroutineScope, onApplied: (String) -> Unit) {
    val context = LocalContext.current
    profiles.forEach { profile -> ProfileCard(profile, clusters, scope, context, onApplied) }
}

@Composable
private fun IntermediateTab(profiles: List<CpuProfile>, clusters: List<ClusterInfo>, scope: kotlinx.coroutines.CoroutineScope, onApplied: (String) -> Unit) {
    val context = LocalContext.current
    profiles.forEach { profile -> ProfileCard(profile, clusters, scope, context, onApplied) }
    Spacer(Modifier.height(8.dp))
    Text("Control por cluster", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    clusters.forEach { cluster ->
        var current by remember(cluster) { mutableStateOf(cluster) }
        ClusterCard(current, context,
            onMaxFreqChange = { f -> scope.launch(Dispatchers.IO) { writeToCores(context, current.cores, "scaling_max_freq", "$f") }; current = current.copy(currentMaxFreq = f) },
            onMinFreqChange = { f -> scope.launch(Dispatchers.IO) { writeToCores(context, current.cores, "scaling_min_freq", "$f") }; current = current.copy(currentMinFreq = f) },
            onGovernorChange = { g -> scope.launch(Dispatchers.IO) { writeToCores(context, current.cores, "scaling_governor", g) }; current = current.copy(currentGovernor = g) }
        )
    }
}

@Composable
private fun AdvancedTab(boostAvail: Boolean, boostEn: Boolean, boostMs: Int, thermalAvail: Boolean, thermalEn: Boolean, clusters: List<ClusterInfo>, scope: kotlinx.coroutines.CoroutineScope, context: android.content.Context,
    onBoostToggle: (Boolean) -> Unit, onBoostMsChange: (Int) -> Unit, onThermalToggle: (Boolean) -> Unit) {
    var cpusetsApplied by remember { mutableStateOf(false) }
    
    // CPUsets Card
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prioridad de Proceso (CPUsets)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text("Asigna los procesos en primer plano (juegos) exclusivamente a los núcleos de mayor rendimiento.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val bigPrimeCores = if (clusters.size > 1) {
                            clusters.drop(1).flatMap { it.cores }.joinToString(",")
                        } else {
                            clusters.firstOrNull()?.cores?.joinToString(",") ?: "0"
                        }
                        val cmd = "echo $bigPrimeCores > /dev/cpuset/top-app/cpus"
                        Shell.su("$cmd 2>/dev/null")
                        BootScriptManager.putCpuCommand(context, "cpuset_top_app", cmd)
                        cpusetsApplied = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (cpusetsApplied) "✓ Aplicado en top-app" else "Forzar Juego en Núcleos de Alto Rendimiento", fontWeight = FontWeight.Bold)
            }
        }
    }
    
    if (!boostAvail && !thermalAvail) {
        return
    }
    if (boostAvail) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CPU Boost", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Input Boost", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = boostEn, onCheckedChange = onBoostToggle, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
                }
                Text("Duración: ${boostMs}ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = boostMs.toFloat(), onValueChange = { onBoostMsChange(it.toInt()) }, valueRange = 0f..500f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh))
            }
        }
    }
    if (thermalAvail) {
        var showDialog by remember { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Thermal Throttle", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Desactivar puede causar sobrecalentamiento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Throttling térmico", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = thermalEn, onCheckedChange = { if (!it) showDialog = true else onThermalToggle(true) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF5252)))
                }
            }
        }
        if (showDialog) {
            AlertDialog(onDismissRequest = { showDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Advertencia") 
                    }
                },
                text = { Text("Desactivar el throttling térmico puede dañar tu dispositivo por sobrecalentamiento. ¿Continuar?") },
                confirmButton = { TextButton(onClick = { showDialog = false; onThermalToggle(false) }) { Text("Desactivar", color = Color(0xFFFF5252)) } },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun ProfileCard(profile: CpuProfile, clusters: List<ClusterInfo>, scope: kotlinx.coroutines.CoroutineScope, context: android.content.Context, onApplied: (String) -> Unit) {
    Card(
        onClick = {
            scope.launch(Dispatchers.IO) {
                applyProfile(profile, clusters, context)
            }
            onApplied(profile.name)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(profile.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column {
                Text(profile.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(profile.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun applyProfile(profile: CpuProfile, clusters: List<ClusterInfo>, context: android.content.Context) {
    clusters.forEachIndexed { i, cluster ->
        val ratio = if (i == 0) profile.littleFreqRatio else profile.bigFreqRatio
        val freqs = cluster.availableFrequencies.sorted()
        if (freqs.isNotEmpty()) {
            val idx = (ratio * (freqs.size - 1)).toInt().coerceIn(0, freqs.size - 1)
            val freq = freqs[idx]
            writeToCores(context, cluster.cores, "scaling_max_freq", "$freq")
            if (ratio == 0f) writeToCores(context, cluster.cores, "scaling_min_freq", "${freqs.first()}")
            else writeToCores(context, cluster.cores, "scaling_min_freq", "${freqs.first()}")
        }
        val gov = if (cluster.availableGovernors.contains(profile.governor)) profile.governor
        else cluster.availableGovernors.firstOrNull { it == "schedutil" } ?: cluster.availableGovernors.firstOrNull() ?: return@forEachIndexed
        writeToCores(context, cluster.cores, "scaling_governor", gov)
    }
}

// ── Composables for per-cluster control (reused in Intermediate tab) ──

@Composable
private fun ClusterCard(cluster: ClusterInfo, context: android.content.Context, onMaxFreqChange: (Long) -> Unit, onMinFreqChange: (Long) -> Unit, onGovernorChange: (String) -> Unit) {
    var liveFreq by remember { mutableStateOf<Long?>(null) }
    var coreOnlineState by remember { mutableStateOf(cluster.cores.associateWith { true }) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(cluster.cores) {
        withContext(Dispatchers.IO) {
            while (true) {
                val f = Shell.su("cat /sys/devices/system/cpu/cpu${cluster.representativeCpu}/cpufreq/scaling_cur_freq", silentLog = true)?.trim()?.toLongOrNull()
                if (f != null) liveFreq = f
                
                val newOnlineState = cluster.cores.associateWith { core ->
                    val o = Shell.su("cat /sys/devices/system/cpu/cpu$core/online", silentLog = true)?.trim()
                    o == "1" || o == null || o.isEmpty() // CPU0 often doesn't have an online file or it's implicitly online
                }
                coreOnlineState = newOnlineState
                
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(cluster.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Control individual de núcleos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                liveFreq?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(8.dp)) {
                        Text(formatFrequency(it), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cluster.cores.forEach { core ->
                    val isOnline = coreOnlineState[core] ?: true
                    FilterChip(
                        selected = isOnline,
                        onClick = {
                            val targetState = if (isOnline) "0" else "1"
                            scope.launch(Dispatchers.IO) {
                                val cmd = "echo $targetState > /sys/devices/system/cpu/cpu$core/online"
                                Shell.su(cmd)
                                // NOTE: Core hotplugging is NOT persisted on boot (bootloop risk)
                                val verify = Shell.su("cat /sys/devices/system/cpu/cpu$core/online")?.trim()
                                if (verify == targetState) {
                                    coreOnlineState = coreOnlineState.toMutableMap().apply { put(core, targetState == "1") }
                                }
                            }
                        },
                        label = { Text("Core $core", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF4CAF50),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = isOnline, 
                            borderColor = Color.Transparent, selectedBorderColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            if (cluster.availableFrequencies.isNotEmpty()) {
                FrequencySelector("Frecuencia máxima", cluster.availableFrequencies.sorted(), cluster.currentMaxFreq, onMaxFreqChange)
                FrequencySelector("Frecuencia mínima", cluster.availableFrequencies.sortedDescending(), cluster.currentMinFreq, onMinFreqChange)
            }
            if (cluster.availableGovernors.isNotEmpty()) GovernorSelector(cluster.availableGovernors, cluster.currentGovernor, onGovernorChange)
        }
    }
}

@Composable
private fun FrequencySelector(label: String, frequencies: List<Long>, currentFreq: Long, onFreqChange: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(formatFrequency(currentFreq), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        val idx = frequencies.indexOf(currentFreq).coerceAtLeast(0)
        if (frequencies.size > 1) {
            Slider(value = idx.toFloat(), onValueChange = { onFreqChange(frequencies[it.toInt().coerceIn(0, frequencies.size - 1)]) },
                valueRange = 0f..(frequencies.size - 1).toFloat(), steps = (frequencies.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatFrequency(frequencies.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatFrequency(frequencies.last()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GovernorSelector(govs: List<String>, current: String, onChange: (String) -> Unit) {
    var showTunables by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tunables by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Governor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { 
                scope.launch(Dispatchers.IO) {
                    val out = Shell.su("ls /sys/devices/system/cpu/cpufreq/$current/", silentLog = true) ?: ""
                    val t = mutableMapOf<String, String>()
                    out.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { file ->
                        val v = Shell.su("cat /sys/devices/system/cpu/cpufreq/$current/$file 2>/dev/null", silentLog = true)?.trim()
                        if (v != null && v.toLongOrNull() != null) {
                            t[file] = v
                        }
                    }
                    tunables = t
                    showTunables = true
                }
            }) {
                Icon(Icons.Default.DeveloperMode, contentDescription = "Ajustes del Governor", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            govs.forEach { gov ->
                val sel = gov == current
                Box(Modifier.clip(RoundedCornerShape(12.dp))
                    .then(if (sel) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    .border(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { onChange(gov) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(gov, style = MaterialTheme.typography.labelMedium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }

    if (showTunables) {
        ModalBottomSheet(onDismissRequest = { showTunables = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tunables de $current", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (tunables.isEmpty()) {
                    Text("No se encontraron tunables numéricos o el kernel no permite lectura.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tunables.forEach { (k, v) ->
                        var textVal by remember(k) { mutableStateOf(v) }
                        OutlinedTextField(
                            value = textVal,
                            onValueChange = { textVal = it },
                            label = { Text(k) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            trailingIcon = {
                                IconButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val cmd = "echo $textVal > /sys/devices/system/cpu/cpufreq/$current/$k"
                                        Shell.su(cmd)
                                        BootScriptManager.putCpuCommand(context, "gov_${current}_$k", cmd)
                                        val nv = Shell.su("cat /sys/devices/system/cpu/cpufreq/$current/$k", silentLog = true)?.trim()
                                        if (nv != null) {
                                            tunables = tunables.toMutableMap().apply { put(k, nv) }
                                            textVal = nv
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Guardar", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatFrequency(freqKhz: Long): String {
    val mhz = freqKhz / 1000
    return if (mhz >= 1000) String.format("%.1f GHz", mhz / 1000.0) else "$mhz MHz"
}

// ── sysfs helpers ──

private fun detectClusters(): List<ClusterInfo> {
    val clusters = mutableListOf<ClusterInfo>()
    val visited = mutableSetOf<Int>()
    val totalCpus = try {
        val present = Shell.su("cat /sys/devices/system/cpu/present", silentLog = true) ?: "0-7"
        if (present.contains("-")) (present.split("-").last().trim().toIntOrNull() ?: 7) + 1 else (present.trim().toIntOrNull() ?: 0) + 1
    } catch (_: Exception) { 8 }

    for (cpu in 0 until totalCpus) {
        if (cpu in visited) continue
        try {
            val relatedRaw = Shell.su("cat /sys/devices/system/cpu/cpu$cpu/cpufreq/related_cpus", silentLog = true) ?: continue
            val relatedCpus = relatedRaw.split("\\s+".toRegex()).mapNotNull { it.trim().toIntOrNull() }
            if (relatedCpus.isEmpty() || relatedCpus.any { it in visited }) { visited.addAll(relatedCpus); continue }
            visited.addAll(relatedCpus)
            val base = "/sys/devices/system/cpu/cpu$cpu/cpufreq"
            clusters.add(ClusterInfo(
                name = when (clusters.size) { 0 -> "Little Cluster"; 1 -> "Big Cluster"; else -> "Prime Cluster" },
                representativeCpu = cpu, cores = relatedCpus.sorted(),
                availableFrequencies = Shell.su("cat $base/scaling_available_frequencies", silentLog = true)?.split("\\s+".toRegex())?.mapNotNull { it.trim().toLongOrNull() }?.sorted() ?: emptyList(),
                availableGovernors = Shell.su("cat $base/scaling_available_governors", silentLog = true)?.split("\\s+".toRegex())?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                currentMaxFreq = Shell.su("cat $base/scaling_max_freq", silentLog = true)?.trim()?.toLongOrNull() ?: 0L,
                currentMinFreq = Shell.su("cat $base/scaling_min_freq", silentLog = true)?.trim()?.toLongOrNull() ?: 0L,
                currentGovernor = Shell.su("cat $base/scaling_governor", silentLog = true)?.trim() ?: ""
            ))
        } catch (_: Exception) { visited.add(cpu) }
    }
    return clusters
}

private fun writeToCores(context: android.content.Context, cores: List<Int>, file: String, value: String) {
    cores.forEach { core ->
        val cmd = "echo $value > /sys/devices/system/cpu/cpu$core/cpufreq/$file"
        Shell.su(cmd)
        BootScriptManager.putCpuCommand(context, "core_${core}_$file", cmd)
    }
}

// Helper to destructure 6 values
private data class SixTuple<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
