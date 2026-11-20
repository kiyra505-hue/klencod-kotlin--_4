package com.uranus.island

import android.graphics.Bitmap
import android.media.session.MediaController
import java.util.concurrent.CopyOnWriteArrayList

enum class UranusEventType {
    MUSIC,
    VOLUME,
    NOTIFICATION,
    DOWNLOAD,
    WIFI,
    BATTERY,
    SYSTEM
}

data class UranusEvent(
    val type: UranusEventType,
    val title: String,
    val subtitle: String = "",
    val progress: Int = -1, // 0..100 للتحميل أو الصوت أو البطارية، -1 إذا لا يوجد شريط
    val isPlaying: Boolean = false,
    val albumArt: Bitmap? = null,
    val durationMs: Long = 4000L,
    val timestamp: Long = System.currentTimeMillis()
)

interface UranusEventListener {
    fun onUranusEvent(event: UranusEvent)
    fun onMediaSessionUpdated(controller: MediaController?, title: String, artist: String, isPlaying: Boolean, art: Bitmap?)
}

object UranusEventBus {

    private val listeners = CopyOnWriteArrayList<UranusEventListener>()

    @Volatile
    var activeMediaController: MediaController? = null
        private set

    @Volatile
    var currentTrackTitle: String = "لا يوجد مقطع قيد التشغيل"
        private set

    @Volatile
    var currentTrackArtist: String = "Uranus Media"
        private set

    @Volatile
    var isCurrentTrackPlaying: Boolean = false
        private set

    @Volatile
    var currentAlbumArt: Bitmap? = null
        private set

    @Volatile
    var lastEvent: UranusEvent = UranusEvent(
        type = UranusEventType.SYSTEM,
        title = "Uranus • جاهز",
        subtitle = "الجزيرة التفاعلية بانتظار الأحداث",
        progress = -1,
        isPlaying = false,
        albumArt = null,
        durationMs = 0L
    )
        private set

    fun register(listener: UranusEventListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregister(listener: UranusEventListener) {
        listeners.remove(listener)
    }

    fun publishEvent(event: UranusEvent) {
        lastEvent = event
        for (listener in listeners) {
            try {
                listener.onUranusEvent(event)
            } catch (_: Exception) {
            }
        }
    }

    fun updateMediaState(
        controller: MediaController?,
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumArt: Bitmap?
    ) {
        activeMediaController = controller
        currentTrackTitle = if (title.isBlank()) "مقطع صوتي نشط" else title
        currentTrackArtist = if (artist.isBlank()) "مشغل الوسائط" else artist
        isCurrentTrackPlaying = isPlaying
        currentAlbumArt = albumArt

        for (listener in listeners) {
            try {
                listener.onMediaSessionUpdated(
                    controller,
                    currentTrackTitle,
                    currentTrackArtist,
                    isCurrentTrackPlaying,
                    currentAlbumArt
                )
            } catch (_: Exception) {
            }
        }
    }

    fun sendMediaPlayPause() {
        val ctrl = activeMediaController ?: return
        try {
            val state = ctrl.playbackState?.state
            if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                ctrl.transportControls.pause()
            } else {
                ctrl.transportControls.play()
            }
        } catch (_: Exception) {
        }
    }

    fun sendMediaNext() {
        val ctrl = activeMediaController ?: return
        try {
            ctrl.transportControls.skipToNext()
        } catch (_: Exception) {
        }
    }

    fun sendMediaPrevious() {
        val ctrl = activeMediaController ?: return
        try {
            ctrl.transportControls.skipToPrevious()
        } catch (_: Exception) {
        }
    }
}