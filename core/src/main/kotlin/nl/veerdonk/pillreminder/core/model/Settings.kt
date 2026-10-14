@file:UseSerializers(LocalTimeSerializer::class, DurationSerializer::class)

package nl.veerdonk.pillreminder.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import nl.veerdonk.pillreminder.core.serial.DurationSerializer
import nl.veerdonk.pillreminder.core.serial.LocalTimeSerializer
import java.time.Duration
import java.time.LocalTime

/** All user-editable settings, with the defaults and ranges from 01-requirements FR-8. */
@Serializable
data class Settings(
    val weekdayEarliestTime: LocalTime = LocalTime.of(7, 0),
    val weekdayFallbackTime: LocalTime = LocalTime.of(10, 30),
    val weekendEarliestTime: LocalTime = LocalTime.of(8, 30),
    val weekendFallbackTime: LocalTime = LocalTime.of(11, 30),
    val minUnlocks: Int = 2,
    val minAppUsage: Duration = Duration.ofMinutes(3),
    val detectionWindow: Duration = Duration.ofMinutes(20),
    val detectionPollInterval: Duration = Duration.ofMinutes(5),
    val dosesPerDay: Int = 3,
    val doseInterval: Duration = Duration.ofMinutes(230),
    val escalationDelay: Duration = Duration.ofMinutes(15),
    val snoozeDuration: Duration = Duration.ofMinutes(10),
    val maxSnoozes: Int = 3,
    val alarmRingDuration: Duration = Duration.ofMinutes(2),
    val holdFirstAlarmUntilFallback: Boolean = true, // FR-3.1a
    val dayRolloverTime: LocalTime = LocalTime.of(3, 0),
    val alarmSoundUri: String? = null, // null = system default alarm
    val showStatusNotification: Boolean = false, // FR-2.4: persistent notification between doses
    val pharmacyPhone: String? = null, // FR-10.4: null = no "Call pharmacy" button
    val bottleSize: Int = 90, // FR-10.5: pills added by "Pills refilled"
) {
    /** Human-readable validation errors; an empty list means the settings are valid. */
    fun validate(): List<String> = buildList {
        intRange("minUnlocks", minUnlocks, 1, 10)
        minutesRange("minAppUsage", minAppUsage, 0, 30)
        minutesRange("detectionWindow", detectionWindow, 5, 120)
        minutesRange("detectionPollInterval", detectionPollInterval, 1, 15)
        intRange("dosesPerDay", dosesPerDay, 1, 6)
        minutesRange("doseInterval", doseInterval, 1, 12 * 60)
        minutesRange("escalationDelay", escalationDelay, 1, 120)
        minutesRange("snoozeDuration", snoozeDuration, 1, 60)
        intRange("maxSnoozes", maxSnoozes, 0, 10)
        minutesRange("alarmRingDuration", alarmRingDuration, 1, 10)
        intRange("bottleSize", bottleSize, 1, MAX_BOTTLE_SIZE)

        if (!weekdayFallbackTime.isAfter(weekdayEarliestTime)) {
            add("weekdayFallbackTime must be later than weekdayEarliestTime")
        }
        if (!weekendFallbackTime.isAfter(weekendEarliestTime)) {
            add("weekendFallbackTime must be later than weekendEarliestTime")
        }
        pharmacyPhone?.let { phone ->
            if (!isValidPhone(phone)) {
                add("pharmacyPhone must be up to $MAX_PHONE_LENGTH digits, spaces and + - ( ) characters")
            }
        }
        if (dayRolloverTime.isAfter(MAX_ROLLOVER)) {
            add("dayRolloverTime must be between 00:00 and 06:00")
        }
        if (!dayRolloverTime.isBefore(weekdayEarliestTime)) {
            add("dayRolloverTime must be earlier than weekdayEarliestTime")
        }
        if (!dayRolloverTime.isBefore(weekendEarliestTime)) {
            add("dayRolloverTime must be earlier than weekendEarliestTime")
        }
    }

    private fun MutableList<String>.intRange(name: String, value: Int, min: Int, max: Int) {
        if (value !in min..max) add("$name must be between $min and $max")
    }

    private fun MutableList<String>.minutesRange(name: String, value: Duration, min: Long, max: Long) {
        if (value < Duration.ofMinutes(min) || value > Duration.ofMinutes(max)) {
            add("$name must be between $min and $max minutes")
        } else if (value.toSeconds() % 60 != 0L || value.toNanosPart() != 0) {
            add("$name must be a whole number of minutes")
        }
    }

    companion object {
        private val MAX_ROLLOVER: LocalTime = LocalTime.of(6, 0)
        const val MAX_PHONE_LENGTH = 30
        const val MAX_BOTTLE_SIZE = 999
        private const val PHONE_CHARS = "0123456789+-() "

        /** A trimmed, dialable number: at least one digit, only [PHONE_CHARS], at most [MAX_PHONE_LENGTH] long. */
        private fun isValidPhone(phone: String): Boolean =
            phone == phone.trim() &&
                phone.length <= MAX_PHONE_LENGTH &&
                phone.any { it in '0'..'9' } &&
                phone.all { it in PHONE_CHARS }
    }
}
