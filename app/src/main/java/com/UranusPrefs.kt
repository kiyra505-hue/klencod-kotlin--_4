package com.uranus.island

import android.content.Context
import android.content.SharedPreferences

object UranusPrefs {

    private const val PREF_NAME = "uranus_island_prefs_v1"

    // مفاتيح الموضع والحجم
    const val KEY_ISLAND_ENABLED = "island_enabled"
    const val KEY_OFFSET_X = "offset_x"
    const val KEY_OFFSET_Y = "offset_y"
    const val KEY_COMPACT_WIDTH_DP = "compact_width_dp"
    const val KEY_COMPACT_HEIGHT_DP = "compact_height_dp"
    const val KEY_EXPANDED_WIDTH_DP = "expanded_width_dp"
    const val KEY_CORNER_RADIUS_DP = "corner_radius_dp"

    // مفاتيح المظهر والأنيميشن
    const val KEY_GLASS_THEME = "glass_theme" // 0: داكن زجاجي, 1: شفاف ضبابي, 2: شفق أورانوس
    const val KEY_ANIM_DURATION_MS = "anim_duration_ms"
    const val KEY_AUTO_COLLAPSE_SEC = "auto_collapse_sec"
    const val KEY_SHOW_VISUALIZER = "show_visualizer"

    // مفاتيح التفاعلات الذكية
    const val KEY_REACT_MUSIC = "react_music"
    const val KEY_REACT_VOLUME = "react_volume"
    const val KEY_REACT_NOTIFICATIONS = "react_notifications"
    const val KEY_REACT_DOWNLOADS = "react_downloads"
    const val KEY_REACT_WIFI = "react_wifi"
    const val KEY_REACT_BATTERY = "react_battery"

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isIslandEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ISLAND_ENABLED, false)

    fun setIslandEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ISLAND_ENABLED, enabled).apply()

    fun getOffsetX(context: Context): Int =
        prefs(context).getInt(KEY_OFFSET_X, 0)

    fun setOffsetX(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_OFFSET_X, value).apply()

    fun getOffsetY(context: Context): Int =
        prefs(context).getInt(KEY_OFFSET_Y, 20)

    fun setOffsetY(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_OFFSET_Y, value).apply()

    fun getCompactWidthDp(context: Context): Int =
        prefs(context).getInt(KEY_COMPACT_WIDTH_DP, 136)

    fun setCompactWidthDp(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_COMPACT_WIDTH_DP, value).apply()

    fun getCompactHeightDp(context: Context): Int =
        prefs(context).getInt(KEY_COMPACT_HEIGHT_DP, 34)

    fun setCompactHeightDp(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_COMPACT_HEIGHT_DP, value).apply()

    fun getExpandedWidthDp(context: Context): Int =
        prefs(context).getInt(KEY_EXPANDED_WIDTH_DP, 332)

    fun setExpandedWidthDp(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_EXPANDED_WIDTH_DP, value).apply()

    fun getCornerRadiusDp(context: Context): Int =
        prefs(context).getInt(KEY_CORNER_RADIUS_DP, 26)

    fun setCornerRadiusDp(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_CORNER_RADIUS_DP, value).apply()

    fun getGlassTheme(context: Context): Int =
        prefs(context).getInt(KEY_GLASS_THEME, 0)

    fun setGlassTheme(context: Context, themeId: Int) =
        prefs(context).edit().putInt(KEY_GLASS_THEME, themeId).apply()

    fun getAnimDurationMs(context: Context): Int =
        prefs(context).getInt(KEY_ANIM_DURATION_MS, 280)

    fun setAnimDurationMs(context: Context, durationMs: Int) =
        prefs(context).edit().putInt(KEY_ANIM_DURATION_MS, durationMs).apply()

    fun getAutoCollapseSec(context: Context): Int =
        prefs(context).getInt(KEY_AUTO_COLLAPSE_SEC, 4)

    fun setAutoCollapseSec(context: Context, seconds: Int) =
        prefs(context).edit().putInt(KEY_AUTO_COLLAPSE_SEC, seconds).apply()

    fun isVisualizerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_VISUALIZER, true)

    fun setVisualizerEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHOW_VISUALIZER, enabled).apply()

    fun isReactMusic(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REACT_MUSIC, true)

    fun setReactMusic(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_REACT_MUSIC, enabled).apply()

    fun isReactVolume(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REACT_VOLUME, true)

    fun setReactVolume(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_REACT_VOLUME, enabled).apply()

    fun isReactNotifications(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REACT_NOTIFICATIONS, true)

    fun setReactNotifications(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_REACT_NOTIFICATIONS, enabled).apply()

    fun isReactDownloads(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REACT_DOWNLOADS, true)

    fun setReactDownloads(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_REACT_DOWNLOADS, enabled).apply()

    fun isReactWifi(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REACT_WIFI, true)

    fun setReactWifi(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_REACT_WIFI, enabled).apply()

    fun isReactBattery(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REACT_BATTERY, true)

    fun setReactBattery(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_REACT_BATTERY, enabled).apply()

    fun resetToDefaults(context: Context) {
        prefs(context).edit().clear().apply()
    }
}