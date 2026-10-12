package nl.veerdonk.pillreminder.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DayLogEntity::class, DoseLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        const val FILE_NAME = "history.db"
    }
}
