package com.shni.yxa.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.ui.text.withStyle
import com.shni.yxa.util.UpdateInfo
import com.shni.yxa.util.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

fun parseMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val regex = Regex("\\*\\*(.*?)\\*\\*")
    var lastIndex = 0
    regex.findAll(text).forEach { matchResult ->
        builder.append(text.substring(lastIndex, matchResult.range.first))
        builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
            builder.append(matchResult.groupValues[1])
        }
        lastIndex = matchResult.range.last + 1
    }
    builder.append(text.substring(lastIndex))
    return builder.toAnnotatedString()
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = {
            Text(
                text = if (downloadedFile != null) "Descarga completada" else "Actualización disponible: ${updateInfo.versionName}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (downloadedFile != null) {
                    Text(
                        "El paquete de actualización ha sido descargado. Puedes instalarlo ahora.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Descargando...", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Text(
                        "Novedades:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = parseMarkdown(updateInfo.changelog),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                    )
                }
            }
        },
        confirmButton = {
            if (downloadedFile != null) {
                Button(
                    onClick = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "com.shni.yxa.fileprovider",
                            downloadedFile!!
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignorar si no encuentra instalador
                        }
                    }
                ) {
                    Text("Actualizar aplicación")
                }
            } else if (!isDownloading) {
                Button(
                    onClick = {
                        isDownloading = true
                        scope.launch {
                            val file = UpdateManager.downloadApk(
                                urlStr = updateInfo.browserDownloadUrl,
                                context = context,
                                onProgress = { progress -> downloadProgress = progress }
                            )
                            isDownloading = false
                            downloadedFile = file
                        }
                    }
                ) {
                    Text("Descargar")
                }
            }
        },
        dismissButton = {
            if (downloadedFile != null || !isDownloading) {
                TextButton(
                    onClick = {
                        if (downloadedFile != null) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl))
                            context.startActivity(intent)
                        } else {
                            onDismiss()
                        }
                    }
                ) {
                    Text(if (downloadedFile != null) "Instalación manual" else "Más tarde")
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
