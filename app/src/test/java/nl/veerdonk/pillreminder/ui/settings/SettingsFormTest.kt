package nl.veerdonk.pillreminder.ui.settings

import nl.veerdonk.pillreminder.core.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class SettingsFormTest {
    private val defaults = SettingsForm.from(Settings())

    @Test fun defaultsRoundTrip() {
        val r = defaults.parse()
        assertEquals(Settings(), r.settings)
        assertTrue(r.errors.isEmpty())
    }

    @Test fun holdFirstAlarmSwitchRoundTrips() {
        assertEquals(true, defaults.holdFirstAlarm)
        assertEquals(false, defaults.copy(holdFirstAlarm = false).parse().settings!!.holdFirstAlarmUntilFallback)
    }

    @Test fun statusNotificationSwitchRoundTrips() {
        assertEquals(false, defaults.showStatusNotification)
        assertEquals(true, defaults.copy(showStatusNotification = true).parse().settings!!.showStatusNotification)
    }

    @Test fun intervalIsHoursPlusMinutes() {
        assertEquals("3", defaults.doseIntervalHours)
        assertEquals("50", defaults.doseIntervalMinutes)
        val r = defaults.copy(doseIntervalHours = "4", doseIntervalMinutes = "5").parse()
        assertEquals(Duration.ofMinutes(245), r.settings?.doseInterval)
    }

    @Test fun nonNumberIsFieldError() {
        val r = defaults.copy(minUnlocks = "").parse()
        assertNull(r.settings)
        assertEquals(FieldError.NotANumber, r.errors[SettingsField.MIN_UNLOCKS])
        assertEquals(FieldError.NotANumber, defaults.copy(snoozeMinutes = "1.5").parse().errors[SettingsField.SNOOZE_DURATION])
        assertEquals(FieldError.NotANumber, defaults.copy(snoozeMinutes = "-1").parse().errors[SettingsField.SNOOZE_DURATION])
        assertEquals(FieldError.NotANumber, defaults.copy(doseIntervalMinutes = "x").parse().errors[SettingsField.DOSE_INTERVAL])
        assertEquals(FieldError.NotANumber, defaults.copy(maxSnoozes = "99999999999").parse().errors[SettingsField.MAX_SNOOZES])
    }

    @Test fun outOfRangeIsInvalidOnItsField() {
        val r = defaults.copy(dosesPerDay = "7", escalationMinutes = "0").parse()
        assertNull(r.settings)
        assertTrue(r.errors[SettingsField.DOSES_PER_DAY] is FieldError.Invalid)
        assertTrue(r.errors[SettingsField.ESCALATION_DELAY] is FieldError.Invalid)
        assertNull(r.errors[SettingsField.SNOOZE_DURATION])
    }

    @Test fun intervalOverTwelveHoursIsInvalid() {
        val r = defaults.copy(doseIntervalHours = "12", doseIntervalMinutes = "1").parse()
        assertTrue(r.errors[SettingsField.DOSE_INTERVAL] is FieldError.Invalid)
        assertTrue(defaults.copy(doseIntervalHours = "0", doseIntervalMinutes = "90").parse().isValid)
    }

    @Test fun fallbackMustBeAfterEarliest() {
        val r = defaults.copy(weekdayFallback = LocalTime.of(6, 0)).parse()
        assertTrue(r.errors[SettingsField.WEEKDAY_FALLBACK] is FieldError.Invalid)
        val w = defaults.copy(weekendFallback = LocalTime.of(8, 30)).parse()
        assertTrue(w.errors[SettingsField.WEEKEND_FALLBACK] is FieldError.Invalid)
    }

    @Test fun rolloverMustBeBeforeEarliestTimes() {
        val r = defaults.copy(rollover = LocalTime.of(7, 30)).parse()
        assertNull(r.settings)
        assertTrue(r.errors[SettingsField.ROLLOVER] is FieldError.Invalid)
    }

    @Test fun zeroMinAppUsageAndMaxSnoozesAreValid() {
        assertTrue(defaults.copy(minAppUsageMinutes = "0", maxSnoozes = "0").parse().isValid)
    }

    @Test fun alarmSoundIsKept() {
        assertEquals("content://x/1", defaults.copy(alarmSoundUri = "content://x/1").parse().settings?.alarmSoundUri)
    }

    @Test fun pharmacyPhoneIsTrimmedAndEmptyMeansNone() {
        assertEquals("", defaults.pharmacyPhone)
        assertNull(defaults.copy(pharmacyPhone = "   ").parse().settings!!.pharmacyPhone)
        assertEquals("+31 20 123 4567", defaults.copy(pharmacyPhone = " +31 20 123 4567 ").parse().settings?.pharmacyPhone)
        assertEquals("020 123", SettingsForm.from(Settings(pharmacyPhone = "020 123")).pharmacyPhone)
    }

    @Test fun invalidPharmacyPhoneIsErrorOnItsField() {
        val r = defaults.copy(pharmacyPhone = "pharmacy").parse()
        assertNull(r.settings)
        assertTrue(r.errors[SettingsField.PHARMACY_PHONE] is FieldError.Invalid)
    }

    @Test fun parseWholeNumberRules() {
        assertEquals(12, parseWholeNumber(" 12 "))
        assertNull(parseWholeNumber("1234567"))
        assertNull(parseWholeNumber("+1"))
    }
}
