package com.shni.yxa.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.shni.yxa.util.Shell
import kotlinx.coroutines.*

class LagProtectorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var isShielded = false

    companion object {
        private const val CHANNEL_ID = "yxa_services"
        private const val NOTIF_ID = 1002
        private const val JITTER_THRESHOLD_MS = 30.0
        private const val CHECK_INTERVAL_MS = 30_000L

        private val MTU_CANDIDATES = listOf(1500, 1492, 1460, 1400)
        private val MDEV_REGEX = Regex("""rtt min/avg/max/mdev = [\d.]+/[\d.]+/[\d.]+/([\d.]+)""")

        fun start(context: Context) {
            val intent = Intent(context, LagProtectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LagProtectorService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Servicios de Optimizacion Yxa", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Yxa Activo")
            .setContentText("Escudo Anti-Lag en standby")
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

        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        cleanupIptables()
        super.onDestroy()
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                try {
                    val jitter = measureJitter()
                    if (jitter != null && jitter > JITTER_THRESHOLD_MS) {
                        if (!isShielded) {
                            val mtu = applyBestMtu()
                            activateShield()
                            isShielded = true
                            updateNotification("Red inestable (${String.format("%.1f", jitter)}ms) | Escudo ACTIVO | MTU: $mtu")
                        }
                    } else if (isShielded) {
                        cleanupIptables()
                        isShielded = false
                        updateNotification("Escudo Anti-Lag en standby")
                    }
                } catch (_: Exception) { }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun measureJitter(): Double? {
        return try {
            val output = Shell.su("ping -c 5 -q 8.8.8.8 2>/dev/null", timeoutSec = 15, silentLog = true)
                ?: return null
            val statsLine = output.lines().lastOrNull { it.trimStart().startsWith("rtt") }
                ?: return null
            MDEV_REGEX.find(statsLine)?.groupValues?.get(1)?.toDoubleOrNull()
        } catch (_: Exception) { null }
    }

    private fun applyBestMtu(): Int {
        var bestMtu = 1500
        try {
            for (mtu in MTU_CANDIDATES) {
                val testSize = mtu - 28
                val res = Shell.su(
                    "ping -c 1 -M do -s $testSize 8.8.8.8 2>/dev/null",
                    silentLog = true
                )
                if (res != null && !res.contains("Frag needed") && res.contains("bytes from")) {
                    bestMtu = mtu
                    break
                }
            }
            Shell.su(
                "ifconfig wlan0 mtu $bestMtu 2>/dev/null || ip link set dev wlan0 mtu $bestMtu 2>/dev/null",
                silentLog = true
            )
        } catch (_: Exception) { }
        return bestMtu
    }

    private fun activateShield() {
        try {
            val activeUid = resolveActiveGameUid()

            val packagesOutput = Shell.su("pm list packages -U", silentLog = true) ?: return
            val uidSet = mutableSetOf<Int>()

            for (line in packagesOutput.lines()) {
                val uidMatch = Regex("""uid:(\d+)""").find(line)
                val uid = uidMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (uid >= 10000 && uid != activeUid) {
                    uidSet.add(uid)
                }
            }

            Shell.su("iptables -N YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
            Shell.su("iptables -F YXA_LAG_SHIELD", silentLog = true)
            Shell.su("iptables -D OUTPUT -j YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
            Shell.su("iptables -A OUTPUT -j YXA_LAG_SHIELD", silentLog = true)

            for (uid in uidSet) {
                Shell.su("iptables -A YXA_LAG_SHIELD -m owner --uid-owner $uid -j REJECT", silentLog = true)
            }
        } catch (_: Exception) { }
    }

    private fun resolveActiveGameUid(): Int? {
        return try {
            val focusOutput = Shell.su(
                "dumpsys window displays 2>/dev/null | grep mCurrentFocus",
                silentLog = true
            ) ?: return null

            val packageName = Regex("""[a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+""")
                .findAll(focusOutput)
                .map { it.value }
                .firstOrNull { it.contains(".") && !it.startsWith("android") }
                ?: return null

            val uidOutput = Shell.su(
                "pm list packages -U 2>/dev/null | grep $packageName",
                silentLog = true
            ) ?: return null

            Regex("""uid:(\d+)""").find(uidOutput)?.groupValues?.get(1)?.toIntOrNull()
        } catch (_: Exception) { null }
    }

    private fun cleanupIptables() {
        try {
            Shell.su("iptables -D OUTPUT -j YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
            Shell.su("iptables -F YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
            Shell.su("iptables -X YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
        } catch (_: Exception) { }
    }

    private fun updateNotification(text: String) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Yxa Activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, notification)
    }
}
