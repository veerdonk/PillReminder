package nl.veerdonk.pillreminder.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.WakeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate

class StateStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = tmp.root.resolve(DataStoreStateStore.FILE_NAME)

    private fun TestScope.newStore(scope: CoroutineScope = backgroundScope) =
        DataStoreStateStore(DataStoreStateStore.createDataStore(file = { file }, scope = scope))

    private val state = DayState(
        dayId = LocalDate.of(2026, 10, 12),
        phase = Phase.SNOOZED,
        nextDose = 2,
        doses = listOf(
            DoseRecord(
                index = 1,
                status = DoseStatus.TAKEN,
                takenAt = Instant.parse("2026-10-12T05:31:12.345Z"),
                source = DoseSource.ALARM,
                snoozesUsed = 1,
                firstRemindedAt = Instant.parse("2026-10-12T05:10:00Z"),
            ),
        ),
        wakeAt = Instant.parse("2026-10-12T05:05:00Z"),
        wakeSource = WakeSource.DETECTED,
        reminderShownAt = Instant.parse("2026-10-12T09:21:12Z"),
        firstRemindedAt = Instant.parse("2026-10-12T09:21:12Z"),
        ringingSince = null,
        snoozedAt = Instant.parse("2026-10-12T09:38:00.000000001Z"),
        snoozesUsed = 1,
    )

    @Test
    fun missingFileLoadsNull() = runTest {
        val store = newStore()
        assertNull(store.load())
        assertNull(store.state.first())
    }

    @Test
    fun roundTripsDayState() = runTest {
        val store = newStore()
        store.save(state)
        assertEquals(state, store.load())
        assertEquals(state, store.state.first())
    }

    @Test
    fun stateSurvivesANewDataStoreInstance() = runTest {
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        newStore(firstScope).save(state)
        firstScope.coroutineContext.job.cancelAndJoin()

        assertEquals(state, newStore().load())
    }

    @Test
    fun garbageBytesLoadNull() = corruptFileLoadsNull(byteArrayOf(0x00, 0xFF.toByte(), 0x13, 0x37, 0x7B))

    @Test
    fun jsonOfTheWrongShapeLoadsNull() {
        corruptFileLoadsNull("""{"hello":"world"}""".encodeToByteArray())
        corruptFileLoadsNull("""[1, 2, 3]""".encodeToByteArray())
        corruptFileLoadsNull(""""just a string"""".encodeToByteArray())
        // Right keys, but a phase and a date that don't exist.
        corruptFileLoadsNull("""{"dayId":"2026-10-12","phase":"NAPPING","nextDose":1,"doses":[]}""".encodeToByteArray())
        corruptFileLoadsNull("""{"dayId":"2026-13-45","phase":"DONE","nextDose":1,"doses":[]}""".encodeToByteArray())
    }

    @Test
    fun emptyFileLoadsNull() = corruptFileLoadsNull(ByteArray(0))

    @Test
    fun corruptFileCanBeOverwritten() = runTest {
        file.writeText("not json")
        val store = newStore()
        assertNull(store.load())
        store.save(state)
        assertEquals(state, store.load())
    }

    private fun corruptFileLoadsNull(bytes: ByteArray) = runTest {
        file.writeBytes(bytes)
        val store = newStore()
        assertNull(store.load())
        assertNull(store.state.first())
    }
}
