package com.shni.yxa

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shni.yxa.screen.*
import com.shni.yxa.ui.theme.YxaTheme
import com.shni.yxa.ui.theme.YxaThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val CHANNEL_ID = "yxa_root_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()
        setContent { AppEntry(onCloseApp = { showNoRootNotification(); finish() }) }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Yxa Root Access", NotificationManager.IMPORTANCE_HIGH).apply { description = "Notificaciones sobre el acceso Root" }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun showNoRootNotification() {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Yxa — Acceso Root requerido")
            .setContentText("No se detectó acceso Root.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("No se detectó acceso Root en tu dispositivo. Instala Magisk, KernelSU, etc. e intenta de nuevo."))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        try { if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, n) } catch (_: SecurityException) { }
    }

    override fun onDestroy() {
        com.shni.yxa.service.GameOverlayService.stop(this)
        super.onDestroy()
    }
}

@Composable
fun AppEntry(onCloseApp: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE) }
    var themeMode by remember {
        val saved = prefs.getString("theme", "GREEN_AMOLED") ?: "GREEN_AMOLED"
        mutableStateOf(try { YxaThemeMode.valueOf(saved) } catch (_: Exception) { YxaThemeMode.GREEN_AMOLED })
    }

    YxaTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppRoot(onCloseApp = onCloseApp, themeMode = themeMode,
                onThemeChange = { mode -> themeMode = mode; prefs.edit().putString("theme", mode.name).apply() })
        }
    }
}

private enum class AppState { WELCOME, CHECKING_PERMISSIONS, CHECKING_ROOT, CREATING_BACKUP, ROOT_OK, ROOT_FAILED, UNSUPPORTED_OS }

@Composable
fun AppRoot(onCloseApp: () -> Unit, themeMode: YxaThemeMode, onThemeChange: (YxaThemeMode) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yxa_prefs", Context.MODE_PRIVATE) }
    val isFirstLaunch = remember { !prefs.getBoolean("welcome_seen", false) }
    
    var appState by remember { 
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT <= 29) AppState.UNSUPPORTED_OS 
            else if (isFirstLaunch) AppState.WELCOME 
            else AppState.CHECKING_PERMISSIONS
        )
    }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val onRequestNotifications = {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val onRequestOverlay = {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    val onRequestHibernation = {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
        android.widget.Toast.makeText(context, "Desactiva 'Pausar actividad de la app si no se usa'", android.widget.Toast.LENGTH_LONG).show()
    }

    val onVerifyPermissions = {
        if (android.provider.Settings.canDrawOverlays(context)) {
            appState = AppState.CHECKING_ROOT
        } else {
            android.widget.Toast.makeText(context, "Falta el permiso de Superposición", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(appState) {
        if (appState == AppState.CHECKING_PERMISSIONS && !isFirstLaunch) {
            if (android.provider.Settings.canDrawOverlays(context)) {
                appState = AppState.CHECKING_ROOT
            }
        }
        if (appState == AppState.CHECKING_ROOT) {
            val ok = validateRoot()
            if (isFirstLaunch) delay(800)
            if (ok) {
                com.shni.yxa.util.DependencyManager.setupDependencies(context)
                prefs.edit().putBoolean("welcome_seen", true).apply()
                appState = AppState.CREATING_BACKUP
            } else {
                appState = AppState.ROOT_FAILED
            }
        }
        if (appState == AppState.CREATING_BACKUP) {
            com.shni.yxa.util.BackupManager.createBackup(context)
            delay(500) // Small delay for visual feedback
            appState = AppState.ROOT_OK
        }
    }

    AnimatedContent(targetState = appState, transitionSpec = {
        if (targetState == AppState.ROOT_OK) (slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(400))).togetherWith(slideOutVertically(tween(400)) { -it / 6 } + fadeOut(tween(300)))
        else fadeIn(tween(350)).togetherWith(fadeOut(tween(250)))
    }, label = "appState") { state ->
        when (state) {
            AppState.WELCOME -> WelcomeScreen(
                state = 0, onStart = { appState = AppState.CHECKING_PERMISSIONS }, onDismissError = {},
                onRequestNotifications = {}, onRequestOverlay = {}, onRequestHibernation = {}, onVerifyPermissions = {}
            )
            AppState.CHECKING_PERMISSIONS -> WelcomeScreen(
                state = 2, onStart = {}, onDismissError = {},
                onRequestNotifications = onRequestNotifications, onRequestOverlay = onRequestOverlay,
                onRequestHibernation = onRequestHibernation, onVerifyPermissions = onVerifyPermissions
            )
            AppState.CHECKING_ROOT -> WelcomeScreen(1, onStart = {}, onDismissError = {}, onRequestNotifications = {}, onRequestOverlay = {}, onRequestHibernation = {}, onVerifyPermissions = {})
            AppState.CREATING_BACKUP -> WelcomeScreen(5, onStart = {}, onDismissError = {}, onRequestNotifications = {}, onRequestOverlay = {}, onRequestHibernation = {}, onVerifyPermissions = {})
            AppState.ROOT_FAILED -> WelcomeScreen(3, onStart = {}, onDismissError = onCloseApp, onRequestNotifications = {}, onRequestOverlay = {}, onRequestHibernation = {}, onVerifyPermissions = {})
            AppState.UNSUPPORTED_OS -> WelcomeScreen(4, onStart = {}, onDismissError = onCloseApp, onRequestNotifications = {}, onRequestOverlay = {}, onRequestHibernation = {}, onVerifyPermissions = {})
            AppState.ROOT_OK -> AppNavigation(themeMode, onThemeChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(themeMode: YxaThemeMode, onThemeChange: (YxaThemeMode) -> Unit) {
    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }
    var homeScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var gameScreen by remember { mutableStateOf<Screen?>(null) }
    var isForward by remember { mutableStateOf(true) }
    var isCleaning by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsScreen by remember { mutableStateOf<Screen?>(null) }
    val scope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<com.shni.yxa.util.UpdateInfo?>(null) }
    var hasCheckedUpdate by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasCheckedUpdate) {
        if (!hasCheckedUpdate) {
            val update = com.shni.yxa.util.UpdateManager.checkForUpdates()
            if (update != null) {
                updateInfo = update
            }
            hasCheckedUpdate = true
        }
    }

    val context = LocalContext.current

    BackHandler(enabled = true) {
        when {
            showSettings && settingsScreen != null -> {
                settingsScreen = null
            }
            showSettings -> {
                showSettings = false
            }
            selectedTab == BottomTab.GAME && gameScreen != null -> {
                gameScreen = null
            }
            selectedTab == BottomTab.HOME && homeScreen != Screen.Dashboard -> {
                isForward = false
                homeScreen = Screen.Dashboard
            }
            selectedTab != BottomTab.HOME -> {
                selectedTab = BottomTab.HOME
            }
            else -> {
                (context as? Activity)?.finish()
            }
        }
    }
    val showBottomBar = !showSettings && (
        (selectedTab == BottomTab.HOME && homeScreen == Screen.Dashboard)
        || (selectedTab == BottomTab.GAME && gameScreen == null)
        || (selectedTab == BottomTab.RED)
        || (selectedTab == BottomTab.LOGS)
    )

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (showBottomBar) {
                FloatingActionButton(
                    onClick = {
                        if (!isCleaning) {
                            isCleaning = true
                            scope.launch(Dispatchers.IO) {
                                com.shni.yxa.util.Shell.su("sync; echo 3 > /proc/sys/vm/drop_caches")
                                kotlinx.coroutines.delay(1000)
                                isCleaning = false
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.offset(y = 36.dp)
                ) {
                    if (isCleaning) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.FlashOn, contentDescription = "Limpieza Rápida")
                    }
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = com.shni.yxa.ui.components.CurvedBottomBarShape(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = BottomTab.entries
                        // Lado Izquierdo
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            tabs.take(2).forEach { tab ->
                                NavBarItem(
                                    tab = tab,
                                    selected = selectedTab == tab,
                                    onClick = { if (selectedTab == tab) { if (tab == BottomTab.HOME) homeScreen = Screen.Dashboard; if (tab == BottomTab.GAME) gameScreen = null }; selectedTab = tab }
                                )
                            }
                        }
                        // Espacio Central para el Cutout
                        Spacer(Modifier.width(80.dp))
                        // Lado Derecho (2 pestañas)
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            tabs.takeLast(2).forEach { tab ->
                                NavBarItem(
                                    tab = tab,
                                    selected = selectedTab == tab,
                                    onClick = { if (selectedTab == tab) { if (tab == BottomTab.HOME) homeScreen = Screen.Dashboard; if (tab == BottomTab.GAME) gameScreen = null }; selectedTab = tab }
                                )
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = { Text("Yxa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showSettings = true; settingsScreen = null }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            AnimatedContent(targetState = selectedTab, transitionSpec = {
                val dur = 300
                (fadeIn(tween(dur)) + slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { if (targetState.ordinal > initialState.ordinal) it / 4 else -it / 4 })
                    .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(dur)) { if (targetState.ordinal > initialState.ordinal) -it / 6 else it / 6 })
            }, label = "tabNav") { tab ->
                when (tab) {
                    BottomTab.HOME -> {
                        AnimatedContent(targetState = homeScreen, transitionSpec = {
                            val dur = 300
                            if (isForward) (slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(dur))).togetherWith(slideOutHorizontally(tween(dur)) { -it / 5 } + fadeOut(tween(200)))
                            else (slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(dur))).togetherWith(slideOutHorizontally(tween(dur)) { it / 5 } + fadeOut(tween(200)))
                        }, label = "homeNav") { screen ->
                            when (screen) {
                                is Screen.Dashboard -> DashboardScreen(onNavigate = { isForward = true; homeScreen = it })
                                is Screen.CpuDetail -> CpuDetailScreen(onBack = { isForward = false; homeScreen = Screen.Dashboard })
                                is Screen.GpuDetail -> GpuDetailScreen(onBack = { isForward = false; homeScreen = Screen.Dashboard })
                                is Screen.RamDetail -> RamDetailScreen(onBack = { isForward = false; homeScreen = Screen.Dashboard })
                                else -> {}
                            }
                        }
                    }
                    BottomTab.GAME -> {
                        AnimatedContent(targetState = gameScreen, transitionSpec = {
                            val dur = 300
                            if (targetState != null) (slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(dur))).togetherWith(slideOutHorizontally(tween(dur)) { -it / 5 } + fadeOut(tween(200)))
                            else (slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(dur))).togetherWith(slideOutHorizontally(tween(dur)) { it / 5 } + fadeOut(tween(200)))
                        }, label = "gameNav") { screen ->
                            when (screen) {
                                null -> GameTimeScreen(onOpenSettings = { pkg -> gameScreen = Screen.GameSettings(pkg) })
                                is Screen.GameSettings -> GameSettingsScreen(packageName = screen.packageName, onBack = { gameScreen = null })
                                else -> {}
                            }
                        }
                    }
                    BottomTab.RED -> {
                        NetworkSettingsScreen()
                    }
                    BottomTab.LOGS -> {
                        LogsScreen()
                    }
                }
            }
        }
        // Overlay de Ajustes
        if (showSettings) {
            AnimatedContent(targetState = settingsScreen, transitionSpec = {
                val dur = 300
                if (targetState != null) (slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(dur))).togetherWith(slideOutHorizontally(tween(dur)) { -it / 5 } + fadeOut(tween(200)))
                else (slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(dur))).togetherWith(slideOutHorizontally(tween(dur)) { it / 5 } + fadeOut(tween(200)))
            }, label = "settingsNav") { screen ->
                when (screen) {
                    null -> SettingsScreen(currentTheme = themeMode, onThemeChange = onThemeChange, onNavigateToBootScripts = { settingsScreen = Screen.BootScripts })
                    is Screen.BootScripts -> BootScriptsScreen(onBack = { settingsScreen = null })
                    else -> {}
                }
            }
        }
    }

    if (updateInfo != null) {
        com.shni.yxa.ui.components.UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { updateInfo = null }
        )
    }
}

@Composable
private fun NavBarItem(tab: BottomTab, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(tab.icon, contentDescription = tab.label, tint = color)
        Text(
            text = tab.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}

private suspend fun validateRoot(): Boolean = withContext(Dispatchers.IO) {
    com.shni.yxa.util.Shell.suTest("id")
}
