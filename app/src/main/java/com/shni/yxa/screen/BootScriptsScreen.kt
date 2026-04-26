package com.shni.yxa.screen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shni.yxa.util.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val dangerousPatterns = listOf(
    "cpu0/online",
    "echo 0 > /sys/devices/system/cpu/cpu0",
    "rm -rf /",
    "rm -rf /system",
    "rm -rf /data",
    "mkfs",
    "format",
    "dd if=/dev/zero",
    "reboot bootloader",
    "reboot recovery",
    "fastboot"
)

private val expertScripts = listOf(
    "Limpieza agresiva" to """#!/system/bin/sh
# Yxa — Limpieza agresiva de RAM al boot
echo 60 > /proc/sys/vm/swappiness
echo 200 > /proc/sys/vm/vfs_cache_pressure
echo 10 > /proc/sys/vm/dirty_ratio
echo 3 > /proc/sys/vm/dirty_background_ratio
echo 16384 > /proc/sys/vm/min_free_kbytes
echo 500 > /proc/sys/vm/dirty_expire_centisecs
echo 200 > /proc/sys/vm/dirty_writeback_centisecs
""",
    "Limpieza ligera" to """#!/system/bin/sh
# Yxa — Limpieza ligera de RAM al boot
echo 60 > /proc/sys/vm/swappiness
echo 100 > /proc/sys/vm/vfs_cache_pressure
echo 20 > /proc/sys/vm/dirty_ratio
echo 5 > /proc/sys/vm/dirty_background_ratio
echo 8192 > /proc/sys/vm/min_free_kbytes
""",
    "Máximo rendimiento" to """#!/system/bin/sh
# Yxa — Máximo rendimiento al boot
echo 60 > /proc/sys/vm/swappiness
echo 500 > /proc/sys/vm/vfs_cache_pressure
echo 5 > /proc/sys/vm/dirty_ratio
echo 1 > /proc/sys/vm/dirty_background_ratio
echo 32768 > /proc/sys/vm/min_free_kbytes
echo 100 > /proc/sys/vm/dirty_expire_centisecs
echo 100 > /proc/sys/vm/dirty_writeback_centisecs
if [ -f /sys/kernel/mm/ksm/run ]; then echo 1 > /sys/kernel/mm/ksm/run; fi
"""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootScriptsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE) }
    var scriptText by remember { mutableStateOf(prefs.getString("boot_script", "") ?: "") }
    var scriptInstalled by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showDangerWarning by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scriptInstalled = withContext(Dispatchers.IO) {
            val r = Shell.su("test -f /data/adb/service.d/yxa_boot.sh && echo yes") ?: ""
            r.trim() == "yes"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scripts de Arranque", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.tertiary, navigationIconContentColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Editor de Scripts Magisk", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            }
            
            Text("Selecciona un preset o escribe tu script. Se ejecutará en cada arranque via Magisk service.d.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Preset cards
        expertScripts.forEach { (name, script) ->
            Card(onClick = { scriptText = script }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (scriptText == script) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(12.dp)) {
                Text(name, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (scriptText == script) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("Editor de script", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(value = scriptText, onValueChange = { scriptText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 18.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val hasDanger = dangerousPatterns.any { pattern -> scriptText.contains(pattern, ignoreCase = true) }
                if (hasDanger) showDangerWarning = true else showConfirm = true
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = Color.Black),
                enabled = scriptText.isNotBlank()) {
                Text("Instalar", fontWeight = FontWeight.Bold)
            }
            if (scriptInstalled) {
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        Shell.su("rm -f /data/adb/service.d/yxa_boot.sh")
                    }
                    scriptInstalled = false
                    statusMsg = "Script eliminado"
                    prefs.edit().remove("boot_script").apply()
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Desinstalar", color = Color(0xFFFF5252))
                }
            }
        }

        statusMsg?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
            }
        }
        if (scriptInstalled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("Script instalado en /data/adb/service.d/yxa_boot.sh", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        Spacer(modifier = Modifier.height(110.dp))

        if (showConfirm) {
            AlertDialog(onDismissRequest = { showConfirm = false },
                title = { Text("Instalar script de arranque") },
                text = { Text("Este script se ejecutará como root en cada arranque del dispositivo. Asegúrate de que el contenido es correcto.") },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirm = false
                        prefs.edit().putString("boot_script", scriptText).apply()
                        scope.launch(Dispatchers.IO) {
                            val tmpFile = java.io.File(context.cacheDir, "yxa_boot_tmp.sh")
                            tmpFile.writeText(scriptText)
                            Shell.su("mkdir -p /data/adb/service.d")
                            Shell.su("cp ${tmpFile.absolutePath} /data/adb/service.d/yxa_boot.sh")
                            Shell.su("chmod 755 /data/adb/service.d/yxa_boot.sh")
                            tmpFile.delete()
                        }
                        scriptInstalled = true
                        statusMsg = "Script instalado correctamente"
                    }) { Text("Instalar") }
                },
                dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancelar") } })
            }

        if (showDangerWarning) {
            AlertDialog(
                onDismissRequest = { showDangerWarning = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp)) },
                title = { Text("Advertencia de Seguridad Critica", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                text = { Text("Hemos detectado un comando altamente peligroso en tu script (ej. apagar el nucleo principal de CPU). Esto tiene una alta probabilidad de causar un BOOTLOOP e inutilizar tu dispositivo hasta un reinicio forzado. Estas completamente seguro de instalarlo?", color = MaterialTheme.colorScheme.onSurface) },
                confirmButton = {
                    TextButton(onClick = {
                        showDangerWarning = false
                        showConfirm = true
                    }) { Text("Entiendo el riesgo", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDangerWarning = false }) { Text("Cancelar") }
                }
            )
        }
        }
    }
}
