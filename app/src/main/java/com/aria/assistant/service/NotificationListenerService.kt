package com.aria.assistant.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListenerService : NotificationListenerService() {

    companion object {
        val recentNotifications = mutableListOf<String>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val app = sbn.packageName

        if (title.isNotEmpty() || text.isNotEmpty()) {
            val entry = "[$app] $title: $text"
            recentNotifications.add(0, entry)
            if (recentNotifications.size > 20) {
                recentNotifications.removeAt(recentNotifications.size - 1)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
