package nl.veerdonk.pillreminder.core.engine

import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.WakeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Table 4.1: the rollover check that runs first for every event. */
class EngineRolloverTest {
    private val rolloverAt = at("${NEXT_DAY}T03:00")

    private fun missed(index: Int, snoozes: Int = 0, firstRemindedAt: java.time.Instant? = null) =
        DoseRecord(index, DoseStatus.MISSED, null, null, snoozes, firstRemindedAt)

    @Test
    fun `fresh day has dose 1, no doses and every other field null or zero`() {
        val fresh = Engine.fresh(DAY)
        assertEquals(DAY, fresh.dayId)
        assertEquals(Phase.BEFORE_WINDOW, fresh.phase)
        assertEquals(1, fresh.nextDose)
        assertEquals(emptyList<DoseRecord>(), fresh.doses)
        assertNull(fresh.wakeAt)
        assertNull(fresh.wakeSource)
        assertNull(fresh.reminderShownAt)
        assertNull(fresh.firstRemindedAt)
        assertNull(fresh.ringingSince)
        assertNull(fresh.snoozedAt)
        assertEquals(0, fresh.snoozesUsed)
    }

    @Test
    fun `no rollover before the rollover time`() {
        val tr = reduce(Engine.fresh(DAY), tick, at("${NEXT_DAY}T02:59:59"))
        assertNull(tr.finishedDay)
        assertEquals(DAY, tr.state.dayId)
    }

    @Test
    fun `rollover while RINGING records the pending doses as MISSED and gives a fresh day`() {
        val state = ringing(dose = 1, since = at("${DAY}T23:59"), snoozesUsed = 2)
        val tr = reduce(state, tick, rolloverAt)

        val finished = tr.finishedDay!!
        assertEquals(DAY, finished.dayId)
        assertEquals(
            listOf(missed(1, snoozes = 2, firstRemindedAt = state.firstRemindedAt), missed(2), missed(3)),
            finished.doses,
        )
        assertEquals(Engine.fresh(NEXT_DAY), tr.state)
    }

    @Test
    fun `rollover keeps taken doses and marks only the remaining ones as MISSED`() {
        val state = waiting(takenAt = t("08:00"))
        val tr = reduce(state, tick, rolloverAt)
        assertEquals(listOf(taken(1, t("08:00")), missed(2), missed(3)), tr.finishedDay!!.doses)
    }

    @Test
    fun `rollover in every phase other than DONE records MISSED doses`() {
        for (phase in Phase.entries - Phase.DONE) {
            val state = stateIn(phase)
            val tr = reduce(state, tick, rolloverAt)
            val missedIndices = tr.finishedDay!!.doses.filter { it.status == DoseStatus.MISSED }.map { it.index }
            assertEquals("phase $phase", (state.nextDose..3).toList(), missedIndices)
            assertEquals("phase $phase", Engine.fresh(NEXT_DAY), tr.state)
        }
    }

    @Test
    fun `rollover from DONE finishes the day unchanged`() {
        val state = done()
        val tr = reduce(state, tick, rolloverAt)
        assertEquals(state, tr.finishedDay)
        assertEquals(Engine.fresh(NEXT_DAY), tr.state)
    }

    @Test
    fun `rollover after a skip keeps the SKIPPED records`() {
        val state = step(reminder(dose = 1), Event.SkipRest(1), t("07:30"))
        val tr = reduce(state, tick, rolloverAt)
        assertEquals(state, tr.finishedDay)
    }

    @Test
    fun `rollover with dosesPerDay lowered below nextDose records nothing`() {
        val state = reminder(dose = 3)
        val tr = reduce(state, tick, rolloverAt, DEFAULTS.copy(dosesPerDay = 2))
        assertEquals(state.doses, tr.finishedDay!!.doses)
    }

    @Test
    fun `rollover after several days jumps to the current day`() {
        val tr = reduce(reminder(), tick, at("2026-10-16T12:00"))
        assertEquals(DAY, tr.finishedDay!!.dayId)
        assertEquals(java.time.LocalDate.parse("2026-10-16"), tr.state.dayId)
    }

    @Test
    fun `TookDose after a rollover is ignored`() {
        val tr = reduce(reminder(), Event.TookDose(1, rolloverAt, DoseSource.NOTIFICATION), rolloverAt)
        assertEquals(Engine.fresh(NEXT_DAY), tr.state)
        assertEquals(DAY, tr.finishedDay!!.dayId)
    }

    @Test
    fun `SkipRest after a rollover is ignored`() {
        val tr = reduce(reminder(), Event.SkipRest(1), rolloverAt)
        assertEquals(Engine.fresh(NEXT_DAY), tr.state)
        assertEquals(DAY, tr.finishedDay!!.dayId)
    }

    @Test
    fun `Snooze after a rollover is ignored`() {
        val tr = reduce(ringing(), Event.Snooze(1), rolloverAt)
        assertEquals(Engine.fresh(NEXT_DAY), tr.state)
        assertEquals(DAY, tr.finishedDay!!.dayId)
    }

    @Test
    fun `Tick after a rollover is processed against the fresh day`() {
        // Phone was off over night and boots at 07:30 on the next day.
        val tr = reduce(reminder(), tick, at("${NEXT_DAY}T07:30"))
        assertEquals(Engine.fresh(NEXT_DAY).copy(phase = Phase.DETECTING), tr.state)

        val late = reduce(reminder(), tick, at("${NEXT_DAY}T10:30"))
        assertEquals(Phase.REMINDER, late.state.phase)
        assertEquals(WakeSource.FALLBACK, late.state.wakeSource)
    }

    @Test
    fun `RemindNow after a rollover is processed against the fresh day`() {
        val now = at("${NEXT_DAY}T05:00")
        val tr = reduce(done(), Event.RemindNow, now)
        assertEquals(DAY, tr.finishedDay!!.dayId)
        assertEquals(
            Engine.fresh(NEXT_DAY).copy(
                phase = Phase.REMINDER,
                wakeAt = now,
                wakeSource = WakeSource.MANUAL,
                reminderShownAt = now,
                firstRemindedAt = now,
            ),
            tr.state,
        )
    }

    @Test
    fun `ResumeDay after a rollover is processed against the fresh day, where it is invalid`() {
        val skipped = step(reminder(), Event.SkipRest(1), t("07:30"))
        val tr = reduce(skipped, Event.ResumeDay, rolloverAt)
        assertEquals(skipped, tr.finishedDay)
        assertEquals(Engine.fresh(NEXT_DAY), tr.state)
    }
}
