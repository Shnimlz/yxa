package com.shni.yxa.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.launch

object BootScriptManager {
    private const val PREFS_NAME = "yxa_boot_scripts"
    private const val PREF_CPU_ENABLED = "apply_on_boot_cpu"
    private const val PREF_RAM_ENABLED = "apply_on_boot_ram"
    private const val PREF_NET_ENABLED = "apply_on_boot_net"
    private const val PREF_GPU_ENABLED = "apply_on_boot_gpu"

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

    fun isGpuEnabled(context: Context): Boolean = getPrefs(context).getBoolean(PREF_GPU_ENABLED, false)
    fun setGpuEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_GPU_ENABLED, enabled).apply()
        regenerateScript(context)
    }

    fun putGpuCommand(context: Context, key: String, command: String) {
        getPrefs(context).edit().putString("gpu_$key", command).apply()
        if (isGpuEnabled(context)) regenerateScript(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var debounceJob: kotlinx.coroutines.Job? = null

    private fun regenerateScript(context: Context) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            kotlinx.coroutines.delay(500) // Coalesce rapid successive calls into one write
            val prefs = getPrefs(context)
        val cpuEnabled = prefs.getBoolean(PREF_CPU_ENABLED, false)
        val ramEnabled = prefs.getBoolean(PREF_RAM_ENABLED, false)
        val netEnabled = prefs.getBoolean(PREF_NET_ENABLED, false)
        val gpuEnabled = prefs.getBoolean(PREF_GPU_ENABLED, false)

        // SAFETY: Filter out dangerous commands that can cause bootloop
        val dangerousKeys = setOf("online") // core hotplugging at boot = bootloop
        
        val cpuCommands = if (cpuEnabled) {
            prefs.all.filterKeys { key ->
                key.startsWith("cpu_") && dangerousKeys.none { danger -> key.contains(danger) }
            }.values.map { it.toString() }
        } else emptyList()
        
        val ramCommands = if (ramEnabled) prefs.all.filterKeys { it.startsWith("ram_") }.values.map { it.toString() } else emptyList()
        val netCommands = if (netEnabled) prefs.all.filterKeys { it.startsWith("net_") }.values.map { it.toString() } else emptyList()
        val gpuCommands = if (gpuEnabled) prefs.all.filterKeys { it.startsWith("gpu_") }.values.map { it.toString() } else emptyList()

        if (cpuCommands.isEmpty() && ramCommands.isEmpty() && netCommands.isEmpty() && gpuCommands.isEmpty()) {
            Shell.su("rm -f /data/adb/service.d/yxa_boot.sh")
            return@launch
        }

        val scriptBuilder = StringBuilder()
        scriptBuilder.appendLine("#!/system/bin/sh")
        scriptBuilder.appendLine("# Yxa Auto-Generated Boot Script")
        scriptBuilder.appendLine("# Safety: errors are suppressed to prevent bootloop")
        scriptBuilder.appendLine("(")
        scriptBuilder.appendLine("  set +e")
        scriptBuilder.appendLine("  sleep 60")
        
        if (cpuCommands.isNotEmpty()) {
            scriptBuilder.appendLine("  sleep 1")
            scriptBuilder.appendLine("  # Comandos de CPU")
            cpuCommands.forEach { scriptBuilder.appendLine("  $it 2>/dev/null || true") }
        }

        if (ramCommands.isNotEmpty()) {
            scriptBuilder.appendLine("  sleep 1")
            scriptBuilder.appendLine("  # Comandos de RAM")
            ramCommands.forEach { scriptBuilder.appendLine("  $it 2>/dev/null || true") }
        }

        if (netCommands.isNotEmpty()) {
            scriptBuilder.appendLine("  sleep 1")
            scriptBuilder.appendLine("  # Comandos de RED")
            netCommands.forEach { scriptBuilder.appendLine("  $it 2>/dev/null || true") }
        }

        if (gpuCommands.isNotEmpty()) {
            scriptBuilder.appendLine("  sleep 1")
            scriptBuilder.appendLine("  # Comandos de GPU")
            gpuCommands.forEach { scriptBuilder.appendLine("  $it 2>/dev/null || true") }
        }
        
        scriptBuilder.appendLine(") &")

        val scriptContent = scriptBuilder.toString()
        
        // Safe write: use temp file instead of heredoc (cat << EOF fails via ProcessBuilder)
        val tmpFile = java.io.File(context.cacheDir, "yxa_boot_tmp.sh")
        tmpFile.writeText(scriptContent)
        
        Shell.su("mkdir -p /data/adb/service.d")
        Shell.su("cp ${tmpFile.absolutePath} /data/adb/service.d/yxa_boot.sh")
        Shell.su("chmod 755 /data/adb/service.d/yxa_boot.sh")
        tmpFile.delete()
        }
    }

    fun applyBootOptimizations(context: Context) {
        scope.launch {
            val prefs = getPrefs(context)
            val cpuEnabled = prefs.getBoolean(PREF_CPU_ENABLED, false)
            val ramEnabled = prefs.getBoolean(PREF_RAM_ENABLED, false)
            val netEnabled = prefs.getBoolean(PREF_NET_ENABLED, false)
            val gpuEnabled = prefs.getBoolean(PREF_GPU_ENABLED, false)

            val dangerousKeys = setOf("online")
            
            val commands = mutableListOf<String>()
            
            if (cpuEnabled) {
                commands.addAll(prefs.all.filterKeys { it.startsWith("cpu_") && dangerousKeys.none { d -> it.contains(d) } }.values.map { it.toString() })
            }
            if (ramEnabled) {
                commands.addAll(prefs.all.filterKeys { it.startsWith("ram_") }.values.map { it.toString() })
            }
            if (netEnabled) {
                commands.addAll(prefs.all.filterKeys { it.startsWith("net_") }.values.map { it.toString() })
            }
            if (gpuEnabled) {
                commands.addAll(prefs.all.filterKeys { it.startsWith("gpu_") }.values.map { it.toString() })
            }

            if (commands.isNotEmpty()) {
                val fullCommand = commands.joinToString(";")
                Shell.su(fullCommand)
            }
        }
    }
}
