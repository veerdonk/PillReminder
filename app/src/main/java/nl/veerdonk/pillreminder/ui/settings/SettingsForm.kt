package nl.veerdonk.pillreminder.ui.settings

import nl.veerdonk.pillreminder.core.model.Settings
import java.time.Duration
import java.time.LocalTime

/** The editable fields of the Settings screen, for inline errors. */
enum class SettingsField {
    WEEKDAY_EARLIEST,
    WEEKDAY_FALLBACK,
    WEEKEND_EARLIEST,
    WEEKEND_FALLBACK,
    MIN_UNLOCKS,
    MIN_APP_USAGE,
    DETECTION_WINDOW,
    POLL_INTERVAL,
    DOSES_PER_DAY,
    DOSE_INTERVAL,
    ESCALATION_DELAY,
    SNOOZE_DURATION,
    MAX_SNOOZES,
    RING_DURATION,
    ROLLOVER,
}

sealed interface FieldError {
    /** The text is empty or not a whole number. */
    data object NotANumber : FieldError

    /** A message from `Settings.validate()` (English, from :core). */
    data class Invalid(val message: String) : FieldError
}

/**
 * The form state: numbers are kept as the text the user typed, so half-typed input survives.
 * Times can't be malformed because they come from pickers.
 */
data class SettingsForm(
    val weekdayEarliest: LocalTime,
    val weekdayFallback: LocalTime,
    val weekendEarliest: LocalTime,
    val weekendFallback: LocalTime,
    val minUnlocks: String,
    val minAppUsageMinutes: String,
    val detectionWindowMinutes: String,
    val pollIntervalMinutes: String,
    val dosesPerDay: String,
    val doseIntervalHours: String,
    val doseIntervalMinutes: String,
    val escalationMinutes: String,
    val snoozeMinutes: String,
    val maxSnoozes: String,
    val ringMinutes: String,
    val rollover: LocalTime,
    val alarmSoundUri: String?,
) {
    /** Parses and validates the form. */
    fun parse(): FormResult {
        val errors = linkedMapOf<SettingsField, FieldError>()

        fun int(field: SettingsField, text: String): Int? =
            parseWholeNumber(text).also { if (it == null) errors[field] = FieldError.NotANumber }

        val minUnlocks = int(SettingsField.MIN_UNLOCKS, minUnlocks)
        val minApp = int(SettingsField.MIN_APP_USAGE, minAppUsageMinutes)
        val window = int(SettingsField.DETECTION_WINDOW, detectionWindowMinutes)
        val poll = int(SettingsField.POLL_INTERVAL, pollIntervalMinutes)
        val doses = int(SettingsField.DOSES_PER_DAY, dosesPerDay)
        val hours = parseWholeNumber(doseIntervalHours)
        val minutes = parseWholeNumber(doseIntervalMinutes)
        if (hours == null || minutes == null) errors[SettingsField.DOSE_INTERVAL] = FieldError.NotANumber
        val escalation = int(SettingsField.ESCALATION_DELAY, escalationMinutes)
        val snooze = int(SettingsField.SNOOZE_DURATION, snoozeMinutes)
        val maxSnoozes = int(SettingsField.MAX_SNOOZES, maxSnoozes)
        val ring = int(SettingsField.RING_DURATION, ringMinutes)

        if (errors.isNotEmpty()) return FormResult(null, errors)

        // Non-null: every parse above succeeded. Minutes are widened to Long, so no overflow.
        val settings = Settings(
            weekdayEarliestTime = weekdayEarliest,
            weekdayFallbackTime = weekdayFallback,
            weekendEarliestTime = weekendEarliest,
            weekendFallbackTime = weekendFallback,
            minUnlocks = minUnlocks!!,
            minAppUsage = Duration.ofMinutes(minApp!!.toLong()),
            detectionWindow = Duration.ofMinutes(window!!.toLong()),
            detectionPollInterval = Duration.ofMinutes(poll!!.toLong()),
            dosesPerDay = doses!!,
            doseInterval = Duration.ofMinutes(hours!!.toLong() * 60 + minutes!!.toLong()),
            escalationDelay = Duration.ofMinutes(escalation!!.toLong()),
            snoozeDuration = Duration.ofMinutes(snooze!!.toLong()),
            maxSnoozes = maxSnoozes!!,
            alarmRingDuration = Duration.ofMinutes(ring!!.toLong()),
            dayRolloverTime = rollover,
            alarmSoundUri = alarmSoundUri,
        )
        val problems = settings.validate()
        for (message in problems) {
            for (field in fieldsOf(message)) errors.putIfAbsent(field, FieldError.Invalid(message))
        }
        return if (problems.isEmpty()) FormResult(settings, errors) else FormResult(null, errors)
    }

    companion object {
        fun from(s: Settings): SettingsForm {
            val intervalMinutes = s.doseInterval.toMinutes()
            return SettingsForm(
                weekdayEarliest = s.weekdayEarliestTime,
                weekdayFallback = s.weekdayFallbackTime,
                weekendEarliest = s.weekendEarliestTime,
                weekendFallback = s.weekendFallbackTime,
                minUnlocks = s.minUnlocks.toString(),
                minAppUsageMinutes = s.minAppUsage.toMinutes().toString(),
                detectionWindowMinutes = s.detectionWindow.toMinutes().toString(),
                pollIntervalMinutes = s.detectionPollInterval.toMinutes().toString(),
                dosesPerDay = s.dosesPerDay.toString(),
                doseIntervalHours = (intervalMinutes / 60).toString(),
                doseIntervalMinutes = (intervalMinutes % 60).toString(),
                escalationMinutes = s.escalationDelay.toMinutes().toString(),
                snoozeMinutes = s.snoozeDuration.toMinutes().toString(),
                maxSnoozes = s.maxSnoozes.toString(),
                ringMinutes = s.alarmRingDuration.toMinutes().toString(),
                rollover = s.dayRolloverTime,
                alarmSoundUri = s.alarmSoundUri,
            )
        }
    }
}

/** [settings] is non-null exactly when the form is valid (Save is enabled). */
data class FormResult(val settings: Settings?, val errors: Map<SettingsField, FieldError>) {
    val isValid: Boolean get() = settings != null
}

/** A non-negative whole number of at most 6 digits (so nothing overflows), else null. */
fun parseWholeNumber(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty() || t.length > 6 || !t.all { it in '0'..'9' }) return null
    return t.toInt()
}

/** Maps a `Settings.validate()` message to the field(s) it is about, by its leading key name. */
internal fun fieldsOf(message: String): List<SettingsField> {
    // Relation errors ("dayRolloverTime must be earlier than weekdayEarliestTime") belong to the
    // first-named field only, which is the one the user should change.
    return when (message.substringBefore(' ')) {
        "minUnlocks" -> listOf(SettingsField.MIN_UNLOCKS)
        "minAppUsage" -> listOf(SettingsField.MIN_APP_USAGE)
        "detectionWindow" -> listOf(SettingsField.DETECTION_WINDOW)
        "detectionPollInterval" -> listOf(SettingsField.POLL_INTERVAL)
        "dosesPerDay" -> listOf(SettingsField.DOSES_PER_DAY)
        "doseInterval" -> listOf(SettingsField.DOSE_INTERVAL)
        "escalationDelay" -> listOf(SettingsField.ESCALATION_DELAY)
        "snoozeDuration" -> listOf(SettingsField.SNOOZE_DURATION)
        "maxSnoozes" -> listOf(SettingsField.MAX_SNOOZES)
        "alarmRingDuration" -> listOf(SettingsField.RING_DURATION)
        "weekdayFallbackTime" -> listOf(SettingsField.WEEKDAY_FALLBACK)
        "weekendFallbackTime" -> listOf(SettingsField.WEEKEND_FALLBACK)
        "dayRolloverTime" -> listOf(SettingsField.ROLLOVER)
        else -> emptyList()
    }
}
