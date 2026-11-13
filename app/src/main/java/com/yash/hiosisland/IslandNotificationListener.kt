package com.yash.hiosisland

import android.content.Intent
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
        if (title.isBlank() && text.isBlank()) return

        val broadcast = Intent(IslandService.ACTION_NOTIFICATION).apply {
            setPackage(packageName)
            putExtra("title", title.take(100))
            putExtra("text", text.take(220))
            putExtra("package", sbn.packageName)
            putExtra("when", sbn.postTime)
            n.actions?.take(5)?.forEachIndexed { index, action ->
                val label = action.title?.toString().orEmpty()
                if (label.contains("play", true) || label.contains("pause", true)) {
                    putExtra("media_toggle", action.actionIntent)
                }
                if (label.contains("next", true)) putExtra("media_next", action.actionIntent)
                if (label.contains("previous", true)) putExtra("media_previous", action.actionIntent)
            }
        }
        sendBroadcast(broadcast)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        sendBroadcast(Intent(IslandService.ACTION_NOTIFICATION_REMOVED).setPackage(packageName))
    }
}
