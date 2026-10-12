package nl.veerdonk.pillreminder.data.history

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.WakeSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var db: HistoryDatabase
    private val clock = MutableClock(Instant.parse("2026-10-12T20:00:00Z"))
    private lateinit var repo: RoomHistoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomHistoryRepository(db.historyDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private val day1 = LocalDate.of(2026, 10, 11)
    private val day2 = LocalDate.of(2026, 10, 12)
    private val day0 = LocalDate.of(2026, 10, 10)

    private fun taken(index: Int, at: String) = DoseRecord(
        index = index,
        status = DoseStatus.TAKEN,
        takenAt = Instant.parse(at),
        source = DoseSource.NOTIFICATION,
        snoozesUsed = index - 1,
        firstRemindedAt = Instant.parse(at).minusSeconds(600),
    )

    private val threeDoses = listOf(
        taken(1, "2026-10-12T05:30:00Z"),
        taken(2, "2026-10-12T09:25:00.123Z"),
        DoseRecord(3, DoseStatus.MISSED, null, null, 3, Instant.parse("2026-10-12T13:20:00Z")),
    )

    private fun state(day: LocalDate, phase: Phase, doses: List<DoseRecord>) = DayState(
        dayId = day,
        phase = phase,
        nextDose = doses.size + 1,
        doses = doses,
        wakeAt = Instant.parse("${day}T05:00:00Z"),
        wakeSource = WakeSource.FALLBACK,
    )

    @Test
    fun upsertRoundTripsADay() = runTest {
        repo.upsert(state(day2, Phase.DONE, threeDoses))

        val days = repo.days().first()
        assertEquals(
            listOf(
                DayWithDoses(
                    dayId = day2,
                    wakeAt = Instant.parse("2026-10-12T05:00:00Z"),
                    wakeSource = WakeSource.FALLBACK,
                    finalPhase = Phase.DONE,
                    updatedAt = clock.instant(),
                    doses = threeDoses,
                ),
            ),
            days,
        )
    }

    @Test
    fun upsertReplacesThatDaysDoseRowsOnly() = runTest {
        repo.upsert(state(day1, Phase.DONE, listOf(taken(1, "2026-10-11T06:00:00Z"))))
        repo.upsert(state(day2, Phase.DONE, threeDoses))

        clock.now = Instant.parse("2026-10-12T21:00:00Z")
        val oneDose = listOf(taken(1, "2026-10-12T05:45:00Z"))
        repo.upsert(state(day2, Phase.WAITING, oneDose).copy(wakeSource = WakeSource.MANUAL))

        val days = repo.days().first()
        assertEquals(listOf(day2, day1), days.map { it.dayId })

        val updated = days[0]
        assertEquals(oneDose, updated.doses)
        assertEquals(Phase.WAITING, updated.finalPhase)
        assertEquals(WakeSource.MANUAL, updated.wakeSource)
        assertEquals(Instant.parse("2026-10-12T21:00:00Z"), updated.updatedAt)

        val untouched = days[1]
        assertEquals(listOf(taken(1, "2026-10-11T06:00:00Z")), untouched.doses)
        assertEquals(Instant.parse("2026-10-12T20:00:00Z"), untouched.updatedAt)
    }

    @Test
    fun upsertWithNoDosesClearsTheDaysRows() = runTest {
        repo.upsert(state(day2, Phase.DONE, threeDoses))
        repo.upsert(state(day2, Phase.BEFORE_WINDOW, emptyList()).copy(wakeAt = null, wakeSource = null))

        val day = repo.days().first().single()
        assertEquals(emptyList<DoseRecord>(), day.doses)
        assertEquals(null, day.wakeAt)
        assertEquals(null, day.wakeSource)
    }

    @Test
    fun daysAreNewestFirstAndDosesOrderedByIndex() = runTest {
        repo.upsert(state(day1, Phase.DONE, emptyList()))
        repo.upsert(state(day2, Phase.DONE, threeDoses.reversed()))
        repo.upsert(state(day0, Phase.DONE, emptyList()))

        val days = repo.days().first()
        assertEquals(listOf(day2, day1, day0), days.map { it.dayId })
        assertEquals(listOf(1, 2, 3), days[0].doses.map { it.index })
    }

    @Test
    fun daysIsEmptyUntilTheFirstUpsert() = runTest {
        assertEquals(emptyList<DayWithDoses>(), repo.days().first())
        repo.upsert(state(day2, Phase.WAITING, threeDoses.take(1)))
        assertEquals(1, repo.days().first().single().doses.size)
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
}
