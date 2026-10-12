package nl.veerdonk.pillreminder

import android.content.Context
import android.util.Log
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.controller.ReminderController
import nl.veerdonk.pillreminder.data.DataStoreSettingsRepository
import nl.veerdonk.pillreminder.data.DataStoreStateStore
import nl.veerdonk.pillreminder.data.SettingsRepository
import nl.veerdonk.pillreminder.data.StateStore
import nl.veerdonk.pillreminder.data.history.HistoryDatabase
import nl.veerdonk.pillreminder.data.history.HistoryRepository
import nl.veerdonk.pillreminder.data.history.RoomHistoryRepository
import nl.veerdonk.pillreminder.platform.AlarmScheduler
import nl.veerdonk.pillreminder.platform.AndroidAlarmScheduler
import nl.veerdonk.pillreminder.platform.AndroidNotifier
import nl.veerdonk.pillreminder.platform.AndroidWakeDetector
import nl.veerdonk.pillreminder.platform.DetectionLogEntry
import nl.veerdonk.pillreminder.platform.NoOpRingController
import nl.veerdonk.pillreminder.platform.Notifier
import nl.veerdonk.pillreminder.platform.RingController
import nl.veerdonk.pillreminder.platform.WakeDetector
import java.time.Clock
import java.time.ZoneId

/**
 * Manual dependency container (no Hilt), created once by [PillReminderApp]. Everything here is
 * a process-wide singleton: in particular there must be exactly one DataStore per file, since a
 * second one on the same file throws at runtime.
 *
 * The ring controller is a no-op until T8 replaces it.
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

    /**
     * Process-wide scope for work that must outlive a receiver or activity, such as a tick
     * started from `goAsync()`. A failed job is logged; it doesn't cancel the others.
     */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Background job failed", e)
        },
    )

    val alarmScheduler: AlarmScheduler = AndroidAlarmScheduler(appContext)

    val notifier: Notifier = AndroidNotifier(appContext)

    val ringController: RingController = NoOpRingController // T8

    private val androidWakeDetector = AndroidWakeDetector(appContext)

    val wakeDetector: WakeDetector = androidWakeDetector

    /** The last wake check, for the debug "Detection log" in Settings → Advanced (T10). */
    val detectionLog: StateFlow<DetectionLogEntry?> = androidWakeDetector.lastCheck

    val reminderController = ReminderController(
        clock = clock,
        zone = ZoneId::systemDefault,
        settingsRepository = settingsRepository,
        stateStore = stateStore,
        historyRepository = historyRepository,
        wakeDetector = wakeDetector,
        notifier = notifier,
        ringController = ringController,
        alarmScheduler = alarmScheduler,
    )

    /**
     * Ticks after every settings change (design §5), so deadlines derived from the settings are
     * rescheduled. The current value on start is skipped: it is not a change.
     */
    fun tickOnSettingsChange() {
        appScope.launch {
            settingsRepository.settings.drop(1).collect { reminderController.tick() }
        }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
