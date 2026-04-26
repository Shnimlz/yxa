package com.shni.yxa.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shni.yxa.util.Shell
import kotlinx.coroutines.*
import java.io.File

class AutoRamCleanerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channelId = "yxa_ram_cleaner_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Yxa Auto RAM Cleaner", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Yxa Auto-Drop")
            .setContentText("Monitor de memoria RAM activo")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Needed to avoid ForegroundServiceTypeNotDeclaredException if targetSDK >= 34
            try {
                startForeground(3001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } catch (e: Exception) {
                startForeground(3001, notification)
            }
        } else {
            startForeground(3001, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                        if (line.startsWith("MemTotal:")) {
                            memTotal = line.substringAfter("MemTotal:").substringBefore("kB").trim().toLongOrNull() ?: 0L
                        } else if (line.startsWith("MemAvailable:")) {
                            memAvailable = line.substringAfter("MemAvailable:").substringBefore("kB").trim().toLongOrNull() ?: 0L
                        }
                    }

                    var delayedLong = false
                    if (memTotal > 0) {
                        val memUsed = memTotal - memAvailable
                        val usedPercent = ((memUsed.toDouble() / memTotal.toDouble()) * 100).toInt()
                        
                        if (usedPercent > threshold) {
                            Shell.su("sync; echo 3 > /proc/sys/vm/drop_caches")
                            
                            val whitelist = prefs.getStringSet("ram_whitelist", emptySet()) ?: emptySet()
                            val output = Shell.su("pm list packages -3", silentLog = true)
                            if (output != null) {
                                val packagesToKill = output.lines()
                                    .filter { it.startsWith("package:") }
                                    .map { it.removePrefix("package:").trim() }
                                    .filter { it.isNotBlank() && it != "com.shni.yxa" && !whitelist.contains(it) }
                                
                                packagesToKill.forEach { pkg ->
                                    Shell.su("am kill $pkg", silentLog = true)
                                }
                            }
                            
                            delayedLong = true
                        }
                    }
                    
                    if (delayedLong) {
                        delay(60000)
                    } else {
                        delay(5000)
                    }

                } catch (e: Exception) {
                    delay(5000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
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
            val intent = Intent(context, AutoRamCleanerService::class.java)
            context.stopService(intent)
        }
    }
}
