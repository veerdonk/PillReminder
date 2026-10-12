package nl.veerdonk.pillreminder.controller

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.veerdonk.pillreminder.core.engine.Engine
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.model.WakeSource
import nl.veerdonk.pillreminder.core.time.TimeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderControllerTest {

    // --- fixtures: Europe/Amsterdam, default settings, Tuesday 13 Oct 2026 ---

    private val ams: ZoneId = ZoneId.of("Europe/Amsterdam")
    private val day: LocalDate = LocalDate.parse("2026-10-13")
    private val nextDay: LocalDate = day.plusDays(1)

    private fun at(date: LocalDate, hhmm: String): Instant =
        LocalDateTime.parse("${date}T$hhmm").atZone(ams).toInstant()

    private fun t(hhmm: String): Instant = at(day, hhmm)

    private fun taken(index: Int, at: Instant) =
        DoseRecord(index, DoseStatus.TAKEN, at, DoseSource.NOTIFICATION, snoozesUsed = 0, firstRemindedAt = at)

    private val beforeWindow = Engine.fresh(day)
    private val detecting = beforeWindow.copy(phase = Phase.DETECTING)
    private val woken = beforeWindow.copy(wakeAt = t("07:25"), wakeSource = WakeSource.DETECTED)
    private val waiting = woken.copy(phase = Phase.WAITING, nextDose = 2, doses = listOf(taken(1, t("08:00"))))
    private val reminder = woken.copy(phase = Phase.REMINDER, reminderShownAt = t("07:25"), firstRemindedAt = t("07:25"))
    private val ringing = reminder.copy(phase = Phase.RINGING, ringingSince = t("07:40"))
    private val snoozed = ringing.copy(phase = Phase.SNOOZED, ringingSince = null, snoozedAt = t("07:42"), snoozesUsed = 1)
    private val pendingSilent = reminder.copy(phase = Phase.PENDING_SILENT, snoozesUsed = 3)
    private val done = woken.copy(
        phase = Phase.DONE,
        nextDose = 3,
        doses = listOf(taken(1, t("07:30")), taken(2, t("11:30")), taken(3, t("15:30"))),
    )

    private class Harness(initial: DayState?, now: Instant, zone: ZoneId, vararg awake: Boolean) {
        val log = CallLog()
        val clock = MutableClock(now, zone)
        val settings = FakeSettingsRepository()
        val store = FakeStateStore(log, initial)
        val history = FakeHistoryRepository(log)
        val detector = FakeWakeDetector(log, *awake)
        val controller = ReminderController(
            clock = clock,
            zone = { zone },
            settingsRepository = settings,
            stateStore = store,
            historyRepository = history,
            wakeDetector = detector,
            notifier = FakeNotifier(log),
            ringController = FakeRingController(log),
            alarmScheduler = FakeAlarmScheduler(log),
        )

        val state: DayState get() = store.stored!!

        fun platformCalls(): List<Call> = log.calls.filter {
            it is Call.ShowReminder || it is Call.CancelReminder || it is Call.EnsureRinging ||
                it is Call.EnsureStopped || it is Call.Schedule || it is Call.Cancel
        }

        fun lastSchedule(): Call.Schedule = log.calls.filterIsInstance<Call.Schedule>().last()
    }

    private fun harness(initial: DayState?, now: Instant, vararg awake: Boolean) = Harness(initial, now, ams, *awake)

    // --- detection is only invoked in DETECTING ---

    @Test
    fun `tick outside DETECTING never calls the detector`() = runTest {
        val cases = listOf(
            beforeWindow to t("06:00"),
            waiting to t("09:00"),
            reminder to t("07:30"),
            reminder to t("07:40"), // escalates to RINGING
            ringing to t("07:41"),
            snoozed to t("07:45"),
            pendingSilent to t("09:00"),
            done to t("16:00"),
        )
        for ((state, now) in cases) {
            val h = harness(state, now, true)
            h.controller.tick()
            assertEquals("${state.phase} at $now", 0, h.detector.calls)
        }
    }

    @Test
    fun `user event in DETECTING does not call the detector`() = runTest {
        val h = harness(detecting, t("07:30"), true)
        h.controller.handle(Event.RemindNow)
        assertEquals(0, h.detector.calls)
        assertEquals(Phase.REMINDER, h.state.phase)
        assertEquals(WakeSource.MANUAL, h.state.wakeSource)
    }

    @Test
    fun `tick in DETECTING calls the detector exactly once per tick`() = runTest {
        val h = harness(detecting, t("07:30"), false, false, true)

        h.controller.tick()
        assertEquals(1, h.detector.calls)
        assertEquals(Phase.DETECTING, h.state.phase)

        h.clock.now = t("07:35")
        h.controller.tick()
        assertEquals(2, h.detector.calls)
        assertEquals(Phase.DETECTING, h.state.phase)

        h.clock.now = t("07:40")
        h.controller.tick()
        assertEquals(3, h.detector.calls)
        assertEquals(Phase.REMINDER, h.state.phase)
        assertEquals(1, h.state.nextDose)
        assertEquals(WakeSource.DETECTED, h.state.wakeSource)
        assertEquals(t("07:40"), h.state.wakeAt)
    }

    @Test
    fun `detection window is the last detectionWindow, but not before earliestAt`() = runTest {
        val h = harness(detecting, t("07:05"))
        h.controller.tick()
        h.clock.now = t("07:30")
        h.controller.tick()
        assertEquals(
            listOf(Call.IsAwake(t("07:00"), t("07:05")), Call.IsAwake(t("07:10"), t("07:30"))),
            h.log.calls.filterIsInstance<Call.IsAwake>(),
        )
    }

    // --- BEFORE_WINDOW -> DETECTING within one tick runs detection (second-tick rule) ---

    @Test
    fun `tick entering DETECTING runs detection and a detected wake ends in REMINDER`() = runTest {
        val h = harness(beforeWindow, t("07:05"), true)
        h.controller.tick()

        assertEquals(listOf(Call.IsAwake(t("07:00"), t("07:05"))), h.log.calls.filterIsInstance<Call.IsAwake>())
        assertEquals(Phase.REMINDER, h.state.phase)
        assertEquals(1, h.state.nextDose)
        assertEquals(WakeSource.DETECTED, h.state.wakeSource)
        assertEquals(t("07:05"), h.state.wakeAt)
        assertEquals(t("07:05"), h.state.reminderShownAt)
    }

    @Test
    fun `tick entering DETECTING without a wake stays in DETECTING after one detection`() = runTest {
        val h = harness(beforeWindow, t("07:05"), false)
        h.controller.tick()

        assertEquals(1, h.detector.calls)
        assertEquals(Phase.DETECTING, h.state.phase)
        assertEquals(Call.Schedule(t("07:10"), userVisible = false), h.lastSchedule())
    }

    @Test
    fun `tick rolling over from DETECTING into the new day's window detects for the new day only`() = runTest {
        val h = harness(detecting, at(nextDay, "07:05"), true)
        h.controller.tick()

        // Not called for the old day's window: once, for the new day's.
        assertEquals(
            listOf(Call.IsAwake(at(nextDay, "07:00"), at(nextDay, "07:05"))),
            h.log.calls.filterIsInstance<Call.IsAwake>(),
        )
        assertEquals(nextDay, h.state.dayId)
        assertEquals(Phase.REMINDER, h.state.phase)
        assertEquals(WakeSource.DETECTED, h.state.wakeSource)
        assertEquals(day, h.history.upserts.first().dayId)
    }

    // --- finishedDay is written on rollover ---

    @Test
    fun `rollover upserts the finished day, then saves and upserts the fresh day`() = runTest {
        val dose2Reminder = woken.copy(
            phase = Phase.REMINDER,
            nextDose = 2,
            doses = listOf(taken(1, t("07:30"))),
            reminderShownAt = t("11:30"),
            firstRemindedAt = t("11:20"),
            snoozesUsed = 0,
        )
        val h = harness(dose2Reminder, at(nextDay, "03:00"))
        h.controller.tick()

        val finished = h.history.upserts[0]
        assertEquals(day, finished.dayId)
        assertEquals(Phase.DONE, finished.phase)
        assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.MISSED, DoseStatus.MISSED), finished.doses.map { it.status })
        assertEquals(t("11:20"), finished.doses[1].firstRemindedAt)
        assertNull(finished.reminderShownAt)

        val fresh = Engine.fresh(nextDay)
        assertEquals(listOf(finished, fresh), h.history.upserts)
        assertEquals(fresh, h.state)
        assertEquals(
            listOf("Load", "Upsert", "Save", "Upsert", "EnsureStopped", "CancelReminder", "Schedule"),
            h.log.names(),
        )
        assertEquals(Call.Upsert(finished), h.log.calls[1])
        assertEquals(Call.Save(fresh), h.log.calls[2])
        assertEquals(Call.Schedule(at(nextDay, "07:00"), userVisible = false), h.lastSchedule())
    }

    @Test
    fun `without a rollover only today's day is upserted, after the save`() = runTest {
        val h = harness(reminder, t("07:30"))
        h.controller.tick()
        assertEquals(listOf("Load", "Save", "Upsert"), h.log.names().take(3))
        assertEquals(listOf(h.state), h.history.upserts)
    }

    // --- apply order ---

    @Test
    fun `while ringing apply cancels the reminder, then starts ringing, then schedules`() = runTest {
        // Dose 1 rings at the fallback time (FR-3.1a).
        val h = harness(reminder, t("10:30"))
        h.controller.tick()

        assertEquals(Phase.RINGING, h.state.phase)
        val calls = h.platformCalls()
        assertEquals(listOf("CancelReminder", "EnsureRinging", "Schedule"), calls.map { it.name })
        val ring = (calls[1] as Call.EnsureRinging).spec
        assertEquals(day, ring.dayId)
        assertEquals(1, ring.dose)
        assertEquals(Call.Schedule(t("10:32"), userVisible = true), calls[2])
    }

    @Test
    fun `not ringing apply stops ringing, then shows the reminder, then schedules`() = runTest {
        val h = harness(reminder, t("07:30"))
        h.controller.tick()

        val calls = h.platformCalls()
        assertEquals(listOf("EnsureStopped", "ShowReminder", "Schedule"), calls.map { it.name })
        val spec = (calls[1] as Call.ShowReminder).spec
        assertEquals(day, spec.dayId)
        assertEquals(1, spec.dose)
        assertEquals(false, spec.alarmStyle)
        assertEquals(Call.Schedule(t("10:30"), userVisible = true), calls[2])
    }

    @Test
    fun `not ringing and no reminder apply stops ringing, then cancels the reminder, then schedules`() = runTest {
        val h = harness(ringing, t("07:41"))
        h.controller.handle(Event.TookDose(1, t("07:41"), DoseSource.ALARM))

        assertEquals(Phase.WAITING, h.state.phase)
        assertEquals(listOf("EnsureStopped", "CancelReminder", "Schedule"), h.platformCalls().map { it.name })
        assertEquals(Call.Schedule(t("07:41") + Settings().doseInterval, userVisible = true), h.lastSchedule())
    }

    @Test
    fun `apply runs after the state is saved and history written`() = runTest {
        val h = harness(reminder, t("07:30"))
        h.controller.tick()
        val names = h.log.names()
        assertTrue(names.indexOf("Upsert") < names.indexOf("EnsureStopped"))
    }

    @Test
    fun `apply is idempotent - the same outputs give the same calls`() = runTest {
        val h = harness(reminder, t("07:40"))
        h.controller.tick()
        val first = h.platformCalls()
        h.log.calls.clear()
        h.controller.tick()
        assertEquals(first, h.platformCalls())
    }

    // --- stale dayId actions are dropped ---

    @Test
    fun `action for another day is dropped without touching anything`() = runTest {
        val h = harness(reminder, t("07:30"))
        h.controller.handle(Event.TookDose(1, t("07:30"), DoseSource.NOTIFICATION), expectedDayId = day.minusDays(1))

        assertEquals(reminder, h.state)
        assertTrue(h.log.calls.none { it is Call.Save || it is Call.Upsert })
        assertEquals(emptyList<Call>(), h.platformCalls())
    }

    @Test
    fun `action for the state's day is dropped once the day has rolled over`() = runTest {
        val h = harness(reminder, at(nextDay, "03:30"))
        h.controller.handle(Event.Snooze(1), expectedDayId = day)

        assertEquals(reminder, h.state)
        assertTrue(h.log.calls.none { it is Call.Save || it is Call.Upsert })
        assertEquals(emptyList<Call>(), h.platformCalls())
    }

    @Test
    fun `action for the current day is handled`() = runTest {
        val h = harness(reminder, t("07:30"))
        h.controller.handle(Event.TookDose(1, t("07:30"), DoseSource.NOTIFICATION), expectedDayId = day)

        assertEquals(Phase.WAITING, h.state.phase)
        assertEquals(2, h.state.nextDose)
    }

    // --- missing or corrupt state ---

    @Test
    fun `missing state starts from a fresh day`() = runTest {
        val h = harness(null, t("06:00"))
        h.controller.tick()

        assertEquals(Engine.fresh(day), h.state)
        assertEquals(listOf(Engine.fresh(day)), h.history.upserts)
        assertEquals(Call.Schedule(t("07:00"), userVisible = false), h.lastSchedule())
    }

    @Test
    fun `missing state takes the user event against the fresh day`() = runTest {
        val h = harness(null, t("06:00"))
        h.controller.handle(Event.RemindNow)

        assertEquals(day, h.state.dayId)
        assertEquals(Phase.REMINDER, h.state.phase)
        assertEquals(WakeSource.MANUAL, h.state.wakeSource)
    }

    // --- userVisible comes from the phase ---

    @Test
    fun `schedule is user-visible exactly for WAITING, REMINDER, RINGING and SNOOZED`() = runTest {
        val cases = listOf(
            Triple(beforeWindow, t("06:00"), Phase.BEFORE_WINDOW),
            Triple(detecting, t("07:30"), Phase.DETECTING),
            Triple(waiting, t("09:00"), Phase.WAITING),
            Triple(reminder, t("07:30"), Phase.REMINDER),
            Triple(ringing, t("07:41"), Phase.RINGING),
            Triple(snoozed, t("07:45"), Phase.SNOOZED),
            Triple(pendingSilent, t("09:00"), Phase.PENDING_SILENT),
            Triple(done, t("16:00"), Phase.DONE),
        )
        val visible = setOf(Phase.WAITING, Phase.REMINDER, Phase.RINGING, Phase.SNOOZED)
        for ((state, now, phase) in cases) {
            val h = harness(state, now)
            h.controller.tick()
            assertEquals(phase, h.state.phase)
            assertEquals("$phase", phase in visible, h.lastSchedule().userVisible)
        }
    }

    @Test
    fun `DONE schedules the next rollover`() = runTest {
        val h = harness(done, t("16:00"))
        h.controller.tick()
        assertEquals(
            Call.Schedule(TimeMath.nextRolloverAt(day, Settings(), ams), userVisible = false),
            h.lastSchedule(),
        )
    }

    // --- settings and zone are read on every call ---

    @Test
    fun `a settings change moves the scheduled deadline at the next tick`() = runTest {
        val shownLate = reminder.copy(reminderShownAt = t("10:20"), firstRemindedAt = t("10:20"))
        val h = harness(shownLate, t("10:25"))
        h.controller.tick()
        assertEquals(t("10:35"), h.lastSchedule().at)

        h.settings.value = Settings(escalationDelay = Duration.ofMinutes(30))
        h.controller.tick()
        assertEquals(t("10:50"), h.lastSchedule().at)
    }

    @Test
    fun `the zone is read on every call`() = runTest {
        var zone = ams
        val log = CallLog()
        val store = FakeStateStore(log, beforeWindow)
        val controller = ReminderController(
            clock = MutableClock(t("06:30"), ams),
            zone = { zone },
            settingsRepository = FakeSettingsRepository(),
            stateStore = store,
            historyRepository = FakeHistoryRepository(log),
            wakeDetector = FakeWakeDetector(log),
            notifier = FakeNotifier(log),
            ringController = FakeRingController(log),
            alarmScheduler = FakeAlarmScheduler(log),
        )
        controller.tick()
        assertEquals(Phase.BEFORE_WINDOW, store.stored!!.phase)

        // 06:30 in Amsterdam is 07:30 in Helsinki: past earliestAt there.
        zone = ZoneId.of("Europe/Helsinki")
        controller.tick()
        assertEquals(Phase.DETECTING, store.stored!!.phase)
    }

    // --- the mutex serialises handle() ---

    @OptIn(ExperimentalCoroutinesApi::class) // runCurrent
    @Test
    fun `concurrent calls run one after the other`() = runTest {
        val h = harness(beforeWindow, t("06:00"))
        val gate = CompletableDeferred<Unit>()
        var first = true
        h.store.loadGate = {
            if (first) {
                first = false
                gate.await()
            }
        }

        val a = launch { h.controller.tick() }
        val b = launch { h.controller.handle(Event.RemindNow) }
        runCurrent()
        // The first call is suspended inside load(); the second waits for the lock.
        assertEquals(listOf("Load"), h.log.names())

        gate.complete(Unit)
        a.join()
        b.join()

        val names = h.log.names()
        val firstSchedule = names.indexOf("Schedule")
        val secondLoad = names.lastIndexOf("Load")
        assertTrue("second load after first apply: $names", secondLoad > firstSchedule)
        // The second call saw what the first one saved.
        val firstSave = h.log.calls.filterIsInstance<Call.Save>().first().state
        assertEquals(firstSave, (h.log.calls[secondLoad] as Call.Load).returned)
        assertEquals(Phase.REMINDER, h.state.phase)
    }
}
