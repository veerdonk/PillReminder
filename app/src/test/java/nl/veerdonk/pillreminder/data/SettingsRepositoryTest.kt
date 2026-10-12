package nl.veerdonk.pillreminder.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.veerdonk.pillreminder.core.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Modifier
import java.time.Duration
import java.time.LocalTime

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.newDataStore(scope: CoroutineScope = backgroundScope) =
        DataStoreSettingsRepository.createDataStore(
            file = { tmp.root.resolve("settings.preferences_pb") },
            scope = scope,
        )

    /** Every field differs from Settings(); times at 00:00 and 23:59. */
    private val allChanged = Settings(
        weekdayEarliestTime = LocalTime.of(6, 15),
        weekdayFallbackTime = LocalTime.of(23, 59),
        weekendEarliestTime = LocalTime.of(9, 5),
        weekendFallbackTime = LocalTime.of(12, 45),
        minUnlocks = 4,
        minAppUsage = Duration.ofMinutes(0),
        detectionWindow = Duration.ofMinutes(45),
        detectionPollInterval = Duration.ofMinutes(1),
        dosesPerDay = 6,
        doseInterval = Duration.ofMinutes(12 * 60),
        escalationDelay = Duration.ofMinutes(120),
        snoozeDuration = Duration.ofMinutes(60),
        maxSnoozes = 0,
        alarmRingDuration = Duration.ofMinutes(10),
        holdFirstAlarmUntilFallback = false,
        dayRolloverTime = LocalTime.MIDNIGHT,
        alarmSoundUri = "content://media/internal/audio/media/42",
    )

    @Test
    fun emptyStoreReadsDefaults() = runTest {
        val repo = DataStoreSettingsRepository(newDataStore())
        assertEquals(Settings(), repo.current())
    }

    @Test
    fun roundTripsEveryFieldIncludingMidnightAnd2359() = runTest {
        val defaults = Settings()
        // Guard: the fixture really changes every field.
        Settings::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { f ->
            f.isAccessible = true
            assertTrue("${f.name} must differ from default", f.get(allChanged) != f.get(defaults))
        }
        assertTrue(allChanged.validate().isEmpty())

        val repo = DataStoreSettingsRepository(newDataStore())
        repo.update(allChanged)
        assertEquals(allChanged, repo.current())
        assertEquals(allChanged, repo.settings.first())
    }

    @Test
    fun valuesSurviveANewDataStoreInstance() = runTest {
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        DataStoreSettingsRepository(newDataStore(firstScope)).update(allChanged)
        // Only one active DataStore per file is allowed, so close the first one.
        firstScope.coroutineContext.job.cancelAndJoin()

        assertEquals(allChanged, DataStoreSettingsRepository(newDataStore()).current())
    }

    @Test
    fun alarmSoundUriCanBeSetAndClearedAgain() = runTest {
        val repo = DataStoreSettingsRepository(newDataStore())
        repo.update(Settings(alarmSoundUri = "content://media/internal/audio/media/7"))
        assertEquals("content://media/internal/audio/media/7", repo.current().alarmSoundUri)

        repo.update(Settings(alarmSoundUri = null))
        assertNull(repo.current().alarmSoundUri)
        assertEquals(Settings(), repo.current())
    }

    @Test
    fun updateRejectsInvalidSettingsAndKeepsStoredOnes() = runTest {
        val repo = DataStoreSettingsRepository(newDataStore())
        repo.update(allChanged)

        val invalid = listOf(
            allChanged.copy(minUnlocks = 0),
            allChanged.copy(doseInterval = Duration.ofSeconds(90)),
            allChanged.copy(weekdayFallbackTime = allChanged.weekdayEarliestTime),
            allChanged.copy(dayRolloverTime = LocalTime.of(7, 0)),
        )
        for (s in invalid) {
            assertTrue(s.validate().isNotEmpty())
            try {
                repo.update(s)
                fail("update accepted invalid settings: ${s.validate()}")
            } catch (_: IllegalArgumentException) {
                // expected
            }
            assertEquals(allChanged, repo.current())
        }
    }

    @Test
    fun storedValuesThatFailValidationFallBackToDefaults() = runTest {
        val store = newDataStore()
        val repo = DataStoreSettingsRepository(store)
        repo.update(allChanged)
        // e.g. written by a newer build with wider ranges: minUnlocks = 50.
        store.edit { it[intPreferencesKey("min_unlocks")] = 50 }
        assertEquals(Settings(), repo.current()) // A12: whole object falls back

        store.edit { it[intPreferencesKey("min_unlocks")] = 4 }
        assertEquals(allChanged, repo.current())

        // A minute-of-day outside 0..1439 can't even be turned into a LocalTime.
        store.edit { it[intPreferencesKey("day_rollover_time")] = 24 * 60 }
        assertEquals(Settings(), repo.current())
    }

    @Test
    fun corruptFileReadsDefaults() = runTest {
        tmp.root.resolve("settings.preferences_pb").writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x55, 0x00))
        val repo = DataStoreSettingsRepository(newDataStore())
        assertEquals(Settings(), repo.current())
        repo.update(allChanged)
        assertEquals(allChanged, repo.current())
    }
}
