package com.shni.yxa.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
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

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE)

                if (prefs.getBoolean("lag_protector", false)) {
                    LagProtectorService.start(context)
                }

                if (prefs.getBoolean("auto_ram_clean", false)) {
                    AutoRamCleanerService.start(context)
                }

                if (prefs.getBoolean("overlay_active", false)) {
                    val pkg = prefs.getString("last_overlay_pkg", null)
                    if (!pkg.isNullOrBlank() && Settings.canDrawOverlays(context)) {
                        GameOverlayService.start(context, pkg)
                    }
                }
            } catch (_: Exception) { }

            pendingResult.finish()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                BootScriptManager.applyBootOptimizations(context)
            } catch (_: Exception) { }
        }
    }
}
