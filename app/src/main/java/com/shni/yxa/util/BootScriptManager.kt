package com.shni.yxa.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*

object BootScriptManager {
    private const val PREFS_NAME = "yxa_boot_scripts"
    private const val PREF_CPU_ENABLED = "apply_on_boot_cpu"
    private const val PREF_RAM_ENABLED = "apply_on_boot_ram"
    private const val PREF_NET_ENABLED = "apply_on_boot_net"
    private const val PREF_GPU_ENABLED = "apply_on_boot_gpu"

    private val DANGEROUS_KEYS = setOf("online", "offline", "thermal", "hotplug")

    fun isCpuEnabled(context: Context): Boolean = getPrefs(context).getBoolean(PREF_CPU_ENABLED, false)
    fun setCpuEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_CPU_ENABLED, enabled).apply()
        regenerateScript(context)
    }

    fun isRamEnabled(context: Context): Boolean = getPrefs(context).getBoolean(PREF_RAM_ENABLED, false)
    fun setRamEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_RAM_ENABLED, enabled).apply()
        regenerateScript(context)
    }

    fun isNetworkEnabled(context: Context): Boolean = getPrefs(context).getBoolean(PREF_NET_ENABLED, false)
    fun setNetworkEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_NET_ENABLED, enabled).apply()
        regenerateScript(context)
    }

    fun isGpuEnabled(context: Context): Boolean = getPrefs(context).getBoolean(PREF_GPU_ENABLED, false)
    fun setGpuEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_GPU_ENABLED, enabled).apply()
        regenerateScript(context)
    }

    fun putCpuCommand(context: Context, key: String, command: String) {
        getPrefs(context).edit().putString("cpu_$key", command).apply()
        if (isCpuEnabled(context)) regenerateScript(context)
    }

    fun putRamCommand(context: Context, key: String, command: String) {
        getPrefs(context).edit().putString("ram_$key", command).apply()
        if (isRamEnabled(context)) regenerateScript(context)
    }

    fun putNetworkCommand(context: Context, key: String, command: String) {
        getPrefs(context).edit().putString("net_$key", command).apply()
        if (isNetworkEnabled(context)) regenerateScript(context)
    }

    fun putGpuCommand(context: Context, key: String, command: String) {
        getPrefs(context).edit().putString("gpu_$key", command).apply()
        if (isGpuEnabled(context)) regenerateScript(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null

    private fun isSafeCommand(key: String): Boolean {
        return DANGEROUS_KEYS.none { danger -> key.contains(danger, ignoreCase = true) }
    }

    private fun sanitizeCommand(cmd: String): String {
        return cmd.trim()
            .replace("`", "")
            .replace("$(", "")
            .replace("&&", ";")
            .replace("||", ";")
    }

    private fun collectCommands(prefs: SharedPreferences, prefix: String): List<String> {
        return prefs.all
            .filterKeys { it.startsWith(prefix) && isSafeCommand(it) }
            .values
            .map { sanitizeCommand(it.toString()) }
            .filter { it.isNotBlank() }
    }

    private fun regenerateScript(context: Context) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(500)
            val prefs = getPrefs(context)

            val cpuCommands = if (prefs.getBoolean(PREF_CPU_ENABLED, false)) collectCommands(prefs, "cpu_") else emptyList()
            val ramCommands = if (prefs.getBoolean(PREF_RAM_ENABLED, false)) collectCommands(prefs, "ram_") else emptyList()
            val netCommands = if (prefs.getBoolean(PREF_NET_ENABLED, false)) collectCommands(prefs, "net_") else emptyList()
            val gpuCommands = if (prefs.getBoolean(PREF_GPU_ENABLED, false)) collectCommands(prefs, "gpu_") else emptyList()

            if (cpuCommands.isEmpty() && ramCommands.isEmpty() && netCommands.isEmpty() && gpuCommands.isEmpty()) {
                Shell.su("rm -f /data/adb/service.d/yxa_boot.sh")
                return@launch
            }

            val sb = StringBuilder()
            sb.appendLine("#!/system/bin/sh")
            sb.appendLine("# Yxa Boot Script - Auto-generated")
            sb.appendLine("# Runs in background subshell with error suppression")
            sb.appendLine("(")
            sb.appendLine("  set +e")
            sb.appendLine("  sleep 90")

            fun appendBlock(label: String, cmds: List<String>) {
                if (cmds.isNotEmpty()) {
                    sb.appendLine("  # $label")
                    cmds.forEach { cmd ->
                        cmd.split("\\n").forEach { line ->
                            if (line.isNotBlank()) {
                                val safeLine = if (line.contains("2>/dev/null")) line else "$line 2>/dev/null"
                                sb.appendLine("  $safeLine || true")
                            }
                        }
                    }
                    sb.appendLine("  sleep 2")
                }
            }

            appendBlock("CPU", cpuCommands)
            appendBlock("RAM", ramCommands)
            appendBlock("NET", netCommands)
            appendBlock("GPU", gpuCommands)

            sb.appendLine(") &")
            sb.appendLine("exit 0")

            val scriptContent = sb.toString()

            val tmpFile = java.io.File(context.cacheDir, "yxa_boot_tmp.sh")
            tmpFile.writeText(scriptContent)

            Shell.su("mkdir -p /data/adb/service.d")
            Shell.su("cp ${tmpFile.absolutePath} /data/adb/service.d/yxa_boot.sh")
            Shell.su("chmod 755 /data/adb/service.d/yxa_boot.sh")
            tmpFile.delete()
        }
    }

    suspend fun applyBootOptimizations(context: Context) {
        delay(90_000)

        val prefs = getPrefs(context)
        val commands = mutableListOf<String>()

        if (prefs.getBoolean(PREF_CPU_ENABLED, false)) commands.addAll(collectCommands(prefs, "cpu_"))
        if (prefs.getBoolean(PREF_RAM_ENABLED, false)) commands.addAll(collectCommands(prefs, "ram_"))
        if (prefs.getBoolean(PREF_NET_ENABLED, false)) commands.addAll(collectCommands(prefs, "net_"))
        if (prefs.getBoolean(PREF_GPU_ENABLED, false)) commands.addAll(collectCommands(prefs, "gpu_"))

        for (cmd in commands) {
            try {
                val lines = cmd.split("\\n").filter { it.isNotBlank() }
                for (line in lines) {
                    val safeLine = if (line.contains("2>/dev/null")) line else "$line 2>/dev/null"
                    Shell.su(safeLine, silentLog = true)
                }
            } catch (_: Exception) { }
        }
    }
}
