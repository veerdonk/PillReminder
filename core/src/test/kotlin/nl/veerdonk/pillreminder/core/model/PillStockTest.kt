package nl.veerdonk.pillreminder.core.model

import nl.veerdonk.pillreminder.core.engine.NEXT_DAY
import nl.veerdonk.pillreminder.core.engine.at
import nl.veerdonk.pillreminder.core.engine.beforeWindow
import nl.veerdonk.pillreminder.core.engine.done
import nl.veerdonk.pillreminder.core.engine.reduce
import nl.veerdonk.pillreminder.core.engine.reminder
import nl.veerdonk.pillreminder.core.engine.step
import nl.veerdonk.pillreminder.core.engine.t
import nl.veerdonk.pillreminder.core.engine.tick
import nl.veerdonk.pillreminder.core.engine.waiting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PillStockTest {

    @Test
    fun `a taken dose counts one pill`() {
        val before = reminder(dose = 2, shownAt = t("12:00"))
        val after = step(before, Event.TookDose(2, t("12:05"), DoseSource.NOTIFICATION), t("12:05"))
        assertEquals(1, PillStock.pillsTaken(before, after))
    }

    @Test
    fun `a backdated dose counts one pill`() {
        val before = beforeWindow()
        val after = step(before, Event.TookDose(1, t("06:30"), DoseSource.APP_BACKDATED), t("07:00"))
        assertEquals(1, PillStock.pillsTaken(before, after))
    }

    @Test
    fun `skipping, ticking and resuming count no pills`() {
        val waiting = waiting()
        val skipped = step(waiting, Event.SkipRest(2), t("09:00"))
        assertEquals(0, PillStock.pillsTaken(waiting, skipped))
        val resumed = step(skipped, Event.ResumeDay, t("09:10"))
        assertEquals(0, PillStock.pillsTaken(skipped, resumed))
        val reminded = step(waiting, tick, t("12:00"))
        assertEquals(0, PillStock.pillsTaken(waiting, reminded))
    }

    @Test
    fun `a rollover counts no pills`() {
        val before = done()
        val after = reduce(before, tick, at("${NEXT_DAY}T03:00")).state
        assertEquals(0, PillStock.pillsTaken(before, after))
    }

    @Test
    fun `the count never goes below zero`() {
        assertEquals(89, PillStock.afterTaking(90, 1))
        assertEquals(0, PillStock.afterTaking(0, 1))
    }

    @Test
    fun `low below five days of pills`() {
        // 3 a day: 15 pills is exactly five days, 14 is four whole days.
        assertNull(PillStock.lowStock(15, 3))
        assertEquals(LowStock(pillsLeft = 14, daysLeft = 4), PillStock.lowStock(14, 3))
        assertEquals(LowStock(pillsLeft = 0, daysLeft = 0), PillStock.lowStock(0, 3))
        // The threshold follows dosesPerDay.
        assertNull(PillStock.lowStock(10, 2))
        assertEquals(LowStock(pillsLeft = 9, daysLeft = 4), PillStock.lowStock(9, 2))
    }

    @Test
    fun `no warning when the count isn't tracked`() {
        assertNull(PillStock.lowStock(null, 3))
    }

    @Test
    fun `a refill adds the bottle to the pills left`() {
        assertEquals(102, PillStock.afterRefill(12, 90))
        assertEquals(90, PillStock.afterRefill(0, 90))
    }

    @Test
    fun `a refill while not counting starts the count`() {
        assertEquals(90, PillStock.afterRefill(null, 90))
    }

    @Test
    fun `a refill is capped at the largest count`() {
        assertEquals(PillStock.MAX_PILLS, PillStock.afterRefill(PillStock.MAX_PILLS - 10, 90))
    }

    @Test
    fun `call the pharmacy below three days of pills`() {
        // At 3 a day: 9 pills is exactly three days, 8 is two.
        assertFalse(PillStock.shouldCallPharmacy(PillStock.lowStock(9, 3)!!))
        assertTrue(PillStock.shouldCallPharmacy(PillStock.lowStock(8, 3)!!))
        assertTrue(PillStock.shouldCallPharmacy(PillStock.lowStock(0, 3)!!))
    }
}
