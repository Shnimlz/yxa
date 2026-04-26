package com.shni.yxa.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium welcome/onboarding screen.
 * state: 0=welcome, 1=checking root, 3=root failed
 */
@Composable
fun WelcomeScreen(
    state: Int,
    onStart: () -> Unit,
    onDismissError: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestHibernation: () -> Unit,
    onVerifyPermissions: () -> Unit
) {
    val primaryGreen = Color(0xFF66FF99)

    // Staggered entrance animations
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Background ambient glows ──
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-60).dp)
                .blur(150.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryGreen.copy(alpha = 0.08f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 80.dp)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryGreen.copy(alpha = 0.05f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // ── Geometric spirograph art ──
        val infiniteTransition = rememberInfiniteTransition(label = "geo")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
            label = "rotation"
        )
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(4000, easing = EaseInOut), repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        val innerRotation by infiniteTransition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
            label = "innerRotation"
        )

        Canvas(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopCenter)
                .offset(y = 60.dp)
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = size.minDimension / 2.6f * pulse

            // Outer rotating ellipses
            for (i in 0 until 14) {
                val angle = (i * (360f / 14f)) + rotation
                val alphaVal = (0.08f + i * 0.018f).coerceAtMost(0.3f)
                rotate(angle, pivot = Offset(cx, cy)) {
                    drawOval(
                        color = primaryGreen.copy(alpha = alphaVal),
                        topLeft = Offset(cx - r, cy - r * 0.32f),
                        size = Size(r * 2, r * 0.64f),
                        style = Stroke(width = 1f, cap = StrokeCap.Round)
                    )
                }
            }

            // Inner ring
            drawCircle(
                color = primaryGreen.copy(alpha = 0.1f * pulse),
                radius = r * 0.45f,
                center = Offset(cx, cy),
                style = Stroke(width = 1f)
            )

            // Center hexagon — counter-rotating
            val hexR = r * 0.2f
            val hexPath = Path()
            for (i in 0..5) {
                val a = Math.toRadians((60.0 * i) - 30 + innerRotation * 0.5)
                val x = cx + hexR * cos(a).toFloat()
                val y = cy + hexR * sin(a).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()

            // Hex fill glow
            drawPath(hexPath, color = primaryGreen.copy(alpha = 0.06f * pulse))
            // Hex stroke
            drawPath(hexPath, color = primaryGreen.copy(alpha = 0.45f * pulse), style = Stroke(1.5f))

            // Center dot
            drawCircle(
                color = primaryGreen.copy(alpha = 0.6f * pulse),
                radius = 3f,
                center = Offset(cx, cy)
            )
        }

        // ── Gradient overlay from bottom for text readability ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f), Color.Black)
                    )
                )
        )

        // ── Content ──
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(600)) + slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec = tween(600, easing = EaseOut)
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(bottom = 48.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {
                // App branding
                Text(
                    "Yxa",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.5).sp
                    ),
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "System\nMonitor",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 38.sp
                    ),
                    color = primaryGreen
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    "Monitoreo en tiempo real de CPU, RAM y GPU.\nRequiere acceso Root.",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = Color.White.copy(alpha = 0.45f)
                )

                Spacer(Modifier.height(36.dp))

                // ── Action area ──
                when (state) {
                    0 -> {
                        Button(
                            onClick = onStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryGreen,
                                contentColor = Color.Black
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp
                            )
                        ) {
                            Text(
                                "Comenzar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    1 -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = primaryGreen.copy(alpha = 0.25f),
                                disabledContentColor = Color.Black.copy(alpha = 0.7f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Verificando Root…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    5 -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = primaryGreen.copy(alpha = 0.25f),
                                disabledContentColor = Color.Black.copy(alpha = 0.7f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Creando copia de seguridad...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    3 -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1A0505)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Acceso Root no disponible",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFFF5252),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Yxa necesita permisos Root para monitorear el sistema. Instala un gestor de Root (Magisk, KernelSU) e intenta de nuevo.",
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = onDismissError,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF5252).copy(alpha = 0.15f),
                                        contentColor = Color(0xFFFF5252)
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("Cerrar", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    4 -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1A0505)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Sistema no compatible",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFFF5252),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Yxa requiere Android 11 o superior para funcionar de manera segura. Tu versión de Android no está soportada.",
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = onDismissError,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF5252).copy(alpha = 0.15f),
                                        contentColor = Color(0xFFFF5252)
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("Cerrar", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    2 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Permisos requeridos", style = MaterialTheme.typography.titleSmall, color = primaryGreen, fontWeight = FontWeight.Bold)
                            
                            Button(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)) {
                                Text("1. Permitir Notificaciones")
                            }
                            
                            Button(onClick = onRequestOverlay, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)) {
                                Text("2. Permiso de Superposición")
                            }
                            
                            Button(onClick = onRequestHibernation, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)) {
                                Text("3. Desactivar Hibernación")
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(onClick = onVerifyPermissions, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen, contentColor = Color.Black)) {
                                Text("Verificar y Continuar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
