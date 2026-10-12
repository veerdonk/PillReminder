package nl.veerdonk.pillreminder.ui.home

import nl.veerdonk.pillreminder.core.engine.Engine
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.time.TimeMath
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/*
 * Pure view model of the Home screen (FR-5, FR-6). Everything here is a function of the
 * persisted DayState, the Settings, `now` and the zone, with no Android types. Text is typed
 * instead of worded, so the composables can use strings.xml (NFR-5, assumption A9).
 */

/** Status of one dose row on the status card (FR-6.1). */
sealed interface DoseRowStatus {
    data class Taken(val at: Instant) : DoseRowStatus

    /** Reminder or alarm active for this dose. */
    data object Pending : DoseRowStatus

    /** Not yet due. [approxAt] is the estimate ("upcoming at ~HH:MM"), null if unknown. */
    data class Upcoming(val approxAt: Instant?) : DoseRowStatus

    data object Skipped : DoseRowStatus

    data object Missed : DoseRowStatus
}

data class DoseRow(val index: Int, val status: DoseRowStatus)

/** The detection line shown before dose 1 (FR-6.1). */
data class DetectionInfo(val earliestAt: Instant, val fallbackAt: Instant)

/** The next-action line (FR-6.2). */
sealed interface NextAction {
    /** BEFORE_WINDOW: detection starts at [at]. */
    data class DetectionStartsAt(val at: Instant) : NextAction

    /** DETECTING: the reminder comes at [fallbackAt] at the latest. */
    data class ReminderByFallback(val fallbackAt: Instant) : NextAction

    /** WAITING: the next dose is due at [at]. */
    data class ReminderAt(val at: Instant) : NextAction

    /** REMINDER: the alarm rings in [minutes] (rounded up, at least 1). */
    data class AlarmIn(val minutes: Long) : NextAction

    /** REMINDER: the alarm rings at [at], more than [ALARM_IN_MAX_MINUTES] away (e.g. dose 1 held until fallback). */
    data class AlarmAt(val at: Instant) : NextAction

    /** RINGING. */
    data object Ringing : NextAction

    /** SNOOZED until [until]. */
    data class SnoozedUntil(val until: Instant) : NextAction

    /** PENDING_SILENT: no more alarms for this dose. */
    data object NoMoreAlarms : NextAction

    /** DONE: quiet until the next app-day starts at [nextDayAt]. */
    data class DoneUntil(val nextDayAt: Instant) : NextAction
}

enum class SkipKind { NOT_TODAY, DONE_FOR_TODAY }

/** Which actions are valid in the current phase (02 §4.3). */
data class HomeActions(
    /** The dose that "Took pill N now/at..." records, or null when none is valid. */
    val recordDose: Int?,
    val remindNow: Boolean,
    /** "Not today" (dose 1) or "I'm done for today" (later doses); null when not valid. */
    val skip: SkipKind?,
    val resumeDay: Boolean,
)

data class HomeModel(
    val state: DayState,
    val settings: Settings,
    val doseCount: Int,
    val rows: List<DoseRow>,
    val detection: DetectionInfo?,
    val nextAction: NextAction,
    val actions: HomeActions,
)

private val REMINDER_PHASES = setOf(Phase.REMINDER, Phase.RINGING, Phase.SNOOZED, Phase.PENDING_SILENT)
private val REMIND_NOW_PHASES = setOf(Phase.BEFORE_WINDOW, Phase.DETECTING, Phase.WAITING, Phase.PENDING_SILENT)

/** True while a reminder or alarm for the next dose is active. */
fun isReminderActive(phase: Phase): Boolean = phase in REMINDER_PHASES

/**
 * Builds the Home model. A state from a previous app-day (the rollover Tick hasn't run yet) is
 * shown as its rolled-over successor, so the screen never shows yesterday as today.
 */
fun buildHomeModel(stored: DayState?, settings: Settings, now: Instant, zone: ZoneId): HomeModel {
    val today = TimeMath.dayIdOf(now, settings, zone)
    val base = stored ?: Engine.fresh(today)
    val state = if (base.dayId != today) Engine.reduce(base, Event.Tick(false), settings, now, zone).state else base
    return HomeModel(
        state = state,
        settings = settings,
        doseCount = settings.dosesPerDay,
        rows = doseRows(state, settings, now),
        detection = detectionInfo(state, settings, zone),
        nextAction = nextAction(state, settings, now, zone),
        actions = homeActions(state),
    )
}

/** Per-dose status rows (FR-6.1). In DONE only dose records are listed. */
fun doseRows(state: DayState, settings: Settings, now: Instant): List<DoseRow> {
    val byIndex = state.doses.associateBy { it.index }
    val count = if (state.phase == Phase.DONE) state.doses.maxOfOrNull { it.index } ?: 0 else settings.dosesPerDay
    val nextDue = nextDoseEstimate(state, settings, now)
    return (1..count).map { index ->
        val record = byIndex[index]
        val status = when {
            record != null -> when (record.status) {
                DoseStatus.TAKEN -> DoseRowStatus.Taken(record.takenAt ?: now)
                DoseStatus.SKIPPED -> DoseRowStatus.Skipped
                DoseStatus.MISSED -> DoseRowStatus.Missed
            }
            state.phase != Phase.DONE && index == state.nextDose && isReminderActive(state.phase) ->
                DoseRowStatus.Pending
            else -> {
                val steps = (index - state.nextDose).toLong()
                DoseRowStatus.Upcoming(if (steps >= 0) nextDue?.plus(settings.doseInterval.multipliedBy(steps)) else null)
            }
        }
        DoseRow(index, status)
    }
}

/** When the next dose is expected, if known: only while WAITING after a taken dose. */
private fun nextDoseEstimate(state: DayState, settings: Settings, now: Instant): Instant? {
    if (state.phase != Phase.WAITING) return null
    val last = state.doses.firstOrNull { it.index == state.nextDose - 1 }?.takenAt
    return last?.plus(settings.doseInterval) ?: now
}

/** The detection line is only shown while waiting for dose 1 to be triggered. */
fun detectionInfo(state: DayState, settings: Settings, zone: ZoneId): DetectionInfo? =
    if (state.phase == Phase.BEFORE_WINDOW || state.phase == Phase.DETECTING) {
        DetectionInfo(
            earliestAt = TimeMath.earliestAt(state.dayId, settings, zone),
            fallbackAt = TimeMath.fallbackAt(state.dayId, settings, zone),
        )
    } else {
        null
    }

/** The next-action line (FR-6.2), from the engine's outputs and the derived deadlines. */
fun nextAction(state: DayState, settings: Settings, now: Instant, zone: ZoneId): NextAction {
    val outputs = Engine.outputs(state, settings, now, zone)
    return when (state.phase) {
        Phase.BEFORE_WINDOW -> NextAction.DetectionStartsAt(outputs.nextWakeAt)
        Phase.DETECTING -> NextAction.ReminderByFallback(TimeMath.fallbackAt(state.dayId, settings, zone))
        Phase.WAITING -> NextAction.ReminderAt(outputs.nextWakeAt)
        Phase.REMINDER -> {
            val minutes = minutesUntil(outputs.nextWakeAt, now)
            if (minutes > ALARM_IN_MAX_MINUTES) NextAction.AlarmAt(outputs.nextWakeAt) else NextAction.AlarmIn(minutes)
        }
        Phase.RINGING -> NextAction.Ringing
        Phase.SNOOZED -> NextAction.SnoozedUntil(outputs.nextWakeAt)
        Phase.PENDING_SILENT -> NextAction.NoMoreAlarms
        Phase.DONE -> NextAction.DoneUntil(TimeMath.nextRolloverAt(state.dayId, settings, zone))
    }
}

/** Up to this many minutes the alarm is shown as "Alarm in N min"; beyond it as a time of day. */
const val ALARM_IN_MAX_MINUTES = 60L

/** Whole minutes until [at], rounded up and at least 1. */
fun minutesUntil(at: Instant, now: Instant): Long {
    val millis = Duration.between(now, at).toMillis()
    return maxOf(1L, (millis + 59_999) / 60_000)
}

/** Only actions whose event is valid in the phase (02 §4.3). */
fun homeActions(state: DayState): HomeActions {
    val done = state.phase == Phase.DONE
    return HomeActions(
        recordDose = if (done) null else state.nextDose,
        remindNow = state.phase in REMIND_NOW_PHASES,
        skip = when {
            done -> null
            state.nextDose == 1 -> SkipKind.NOT_TODAY
            else -> SkipKind.DONE_FOR_TODAY
        },
        resumeDay = done && state.doses.any { it.status == DoseStatus.SKIPPED },
    )
}

// ---- Backdating (FR-5.2) ----------------------------------------------------------------

enum class BackdateError { IN_FUTURE, BEFORE_PREVIOUS_DOSE, BEFORE_START_OF_DAY }

sealed interface BackdateResult {
    data class Valid(val at: Instant) : BackdateResult

    /** [limit] is the boundary that was violated (now, the previous dose, or the start of day). */
    data class Invalid(val error: BackdateError, val limit: Instant) : BackdateResult
}

/**
 * Maps a time of day from the picker to an instant of the current app-day: on the app-day's
 * calendar date, or the next date when the time is before `dayRolloverTime` (so 01:30 after
 * midnight belongs to the day that started the evening before).
 */
fun backdatedInstant(time: LocalTime, settings: Settings, now: Instant, zone: ZoneId): Instant {
    val day = TimeMath.dayIdOf(now, settings, zone)
    val date = if (time.isBefore(settings.dayRolloverTime)) day.plusDays(1) else day
    return date.atTime(time).atZone(zone).toInstant()
}

/** FR-5.2: not in the future, not before dose N-1's time (dose 1: not before the start of day). */
fun validateBackdate(at: Instant, state: DayState, settings: Settings, now: Instant, zone: ZoneId): BackdateResult {
    if (at.isAfter(now)) return BackdateResult.Invalid(BackdateError.IN_FUTURE, now)
    val previous = state.doses.firstOrNull { it.index == state.nextDose - 1 }?.takenAt
    if (previous != null) {
        if (at.isBefore(previous)) return BackdateResult.Invalid(BackdateError.BEFORE_PREVIOUS_DOSE, previous)
    } else {
        val start = TimeMath.startOfDay(state.dayId, settings, zone)
        if (at.isBefore(start)) return BackdateResult.Invalid(BackdateError.BEFORE_START_OF_DAY, start)
    }
    return BackdateResult.Valid(at)
}

/** Picker time to a validated instant. */
fun resolveBackdate(time: LocalTime, state: DayState, settings: Settings, now: Instant, zone: ZoneId): BackdateResult =
    validateBackdate(backdatedInstant(time, settings, now, zone), state, settings, now, zone)
