package com.shni.yxa.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object BackupManager {
    private const val BACKUP_FILE_NAME = "factory_backup.json"

    private val pathsToBackup = listOf(
        "/proc/sys/net/ipv4/tcp_congestion_control",
        "/proc/sys/vm/swappiness",
        "/proc/sys/vm/vfs_cache_pressure",
        "/proc/sys/vm/dirty_ratio",
        "/proc/sys/vm/dirty_background_ratio",
        "/proc/sys/vm/min_free_kbytes",
        "/sys/block/sda/queue/scheduler",
        "/sys/block/mmcblk0/queue/scheduler",
        "/sys/class/net/wlan0/mtu"
    )

    suspend fun createBackup(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, BACKUP_FILE_NAME)
        if (file.exists()) {
            return@withContext // Backup already exists, abort
        }

        val jsonObject = JSONObject()

        for (path in pathsToBackup) {
            val result = Shell.su("cat $path")
            if (result != null && result.isNotBlank() && !result.contains("No such file", ignoreCase = true) && !result.contains("Permission denied", ignoreCase = true)) {
                var valueToSave = result.trim()
                // Extraer el valor seleccionado si tiene formato de scheduler ej: noop deadline [cfq]
                if (valueToSave.contains("[") && valueToSave.contains("]")) {
                    val match = Regex("\\[(.*?)\\]").find(valueToSave)
                    if (match != null) {
                        valueToSave = match.groupValues[1]
                    }
                }
                jsonObject.put(path, valueToSave)
            }
        }

        try {
            file.writeText(jsonObject.toString(4))
            Log.d("BackupManager", "Factory backup created successfully.")
        } catch (e: Exception) {
            Log.e("BackupManager", "Failed to write backup file: ${e.message}")
        }
    }

    suspend fun restoreBackup(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, BACKUP_FILE_NAME)
        if (!file.exists()) return@withContext

        try {
            val jsonText = file.readText()
            val jsonObject = JSONObject(jsonText)

            val keys = jsonObject.keys()
            val commands = mutableListOf<String>()

            while (keys.hasNext()) {
                val path = keys.next()
                val value = jsonObject.getString(path)
                commands.add("echo '$value' > $path")
            }

            if (commands.isNotEmpty()) {
                // Execute all restores in a single root call for speed
                Shell.su(commands.joinToString(" ; "))
            }

            // Clean SharedPreferences related to Yxa optimizations
            val prefs = context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Loop through all keys and remove them except 'welcome_seen' and 'theme'
            for (key in prefs.all.keys) {
                if (key != "welcome_seen" && key != "theme") {
                    editor.remove(key)
                }
            }
            editor.apply()

        } catch (e: Exception) {
            Log.e("BackupManager", "Failed to restore backup: ${e.message}")
        }
    }
}
