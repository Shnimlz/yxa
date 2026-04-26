package com.shni.yxa.screen

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var applyOnBoot by remember { mutableStateOf(BootScriptManager.isNetworkEnabled(context)) }

    // TCP
    var tcpCongestion by remember { mutableStateOf("cubic") }
    var supportedAlgos by remember { mutableStateOf<List<String>>(emptyList()) }
    var fastOpen by remember { mutableStateOf(false) }
    var windowScaling by remember { mutableStateOf(false) }
    var sack by remember { mutableStateOf(false) }

    // IPv6
    var ipv6Disabled by remember { mutableStateOf(false) }
    // TTL
    var ttlSpoofing by remember { mutableStateOf(false) }

    // DNS
    var currentDns by remember { mutableStateOf("default") }

    // Diagnostics
    var pingResult by remember { mutableStateOf<String?>(null) }
    var showPingDialog by remember { mutableStateOf(false) }
    var isRunningIperf by remember { mutableStateOf(false) }
    var iperfResult by remember { mutableStateOf<String?>(null) }
    var testMode by remember { mutableStateOf("TCP") }
    var parallelStreams by remember { mutableIntStateOf(1) }

    var wlan0Mtu by remember { mutableStateOf("1500") }
    var isCalibratingMtu by remember { mutableStateOf(false) }
    var mtuResult by remember { mutableStateOf<Int?>(null) }
    var bufferbloatEnabled by remember { 
        mutableStateOf(context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).getBoolean("bufferbloat", false)) 
    }
    var lagProtectorEnabled by remember { 
        mutableStateOf(context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).getBoolean("lag_protector", false)) 
    }
    
    var isRunningTrim by remember { mutableStateOf(false) }
    var trimResult by remember { mutableStateOf<String?>(null) }
    var ioSchedulerEnabled by remember { 
        mutableStateOf(context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).getBoolean("io_scheduler", false)) 
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            tcpCongestion = Shell.su("cat /proc/sys/net/ipv4/tcp_congestion_control")?.trim() ?: "cubic"
            val algosStr = Shell.su("cat /proc/sys/net/ipv4/tcp_available_congestion_control")?.trim()
            supportedAlgos = algosStr?.split("\\s+".toRegex())?.filter { it.isNotEmpty() } ?: emptyList()
            
            fastOpen = Shell.su("cat /proc/sys/net/ipv4/tcp_fastopen")?.trim() != "0"
            windowScaling = Shell.su("cat /proc/sys/net/ipv4/tcp_window_scaling")?.trim() == "1"
            sack = Shell.su("cat /proc/sys/net/ipv4/tcp_sack")?.trim() == "1"
            ipv6Disabled = Shell.su("cat /proc/sys/net/ipv6/conf/all/disable_ipv6")?.trim() == "1"
            
            val mtu = Shell.su("cat /sys/class/net/wlan0/mtu")?.trim()
            if (!mtu.isNullOrEmpty() && mtu.toIntOrNull() != null) {
                wlan0Mtu = mtu
            }
            bufferbloatEnabled = com.shni.yxa.util.BufferbloatManager.isEnabled()
        }
    }

    fun runNetCmd(key: String, cmd: String) {
        scope.launch(Dispatchers.IO) {
            Shell.su(cmd)
            BootScriptManager.putNetworkCommand(context, key, cmd)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Red y Modem",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
            ApplyOnBootToggle(
                checked = applyOnBoot,
                onCheckedChange = {
                    applyOnBoot = it
                    BootScriptManager.setNetworkEnabled(context, it)
                }
            )

            // TCP Stack
            CategoryCard("TCP Stack", Icons.Default.Transform) {
                Text("Algoritmo de Congestión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val defaultAlgos = listOf("bbr", "cubic", "reno")
                    val displayAlgos = (defaultAlgos + supportedAlgos).distinct()
                    displayAlgos.forEach { algo ->
                        val isSupported = supportedAlgos.isEmpty() || supportedAlgos.contains(algo)
                        FilterChip(
                            selected = tcpCongestion == algo,
                            onClick = {
                                tcpCongestion = algo
                                runNetCmd("tcp_congestion", "sysctl -w net.ipv4.tcp_congestion_control=$algo")
                            },
                            label = { Text(algo.uppercase()) },
                            enabled = isSupported
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                NetSwitch("TCP Fast Open", "Reduce la latencia en conexiones sucesivas", fastOpen) { 
                    fastOpen = it
                    runNetCmd("tcp_fastopen", "sysctl -w net.ipv4.tcp_fastopen=${if (it) 3 else 0}")
                }
                NetSwitch("Window Scaling", "Aumenta el rendimiento en conexiones rápidas", windowScaling) { 
                    windowScaling = it
                    runNetCmd("tcp_window_scaling", "sysctl -w net.ipv4.tcp_window_scaling=${if (it) 1 else 0}")
                }
                NetSwitch("TCP SACK", "Recuperación selectiva de paquetes perdidos", sack) { 
                    sack = it
                    runNetCmd("tcp_sack", "sysctl -w net.ipv4.tcp_sack=${if (it) 1 else 0}")
                }
            }

            // Wi-Fi Tweaks
            CategoryCard("Wi-Fi Tweaks", Icons.Default.WifiTethering) {
                OutlinedTextField(
                    value = wlan0Mtu,
                    onValueChange = { wlan0Mtu = it },
                    label = { Text("MTU (wlan0)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = {
                            val mtuInt = wlan0Mtu.toIntOrNull()
                            if (mtuInt != null) {
                                runNetCmd("wifi_mtu", "ifconfig wlan0 mtu $mtuInt || ip link set dev wlan0 mtu $mtuInt")
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Aplicar MTU", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                Text("Nota: Valores comunes son 1500 o 1420 (VPN).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Red Móvil
            CategoryCard("Red Móvil", Icons.Default.CellTower) {
                Text("Forzar Bandas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runNetCmd("mobile_network_mode", "settings put global preferred_network_mode 23")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Text("Forzar LTE")
                    }
                    Button(
                        onClick = {
                            runNetCmd("mobile_network_mode", "settings put global preferred_network_mode 33")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Text("Forzar 5G/NR")
                    }
                }
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                setClassName("com.android.settings", "com.android.settings.RadioInfo")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Menú Secreto de Radio Info")
                }
                Spacer(Modifier.height(8.dp))
                
                NetSwitch("Deshabilitar IPv6", "Fuerza IPv4 para mayor privacidad o compatibilidad", ipv6Disabled) { 
                    ipv6Disabled = it
                    runNetCmd("disable_ipv6", "sysctl -w net.ipv6.conf.all.disable_ipv6=${if (it) 1 else 0}")
                }
                NetSwitch("TTL Spoofing (64)", "Oculta el tethering al operador modificando los paquetes", ttlSpoofing) { 
                    ttlSpoofing = it
                    val cmd = if (it) "iptables -t mangle -A POSTROUTING -j TTL --ttl-set 64" else "iptables -t mangle -D POSTROUTING -j TTL --ttl-set 64"
                    runNetCmd("ttl_spoof", cmd)
                }
            }

            // DNS & Diagnóstico
            CategoryCard("DNS y Diagnóstico", Icons.Default.Dns) {
                Text("Servidor DNS (Global)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val opts = listOf("Default", "Cloudflare", "Google")
                    opts.forEachIndexed { index, dns ->
                        SegmentedButton(
                            selected = currentDns == dns,
                            onClick = {
                                currentDns = dns
                                val cmd = when(dns) {
                                    "Cloudflare" -> "setprop net.dns1 1.1.1.1 && setprop net.dns2 1.0.0.1"
                                    "Google" -> "setprop net.dns1 8.8.8.8 && setprop net.dns2 8.8.4.4"
                                    else -> "setprop net.dns1 '' && setprop net.dns2 ''"
                                }
                                runNetCmd("custom_dns", cmd)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                        ) {
                            Text(dns)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        showPingDialog = true
                        pingResult = "Haciendo ping a 8.8.8.8...\n"
                        scope.launch(Dispatchers.IO) {
                            val res = Shell.su("ping -c 4 8.8.8.8")
                            pingResult = res ?: "Error al ejecutar ping."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ejecutar Ping de Diagnóstico")
                }
            }

            CategoryCard("Diagnostico de Estres (Iperf3)", Icons.Default.NetworkCheck) {
                Text("Mide el ancho de banda real y la estabilidad de la red", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(8.dp))
                Text("Modo de prueba", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("TCP", "Gaming (UDP)", "Descarga (Rev)").forEach { mode ->
                        FilterChip(
                            selected = testMode == mode,
                            onClick = { testMode = mode },
                            label = { Text(mode) },
                            leadingIcon = if (testMode == mode) {{ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }} else null
                        )
                    }
                }
                when (testMode) {
                    "Gaming (UDP)" -> Text("Mide latencia y estabilidad (Jitter)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    "Descarga (Rev)" -> Text("Mide la velocidad de recepcion de datos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hilos paralelos: $parallelStreams", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = parallelStreams.toFloat(),
                        onValueChange = { parallelStreams = it.toInt() },
                        valueRange = 1f..4f,
                        steps = 2,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        isRunningIperf = true
                        iperfResult = null
                        scope.launch(Dispatchers.IO) {
                            val fallback = listOf(
                                "121.127.43.65" to "5201",
                                "37.19.216.1" to "5201"
                            )
                            val dynamic = com.shni.yxa.util.NetworkServerProvider.getNearbyServers(context)
                            val servers = if (dynamic.isNotEmpty()) dynamic else fallback

                            var result: String? = null
                            for ((ip, port) in servers) {
                                try {
                                    val modeFlags = when (testMode) {
                                        "Gaming (UDP)" -> "-u -b 10M"
                                        "Descarga (Rev)" -> "-R"
                                        else -> ""
                                    }
                                    val parallelFlag = if (parallelStreams > 1) "-P $parallelStreams" else ""
                                    val cmd = "${Shell.INTERNAL_BIN_PATH}iperf3 -c $ip -p $port $modeFlags $parallelFlag -t 5 2>&1"
                                    val output = Shell.su(cmd, timeoutSec = 15)
                                    if (output != null && !output.contains("busy", ignoreCase = true) && !output.contains("error", ignoreCase = true)) {
                                        result = output.lines().filter { line ->
                                            !line.startsWith("WARNING") && !line.startsWith("iperf3:")
                                        }.joinToString("\n").trim()
                                        break
                                    }
                                } catch (_: Exception) { }
                            }

                            iperfResult = if (result.isNullOrBlank()) "Todos los servidores estan ocupados o no disponibles. Intenta de nuevo." else result
                            isRunningIperf = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunningIperf,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isRunningIperf) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Analizando red (5s)...")
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar Test")
                    }
                }

                if (iperfResult != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = iperfResult!!,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            CategoryCard("Calibrador de MTU", Icons.Default.Tune) {
                Text("Encuentra el tamano maximo de paquete sin fragmentacion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (mtuResult != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("MTU optimo detectado: $mtuResult", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        com.shni.yxa.util.MtuOptimizer.applyMtu(mtuResult!!)
                                        wlan0Mtu = mtuResult.toString()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Aplicar")
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        isCalibratingMtu = true
                        mtuResult = null
                        scope.launch(Dispatchers.IO) {
                            val optimal = com.shni.yxa.util.MtuOptimizer.findOptimalMtu()
                            mtuResult = optimal
                            isCalibratingMtu = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCalibratingMtu,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isCalibratingMtu) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Calibrando...")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Calibrar MTU")
                    }
                }
            }

            // -- Anti-Bufferbloat --
            CategoryCard("Anti-Bufferbloat (fq_codel)", Icons.Default.SwapVert) {
                Text("Prioriza paquetes de juego sobre descargas pesadas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NetSwitch(
                    "Activar fq_codel",
                    "Algoritmo de cola justa con delay controlado",
                    bufferbloatEnabled
                ) {
                    bufferbloatEnabled = it
                    context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).edit().putBoolean("bufferbloat", it).apply()
                    scope.launch(Dispatchers.IO) {
                        if (it) com.shni.yxa.util.BufferbloatManager.enable()
                        else com.shni.yxa.util.BufferbloatManager.disable()
                    }
                }
            }

            // -- Lag Protector --
            CategoryCard("Escudo Anti-Lag", Icons.Default.Shield) {
                Text("Monitorea latencia y bloquea apps que consumen red en segundo plano", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NetSwitch(
                    "Activar Escudo",
                    "Analiza Jitter cada 30s y aplica reglas de firewall",
                    lagProtectorEnabled
                ) {
                    lagProtectorEnabled = it
                    context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).edit().putBoolean("lag_protector", it).apply()
                    if (it) {
                        com.shni.yxa.service.LagProtectorService.start(context)
                    } else {
                        com.shni.yxa.service.LagProtectorService.stop(context)
                    }
                }
                if (lagProtectorEnabled) {
                    Text(
                        "El servicio esta activo en segundo plano. Se detiene al desactivar.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // -- FSTRIM --
            CategoryCard("Almacenamiento y Sistema", Icons.Default.Storage) {
                Text("Mantenimiento de la memoria interna (Trim)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Button(
                    onClick = {
                        isRunningTrim = true
                        trimResult = null
                        scope.launch(Dispatchers.IO) {
                            val startTime = System.currentTimeMillis()
                            val cmd = "sm fstrim"
                            val result = com.shni.yxa.util.Shell.su(cmd, timeoutSec = 20)
                            val elapsedSecs = (System.currentTimeMillis() - startTime) / 1000.0
                            
                            trimResult = if (result.isNullOrBlank() || result.contains("Bundle", ignoreCase = true)) {
                                "✅ Almacenamiento optimizado.\n⏱️ Tiempo de limpieza: $elapsedSecs segundos."
                            } else {
                                result.trim()
                            }
                            isRunningTrim = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunningTrim,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isRunningTrim) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Optimizando...")
                    } else {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mantenimiento Express (FSTRIM)")
                    }
                }
                
                if (trimResult != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = trimResult!!,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                NetSwitch(
                    "Modo Juego en Disco (I/O Scheduler)",
                    "Reduce la latencia de carga en juegos pesados",
                    ioSchedulerEnabled
                ) {
                    ioSchedulerEnabled = it
                    context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).edit().putBoolean("io_scheduler", it).apply()
                    scope.launch(Dispatchers.IO) {
                        if (it) {
                            com.shni.yxa.util.Shell.su("echo deadline > /sys/block/sda/queue/scheduler 2>/dev/null || echo deadline > /sys/block/mmcblk0/queue/scheduler 2>/dev/null", silentLog = true)
                        } else {
                            com.shni.yxa.util.Shell.su("echo mq-deadline > /sys/block/sda/queue/scheduler 2>/dev/null || echo cfq > /sys/block/mmcblk0/queue/scheduler 2>/dev/null", silentLog = true)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(110.dp))
        }

    if (showPingDialog) {
        AlertDialog(
            onDismissRequest = { showPingDialog = false },
            title = { Text("Resultados del Ping") },
            text = {
                Text(pingResult ?: "", style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            },
            confirmButton = {
                TextButton(onClick = { showPingDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun CategoryCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun NetSwitch(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}
