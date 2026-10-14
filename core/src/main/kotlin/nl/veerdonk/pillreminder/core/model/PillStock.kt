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

    /**
     * Below this many days of pills, the status notification stays up once the day is done,
     * asking you to call the pharmacy (FR-10.6).
     */
    const val CALL_PHARMACY_DAYS = 3

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

    /**
     * The count after a refill of [bottleSize] pills (FR-10.5): leftover pills are kept, so the
     * bottle is added to them; an untracked count starts at [bottleSize]. Capped at [MAX_PILLS].
     */
    fun afterRefill(pillsLeft: Int?, bottleSize: Int): Int = ((pillsLeft ?: 0) + bottleSize).coerceAtMost(MAX_PILLS)

    /** Whole days [pillsLeft] lasts at [dosesPerDay] pills a day. */
    fun daysLeft(pillsLeft: Int, dosesPerDay: Int): Int = pillsLeft / dosesPerDay

    /** The warning to show, or null when the count isn't tracked or isn't low. */
    fun lowStock(pillsLeft: Int?, dosesPerDay: Int): LowStock? {
        if (pillsLeft == null || pillsLeft >= LOW_STOCK_DAYS * dosesPerDay) return null
        return LowStock(pillsLeft, daysLeft(pillsLeft, dosesPerDay))
    }

    /** True when [lowStock] is below [CALL_PHARMACY_DAYS] days (FR-10.6). */
    fun shouldCallPharmacy(lowStock: LowStock): Boolean = lowStock.daysLeft < CALL_PHARMACY_DAYS

    private fun takenCount(day: DayState): Int = day.doses.count { it.status == DoseStatus.TAKEN }
}
