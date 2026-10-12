package nl.veerdonk.pillreminder.core.engine

import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Outputs
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.ReminderSubtitle
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.model.Transition
import nl.veerdonk.pillreminder.core.model.WakeSource
import nl.veerdonk.pillreminder.core.time.TimeMath
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The daily state machine as a pure reducer (02-state-machine §4) plus its declarative
 * outputs (§5). No I/O and no clock: all time comes from the `now` and `zone` arguments.
 */
object Engine {

    /** Upper bound on Tick iterations per event (02 §4.2). */
    private const val MAX_TICK_ITERATIONS = 10

    private val OVERDUE_DELAY: Duration = Duration.ofSeconds(1)

    /** A fresh day: BEFORE_WINDOW, dose 1, no doses, every other field null/0. */
    fun fresh(dayId: LocalDate): DayState =
        DayState(dayId = dayId, phase = Phase.BEFORE_WINDOW, nextDose = 1, doses = emptyList())

    fun reduce(state: DayState, event: Event, settings: Settings, now: Instant, zone: ZoneId): Transition {
        // 4.1: the rollover check runs first, for every event.
        val today = TimeMath.dayIdOf(now, settings, zone)
        if (today != state.dayId) {
            val finished = finalise(state, settings)
            val fresh = fresh(today)
            val next = when (event) {
                // Stale: these targeted a dose of the old day.
                is Event.TookDose, is Event.SkipRest, is Event.Snooze -> fresh
                else -> process(fresh, event, settings, now, zone)
            }
            return Transition(next, finished)
        }
        return Transition(process(state, event, settings, now, zone), null)
    }

    fun outputs(state: DayState, settings: Settings, now: Instant, zone: ZoneId): Outputs {
        val doseCount = settings.dosesPerDay
        val canSnooze = canSnooze(state, settings)
        val snoozesLeft = (settings.maxSnoozes - state.snoozesUsed).coerceAtLeast(0)
        val rolloverAt = TimeMath.nextRolloverAt(state.dayId, settings, zone)

        fun reminder(alarmStyle: Boolean, subtitle: ReminderSubtitle?) = ReminderSpec(
            dayId = state.dayId,
            dose = state.nextDose,
            doseCount = doseCount,
            alarmStyle = alarmStyle,
            canSnooze = canSnooze,
            snoozesLeft = snoozesLeft,
            subtitle = subtitle,
        )

        var reminder: ReminderSpec? = null
        var ringing: RingSpec? = null
        // A missing base timestamp means a corrupt state: wake at once, so a Tick repairs it.
        val wakeAt: Instant = when (state.phase) {
            Phase.BEFORE_WINDOW -> TimeMath.earliestAt(state.dayId, settings, zone)
            Phase.DETECTING -> minOf(
                now + settings.detectionPollInterval,
                TimeMath.fallbackAt(state.dayId, settings, zone),
            )
            Phase.WAITING -> dueAt(state, settings) ?: now
            Phase.REMINDER -> {
                reminder = reminder(alarmStyle = false, subtitle = null)
                escalateAt(state, settings, zone) ?: now
            }
            Phase.RINGING -> {
                reminder = reminder(alarmStyle = true, subtitle = null)
                ringing = RingSpec(state.dayId, state.nextDose, doseCount, canSnooze, snoozesLeft)
                ringEndAt(state, settings) ?: now
            }
            Phase.SNOOZED -> {
                val end = snoozeEndAt(state, settings) ?: now
                reminder = reminder(alarmStyle = false, subtitle = ReminderSubtitle.Snoozed(end))
                end
            }
            Phase.PENDING_SILENT -> {
                reminder = reminder(alarmStyle = false, subtitle = ReminderSubtitle.AlarmStopped)
                rolloverAt
            }
            Phase.DONE -> rolloverAt
        }
        val capped = minOf(wakeAt, rolloverAt)
        val nextWakeAt = if (capped <= now) now + OVERDUE_DELAY else capped
        return Outputs(reminder = reminder, ringing = ringing, nextWakeAt = nextWakeAt)
    }

    private fun process(state: DayState, event: Event, s: Settings, now: Instant, zone: ZoneId): DayState =
        when (event) {
            is Event.Tick -> tick(state, event.wakeDetected, s, now, zone)
            is Event.TookDose -> tookDose(state, event, s, now, zone)
            is Event.SkipRest -> skipRest(state, event, s)
            is Event.Snooze -> snooze(state, event, s, now)
            Event.RemindNow -> remindNow(state, now)
            Event.ResumeDay -> resumeDay(state, now)
        }

    // --- 4.1 rollover ---

    private fun finalise(state: DayState, s: Settings): DayState {
        if (state.phase == Phase.DONE) return state
        val recorded = state.doses.map { it.index }.toSet()
        val missed = (state.nextDose..s.dosesPerDay)
            .filter { it !in recorded }
            .map { index ->
                val current = index == state.nextDose
                DoseRecord(
                    index = index,
                    status = DoseStatus.MISSED,
                    takenAt = null,
                    source = null,
                    snoozesUsed = if (current) state.snoozesUsed else 0,
                    firstRemindedAt = if (current) state.firstRemindedAt else null,
                )
            }
        // Every DONE state looks the same: per-dose fields cleared (assumption A10).
        return state.copy(phase = Phase.DONE, doses = (state.doses + missed).sortedBy { it.index }).clearPerDose()
    }

    // --- 4.2 Tick ---

    private fun tick(state: DayState, wakeDetected: Boolean, s: Settings, now: Instant, zone: ZoneId): DayState {
        // Detection only ran if the day was already DETECTING (02 §6, FR-1.6; assumption A10).
        val detected = wakeDetected && state.phase == Phase.DETECTING
        var current = state
        repeat(MAX_TICK_ITERATIONS) {
            val next = tickOnce(current, detected, s, now, zone)
            if (next.phase == current.phase) return next
            current = next
        }
        return current
    }

    private fun tickOnce(state: DayState, wakeDetected: Boolean, s: Settings, now: Instant, zone: ZoneId): DayState {
        // dosesPerDay lowered below nextDose: the day is complete, nothing recorded.
        if (state.phase != Phase.DONE && state.nextDose > s.dosesPerDay) {
            return state.copy(phase = Phase.DONE).clearPerDose()
        }
        return when (state.phase) {
            Phase.BEFORE_WINDOW ->
                if (now >= TimeMath.earliestAt(state.dayId, s, zone)) state.copy(phase = Phase.DETECTING) else state
            Phase.DETECTING -> when {
                wakeDetected -> wake(state, WakeSource.DETECTED, now)
                now >= TimeMath.fallbackAt(state.dayId, s, zone) -> wake(state, WakeSource.FALLBACK, now)
                else -> state
            }
            // No previous dose time to wait from (corrupt state): remind now.
            Phase.WAITING ->
                if (reached(dueAt(state, s), now)) {
                    state.copy(phase = Phase.REMINDER, reminderShownAt = now, firstRemindedAt = now)
                } else {
                    state
                }
            // A missing base timestamp (corrupt state) restarts that phase's timer from now (A10).
            Phase.REMINDER -> when {
                state.reminderShownAt == null -> state.copy(reminderShownAt = now)
                reached(escalateAt(state, s, zone), now) -> state.copy(phase = Phase.RINGING, ringingSince = now)
                else -> state
            }
            Phase.RINGING -> when {
                state.ringingSince == null -> state.copy(ringingSince = now)
                !reached(ringEndAt(state, s), now) -> state
                canSnooze(state, s) ->
                    state.copy(phase = Phase.SNOOZED, snoozedAt = now, snoozesUsed = state.snoozesUsed + 1)
                else -> state.copy(phase = Phase.PENDING_SILENT, ringingSince = null)
            }
            Phase.SNOOZED -> when {
                state.snoozedAt == null -> state.copy(snoozedAt = now)
                reached(snoozeEndAt(state, s), now) -> state.copy(phase = Phase.RINGING, ringingSince = now)
                else -> state
            }
            Phase.PENDING_SILENT, Phase.DONE -> state
        }
    }

    private fun wake(state: DayState, source: WakeSource, now: Instant): DayState = state.copy(
        phase = Phase.REMINDER,
        wakeAt = now,
        wakeSource = source,
        reminderShownAt = now,
        firstRemindedAt = now,
    )

    // --- 4.3 user events ---

    private fun tookDose(state: DayState, e: Event.TookDose, s: Settings, now: Instant, zone: ZoneId): DayState {
        val earliestAllowed = lastTakenAt(state) ?: TimeMath.startOfDay(state.dayId, s, zone)
        val valid = state.phase != Phase.DONE &&
            e.dose == state.nextDose &&
            e.at <= now &&
            e.at >= earliestAllowed
        if (!valid) return state

        val record = DoseRecord(
            index = e.dose,
            status = DoseStatus.TAKEN,
            takenAt = e.at,
            source = e.source,
            snoozesUsed = state.snoozesUsed,
            firstRemindedAt = state.firstRemindedAt,
        )
        val manualWake = e.dose == 1 && state.wakeAt == null
        val taken = state.copy(
            phase = if (e.dose == s.dosesPerDay) Phase.DONE else Phase.WAITING,
            nextDose = if (e.dose == s.dosesPerDay) state.nextDose else e.dose + 1,
            doses = state.doses + record,
            wakeAt = if (manualWake) e.at else state.wakeAt,
            wakeSource = if (manualWake) WakeSource.MANUAL else state.wakeSource,
        ).clearPerDose()
        // A backdated dose may already be due.
        return tick(taken, wakeDetected = false, s, now, zone)
    }

    private fun skipRest(state: DayState, e: Event.SkipRest, s: Settings): DayState {
        if (state.phase == Phase.DONE || e.dose != state.nextDose) return state
        val skipped = (e.dose..s.dosesPerDay).map { index ->
            val current = index == e.dose
            DoseRecord(
                index = index,
                status = DoseStatus.SKIPPED,
                takenAt = null,
                source = null,
                snoozesUsed = if (current) state.snoozesUsed else 0,
                firstRemindedAt = if (current) state.firstRemindedAt else null,
            )
        }
        return state.copy(phase = Phase.DONE, doses = state.doses + skipped).clearPerDose()
    }

    private fun snooze(state: DayState, e: Event.Snooze, s: Settings, now: Instant): DayState {
        if (state.phase != Phase.RINGING || e.dose != state.nextDose || !canSnooze(state, s)) return state
        return state.copy(
            phase = Phase.SNOOZED,
            snoozedAt = now,
            snoozesUsed = state.snoozesUsed + 1,
            ringingSince = null,
        )
    }

    private fun remindNow(state: DayState, now: Instant): DayState {
        if (state.phase !in REMIND_NOW_PHASES) return state
        val manualWake = state.nextDose == 1 && state.wakeAt == null
        return state.copy(
            phase = Phase.REMINDER,
            reminderShownAt = now,
            firstRemindedAt = state.firstRemindedAt ?: now,
            wakeAt = if (manualWake) now else state.wakeAt,
            wakeSource = if (manualWake) WakeSource.MANUAL else state.wakeSource,
        )
    }

    private fun resumeDay(state: DayState, now: Instant): DayState {
        if (state.phase != Phase.DONE) return state
        val (skipped, kept) = state.doses.partition { it.status == DoseStatus.SKIPPED }
        if (skipped.isEmpty()) return state
        val resumed = skipped.minOf { it.index }
        // FR-5.5: "as if Remind me now had been tapped" (assumption A8 in 01).
        val manualWake = resumed == 1 && state.wakeAt == null
        return state.copy(
            phase = Phase.REMINDER,
            nextDose = resumed,
            doses = kept,
            wakeAt = if (manualWake) now else state.wakeAt,
            wakeSource = if (manualWake) WakeSource.MANUAL else state.wakeSource,
        ).clearPerDose().copy(reminderShownAt = now, firstRemindedAt = now)
    }

    private val REMIND_NOW_PHASES = setOf(Phase.BEFORE_WINDOW, Phase.DETECTING, Phase.WAITING, Phase.PENDING_SILENT)

    // --- derived values (02 §2) ---

    private fun DayState.clearPerDose(): DayState = copy(
        reminderShownAt = null,
        firstRemindedAt = null,
        ringingSince = null,
        snoozedAt = null,
        snoozesUsed = 0,
    )

    private fun lastTakenAt(state: DayState): Instant? =
        state.doses.firstOrNull { it.index == state.nextDose - 1 }?.takenAt

    private fun dueAt(state: DayState, s: Settings): Instant? = lastTakenAt(state)?.plus(s.doseInterval)

    /** Dose 1 never rings before the day's fallback time, however early wake was detected (FR-3.1a). */
    private fun escalateAt(state: DayState, s: Settings, zone: ZoneId): Instant? {
        val afterDelay = state.reminderShownAt?.plus(s.escalationDelay) ?: return null
        if (state.nextDose != 1) return afterDelay
        return maxOf(afterDelay, TimeMath.fallbackAt(state.dayId, s, zone))
    }

    private fun ringEndAt(state: DayState, s: Settings): Instant? = state.ringingSince?.plus(s.alarmRingDuration)

    private fun snoozeEndAt(state: DayState, s: Settings): Instant? = state.snoozedAt?.plus(s.snoozeDuration)

    private fun canSnooze(state: DayState, s: Settings): Boolean = state.snoozesUsed < s.maxSnoozes

    /** A deadline is reached at or after it; a missing one (no previous dose) counts as reached. */
    private fun reached(deadline: Instant?, now: Instant): Boolean = deadline == null || now >= deadline
}
