package nl.veerdonk.pillreminder.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class SettingsTest {
    private val d = Settings()

    private fun min(m: Long): Duration = Duration.ofMinutes(m)

    private fun assertSingleError(s: Settings, fragment: String) {
        val errors = s.validate()
        assertEquals("errors: $errors", 1, errors.size)
        assertTrue("errors: $errors", errors[0].contains(fragment))
    }

    @Test
    fun `defaults match the requirements and are valid`() {
        assertEquals(LocalTime.of(7, 0), d.weekdayEarliestTime)
        assertEquals(LocalTime.of(10, 30), d.weekdayFallbackTime)
        assertEquals(LocalTime.of(8, 30), d.weekendEarliestTime)
        assertEquals(LocalTime.of(11, 30), d.weekendFallbackTime)
        assertEquals(2, d.minUnlocks)
        assertEquals(min(3), d.minAppUsage)
        assertEquals(min(20), d.detectionWindow)
        assertEquals(min(5), d.detectionPollInterval)
        assertEquals(3, d.dosesPerDay)
        assertEquals(Duration.ofHours(3).plusMinutes(50), d.doseInterval)
        assertEquals(min(15), d.escalationDelay)
        assertEquals(min(10), d.snoozeDuration)
        assertEquals(3, d.maxSnoozes)
        assertEquals(min(2), d.alarmRingDuration)
        assertEquals(LocalTime.of(3, 0), d.dayRolloverTime)
        assertEquals(null, d.alarmSoundUri)
        assertEquals(null, d.pharmacyPhone)
        assertEquals(emptyList<String>(), d.validate())
    }

    @Test
    fun `all lower bounds are valid`() {
        val s = d.copy(
            minUnlocks = 1, minAppUsage = min(0), detectionWindow = min(5), detectionPollInterval = min(1),
            dosesPerDay = 1, doseInterval = min(1), escalationDelay = min(1), snoozeDuration = min(1),
            maxSnoozes = 0, alarmRingDuration = min(1), dayRolloverTime = LocalTime.MIDNIGHT,
        )
        assertEquals(emptyList<String>(), s.validate())
    }

    @Test
    fun `all upper bounds are valid`() {
        val s = d.copy(
            minUnlocks = 10, minAppUsage = min(30), detectionWindow = min(120), detectionPollInterval = min(15),
            dosesPerDay = 6, doseInterval = Duration.ofHours(12), escalationDelay = min(120), snoozeDuration = min(60),
            maxSnoozes = 10, alarmRingDuration = min(10), dayRolloverTime = LocalTime.of(6, 0),
        )
        assertEquals(emptyList<String>(), s.validate())
    }

    @Test
    fun `int ranges`() {
        assertSingleError(d.copy(minUnlocks = 0), "minUnlocks")
        assertSingleError(d.copy(minUnlocks = 11), "minUnlocks")
        assertSingleError(d.copy(dosesPerDay = 0), "dosesPerDay")
        assertSingleError(d.copy(dosesPerDay = 7), "dosesPerDay")
        assertSingleError(d.copy(maxSnoozes = -1), "maxSnoozes")
        assertSingleError(d.copy(maxSnoozes = 11), "maxSnoozes")
    }

    @Test
    fun `duration ranges`() {
        assertSingleError(d.copy(minAppUsage = min(-1)), "minAppUsage")
        assertSingleError(d.copy(minAppUsage = min(31)), "minAppUsage")
        assertSingleError(d.copy(detectionWindow = min(4)), "detectionWindow")
        assertSingleError(d.copy(detectionWindow = min(121)), "detectionWindow")
        assertSingleError(d.copy(detectionPollInterval = min(0)), "detectionPollInterval")
        assertSingleError(d.copy(detectionPollInterval = min(16)), "detectionPollInterval")
        assertSingleError(d.copy(doseInterval = min(0)), "doseInterval")
        assertSingleError(d.copy(doseInterval = Duration.ofHours(12).plusMinutes(1)), "doseInterval")
        assertSingleError(d.copy(escalationDelay = min(0)), "escalationDelay")
        assertSingleError(d.copy(escalationDelay = min(121)), "escalationDelay")
        assertSingleError(d.copy(snoozeDuration = min(0)), "snoozeDuration")
        assertSingleError(d.copy(snoozeDuration = min(61)), "snoozeDuration")
        assertSingleError(d.copy(alarmRingDuration = min(0)), "alarmRingDuration")
        assertSingleError(d.copy(alarmRingDuration = min(11)), "alarmRingDuration")
    }

    @Test
    fun `durations must be whole minutes`() {
        assertSingleError(d.copy(snoozeDuration = min(5).plusSeconds(30)), "whole number of minutes")
    }

    @Test
    fun `fallback must be later than earliest`() {
        assertSingleError(d.copy(weekdayFallbackTime = LocalTime.of(7, 0)), "weekdayFallbackTime")
        assertSingleError(d.copy(weekdayFallbackTime = LocalTime.of(6, 59)), "weekdayFallbackTime")
        assertSingleError(d.copy(weekendFallbackTime = LocalTime.of(8, 30)), "weekendFallbackTime")
        assertSingleError(d.copy(weekendFallbackTime = LocalTime.of(8, 0)), "weekendFallbackTime")
    }

    @Test
    fun `rollover must be at most 0600`() {
        val s = d.copy(dayRolloverTime = LocalTime.of(6, 1), weekdayEarliestTime = LocalTime.of(8, 0))
        assertSingleError(s, "between 00:00 and 06:00")
    }

    @Test
    fun `rollover must be earlier than both earliest times`() {
        assertSingleError(
            d.copy(dayRolloverTime = LocalTime.of(5, 0), weekdayEarliestTime = LocalTime.of(5, 0)),
            "earlier than weekdayEarliestTime",
        )
        assertSingleError(
            d.copy(dayRolloverTime = LocalTime.of(5, 0), weekendEarliestTime = LocalTime.of(4, 30)),
            "earlier than weekendEarliestTime",
        )
    }

    @Test
    fun `multiple errors are all reported`() {
        val s = d.copy(minUnlocks = 0, dosesPerDay = 9, weekendFallbackTime = LocalTime.of(8, 0))
        assertEquals(3, s.validate().size)
    }

    @Test
    fun `pharmacy phone accepts usual number formats`() {
        listOf("0201234567", "+31 20 123 4567", "(020) 123-45-67", "112").forEach {
            assertEquals(it, emptyList<String>(), d.copy(pharmacyPhone = it).validate())
        }
    }

    @Test
    fun `pharmacy phone rejects letters, no digits, padding and overlong numbers`() {
        listOf("call me", "+-()", "", " 020", "0".repeat(31), "020#1").forEach {
            assertSingleError(d.copy(pharmacyPhone = it), "pharmacyPhone")
        }
        assertEquals(emptyList<String>(), d.copy(pharmacyPhone = "0".repeat(30)).validate())
    }
}
