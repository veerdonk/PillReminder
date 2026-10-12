package nl.veerdonk.pillreminder.core.time

import nl.veerdonk.pillreminder.core.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TimeMathTest {
    private val ams = ZoneId.of("Europe/Amsterdam")
    private val s = Settings()

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(ams).toInstant()
    private fun date(text: String): LocalDate = LocalDate.parse(text)

    // --- dayIdOf around rollover ---

    @Test
    fun `0259 belongs to the previous day`() {
        assertEquals(date("2026-10-12"), TimeMath.dayIdOf(local("2026-10-13T02:59:59"), s, ams))
    }

    @Test
    fun `0300 starts the new day`() {
        assertEquals(date("2026-10-13"), TimeMath.dayIdOf(local("2026-10-13T03:00"), s, ams))
    }

    @Test
    fun `midday and just before midnight belong to the same day`() {
        assertEquals(date("2026-10-13"), TimeMath.dayIdOf(local("2026-10-13T12:00"), s, ams))
        assertEquals(date("2026-10-13"), TimeMath.dayIdOf(local("2026-10-13T23:59:59"), s, ams))
        assertEquals(date("2026-10-13"), TimeMath.dayIdOf(local("2026-10-14T00:00"), s, ams))
    }

    @Test
    fun `custom rollover at midnight`() {
        val midnight = s.copy(dayRolloverTime = LocalTime.MIDNIGHT)
        assertEquals(date("2026-10-12"), TimeMath.dayIdOf(local("2026-10-12T23:59:59"), midnight, ams))
        assertEquals(date("2026-10-13"), TimeMath.dayIdOf(local("2026-10-13T00:00"), midnight, ams))
    }

    @Test
    fun `dayIdOf uses the passed zone`() {
        val utc = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-13T02:30:00Z") // 04:30 in Amsterdam
        assertEquals(date("2026-10-12"), TimeMath.dayIdOf(now, s, utc))
        assertEquals(date("2026-10-13"), TimeMath.dayIdOf(now, s, ams))
    }

    @Test
    fun `startOfDay and nextRolloverAt`() {
        assertEquals(local("2026-10-12T03:00"), TimeMath.startOfDay(date("2026-10-12"), s, ams))
        assertEquals(local("2026-10-13T03:00"), TimeMath.nextRolloverAt(date("2026-10-12"), s, ams))
    }

    // --- weekday vs weekend ---

    @Test
    fun `isWeekend is true only on Saturday and Sunday`() {
        assertFalse(TimeMath.isWeekend(date("2026-10-09"))) // Friday
        assertTrue(TimeMath.isWeekend(date("2026-10-10"))) // Saturday
        assertTrue(TimeMath.isWeekend(date("2026-10-11"))) // Sunday
        assertFalse(TimeMath.isWeekend(date("2026-10-12"))) // Monday
    }

    @Test
    fun `weekday uses weekday times`() {
        val monday = date("2026-10-12")
        assertEquals(local("2026-10-12T07:00"), TimeMath.earliestAt(monday, s, ams))
        assertEquals(local("2026-10-12T10:30"), TimeMath.fallbackAt(monday, s, ams))
    }

    @Test
    fun `weekend uses weekend times`() {
        val saturday = date("2026-10-10")
        assertEquals(local("2026-10-10T08:30"), TimeMath.earliestAt(saturday, s, ams))
        assertEquals(local("2026-10-10T11:30"), TimeMath.fallbackAt(saturday, s, ams))
    }

    @Test
    fun `Sunday night before rollover is still Sunday, then Monday uses weekday times`() {
        // 02:00 on Monday morning still belongs to the Sunday app-day.
        val sundayNight = TimeMath.dayIdOf(local("2026-10-12T02:00"), s, ams)
        assertEquals(date("2026-10-11"), sundayNight)
        assertTrue(TimeMath.isWeekend(sundayNight))
        assertEquals(local("2026-10-11T08:30"), TimeMath.earliestAt(sundayNight, s, ams))

        val monday = TimeMath.dayIdOf(local("2026-10-12T03:00"), s, ams)
        assertEquals(date("2026-10-12"), monday)
        assertFalse(TimeMath.isWeekend(monday))
        assertEquals(local("2026-10-12T07:00"), TimeMath.earliestAt(monday, s, ams))
        assertEquals(local("2026-10-12T10:30"), TimeMath.fallbackAt(monday, s, ams))
    }

    // --- DST in Europe/Amsterdam (2026: spring forward 29 Mar, fall back 25 Oct) ---

    @Test
    fun `spring forward day is 23 hours and times are local`() {
        val sat = date("2026-03-28")
        val sun = date("2026-03-29")
        assertEquals(Instant.parse("2026-03-28T02:00:00Z"), TimeMath.startOfDay(sat, s, ams)) // 03:00 CET
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), TimeMath.nextRolloverAt(sat, s, ams)) // 03:00 CEST
        assertEquals(
            Duration.ofHours(23),
            Duration.between(TimeMath.startOfDay(sat, s, ams), TimeMath.nextRolloverAt(sat, s, ams)),
        )
        assertEquals(Instant.parse("2026-03-29T06:30:00Z"), TimeMath.earliestAt(sun, s, ams)) // 08:30 CEST
        assertEquals(Instant.parse("2026-03-29T09:30:00Z"), TimeMath.fallbackAt(sun, s, ams)) // 11:30 CEST

        // 01:59:59 CET is the last second of Saturday's app-day; the next second is 03:00 CEST.
        assertEquals(sat, TimeMath.dayIdOf(Instant.parse("2026-03-29T00:59:59Z"), s, ams))
        assertEquals(sun, TimeMath.dayIdOf(Instant.parse("2026-03-29T01:00:00Z"), s, ams))
    }

    @Test
    fun `fall back day is 25 hours and the repeated hour belongs to the old day`() {
        val sat = date("2026-10-24")
        val sun = date("2026-10-25")
        assertEquals(Instant.parse("2026-10-24T01:00:00Z"), TimeMath.startOfDay(sat, s, ams)) // 03:00 CEST
        assertEquals(Instant.parse("2026-10-25T02:00:00Z"), TimeMath.nextRolloverAt(sat, s, ams)) // 03:00 CET
        assertEquals(
            Duration.ofHours(25),
            Duration.between(TimeMath.startOfDay(sat, s, ams), TimeMath.nextRolloverAt(sat, s, ams)),
        )
        assertEquals(Instant.parse("2026-10-25T07:30:00Z"), TimeMath.earliestAt(sun, s, ams)) // 08:30 CET

        // 02:30 occurs twice; both belong to Saturday's app-day.
        assertEquals(sat, TimeMath.dayIdOf(Instant.parse("2026-10-25T00:30:00Z"), s, ams)) // 02:30 CEST
        assertEquals(sat, TimeMath.dayIdOf(Instant.parse("2026-10-25T01:30:00Z"), s, ams)) // 02:30 CET
        assertEquals(sun, TimeMath.dayIdOf(Instant.parse("2026-10-25T02:00:00Z"), s, ams)) // 03:00 CET
    }

    @Test
    fun `rollover inside the spring-forward gap is shifted forward consistently`() {
        val s0230 = s.copy(dayRolloverTime = LocalTime.of(2, 30))
        val sat = date("2026-03-28")
        val sun = date("2026-03-29")
        // 02:30 doesn't exist on 29 Mar; it becomes 03:30 CEST.
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), TimeMath.startOfDay(sun, s0230, ams))
        assertEquals(sat, TimeMath.dayIdOf(Instant.parse("2026-03-29T01:29:59Z"), s0230, ams))
        assertEquals(sun, TimeMath.dayIdOf(Instant.parse("2026-03-29T01:30:00Z"), s0230, ams))
    }

    @Test
    fun `rollover inside the fall-back overlap never goes back to the old day`() {
        val s0230 = s.copy(dayRolloverTime = LocalTime.of(2, 30))
        val sun = date("2026-10-25")
        // The earlier 02:30 (CEST) starts the day.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), TimeMath.startOfDay(sun, s0230, ams))
        assertEquals(sun, TimeMath.dayIdOf(Instant.parse("2026-10-25T00:30:00Z"), s0230, ams))
        // 02:15 CET (after the clocks went back) is still Sunday.
        assertEquals(sun, TimeMath.dayIdOf(Instant.parse("2026-10-25T01:15:00Z"), s0230, ams))
    }

    @Test
    fun `dayIdOf is consistent with startOfDay for every day of the year`() {
        val rollovers = listOf("00:00", "01:59", "02:00", "02:30", "03:00", "06:00").map(LocalTime::parse)
        for (rollover in rollovers) {
            val settings = s.copy(dayRolloverTime = rollover)
            var day = date("2026-01-01")
            while (day.year == 2026) {
                val start = TimeMath.startOfDay(day, settings, ams)
                assertEquals("$rollover $day", day, TimeMath.dayIdOf(start, settings, ams))
                assertEquals("$rollover $day", day.minusDays(1), TimeMath.dayIdOf(start.minusNanos(1), settings, ams))
                assertEquals(TimeMath.startOfDay(day.plusDays(1), settings, ams), TimeMath.nextRolloverAt(day, settings, ams))
                day = day.plusDays(1)
            }
        }
    }
}
