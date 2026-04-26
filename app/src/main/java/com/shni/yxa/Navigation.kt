package com.shni.yxa

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen {
    data object Dashboard : Screen()
    data object CpuDetail : Screen()
    data object GpuDetail : Screen()
    data object RamDetail : Screen()
    data object GameTime : Screen()
    data class GameSettings(val packageName: String) : Screen()
    data object Settings : Screen()
    data object BootScripts : Screen()
    data object NetworkSettings : Screen()
    data object ProcessMonitor : Screen()
}

enum class BottomTab(val label: String, val icon: ImageVector) {
    RED("Red", Icons.Default.Router),
    HOME("Inicio", Icons.Default.Home),
    PROC("Procesos", Icons.Default.Memory),
    LOGS("Logs", Icons.AutoMirrored.Filled.ReceiptLong),
    GAME("GameTime", Icons.Default.VideogameAsset)
}
