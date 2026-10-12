package nl.veerdonk.pillreminder.data.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.WakeSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Day history (docs/03-technical-design.md §3.3, §4). Kept indefinitely (FR-7.2). */
interface HistoryRepository {
    /** All logged days, newest first. */
    fun days(): Flow<List<DayWithDoses>>

    /** Writes [day]'s `day_log` row and replaces all its `dose_log` rows with `day.doses`. */
    suspend fun upsert(day: DayState)
}

/** [HistoryRepository] on Room. `updated_at` comes from [clock]. */
class RoomHistoryRepository(
    private val dao: HistoryDao,
    private val clock: Clock,
) : HistoryRepository {

    override fun days(): Flow<List<DayWithDoses>> = dao.days().map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsert(day: DayState) {
        val dayId = day.dayId.toString()
        dao.replaceDay(
            day = DayLogEntity(
                dayId = dayId,
                wakeAt = day.wakeAt?.toEpochMilli(),
                wakeSource = day.wakeSource?.name,
                finalPhase = day.phase.name,
                updatedAt = clock.millis(),
            ),
            doses = day.doses.map { d ->
                DoseLogEntity(
                    dayId = dayId,
                    doseIndex = d.index,
                    status = d.status.name,
                    takenAt = d.takenAt?.toEpochMilli(),
                    source = d.source?.name,
                    snoozesUsed = d.snoozesUsed,
                    firstRemindedAt = d.firstRemindedAt?.toEpochMilli(),
                )
            },
        )
    }

    private fun DayLogWithDoseRows.toDomain() = DayWithDoses(
        dayId = LocalDate.parse(day.dayId),
        wakeAt = day.wakeAt?.let(Instant::ofEpochMilli),
        wakeSource = day.wakeSource?.let(WakeSource::valueOf),
        finalPhase = Phase.valueOf(day.finalPhase),
        updatedAt = Instant.ofEpochMilli(day.updatedAt),
        doses = doses.sortedBy { it.doseIndex }.map { d ->
            DoseRecord(
                index = d.doseIndex,
                status = DoseStatus.valueOf(d.status),
                takenAt = d.takenAt?.let(Instant::ofEpochMilli),
                source = d.source?.let(DoseSource::valueOf),
                snoozesUsed = d.snoozesUsed,
                firstRemindedAt = d.firstRemindedAt?.let(Instant::ofEpochMilli),
            )
        },
    )
}
