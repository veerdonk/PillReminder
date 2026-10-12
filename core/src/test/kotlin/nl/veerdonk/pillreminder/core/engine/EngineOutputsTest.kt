package nl.veerdonk.pillreminder.core.engine

import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Outputs
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.ReminderSubtitle
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.core.model.StatusSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** Table §5: declarative outputs per phase. */
class EngineOutputsTest {
    private val rolloverAt = at("${NEXT_DAY}T03:00")

    private fun spec(
        dose: Int = 1,
        alarmStyle: Boolean = false,
        canSnooze: Boolean = true,
        snoozesLeft: Int = 3,
        subtitle: ReminderSubtitle? = null,
    ) = ReminderSpec(DAY, dose, 3, alarmStyle, canSnooze, snoozesLeft, subtitle)

    @Test
    fun `BEFORE_WINDOW wakes at earliestAt`() {
        assertEquals(Outputs(null, null, t("07:00")), outputs(beforeWindow(), t("03:00")))
    }

    @Test
    fun `DETECTING polls every detectionPollInterval`() {
        assertEquals(Outputs(null, null, t("07:05")), outputs(detecting(), t("07:00")))
    }

    @Test
    fun `DETECTING wakes at fallbackAt when that comes before the next poll`() {
        assertEquals(Outputs(null, null, t("10:30")), outputs(detecting(), t("10:28")))
    }

    @Test
    fun `WAITING wakes at dueAt`() {
        assertEquals(Outputs(null, null, t("11:50")), outputs(waiting(takenAt = t("08:00")), t("08:00")))
    }

    @Test
    fun `WAITING is capped at nextRolloverAt`() {
        val state = waiting(takenAt = t("23:30")) // due 03:20 the next morning
        assertEquals(Outputs(null, null, rolloverAt), outputs(state, t("23:30")))
    }

    @Test
    fun `REMINDER shows a normal reminder and wakes at escalateAt`() {
        assertEquals(
            Outputs(spec(dose = 2), null, t("11:40")),
            outputs(reminder(dose = 2, shownAt = t("11:25")), t("11:25")),
        )
    }

    @Test
    fun `dose 1 REMINDER before fallback wakes at fallbackAt`() {
        assertEquals(
            Outputs(spec(), null, t("10:30")),
            outputs(reminder(shownAt = t("07:25")), t("07:25")),
        )
    }

    @Test
    fun `dose 1 REMINDER wakes at escalateAt when the hold is off`() {
        val off = DEFAULTS.copy(holdFirstAlarmUntilFallback = false)
        assertEquals(t("07:40"), outputs(reminder(shownAt = t("07:25")), t("07:25"), off).nextWakeAt)
    }

    @Test
    fun `dose 1 REMINDER close to fallback still waits escalationDelay`() {
        assertEquals(t("10:35"), outputs(reminder(shownAt = t("10:20")), t("10:20")).nextWakeAt)
    }

    @Test
    fun `RINGING shows the alarm style reminder, rings and wakes at ringEndAt`() {
        assertEquals(
            Outputs(
                spec(alarmStyle = true, snoozesLeft = 2),
                RingSpec(DAY, 1, 3, canSnooze = true, snoozesLeft = 2),
                t("07:42"),
            ),
            outputs(ringing(since = t("07:40"), snoozesUsed = 1), t("07:40")),
        )
    }

    @Test
    fun `RINGING without snoozes left has no snooze`() {
        assertEquals(
            Outputs(
                spec(dose = 2, alarmStyle = true, canSnooze = false, snoozesLeft = 0),
                RingSpec(DAY, 2, 3, canSnooze = false, snoozesLeft = 0),
                t("12:02"),
            ),
            outputs(ringing(dose = 2, since = t("12:00"), snoozesUsed = 3), t("12:00")),
        )
    }

    @Test
    fun `SNOOZED shows the snooze end and wakes at snoozeEndAt`() {
        assertEquals(
            Outputs(spec(snoozesLeft = 2, subtitle = ReminderSubtitle.Snoozed(t("07:52"))), null, t("07:52")),
            outputs(snoozed(snoozedAt = t("07:42"), snoozesUsed = 1), t("07:42")),
        )
    }

    @Test
    fun `SNOOZED subtitle carries the exact snooze end`() {
        val out = outputs(snoozed(snoozedAt = t("07:42:30")), t("07:42:30"))
        assertEquals(ReminderSubtitle.Snoozed(t("07:52:30")), out.reminder!!.subtitle)
    }

    @Test
    fun `PENDING_SILENT shows the stopped subtitle and wakes at rollover`() {
        assertEquals(
            Outputs(
                spec(canSnooze = false, snoozesLeft = 0, subtitle = ReminderSubtitle.AlarmStopped),
                null,
                rolloverAt,
            ),
            outputs(pendingSilent(), t("09:00")),
        )
    }

    @Test
    fun `DONE wakes at rollover`() {
        assertEquals(Outputs(null, null, rolloverAt), outputs(done(), t("16:00")))
    }

    @Test
    fun `doseCount is the current dosesPerDay`() {
        val out = outputs(reminder(), t("07:25"), DEFAULTS.copy(dosesPerDay = 4))
        assertEquals(4, out.reminder!!.doseCount)
    }

    @Test
    fun `an overdue deadline is scheduled one second from now`() {
        val now = t("12:00")
        assertEquals(now.plusSeconds(1), outputs(reminder(dose = 2, shownAt = t("11:00")), now).nextWakeAt)
    }

    @Test
    fun `a deadline equal to now is scheduled one second from now`() {
        val now = t("11:40")
        assertEquals(now.plusSeconds(1), outputs(reminder(dose = 2, shownAt = t("11:25")), now).nextWakeAt)
    }

    @Test
    fun `a corrupt state without its base timestamp wakes one second from now`() {
        val now = t("08:00")
        for (state in listOf(
            reminder().copy(reminderShownAt = null),
            ringing().copy(ringingSince = null),
            snoozed().copy(snoozedAt = null),
            waiting().copy(doses = emptyList()),
        )) {
            assertEquals("phase ${state.phase}", now.plusSeconds(1), outputs(state, now).nextWakeAt)
        }
    }

    @Test
    fun `a state whose day has already ended wakes one second from now`() {
        val now = at("${NEXT_DAY}T05:00")
        assertEquals(now.plusSeconds(1), outputs(done(), now).nextWakeAt)
    }

    @Test
    fun `nextRolloverAt uses the passed zone`() {
        val utc = ZoneId.of("UTC")
        val out = Engine.outputs(done(), DEFAULTS, t("16:00"), utc)
        assertEquals(java.time.Instant.parse("${NEXT_DAY}T03:00:00Z"), out.nextWakeAt)
    }

    // Status notification (FR-2.4)

    private val withStatus = DEFAULTS.copy(showStatusNotification = true)

    @Test
    fun `status is off by default`() {
        assertEquals(null, outputs(waiting(takenAt = t("08:00")), t("08:00")).status)
        assertEquals(null, outputs(done(), t("20:00")).status)
    }

    @Test
    fun `status counts down to dueAt while WAITING`() {
        val out = outputs(waiting(takenAt = t("08:00")), t("08:00"), withStatus)
        assertEquals(Outputs(null, null, t("11:50"), StatusSpec.NextDose(2, 3, t("11:50"))), out)
    }

    @Test
    fun `status waits for wake-up before dose 1`() {
        val expected = StatusSpec.WaitingForWake(3, t("10:30"))
        assertEquals(expected, outputs(beforeWindow(), t("03:00"), withStatus).status)
        assertEquals(expected, outputs(detecting(), t("07:00"), withStatus).status)
    }

    @Test
    fun `status is gone in DONE`() {
        assertEquals(null, outputs(done(), t("20:00"), withStatus).status)
        val skipped = step(waiting(takenAt = t("08:00")), Event.SkipRest(2), t("09:00"), withStatus)
        assertEquals(Phase.DONE, skipped.phase)
        assertEquals(null, outputs(skipped, t("09:00"), withStatus).status)
    }

    @Test
    fun `status is null while a reminder or alarm shows`() {
        for (state in listOf(reminder(dose = 2, shownAt = t("11:25")), ringing(), snoozed(), heldDriving(), pendingSilent())) {
            assertEquals(state.phase.name, null, outputs(state, t("11:30"), withStatus).status)
        }
    }

    @Test
    fun `status does not change nextWakeAt`() {
        for (state in listOf(beforeWindow(), detecting(), waiting(takenAt = t("08:00")), done())) {
            assertEquals(outputs(state, t("07:00")).nextWakeAt, outputs(state, t("07:00"), withStatus).nextWakeAt)
        }
    }
}
