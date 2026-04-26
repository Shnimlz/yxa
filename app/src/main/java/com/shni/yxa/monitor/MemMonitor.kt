package com.shni.yxa.monitor

fun parseMemInfo(lines: List<String>): Float {
    return try {
        val total = lines.first { it.startsWith("MemTotal") }.filter { it.isDigit() }.toLong()
        val disponible = try {
            lines.first { it.startsWith("MemAvailable") }.filter { it.isDigit() }.toLong()
        } catch (e: Exception) {
            val free = lines.firstOrNull { it.startsWith("MemFree") }?.filter { it.isDigit() }?.toLong() ?: 0L
            val cached = lines.firstOrNull { it.startsWith("Cached") }?.filter { it.isDigit() }?.toLong() ?: 0L
            free + cached
        }
        if (total > 0L) ((total - disponible).toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f) else 0f
    } catch (e: Exception) {
        0f
    }
}
