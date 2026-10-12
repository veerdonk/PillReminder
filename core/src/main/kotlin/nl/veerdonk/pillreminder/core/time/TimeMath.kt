package nl.veerdonk.pillreminder.core.time

import nl.veerdonk.pillreminder.core.model.Settings
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Day-boundary and wake-window arithmetic (02-state-machine §2, derived values).
 *
 * Local times are converted with [java.time.ZonedDateTime] rules: a time inside a DST gap is
 * moved forward by the gap length, and a time inside a DST overlap uses the earlier offset.
 */
object TimeMath {

    /**
     * The app-day that [now] belongs to: the day `d` with `startOfDay(d) ≤ now < startOfDay(d + 1)`.
     * For example 02:59 on 13 Oct belongs to day 12 Oct (with the default 03:00 rollover).
     *
     * This equals `(now in zone − dayRolloverTime).toLocalDate()` except when the rollover
     * time falls in a DST gap or overlap; there it stays consistent with [startOfDay] and
     * [nextRolloverAt], so the day id never goes backwards.
     */
    fun dayIdOf(now: Instant, s: Settings, zone: ZoneId): LocalDate {
        val candidate = now.atZone(zone).toLocalDateTime()
            .minusNanos(s.dayRolloverTime.toNanoOfDay())
            .toLocalDate()
        return when {
            now < startOfDay(candidate, s, zone) -> candidate.minusDays(1)
            now >= startOfDay(candidate.plusDays(1), s, zone) -> candidate.plusDays(1)
            else -> candidate
        }
    }

    /** `day` at `dayRolloverTime`. */
    fun startOfDay(day: LocalDate, s: Settings, zone: ZoneId): Instant =
        at(day, s.dayRolloverTime, zone)

    /** `day` at the weekday or weekend earliest time. */
    fun earliestAt(day: LocalDate, s: Settings, zone: ZoneId): Instant =
        at(day, if (isWeekend(day)) s.weekendEarliestTime else s.weekdayEarliestTime, zone)

    /** `day` at the weekday or weekend fallback time. */
    fun fallbackAt(day: LocalDate, s: Settings, zone: ZoneId): Instant =
        at(day, if (isWeekend(day)) s.weekendFallbackTime else s.weekdayFallbackTime, zone)

    /** `(day + 1)` at `dayRolloverTime`, i.e. when this app-day ends. */
    fun nextRolloverAt(day: LocalDate, s: Settings, zone: ZoneId): Instant =
        startOfDay(day.plusDays(1), s, zone)

    fun isWeekend(day: LocalDate): Boolean =
        day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

    private fun at(day: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        day.atTime(time).atZone(zone).toInstant()
}
