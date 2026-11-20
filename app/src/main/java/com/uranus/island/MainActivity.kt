package com.uranus.island

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var previewIsland: LinearLayout
    private lateinit var previewTitle: TextView
    private lateinit var previewSubtitle: TextView
    private var isPreviewExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#070B14"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(32))
        }

        // العنوان الرئيسي للتطبيق
        val appTitle = TextView(this).apply {
            text = "أورانوس • URANUS ISLAND"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val appSub = TextView(this).apply {
            text = "منظومة الجزيرة التفاعلية الزجاجية المتكاملة للأندرويد"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, dpToPx(18))
        }
        container.addView(appTitle)
        container.addView(appSub)

        // 1. قسم المعاينة الحية والتشغيل
        val controlCard = createSectionCard("1. التشغيل والمعاينة الحية")

        val previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(14), dpToPx(20), dpToPx(14), dpToPx(20))
            background = createGlass("#16FFFFFF", "#33FFFFFF", dpToPx(22).toFloat(), 1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(14)
            }
        }

        previewIsland = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            background = buildPreviewDrawable()
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(UranusPrefs.getCompactWidthDp(this@MainActivity)),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                isPreviewExpanded = !isPreviewExpanded
                updatePreview()
            }
        }

        previewTitle = TextView(this).apply {
            text = "✦ Uranus"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        previewSubtitle = TextView(this).apply {
            text = "اضغط للتوسيع أو الطي • جاهز للتفاعل"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 11f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dpToPx(4), 0, 0)
        }

        previewIsland.addView(previewTitle)
        previewIsland.addView(previewSubtitle)
        previewBox.addView(previewIsland)
        controlCard.addView(previewBox)

        val btnStart = createPrimaryButton("تشغيل / تحديث جزيرة أورانوس", "#059669", "#34D399") {
            if (!canDrawOverlay()) {
                Toast.makeText(this, "يرجى تفعيل صلاحية الظهور فوق التطبيقات أولاً", Toast.LENGTH_SHORT).show()
                openOverlaySettings()
                return@createPrimaryButton
            }
            val intent = Intent(this, UranusIslandService::class.java)
            startService(intent)
            Toast.makeText(this, "تم تشغيل جزيرة أورانوس", Toast.LENGTH_SHORT).show()
        }

        val btnStop = createPrimaryButton("إيقاف الجزيرة العائمة", "#991B1B", "#F87171") {
            stopService(Intent(this, UranusIslandService::class.java))
            Toast.makeText(this, "تم إيقاف الجزيرة", Toast.LENGTH_SHORT).show()
        }

        controlCard.addView(btnStart)
        controlCard.addView(btnStop)
        container.addView(controlCard)

        // 2. قسم الصلاحيات الأساسية
        val permCard = createSectionCard("2. الصلاحيات المطلوبة للتفاعل الكامل")

        val btnOverlayPerm = createPrimaryButton(
            "صلاحية الظهور فوق التطبيقات (مطلوبة)",
            "#1D4ED8",
            "#60A5FA"
        ) {
            openOverlaySettings()
        }

        val btnNotifPerm = createPrimaryButton(
            "صلاحية الإشعارات والوسائط والتحميل (مطلوبة)",
            "#4338CA",
            "#818CF8"
        ) {
            openNotificationListenerSettings()
        }

        permCard.addView(btnOverlayPerm)
        permCard.addView(btnNotifPerm)
        container.addView(permCard)

        // 3. قسم ضبط الكاميرا والموقع والحجم
        val posCard = createSectionCard("3. ضبط الكاميرا والموقع والحجم")

        posCard.addView(
            createSeekRow(
                labelPrefix = "الإزاحة الأفقية (X)",
                unit = "px",
                minVal = -220,
                maxVal = 220,
                initialVal = UranusPrefs.getOffsetX(this)
            ) { v ->
                UranusPrefs.setOffsetX(this, v)
                notifyServiceUpdate()
            }
        )

        posCard.addView(
            createSeekRow(
                labelPrefix = "الإزاحة العلوية (Y)",
                unit = "px",
                minVal = 0,
                maxVal = 220,
                initialVal = UranusPrefs.getOffsetY(this)
            ) { v ->
                UranusPrefs.setOffsetY(this, v)
                notifyServiceUpdate()
            }
        )

        posCard.addView(
            createSeekRow(
                labelPrefix = "عرض الكبسولة المصغرة",
                unit = "dp",
                minVal = 96,
                maxVal = 240,
                initialVal = UranusPrefs.getCompactWidthDp(this)
            ) { v ->
                UranusPrefs.setCompactWidthDp(this, v)
                updatePreview()
                notifyServiceUpdate()
            }
        )

        posCard.addView(
            createSeekRow(
                labelPrefix = "عرض البطاقة الموسعة",
                unit = "dp",
                minVal = 260,
                maxVal = 380,
                initialVal = UranusPrefs.getExpandedWidthDp(this)
            ) { v ->
                UranusPrefs.setExpandedWidthDp(this, v)
                updatePreview()
                notifyServiceUpdate()
            }
        )

        posCard.addView(
            createSeekRow(
                labelPrefix = "استدارة الحواف",
                unit = "dp",
                minVal = 12,
                maxVal = 40,
                initialVal = UranusPrefs.getCornerRadiusDp(this)
            ) { v ->
                UranusPrefs.setCornerRadiusDp(this, v)
                updatePreview()
                notifyServiceUpdate()
            }
        )

        container.addView(posCard)

        // 4. قسم المظهر والأنيميشن
        val themeCard = createSectionCard("4. المظهر والأنيميشن")

        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }
        }

        val btnThemeDark = createChipButton("داكن زجاجي") {
            UranusPrefs.setGlassTheme(this, 0)
            updatePreview()
            notifyServiceUpdate()
        }
        val btnThemeGlass = createChipButton("شفاف ضبابي") {
            UranusPrefs.setGlassTheme(this, 1)
            updatePreview()
            notifyServiceUpdate()
        }
        val btnThemeAurora = createChipButton("شفق أورانوس") {
            UranusPrefs.setGlassTheme(this, 2)
            updatePreview()
            notifyServiceUpdate()
        }

        themeRow.addView(btnThemeDark)
        themeRow.addView(btnThemeGlass)
        themeRow.addView(btnThemeAurora)
        themeCard.addView(themeRow)

        themeCard.addView(
            createSeekRow(
                labelPrefix = "سرعة أنيميشن التمدد",
                unit = "ms",
                minVal = 140,
                maxVal = 650,
                initialVal = UranusPrefs.getAnimDurationMs(this)
            ) { v ->
                UranusPrefs.setAnimDurationMs(this, v)
            }
        )

        themeCard.addView(
            createSeekRow(
                labelPrefix = "الطي التلقائي بعد",
                unit = "ث",
                minVal = 0,
                maxVal = 12,
                initialVal = UranusPrefs.getAutoCollapseSec(this)
            ) { v ->
                UranusPrefs.setAutoCollapseSec(this, v)
            }
        )

        themeCard.addView(
            createSwitchRow("عرض موجات الموسيقى المتحركة", UranusPrefs.isVisualizerEnabled(this)) { checked ->
                UranusPrefs.setVisualizerEnabled(this, checked)
            }
        )

        container.addView(themeCard)

        // 5. قسم التفاعلات الذكية مع النظام
        val reactCard = createSectionCard("5. التفاعلات الذكية مع النظام")

        reactCard.addView(
            createSwitchRow("التفاعل مع الموسيقى والوسائط", UranusPrefs.isReactMusic(this)) {
                UranusPrefs.setReactMusic(this, it)
            }
        )
        reactCard.addView(
            createSwitchRow("التفاعل عند رفع أو خفض الصوت", UranusPrefs.isReactVolume(this)) {
                UranusPrefs.setReactVolume(this, it)
            }
        )
        reactCard.addView(
            createSwitchRow("التفاعل مع الإشعارات الواردة", UranusPrefs.isReactNotifications(this)) {
                UranusPrefs.setReactNotifications(this, it)
            }
        )
        reactCard.addView(
            createSwitchRow("التفاعل مع عمليات التحميل", UranusPrefs.isReactDownloads(this)) {
                UranusPrefs.setReactDownloads(this, it)
            }
        )
        reactCard.addView(
            createSwitchRow("التفاعل مع حالة Wi-Fi والشبكة", UranusPrefs.isReactWifi(this)) {
                UranusPrefs.setReactWifi(this, it)
            }
        )
        reactCard.addView(
            createSwitchRow("التفاعل مع حالة البطارية والشحن", UranusPrefs.isReactBattery(this)) {
                UranusPrefs.setReactBattery(this, it)
            }
        )

        container.addView(reactCard)

        // 6. قسم الاختبار المباشر وإعادة الضبط
        val testCard = createSectionCard("6. اختبار الأحداث وإعادة الضبط")

        val testRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val btnTestNotif = createChipButton("اختبار إشعار") {
            UranusEventBus.publishEvent(
                UranusEvent(
                    type = UranusEventType.NOTIFICATION,
                    title = "رسالة جديدة • أورانوس",
                    subtitle = "تجربة عرض الإشعار داخل الجزيرة التفاعلية",
                    durationMs = 4000L
                )
            )
        }

        val btnTestDownload = createChipButton("اختبار تحميل 75%") {
            UranusEventBus.publishEvent(
                UranusEvent(
                    type = UranusEventType.DOWNLOAD,
                    title = "تحميل ملف Uranus_Update.apk",
                    subtitle = "جاري التنزيل بسرعة عالية • 75%",
                    progress = 75,
                    durationMs = 4500L
                )
            )
        }

        val btnTestVolume = createChipButton("اختبار صوت 80%") {
            UranusEventBus.publishEvent(
                UranusEvent(
                    type = UranusEventType.VOLUME,
                    title = "مستوى الصوت: 80%",
                    subtitle = "مؤشر الصوت التفاعلي",
                    progress = 80,
                    durationMs = 2800L
                )
            )
        }

        testRow1.addView(btnTestNotif)
        testRow1.addView(btnTestDownload)
        testRow1.addView(btnTestVolume)
        testCard.addView(testRow1)

        val btnReset = createPrimaryButton("إعادة ضبط جميع الإعدادات الافتراضية", "#334155", "#94A3B8") {
            UranusPrefs.resetToDefaults(this)
            recreate()
        }
        testCard.addView(btnReset)

        container.addView(testCard)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    private fun updatePreview() {
        val radius = dpToPx(UranusPrefs.getCornerRadiusDp(this)).toFloat()
        previewIsland.background = buildPreviewDrawable(radius)
        val targetW = if (isPreviewExpanded) {
            dpToPx(UranusPrefs.getExpandedWidthDp(this))
        } else {
            dpToPx(UranusPrefs.getCompactWidthDp(this))
        }
        previewIsland.layoutParams = LinearLayout.LayoutParams(
            targetW,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        if (isPreviewExpanded) {
            previewTitle.text = "Uranus Island • وضع موسّع"
            previewSubtitle.visibility = View.VISIBLE
        } else {
            previewTitle.text = "✦ Uranus"
            previewSubtitle.visibility = View.GONE
        }
    }

    private fun buildPreviewDrawable(radiusPx: Float = dpToPx(26).toFloat()): GradientDrawable {
        return when (UranusPrefs.getGlassTheme(this)) {
            1 -> createGlass("#881E293B", "#AAFFFFFF", radiusPx, 2)
            2 -> createGlass("#B81E1B4B", "#99A5B4FC", radiusPx, 2)
            else -> createGlass("#D9090D16", "#88FFFFFF", radiusPx, 2)
        }
    }

    private fun notifyServiceUpdate() {
        if (UranusPrefs.isIslandEnabled(this)) {
            startService(Intent(this, UranusIslandService::class.java))
        }
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val cn = ComponentName(this, UranusNotificationListener::class.java)
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", cn.flattenToString())
                }
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun createSectionCard(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
            background = createGlass("#14FFFFFF", "#2EFFFFFF", dpToPx(20).toFloat(), 1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(14)
            }
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dpToPx(10))
            })
        }
    }

    private fun createSeekRow(
        labelPrefix: String,
        unit: String,
        minVal: Int,
        maxVal: Int,
        initialVal: Int,
        onValueChanged: (Int) -> Unit
    ): LinearLayout {
        val clampedInit = initialVal.coerceIn(minVal, maxVal)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }
        }
        val tv = Text