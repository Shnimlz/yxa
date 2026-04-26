package com.shni.yxa.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DependencyManager {
    private const val BIN_VERSION = 3

    suspend fun setupDependencies(context: Context) = withContext(Dispatchers.IO) {
        try {
            val destDir = File(context.filesDir, "bin")
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            
            Shell.INTERNAL_BIN_PATH = destDir.absolutePath + "/"

            val prefs = context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE)
            val currentVersion = prefs.getInt("bin_version", 0)

            if (currentVersion == BIN_VERSION && destDir.listFiles()?.isNotEmpty() == true) {
                return@withContext
            }

            val destFile = File(destDir, "iperf3")
            context.assets.open("bin/iperf3").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Shell.su("chmod 755 ${destDir.absolutePath}/*", silentLog = true)

            prefs.edit().putInt("bin_version", BIN_VERSION).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
