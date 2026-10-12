package nl.veerdonk.pillreminder.platform

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.platform.receivers.ActionReceiver
import java.time.LocalDate
import java.time.format.DateTimeParseException

/*
 * Pure decisions and intent plumbing of the ring service (docs/03-technical-design.md §8),
 * kept apart from the Android service so they can be unit tested.
 */

/**
 * FR-3.3: the alarm is silent (light and vibration only) when the ringer is on silent or Do Not
 * Disturb is on, i.e. the interruption filter is anything other than "all". Vibrate mode still
 * plays the sound: the alarm stream ignores it.
 */
internal fun shouldPlaySound(ringerMode: Int, interruptionFilter: Int): Boolean =
    ringerMode != AudioManager.RINGER_MODE_SILENT &&
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL

/** What is ringing, or about to: a real dose, or the Settings "Test alarm" ([test]). */
internal data class ActiveRing(val spec: RingSpec, val test: Boolean = false) {
    companion object {
        /** The placeholder spec of a test alarm. It is never sent to the engine. */
        val TEST_SPEC = RingSpec(dayId = LocalDate.of(1970, 1, 1), dose = 1, doseCount = 1, canSnooze = false, snoozesLeft = 0)

        fun test() = ActiveRing(TEST_SPEC, test = true)
    }
}

internal enum class RingChange {
    /** Nothing is ringing for this dose yet: stop whatever rings and start fresh. */
    START,

    /** Already ringing for this dose, and nothing visible changed: do nothing. */
    NONE,

    /** Already ringing for this dose; only the Snooze button appeared or disappeared. */
    UPDATE_NOTIFICATION,
}

/** Design §8: the same dayId/dose is idempotent, and a changed `canSnooze` only updates the notification. */
internal fun ringChange(current: ActiveRing?, requested: ActiveRing): RingChange = when {
    current == null -> RingChange.START
    current.test != requested.test -> RingChange.START
    current.spec.dayId != requested.spec.dayId || current.spec.dose != requested.spec.dose -> RingChange.START
    current.spec.canSnooze != requested.spec.canSnooze -> RingChange.UPDATE_NOTIFICATION
    else -> RingChange.NONE
}

/**
 * Process-wide record of what the ring controller last asked for. It lets the controller stay
 * idempotent without a service round trip, and lets a test AlarmActivity close itself when the
 * test ring ends.
 */
internal object RingState {
    val current = MutableStateFlow<ActiveRing?>(null)
}

/**
 * Intent extras that carry a [RingSpec] to the service. DAY_ID and DOSE share their keys with
 * the notification buttons ([ActionReceiver]), so AlarmActivity reads the same extras whether it
 * was opened by the notification or by the service.
 */
internal object RingExtras {
    const val DAY_ID = ActionReceiver.EXTRA_DAY_ID
    const val DOSE = ActionReceiver.EXTRA_DOSE
    const val DOSE_COUNT = "nl.veerdonk.pillreminder.extra.DOSE_COUNT"
    const val CAN_SNOOZE = "nl.veerdonk.pillreminder.extra.CAN_SNOOZE"
    const val SNOOZES_LEFT = "nl.veerdonk.pillreminder.extra.SNOOZES_LEFT"
    const val TEST = "nl.veerdonk.pillreminder.extra.TEST"

    fun put(intent: Intent, ring: ActiveRing): Intent = intent
        .putExtra(DAY_ID, ring.spec.dayId.toString())
        .putExtra(DOSE, ring.spec.dose)
        .putExtra(DOSE_COUNT, ring.spec.doseCount)
        .putExtra(CAN_SNOOZE, ring.spec.canSnooze)
        .putExtra(SNOOZES_LEFT, ring.spec.snoozesLeft)
        .putExtra(TEST, ring.test)

    /** The ring in [intent], or null when an extra is missing or the dayId can't be parsed. */
    fun read(intent: Intent?): ActiveRing? {
        val dayId = intent?.getStringExtra(DAY_ID) ?: return null
        val day = try {
            LocalDate.parse(dayId)
        } catch (_: DateTimeParseException) {
            return null
        }
        val dose = intent.getIntExtra(DOSE, 0)
        if (dose < 1) return null
        return ActiveRing(
            spec = RingSpec(
                dayId = day,
                dose = dose,
                doseCount = intent.getIntExtra(DOSE_COUNT, dose),
                canSnooze = intent.getBooleanExtra(CAN_SNOOZE, false),
                snoozesLeft = intent.getIntExtra(SNOOZES_LEFT, 0),
            ),
            test = intent.getBooleanExtra(TEST, false),
        )
    }
}
