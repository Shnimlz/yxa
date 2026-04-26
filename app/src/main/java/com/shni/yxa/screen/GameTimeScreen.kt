package com.shni.yxa.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.shni.yxa.data.FloatingAppsRepository
import com.shni.yxa.data.GameProfile
import com.shni.yxa.data.GameProfileRepository
import com.shni.yxa.service.GameOverlayService
import com.shni.yxa.util.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTimeScreen(onOpenSettings: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { GameProfileRepository(context) }
    val floatingRepo = remember { FloatingAppsRepository(context) }
    var games by remember { mutableStateOf(repo.getAll()) }
    var floatingApps by remember { mutableStateOf(floatingRepo.getAll()) }
    var showPicker by remember { mutableStateOf(false) }
    var showFloatingPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pm = context.packageManager
    val hasOverlayPerm = remember { Settings.canDrawOverlays(context) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GameTime", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${games.size} juegos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent, titleContentColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Overlay permission banner
            if (!hasOverlayPerm) {
                item {
                    Card(onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Toca para permitir overlay (necesario para el panel flotante)", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Game list
            items(games, key = { it.packageName }) { game ->
                val icon = remember(game.packageName) {
                    try { pm.getApplicationIcon(game.packageName) } catch (_: Exception) { null }
                }
                GameCard(game, icon, onSettings = { onOpenSettings(game.packageName) },
                    onLaunch = { launchGame(context, game, scope) },
                    onDelete = { repo.delete(game.packageName); games = repo.getAll() })
            }

            // Add game button
            item {
                OutlinedButton(onClick = {
                    showPicker = true
                    isLoadingApps = true
                    scope.launch {
                        installedApps = withContext(Dispatchers.IO) { getInstalledApps(pm, games.map { it.packageName }.toSet(), false) }
                        isLoadingApps = false
                    }
                }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar juego", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (games.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.VideogameAsset, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Agrega juegos para configurar perfiles individuales", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Floating Apps Section ──
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Apps flotantes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text("Se muestran en el overlay durante el juego", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(floatingApps) { pkg ->
                val appLabel = remember(pkg) { try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg } }
                val appIcon = remember(pkg) { try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null } }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        appIcon?.let { Image(bitmap = it.toBitmap(48, 48).asImageBitmap(), contentDescription = null, Modifier.size(36.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(appLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        IconButton(onClick = { floatingRepo.remove(pkg); floatingApps = floatingRepo.getAll() }) {
                            Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = {
                    showFloatingPicker = true; isLoadingApps = true
                    scope.launch {
                        installedApps = withContext(Dispatchers.IO) { getInstalledApps(pm, floatingApps.toSet(), true) }
                        isLoadingApps = false
                    }
                }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar app flotante", fontSize = 13.sp)
                }
            }
            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }

    // Game picker dialog
    if (showPicker) {
        AlertDialog(onDismissRequest = { showPicker = false }, title = { Text("Seleccionar juego") }, text = {
            if (isLoadingApps) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(installedApps, key = { it.packageName }) { app ->
                        Card(onClick = {
                            repo.save(GameProfile(packageName = app.packageName, label = app.label))
                            games = repo.getAll(); showPicker = false
                        }, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                app.icon?.let { drawable -> Image(bitmap = drawable.toBitmap(48, 48).asImageBitmap(), contentDescription = null, Modifier.size(40.dp)) }
                                Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } })
    }

    // Floating app picker dialog
    if (showFloatingPicker) {
        AlertDialog(onDismissRequest = { showFloatingPicker = false }, title = { Text("Agregar app flotante") }, text = {
            if (isLoadingApps) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(installedApps, key = { it.packageName }) { app ->
                        Card(onClick = {
                            floatingRepo.add(app.packageName)
                            floatingApps = floatingRepo.getAll(); showFloatingPicker = false
                        }, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                app.icon?.let { drawable -> Image(bitmap = drawable.toBitmap(48, 48).asImageBitmap(), contentDescription = null, Modifier.size(40.dp)) }
                                Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showFloatingPicker = false }) { Text("Cancelar") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameCard(game: GameProfile, icon: Drawable?, onSettings: () -> Unit, onLaunch: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // App icon
            icon?.let { drawable ->
                Image(bitmap = drawable.toBitmap(56, 56).asImageBitmap(), contentDescription = null, Modifier.size(48.dp))
                Spacer(Modifier.width(14.dp))
            }

            // Name + profile summary
            Column(Modifier.weight(1f)) {
                Text(game.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                val mode = when (game.perfMode) { 0 -> "Ahorro"; 2 -> "Turbo"; else -> "Equilibrado" }
                Text("$mode · ${game.refreshRate}Hz · ${game.resolution}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Settings button
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Configuración", tint = MaterialTheme.colorScheme.onSurface) }

            // Launch button
            IconButton(onClick = onLaunch) { Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar", tint = MaterialTheme.colorScheme.primary) }

            // More menu
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = MaterialTheme.colorScheme.onSurface) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Eliminar", color = Color(0xFFFF5252)) }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

private fun launchGame(context: Context, game: GameProfile, scope: kotlinx.coroutines.CoroutineScope) {
    // Apply profile
    scope.launch(Dispatchers.IO) {
        // CPU
        val gov = when (game.perfMode) { 0 -> "powersave"; 2 -> "performance"; else -> "schedutil" }
        Shell.su("for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \$c 2>/dev/null; done")

        // Refresh rate
        Shell.su("settings put system min_refresh_rate ${game.refreshRate}")
        Shell.su("settings put system peak_refresh_rate ${game.refreshRate}")
        val checkHz = Shell.su("settings get system min_refresh_rate")?.trim()
        android.util.Log.d("YxaVerifier", "Hz applied: $checkHz")

        // Resolution
        if (game.resolution < 100) {
            val rawSize = Shell.su("wm size")?.substringAfter("Physical size:")?.trim() ?: return@launch
            val rawDensity = Shell.su("wm density")?.substringAfter("Physical density:")?.substringBefore('\n')?.trim() ?: return@launch
            val parts = rawSize.split("x")
            if (parts.size == 2) {
                val w = parts[0].trim().toIntOrNull() ?: return@launch
                val h = parts[1].trim().toIntOrNull() ?: return@launch
                val newW = w * game.resolution / 100
                val newH = h * game.resolution / 100
                Shell.su("wm size ${newW}x${newH}")
                
                val density = rawDensity.toIntOrNull()
                if (density != null) {
                    val newDensity = density * game.resolution / 100
                    Shell.su("wm density $newDensity")
                }
            }
        } else {
            Shell.su("wm size reset")
            Shell.su("wm density reset")
        }

        // Graphics API
        if (game.graphicsApi == "default") {
            Shell.su("settings delete global angle_gl_driver_selection_pkgs")
            Shell.su("settings delete global angle_gl_driver_selection_values")
            val checkApi = Shell.su("settings get global angle_gl_driver_selection_pkgs")?.trim()
            android.util.Log.d("YxaVerifier", "Graphics API applied (default): $checkApi")
        } else {
            Shell.su("settings put global angle_gl_driver_selection_pkgs ${game.packageName}")
            Shell.su("settings put global angle_gl_driver_selection_values ${game.graphicsApi}")
            val checkApi = Shell.su("settings get global angle_gl_driver_selection_values")?.trim()
            android.util.Log.d("YxaVerifier", "Graphics API applied (${game.graphicsApi}): $checkApi")
        }

        // Touch
        if (game.touchSensitivity) Shell.su("echo 1 > /proc/touchpanel/game_switch_enable 2>/dev/null")
        // Wi-Fi
        if (game.wifiLowLatency) {
            Shell.su("sysctl -w net.ipv4.tcp_low_latency=1 2>/dev/null")
            Shell.su("sysctl -w net.ipv4.tcp_congestion_control=${game.tcpCongestion} 2>/dev/null")
            val checkTcp = Shell.su("sysctl -n net.ipv4.tcp_congestion_control 2>/dev/null")?.trim()
            android.util.Log.d("YxaVerifier", "TCP algorithm applied: $checkTcp")
            if (game.advancedNet) {
                // Set minimum buffers to prevent lag spikes (bloatbuffer)
                Shell.su("sysctl -w net.ipv4.tcp_rmem='4096 87380 1048576' 2>/dev/null")
                Shell.su("sysctl -w net.ipv4.tcp_wmem='4096 16384 1048576' 2>/dev/null")
            }
        }
        // GPU
        if (game.gpuBoost) {
            Shell.su("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null")
            Shell.su("echo performance > /sys/class/devfreq/*gpu*/governor 2>/dev/null")
        }
        // DND
        if (game.dndEnabled) Shell.su("cmd notification set_dnd priority 2>/dev/null")
        
        // Extreme Memory Management
        if (game.aggressiveClear) {
            Shell.su("sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null")
        }
        if (game.disableZram) {
            Shell.su("sysctl -w vm.swappiness=10 2>/dev/null")
        }
        if (game.killBackground) {
            Shell.su("am kill-all 2>/dev/null; am shrink-all 2>/dev/null")
        }
    }

    if (Settings.canDrawOverlays(context)) {
        val prefs = context.getSharedPreferences("yxa_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("overlay_active", true)
            .putString("last_overlay_pkg", game.packageName)
            .apply()
        GameOverlayService.start(context, game.packageName)
    }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(game.packageName)
    launchIntent?.let { context.startActivity(it) }
}

private fun getInstalledApps(pm: PackageManager, exclude: Set<String>, isForFloating: Boolean): List<AppEntry> {
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
    val bankKeywords = listOf("bank", "banco", "pay", "pago", "fintec", "bbva", "santander", "banamex", "azteca", "nubank", "stori", "uala", "paypal", "spin", "oxxo")

    return resolveInfos.mapNotNull { resolveInfo ->
        val appInfo = resolveInfo.activityInfo.applicationInfo
        val pkg = appInfo.packageName

        if (pkg in exclude || pkg == "com.shni.yxa") return@mapNotNull null

        if (isForFloating) {
            val isGame = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                         (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME)
            if (isGame) return@mapNotNull null

            val pkgLower = pkg.lowercase()
            if (bankKeywords.any { pkgLower.contains(it) }) return@mapNotNull null
        }

        val label = resolveInfo.loadLabel(pm).toString()
        val icon = try { resolveInfo.loadIcon(pm) } catch (_: Exception) { null }
        AppEntry(pkg, label, icon)
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
}
