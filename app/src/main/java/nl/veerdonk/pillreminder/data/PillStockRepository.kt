package nl.veerdonk.pillreminder.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import nl.veerdonk.pillreminder.core.model.PillStock
import java.io.File
import java.io.IOException

/** The number of pills on hand (01 FR-10). `null` means the count isn't tracked. */
interface PillStockRepository {
    val pillsLeft: Flow<Int?>
    suspend fun current(): Int?

    /** Sets the count, or stops tracking with `null`. */
    suspend fun set(count: Int?)

    /** Subtracts [count] taken pills, never below zero. Does nothing while untracked. */
    suspend fun take(count: Int)
}

/** [PillStockRepository] on its own small Preferences DataStore file (`pill_stock`). */
class DataStorePillStockRepository(
    private val dataStore: DataStore<Preferences>,
) : PillStockRepository {

    override val pillsLeft: Flow<Int?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[PILLS_LEFT] }
        .distinctUntilChanged()

    override suspend fun current(): Int? = pillsLeft.first()

    override suspend fun set(count: Int?) {
        require(count == null || count in 0..PillStock.MAX_PILLS) { "Invalid pill count $count" }
        dataStore.edit { p -> if (count == null) p.remove(PILLS_LEFT) else p[PILLS_LEFT] = count }
    }

    override suspend fun take(count: Int) {
        // One edit, so a count set at the same moment isn't lost.
        dataStore.edit { p -> p[PILLS_LEFT]?.let { p[PILLS_LEFT] = PillStock.afterTaking(it, count) } }
    }

    companion object {
        const val FILE_NAME = "pill_stock"

        private val PILLS_LEFT = intPreferencesKey("pills_left")

        /** Creates the DataStore for [file]. Create only one per file per process. */
        fun createDataStore(
            file: () -> File,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )
    }
}
