package com.shni.yxa.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shni.yxa.util.BootScriptManager
import com.shni.yxa.util.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class GpuProfile(
    val name: String, val icon: ImageVector, val desc: String,
    val governor: String
)

private val basicProfiles = listOf(
    GpuProfile("Ahorro", Icons.Default.BatterySaver, "Governor powersave/simple_ondemand", "powersave"),
    GpuProfile("Equilibrado", Icons.Default.Speed, "Governor msm-adreno-tz/interactive", "msm-adreno-tz"),
    GpuProfile("Gaming", Icons.Default.SportsEsports, "Governor performance, máximo rendimiento", "performance"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuDetailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var gpuPath by remember { mutableStateOf<String?>(null) }
    var gpuType by remember { mutableStateOf("Desconocida") }
    var isLoading by remember { mutableStateOf(true) }
    
    // Hardware detection flags
    var supportAdrenoIdler by remember { mutableStateOf(false) }
    var adrenoIdlerActive by remember { mutableStateOf(false) }
    
    var thermalPath by remember { mutableStateOf<String?>(null) }
    
    // Intermediate tab states
    var availableFreqs by remember { mutableStateOf<List<Long>>(emptyList()) }
    var availableGovs by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentGov by remember { mutableStateOf("") }
    var currentMaxFreq by remember { mutableStateOf(0L) }
    
    // Advanced states
    var forceGpuRendering by remember { mutableStateOf(false) }
    var forceMsaa by remember { mutableStateOf(false) }
    
    // Expert states
    var tunables by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    var applyOnBoot by remember { mutableStateOf(context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).getBoolean("gpu_apply_boot", false)) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // 1. Detect GPU Path
            val paths = listOf(
                "/sys/class/kgsl/kgsl-3d0" to "Adreno",
                "/sys/class/misc/mali0/device" to "Mali",
            )
            var detectedBasePath: String? = null
            for ((p, name) in paths) {
                if (Shell.su("test -e $p && echo 1", silentLog = true)?.trim() == "1") {
                    detectedBasePath = p
                    gpuType = name
                    break
                }
            }
            
            // Fallback devfreq
            if (detectedBasePath == null) {
                val nodes = Shell.su("ls /sys/class/devfreq/", silentLog = true)
                val gpuNode = nodes?.lines()?.firstOrNull { it.contains("gpu", true) || it.contains("kgsl", true) || it.contains("mali", true) }
                if (gpuNode != null) {
                    detectedBasePath = "/sys/class/devfreq/$gpuNode"
                    gpuType = if (gpuNode.contains("mali", true)) "Mali" else "Adreno/Generica"
                }
            }
            
            gpuPath = detectedBasePath
            
            // 2. Hardware Features Detection
            supportAdrenoIdler = Shell.su("test -e /sys/module/adreno_idler/parameters/adreno_idler_active && echo 1", silentLog = true)?.trim() == "1"
            if (supportAdrenoIdler) {
                adrenoIdlerActive = Shell.su("cat /sys/module/adreno_idler/parameters/adreno_idler_active", silentLog = true)?.trim() == "Y"
            }
            
            // Thermal path detection
            val t1 = "/sys/class/kgsl/kgsl-3d0/temp"
            val t2 = "/sys/class/thermal/thermal_zone0/temp"
            if (Shell.su("test -e $t1 && echo 1", silentLog = true)?.trim() == "1") thermalPath = t1
            else if (Shell.su("test -e $t2 && echo 1", silentLog = true)?.trim() == "1") thermalPath = t2

            // 3. Read Intermediate states
            if (detectedBasePath != null) {
                var fPath = "$detectedBasePath/devfreq/available_frequencies"
                if (Shell.su("test -e $fPath && echo 1", silentLog = true)?.trim() != "1") {
                    fPath = "$detectedBasePath/available_frequencies"
                }
                
                var gPath = "$detectedBasePath/devfreq/available_governors"
                if (Shell.su("test -e $gPath && echo 1", silentLog = true)?.trim() != "1") {
                    gPath = "$detectedBasePath/available_governors"
                }
                
                val freqs = Shell.su("cat $fPath", silentLog = true)?.split("\\s+".toRegex())?.mapNotNull { it.trim().toLongOrNull() }?.sorted() ?: emptyList()
                val govs = Shell.su("cat $gPath", silentLog = true)?.split("\\s+".toRegex())?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                
                availableFreqs = freqs
                availableGovs = govs
                
                currentGov = Shell.su("cat $detectedBasePath/devfreq/governor 2>/dev/null || cat $detectedBasePath/governor 2>/dev/null", silentLog = true)?.trim() ?: ""
                currentMaxFreq = Shell.su("cat $detectedBasePath/devfreq/max_freq 2>/dev/null || cat $detectedBasePath/max_freq 2>/dev/null", silentLog = true)?.trim()?.toLongOrNull() ?: 0L
                
                // 4. Read Expert Tunables
                if (currentGov.isNotEmpty()) {
                    val govDir = "$detectedBasePath/devfreq/governor_tunables" // Common in mali or some devfreq
                    val govDir2 = "/sys/class/devfreq/*gpu*/$currentGov"
                    // Try to list files in governor specific dir
                    val lsOut = Shell.su("ls $detectedBasePath/$currentGov/ 2>/dev/null || ls /sys/class/kgsl/kgsl-3d0/devfreq/$currentGov/ 2>/dev/null", silentLog = true) ?: ""
                    val tMap = mutableMapOf<String, String>()
                    lsOut.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { file ->
                        val v = Shell.su("cat $detectedBasePath/$currentGov/$file 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/devfreq/$currentGov/$file 2>/dev/null", silentLog = true)?.trim()
                        if (v != null && v.isNotEmpty()) tMap[file] = v
                    }
                    tunables = tMap
                }
            }
            
            // Advanced states read
            val msaa = Shell.su("getprop debug.hwui.force_msaa", silentLog = true)?.trim()
            forceMsaa = msaa == "true"
            
            isLoading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Configuración GPU", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("Detectada: $gpuType", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Apply on Boot Toggle
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Aplicar al iniciar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Restaura estos ajustes en el próximo reinicio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = applyOnBoot,
                        onCheckedChange = { 
                            applyOnBoot = it
                            context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).edit().putBoolean("gpu_apply_boot", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tabs
            val tabs = listOf("Básico", "Intermedio", "Avanzado", "Experto")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (selectedTab) {
                    0 -> { // Basic Tab
                        if (gpuPath == null) {
                            Text("No se detectó una ruta válida de GPU. Estos perfiles no se pueden aplicar.", color = MaterialTheme.colorScheme.error)
                        } else {
                            basicProfiles.forEach { p ->
                                GpuProfileCard(p, isSelected = currentGov == p.governor) {
                                    scope.launch(Dispatchers.IO) {
                                        val c1 = "echo ${p.governor} > $gpuPath/devfreq/governor 2>/dev/null || true"
                                        val c2 = "echo ${p.governor} > $gpuPath/governor 2>/dev/null || true"
                                        Shell.su("$c1; $c2")
                                        if (applyOnBoot) {
                                            BootScriptManager.putGpuCommand(context, "gpu_gov", "$c1\n$c2")
                                        }
                                        currentGov = Shell.su("cat $gpuPath/devfreq/governor 2>/dev/null || cat $gpuPath/governor 2>/dev/null", silentLog = true)?.trim() ?: ""
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Intermediate Tab
                        if (availableGovs.isEmpty() && availableFreqs.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.width(12.dp))
                                    Text("El kernel actual no permite modificar estos parámetros dinámicamente.", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        } else {
                            if (availableGovs.isNotEmpty()) {
                                Text("Gobernador", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableGovs.forEach { gov ->
                                        val isSel = currentGov == gov
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .clickable {
                                                    scope.launch(Dispatchers.IO) {
                                                        val c1 = "echo $gov > $gpuPath/devfreq/governor 2>/dev/null || true"
                                                        val c2 = "echo $gov > $gpuPath/governor 2>/dev/null || true"
                                                        Shell.su("$c1; $c2")
                                                        if (applyOnBoot) BootScriptManager.putGpuCommand(context, "gpu_gov", "$c1\n$c2")
                                                        currentGov = gov
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(gov, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                            
                            if (availableFreqs.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Frecuencia Máxima", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                val maxIdx = availableFreqs.indexOf(currentMaxFreq).takeIf { it >= 0 }?.toFloat() ?: (availableFreqs.size - 1).toFloat()
                                Slider(
                                    value = maxIdx,
                                    onValueChange = { idx ->
                                        val f = availableFreqs[idx.toInt()]
                                        scope.launch(Dispatchers.IO) {
                                            val c1 = "echo $f > $gpuPath/devfreq/max_freq 2>/dev/null || true"
                                            val c2 = "echo $f > $gpuPath/max_freq 2>/dev/null || true"
                                            Shell.su("$c1; $c2")
                                            if (applyOnBoot) BootScriptManager.putGpuCommand(context, "gpu_max_freq", "$c1\n$c2")
                                            currentMaxFreq = f
                                        }
                                    },
                                    valueRange = 0f..(availableFreqs.size - 1).toFloat(),
                                    steps = if (availableFreqs.size > 2) availableFreqs.size - 2 else 0,
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                )
                                Text("${currentMaxFreq / 1000000} MHz", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    2 -> { // Advanced Tab
                        // Hardware rendering force
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Forzar Renderizado GPU", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Obliga a usar GPU en 2D (puede causar glitches visuales)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = forceGpuRendering,
                                        onCheckedChange = { state ->
                                            forceGpuRendering = state
                                            scope.launch(Dispatchers.IO) {
                                                val v = if (state) "1" else "0"
                                                val cmd = "service call SurfaceFlinger 1008 i32 $v 2>/dev/null || true"
                                                Shell.su(cmd)
                                                if (applyOnBoot) BootScriptManager.putGpuCommand(context, "gpu_render", cmd)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Forzar MSAA 4x", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Mejora bordes en juegos OpenGL (consume más batería)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = forceMsaa,
                                        onCheckedChange = { state ->
                                            forceMsaa = state
                                            scope.launch(Dispatchers.IO) {
                                                val cmd = "setprop debug.hwui.force_msaa $state 2>/dev/null || true"
                                                Shell.su(cmd)
                                                if (applyOnBoot) BootScriptManager.putGpuCommand(context, "gpu_msaa", cmd)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }

                        if (supportAdrenoIdler) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Adreno Idler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                            Text("Ahorro de batería agresivo cuando la GPU está inactiva", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = adrenoIdlerActive,
                                            onCheckedChange = { state ->
                                                adrenoIdlerActive = state
                                                scope.launch(Dispatchers.IO) {
                                                    val v = if (state) "Y" else "N"
                                                    val cmd = "echo $v > /sys/module/adreno_idler/parameters/adreno_idler_active 2>/dev/null || true"
                                                    Shell.su(cmd)
                                                    if (applyOnBoot) BootScriptManager.putGpuCommand(context, "gpu_idler", cmd)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    3 -> { // Expert Tab
                        if (thermalPath == null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.width(12.dp))
                                    Text("No se detectó zona térmica compatible con la GPU en este dispositivo.", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        if (tunables.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Tunables del Gobernador bloqueados o inexistentes para '$currentGov'.", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        } else {
                            Text("Tunables: $currentGov", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            tunables.forEach { (k, v) ->
                                var textVal by remember(k, v) { mutableStateOf(v) }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                        OutlinedTextField(
                                            value = textVal,
                                            onValueChange = { textVal = it },
                                            modifier = Modifier.width(120.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                                            singleLine = true,
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        val p1 = "$gpuPath/$currentGov/$k"
                                                        val p2 = "/sys/class/kgsl/kgsl-3d0/devfreq/$currentGov/$k"
                                                        val cmd = "echo $textVal > $p1 2>/dev/null || echo $textVal > $p2 2>/dev/null || true"
                                                        Shell.su(cmd)
                                                        if (applyOnBoot) BootScriptManager.putGpuCommand(context, "gpu_tunable_$k", cmd)
                                                    }
                                                }) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun GpuProfileCard(profile: GpuProfile, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(profile.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(profile.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
