package nl.veerdonk.pillreminder.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {

    @Upsert
    protected abstract suspend fun upsertDayRow(day: DayLogEntity)

    @Query("DELETE FROM dose_log WHERE day_id = :dayId")
    protected abstract suspend fun deleteDoseRows(dayId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertDoseRows(doses: List<DoseLogEntity>)

    /** Writes the day row and replaces all of that day's dose rows with [doses], atomically. */
    @Transaction
    open suspend fun replaceDay(day: DayLogEntity, doses: List<DoseLogEntity>) {
        upsertDayRow(day)
        deleteDoseRows(day.dayId)
        insertDoseRows(doses)
    }

    /** All days, newest first. ISO dates sort chronologically as text. */
    @Transaction
    @Query("SELECT * FROM day_log ORDER BY day_id DESC")
    abstract fun days(): Flow<List<DayLogWithDoseRows>>
}
