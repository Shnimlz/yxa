package com.shni.yxa.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class AppLog(
    val id: Long,
    val timestamp: String,
    val command: String,
    val success: Boolean
)

object LogManager {
    private val _logs = MutableStateFlow<List<AppLog>>(emptyList())
    val logs: StateFlow<List<AppLog>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val idCounter = AtomicLong(0)

    fun addLog(command: String, success: Boolean) {
        val timestamp = timeFormat.format(Date())
        val newLog = AppLog(idCounter.getAndIncrement(), timestamp, command, success)
        _logs.value = listOf(newLog) + _logs.value
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
