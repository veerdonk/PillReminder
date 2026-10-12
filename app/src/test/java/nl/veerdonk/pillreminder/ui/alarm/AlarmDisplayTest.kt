package nl.veerdonk.pillreminder.ui.alarm

import nl.veerdonk.pillreminder.core.model.RingSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AlarmDisplayTest {
    private val day = LocalDate.parse("2026-10-13")
    private val ringing = RingSpec(day, 2, 3, canSnooze = true, snoozesLeft = 1)

    @Test
    fun `shows the engine's spec while ringing for the same dose`() {
        assertEquals(Display.Show(ringing), displayFor(day, 2, ringing))
    }

    @Test
    fun `finishes when nothing rings any more`() {
        assertEquals(Display.Finish, displayFor(day, 2, null))
    }

    @Test
    fun `finishes when another dose or day rings`() {
        assertEquals(Display.Finish, displayFor(day, 1, ringing))
        assertEquals(Display.Finish, displayFor(day.minusDays(1), 2, ringing))
    }

    @Test
    fun `test spec shows all buttons and follows the settings`() {
        val spec = testSpec(dosesPerDay = 4, maxSnoozes = 3)
        assertEquals(1, spec.dose)
        assertEquals(4, spec.doseCount)
        assertEquals(3, spec.snoozesLeft)
        assertEquals(true, spec.canSnooze)
    }
}
