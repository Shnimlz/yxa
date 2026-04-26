package com.shni.yxa.data

import android.content.Context
import org.json.JSONObject

data class GameProfile(
    val packageName: String,
    val label: String,
    val perfMode: Int = 1,        // 0=ahorro 1=equilibrado 2=turbo
    val refreshRate: Int = 60,
    val resolution: Int = 100,
    val touchSensitivity: Boolean = false,
    val wifiLowLatency: Boolean = false,
    val gpuBoost: Boolean = false,
    val dndEnabled: Boolean = false,
    val graphicsApi: String = "default",
    val tcpCongestion: String = "cubic",
    val advancedNet: Boolean = false,
    val aggressiveClear: Boolean = false,
    val disableZram: Boolean = false,
    val killBackground: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName); put("label", label)
        put("perfMode", perfMode); put("refreshRate", refreshRate); put("resolution", resolution)
        put("touchSensitivity", touchSensitivity); put("wifiLowLatency", wifiLowLatency)
        put("gpuBoost", gpuBoost); put("dndEnabled", dndEnabled)
        put("graphicsApi", graphicsApi)
        put("tcpCongestion", tcpCongestion); put("advancedNet", advancedNet)
        put("aggressiveClear", aggressiveClear); put("disableZram", disableZram); put("killBackground", killBackground)
    }

    companion object {
        fun fromJson(json: JSONObject) = GameProfile(
            packageName = json.getString("packageName"), label = json.getString("label"),
            perfMode = json.optInt("perfMode", 1), refreshRate = json.optInt("refreshRate", 60),
            resolution = json.optInt("resolution", 100),
            touchSensitivity = json.optBoolean("touchSensitivity", false),
            wifiLowLatency = json.optBoolean("wifiLowLatency", false),
            gpuBoost = json.optBoolean("gpuBoost", false),
            dndEnabled = json.optBoolean("dndEnabled", false),
            graphicsApi = json.optString("graphicsApi", "default"),
            tcpCongestion = json.optString("tcpCongestion", "cubic"),
            advancedNet = json.optBoolean("advancedNet", false),
            aggressiveClear = json.optBoolean("aggressiveClear", false),
            disableZram = json.optBoolean("disableZram", false),
            killBackground = json.optBoolean("killBackground", false),
        )
    }
}

class GameProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("yxa_games", Context.MODE_PRIVATE)

    fun getAll(): List<GameProfile> {
        val raw = prefs.getString("profiles", "{}") ?: "{}"
        val root = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        return root.keys().asSequence().mapNotNull { key ->
            try { GameProfile.fromJson(root.getJSONObject(key)) } catch (_: Exception) { null }
        }.toList()
    }

    fun get(packageName: String): GameProfile? {
        val root = try { JSONObject(prefs.getString("profiles", "{}") ?: "{}") } catch (_: Exception) { return null }
        return if (root.has(packageName)) try { GameProfile.fromJson(root.getJSONObject(packageName)) } catch (_: Exception) { null } else null
    }

    fun save(profile: GameProfile) {
        val root = try { JSONObject(prefs.getString("profiles", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        root.put(profile.packageName, profile.toJson())
        prefs.edit().putString("profiles", root.toString()).apply()
    }

    fun delete(packageName: String) {
        val root = try { JSONObject(prefs.getString("profiles", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        root.remove(packageName)
        prefs.edit().putString("profiles", root.toString()).apply()
    }
}
