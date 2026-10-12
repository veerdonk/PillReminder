package nl.veerdonk.pillreminder.core.engine

import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.model.WakeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Table 4.3: user events, with a negative test for every "Valid when" condition. */
class EngineUserEventTest {

    private fun took(dose: Int, at: Instant, source: DoseSource = DoseSource.NOTIFICATION) =
        Event.TookDose(dose, at, source)

    /** Asserts that [event] is ignored: the state comes back identical and no day is finished. */
    private fun assertIgnored(state: DayState, event: Event, now: Instant, s: Settings = DEFAULTS) {
        val tr = reduce(state, event, now, s)
        assertNull(tr.finishedDay)
        assertEquals(state, tr.state)
    }

    private fun skipped(index: Int, snoozes: Int = 0, firstRemindedAt: Instant? = null) =
        DoseRecord(index, DoseStatus.SKIPPED, null, null, snoozes, firstRemindedAt)

    // ===== TookDose =====

    @Test
    fun `TookDose from REMINDER records the dose and waits for the next one`() {
        val state = reminder(dose = 1, shownAt = t("07:25"))
        val next = step(state, took(1, t("07:30")), t("07:30"))
        assertEquals(
            state.copy(
                phase = Phase.WAITING,
                nextDose = 2,
                doses = listOf(DoseRecord(1, DoseStatus.TAKEN, t("07:30"), DoseSource.NOTIFICATION, 0, t("07:25"))),
                reminderShownAt = null,
                firstRemindedAt = null,
            ),
            next,
        )
        assertEquals(WakeSource.DETECTED, next.wakeSource) // already awake: unchanged
    }

    @Test
    fun `TookDose from RINGING, SNOOZED and PENDING_SILENT goes to WAITING with per-dose fields cleared`() {
        for (state in listOf(ringing(snoozesUsed = 1), snoozed(snoozesUsed = 2), pendingSilent())) {
            val next = step(state, took(1, t("09:00"), DoseSource.ALARM), t("09:00"))
            assertEquals(Phase.WAITING, next.phase)
            assertEquals(2, next.nextDose)
            assertEquals(
                listOf(DoseRecord(1, DoseStatus.TAKEN, t("09:00"), DoseSource.ALARM, state.snoozesUsed, state.firstRemindedAt)),
                next.doses,
            )
            assertNull(next.reminderShownAt)
            assertNull(next.firstRemindedAt)
            assertNull(next.ringingSince)
            assertNull(next.snoozedAt)
            assertEquals(0, next.snoozesUsed)
        }
    }

    @Test
    fun `TookDose 1 before wake sets a MANUAL wake at the taken time`() {
        for (state in listOf(beforeWindow(), detecting())) {
            val next = step(state, took(1, t("06:30"), DoseSource.APP_BACKDATED), t("06:45"))
            assertEquals(Phase.WAITING, next.phase)
            assertEquals(2, next.nextDose)
            assertEquals(t("06:30"), next.wakeAt)
            assertEquals(WakeSource.MANUAL, next.wakeSource)
        }
    }

    @Test
    fun `TookDose of the last dose ends the day`() {
        val state = reminder(dose = 3, shownAt = t("15:40"))
        val next = step(state, took(3, t("15:41")), t("15:41"))
        assertEquals(Phase.DONE, next.phase)
        assertEquals(listOf(1, 2, 3), next.doses.map { it.index })
        assertEquals(DoseStatus.TAKEN, next.doses.last().status)
    }

    @Test
    fun `TookDose 1 with one dose per day ends the day`() {
        val next = step(detecting(), took(1, t("07:10")), t("07:10"), DEFAULTS.copy(dosesPerDay = 1))
        assertEquals(Phase.DONE, next.phase)
        assertEquals(WakeSource.MANUAL, next.wakeSource)
    }

    @Test
    fun `backdated TookDose already due goes straight to REMINDER`() {
        // Dose 1 at 07:00, recorded at 11:00: dose 2 was due at 10:50.
        val now = t("11:00")
        val next = step(detecting(), took(1, t("07:00"), DoseSource.APP_BACKDATED), now)
        assertEquals(Phase.REMINDER, next.phase)
        assertEquals(2, next.nextDose)
        assertEquals(now, next.reminderShownAt)
        assertEquals(now, next.firstRemindedAt)
    }

    @Test
    fun `TookDose for a dose beyond a lowered dosesPerDay ends the day`() {
        val next = step(reminder(dose = 3), took(3, t("15:00")), t("15:00"), DEFAULTS.copy(dosesPerDay = 2))
        assertEquals(Phase.DONE, next.phase)
    }

    @Test
    fun `TookDose accepts the boundary times`() {
        // at == now
        assertEquals(Phase.WAITING, step(reminder(), took(1, t("07:30")), t("07:30")).phase)
        // at == startOfDay for dose 1
        assertEquals(Phase.WAITING, step(beforeWindow(), took(1, t("03:00")), t("04:00")).phase)
        // at == lastTakenAt for later doses
        val r2 = reminder(dose = 2, shownAt = t("11:30"))
        val last = r2.doses.single().takenAt!!
        assertEquals(Phase.WAITING, step(r2, took(2, last), t("11:31")).phase)
    }

    @Test
    fun `TookDose is ignored in DONE`() {
        assertIgnored(done(), took(3, t("16:00")), t("16:00"))
    }

    @Test
    fun `TookDose is ignored for a dose other than nextDose`() {
        val state = reminder(dose = 2, shownAt = t("11:30"))
        assertIgnored(state, took(1, t("11:31")), t("11:31"))
        assertIgnored(state, took(3, t("11:31")), t("11:31"))
    }

    @Test
    fun `TookDose is ignored for a time in the future`() {
        assertIgnored(reminder(), took(1, t("07:31")), t("07:30"))
    }

    @Test
    fun `TookDose is ignored for a time before the previous dose`() {
        val state = reminder(dose = 2, shownAt = t("11:30"))
        val last = state.doses.single().takenAt!!
        assertIgnored(state, took(2, last.minusSeconds(1)), t("11:31"))
    }

    @Test
    fun `TookDose 1 is ignored for a time before the start of the day`() {
        assertIgnored(beforeWindow(), took(1, t("02:59:59")), t("04:00"))
    }

    // ===== SkipRest =====

    @Test
    fun `SkipRest on dose 1 skips every dose and ends the day`() {
        val state = ringing(since = t("07:40"), snoozesUsed = 1)
        val next = step(state, Event.SkipRest(1), t("07:41"))
        assertEquals(
            doneFrom(state, listOf(skipped(1, snoozes = 1, firstRemindedAt = state.firstRemindedAt), skipped(2), skipped(3))),
            next,
        )
    }

    @Test
    fun `SkipRest on a later dose skips only the remaining doses`() {
        val next = step(waiting(takenAt = t("08:00")), Event.SkipRest(2), t("09:00"))
        assertEquals(Phase.DONE, next.phase)
        assertEquals(listOf(taken(1, t("08:00")), skipped(2), skipped(3)), next.doses)
    }

    @Test
    fun `SkipRest is valid in every phase other than DONE`() {
        for (phase in Phase.entries - Phase.DONE) {
            val state = stateIn(phase)
            val next = step(state, Event.SkipRest(state.nextDose), t("08:01"))
            assertEquals("phase $phase", Phase.DONE, next.phase)
        }
    }

    @Test
    fun `SkipRest is ignored in DONE`() {
        assertIgnored(done(), Event.SkipRest(3), t("16:00"))
    }

    @Test
    fun `SkipRest is ignored for a dose other than nextDose`() {
        assertIgnored(reminder(dose = 2, shownAt = t("11:30")), Event.SkipRest(1), t("11:31"))
        assertIgnored(reminder(dose = 2, shownAt = t("11:30")), Event.SkipRest(3), t("11:31"))
    }

    // ===== Snooze =====

    @Test
    fun `Snooze while RINGING snoozes`() {
        val state = ringing(since = t("07:40"), snoozesUsed = 1)
        assertEquals(
            state.copy(phase = Phase.SNOOZED, snoozedAt = t("07:41"), snoozesUsed = 2, ringingSince = null),
            step(state, Event.Snooze(1), t("07:41")),
        )
    }

    @Test
    fun `Snooze is ignored outside RINGING`() {
        for (phase in Phase.entries - Phase.RINGING) {
            val state = stateIn(phase)
            assertIgnored(state, Event.Snooze(state.nextDose), t("08:01"))
        }
    }

    @Test
    fun `Snooze is ignored for a dose other than nextDose`() {
        assertIgnored(ringing(dose = 2, since = t("12:00")), Event.Snooze(1), t("12:01"))
    }

    @Test
    fun `Snooze is ignored when no snoozes are left`() {
        assertIgnored(ringing(since = t("07:40"), snoozesUsed = 3), Event.Snooze(1), t("07:41"))
        assertIgnored(ringing(since = t("07:40")), Event.Snooze(1), t("07:41"), DEFAULTS.copy(maxSnoozes = 0))
    }

    // ===== RemindNow =====

    @Test
    fun `RemindNow before wake shows the dose 1 reminder and sets a MANUAL wake`() {
        for (state in listOf(beforeWindow(), detecting())) {
            val now = t("06:50")
            assertEquals(
                state.copy(
                    phase = Phase.REMINDER,
                    wakeAt = now,
                    wakeSource = WakeSource.MANUAL,
                    reminderShownAt = now,
                    firstRemindedAt = now,
                ),
                step(state, Event.RemindNow, now),
            )
        }
    }

    @Test
    fun `RemindNow while WAITING shows the next reminder and keeps the wake`() {
        val state = waiting(takenAt = t("08:00"))
        val now = t("09:00")
        assertEquals(
            state.copy(phase = Phase.REMINDER, reminderShownAt = now, firstRemindedAt = now),
            step(state, Event.RemindNow, now),
        )
    }

    @Test
    fun `RemindNow from PENDING_SILENT keeps the snooze count and firstRemindedAt`() {
        val state = pendingSilent()
        val now = t("09:00")
        assertEquals(
            state.copy(phase = Phase.REMINDER, reminderShownAt = now),
            step(state, Event.RemindNow, now),
        )
    }

    @Test
    fun `RemindNow is ignored in REMINDER, RINGING, SNOOZED, HELD_DRIVING and DONE`() {
        for (phase in listOf(Phase.REMINDER, Phase.RINGING, Phase.SNOOZED, Phase.HELD_DRIVING, Phase.DONE)) {
            assertIgnored(stateIn(phase), Event.RemindNow, t("08:01"))
        }
    }

    // ===== ResumeDay =====

    @Test
    fun `ResumeDay after Not today re-opens dose 1`() {
        val state = step(reminder(dose = 1, shownAt = t("07:25"), snoozesUsed = 0), Event.SkipRest(1), t("07:30"))
        val now = t("08:00")
        assertEquals(
            state.copy(
                phase = Phase.REMINDER,
                nextDose = 1,
                doses = emptyList(),
                reminderShownAt = now,
                firstRemindedAt = now,
                ringingSince = null,
                snoozedAt = null,
                snoozesUsed = 0,
            ),
            step(state, Event.ResumeDay, now),
        )
    }

    @Test
    fun `ResumeDay re-opens the lowest skipped dose and keeps taken doses`() {
        val state = step(ringing(dose = 2, since = t("12:00"), snoozesUsed = 2), Event.SkipRest(2), t("12:01"))
        val next = step(state, Event.ResumeDay, t("13:00"))
        assertEquals(Phase.REMINDER, next.phase)
        assertEquals(2, next.nextDose)
        assertEquals(state.doses.filter { it.status == DoseStatus.TAKEN }, next.doses)
        assertEquals(t("13:00"), next.reminderShownAt)
        assertNull(next.ringingSince)
        assertEquals(0, next.snoozesUsed)
        assertEquals(state.wakeAt, next.wakeAt)
    }

    @Test
    fun `ResumeDay after Not today before wake sets a MANUAL wake`() {
        val state = step(beforeWindow(), Event.SkipRest(1), t("05:00"))
        val next = step(state, Event.ResumeDay, t("06:00"))
        assertEquals(t("06:00"), next.wakeAt)
        assertEquals(WakeSource.MANUAL, next.wakeSource)
    }

    @Test
    fun `ResumeDay is ignored outside DONE`() {
        for (phase in Phase.entries - Phase.DONE) {
            assertIgnored(stateIn(phase), Event.ResumeDay, t("08:01"))
        }
    }

    @Test
    fun `ResumeDay is ignored when no dose was skipped`() {
        assertIgnored(done(), Event.ResumeDay, t("16:00"))
    }
}
