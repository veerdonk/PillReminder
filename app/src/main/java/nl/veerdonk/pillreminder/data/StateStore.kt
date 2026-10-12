package nl.veerdonk.pillreminder.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.serial.CoreJson
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.DateTimeException

/**
 * The persisted [DayState] of the current app-day (docs/03-technical-design.md §3.2, §4).
 * `null` means there is no usable state; the caller then starts with `Engine.fresh(...)`.
 */
interface StateStore {
    val state: Flow<DayState?>
    suspend fun load(): DayState?
    suspend fun save(s: DayState)
}

/**
 * [StateStore] on its own DataStore file (`day_state.json`) holding [DayState] as JSON via
 * [CoreJson]. Missing, empty or undecodable data reads as `null`; neither [state] nor [load]
 * throws for it. This class never creates a fresh state itself.
 */
class DataStoreStateStore(
    private val dataStore: DataStore<DayState?>,
) : StateStore {

    override val state: Flow<DayState?> = dataStore.data
        .catch { e -> if (e is IOException) emit(null) else throw e }

    override suspend fun load(): DayState? = state.first()

    override suspend fun save(s: DayState) {
        dataStore.updateData { s }
    }

    companion object {
        const val FILE_NAME = "day_state.json"

        /** Creates the DataStore for [file]. Create only one per file per process. */
        fun createDataStore(
            file: () -> File,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): DataStore<DayState?> = DataStoreFactory.create(
            serializer = DayStateSerializer,
            scope = scope,
            produceFile = file,
        )
    }
}

/** JSON serializer for the day state. Decode errors become `null` instead of an exception. */
internal object DayStateSerializer : Serializer<DayState?> {
    override val defaultValue: DayState? = null

    override suspend fun readFrom(input: InputStream): DayState? {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return null
        return try {
            CoreJson.decodeFromString(DayState.serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: DateTimeException) {
            null // a malformed date/time string inside otherwise valid JSON
        }
    }

    override suspend fun writeTo(t: DayState?, output: OutputStream) {
        if (t != null) output.write(CoreJson.encodeToString(DayState.serializer(), t).encodeToByteArray())
    }
}
