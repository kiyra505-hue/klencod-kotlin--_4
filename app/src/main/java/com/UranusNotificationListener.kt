package com.uranus.island

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class UranusNotificationListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val sessionCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            pushCurrentMediaState()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            pushCurrentMediaState()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachToBestController(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        setupMediaSessionTracking()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        detachController()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: ""

        // فحص إذا كان الإشعار يمثل عملية تحميل (Progress Bar)
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progressCurrent = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val progressIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        if (progressMax > 0 || progressIndeterminate) {
            if (!UranusPrefs.isReactDownloads(this)) return
            val pct = if (progressMax > 0) {
                ((progressCurrent.coerceAtLeast(0) * 100f) / progressMax).toInt().coerceIn(0, 100)
            } else {
                -1
            }
            val downloadTitle = if (title.isNotEmpty()) title else "جاري التحميل..."
            val downloadSub = if (pct >= 0) "$text • $pct%".trim().trimStart('•').trim() else text.ifEmpty { "عملية نشطة" }

            UranusEventBus.publishEvent(
                UranusEvent(
                    type = UranusEventType.DOWNLOAD,
                    title = downloadTitle,
                    subtitle = downloadSub,
                    progress = pct,
                    durationMs = 4500L
                )
            )
            return
        }

        // تجاهل إشعارات الوسائط المكررة لأنها تُعالج عبر MediaSession
        val hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        if (hasMediaSession) {
            handler.post { refreshActiveSessions() }
            return
        }

        if (!UranusPrefs.isReactNotifications(this)) return
        if (title.isEmpty() && text.isEmpty()) return

        val appLabel = getAppName(sbn.packageName)
        val displayTitle = if (title.isNotEmpty()) title else appLabel
        val displaySubtitle = if (text.isNotEmpty()) {
            if (title.isNotEmpty() && appLabel.isNotEmpty() && title != appLabel) "$appLabel • $text" else text
        } else {
            appLabel
        }

        UranusEventBus.publishEvent(
            UranusEvent(
                type = UranusEventType.NOTIFICATION,
                title = displayTitle,
                subtitle = displaySubtitle,
                progress = -1,
                durationMs = 4500L
            )
        )
    }

    private fun setupMediaSessionTracking() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, UranusNotificationListener::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            refreshActiveSessions()
        } catch (_: Exception) {
        }
    }

    private fun refreshActiveSessions() {
        try {
            val componentName = ComponentName(this, UranusNotificationListener::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            attachToBestController(controllers)
        } catch (_: Exception) {
        }
    }

    private fun attachToBestController(controllers: List<MediaController>?) {
        detachController()
        if (controllers.isNullOrEmpty()) return

        val playingCtrl = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        activeController = playingCtrl
        try {
            activeController?.registerCallback(sessionCallback)
        } catch (_: Exception) {
        }
        pushCurrentMediaState()
    }

    private fun detachController() {
        try {
            activeController?.unregisterCallback(sessionCallback)
        } catch (_: Exception) {
        }
        activeController = null
    }

    private fun pushCurrentMediaState() {
        val ctrl = activeController
        if (ctrl == null) {
            UranusEventBus.updateMediaState(
                controller = null,
                title = "لا يوجد مقطع قيد التشغيل",
                artist = "Uranus Media",
                isPlaying = false,
                albumArt = null
            )
            return
        }

        val metadata = ctrl.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "مقطع صوتي نشط"

        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: "مشغل الوسائط"

        val art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val state = ctrl.playbackState?.state
        val isPlaying = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

        UranusEventBus.updateMediaState(
            controller = ctrl,
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            albumArt = art
        )

        if (UranusPrefs.isReactMusic(this) && isPlaying) {
            UranusEventBus.publishEvent(
                UranusEvent(
                    type = UranusEventType.MUSIC,
                    title = title,
                    subtitle = artist,
                    progress = -1,
                    isPlaying = true,
                    albumArt = art,
                    durationMs = 4500L
                )
            )
        }
    }

    private fun getAppName(pkgName: String): String {
        return try {
            val pm = packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkgName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkgName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkgName
        }
    }
}