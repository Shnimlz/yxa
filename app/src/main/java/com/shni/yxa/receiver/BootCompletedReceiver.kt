package com.shni.yxa.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shni.yxa.util.BootScriptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    BootScriptManager.applyBootOptimizations(context)
                } catch (e: Exception) {
                    // Fail silently, root might not be available yet or other boot-time errors
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
