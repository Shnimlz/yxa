package com.shni.yxa.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.shni.yxa.util.Shell
import kotlinx.coroutines.*
import java.io.File

class AutoRamCleanerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notifId = 1001
    private val channelId = "yxa_services"
    private val textIdle = "Optimizando el sistema en segundo plano"
    private val textCleaned = "Limpieza profunda ejecutada. RAM liberada."

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Servicios de Optimizacion Yxa", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Yxa Activo")
            .setContentText(textIdle)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(notifId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } catch (_: Exception) {
                startForeground(notifId, notification)
            }
        } else {
            startForeground(notifId, notification)
        }

        serviceScope.launch {
            while (isActive) {
                try {
                    val prefs = getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE)
                    val isEnabled = prefs.getBoolean("auto_ram_clean", false)

                    if (!isEnabled) {
                        stopSelf()
                        break
                    }

                    val threshold = prefs.getInt("auto_ram_clean_threshold", 75)

                    val meminfo = File("/proc/meminfo").readText()
                    var memTotal = 0L
                    var memAvailable = 0L

                    meminfo.lines().forEach { line ->
                        when {
                            line.startsWith("MemTotal:") ->
                                memTotal = line.substringAfter("MemTotal:").substringBefore("kB").trim().toLongOrNull() ?: 0L
                            line.startsWith("MemAvailable:") ->
                                memAvailable = line.substringAfter("MemAvailable:").substringBefore("kB").trim().toLongOrNull() ?: 0L
                        }
                    }

                    var didClean = false
                    if (memTotal > 0) {
                        val usedPercent = (((memTotal - memAvailable).toDouble() / memTotal) * 100).toInt()

                        if (usedPercent > threshold) {
                            Shell.su("sync; echo 3 > /proc/sys/vm/drop_caches")

                            val whitelist = prefs.getStringSet("ram_whitelist", emptySet()) ?: emptySet()
                            val output = Shell.su("pm list packages -3", silentLog = true)
                            if (output != null) {
                                output.lines()
                                    .filter { it.startsWith("package:") }
                                    .map { it.removePrefix("package:").trim() }
                                    .filter { it.isNotBlank() && it != "com.shni.yxa" && !whitelist.contains(it) }
                                    .forEach { pkg -> Shell.su("am kill $pkg", silentLog = true) }
                            }

                            didClean = true
                            updateNotification(textCleaned)
                        }
                    }

                    if (didClean) {
                        delay(10_000)
                        updateNotification(textIdle)
                        delay(50_000)
                    } else {
                        delay(5_000)
                    }

                } catch (_: Exception) {
                    delay(5_000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Yxa Activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(notifId, notification)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AutoRamCleanerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoRamCleanerService::class.java))
        }
    }
}
