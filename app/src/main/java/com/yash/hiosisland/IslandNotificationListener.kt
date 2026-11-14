package com.yash.hiosisland

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandNotificationListener : NotificationListenerService() {
    private var trackedController: MediaController? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishController(trackedController)
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            publishController(trackedController)
        }

        override fun onSessionDestroyed() {
            trackedController = null
            publishActiveMediaSession()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publishActiveMediaSession()
        // Ignore this app own foreground-service notification to prevent a self-notification loop.
        if (sbn.packageName == packageName) return
        publishNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        publishActiveMediaSession()
        if (sbn.packageName == packageName) return
        sendBroadcast(Intent(IslandService.ACTION_NOTIFICATION_REMOVED).apply {
            setPackage(packageName)
            putExtra("notification_key", sbn.key)
        })
        val remaining = try {
            activeNotifications?.filterNot { it.key == sbn.key }?.maxByOrNull { it.postTime }
        } catch (_: Exception) { null }
        if (remaining != null) publishNotification(remaining)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        publishActiveMediaSession()
        try { activeNotifications?.filterNot { it.packageName == packageName }?.maxByOrNull { it.postTime }?.let { publishNotification(it) } }
        catch (_: Exception) { }
    }

    override fun onDestroy() {
        try { trackedController?.unregisterCallback(mediaCallback) } catch (_: Exception) { }
        trackedController = null
        super.onDestroy()
    }

    private fun publishNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return
        sendBroadcast(Intent(IslandService.ACTION_NOTIFICATION).apply {
            setPackage(packageName)
            putExtra("title", title.take(100))
            putExtra("text", body.take(240))
            putExtra("package", sbn.packageName)
            putExtra("notification_key", sbn.key)
            putExtra("content_intent", notification.contentIntent)
            putExtra("when", sbn.postTime)
        })
    }

    private fun publishActiveMediaSession() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(this, IslandNotificationListener::class.java))
            val selected = sessions.firstOrNull {
                it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull()
            val previous = trackedController
            if (previous?.sessionToken != selected?.sessionToken) {
                try { previous?.unregisterCallback(mediaCallback) } catch (_: Exception) { }
                trackedController = selected
                try { selected?.registerCallback(mediaCallback, Handler(Looper.getMainLooper())) }
                catch (_: Exception) { }
            }
            publishController(selected)
        } catch (_: Exception) {
            publishController(null)
        }
    }

    private fun publishController(controller: MediaController?) {
        val metadata = controller?.metadata
        val state = controller?.playbackState
        sendBroadcast(Intent(IslandService.ACTION_MEDIA_SESSION).apply {
            setPackage(packageName)
            putExtra("media_title", metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty())
            putExtra("media_artist", metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty())
            putExtra("media_playing", state?.state == android.media.session.PlaybackState.STATE_PLAYING)
            putExtra("media_position", state?.position ?: 0L)
            putExtra("media_duration", metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
            if (controller != null) putExtra("media_token", controller.sessionToken)
        })
    }
}
