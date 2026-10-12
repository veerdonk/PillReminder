package nl.veerdonk.pillreminder.data.history

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One row per app-day (docs/03-technical-design.md §3.3). Times are epoch millis, enums are
 * stored by name.
 */
@Entity(tableName = "day_log")
data class DayLogEntity(
    @PrimaryKey @ColumnInfo(name = "day_id") val dayId: String, // ISO date
    @ColumnInfo(name = "wake_at") val wakeAt: Long?,
    @ColumnInfo(name = "wake_source") val wakeSource: String?,
    @ColumnInfo(name = "final_phase") val finalPhase: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** One row per finalised dose of a day (docs/03-technical-design.md §3.3). */
@Entity(tableName = "dose_log", primaryKeys = ["day_id", "dose_index"])
data class DoseLogEntity(
    @ColumnInfo(name = "day_id") val dayId: String,
    @ColumnInfo(name = "dose_index") val doseIndex: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "taken_at") val takenAt: Long?,
    @ColumnInfo(name = "source") val source: String?,
    @ColumnInfo(name = "snoozes_used") val snoozesUsed: Int,
    @ColumnInfo(name = "first_reminded_at") val firstRemindedAt: Long?,
)

/** Room read model: a day_log row with its dose_log rows (in no particular order). */
data class DayLogWithDoseRows(
    @Embedded val day: DayLogEntity,
    @Relation(parentColumn = "day_id", entityColumn = "day_id")
    val doses: List<DoseLogEntity>,
)
