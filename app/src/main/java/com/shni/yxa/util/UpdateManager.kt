package com.shni.yxa.util

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val browserDownloadUrl: String,
    val htmlUrl: String,
    val changelog: String
)

object UpdateManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/Shnimlz/yxa/releases/latest"

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                val tagName = json.getString("tag_name")
                
                val cleanRemote = tagName.removePrefix("v").trim()
                val cleanLocal = currentVersion.removePrefix("v").trim()
                
                if (cleanRemote == cleanLocal) {
                    return@withContext null
                }
                
                val htmlUrl = json.getString("html_url")
                val body = json.getString("body")
                
                val assets = json.getJSONArray("assets")
                var browserDownloadUrl = ""
                
                if (assets.length() > 0) {
                    val firstAsset = assets.getJSONObject(0)
                    browserDownloadUrl = firstAsset.getString("browser_download_url")
                }
                
                if (browserDownloadUrl.isNotEmpty()) {
                    return@withContext UpdateInfo(
                        versionName = tagName,
                        browserDownloadUrl = browserDownloadUrl,
                        htmlUrl = htmlUrl,
                        changelog = body
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to check for updates", e)
        }
        return@withContext null
    }

    suspend fun downloadApk(urlStr: String, context: Context, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        var file: File? = null
        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val fileLength = connection.contentLength
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            
            file = File(dir, "update.apk")
            if (file.exists()) {
                file.delete()
            }

            val input = connection.inputStream
            val output = FileOutputStream(file)
            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength)
                }
                output.write(data, 0, count)
            }
            
            output.flush()
            output.close()
            input.close()
            
            return@withContext file
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to download APK", e)
            file?.delete()
            return@withContext null
        }
    }
}
