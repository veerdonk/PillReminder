package nl.veerdonk.pillreminder.platform

import android.app.Notification
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.core.model.StatusSpec

/** Notification channels, the reminder, status and alarm notifications (docs/03-technical-design.md §4, §7). */
interface Notifier {
    fun ensureChannels()
    fun showReminder(spec: ReminderSpec)
    fun cancelReminder()
    fun showStatus(spec: StatusSpec) // persistent status between doses (FR-2.4)
    fun cancelStatus()
    fun buildAlarmNotification(spec: RingSpec): Notification // used by the ring service
}
