package nl.veerdonk.pillreminder.core.engine

import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.WakeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** End-to-end scenarios built from single reduce steps. */
class EngineScenarioTest {

    @Test
    fun `worked example from 02 section 7`() {
        // Yesterday was completed; the 03:00 rollover tick starts today.
        val yesterday = done().copy(dayId = DAY.minusDays(1))
        val rollover = reduce(yesterday, tick, t("03:00"))
        assertEquals(yesterday, rollover.finishedDay)
        var s: DayState = rollover.state
        assertEquals(Phase.BEFORE_WINDOW, s.phase)
        assertEquals(t("07:00"), outputs(s, t("03:00")).nextWakeAt)

        s = step(s, tick, t("07:00"))
        assertEquals(Phase.DETECTING, s.phase)
        assertEquals(t("07:05"), outputs(s, t("07:00")).nextWakeAt)

        s = step(s, tickAwake, t("07:25"))
        assertEquals(Phase.REMINDER, s.phase)
        assertEquals(WakeSource.DETECTED, s.wakeSource)
        assertEquals(t("07:40"), outputs(s, t("07:25")).nextWakeAt)

        s = step(s, tick, t("07:40"))
        assertEquals(Phase.RINGING, s.phase)
        assertEquals(t("07:42"), outputs(s, t("07:40")).nextWakeAt)

        s = step(s, tick, t("07:42"))
        assertEquals(Phase.SNOOZED, s.phase)
        assertEquals(1, s.snoozesUsed)
        assertEquals("Snoozed until 07:52", outputs(s, t("07:42")).reminder!!.subtitle)

        s = step(s, tick, t("07:52"))
        assertEquals(Phase.RINGING, s.phase)
        s = step(s, Event.TookDose(1, t("07:52"), DoseSource.ALARM), t("07:52"))
        assertEquals(Phase.WAITING, s.phase)
        assertEquals(2, s.nextDose)
        assertEquals(t("11:42"), outputs(s, t("07:52")).nextWakeAt)

        s = step(s, tick, t("11:42"))
        assertEquals(Phase.REMINDER, s.phase)
        assertEquals(2, outputs(s, t("11:42")).reminder!!.dose)

        s = step(s, Event.TookDose(2, t("11:50"), DoseSource.NOTIFICATION), t("11:50"))
        assertEquals(Phase.WAITING, s.phase)
        assertEquals(t("15:40"), outputs(s, t("11:50")).nextWakeAt)

        s = step(s, tick, t("15:40"))
        assertEquals(Phase.REMINDER, s.phase)
        s = step(s, Event.TookDose(3, t("15:41"), DoseSource.NOTIFICATION), t("15:41"))
        assertEquals(Phase.DONE, s.phase)
        assertEquals(at("${NEXT_DAY}T03:00"), outputs(s, t("15:41")).nextWakeAt)

        assertEquals(
            listOf(
                DoseRecord(1, DoseStatus.TAKEN, t("07:52"), DoseSource.ALARM, 1, t("07:25")),
                DoseRecord(2, DoseStatus.TAKEN, t("11:50"), DoseSource.NOTIFICATION, 0, t("11:42")),
                DoseRecord(3, DoseStatus.TAKEN, t("15:41"), DoseSource.NOTIFICATION, 0, t("15:40")),
            ),
            s.doses,
        )
        assertEquals(t("07:25"), s.wakeAt)
    }

    @Test
    fun `dose 1 with three timeouts goes PENDING_SILENT, then TookDose goes to WAITING`() {
        var s = step(detecting(), tick, t("10:30")) // fallback reminder
        assertEquals(Phase.REMINDER, s.phase)
        assertEquals(WakeSource.FALLBACK, s.wakeSource)

        // reminder 10:30, ring 10:45-10:47, then three snoozes of 10 min, each ringing 2 min.
        s = step(s, tick, t("10:45"))
        assertEquals(Phase.RINGING, s.phase)
        var ringStart = t("10:45")
        for (snooze in 1..3) {
            s = step(s, tick, ringStart.plusSeconds(120))
            assertEquals(Phase.SNOOZED, s.phase)
            assertEquals(snooze, s.snoozesUsed)
            ringStart = ringStart.plusSeconds(120 + 600)
            s = step(s, tick, ringStart)
            assertEquals(Phase.RINGING, s.phase)
            assertEquals(snooze < 3, outputs(s, ringStart).ringing!!.canSnooze)
        }
        // The last ring times out with no snoozes left.
        s = step(s, tick, ringStart.plusSeconds(120))
        assertEquals(Phase.PENDING_SILENT, s.phase)
        val out = outputs(s, ringStart.plusSeconds(120))
        assertNull(out.ringing)
        assertNotNull(out.reminder)
        assertEquals(at("${NEXT_DAY}T03:00"), out.nextWakeAt)

        // Nothing rings again.
        assertEquals(s, step(s, tick, t("20:00")))

        s = step(s, Event.TookDose(1, t("20:00"), DoseSource.NOTIFICATION), t("20:00"))
        assertEquals(Phase.WAITING, s.phase)
        assertEquals(2, s.nextDose)
        assertEquals(3, s.doses.single().snoozesUsed)
        assertEquals(0, s.snoozesUsed)
    }

    @Test
    fun `backdated TookDose whose at plus interval is past goes straight to REMINDER`() {
        val dose2 = reminder(dose = 2, shownAt = t("11:30"))
        val last = dose2.doses.single().takenAt!! // 08:30
        // Dose 2 actually taken at 09:00, recorded at 13:00; dose 3 was due at 12:50.
        val s = step(dose2, Event.TookDose(2, t("09:00"), DoseSource.APP_BACKDATED), t("13:00"))
        assertTrue(t("09:00") > last)
        assertEquals(Phase.REMINDER, s.phase)
        assertEquals(3, s.nextDose)
        assertEquals(t("13:00"), s.reminderShownAt)
        assertEquals(t("13:15"), outputs(s, t("13:00")).nextWakeAt)
    }

    @Test
    fun `Not today then Resume today brings back the dose 1 reminder`() {
        var s = step(beforeWindow(), tick, t("10:30"))
        s = step(s, Event.SkipRest(1), t("10:31"))
        assertEquals(Phase.DONE, s.phase)
        assertTrue(s.doses.all { it.status == DoseStatus.SKIPPED })
        s = step(s, Event.ResumeDay, t("11:00"))
        assertEquals(Phase.REMINDER, s.phase)
        assertEquals(1, outputs(s, t("11:00")).reminder!!.dose)
    }

    @Test
    fun `engine sources have no Android imports and read no system clock or zone`() {
        val dir = File("src/main/kotlin/nl/veerdonk/pillreminder/core/engine")
        val sources = dir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("no engine sources found in ${dir.absolutePath}", sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            assertFalse(file.name, Regex("""^import\s+android""", RegexOption.MULTILINE).containsMatchIn(text))
            for (forbidden in listOf("Instant.now", "Clock.", "systemDefault", "LocalDate.now", "System.currentTimeMillis")) {
                assertFalse("${file.name} uses $forbidden", text.contains(forbidden))
            }
        }
    }
}
