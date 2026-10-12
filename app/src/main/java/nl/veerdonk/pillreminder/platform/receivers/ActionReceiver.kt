package nl.veerdonk.pillreminder.platform.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.PillReminderApp
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.Event
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Buttons on the reminder and alarm notifications (docs/03-technical-design.md §7). Not
 * exported: only the app's own immutable PendingIntents reach it.
 *
 * The event is forwarded with the intent's dayId as `expectedDayId`, so the controller drops
 * an action from a notification posted for another day (01 A15).
 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PillReminderApp).container
        val action = parseAction(
            action = intent.action,
            dayId = intent.getStringExtra(EXTRA_DAY_ID),
            dose = if (intent.hasExtra(EXTRA_DOSE)) intent.getIntExtra(EXTRA_DOSE, 0) else null,
            source = intent.getStringExtra(EXTRA_SOURCE),
            now = container.clock.instant(),
        ) ?: return
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.reminderController.handle(action.event, expectedDayId = action.dayId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOOK = "nl.veerdonk.pillreminder.action.TOOK"
        const val ACTION_SKIP = "nl.veerdonk.pillreminder.action.SKIP"
        const val ACTION_SNOOZE = "nl.veerdonk.pillreminder.action.SNOOZE"

        /** The app-day the notification was posted for, as an ISO date (`2026-10-12`). */
        const val EXTRA_DAY_ID = "nl.veerdonk.pillreminder.extra.DAY_ID"

        /** The 1-based dose the notification was posted for. */
        const val EXTRA_DOSE = "nl.veerdonk.pillreminder.extra.DOSE"

        /** Which notification the button was on: [DoseSource.NOTIFICATION] or [DoseSource.ALARM], by name. */
        const val EXTRA_SOURCE = "nl.veerdonk.pillreminder.extra.SOURCE"

        /** The broadcast a button sends. [source] is NOTIFICATION (reminder) or ALARM. */
        fun intent(context: Context, action: String, dayId: LocalDate, dose: Int, source: DoseSource): Intent =
            Intent(context, ActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_DAY_ID, dayId.toString())
                .putExtra(EXTRA_DOSE, dose)
                .putExtra(EXTRA_SOURCE, source.name)
    }
}

/** A button press mapped to the event to send and the day it belongs to. */
internal data class NotificationAction(val event: Event, val dayId: LocalDate)

/**
 * Maps a button intent's [action] and extras to the event for the controller. TOOK becomes
 * `TookDose(dose, now, source)`, SKIP `SkipRest(dose)` and SNOOZE `Snooze(dose)`. Returns null
 * (the intent is ignored) for an unknown action, or a missing or unparsable dayId, dose or
 * source. Only NOTIFICATION and ALARM are valid sources.
 */
internal fun parseAction(action: String?, dayId: String?, dose: Int?, source: String?, now: Instant): NotificationAction? {
    val day = dayId?.let {
        try {
            LocalDate.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    } ?: return null
    if (dose == null || dose < 1) return null
    val event = when (action) {
        ActionReceiver.ACTION_TOOK -> {
            val doseSource = when (source) {
                DoseSource.NOTIFICATION.name -> DoseSource.NOTIFICATION
                DoseSource.ALARM.name -> DoseSource.ALARM
                else -> return null
            }
            Event.TookDose(dose, now, doseSource)
        }
        ActionReceiver.ACTION_SKIP -> Event.SkipRest(dose)
        ActionReceiver.ACTION_SNOOZE -> Event.Snooze(dose)
        else -> return null
    }
    return NotificationAction(event, day)
}
