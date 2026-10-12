package nl.veerdonk.pillreminder.ui.home

import nl.veerdonk.pillreminder.core.engine.Engine
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.model.WakeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class HomeModelTest {
    private val zone: ZoneId = ZoneId.of("Europe/Amsterdam")
    private val s = Settings()
    private val day: LocalDate = LocalDate.parse("2026-10-13") // Tuesday

    private fun at(time: String, date: LocalDate = day): Instant =
        LocalDateTime.of(date, LocalTime.parse(time)).atZone(zone).toInstant()

    private fun taken(index: Int, time: String) =
        DoseRecord(index, DoseStatus.TAKEN, at(time), DoseSource.NOTIFICATION, 0, null)

    private fun record(index: Int, status: DoseStatus) = DoseRecord(index, status, null, null, 0, null)

    private fun state(phase: Phase, next: Int = 1, doses: List<DoseRecord> = emptyList()) =
        Engine.fresh(day).copy(phase = phase, nextDose = next, doses = doses)

    // ---- backdating ----

    @Test fun mapsTimeOfDayToSameDay() {
        assertEquals(at("08:15"), backdatedInstant(LocalTime.parse("08:15"), s, at("12:00"), zone))
    }

    @Test fun timeBeforeRolloverAfterMidnightBelongsToNextCalendarDate() {
        // 00:30 on 14 Oct is still app-day 13 Oct.
        val now = at("00:45", day.plusDays(1))
        assertEquals(at("00:30", day.plusDays(1)), backdatedInstant(LocalTime.parse("00:30"), s, now, zone))
    }

    @Test fun afterMidnightNowStillMapsEveningTimesToPreviousDate() {
        val now = at("01:00", day.plusDays(1))
        assertEquals(at("23:30"), backdatedInstant(LocalTime.parse("23:30"), s, now, zone))
    }

    @Test fun rejectsFutureTime() {
        val st = state(Phase.REMINDER)
        val r = resolveBackdate(LocalTime.parse("12:30"), st, s, at("12:00"), zone)
        assertEquals(BackdateResult.Invalid(BackdateError.IN_FUTURE, at("12:00")), r)
    }

    @Test fun morningTimePickedAtEveningIsNotMovedToTomorrow() {
        // 02:00 is before the 03:00 rollover, so it maps to tomorrow 02:00: in the future.
        val r = resolveBackdate(LocalTime.parse("02:00"), state(Phase.REMINDER), s, at("22:00"), zone)
        assertTrue(r is BackdateResult.Invalid && r.error == BackdateError.IN_FUTURE)
    }

    @Test fun acceptsTimeEqualToNow() {
        val r = resolveBackdate(LocalTime.parse("12:00"), state(Phase.REMINDER), s, at("12:00"), zone)
        assertEquals(BackdateResult.Valid(at("12:00")), r)
    }

    @Test fun rejectsBeforePreviousDose() {
        val st = state(Phase.REMINDER, next = 2, doses = listOf(taken(1, "08:00")))
        val r = resolveBackdate(LocalTime.parse("07:59"), st, s, at("12:00"), zone)
        assertEquals(BackdateResult.Invalid(BackdateError.BEFORE_PREVIOUS_DOSE, at("08:00")), r)
    }

    @Test fun acceptsEqualToPreviousDose() {
        val st = state(Phase.REMINDER, next = 2, doses = listOf(taken(1, "08:00")))
        assertEquals(
            BackdateResult.Valid(at("08:00")),
            resolveBackdate(LocalTime.parse("08:00"), st, s, at("12:00"), zone),
        )
    }

    @Test fun doseOneRejectedBeforeStartOfDay() {
        // With the default 03:00 rollover every same-day pick is after the start, so use 05:00.
        val settings = s.copy(dayRolloverTime = LocalTime.of(5, 0), weekdayEarliestTime = LocalTime.of(7, 0))
        val st = state(Phase.DETECTING)
        val direct = validateBackdate(at("04:00"), st, settings, at("12:00"), zone)
        assertEquals(BackdateResult.Invalid(BackdateError.BEFORE_START_OF_DAY, at("05:00")), direct)
        assertEquals(BackdateResult.Valid(at("05:00")), validateBackdate(at("05:00"), st, settings, at("12:00"), zone))
    }

    // ---- dose rows ----

    @Test fun rowsBeforeDoseOneAreUpcomingWithoutTime() {
        val rows = doseRows(state(Phase.DETECTING), s, at("08:00"))
        assertEquals(listOf(1, 2, 3), rows.map { it.index })
        assertTrue(rows.all { it.status == DoseRowStatus.Upcoming(null) })
    }

    @Test fun rowsWaitingShowTakenAndEstimates() {
        val st = state(Phase.WAITING, next = 2, doses = listOf(taken(1, "08:00")))
        val rows = doseRows(st, s, at("09:00"))
        assertEquals(DoseRowStatus.Taken(at("08:00")), rows[0].status)
        assertEquals(DoseRowStatus.Upcoming(at("11:50")), rows[1].status)
        assertEquals(DoseRowStatus.Upcoming(at("15:40")), rows[2].status)
    }

    @Test fun rowsReminderPhasesMarkNextDosePending() {
        for (phase in listOf(Phase.REMINDER, Phase.RINGING, Phase.SNOOZED, Phase.PENDING_SILENT)) {
            val st = state(phase, next = 2, doses = listOf(taken(1, "08:00")))
            val rows = doseRows(st, s, at("12:00"))
            assertEquals(phase.name, DoseRowStatus.Pending, rows[1].status)
            assertEquals(phase.name, DoseRowStatus.Upcoming(null), rows[2].status)
        }
    }

    @Test fun rowsDoneShowSkippedAndMissed() {
        val st = state(
            Phase.DONE,
            doses = listOf(taken(1, "08:00"), record(2, DoseStatus.SKIPPED), record(3, DoseStatus.MISSED)),
        )
        val rows = doseRows(st, s, at("20:00"))
        assertEquals(
            listOf(DoseRowStatus.Taken(at("08:00")), DoseRowStatus.Skipped, DoseRowStatus.Missed),
            rows.map { it.status },
        )
    }

    // ---- next action ----

    private fun next(st: DayState, now: String) = nextAction(st, s, at(now), zone)

    @Test fun nextActionPerPhase() {
        assertEquals(NextAction.DetectionStartsAt(at("07:00")), next(state(Phase.BEFORE_WINDOW), "05:00"))
        assertEquals(NextAction.ReminderByFallback(at("10:30")), next(state(Phase.DETECTING), "08:00"))
        val waiting = state(Phase.WAITING, next = 2, doses = listOf(taken(1, "08:00")))
        assertEquals(NextAction.ReminderAt(at("11:50")), next(waiting, "09:00"))
        val reminder = state(Phase.REMINDER).copy(reminderShownAt = at("11:50"))
        assertEquals(NextAction.AlarmIn(6), next(reminder, "11:59")) // escalates 12:05
        assertEquals(NextAction.AlarmIn(1), next(reminder, "12:04"))
        val ringing = state(Phase.RINGING).copy(ringingSince = at("12:05"))
        assertEquals(NextAction.Ringing, next(ringing, "12:06"))
        val snoozed = state(Phase.SNOOZED).copy(snoozedAt = at("09:10"), snoozesUsed = 1)
        assertEquals(NextAction.SnoozedUntil(at("09:20")), next(snoozed, "09:11"))
        assertEquals(NextAction.NoMoreAlarms, next(state(Phase.PENDING_SILENT), "13:00"))
        assertEquals(NextAction.HeldWhileDriving, next(state(Phase.HELD_DRIVING), "13:00"))
        assertEquals(NextAction.DoneUntil(at("03:00", day.plusDays(1))), next(state(Phase.DONE), "20:00"))
    }

    @Test fun escalationDelayChangeMovesAlarmLine() {
        val reminder = state(Phase.REMINDER).copy(reminderShownAt = at("11:50"))
        val longer = nextAction(reminder, s.copy(escalationDelay = java.time.Duration.ofMinutes(30)), at("11:59"), zone)
        assertEquals(NextAction.AlarmIn(21), longer)
    }

    @Test fun alarmMoreThanAnHourAwayIsShownAsATime() {
        // Dose 1 detected at 07:25 is held until the 10:30 fallback (FR-3.1a).
        val held = state(Phase.REMINDER).copy(
            wakeAt = at("07:25"),
            wakeSource = WakeSource.DETECTED,
            reminderShownAt = at("07:25"),
        )
        assertEquals(NextAction.AlarmAt(at("10:30")), next(held, "07:25"))
        assertEquals(NextAction.AlarmAt(at("10:30")), next(held, "09:29"))
        assertEquals(NextAction.AlarmIn(60), next(held, "09:30"))
        assertEquals(NextAction.AlarmIn(1), next(held, "10:29"))
    }

    @Test fun minutesUntilRoundsUpAndIsAtLeastOne() {
        val now = at("12:00")
        assertEquals(1, minutesUntil(now, now))
        assertEquals(1, minutesUntil(now.plusSeconds(30), now))
        assertEquals(1, minutesUntil(now.plusSeconds(60), now))
        assertEquals(2, minutesUntil(now.plusSeconds(61), now))
    }

    // ---- actions per phase (02 §4.3) ----

    @Test fun actionsPerPhase() {
        val noDoses = Phase.entries.associateWith { homeActions(state(it)) }
        for (phase in Phase.entries - Phase.DONE) {
            assertEquals(phase.name, 1, noDoses.getValue(phase).recordDose)
            assertEquals(phase.name, SkipKind.NOT_TODAY, noDoses.getValue(phase).skip)
            assertTrue(phase.name, !noDoses.getValue(phase).resumeDay)
        }
        val remindNow = setOf(Phase.BEFORE_WINDOW, Phase.DETECTING, Phase.WAITING, Phase.PENDING_SILENT)
        for (phase in Phase.entries) {
            assertEquals(phase.name, phase in remindNow, noDoses.getValue(phase).remindNow)
        }
        val done = noDoses.getValue(Phase.DONE)
        assertNull(done.recordDose)
        assertNull(done.skip)
    }

    @Test fun laterDosesOfferDoneForToday() {
        val st = state(Phase.REMINDER, next = 2, doses = listOf(taken(1, "08:00")))
        val a = homeActions(st)
        assertEquals(2, a.recordDose)
        assertEquals(SkipKind.DONE_FOR_TODAY, a.skip)
    }

    @Test fun resumeOnlyWhenDoneWithSkippedDose() {
        assertTrue(homeActions(state(Phase.DONE, doses = listOf(record(1, DoseStatus.SKIPPED)))).resumeDay)
        assertTrue(!homeActions(state(Phase.DONE, doses = listOf(taken(1, "08:00")))).resumeDay)
        assertTrue(!homeActions(state(Phase.DONE, doses = listOf(record(1, DoseStatus.MISSED)))).resumeDay)
    }

    // ---- model ----

    @Test fun detectionInfoOnlyBeforeDoseOne() {
        val info = detectionInfo(state(Phase.DETECTING), s, zone)
        assertEquals(DetectionInfo(at("07:00"), at("10:30")), info)
        assertNull(detectionInfo(state(Phase.REMINDER), s, zone))
    }

    @Test fun staleStateIsShownRolledOver() {
        val old = state(Phase.REMINDER).copy(reminderShownAt = at("20:00"))
        val model = buildHomeModel(old, s, at("08:00", day.plusDays(1)), zone)
        assertEquals(day.plusDays(1), model.state.dayId)
        assertEquals(Phase.DETECTING, model.state.phase)
    }

    @Test fun nullStateIsAFreshDay() {
        val model = buildHomeModel(null, s, at("05:00"), zone)
        assertEquals(Phase.BEFORE_WINDOW, model.state.phase)
        assertEquals(3, model.rows.size)
    }
}
