package com.shni.yxa.monitor

class CpuMonitor {
    private var lastTotal = mutableMapOf<Int, Long>()
    private var lastIdle = mutableMapOf<Int, Long>()

    fun parseStat(lines: List<String>): List<Float> {
        val usages = mutableListOf<Float>()
        val coreLines = lines.filter { it.startsWith("cpu") && it.getOrNull(3)?.isDigit() == true }
            .sortedBy { it.substring(3).takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }

        for (line in coreLines) {
            val coreIdxStr = line.substring(3).takeWhile { it.isDigit() }
            val coreIdx = coreIdxStr.toIntOrNull() ?: continue
            val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (parts.size >= 5) {
                val idle = parts[4].toLongOrNull() ?: 0L
                val total = parts.drop(1).take(7).mapNotNull { it.toLongOrNull() }.sum()

                val prevTotal = lastTotal[coreIdx] ?: 0L
                val prevIdle = lastIdle[coreIdx] ?: 0L
                val diffTotal = total - prevTotal
                val diffIdle = idle - prevIdle

                val usage = if (prevTotal > 0 && diffTotal > 0) {
                    ((diffTotal - diffIdle).toFloat() / diffTotal * 100f).coerceIn(0f, 100f)
                } else 0f

                usages.add(usage)
                lastTotal[coreIdx] = total
                lastIdle[coreIdx] = idle
            }
        }
        return usages
    }
}
