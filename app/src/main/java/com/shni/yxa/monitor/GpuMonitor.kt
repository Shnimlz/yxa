package com.shni.yxa.monitor

import com.shni.yxa.util.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GpuMonitor {
    private var detectedPath: String? = null
    private var detectionAttempted = false
    private var readMode: ReadMode = ReadMode.DIRECT_PERCENTAGE

    private enum class ReadMode { DIRECT_PERCENTAGE, BUSY_TOTAL }
    private data class GpuPath(val path: String, val mode: ReadMode)

    private val candidatePaths = listOf(
        GpuPath("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", ReadMode.DIRECT_PERCENTAGE),
        GpuPath("/sys/class/kgsl/kgsl-3d0/gpubusy", ReadMode.BUSY_TOTAL),
        GpuPath("/sys/class/misc/mali0/device/utilization", ReadMode.DIRECT_PERCENTAGE),
    )

    suspend fun readGpuLoad(): Float? = withContext(Dispatchers.IO) {
        if (!detectionAttempted) {
            detectGpuPath()
            detectionAttempted = true
        }
        val path = detectedPath ?: return@withContext null
        try {
            val output = Shell.su("cat $path", silentLog = true) ?: return@withContext null
            when (readMode) {
                ReadMode.DIRECT_PERCENTAGE -> {
                    output.filter { it.isDigit() || it == '.' }.toFloatOrNull()?.coerceIn(0f, 100f)
                }
                ReadMode.BUSY_TOTAL -> {
                    val parts = output.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val busy = parts[0].toLongOrNull() ?: 0L
                        val total = parts[1].toLongOrNull() ?: 1L
                        if (total > 0) (busy.toFloat() / total * 100f).coerceIn(0f, 100f) else 0f
                    } else null
                }
            }
        } catch (_: Exception) { null }
    }

    private suspend fun detectGpuPath() = withContext(Dispatchers.IO) {
        for (c in candidatePaths) {
            val out = Shell.su("cat ${c.path}", timeoutSec = 2, silentLog = true)
            if (out != null) { detectedPath = c.path; readMode = c.mode; return@withContext }
        }
        try {
            val nodes = Shell.su("ls /sys/class/devfreq/", timeoutSec = 2, silentLog = true) ?: return@withContext
            val gpuNode = nodes.lines().firstOrNull {
                it.contains("gpu", true) || it.contains("kgsl", true) || it.contains("mali", true)
            } ?: return@withContext
            val loadPath = "/sys/class/devfreq/$gpuNode/load"
            if (Shell.su("cat $loadPath", timeoutSec = 2, silentLog = true) != null) {
                detectedPath = loadPath; readMode = ReadMode.DIRECT_PERCENTAGE
            }
        } catch (_: Exception) {}
    }

    fun isAvailable(): Boolean = detectedPath != null
}
