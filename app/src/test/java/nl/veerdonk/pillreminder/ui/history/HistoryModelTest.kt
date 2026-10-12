package nl.veerdonk.pillreminder.ui.history

import nl.veerdonk.pillreminder.core.engine.Engine
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.WakeSource
import nl.veerdonk.pillreminder.data.history.DayWithDoses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HistoryModelTest {
    private val today = LocalDate.parse("2026-10-13")
    private val yesterday = today.minusDays(1)
    private val t0 = Instant.parse("2026-10-13T06:00:00Z")

    private fun taken(i: Int, snoozes: Int = 0) =
        DoseRecord(i, DoseStatus.TAKEN, t0.plusSeconds(i * 3600L), DoseSource.NOTIFICATION, snoozes, null)

    private fun rec(i: Int, status: DoseStatus, snoozes: Int = 0) = DoseRecord(i, status, null, null, snoozes, null)

    private fun day(id: LocalDate, phase: Phase, vararg doses: DoseRecord) =
        DayWithDoses(id, t0, WakeSource.DETECTED, phase, t0, doses.toList())

    @Test fun pastDayShowsOneChipPerRecordAndSnoozeSum() {
        val rows = buildHistoryRows(
            listOf(day(yesterday, Phase.DONE, taken(1, 2), rec(2, DoseStatus.SKIPPED, 1), rec(3, DoseStatus.MISSED))),
            today, 3, null,
        )
        val row = rows.single()
        assertFalse(row.isToday)
        assertEquals(
            listOf(DoseChip.Taken(1, t0.plusSeconds(3600)), DoseChip.Skipped(2), DoseChip.Missed(3)),
            row.chips,
        )
        assertEquals(3, row.snoozes)
    }

    @Test fun todayAddsPendingAndUpcomingChips() {
        val live = Engine.fresh(today).copy(phase = Phase.REMINDER, nextDose = 2, snoozesUsed = 1)
        val rows = buildHistoryRows(listOf(day(today, Phase.REMINDER, taken(1))), today, 3, live)
        val row = rows.single()
        assertTrue(row.isToday)
        assertEquals(listOf(1, 2, 3), row.chips.map { it.index })
        assertEquals(DoseChip.Pending(2), row.chips[1])
        assertEquals(DoseChip.Upcoming(3), row.chips[2])
        assertEquals(1, row.snoozes)
    }

    @Test fun todayWaitingShowsUpcomingOnly() {
        val live = Engine.fresh(today).copy(phase = Phase.WAITING, nextDose = 2)
        val row = buildHistoryRows(listOf(day(today, Phase.WAITING, taken(1))), today, 3, live).single()
        assertEquals(listOf(DoseChip.Upcoming(2), DoseChip.Upcoming(3)), row.chips.drop(1))
    }

    @Test fun orderIsKept() {
        val rows = buildHistoryRows(listOf(day(today, Phase.DONE), day(yesterday, Phase.DONE)), today, 3, null)
        assertEquals(listOf(today, yesterday), rows.map { it.dayId })
    }
}
