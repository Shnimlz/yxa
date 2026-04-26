package com.shni.yxa.screen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import com.shni.yxa.util.BootScriptManager
import com.shni.yxa.util.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class RamProfile(val name: String, val icon: ImageVector, val desc: String, val swappiness: Int, val cachePressure: Int, val dirtyRatio: Int, val dirtyBg: Int, val minFreeKb: Int)

private val ramProfiles = listOf(
    RamProfile("Conservador", Icons.Default.BatterySaver, "Swap agresivo, preservar RAM libre", 100, 50, 40, 10, 4096),
    RamProfile("Equilibrado", Icons.Default.Balance, "Valores por defecto de Android", 60, 100, 20, 5, 8192),
    RamProfile("Rendimiento", Icons.Default.RocketLaunch, "Apps en RAM, cache agresivo", 10, 200, 10, 3, 16384),
)

private data class AppInfo(val packageName: String, val name: String, val icon: androidx.compose.ui.graphics.ImageBitmap?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamDetailScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var appliedProfile by remember { mutableStateOf<String?>(null) }
    var swappiness by remember { mutableIntStateOf(60) }
    var cachePressure by remember { mutableIntStateOf(100) }
    var dirtyRatio by remember { mutableIntStateOf(20) }
    var dirtyBgRatio by remember { mutableIntStateOf(5) }
    var minFreeKb by remember { mutableIntStateOf(8192) }
    var dirtyExpire by remember { mutableIntStateOf(3000) }
    var dirtyWriteback by remember { mutableIntStateOf(500) }
    var overcommit by remember { mutableIntStateOf(0) }
    var zramAvailable by remember { mutableStateOf(false) }
    var zramSizeMb by remember { mutableIntStateOf(0) }
    var ksmAvailable by remember { mutableStateOf(false) }
    var ksmEnabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var applyOnBoot by remember { mutableStateOf(BootScriptManager.isRamEnabled(context)) }
    val tabs = listOf("Básico", "Intermedio", "Avanzado")

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            fun readInt(path: String, def: Int) = Shell.su("cat $path")?.trim()?.toIntOrNull() ?: def
            swappiness = readInt("/proc/sys/vm/swappiness", 60)
            cachePressure = readInt("/proc/sys/vm/vfs_cache_pressure", 100)
            dirtyRatio = readInt("/proc/sys/vm/dirty_ratio", 20)
            dirtyBgRatio = readInt("/proc/sys/vm/dirty_background_ratio", 5)
            minFreeKb = readInt("/proc/sys/vm/min_free_kbytes", 8192)
            dirtyExpire = readInt("/proc/sys/vm/dirty_expire_centisecs", 3000)
            dirtyWriteback = readInt("/proc/sys/vm/dirty_writeback_centisecs", 500)
            overcommit = readInt("/proc/sys/vm/overcommit_memory", 0)
            zramAvailable = Shell.su("cat /sys/block/zram0/disksize") != null
            if (zramAvailable) zramSizeMb = ((Shell.su("cat /sys/block/zram0/disksize")?.trim()?.toLongOrNull() ?: 0L) / 1024 / 1024).toInt()
            // Use test -f to check existence silently, avoiding error log spam on devices without KSM
            ksmAvailable = Shell.su("test -f /sys/kernel/mm/ksm/run && echo yes")?.trim() == "yes"
            if (ksmAvailable) ksmEnabled = Shell.su("cat /sys/kernel/mm/ksm/run")?.trim() == "1"
        }
        isLoading = false
    }

    fun writeVm(path: String, value: Int) { 
        scope.launch(Dispatchers.IO) { 
            val cmd = "echo $value > $path"
            Shell.su(cmd) 
            BootScriptManager.putRamCommand(context, path.substringAfterLast("/"), cmd)
        } 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RAM", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.tertiary, navigationIconContentColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary, strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Leyendo configuración RAM…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ApplyOnBootToggle(
                    checked = applyOnBoot,
                    onCheckedChange = {
                        applyOnBoot = it
                        BootScriptManager.setRamEnabled(context, it)
                    }
                )
                
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                ) {
                    tabs.forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(label, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) },
                            selectedContentColor = MaterialTheme.colorScheme.tertiary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                appliedProfile?.let {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(12.dp)) {
                        Text("✓ Perfil \"$it\" aplicado", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                when (selectedTab) {
                    0 -> {
                        ramProfiles.forEach { p ->
                            Card(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val cmdSwap = "echo ${p.swappiness} > /proc/sys/vm/swappiness"
                                    val cmdCache = "echo ${p.cachePressure} > /proc/sys/vm/vfs_cache_pressure"
                                    val cmdDirty = "echo ${p.dirtyRatio} > /proc/sys/vm/dirty_ratio"
                                    val cmdDirtyBg = "echo ${p.dirtyBg} > /proc/sys/vm/dirty_background_ratio"
                                    val cmdMinFree = "echo ${p.minFreeKb} > /proc/sys/vm/min_free_kbytes"
                                    
                                    Shell.su("$cmdSwap && $cmdCache && $cmdDirty && $cmdDirtyBg && $cmdMinFree")
                                    
                                    BootScriptManager.putRamCommand(context, "swappiness", cmdSwap)
                                    BootScriptManager.putRamCommand(context, "vfs_cache_pressure", cmdCache)
                                    BootScriptManager.putRamCommand(context, "dirty_ratio", cmdDirty)
                                    BootScriptManager.putRamCommand(context, "dirty_background_ratio", cmdDirtyBg)
                                    BootScriptManager.putRamCommand(context, "min_free_kbytes", cmdMinFree)
                                }
                                swappiness = p.swappiness; cachePressure = p.cachePressure; dirtyRatio = p.dirtyRatio; dirtyBgRatio = p.dirtyBg; minFreeKb = p.minFreeKb
                                appliedProfile = p.name
                            }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Icon(p.icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(32.dp))
                                    Column {
                                        Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(p.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Swap:${p.swappiness} Cache:${p.cachePressure} Dirty:${p.dirtyRatio}% MinFree:${p.minFreeKb}KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        VmSlider("Swappiness", "Agresividad de swap (0=nunca, 200=agresivo)", swappiness, 0, 200) { swappiness = it; writeVm("/proc/sys/vm/swappiness", it) }
                        VmSlider("Cache Pressure", "Presión de cache (50=conservar, 500=liberar)", cachePressure, 0, 500) { cachePressure = it; writeVm("/proc/sys/vm/vfs_cache_pressure", it) }
                        VmSlider("Dirty Ratio", "% máximo de páginas sucias antes de sync", dirtyRatio, 5, 90) { dirtyRatio = it; writeVm("/proc/sys/vm/dirty_ratio", it) }
                        VmSlider("Dirty BG Ratio", "% para writeback en segundo plano", dirtyBgRatio, 1, 50) { dirtyBgRatio = it; writeVm("/proc/sys/vm/dirty_background_ratio", it) }
                        VmSlider("Min Free KB", "Memoria libre reservada (KB)", minFreeKb, 2048, 65536) { minFreeKb = it; writeVm("/proc/sys/vm/min_free_kbytes", it) }
                        VmSlider("Dirty Expire", "Centisegundos antes de flush (100=1s)", dirtyExpire, 100, 6000) { dirtyExpire = it; writeVm("/proc/sys/vm/dirty_expire_centisecs", it) }
                        VmSlider("Dirty Writeback", "Intervalo de writeback (centisegundos)", dirtyWriteback, 100, 3000) { dirtyWriteback = it; writeVm("/proc/sys/vm/dirty_writeback_centisecs", it) }
                        VmSlider("Overcommit", "0=heurístico 1=siempre 2=nunca", overcommit, 0, 2) { overcommit = it; writeVm("/proc/sys/vm/overcommit_memory", it) }
                    }
                    2 -> AdvancedRamTab(zramAvailable, zramSizeMb, ksmAvailable, ksmEnabled, scope, context, onKsmToggle = { en -> 
                        ksmEnabled = en; 
                        scope.launch(Dispatchers.IO) { 
                            val cmd = "echo ${if (en) "1" else "0"} > /sys/kernel/mm/ksm/run"
                            Shell.su(cmd) 
                            BootScriptManager.putRamCommand(context, "ksm_run", cmd)
                        } 
                    })
                }
            }
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun VmSlider(label: String, desc: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("$value", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = min.toFloat()..max.toFloat(),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.tertiary, activeTrackColor = MaterialTheme.colorScheme.tertiary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$max", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdvancedRamTab(zramAvail: Boolean, zramMb: Int, ksmAvail: Boolean, ksmEn: Boolean, scope: kotlinx.coroutines.CoroutineScope, context: Context, onKsmToggle: (Boolean) -> Unit) {
    var showDrop by remember { mutableStateOf(false) }
    var dropDone by remember { mutableStateOf(false) }
    
    var showWhitelistPicker by remember { mutableStateOf(false) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var currentWhitelistApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    
    val prefs = context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE)
    var autoCleanEnabled by remember { mutableStateOf(prefs.getBoolean("auto_ram_clean", false)) }
    var autoCleanThreshold by remember { mutableIntStateOf(prefs.getInt("auto_ram_clean_threshold", 75)) }
    var whitelist by remember { mutableStateOf(prefs.getStringSet("ram_whitelist", emptySet()) ?: emptySet()) }
    
    LaunchedEffect(whitelist) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val list = mutableListOf<AppInfo>()
            whitelist.forEach { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(info).toString()
                    val drawable = pm.getApplicationIcon(info)
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: run {
                        val bmp = android.graphics.Bitmap.createBitmap(
                            drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                            drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                    list.add(AppInfo(pkg, label, bitmap.asImageBitmap()))
                } catch (e: Exception) {
                    list.add(AppInfo(pkg, pkg, null))
                }
            }
            currentWhitelistApps = list.sortedBy { it.name.lowercase() }
        }
    }
    
    val loadAvailableApps: () -> Unit = {
        isLoadingApps = true
        showWhitelistPicker = true
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = try { pm.getInstalledApplications(0) } catch (e: Exception) { emptyList() }
            val list = mutableListOf<AppInfo>()
            packages.forEach { info ->
                try {
                    val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!isSystem && !whitelist.contains(info.packageName) && info.packageName != context.packageName) {
                        val label = pm.getApplicationLabel(info).toString()
                        val drawable = pm.getApplicationIcon(info)
                        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: run {
                            val bmp = android.graphics.Bitmap.createBitmap(
                                drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                                drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bmp
                        }
                        list.add(AppInfo(info.packageName, label, bitmap.asImageBitmap()))
                    }
                } catch (e: Exception) {}
            }
            installedApps = list.sortedBy { it.name.lowercase() }
            isLoadingApps = false
        }
    }
    
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Limpieza Automática (Auto-Drop)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            Text("Monitorea la memoria y libera RAM cuando se supera el umbral.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Activar Auto-Drop", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = autoCleanEnabled,
                    onCheckedChange = {
                        autoCleanEnabled = it
                        prefs.edit().putBoolean("auto_ram_clean", it).apply()
                        if (it) {
                            com.shni.yxa.service.AutoRamCleanerService.start(context)
                        } else {
                            com.shni.yxa.service.AutoRamCleanerService.stop(context)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.tertiary)
                )
            }
            if (autoCleanEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("Umbral de RAM: $autoCleanThreshold%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = autoCleanThreshold.toFloat(),
                    onValueChange = {
                        autoCleanThreshold = it.toInt()
                        prefs.edit().putInt("auto_ram_clean_threshold", it.toInt()).apply()
                    },
                    valueRange = 60f..95f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.tertiary, activeTrackColor = MaterialTheme.colorScheme.tertiary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                
                Spacer(Modifier.height(16.dp))
                Text("Lista de Excepciones", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Text("Aplicaciones que nunca serán cerradas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Button(
                    onClick = { loadAvailableApps() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Añadir Aplicación")
                }
                
                if (currentWhitelistApps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        currentWhitelistApps.forEach { app ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (app.icon != null) {
                                            androidx.compose.foundation.Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Column {
                                            Text(app.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val updatedSet = whitelist - app.packageName
                                            whitelist = updatedSet
                                            prefs.edit().putStringSet("ram_whitelist", updatedSet).apply()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Drop Caches", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            Text("Libera pagecache, dentries e inodes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { showDrop = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.tertiary)) {
                Text(if (dropDone) "✓ Cache liberado" else "Liberar cache", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (showDrop) {
        AlertDialog(onDismissRequest = { showDrop = false }, title = { Text("Liberar cache") },
            text = { Text("Las apps pueden tardar más temporalmente. ¿Continuar?") },
            confirmButton = { TextButton(onClick = { showDrop = false; dropDone = true; scope.launch(Dispatchers.IO) { Shell.su("echo 3 > /proc/sys/vm/drop_caches") } }) { Text("Liberar") } },
            dismissButton = { TextButton(onClick = { showDrop = false }) { Text("Cancelar") } })
    }
    if (zramAvail) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ZRAM", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Text("Swap comprimido en RAM. Actual: ${zramMb} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (ksmAvail) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("KSM", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Text("Fusiona páginas de memoria idénticas. Usa algo de CPU.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Activar KSM", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ksmEn, onCheckedChange = onKsmToggle, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.tertiary))
                }
            }
        }
    }
    

    if (showWhitelistPicker) {
        AlertDialog(
            onDismissRequest = { showWhitelistPicker = false },
            title = { Text("Seleccionar Aplicación") },
            text = {
                if (isLoadingApps) {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(installedApps.size) { index ->
                            val app = installedApps[index]
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(8.dp),
                                onClick = {
                                    val updatedSet = whitelist + app.packageName
                                    whitelist = updatedSet
                                    prefs.edit().putStringSet("ram_whitelist", updatedSet).apply()
                                    showWhitelistPicker = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (app.icon != null) {
                                        androidx.compose.foundation.Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column {
                                        Text(app.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhitelistPicker = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

