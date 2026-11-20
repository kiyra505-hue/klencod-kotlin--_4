package com.uranus.island

import android.animation.ValueAnimator
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.session.MediaController
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs

class UranusIslandService : Service(), UranusEventListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var islandRoot: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // عناصر الواجهة داخل الجزيرة
    private lateinit var compactRow: LinearLayout
    private lateinit var compactBadge: TextView
    private lateinit var compactTitle: TextView
    private lateinit var visualizerBars: List<View>

    private lateinit var expandedSection: LinearLayout
    private lateinit var albumArtView: ImageView
    private lateinit var expandedTitle: TextView
    private lateinit var expandedSubtitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var mediaControlsRow: LinearLayout
    private lateinit var btnPlayPause: Button

    private var isExpanded = false
    private var lastWifiConnected: Boolean? = null
    private var lastChargingState: Boolean? = null
    private var lastVolumeLevel: Int = -1
    private var sizeAnimator: ValueAnimator? = null
    private var visualizerAnimator: ValueAnimator? = null

    private val autoCollapseRunnable = Runnable {
        if (isExpanded) {
            collapseIsland()
        }
    }

    private val volumeObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (!UranusPrefs.isReactVolume(this@UranusIslandService)) return
            val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            if (current != lastVolumeLevel) {
                lastVolumeLevel = current
                val pct = ((current * 100f) / max).toInt().coerceIn(0, 100)
                UranusEventBus.publishEvent(
                    UranusEvent(
                        type = UranusEventType.VOLUME,
                        title = "مستوى الصوت: $pct%",
                        subtitle = "تحكم فوري في صوت الوسائط",
                        progress = pct,
                        durationMs = 2400L
                    )
                )
            }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            when (intent.action) {
                ConnectivityManager.CONNECTIVITY_ACTION,
                android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    if (!UranusPrefs.isReactWifi(context)) return
                    checkAndPublishWifiState(context)
                }

                Intent.ACTION_BATTERY_CHANGED -> {
                    if (!UranusPrefs.isReactBattery(context)) return
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else -1

                    if (lastChargingState == null) {
                        lastChargingState = isCharging
                    } else if (isCharging != lastChargingState) {
                        lastChargingState = isCharging
                        UranusEventBus.publishEvent(
                            UranusEvent(
                                type = UranusEventType.BATTERY,
                                title = if (isCharging) "بدأ الشحن • ${pct}%" else "تم فصل الشاحن • ${pct}%",
                                subtitle = if (isCharging) "البطارية قيد التغذية" else "العمل على طاقة البطارية",
                                progress = pct,
                                durationMs = 3200L
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        UranusEventBus.register(this)
        UranusPrefs.setIslandEnabled(this, true)

        buildIslandView()
        attachWindow()
        registerSystemMonitors()
        startVisualizerLoop()

        UranusEventBus.publishEvent(
            UranusEvent(
                type = UranusEventType.SYSTEM,
                title = "Uranus Island",
                subtitle = "جزيرة أورانوس التفاعلية نشطة",
                progress = -1,
                durationMs = 2800L
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applySavedPositionAndTheme()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        UranusPrefs.setIslandEnabled(this, false)
        UranusEventBus.unregister(this)
        mainHandler.removeCallbacks(autoCollapseRunnable)
        sizeAnimator?.cancel()
        visualizerAnimator?.cancel()

        try {
            contentResolver.unregisterContentObserver(volumeObserver)
        } catch (_: Exception) {
        }

        try {
            unregisterReceiver(systemReceiver)
        } catch (_: Exception) {
        }

        islandRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        islandRoot = null
    }

    private fun buildIslandView() {
        val compactW = dpToPx(UranusPrefs.getCompactWidthDp(this))
        val compactH = dpToPx(UranusPrefs.getCompactHeightDp(this))

        compactRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = compactH
        }

        compactBadge = TextView(this).apply {
            text = "✦"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, dpToPx(6), 0)
        }

        compactTitle = TextView(this).apply {
            text = "Uranus"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            isSelected = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val barsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), 0, 0, 0)
        }

        val createdBars = mutableListOf<View>()
        val barColors = listOf("#38BDF8", "#818CF8", "#34D399", "#F472B6")
        for (i in 0 until 4) {
            val bar = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor(barColors[i % barColors.size]))
                    cornerRadius = dpToPx(3).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(3), dpToPx(10)).apply {
                    setMargins(dpToPx(1), 0, dpToPx(1), 0)
                }
            }
            createdBars.add(bar)
            barsContainer.addView(bar)
        }
        visualizerBars = createdBars

        compactRow.addView(compactBadge)
        compactRow.addView(compactTitle)
        compactRow.addView(barsContainer)

        // القسم الموسع للتفاصيل والتحكم
        expandedSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(10), 0, 0)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        albumArtView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = createGlassDrawable("#22FFFFFF", "#55FFFFFF", dpToPx(10).toFloat(), 1)
            layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(42)).apply {
                setMargins(0, 0, dpToPx(10), 0)
            }
            visibility = View.GONE
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        expandedTitle = TextView(this).apply {
            text = "Uranus Island"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        expandedSubtitle = TextView(this).apply {
            text = "جاهز للتفاعل مع النظام والموسيقى"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 11.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dpToPx(2), 0, 0)
        }

        textCol.addView(expandedTitle)
        textCol.addView(expandedSubtitle)

        headerRow.addView(albumArtView)
        headerRow.addView(textCol)
        expandedSection.addView(headerRow)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(6)
            ).apply {
                topMargin = dpToPx(8)
            }
        }
        expandedSection.addView(progressBar)

        mediaControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(10)
            }
        }

        val btnPrev = createControlButton("⏮") {
            UranusEventBus.sendMediaPrevious()
            resetAutoCollapseTimer()
        }
        btnPlayPause = createControlButton("⏯") {
            UranusEventBus.sendMediaPlayPause()
            resetAutoCollapseTimer()
        }
        val btnNext = createControlButton("⏭") {
            UranusEventBus.sendMediaNext()
            resetAutoCollapseTimer()
        }
        val btnCollapse = createControlButton("طي") {
            collapseIsland()
        }

        mediaControlsRow.addView(btnPrev)
        mediaControlsRow.addView(btnPlayPause)
        mediaControlsRow.addView(btnNext)
        mediaControlsRow.addView(btnCollapse)

        expandedSection.addView(mediaControlsRow)

        var downRawX = 0f
        var downRawY = 0f
        var initialX = 0
        var initialY = 0
        var isDragging = false

        islandRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(8))
            background = buildThemeDrawable()
            addView(compactRow)
            addView(expandedSection)

            setOnTouchListener { _, event ->
                val lp = layoutParams ?: return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        initialX = lp.x
                        initialY = lp.y
                        isDragging = false
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downRawX).toInt()
                        val dy = (event.rawY - downRawY).toInt()
                        if (abs(dx) > dpToPx(6) || abs(dy) > dpToPx(6)) {
                            isDragging = true
                        }
                        if (isDragging) {
                            lp.x = initialX + dx
                            lp.y = (initialY + dy).coerceAtLeast(0)
                            windowManager?.updateViewLayout(this, lp)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            UranusPrefs.setOffsetX(this@UranusIslandService, lp.x)
                            UranusPrefs.setOffsetY(this@UranusIslandService, lp.y)
                        } else {
                            if (isExpanded) {
                                collapseIsland()
                            } else {
                                expandForCurrentState()
                            }
                        }
                        true
                    }

                    else -> false
                }
            }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            compactW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = UranusPrefs.getOffsetX(this@UranusIslandService)
            y = UranusPrefs.getOffsetY(this@UranusIslandService)
        }
    }

    private fun attachWindow() {
        val view = islandRoot ?: return
        val lp = layoutParams ?: return
        try {
            windowManager?.addView(view, lp)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun applySavedPositionAndTheme() {
        val view = islandRoot ?: return
        val lp = layoutParams ?: return
        view.background = buildThemeDrawable()
        lp.x = UranusPrefs.getOffsetX(this)
        lp.y = UranusPrefs.getOffsetY(this)
        if (!isExpanded) {
            lp.width = dpToPx(UranusPrefs.getCompactWidthDp(this))
        }
        try {
            windowManager?.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }
    }

    private fun buildThemeDrawable(): GradientDrawable {
        val radius = dpToPx(UranusPrefs.getCornerRadiusDp(this)).toFloat()
        return when (UranusPrefs.getGlassTheme(this)) {
            1 -> createGlassDrawable("#881E293B", "#AAFFFFFF", radius, 2)
            2 -> createGlassDrawable("#B81E1B4B", "#99A5B4FC", radius, 2)
            else -> createGlassDrawable("#D9090D16", "#88FFFFFF", radius, 2)
        }
    }

    private fun expandForCurrentState() {
        val last = UranusEventBus.lastEvent
        val hasMedia = UranusEventBus.activeMediaController != null || UranusEventBus.isCurrentTrackPlaying
        if (hasMedia && last.type != UranusEventType.NOTIFICATION && last.type != UranusEventType.DOWNLOAD) {
            showExpandedCard(
                title = UranusEventBus.currentTrackTitle,
                subtitle = UranusEventBus.currentTrackArtist,
                progress = -1,
                albumArt = UranusEventBus.currentAlbumArt,
                showMediaControls = true,
                durationMs = 6000L
            )
        } else {
            showExpandedCard(
                title = last.title,
                subtitle = last.subtitle,
                progress = last.progress,
                albumArt = last.albumArt,
                showMediaControls = hasMedia,
                durationMs = 5000L
            )
        }
    }

    private fun showExpandedCard(
        title: String,
        subtitle: String,
        progress: Int,
        albumArt: Bitmap?,
        showMediaControls: Boolean,
        durationMs: Long
    ) {
        val view = islandRoot ?: return
        val lp = layoutParams ?: return

        isExpanded = true
        expandedTitle.text = title
        expandedSubtitle.text = subtitle

        if (albumArt != null) {
            albumArtView.setImageBitmap(albumArt)
            albumArtView.visibility = View.VISIBLE
        } else {
            albumArtView.visibility = View.GONE
        }

        if (progress in 0..100) {
            progressBar.isIndeterminate = false
            progressBar.progress = progress
            progressBar.visibility = View.VISIBLE
        } else if (progress < -1) {
            progressBar.isIndeterminate = true
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }

        mediaControlsRow.visibility = if (showMediaControls) View.VISIBLE else View.GONE
        expandedSection.visibility = View.VISIBLE

        val targetWidth = dpToPx(UranusPrefs.getExpandedWidthDp(this))
        animateWidthTo(lp.width, targetWidth)
        resetAutoCollapseTimer(durationMs)
    }

    private fun collapseIsland() {
        val lp = layoutParams ?: return
        isExpanded = false
        mainHandler.removeCallbacks(autoCollapseRunnable)
        expandedSection.visibility = View.GONE
        val targetWidth = dpToPx(UranusPrefs.getCompactWidthDp(this))
        animateWidthTo(lp.width, targetWidth)
    }

    private fun animateWidthTo(fromWidth: Int, toWidth: Int) {
        val view = islandRoot ?: return
        val lp = layoutParams ?: return
        if (fromWidth == toWidth) return

        sizeAnimator?.cancel()
        val duration = UranusPrefs.getAnimDurationMs(this).toLong().coerceIn(120L, 800L)
        sizeAnimator = ValueAnimator.ofInt(fromWidth, toWidth).apply {
            this.duration = duration
            interpolator = OvershootInterpolator(0.85f)
            addUpdateListener { anim ->
                lp.width = anim.animatedValue as Int
                try {
                    windowManager?.updateViewLayout(view, lp)
                } catch (_: Exception) {
                }
            }
            start()
        }
    }

    private fun resetAutoCollapseTimer(durationMs: Long = 4000L) {
        mainHandler.removeCallbacks(autoCollapseRunnable)
        val prefSec = UranusPrefs.getAutoCollapseSec(this)
        if (prefSec > 0) {
            val effective = maxOf(durationMs, prefSec * 1000L)
            mainHandler.postDelayed(autoCollapseRunnable, effective)
        }
    }

    private fun startVisualizerLoop() {
        visualizerAnimator?.cancel()
        visualizerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                val showViz = UranusPrefs.isVisualizerEnabled(this@UranusIslandService) &&
                    UranusEventBus.isCurrentTrackPlaying
                val f = anim.animatedValue as Float
                visualizerBars.forEachIndexed { index, bar ->
                    if (!showViz) {
                        bar.visibility = View.GONE
                    } else {
                        bar.visibility = View.VISIBLE
                        val factor = when (index % 4) {
                            0 -> 0.35f + (0.65f * f)
                            1 -> 0.95f - (0.55f * f)
                            2 -> 0.45f + (0.55f * (1f - f))
                            else -> 0.85f - (0.45f * (1f - f))
                        }
                        val newH = dpToPx((6 + (12 * factor)).toInt()).coerceAtLeast(dpToPx(5))
                        val lp = bar.layoutParams
                        if (lp.height != newH) {
                            lp.height = newH
                            bar.layoutParams = lp
                        }
                    }
                }
            }
            start()
        }
    }

    override fun onUranusEvent(event: UranusEvent) {
        mainHandler.post {
            when (event.type) {
                UranusEventType.VOLUME -> {
                    compactBadge.text = "🔊"
                    compactTitle.text = event.title
                    showExpandedCard(
                        title = event.title,
                        subtitle = event.subtitle,
                        progress = event.progress,
                        albumArt = null,
                        showMediaControls = UranusEventBus.activeMediaController != null,
                        durationMs = event.durationMs
                    )
                }

                UranusEventType.NOTIFICATION -> {
                    compactBadge.text = "🔔"
                    compactTitle.text = event.title
                    showExpandedCard(
                        title = event.title,
                        subtitle = event.subtitle,
                        progress = -1,
                        albumArt = null,
                        showMediaControls = false,
                        durationMs = event.durationMs
                    )
                }

                UranusEventType.DOWNLOAD -> {
                    compactBadge.text = "⬇"
                    compactTitle.text = if (event.progress >= 0) "${event.title} ${event.progress}%" else event.title
                    val progValue = if (event.progress >= 0) event.progress else -2
                    showExpandedCard(
                        title = event.title,
                        subtitle = event.subtitle,
                        progress = progValue,
                        albumArt = null,
                        showMediaControls = false,
                        durationMs = event.durationMs
                    )
                }

                UranusEventType.WIFI -> {
                    compactBadge.text = "📶"
                    compactTitle.text = event.title
                    showExpandedCard(
                        title = event.title,
                        subtitle = event.subtitle,
                        progress = -1,
                        albumArt = null,
                        showMediaControls = false,
                        durationMs = event.durationMs
                    )
                }

                UranusEventType.BATTERY -> {
                    compactBadge.text = "🔋"
                    compactTitle.text = event.title
                    showExpandedCard(
                        title = event.title,
                        subtitle = event.subtitle,
                        progress = event.progress,
                        albumArt = null,
                        showMediaControls = false,
                        durationMs = event.durationMs
                    )
                }

                UranusEventType.MUSIC -> {
                    compactBadge.text = "🎵"
                    compactTitle.text = event.title
                    btnPlayPause.text = if (event.isPlaying) "⏸" else "▶"
                }

                UranusEventType.SYSTEM -> {
                    compactBadge.text = "✦"
                    compactTitle.text = event.title
                }
            }
        }
    }

    override fun onMediaSessionUpdated(
        controller: MediaController?,
        title: String,
        artist: String,
        isPlaying: Boolean,
        art: Bitmap?
    ) {
        mainHandler.post {
            btnPlayPause.text = if (isPlaying) "⏸" else "▶"
            if (isPlaying && UranusPrefs.isReactMusic(this)) {
                compactBadge.text = "🎵"
                compactTitle.text = title
            } else if (!isExpanded) {
                compactBadge.text = "✦"
                compactTitle.text = "Uranus"
            }
        }
    }

    private fun registerSystemMonitors() {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            lastVolumeLevel = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
            contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver
            )
        } catch (_: Exception) {
        }

        try {
            val filter = IntentFilter().apply {
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                @Suppress("DEPRECATION")
                addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            registerReceiver(systemReceiver, filter)
            checkAndPublishWifiState(this, initialOnly = true)
        } catch (_: Exception) {
        }
    }

    private fun checkAndPublishWifiState(context: Context, initialOnly: Boolean = false) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        var connected = false
        var transportLabel = "لا يوجد اتصال"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    connected = true
                    transportLabel = "متصل بشبكة Wi-Fi"
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    connected = true
                    transportLabel = "متصل ببيانات الهاتف"
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val active = cm.activeNetworkInfo
            if (active != null && active.isConnected) {
                connected = true
                transportLabel = "متصل بالشبكة"
            }
        }

        if (initialOnly) {
            lastWifiConnected = connected
            return
        }

        if (lastWifiConnected != connected) {
            lastWifiConnected = connected
            UranusEventBus.publishEvent(
                UranusEvent(
                    type = UranusEventType.WIFI,
                    title = if (connected) transportLabel else "تم قطع الاتصال بالشبكة",
                    subtitle = if (connected) "الاتصال يعمل الآن" else "لا توجد شبكة نشطة حالياً",
                    progress = -1,
                    durationMs = 3000L
                )
            )
        }
    }

    private fun createControlButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            background = createGlassDrawable("#2BFFFFFF", "#66FFFFFF", dpToPx(12).toFloat(), 1)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply {
                setMargins(dpToPx(3), 0, dpToPx(3), 0)
            }
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun createGlassDrawable(
        fillHex: String,
        strokeHex: String,
        radiusPx: Float,
        strokeWidthPx: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fillHex))
            cornerRadius = radiusPx
            setStroke(strokeWidthPx, Color.parseColor(strokeHex))
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}