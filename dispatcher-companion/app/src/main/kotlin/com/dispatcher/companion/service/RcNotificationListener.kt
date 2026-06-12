package com.dispatcher.companion.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Watches RingCentral's notifications to auto-start dispatch mode on call
 * start and auto-finish notes on call end (FR-103).
 */
class RcNotificationListener : NotificationListenerService() {

    private var activeCallKey: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!RcCallParser.isRingCentral(sbn.packageName)) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (RcCallParser.looksLikeActiveCall(title, text, sbn.isOngoing)) {
            if (activeCallKey == null) {
                activeCallKey = sbn.key
                DispatchForegroundService.start(this)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == activeCallKey) {
            activeCallKey = null
            DispatchForegroundService.stop(this) // triggers summary generation
        }
    }
}
