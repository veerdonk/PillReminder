package nl.veerdonk.pillreminder.platform.receivers

import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Plain JVM test of the notification button -> event mapping (no Android runtime needed). */
class ActionMappingTest {

    private val now = Instant.parse("2026-10-13T07:05:00Z")
    private val day = LocalDate.parse("2026-10-13")

    private fun parse(
        action: String? = ActionReceiver.ACTION_TOOK,
        dayId: String? = "2026-10-13",
        dose: Int? = 2,
        source: String? = "NOTIFICATION",
    ) = parseAction(action, dayId, dose, source, now)

    @Test
    fun `took on the reminder records a notification dose at the tap time`() {
        assertEquals(NotificationAction(Event.TookDose(2, now, DoseSource.NOTIFICATION), day), parse())
    }

    @Test
    fun `took on the alarm records an alarm dose`() {
        assertEquals(NotificationAction(Event.TookDose(1, now, DoseSource.ALARM), day), parse(dose = 1, source = "ALARM"))
    }

    @Test
    fun `skip maps to SkipRest`() {
        assertEquals(NotificationAction(Event.SkipRest(1), day), parse(ActionReceiver.ACTION_SKIP, dose = 1))
        assertEquals(NotificationAction(Event.SkipRest(3), day), parse(ActionReceiver.ACTION_SKIP, dose = 3, source = "ALARM"))
    }

    @Test
    fun `snooze maps to Snooze`() {
        assertEquals(NotificationAction(Event.Snooze(2), day), parse(ActionReceiver.ACTION_SNOOZE, source = "ALARM"))
    }

    @Test
    fun `the dayId is passed through as given`() {
        assertEquals(LocalDate.parse("2026-10-11"), parse(dayId = "2026-10-11")?.dayId)
    }

    @Test
    fun `unknown or missing action is ignored`() {
        assertNull(parse(action = null))
        assertNull(parse(action = "nl.veerdonk.pillreminder.action.WAKEUP"))
    }

    @Test
    fun `missing or unparsable dayId is ignored`() {
        assertNull(parse(dayId = null))
        assertNull(parse(dayId = ""))
        assertNull(parse(dayId = "13-10-2026"))
        assertNull(parse(dayId = "2026-02-30"))
    }

    @Test
    fun `missing or invalid dose is ignored`() {
        assertNull(parse(dose = null))
        assertNull(parse(dose = 0))
        assertNull(parse(ActionReceiver.ACTION_SNOOZE, dose = -1))
    }

    @Test
    fun `took with a missing or invalid source is ignored`() {
        assertNull(parse(source = null))
        assertNull(parse(source = "APP_NOW"))
        assertNull(parse(source = "alarm"))
    }
}
