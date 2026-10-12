package nl.veerdonk.pillreminder.ui.history

import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.WakeSource
import nl.veerdonk.pillreminder.data.history.DayWithDoses
import nl.veerdonk.pillreminder.ui.home.isReminderActive
import java.time.Instant
import java.time.LocalDate

/** One chip per dose on a History row (FR-7.1). */
sealed interface DoseChip {
    val index: Int

    data class Taken(override val index: Int, val at: Instant) : DoseChip

    data class Skipped(override val index: Int) : DoseChip

    data class Missed(override val index: Int) : DoseChip

    /** Today only: reminder or alarm active for this dose. */
    data class Pending(override val index: Int) : DoseChip

    /** Today only: no row yet and not due. */
    data class Upcoming(override val index: Int) : DoseChip
}

data class HistoryRow(
    val dayId: LocalDate,
    val wakeAt: Instant?,
    val wakeSource: WakeSource?,
    val chips: List<DoseChip>,
    val snoozes: Int,
    val isToday: Boolean,
)

/**
 * Rows for the History list, in the order of [days] (newest first). For today, doses without a
 * row show as pending/upcoming (design §3.3) and the snoozes of the dose in progress, which are
 * only on the live [liveState], are added.
 */
fun buildHistoryRows(days: List<DayWithDoses>, today: LocalDate, doseCount: Int, liveState: DayState?): List<HistoryRow> =
    days.map { day ->
        val isToday = day.dayId == today
        val chips = day.doses.map { it.toChip() }.toMutableList<DoseChip>()
        var snoozes = day.doses.sumOf { it.snoozesUsed }
        if (isToday) {
            val recorded = day.doses.map { it.index }.toSet()
            val live = liveState?.takeIf { it.dayId == day.dayId }
            val activeDose = live?.takeIf { isReminderActive(it.phase) }?.nextDose
            for (index in 1..doseCount) {
                if (index in recorded) continue
                chips += if (index == activeDose) DoseChip.Pending(index) else DoseChip.Upcoming(index)
            }
            chips.sortBy { it.index }
            if (live != null) snoozes += live.snoozesUsed
        }
        HistoryRow(day.dayId, day.wakeAt, day.wakeSource, chips, snoozes, isToday)
    }

private fun nl.veerdonk.pillreminder.core.model.DoseRecord.toChip(): DoseChip = when (status) {
    DoseStatus.TAKEN -> DoseChip.Taken(index, takenAt ?: Instant.EPOCH)
    DoseStatus.SKIPPED -> DoseChip.Skipped(index)
    DoseStatus.MISSED -> DoseChip.Missed(index)
}
