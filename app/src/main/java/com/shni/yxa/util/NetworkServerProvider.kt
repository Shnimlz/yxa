package com.shni.yxa.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Scrapes R0GGER's public iperf3 server list from GitHub
 * and returns nearby servers from the LATIN AMERICA section.
 */
object NetworkServerProvider {

    private const val README_URL =
        "https://raw.githubusercontent.com/R0GGER/public-iperf3-servers/main/README.md"

    private val CMD_PATTERN = Regex("""iperf3\s+-c\s+([^\s]+)\s+-p\s+([^\s|]+)""")

    /**
     * Downloads the README and extracts servers from the
     * LATIN AMERICA section for optimal latency.
     * Returns an empty list on failure so the caller can use its own fallback.
     *
     * @return List of Pair(host, port)
     */
    suspend fun getNearbyServers(context: Context): List<Pair<String, String>> {
        return try {
            withContext(Dispatchers.IO) {
                val text = URL(README_URL).readText()

                val latinIndex = text.indexOf("### LATIN AMERICA")
                val northIndex = text.indexOf("### NORTH AMERICA")

                if (latinIndex == -1) {
                    return@withContext emptyList()
                }

                val endIndex = if (northIndex != -1) northIndex else text.length
                val regionText = text.substring(latinIndex, endIndex)

                val servers = CMD_PATTERN.findAll(regionText).map { match ->
                    val host = match.groupValues[1]
                    val portRaw = match.groupValues[2]
                    val port = portRaw.split("-").first()
                    Pair(host, port)
                }.toList()

                servers
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
