package com.shni.yxa.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.shni.yxa.util.Shell
import kotlinx.coroutines.*

/**
 * Foreground service that monitors network jitter every 30 seconds.
 * If jitter exceeds the threshold, it blocks non-essential app traffic
 * using iptables to protect the gaming session.
 */
class LagProtectorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var isShielded = false

    companion object {
        private const val CHANNEL_ID = "yxa_lag_protector"
        private const val NOTIF_ID = 2002
        private const val JITTER_THRESHOLD_MS = 30.0
        private const val CHECK_INTERVAL_MS = 30_000L
        private const val TEST_SERVER_IP = "121.127.43.65"
        private const val TEST_SERVER_PORT = "5201"

        private val JITTER_REGEX = Regex("""(\d+\.?\d*)\s+ms\s+\d+""")

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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Monitoreando estabilidad de red..."))
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
                            activateShield()
                            isShielded = true
                            updateNotification("Red inestable (${String.format("%.1f", jitter)}ms). Escudo ACTIVO.")
                        }
                    } else if (isShielded) {
                        cleanupIptables()
                        isShielded = false
                        updateNotification("Red estable. Monitoreando...")
                    }
                } catch (_: Exception) { }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Runs a quick 2-second UDP iperf3 test and extracts the jitter value.
     */
    private fun measureJitter(): Double? {
        val binPath = Shell.INTERNAL_BIN_PATH
        if (binPath.isEmpty()) return null

        val output = Shell.su(
            "${binPath}iperf3 -c $TEST_SERVER_IP -p $TEST_SERVER_PORT -u -t 2 2>&1",
            timeoutSec = 8,
            silentLog = true
        ) ?: return null

        // Find jitter in the sender summary line
        val lines = output.lines()
        for (line in lines) {
            if (line.contains("sender", ignoreCase = true) || line.contains("/sec", ignoreCase = true)) {
                val match = JITTER_REGEX.find(line)
                if (match != null) {
                    return match.groupValues[1].toDoubleOrNull()
                }
            }
        }
        return null
    }

    /**
     * Blocks network traffic from non-essential user apps using iptables.
     * Preserves system UIDs (< 10000) and the active game.
     */
    private fun activateShield() {
        try {
            // Get all user-installed package UIDs
            val packagesOutput = Shell.su("pm list packages -U", silentLog = true) ?: return
            val uidSet = mutableSetOf<Int>()

            for (line in packagesOutput.lines()) {
                // Format: package:com.example.app uid:10123
                val uidMatch = Regex("""uid:(\d+)""").find(line)
                val uid = uidMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
                // Only block user apps (UID >= 10000), skip system
                if (uid >= 10000) {
                    uidSet.add(uid)
                }
            }

            // Create a dedicated chain to avoid polluting the OUTPUT chain
            Shell.su("iptables -N YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
            Shell.su("iptables -F YXA_LAG_SHIELD", silentLog = true)
            Shell.su("iptables -D OUTPUT -j YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
            Shell.su("iptables -A OUTPUT -j YXA_LAG_SHIELD", silentLog = true)

            for (uid in uidSet) {
                Shell.su(
                    "iptables -A YXA_LAG_SHIELD -m owner --uid-owner $uid -j REJECT",
                    silentLog = true
                )
            }
        } catch (_: Exception) { }
    }

    /**
     * Removes all iptables rules created by the shield.
     */
    private fun cleanupIptables() {
        try {
            Shell.su("iptables -D OUTPUT -j YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
            Shell.su("iptables -F YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
            Shell.su("iptables -X YXA_LAG_SHIELD 2>/dev/null", silentLog = true)
        } catch (_: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Escudo Anti-Lag",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo de estabilidad de red en segundo plano"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Yxa - Escudo Anti-Lag")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
