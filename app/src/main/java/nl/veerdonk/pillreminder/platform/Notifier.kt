package nl.veerdonk.pillreminder.platform

import android.app.Notification
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.RingSpec

/** Notification channels, the reminder notification and the alarm notification (docs/03-technical-design.md §4, §7). */
interface Notifier {
    fun ensureChannels()
    fun showReminder(spec: ReminderSpec)
    fun cancelReminder()
    fun buildAlarmNotification(spec: RingSpec): Notification // used by the ring service
}
