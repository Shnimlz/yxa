package com.shni.yxa.data

import android.content.Context
import org.json.JSONArray

/**
 * Stores the list of user-selected floating apps (packages) for GameTime overlay.
 */
class FloatingAppsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("yxa_floating_apps", Context.MODE_PRIVATE)

    fun getAll(): List<String> {
        val raw = prefs.getString("packages", null) ?: return defaultApps()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { defaultApps() }
    }

    fun save(packages: List<String>) {
        val arr = JSONArray()
        packages.forEach { arr.put(it) }
        prefs.edit().putString("packages", arr.toString()).apply()
    }

    fun add(pkg: String) { save(getAll() + pkg) }
    fun remove(pkg: String) { save(getAll() - pkg) }

    private fun defaultApps() = listOf(
        "com.google.android.calculator",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.android.chrome"
    )
}
