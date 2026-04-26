package com.shni.yxa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.shni.yxa.data.GameProfile
import com.shni.yxa.data.GameProfileRepository
import com.shni.yxa.util.Shell
import kotlinx.coroutines.*

class GameWatcherService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchJob: Job? = null
    private var activeGamePkg: String? = null

    companion object {
        private const val CHANNEL_ID = "yxa_services"
        private const val NOTIF_ID = 1003
        private const val POLL_INTERVAL_MS = 4_000L
        private const val TAG = "YxaGameWatcher"

        fun start(context: Context) {
            val intent = Intent(context, GameWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameWatcherService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Servicios de Optimizacion Yxa", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Yxa Activo")
            .setContentText("Vigilando juegos configurados")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } catch (_: Exception) {
                startForeground(NOTIF_ID, notification)
            }
        } else {
            startForeground(NOTIF_ID, notification)
        }

        startWatching()
        return START_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startWatching() {
        watchJob?.cancel()
        watchJob = scope.launch {
            val repo = GameProfileRepository(this@GameWatcherService)

            while (isActive) {
                try {
                    val foregroundPkg = detectForegroundPackage()

                    if (foregroundPkg != null && foregroundPkg != activeGamePkg) {
                        val profile = repo.get(foregroundPkg)
                        if (profile != null) {
                            Log.i(TAG, "Game detected in foreground: $foregroundPkg - applying profile")
                            activeGamePkg = foregroundPkg
                            applyGameProfile(profile)
                            withContext(Dispatchers.Main) {
                                startOverlayIfPossible(profile.packageName)
                            }
                            updateNotification("Perfil activo: ${profile.label}")
                        }
                    } else if (foregroundPkg != null && foregroundPkg != activeGamePkg && activeGamePkg != null) {
                        Log.i(TAG, "Game left foreground: $activeGamePkg")
                        activeGamePkg = null
                        updateNotification("Vigilando juegos configurados")
                    } else if (foregroundPkg == null && activeGamePkg != null) {
                        activeGamePkg = null
                        updateNotification("Vigilando juegos configurados")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Watcher cycle error", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun detectForegroundPackage(): String? {
        return try {
            val output = Shell.su(
                "dumpsys window displays 2>/dev/null | grep mCurrentFocus",
                silentLog = true
            ) ?: return null

            Regex("""[a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+""")
                .findAll(output)
                .map { it.value }
                .firstOrNull { it.contains(".") && !it.startsWith("android") && it != "com.shni.yxa" }
        } catch (_: Exception) { null }
    }

    private suspend fun applyGameProfile(game: GameProfile) {
        try {
            val gov = when (game.perfMode) { 0 -> "powersave"; 2 -> "performance"; else -> "schedutil" }
            Shell.su("for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \$c 2>/dev/null; done")

            Shell.su("settings put system min_refresh_rate ${game.refreshRate}")
            Shell.su("settings put system peak_refresh_rate ${game.refreshRate}")

            if (game.resolution < 100) {
                val rawSize = Shell.su("wm size")?.substringAfter("Physical size:")?.trim()
                val rawDensity = Shell.su("wm density")?.substringAfter("Physical density:")?.substringBefore('\n')?.trim()
                if (rawSize != null && rawDensity != null) {
                    val parts = rawSize.split("x")
                    if (parts.size == 2) {
                        val w = parts[0].trim().toIntOrNull()
                        val h = parts[1].trim().toIntOrNull()
                        if (w != null && h != null) {
                            Shell.su("wm size ${w * game.resolution / 100}x${h * game.resolution / 100}")
                            rawDensity.toIntOrNull()?.let { d ->
                                Shell.su("wm density ${d * game.resolution / 100}")
                            }
                        }
                    }
                }
            }

            if (game.graphicsApi != "default") {
                Shell.su("settings put global angle_gl_driver_selection_pkgs ${game.packageName}")
                Shell.su("settings put global angle_gl_driver_selection_values ${game.graphicsApi}")
            }

            if (game.touchSensitivity) Shell.su("echo 1 > /proc/touchpanel/game_switch_enable 2>/dev/null")
            if (game.wifiLowLatency) {
                Shell.su("sysctl -w net.ipv4.tcp_low_latency=1 2>/dev/null")
                Shell.su("sysctl -w net.ipv4.tcp_congestion_control=${game.tcpCongestion} 2>/dev/null")
                if (game.advancedNet) {
                    Shell.su("sysctl -w net.ipv4.tcp_rmem='4096 87380 1048576' 2>/dev/null")
                    Shell.su("sysctl -w net.ipv4.tcp_wmem='4096 16384 1048576' 2>/dev/null")
                }
            }
            if (game.gpuBoost) {
                Shell.su("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null")
                Shell.su("echo performance > /sys/class/devfreq/*gpu*/governor 2>/dev/null")
            }
            if (game.dndEnabled) Shell.su("cmd notification set_dnd priority 2>/dev/null")
            if (game.aggressiveClear) Shell.su("sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null")
            if (game.disableZram) Shell.su("sysctl -w vm.swappiness=10 2>/dev/null")
            if (game.killBackground) Shell.su("am kill-all 2>/dev/null; am shrink-all 2>/dev/null")

            Log.i(TAG, "Profile applied for ${game.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply profile for ${game.packageName}", e)
        }
    }

    private fun startOverlayIfPossible(packageName: String) {
        try {
            if (Settings.canDrawOverlays(this)) {
                val prefs = getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("overlay_active", true)
                    .putString("last_overlay_pkg", packageName)
                    .apply()
                GameOverlayService.start(this, packageName)
                Log.i(TAG, "Overlay started for $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start overlay", e)
        }
    }

    private fun updateNotification(text: String) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Yxa Activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notification)
    }
}
