package com.shni.yxa.util

import java.util.concurrent.TimeUnit

/**
 * Shared utility for executing root commands safely.
 * Uses ProcessBuilder with redirectErrorStream to prevent buffer deadlocks.
 */
object Shell {
    var INTERNAL_BIN_PATH = ""
    val BUSYBOX get() = "${INTERNAL_BIN_PATH}busybox"

    fun runBusy(cmd: String, timeoutSec: Long = 3, silentLog: Boolean = false): String? {
        return su("$BUSYBOX $cmd", timeoutSec, silentLog)
    }

    /**
     * Execute a command via su. Returns stdout or null on failure.
     * stderr is merged with stdout to avoid buffer deadlocks.
     * All commands have a timeout to prevent hangs.
     * @param silentLog If true, the command will not be recorded in LogManager.
     */
    fun su(command: String, timeoutSec: Long = 3, silentLog: Boolean = false): String? {
        return try {
            val pb = ProcessBuilder("su", "-c", "$command 2>/dev/null")
            pb.redirectErrorStream(true)
            val process = pb.start()

            val output = process.inputStream.bufferedReader().use { it.readText().trim() }

            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }

            val exitCode = process.exitValue()
            val isSuccess = exitCode == 0
            if (!silentLog) LogManager.addLog(command, isSuccess)
            
            if (isSuccess && output.isNotBlank()) output else null
        } catch (e: Exception) {
            if (!silentLog) LogManager.addLog(command, false)
            null
        }
    }

    /**
     * Execute a su command and return true if it succeeded (exit code 0).
     * @param silentLog If true, the command will not be recorded in LogManager.
     */
    fun suTest(command: String, silentLog: Boolean = false): Boolean {
        return try {
            val pb = ProcessBuilder("su", "-c", command)
            pb.redirectErrorStream(true)
            val process = pb.start()
            process.inputStream.bufferedReader().use { it.readText() } // drain
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            val exitCode = process.exitValue()
            val isSuccess = exitCode == 0
            if (!silentLog) LogManager.addLog(command, isSuccess)
            isSuccess
        } catch (e: Exception) {
            if (!silentLog) LogManager.addLog(command, false)
            false
        }
    }
}
