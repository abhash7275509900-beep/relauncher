package com.zenith.launcher.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Only per-app counts are kept in memory. Notification contents are never read, stored or sent. */
object NotificationDots {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
}

class DotsService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onListenerDisconnected() { NotificationDots.counts = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        try {
            NotificationDots.counts = (activeNotifications ?: emptyArray())
                .filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
                .groupingBy { it.packageName }
                .eachCount()
        } catch (_: Exception) { }
    }
}
