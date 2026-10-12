package nl.veerdonk.pillreminder.data.history

import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.WakeSource
import java.time.Instant
import java.time.LocalDate

/**
 * One day of history as shown by the History screen (FR-7.1): the `day_log` row plus its
 * `dose_log` rows as core [DoseRecord]s, ordered by dose index. Doses without a row (not yet
 * finalised) are absent; the UI shows them as pending for today only.
 *
 * Instants have millisecond precision, because history stores epoch millis.
 */
data class DayWithDoses(
    val dayId: LocalDate,
    val wakeAt: Instant?,
    val wakeSource: WakeSource?,
    val finalPhase: Phase, // phase at the last write
    val updatedAt: Instant,
    val doses: List<DoseRecord>, // ordered by DoseRecord.index
)
