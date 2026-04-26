package com.shni.yxa.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.shni.yxa.service.AutoRamCleanerService
import com.shni.yxa.service.GameOverlayService
import com.shni.yxa.service.LagProtectorService
import com.shni.yxa.util.BootScriptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        Log.i("YxaBoot", "Boot completed received, restoring services...")

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE)

        Handler(Looper.getMainLooper()).post {
            try {
                if (prefs.getBoolean("lag_protector", false)) {
                    Log.i("YxaBoot", "Starting LagProtectorService")
                    LagProtectorService.start(appContext)
                }
            } catch (e: Exception) {
                Log.e("YxaBoot", "Failed to start LagProtector", e)
            }

            try {
                if (prefs.getBoolean("auto_ram_clean", false)) {
                    Log.i("YxaBoot", "Starting AutoRamCleanerService")
                    AutoRamCleanerService.start(appContext)
                }
            } catch (e: Exception) {
                Log.e("YxaBoot", "Failed to start AutoRamCleaner", e)
            }

            try {
                if (prefs.getBoolean("overlay_active", false)) {
                    val pkg = prefs.getString("last_overlay_pkg", null)
                    if (!pkg.isNullOrBlank() && Settings.canDrawOverlays(appContext)) {
                        Log.i("YxaBoot", "Starting GameOverlayService for $pkg")
                        GameOverlayService.start(appContext, pkg)
                    }
                }
            } catch (e: Exception) {
                Log.e("YxaBoot", "Failed to start GameOverlay", e)
            }

            try {
                if (prefs.getBoolean("game_watcher_enabled", false)) {
                    Log.i("YxaBoot", "Starting GameWatcherService")
                    com.shni.yxa.service.GameWatcherService.start(appContext)
                }
            } catch (e: Exception) {
                Log.e("YxaBoot", "Failed to start GameWatcherService", e)
            }
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BootScriptManager.applyBootOptimizations(appContext)
            } catch (e: Exception) {
                Log.e("YxaBoot", "Failed to apply boot optimizations", e)
            }
            pendingResult.finish()
        }
    }
}
