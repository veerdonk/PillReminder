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

/** Placeholder until T7 wires the real notifier: shows nothing. */
object NoOpNotifier : Notifier {
    override fun ensureChannels() = Unit
    override fun showReminder(spec: ReminderSpec) = Unit
    override fun cancelReminder() = Unit

    // Only the ring service (T8) calls this, and the no-op RingController never starts it.
    override fun buildAlarmNotification(spec: RingSpec): Notification = Notification()
}
