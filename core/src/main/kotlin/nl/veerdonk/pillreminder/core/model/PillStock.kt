package nl.veerdonk.pillreminder.core.model

import kotlinx.serialization.Serializable

/**
 * "Running low" line on the reminder (FR-10.3): [pillsLeft] pills, enough for [daysLeft] whole
 * days at the current `dosesPerDay`.
 */
@Serializable
data class LowStock(val pillsLeft: Int, val daysLeft: Int)

/**
 * Pure rules for the pill count (01 FR-10). The count itself lives outside [DayState], because
 * it spans days; `:app` stores it and calls these helpers.
 */
object PillStock {
    /** Warn when fewer than this many days of pills are left (FR-10.3). */
    const val LOW_STOCK_DAYS = 5

    /** Largest count the app accepts as input. */
    const val MAX_PILLS = 9999

    /**
     * Pills taken by a reduce from [before] to [after]: the number of TAKEN records it added.
     * Zero across a rollover, since a dose event aimed at the old day is ignored (02 §4.1).
     */
    fun pillsTaken(before: DayState, after: DayState): Int {
        if (before.dayId != after.dayId) return 0
        return (takenCount(after) - takenCount(before)).coerceAtLeast(0)
    }

    /** The count after [taken] more pills, never below zero. */
    fun afterTaking(pillsLeft: Int, taken: Int): Int = (pillsLeft - taken).coerceAtLeast(0)

    /** Whole days [pillsLeft] lasts at [dosesPerDay] pills a day. */
    fun daysLeft(pillsLeft: Int, dosesPerDay: Int): Int = pillsLeft / dosesPerDay

    /** The warning to show, or null when the count isn't tracked or isn't low. */
    fun lowStock(pillsLeft: Int?, dosesPerDay: Int): LowStock? {
        if (pillsLeft == null || pillsLeft >= LOW_STOCK_DAYS * dosesPerDay) return null
        return LowStock(pillsLeft, daysLeft(pillsLeft, dosesPerDay))
    }

    private fun takenCount(day: DayState): Int = day.doses.count { it.status == DoseStatus.TAKEN }
}
