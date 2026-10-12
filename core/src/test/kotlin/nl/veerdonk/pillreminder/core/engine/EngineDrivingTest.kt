package nl.veerdonk.pillreminder.core.engine

import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Outputs
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.ReminderSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test

/** FR-3.8: the alarm is held while Google Maps navigates (02 §4.2, the HELD_DRIVING rows). */
class EngineDrivingTest {

    @Test
    fun `REMINDER is held instead of ringing at escalateAt while navigating`() {
        // Dose 2: dose 1 from wake detection would wait for the fallback time first (FR-3.1a).
        val state = reminder(dose = 2, shownAt = t("07:25"))
        assertEquals(state.copy(phase = Phase.HELD_DRIVING), step(state, tickNavigating, t("07:40")))
    }

    @Test
    fun `REMINDER before escalateAt is unaffected by navigating`() {
        val state = reminder(dose = 2, shownAt = t("07:25"))
        assertEquals(state, step(state, tickNavigating, t("07:39")))
    }

    @Test
    fun `SNOOZED is held instead of ringing at snoozeEndAt while navigating`() {
        val state = snoozed(snoozedAt = t("07:42"), snoozesUsed = 1)
        assertEquals(state.copy(phase = Phase.HELD_DRIVING), step(state, tickNavigating, t("07:52")))
    }

    @Test
    fun `SNOOZED before snoozeEndAt is unaffected by navigating`() {
        val state = snoozed(snoozedAt = t("07:42"))
        assertEquals(state, step(state, tickNavigating, t("07:50")))
    }

    @Test
    fun `RINGING stops without using a snooze when navigation starts`() {
        val state = ringing(since = t("07:40"))
        assertEquals(
            state.copy(phase = Phase.HELD_DRIVING, ringingSince = null),
            step(state, tickNavigating, t("07:41")),
        )
    }

    @Test
    fun `RINGING past its ring end is held, not snoozed, while navigating`() {
        val state = ringing(since = t("07:40"), snoozesUsed = 0)
        val held = step(state, tickNavigating, t("07:50"))
        assertEquals(Phase.HELD_DRIVING, held.phase)
        assertEquals(0, held.snoozesUsed)
    }

    @Test
    fun `HELD_DRIVING stays held while navigating`() {
        assertEquals(heldDriving(), step(heldDriving(), tickNavigating, t("09:00")))
    }

    @Test
    fun `HELD_DRIVING rings once navigation ends, with snoozes untouched`() {
        val state = heldDriving(snoozesUsed = 2)
        assertEquals(
            state.copy(phase = Phase.RINGING, ringingSince = t("09:00")),
            step(state, tick, t("09:00")),
        )
    }

    @Test
    fun `navigating is ignored outside the alarm phases`() {
        assertEquals(Phase.DETECTING, step(beforeWindow(), tickNavigating, t("07:00")).phase)
        assertEquals(Phase.REMINDER, step(waiting(t("08:00")), tickNavigating, t("12:00")).phase)
        assertEquals(pendingSilent(), step(pendingSilent(), tickNavigating, t("09:00")))
    }

    @Test
    fun `TookDose from HELD_DRIVING moves on to the next dose`() {
        val next = step(heldDriving(), Event.TookDose(1, t("08:00"), DoseSource.NOTIFICATION), t("08:00"))
        assertEquals(Phase.WAITING, next.phase)
        assertEquals(2, next.nextDose)
    }

    @Test
    fun `Snooze is ignored in HELD_DRIVING`() {
        assertEquals(heldDriving(), step(heldDriving(), Event.Snooze(1), t("08:00")))
    }

    @Test
    fun `HELD_DRIVING shows the held subtitle, never rings and re-checks after the interval`() {
        val now = t("08:00")
        assertEquals(
            Outputs(
                ReminderSpec(
                    dayId = DAY,
                    dose = 1,
                    doseCount = DEFAULTS.dosesPerDay,
                    alarmStyle = false,
                    canSnooze = true,
                    snoozesLeft = DEFAULTS.maxSnoozes,
                    subtitle = ReminderSubtitle.HeldWhileDriving,
                ),
                null,
                now + Engine.NAVIGATION_RECHECK_INTERVAL,
            ),
            outputs(heldDriving(), now),
        )
    }
}
