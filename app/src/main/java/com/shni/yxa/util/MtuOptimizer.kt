package com.shni.yxa.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers the optimal MTU for the active network interface by sending
 * pings with the "Do Not Fragment" flag, then applies the result.
 */
object MtuOptimizer {

    /**
     * Detects the active network interface (wlan0, rmnet_data0, etc.)
     */
    private fun getActiveInterface(): String {
        val route = Shell.su("ip route | grep default | awk '{print \$5}'", silentLog = true)
        return route?.trim()?.lines()?.firstOrNull()?.trim() ?: "wlan0"
    }

    /**
     * Finds the optimal MTU by binary searching between 1200 and 1500.
     * Uses BUSYBOX ping with Do Not Fragment flag.
     *
     * @return The optimal MTU value (payload + 28), or 1500 as default if detection fails.
     */
    suspend fun findOptimalMtu(): Int = withContext(Dispatchers.IO) {
        var low = 1200
        var high = 1500
        var maxMtu = 1500

        while (low <= high) {
            val mid = low + (high - low) / 2
            val payload = mid - 28
            try {
                val ok = Shell.suTest(
                    "${Shell.BUSYBOX} ping -M do -s $payload -c 1 -W 1 8.8.8.8",
                    silentLog = true
                )
                if (ok) {
                    maxMtu = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            } catch (_: Exception) {
                high = mid - 1
            }
        }
        
        // Apply immediately as requested
        applyMtu(maxMtu)
        
        maxMtu
    }

    /**
     * Applies the given MTU value to the active network interface.
     *
     * @return true if the commands succeeded.
     */
    suspend fun applyMtu(mtu: Int): Boolean = withContext(Dispatchers.IO) {
        val iface = getActiveInterface()
        val r1 = Shell.suTest("ifconfig $iface mtu $mtu")
        val r2 = Shell.suTest("ip link set dev $iface mtu $mtu")
        r1 || r2
    }

    /**
     * Reads the current MTU of the active network interface.
     */
    suspend fun getCurrentMtu(): String = withContext(Dispatchers.IO) {
        val iface = getActiveInterface()
        Shell.su("cat /sys/class/net/$iface/mtu", silentLog = true)?.trim() ?: "1500"
    }
}
