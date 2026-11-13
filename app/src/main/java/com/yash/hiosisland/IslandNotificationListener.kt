package com.yash.hiosisland

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

class IslandNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        publishActiveMediaSession()
        if (title.isBlank() && text.isBlank()) return
        sendBroadcast(Intent(IslandService.ACTION_NOTIFICATION).apply {
            setPackage(packageName)
            putExtra("title", title.take(100))
            putExtra("text", text.take(220))
            putExtra("package", sbn.packageName)
            putExtra("when", sbn.postTime)
        })
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        publishActiveMediaSession()
        sendBroadcast(Intent(IslandService.ACTION_NOTIFICATION_REMOVED).setPackage(packageName))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        publishActiveMediaSession()
    }

    private fun publishActiveMediaSession() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(this, IslandNotificationListener::class.java))
            val controller = sessions.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
            val metadata = controller?.metadata
            val state = controller?.playbackState
            sendBroadcast(Intent(IslandService.ACTION_MEDIA_SESSION).apply {
                setPackage(packageName)
                putExtra("media_title", metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty())
                putExtra("media_artist", metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty())
                putExtra("media_playing", state?.state == android.media.session.PlaybackState.STATE_PLAYING)
                putExtra("media_position", state?.position ?: 0L)
                putExtra("media_duration", metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
                if (controller != null) putExtra("media_token", controller.sessionToken)
            })
        } catch (_: Exception) {
            // A media session may disappear while it is being queried.
        }
    }
}
