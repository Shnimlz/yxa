package com.shni.yxa.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import android.util.Log
import com.shni.yxa.data.FloatingAppsRepository
import com.shni.yxa.data.GameProfile
import com.shni.yxa.data.GameProfileRepository
import com.shni.yxa.util.Shell
import kotlinx.coroutines.*

class GameOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var fabView: View? = null
    private var panelView: View? = null
    private var isPanelOpen = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var monitorJob: Job? = null
    private var gamePackage: String = ""

    private var cpuText: TextView? = null
    private var ramText: TextView? = null
    private var gpuText: TextView? = null
    private var tempText: TextView? = null
    private var cpuGauge: RingGaugeView? = null
    private var ramGauge: RingGaugeView? = null
    private var gpuGauge: RingGaugeView? = null

    companion object {
        private const val CHANNEL_ID = "yxa_overlay"
        private const val NOTIF_ID = 2001
        const val EXTRA_PACKAGE = "game_package"

        fun start(context: Context, gamePackage: String) {
            val intent = Intent(context, GameOverlayService::class.java).putExtra(EXTRA_PACKAGE, gamePackage)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context, GameOverlayService::class.java)) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        gamePackage = intent?.getStringExtra(EXTRA_PACKAGE) ?: ""
        if (fabView != null) return START_NOT_STICKY
        createFab()
        startGameLifecycleMonitor()
        return START_NOT_STICKY
    }

    private fun startGameLifecycleMonitor() {
        scope.launch {
            var outOfFocusCount = 0
            val maxOutOfFocusCount = 10 // 10 checks * 2 seconds = 20 seconds

            while (true) {
                // Ejecutar dumpsys sin pipes problemáticos y procesar en Kotlin
                val focusDump = withContext(Dispatchers.IO) { 
                    Shell.su("dumpsys window displays", silentLog = true) 
                } ?: ""
                
                var inForegroundFinal = false
                val lines = focusDump.split('\n')
                for (line in lines) {
                    if (line.contains("mCurrentFocus") || line.contains("mFocusedApp") || 
                        line.contains("mResumedActivity") || line.contains("deepestLastOrientationSource")) {
                        android.util.Log.d("YxaGameTime", "Focus line detected: $line")
                        if (line.contains(gamePackage)) {
                            inForegroundFinal = true
                            break
                        }
                    }
                }
                
                // Mantenemos el log del dump (limitado a 500 chars para no saturar)
                android.util.Log.d("YxaGameTime", "Current FocusDump (First 500 chars): ${focusDump.take(500)}")
                android.util.Log.d("YxaGameTime", "Searching for package: $gamePackage | Result: $inForegroundFinal")
                
                withContext(Dispatchers.Main) {
                    if (inForegroundFinal) {
                        outOfFocusCount = 0
                        fabView?.visibility = View.VISIBLE
                    } else {
                        outOfFocusCount++
                        if (outOfFocusCount >= 3) {
                            fabView?.visibility = View.GONE
                            if (isPanelOpen) hidePanel()
                        }
                        
                        if (outOfFocusCount >= maxOutOfFocusCount) {
                            android.util.Log.d("YxaGameTime", "El juego se cerró o minimizó por 20s. Cerrando overlay...")
                            Toast.makeText(this@GameOverlayService, "GameTime cerrado", Toast.LENGTH_SHORT).show()
                            stopSelf()
                        }
                    }
                }
                
                if (outOfFocusCount >= maxOutOfFocusCount) break
                delay(2000)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFab() {
        val size = dp(42)
        val fab = FrameLayout(this).apply {
            background = makeRoundRect(0x99000000.toInt(), dp(21).toFloat())
            alpha = 0.5f // Semi-transparent initially
            addView(ImageView(this@GameOverlayService).apply {
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val dm = resources.displayMetrics
        val targetInitX = -size / 2 // Initial snap to left edge
        val params = overlayParams(size, size).apply { gravity = Gravity.TOP or Gravity.START; x = targetInitX; y = dp(200) }
        
        var lastX = 0; var lastY = 0; var touchX = 0f; var touchY = 0f; var moved = false
        fab.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { 
                    fab.alpha = 1.0f 
                    lastX = params.x; lastY = params.y; touchX = ev.rawX; touchY = ev.rawY; moved = false
                    true 
                }
                MotionEvent.ACTION_MOVE -> { 
                    val dx = (ev.rawX - touchX).toInt(); val dy = (ev.rawY - touchY).toInt()
                    if (dx*dx+dy*dy > 100) moved = true
                    params.x = lastX+dx; params.y = lastY+dy
                    wm.updateViewLayout(fab, params)
                    true 
                }
                MotionEvent.ACTION_UP -> { 
                    if (!moved) {
                        fab.alpha = 0.5f
                        togglePanel()
                    } else {
                        val displayW = resources.displayMetrics.widthPixels
                        val targetX = if (params.x + size / 2 < displayW / 2) -size / 2 else displayW - (size / 2)
                        android.animation.ValueAnimator.ofInt(params.x, targetX).apply {
                            duration = 250
                            interpolator = android.view.animation.DecelerateInterpolator()
                            addUpdateListener { anim ->
                                params.x = anim.animatedValue as Int
                                try { wm.updateViewLayout(fab, params) } catch (e: Exception) {}
                            }
                            addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    fab.alpha = 0.5f
                                }
                            })
                            start()
                        }
                    }
                    true 
                }
                else -> false
            }
        }
        wm.addView(fab, params)
        fabView = fab
    }

    private fun togglePanel() { if (isPanelOpen) hidePanel() else showPanel() }

    @SuppressLint("SetTextI18n")
    private fun showPanel() {
        val dm = resources.displayMetrics
        val panelW = (dm.widthPixels * 0.66).toInt() // Reduced to ~75% of previous size
        val panelH = ViewGroup.LayoutParams.WRAP_CONTENT

        val repo = GameProfileRepository(this)
        val profile = repo.get(gamePackage)
        val floatingRepo = FloatingAppsRepository(this)
        val floatingPkgs = floatingRepo.getAll()
        val pm = packageManager

        // Glassmorphism container with border
        val panelBg = GradientDrawable().apply {
            setColor(0xCC000000.toInt())
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), 0x33FFFFFF)
        }
        val root = FrameLayout(this).apply { background = panelBg }
        val scroll = ScrollView(this).apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ── Header ──
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "Yxa GameTime"; textSize = 13f; setTextColor(0xFFB388FF.toInt()); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        tempText = TextView(this).apply { text = "-- C"; textSize = 11f; setTextColor(0xFFFF8A65.toInt()); typeface = Typeface.DEFAULT_BOLD; setPadding(0,0,dp(16),0) }
        header.addView(tempText)
        // Close button - clearly separated at top right
        val closeBtn = FrameLayout(this).apply {
            background = makeRoundRect(0x33FFFFFF, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            addView(ImageView(this@GameOverlayService).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(0xDDFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener { hidePanel() }
        }
        header.addView(closeBtn)
        content.addView(header)
        content.addView(spacer(dp(14)))

        // ── Circular Live Stats (Neumorphic) ──
        val statsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(2), 0, dp(2), 0) }
        makeGaugeCard("CPU", 0xFF69F0AE.toInt(), 0xFF1DE9B6.toInt()).let { (card, ring, tv) -> cpuGauge = ring; cpuText = tv; statsRow.addView(card) }
        makeGaugeCard("RAM", 0xFF40C4FF.toInt(), 0xFF00B0FF.toInt()).let { (card, ring, tv) -> ramGauge = ring; ramText = tv; statsRow.addView(card) }
        makeGaugeCard("GPU", 0xFFFFAB40.toInt(), 0xFFFF6E40.toInt()).let { (card, ring, tv) -> gpuGauge = ring; gpuText = tv; statsRow.addView(card) }
        content.addView(statsRow)
        content.addView(spacer(dp(10)))

        // ── Current Game Profile ──
        if (profile != null) {
            content.addView(divider())
            content.addView(spacer(dp(10)))
            val appLabelStr = try { pm.getApplicationLabel(pm.getApplicationInfo(gamePackage, 0)).toString() } catch (_: Exception) { "Juego activo" }
            content.addView(TextView(this).apply { text = appLabelStr; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
            content.addView(spacer(dp(2)))

            // Info line ABOVE the selector
            val info = "${profile.refreshRate}Hz  |  ${profile.resolution}% Res" + (if (profile.touchSensitivity) "  |  Tacto" else "") + (if (profile.gpuBoost) "  |  GPU Max" else "") + (if (profile.dndEnabled) "  |  DND" else "")
            content.addView(TextView(this).apply { text = info; textSize = 8f; setTextColor(0x88FFFFFF.toInt()); gravity = Gravity.CENTER })
            content.addView(spacer(dp(8)))

            // Segmented Control
            val modeNames = arrayOf("Ahorro", "Equilibrado", "Turbo")
            val segBg = GradientDrawable().apply { setColor(0x1AFFFFFF); cornerRadius = dp(10).toFloat() }
            val modeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = segBg
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }
            modeNames.forEachIndexed { i, name ->
                val selected = profile.perfMode == i
                val btn = TextView(this).apply {
                    text = name; textSize = 10f; gravity = Gravity.CENTER
                    setTextColor(if (selected) 0xFF000000.toInt() else 0xAAFFFFFF.toInt())
                    background = if (selected) makeRoundRect(0xFF00E676.toInt(), dp(8).toFloat()) else null
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        val updated = profile.copy(perfMode = i)
                        repo.save(updated)
                        applyProfile(updated)
                        hidePanel(); showPanel()
                    }
                }
                modeRow.addView(btn)
            }
            content.addView(modeRow)
        }

        content.addView(spacer(dp(10)))
        content.addView(divider())
        content.addView(spacer(dp(8)))

        // ── Quick Toggles ──
        content.addView(label("Controles rapidos"))
        content.addView(spacer(dp(6)))
        
        val toggleGrid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        // Turbo CPU
        toggleGrid.addView(circleIconBtn(android.R.drawable.ic_media_ff, "Turbo") {
            scope.launch(Dispatchers.IO) {
                Shell.su("for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$c; done")
                withContext(Dispatchers.Main) { Toast.makeText(this@GameOverlayService, "Modo Turbo Activado", Toast.LENGTH_SHORT).show() }
            }
        })
        // GPU Boost
        toggleGrid.addView(circleIconBtn(android.R.drawable.ic_menu_slideshow, "GPU") {
            scope.launch(Dispatchers.IO) {
                Shell.su("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor; echo performance > /sys/class/devfreq/*gpu*/governor")
                withContext(Dispatchers.Main) { Toast.makeText(this@GameOverlayService, "GPU Boost Activado", Toast.LENGTH_SHORT).show() }
            }
        })
        // DND
        toggleGrid.addView(circleIconBtn(android.R.drawable.ic_lock_silent_mode, "DND") {
            scope.launch(Dispatchers.IO) {
                Shell.su("cmd notification set_dnd priority")
                withContext(Dispatchers.Main) { Toast.makeText(this@GameOverlayService, "No Molestar Activado", Toast.LENGTH_SHORT).show() }
            }
        })
        // Limpiar RAM
        toggleGrid.addView(circleIconBtn(android.R.drawable.ic_menu_delete, "Limpiar") {
            scope.launch(Dispatchers.IO) {
                Shell.su("sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all")
                withContext(Dispatchers.Main) { Toast.makeText(this@GameOverlayService, "Memoria RAM Liberada", Toast.LENGTH_SHORT).show() }
            }
        })
        // Sensibilidad tactil
        toggleGrid.addView(circleIconBtn(android.R.drawable.ic_menu_manage, "Tacto") {
            scope.launch(Dispatchers.IO) {
                Shell.su("echo 1 > /proc/touchpanel/game_switch_enable")
                withContext(Dispatchers.Main) { Toast.makeText(this@GameOverlayService, "Sensibilidad Tactil Activada", Toast.LENGTH_SHORT).show() }
            }
        })
        content.addView(toggleGrid)

        content.addView(spacer(dp(10)))
        content.addView(divider())
        content.addView(spacer(dp(8)))

        // ── Floating Apps (user-configurable) ──
        content.addView(spacer(dp(4)))
        content.addView(divider())
        content.addView(spacer(dp(8)))
        content.addView(label("Acceso Rapido"))
        content.addView(spacer(dp(6)))
        val appsGrid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        floatingPkgs.take(6).forEach { pkg ->
            val appIcon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }
            val btn = FrameLayout(this).apply {
                val cardBg = GradientDrawable().apply {
                    setColor(0x22FFFFFF)
                    cornerRadius = dp(14).toFloat()
                }
                background = cardBg
                elevation = dp(4).toFloat()
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(5); marginEnd = dp(5) }
                setOnClickListener { launchFloating(pkg); hidePanel() }
                if (appIcon != null) {
                    addView(ImageView(this@GameOverlayService).apply { setImageDrawable(appIcon) }, FrameLayout.LayoutParams(dp(30), dp(30)).apply { gravity = Gravity.CENTER })
                }
            }
            appsGrid.addView(btn)
        }
        content.addView(appsGrid)

        scroll.addView(content)
        root.addView(scroll)

        val params = overlayParams(panelW, panelH).apply {
            gravity = Gravity.CENTER
            flags = (flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND) and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            dimAmount = 0.15f
        }
        wm.addView(root, params)
        panelView = root
        isPanelOpen = true
        startMonitoring()
    }

    private data class OriginalSystemState(
        val cpuGovernor: String?,
        val minRefreshRate: String?,
        val peakRefreshRate: String?,
        val anglePkgs: String?,
        val angleValues: String?,
        val tcpLowLatency: String?,
        val tcpCongestion: String?,
        val tcpRmem: String?,
        val tcpWmem: String?,
        val swappiness: String?,
        val originalSize: String?,
        val originalDensity: String?
    )

    private var originalState: OriginalSystemState? = null

    private fun applyProfile(p: GameProfile) {
        scope.launch(Dispatchers.IO) {
            // Backup
            originalState = OriginalSystemState(
                cpuGovernor = Shell.su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null", silentLog = true)?.trim(),
                minRefreshRate = Shell.su("settings get system min_refresh_rate 2>/dev/null", silentLog = true)?.trim(),
                peakRefreshRate = Shell.su("settings get system peak_refresh_rate 2>/dev/null", silentLog = true)?.trim(),
                anglePkgs = Shell.su("settings get global angle_gl_driver_selection_pkgs 2>/dev/null", silentLog = true)?.trim(),
                angleValues = Shell.su("settings get global angle_gl_driver_selection_values 2>/dev/null", silentLog = true)?.trim(),
                tcpLowLatency = Shell.su("sysctl -n net.ipv4.tcp_low_latency 2>/dev/null", silentLog = true)?.trim(),
                tcpCongestion = Shell.su("sysctl -n net.ipv4.tcp_congestion_control 2>/dev/null", silentLog = true)?.trim(),
                tcpRmem = Shell.su("sysctl -n net.ipv4.tcp_rmem 2>/dev/null", silentLog = true)?.trim(),
                tcpWmem = Shell.su("sysctl -n net.ipv4.tcp_wmem 2>/dev/null", silentLog = true)?.trim(),
                swappiness = Shell.su("sysctl -n vm.swappiness 2>/dev/null", silentLog = true)?.trim(),
                originalSize = Shell.su("wm size 2>/dev/null", silentLog = true)?.substringAfter("Physical size:")?.trim(),
                originalDensity = Shell.su("wm density 2>/dev/null", silentLog = true)?.substringAfter("Physical density:")?.substringBefore('\n')?.trim()
            )
            val gov = when (p.perfMode) { 0 -> "powersave"; 2 -> "performance"; else -> "schedutil" }
            Shell.su("for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \$c 2>/dev/null; done")
            Shell.su("settings put system min_refresh_rate ${p.refreshRate}")
            Shell.su("settings put system peak_refresh_rate ${p.refreshRate}")
            if (p.resolution < 100) {
                val rawSize = originalState?.originalSize ?: Shell.su("wm size")?.substringAfter("Physical size:")?.trim() ?: return@launch
                val rawDensity = originalState?.originalDensity ?: Shell.su("wm density")?.substringAfter("Physical density:")?.substringBefore('\n')?.trim() ?: return@launch
                val parts = rawSize.split("x")
                if (parts.size == 2) {
                    val w = parts[0].trim().toIntOrNull() ?: return@launch
                    val h = parts[1].trim().toIntOrNull() ?: return@launch
                    val newW = w * p.resolution / 100
                    val newH = h * p.resolution / 100
                    Shell.su("wm size ${newW}x${newH}")
                    
                    val density = rawDensity.toIntOrNull()
                    if (density != null) {
                        val newDensity = density * p.resolution / 100
                        Shell.su("wm density $newDensity")
                    }
                }
            } else {
                Shell.su("wm size reset")
                Shell.su("wm density reset")
            }
            if (p.graphicsApi == "default") {
                Shell.su("settings delete global angle_gl_driver_selection_pkgs")
                Shell.su("settings delete global angle_gl_driver_selection_values")
            } else {
                Shell.su("settings put global angle_gl_driver_selection_pkgs ${p.packageName}")
                Shell.su("settings put global angle_gl_driver_selection_values ${p.graphicsApi}")
            }
            if (p.touchSensitivity) Shell.su("echo 1 > /proc/touchpanel/game_switch_enable 2>/dev/null")
            if (p.wifiLowLatency) {
                Shell.su("sysctl -w net.ipv4.tcp_low_latency=1 2>/dev/null")
                Shell.su("sysctl -w net.ipv4.tcp_congestion_control=${p.tcpCongestion} 2>/dev/null")
                if (p.advancedNet) {
                    Shell.su("sysctl -w net.ipv4.tcp_rmem='4096 87380 1048576' 2>/dev/null")
                    Shell.su("sysctl -w net.ipv4.tcp_wmem='4096 16384 1048576' 2>/dev/null")
                }
            }
            if (p.gpuBoost) { Shell.su("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null"); Shell.su("echo performance > /sys/class/devfreq/*gpu*/governor 2>/dev/null") }
            if (p.dndEnabled) Shell.su("cmd notification set_dnd priority 2>/dev/null")
            
            // Extreme Memory Management
            if (p.aggressiveClear) {
                Shell.su("sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null")
            }
            if (p.disableZram) {
                Shell.su("sysctl -w vm.swappiness=10 2>/dev/null")
            }
            if (p.killBackground) {
                Shell.su("am kill-all 2>/dev/null; am shrink-all 2>/dev/null")
            }
        }
    }

    private fun makeGaugeCard(title: String, colorStart: Int, colorEnd: Int): Triple<LinearLayout, RingGaugeView, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = makeRoundRect(0x16FFFFFF, dp(16).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) }
            setPadding(0, dp(10), 0, dp(10))
        }
        val lbl = TextView(this).apply { text = title; textSize = 11f; setTextColor(colorStart); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD }
        
        val ringContainer = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply { topMargin = dp(6) } }
        val ring = RingGaugeView(this, colorStart, colorEnd).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val tv = TextView(this).apply { text = "--%"; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD }
        
        ringContainer.addView(ring); ringContainer.addView(tv)
        card.addView(lbl); card.addView(ringContainer)
        return Triple(card, ring, tv)
    }

    private fun circleIconBtn(resId: Int, label: String, action: () -> Unit): LinearLayout {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val circle = FrameLayout(this).apply {
            background = makeRoundRect(0x33FFFFFF, dp(22).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            addView(ImageView(this@GameOverlayService).apply {
                setImageResource(resId)
                setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener { action() }
        }
        val tv = TextView(this).apply { text = label; textSize = 7f; setTextColor(0x99FFFFFF.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) }
        wrapper.addView(circle); wrapper.addView(tv)
        return wrapper
    }

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 9f; setTextColor(0x99FFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD }
    private fun divider() = View(this).apply { setBackgroundColor(0x22FFFFFF); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1) }
    private fun spacer(s: Int, h: Boolean = false) = View(this).apply { layoutParams = if (h) LinearLayout.LayoutParams(s, 0) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s) }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isPanelOpen) {
                try {
                    val (cpu, ram, gpu, temp) = withContext(Dispatchers.IO) {
                        val stat = Shell.su("cat /proc/stat", silentLog = true)?.lines()?.firstOrNull { it.startsWith("cpu ") }
                        val cpuPct = parseCpuLine(stat)
                        val memLines = Shell.su("cat /proc/meminfo", silentLog = true)?.lines() ?: emptyList()
                        val total = memLines.find { it.startsWith("MemTotal") }?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 1L
                        val avail = memLines.find { it.startsWith("MemAvailable") }?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 0L
                        val ramPct = ((total - avail) * 100 / total).toInt()
                        val gpuPct = Shell.su("cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null", silentLog = true)?.replace("%","")?.trim()?.toIntOrNull()
                            ?: Shell.su("cat /sys/class/devfreq/*gpu*/load 2>/dev/null", silentLog = true)?.split("\\s+".toRegex())?.firstOrNull()?.toIntOrNull() ?: 0
                        val t = Shell.su("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null", silentLog = true)?.trim()?.toIntOrNull()?.let { if (it > 1000) it / 1000 else it } ?: 0
                        arrayOf(cpuPct, ramPct, gpuPct, t)
                    }
                    cpuGauge?.progress = cpu; cpuText?.text = "$cpu%"
                    ramGauge?.progress = ram; ramText?.text = "$ram%"
                    gpuGauge?.progress = gpu; gpuText?.text = "$gpu%"
                    tempText?.text = "${temp} C"
                } catch (_: Exception) { }
                delay(1000)
            }
        }
    }

    private var prevIdle = 0L; private var prevTotal = 0L
    private fun parseCpuLine(line: String?): Int {
        if (line == null) return 0
        val parts = line.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return 0
        val idle = parts[3]; val total = parts.sum()
        val dIdle = idle - prevIdle; val dTotal = total - prevTotal
        prevIdle = idle; prevTotal = total
        return if (dTotal > 0) (100 * (dTotal - dIdle) / dTotal).toInt().coerceIn(0, 100) else 0
    }

    private fun hidePanel() { monitorJob?.cancel(); panelView?.let { try { wm.removeView(it) } catch (_: Exception) {} }; panelView = null; isPanelOpen = false }

    private fun launchFloating(pkg: String) {
        scope.launch(Dispatchers.IO) {
            android.util.Log.d("YxaGameTime", "Intentando lanzar $pkg en modo libre...")
            Shell.su("settings put global hidden_api_policy 0 2>/dev/null")
            Shell.su("settings put global enable_freeform_support 1 2>/dev/null")
            Shell.su("settings put global force_resizable_activities 1 2>/dev/null")
            withContext(Dispatchers.Main) {
                val intent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                } 
                
                if (intent == null) {
                    android.util.Log.e("YxaGameTime", "No se encontró el intent para el paquete: $pkg")
                    Toast.makeText(this@GameOverlayService, "Error: App no encontrada", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                try {
                    android.util.Log.d("YxaGameTime", "Configurando ActivityOptions mediante reflexión...")
                    val options = ActivityOptions.makeBasic()
                    
                    val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.java)
                    method.isAccessible = true
                    method.invoke(options, 5) // 5 = WINDOWING_MODE_FREEFORM
                    android.util.Log.d("YxaGameTime", "setLaunchWindowingMode(5) invocado con éxito")

                    val dm = resources.displayMetrics
                    val boundsMethod = ActivityOptions::class.java.getMethod("setLaunchBounds", Rect::class.java)
                    boundsMethod.isAccessible = true
                    boundsMethod.invoke(options, Rect(dm.widthPixels/5, dm.heightPixels/5, dm.widthPixels*4/5, dm.heightPixels*4/5))
                    android.util.Log.d("YxaGameTime", "setLaunchBounds invocado con éxito")

                    startActivity(intent, options.toBundle())
                    android.util.Log.d("YxaGameTime", "startActivity ejecutado en modo libre")
                } catch (e: Exception) {
                    val errorMsg = "Error en reflexion (Freeform): ${e.message}\nCause: ${e.cause}"
                    android.util.Log.e("YxaGameTime", errorMsg, e)
                    Toast.makeText(this@GameOverlayService, "Fallo Freeform: ${e.message}", Toast.LENGTH_LONG).show()
                    
                    // Fallback normal launch
                    android.util.Log.d("YxaGameTime", "Ejecutando fallback: lanzamiento normal")
                    startActivity(intent)
                }
            }
        }
    }

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
    private fun makeRoundRect(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Yxa Game Overlay", NotificationManager.IMPORTANCE_LOW).apply { description = "Overlay de GameTime" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }
    private fun buildNotification(): Notification = Notification.Builder(this, CHANNEL_ID).setContentTitle("Yxa GameTime activo").setContentText("Panel de control activo").setSmallIcon(android.R.drawable.ic_menu_manage).build()

    private fun restoreOriginalState(state: OriginalSystemState?) {
        if (state == null) return
        state.cpuGovernor?.let { Shell.su("for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $it > \$c 2>/dev/null; done") }
        
        if (state.minRefreshRate != null && state.minRefreshRate != "null") Shell.su("settings put system min_refresh_rate ${state.minRefreshRate}")
        else Shell.su("settings delete system min_refresh_rate")
        
        if (state.peakRefreshRate != null && state.peakRefreshRate != "null") Shell.su("settings put system peak_refresh_rate ${state.peakRefreshRate}")
        else Shell.su("settings delete system peak_refresh_rate")

        Shell.su("wm size reset 2>/dev/null")
        Shell.su("wm density reset 2>/dev/null")
        
        if (state.anglePkgs != null && state.anglePkgs != "null") Shell.su("settings put global angle_gl_driver_selection_pkgs ${state.anglePkgs}")
        else Shell.su("settings delete global angle_gl_driver_selection_pkgs")
        
        if (state.angleValues != null && state.angleValues != "null") Shell.su("settings put global angle_gl_driver_selection_values ${state.angleValues}")
        else Shell.su("settings delete global angle_gl_driver_selection_values")
        
        state.tcpLowLatency?.let { Shell.su("sysctl -w net.ipv4.tcp_low_latency=$it 2>/dev/null") }
        state.tcpCongestion?.let { Shell.su("sysctl -w net.ipv4.tcp_congestion_control=$it 2>/dev/null") }
        state.tcpRmem?.let { Shell.su("sysctl -w net.ipv4.tcp_rmem='$it' 2>/dev/null") }
        state.tcpWmem?.let { Shell.su("sysctl -w net.ipv4.tcp_wmem='$it' 2>/dev/null") }
        state.swappiness?.let { Shell.su("sysctl -w vm.swappiness=$it 2>/dev/null") }
        
        Shell.su("cmd notification set_dnd off 2>/dev/null")
    }

    override fun onDestroy() {
        fabView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        hidePanel()
        CoroutineScope(Dispatchers.IO).launch { restoreOriginalState(originalState) }
        scope.cancel()
        super.onDestroy()
    }
}

class RingGaugeView(context: Context, val colorStart: Int, val colorEnd: Int) : View(context) {
    var progress = 0
        set(value) { field = value; invalidate() }
    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14f; color = 0x22FFFFFF; strokeCap = Paint.Cap.ROUND }
    private val paintFg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14f; strokeCap = Paint.Cap.ROUND }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val r = Math.min(cx, cy) - 10f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, 135f, 270f, false, paintBg)
        if (progress > 0) {
            paintFg.shader = SweepGradient(cx, cy, colorStart, colorEnd).apply { val matrix = Matrix(); matrix.setRotate(135f, cx, cy); setLocalMatrix(matrix) }
            canvas.drawArc(rect, 135f, 270f * (progress / 100f), false, paintFg)
        }
    }
}
