package com.shni.yxa.util

/**
 * Manages the fq_codel queueing discipline on the active network interface
 * to reduce bufferbloat and prioritize small packets (gaming) over bulk traffic.
 */
object BufferbloatManager {

    /**
     * Detects the active network interface.
     */
    private fun getActiveInterface(): String {
        val route = Shell.su("ip route | grep default | awk '{print \$5}'", silentLog = true)
        return route?.trim()?.lines()?.firstOrNull()?.trim() ?: "wlan0"
    }

    /**
     * Enables fq_codel on the active interface.
     */
    fun enable(): Boolean {
        val iface = getActiveInterface()
        return Shell.suTest("tc qdisc add dev $iface root fq_codel")
    }

    /**
     * Disables fq_codel by removing the root qdisc from the active interface.
     */
    fun disable(): Boolean {
        val iface = getActiveInterface()
        return Shell.suTest("tc qdisc del dev $iface root")
    }

    /**
     * Checks if fq_codel is currently active on the interface.
     */
    fun isEnabled(): Boolean {
        val iface = getActiveInterface()
        val output = Shell.su("tc qdisc show dev $iface", silentLog = true)
        return output?.contains("fq_codel", ignoreCase = true) == true
    }
}
