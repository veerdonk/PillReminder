package nl.veerdonk.pillreminder

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import nl.veerdonk.pillreminder.data.DataStoreSettingsRepository
import nl.veerdonk.pillreminder.data.DataStoreStateStore
import nl.veerdonk.pillreminder.data.SettingsRepository
import nl.veerdonk.pillreminder.data.StateStore
import nl.veerdonk.pillreminder.data.history.HistoryDatabase
import nl.veerdonk.pillreminder.data.history.HistoryRepository
import nl.veerdonk.pillreminder.data.history.RoomHistoryRepository
import java.time.Clock

/**
 * Manual dependency container (no Hilt), created once by [PillReminderApp]. Everything here is
 * a process-wide singleton: in particular there must be exactly one DataStore per file, since a
 * second one on the same file throws at runtime. Platform services and the ReminderController
 * are added by later tasks (see docs/03-technical-design.md §2).
 */
class AppContainer(appContext: Context) {

    /** Only instants are persisted, so the clock's zone doesn't matter for storage. */
    val clock: Clock = Clock.systemDefaultZone()

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(
        DataStoreSettingsRepository.createDataStore(
            file = { appContext.preferencesDataStoreFile(DataStoreSettingsRepository.FILE_NAME) },
        ),
    )

    val stateStore: StateStore = DataStoreStateStore(
        DataStoreStateStore.createDataStore(
            file = { appContext.dataStoreFile(DataStoreStateStore.FILE_NAME) },
        ),
    )

    private val historyDatabase: HistoryDatabase = Room.databaseBuilder(
        appContext,
        HistoryDatabase::class.java,
        HistoryDatabase.FILE_NAME,
    ).build()

    val historyRepository: HistoryRepository = RoomHistoryRepository(historyDatabase.historyDao(), clock)
}
