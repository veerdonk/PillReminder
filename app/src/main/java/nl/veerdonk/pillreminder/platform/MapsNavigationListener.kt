package nl.veerdonk.pillreminder.platform

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import nl.veerdonk.pillreminder.PillReminderApp

/**
 * Watches Google Maps' notifications to hold the alarm while it navigates (FR-3.8, design §6a).
 * The system binds it once the user grants notification access; it is not a foreground service.
 * Only Maps' notifications are looked at, and nothing is stored.
 */
class MapsNavigationListener : NotificationListenerService() {

    private val detector: AndroidNavigationDetector
        get() = (application as PillReminderApp).container.navigationDetector

    override fun onListenerConnected() {
        val active = try {
            activeNotifications.orEmpty().map { it.facts() }
        } catch (e: SecurityException) {
            Log.w(TAG, "Couldn't read active notifications", e)
            emptyList()
        }
        detector.update { it.connected(active) }
    }

    override fun onListenerDisconnected() {
        detector.update { it.disconnected() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == MapsNavigation.MAPS_PACKAGE) detector.update { it.posted(sbn.facts()) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MapsNavigation.MAPS_PACKAGE) detector.update { it.removed(sbn.key) }
    }

    private fun StatusBarNotification.facts() = NotificationFacts(
        key = key,
        packageName = packageName,
        id = id,
        ongoing = isOngoing,
        category = notification.category,
        channelId = notification.channelId,
    )

    private companion object {
        const val TAG = "MapsNavigationListener"
    }
}
