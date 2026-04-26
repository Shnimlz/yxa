package com.shni.yxa.screen

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shni.yxa.util.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────── Data models ───────────────

data class ProcessEntry(
    val pid: String,
    val user: String,
    val name: String,
    val cpuPct: Float,
    val ramKb: Long
)

data class ProcAppEntry(
    val label: String,
    val packageName: String,
    val uid: Int,
    val versionName: String,
    val isSystem: Boolean,
    val isDisabled: Boolean
)

// ─────────────── Main screen ───────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Procesos", "Aplicaciones", "Oculto")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitor de Procesos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
            when (selectedTab) {
                0 -> ProcessTab()
                1 -> AppsTab()
                2 -> HiddenTab()
            }
        }
    }
}

// ─────────────── Tab 1: Live processes ───────────────

@Composable
fun ProcessTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var processes by remember { mutableStateOf<List<ProcessEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("cpu") }
    var isLoading by remember { mutableStateOf(true) }
    var killTarget by remember { mutableStateOf<ProcessEntry?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val raw = withContext(Dispatchers.IO) {
                Shell.su("ps -A -o pid,user,%cpu,rss,name 2>/dev/null")
            } ?: ""
            val parsed = withContext(Dispatchers.Default) { parsePsOutput(raw) }
            processes = parsed
            isLoading = false
            delay(3000)
        }
    }

    val sorted by remember(processes, sortBy, query) {
        derivedStateOf {
            var list = if (query.isBlank()) processes else processes.filter {
                it.name.contains(query, ignoreCase = true) || it.pid.contains(query)
            }
            list = when (sortBy) {
                "ram" -> list.sortedByDescending { it.ramKb }
                "pid" -> list.sortedBy { it.pid.toIntOrNull() ?: 0 }
                else -> list.sortedByDescending { it.cpuPct }
            }
            list
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar proceso...", fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )
            FilterChip(selected = sortBy == "cpu", onClick = { sortBy = "cpu" }, label = { Text("CPU", fontSize = 11.sp) })
            FilterChip(selected = sortBy == "ram", onClick = { sortBy = "ram" }, label = { Text("RAM", fontSize = 11.sp) })
        }

        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PID", Modifier.width(52.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("PROCESO", Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("CPU%", Modifier.width(48.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("RAM", Modifier.width(52.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sorted, key = { it.pid }) { proc ->
                    ProcessRow(proc, onKill = { killTarget = proc })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }

    if (killTarget != null) {
        val target = killTarget!!
        AlertDialog(
            onDismissRequest = { killTarget = null },
            title = { Text("Matar proceso") },
            text = { Text("¿Matar PID ${target.pid} (${target.name})?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { Shell.su("kill -9 ${target.pid}") }
                    killTarget = null
                }) { Text("Matar", color = Color(0xFFFF5252)) }
            },
            dismissButton = { TextButton(onClick = { killTarget = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ProcessRow(proc: ProcessEntry, onKill: () -> Unit) {
    val cpuColor = when {
        proc.cpuPct > 50f -> Color(0xFFFF5252)
        proc.cpuPct > 20f -> Color(0xFFFFAB40)
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(proc.pid, Modifier.width(52.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(proc.name.substringAfterLast("/"), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(proc.user, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.End) {
            Text("${proc.cpuPct.toInt()}%", fontSize = 12.sp, color = cpuColor, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { (proc.cpuPct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = cpuColor,
                trackColor = cpuColor.copy(alpha = 0.15f)
            )
        }
        Spacer(Modifier.width(6.dp))
        val ramStr = if (proc.ramKb > 1024) "${proc.ramKb / 1024}M" else "${proc.ramKb}K"
        Text(ramStr, Modifier.width(40.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        IconButton(onClick = onKill, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Matar", tint = Color(0xFFFF5252).copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
    }
}

private fun parsePsOutput(raw: String): List<ProcessEntry> {
    val result = mutableListOf<ProcessEntry>()
    val lines = raw.lines().drop(1)
    for (line in lines) {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5) continue
        val pid = parts[0]
        if (pid.toIntOrNull() == null) continue
        val user = parts[1]
        val cpuPct = parts[2].replace(",", ".").toFloatOrNull() ?: 0f
        val ramKb = parts[3].toLongOrNull() ?: 0L
        val name = parts.drop(4).joinToString(" ")
        result.add(ProcessEntry(pid, user, name, cpuPct, ramKb))
    }
    return result
}

// ─────────────── Tab 2: Installed apps ───────────────

@Composable
fun AppsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(0) }
    var apps by remember { mutableStateOf<List<ProcAppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadProcApps(context) }
        isLoading = false
    }

    val visible by remember(apps, filter, query) {
        derivedStateOf {
            apps.filter { app ->
                val matchFilter = when (filter) {
                    1 -> !app.isSystem
                    2 -> app.isSystem
                    else -> true
                }
                val matchQuery = query.isBlank() || app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
                matchFilter && matchQuery
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Buscar app o paquete...", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todas", "Usuario", "Sistema").forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(4.dp))
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.packageName }) { app ->
                    AppRow(app, onForceStop = {
                        scope.launch(Dispatchers.IO) { Shell.su("am force-stop ${app.packageName}") }
                    }, onClearData = {
                        scope.launch(Dispatchers.IO) { Shell.su("pm clear ${app.packageName}") }
                    }, onInfo = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                        context.startActivity(intent)
                    })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: ProcAppEntry, onForceStop: () -> Unit, onClearData: () -> Unit, onInfo: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (app.isSystem) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (app.isSystem) Icons.Default.Android else Icons.Default.Apps,
                        contentDescription = null,
                        tint = if (app.isSystem) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (app.isDisabled) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFF5252).copy(alpha = 0.15f)) {
                            Text("DESACT.", fontSize = 9.sp, color = Color(0xFFFF5252), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text("UID ${app.uid}  •  v${app.versionName}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        if (expanded) {
            Row(Modifier.padding(top = 6.dp, start = 46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onForceStop, modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Forzar cierre", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onClearData, modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Limpiar datos", fontSize = 11.sp)
                }
                IconButton(onClick = onInfo, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun loadProcApps(context: Context): List<ProcAppEntry> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .map { info ->
            val label = pm.getApplicationLabel(info).toString()
            val versionName = try { pm.getPackageInfo(info.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isDisabled = info.enabled.not()
            ProcAppEntry(label = label, packageName = info.packageName, uid = info.uid, versionName = versionName, isSystem = isSystem, isDisabled = isDisabled)
        }
        .sortedBy { it.label.lowercase() }
}

// ─────────────── Tab 3: Hidden / System ───────────────

@Composable
fun HiddenTab() {
    val scope = rememberCoroutineScope()
    var disabledPkgs by remember { mutableStateOf("") }
    var rootPkgs by remember { mutableStateOf("") }
    var dangerousPerms by remember { mutableStateOf("") }
    var activeServices by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            disabledPkgs = Shell.su("pm list packages -d 2>/dev/null") ?: "Sin datos"
            rootPkgs = Shell.su("pm list packages --uid 1000 2>/dev/null") ?: "Sin datos"
            dangerousPerms = Shell.su("dumpsys package permissions 2>/dev/null | grep -E 'permission|protection' | head -50") ?: "Sin datos"
            activeServices = Shell.su("dumpsys activity services 2>/dev/null | grep 'ServiceRecord' | head -30") ?: "Sin datos"
        }
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)) }

        item { HiddenSection("Apps desactivadas", Icons.Default.Block, disabledPkgs, Color(0xFFFF5252)) }
        item { HiddenSection("Paquetes UID Sistema (1000)", Icons.Default.Shield, rootPkgs, Color(0xFFFFAB40)) }
        item { HiddenSection("Permisos peligrosos otorgados", Icons.Default.Warning, dangerousPerms, Color(0xFFFFD740)) }
        item { HiddenSection("Servicios activos del sistema", Icons.Default.Dns, activeServices, MaterialTheme.colorScheme.primary) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HiddenSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: String, accentColor: Color) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accentColor)
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            if (expanded) {
                HorizontalDivider(color = accentColor.copy(alpha = 0.2f))
                val lines = content.lines().filter { it.isNotBlank() }
                if (lines.isEmpty() || content == "Sin datos") {
                    Text("Sin datos disponibles", Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        lines.take(50).forEach { line ->
                            Text(
                                line.trim().removePrefix("package:"),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}
