package nl.veerdonk.pillreminder.core.engine

import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.WakeSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** Table 4.2: Tick, repeated until the phase stops changing. */
class EngineTickTest {

    // --- BEFORE_WINDOW ---

    @Test
    fun `BEFORE_WINDOW stays before earliestAt`() {
        assertEquals(beforeWindow(), step(beforeWindow(), tick, t("06:59:59")))
    }

    @Test
    fun `BEFORE_WINDOW goes to DETECTING at earliestAt`() {
        assertEquals(beforeWindow().copy(phase = Phase.DETECTING), step(beforeWindow(), tick, t("07:00")))
    }

    @Test
    fun `BEFORE_WINDOW uses the weekend earliest time on Saturday`() {
        val saturday = java.time.LocalDate.parse("2026-10-17")
        val state = beforeWindow(saturday)
        assertEquals(Phase.BEFORE_WINDOW, step(state, tick, at("2026-10-17T08:29")).phase)
        assertEquals(Phase.DETECTING, step(state, tick, at("2026-10-17T08:30")).phase)
    }

    // --- DETECTING ---

    @Test
    fun `DETECTING stays without wake and before fallback`() {
        assertEquals(detecting(), step(detecting(), tick, t("10:29:59")))
    }

    @Test
    fun `DETECTING goes to REMINDER when wake is detected`() {
        val now = t("07:25")
        assertEquals(
            detecting().copy(
                phase = Phase.REMINDER,
                wakeAt = now,
                wakeSource = WakeSource.DETECTED,
                reminderShownAt = now,
                firstRemindedAt = now,
            ),
            step(detecting(), tickAwake, now),
        )
    }

    @Test
    fun `DETECTING goes to REMINDER at fallbackAt`() {
        val now = t("10:30")
        assertEquals(
            detecting().copy(
                phase = Phase.REMINDER,
                wakeAt = now,
                wakeSource = WakeSource.FALLBACK,
                reminderShownAt = now,
                firstRemindedAt = now,
            ),
            step(detecting(), tick, now),
        )
    }

    @Test
    fun `DETECTING prefers DETECTED when wake and fallback coincide`() {
        assertEquals(WakeSource.DETECTED, step(detecting(), tickAwake, t("10:30")).wakeSource)
    }

    @Test
    fun `wakeDetected is ignored in the Tick that enters DETECTING`() {
        assertEquals(beforeWindow().copy(phase = Phase.DETECTING), step(beforeWindow(), tickAwake, t("07:30")))
    }

    @Test
    fun `BEFORE_WINDOW passes through DETECTING to REMINDER in one Tick after fallback`() {
        val next = step(beforeWindow(), tick, t("10:45"))
        assertEquals(Phase.REMINDER, next.phase)
        assertEquals(WakeSource.FALLBACK, next.wakeSource)
        assertEquals(t("10:45"), next.reminderShownAt)
    }

    // --- WAITING ---

    @Test
    fun `WAITING stays before dueAt`() {
        val state = waiting(takenAt = t("08:00"))
        assertEquals(state, step(state, tick, t("11:49:59")))
    }

    @Test
    fun `WAITING goes to REMINDER at dueAt`() {
        val state = waiting(takenAt = t("08:00"))
        val now = t("11:50")
        assertEquals(
            state.copy(phase = Phase.REMINDER, reminderShownAt = now, firstRemindedAt = now),
            step(state, tick, now),
        )
    }

    @Test
    fun `WAITING ignores wakeDetected`() {
        val state = waiting(takenAt = t("08:00"))
        assertEquals(state, step(state, tickAwake, t("09:00")))
    }

    // --- REMINDER ---

    @Test
    fun `REMINDER stays before escalateAt`() {
        val state = reminder(shownAt = t("07:25"))
        assertEquals(state, step(state, tick, t("07:39:59")))
    }

    @Test
    fun `REMINDER goes to RINGING at escalateAt`() {
        val state = reminder(shownAt = t("07:25"))
        assertEquals(state.copy(phase = Phase.RINGING, ringingSince = t("07:40")), step(state, tick, t("07:40")))
    }

    @Test
    fun `REMINDER uses the current escalationDelay`() {
        val state = reminder(shownAt = t("07:25"))
        val longer = DEFAULTS.copy(escalationDelay = Duration.ofMinutes(30))
        assertEquals(state, step(state, tick, t("07:40"), longer))
    }

    @Test
    fun `missed deadlines are not replayed`() {
        // Phone boots 3 h after the reminder was shown: straight to RINGING, based on now.
        val state = reminder(shownAt = t("07:25"))
        val next = step(state, tick, t("10:25"))
        assertEquals(state.copy(phase = Phase.RINGING, ringingSince = t("10:25")), next)
    }

    // --- RINGING ---

    @Test
    fun `RINGING stays before ringEndAt`() {
        val state = ringing(since = t("07:40"))
        assertEquals(state, step(state, tick, t("07:41:59")))
    }

    @Test
    fun `RINGING times out to SNOOZED when a snooze is left`() {
        val state = ringing(since = t("07:40"), snoozesUsed = 2)
        assertEquals(
            state.copy(phase = Phase.SNOOZED, snoozedAt = t("07:42"), snoozesUsed = 3),
            step(state, tick, t("07:42")),
        )
    }

    @Test
    fun `RINGING times out to PENDING_SILENT when no snooze is left`() {
        val state = ringing(since = t("07:40"), snoozesUsed = 3)
        assertEquals(
            state.copy(phase = Phase.PENDING_SILENT, ringingSince = null),
            step(state, tick, t("07:42")),
        )
    }

    @Test
    fun `RINGING with maxSnoozes 0 times out to PENDING_SILENT`() {
        val state = ringing(since = t("07:40"))
        val next = step(state, tick, t("07:42"), DEFAULTS.copy(maxSnoozes = 0))
        assertEquals(Phase.PENDING_SILENT, next.phase)
    }

    // --- SNOOZED ---

    @Test
    fun `SNOOZED stays before snoozeEndAt`() {
        val state = snoozed(snoozedAt = t("07:42"))
        assertEquals(state, step(state, tick, t("07:51:59")))
    }

    @Test
    fun `SNOOZED rings again at snoozeEndAt`() {
        val state = snoozed(snoozedAt = t("07:42"))
        assertEquals(state.copy(phase = Phase.RINGING, ringingSince = t("07:52")), step(state, tick, t("07:52")))
    }

    // --- corrupt states: a missing base timestamp restarts the timer from now ---

    @Test
    fun `REMINDER without reminderShownAt restarts escalation from now`() {
        val state = reminder().copy(reminderShownAt = null)
        val repaired = step(state, tick, t("08:00"))
        assertEquals(state.copy(reminderShownAt = t("08:00")), repaired)
        assertEquals(Phase.REMINDER, step(repaired, tick, t("08:14")).phase)
        assertEquals(Phase.RINGING, step(repaired, tick, t("08:15")).phase)
    }

    @Test
    fun `RINGING without ringingSince rings for a full ring duration from now`() {
        val state = ringing().copy(ringingSince = null)
        assertEquals(state.copy(ringingSince = t("08:00")), step(state, tick, t("08:00")))
    }

    @Test
    fun `SNOOZED without snoozedAt snoozes for a full snooze duration from now`() {
        val state = snoozed().copy(snoozedAt = null)
        assertEquals(state.copy(snoozedAt = t("08:00")), step(state, tick, t("08:00")))
    }

    @Test
    fun `WAITING without a previous dose time shows the reminder now`() {
        val state = waiting().copy(doses = emptyList())
        assertEquals(
            state.copy(phase = Phase.REMINDER, reminderShownAt = t("08:00"), firstRemindedAt = t("08:00")),
            step(state, tick, t("08:00")),
        )
    }

    // --- PENDING_SILENT, DONE ---

    @Test
    fun `PENDING_SILENT is unchanged by Tick`() {
        val state = pendingSilent()
        assertEquals(state, step(state, tick, at("${NEXT_DAY}T02:59")))
        assertEquals(state, step(state, tickAwake, at("${NEXT_DAY}T02:59")))
    }

    @Test
    fun `DONE is unchanged by Tick`() {
        val state = done()
        assertEquals(state, step(state, tick, at("${NEXT_DAY}T02:59")))
    }

    // --- dosesPerDay lowered below nextDose ---

    @Test
    fun `dosesPerDay lowered below nextDose ends the day in every phase other than DONE`() {
        val lowered = DEFAULTS.copy(dosesPerDay = 1)
        for (phase in Phase.entries - Phase.DONE) {
            val state = stateIn(phase).copy(nextDose = 2)
            assertEquals("phase $phase", doneFrom(state), step(state, tick, t("08:01"), lowered))
        }
    }

    @Test
    fun `dosesPerDay lowered but not below nextDose changes nothing`() {
        val state = reminder(dose = 2, shownAt = t("11:30"))
        assertEquals(state, step(state, tick, t("11:31"), DEFAULTS.copy(dosesPerDay = 2)))
    }

    @Test
    fun `dosesPerDay lowered leaves DONE unchanged`() {
        val state = done()
        assertEquals(state, step(state, tick, t("16:00"), DEFAULTS.copy(dosesPerDay = 1)))
    }
}
