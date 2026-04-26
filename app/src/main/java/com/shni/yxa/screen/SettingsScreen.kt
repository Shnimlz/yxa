package com.shni.yxa.screen

import android.content.Context
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shni.yxa.ui.theme.YxaThemeMode

import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ChevronRight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(currentTheme: YxaThemeMode, onThemeChange: (YxaThemeMode) -> Unit, onNavigateToBootScripts: () -> Unit) {
    val context = LocalContext.current
    val supportsSystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent, titleContentColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text("Tema de la app", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)

            // Green AMOLED
            ThemeCard(
                name = "Verde AMOLED",
                desc = "Negro puro con acentos verdes neón",
                colors = listOf(Color(0xFF66FF99), Color(0xFF00C853), Color(0xFFCCFF90)),
                selected = currentTheme == YxaThemeMode.GREEN_AMOLED,
                onClick = { onThemeChange(YxaThemeMode.GREEN_AMOLED) }
            )

            // Orange AMOLED
            ThemeCard(
                name = "Naranja AMOLED",
                desc = "Negro puro con acentos ámbar cálidos",
                colors = listOf(Color(0xFFFFAB40), Color(0xFFE65100), Color(0xFFFFE082)),
                selected = currentTheme == YxaThemeMode.ORANGE_AMOLED,
                onClick = { onThemeChange(YxaThemeMode.ORANGE_AMOLED) }
            )

            // System
            ThemeCard(
                name = "Colores del sistema",
                desc = if (supportsSystem) "Basado en tu fondo de pantalla (Material You)" else "Requiere Android 12+ — no disponible",
                colors = listOf(Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260)),
                selected = currentTheme == YxaThemeMode.SYSTEM,
                onClick = { if (supportsSystem) onThemeChange(YxaThemeMode.SYSTEM) },
                enabled = supportsSystem
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Boot Scripts
            Card(onClick = onNavigateToBootScripts, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Scripts de arranque", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Configura scripts init.d / service.d", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // App info
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Acerca de Yxa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Kernel Path, Root y Performance para Android + Modo Juego", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("v1.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }

            // Reset welcome
            var resetDone by remember { mutableStateOf(false) }
            OutlinedButton(onClick = {
                context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE).edit().putBoolean("welcome_seen", false).apply()
                resetDone = true
            }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                if (resetDone) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (resetDone) "Se mostrará al reiniciar" else "Mostrar pantalla de bienvenida", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(110.dp))
    }
}

@Composable
private fun ThemeCard(name: String, desc: String, colors: List<Color>, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(
            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (enabled) 1f else 0.4f)),
        shape = RoundedCornerShape(20.dp),
        enabled = enabled
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Color preview dots
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                colors.forEach { c ->
                    Box(Modifier.size(28.dp).clip(CircleShape).border(2.dp, Color.Black, CircleShape).then(Modifier.padding(0.dp))) {
                        Surface(Modifier.fillMaxSize(), color = c, shape = CircleShape) {}
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
