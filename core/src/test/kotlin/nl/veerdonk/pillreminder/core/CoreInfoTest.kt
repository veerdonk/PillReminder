package nl.veerdonk.pillreminder.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreInfoTest {
    @Test
    fun appNameIsSet() {
        assertEquals("PillReminder", CoreInfo.APP_NAME)
    }
}
