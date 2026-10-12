package nl.veerdonk.pillreminder.core.engine

import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Outputs
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.model.Transition
import nl.veerdonk.pillreminder.core.model.WakeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Shared fixtures for the engine tests: Europe/Amsterdam, default settings, Tuesday 13 Oct 2026. */
internal val AMS: ZoneId = ZoneId.of("Europe/Amsterdam")
internal val DEFAULTS = Settings()
internal val DAY: LocalDate = LocalDate.parse("2026-10-13") // a Tuesday
internal val NEXT_DAY: LocalDate = DAY.plusDays(1)

/** `at("2026-10-13T07:25")` is that local time in Amsterdam. */
internal fun at(local: String): Instant = LocalDateTime.parse(local).atZone(AMS).toInstant()

/** Shorthand for a time on [DAY]: `t("07:25")`. */
internal fun t(hhmm: String): Instant = at("${DAY}T$hhmm")

internal fun reduce(state: DayState, event: Event, now: Instant, s: Settings = DEFAULTS): Transition =
    Engine.reduce(state, event, s, now, AMS)

/** Reduce on the same day; asserts no rollover happened and returns the new state. */
internal fun step(state: DayState, event: Event, now: Instant, s: Settings = DEFAULTS): DayState {
    val tr = reduce(state, event, now, s)
    check(tr.finishedDay == null) { "unexpected rollover" }
    return tr.state
}

internal fun outputs(state: DayState, now: Instant, s: Settings = DEFAULTS): Outputs =
    Engine.outputs(state, s, now, AMS)

internal val tick = Event.Tick(wakeDetected = false)
internal val tickAwake = Event.Tick(wakeDetected = true)

internal fun taken(index: Int, at: Instant, source: DoseSource = DoseSource.NOTIFICATION) =
    DoseRecord(index, DoseStatus.TAKEN, at, source, snoozesUsed = 0, firstRemindedAt = null)

// --- states in each phase (dose 1 woke at 07:25 unless noted) ---

internal fun beforeWindow(day: LocalDate = DAY): DayState = Engine.fresh(day)

internal fun detecting(day: LocalDate = DAY): DayState = Engine.fresh(day).copy(phase = Phase.DETECTING)

private fun woken(): DayState =
    Engine.fresh(DAY).copy(wakeAt = t("07:25"), wakeSource = WakeSource.DETECTED)

/** Dose 1 taken at [takenAt], waiting for dose 2. */
internal fun waiting(takenAt: Instant = t("08:00")): DayState =
    woken().copy(phase = Phase.WAITING, nextDose = 2, doses = listOf(taken(1, takenAt)))

/** Reminder for [dose] shown at [shownAt]; earlier doses taken. */
internal fun reminder(dose: Int = 1, shownAt: Instant = t("07:25"), snoozesUsed: Int = 0): DayState =
    woken().copy(
        phase = Phase.REMINDER,
        nextDose = dose,
        doses = (1 until dose).map { taken(it, t("07:30").plusSeconds(it * 3600L)) },
        reminderShownAt = shownAt,
        firstRemindedAt = shownAt,
        snoozesUsed = snoozesUsed,
    )

internal fun ringing(dose: Int = 1, since: Instant = t("07:40"), snoozesUsed: Int = 0): DayState =
    reminder(dose, since.minus(DEFAULTS.escalationDelay), snoozesUsed)
        .copy(phase = Phase.RINGING, ringingSince = since)

internal fun snoozed(dose: Int = 1, snoozedAt: Instant = t("07:42"), snoozesUsed: Int = 1): DayState =
    ringing(dose, snoozedAt.minus(DEFAULTS.alarmRingDuration), snoozesUsed)
        .copy(phase = Phase.SNOOZED, snoozedAt = snoozedAt, ringingSince = null)

internal fun pendingSilent(dose: Int = 1): DayState =
    ringing(dose, t("08:20"), snoozesUsed = DEFAULTS.maxSnoozes)
        .copy(phase = Phase.PENDING_SILENT, ringingSince = null)

/** All three doses taken. */
internal fun done(): DayState = woken().copy(
    phase = Phase.DONE,
    nextDose = 3,
    doses = listOf(taken(1, t("07:30")), taken(2, t("11:30")), taken(3, t("15:30"))),
)

/** [state] as DONE with the per-dose fields cleared, the shape every DONE state has (A10). */
internal fun doneFrom(state: DayState, doses: List<DoseRecord> = state.doses): DayState = state.copy(
    phase = Phase.DONE,
    doses = doses,
    reminderShownAt = null,
    firstRemindedAt = null,
    ringingSince = null,
    snoozedAt = null,
    snoozesUsed = 0,
)

/** One state per phase, keyed by phase, for table-driven tests. */
internal fun stateIn(phase: Phase): DayState = when (phase) {
    Phase.BEFORE_WINDOW -> beforeWindow()
    Phase.DETECTING -> detecting()
    Phase.WAITING -> waiting()
    Phase.REMINDER -> reminder()
    Phase.RINGING -> ringing()
    Phase.SNOOZED -> snoozed()
    Phase.PENDING_SILENT -> pendingSilent()
    Phase.DONE -> done()
}
