package nl.veerdonk.pillreminder.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStatusTest {

    private fun allExcept(vararg items: SetupItem) = SetupStatus(SetupItem.entries.toSet() - items.toSet())

    @Test
    fun `only notifications and exact alarms are hard requirements`() {
        assertEquals(
            listOf(SetupItem.NOTIFICATIONS, SetupItem.EXACT_ALARMS),
            SetupItem.entries.filter { it.hard },
        )
    }

    @Test
    fun `everything ok has nothing missing, no banner and no setup once seen`() {
        val status = SetupStatus.ALL_OK
        assertTrue(status.missing.isEmpty())
        assertFalse(status.showBanner)
        assertFalse(status.isSetupNeeded(setupSeen = true))
    }

    @Test
    fun `missing items are listed in checklist order`() {
        val status = allExcept(SetupItem.ONEPLUS_EXTRAS, SetupItem.NOTIFICATIONS, SetupItem.USAGE_ACCESS)
        assertEquals(
            listOf(SetupItem.NOTIFICATIONS, SetupItem.USAGE_ACCESS, SetupItem.ONEPLUS_EXTRAS),
            status.missing,
        )
        assertEquals(listOf(SetupItem.NOTIFICATIONS), status.missingHard)
    }

    @Test
    fun `first launch needs setup even when everything is granted`() {
        assertTrue(SetupStatus.ALL_OK.isSetupNeeded(setupSeen = false))
    }

    @Test
    fun `a missing hard requirement needs setup after the first launch`() {
        assertTrue(allExcept(SetupItem.NOTIFICATIONS).isSetupNeeded(setupSeen = true))
        assertTrue(allExcept(SetupItem.EXACT_ALARMS).isSetupNeeded(setupSeen = true))
    }

    @Test
    fun `a missing soft item alone does not force setup after the first launch`() {
        val soft = SetupItem.entries.filter { !it.hard }
        for (item in soft) {
            assertFalse("$item", allExcept(item).isSetupNeeded(setupSeen = true))
        }
    }

    @Test
    fun `banner shows for any missing item, hard or soft`() {
        for (item in SetupItem.entries.filter { !it.optional }) {
            assertTrue("$item", allExcept(item).showBanner)
        }
    }

    @Test
    fun `an optional item is never missing and raises no banner`() {
        val status = allExcept(SetupItem.MAPS_NAVIGATION)
        assertFalse(status.isOk(SetupItem.MAPS_NAVIGATION))
        assertTrue(status.missing.isEmpty())
        assertFalse(status.showBanner)
        assertFalse(status.isSetupNeeded(setupSeen = true))
    }

    @Test
    fun `no item ok means everything is missing`() {
        val status = SetupStatus(emptySet())
        assertEquals(SetupItem.entries.filter { !it.optional }, status.missing)
        assertEquals(listOf(SetupItem.NOTIFICATIONS, SetupItem.EXACT_ALARMS), status.missingHard)
    }
}
