package nl.veerdonk.pillreminder.core.serial

import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.DoseRecord
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.DoseStatus
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.ReminderSubtitle
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.model.WakeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class JsonRoundTripTest {
    private fun t(text: String) = Instant.parse(text)

    @Test
    fun `fully populated DayState round-trips`() {
        val state = DayState(
            dayId = LocalDate.of(2026, 10, 12),
            phase = Phase.SNOOZED,
            nextDose = 2,
            doses = listOf(
                DoseRecord(1, DoseStatus.TAKEN, t("2026-10-12T05:52:00Z"), DoseSource.ALARM, 1, t("2026-10-12T05:25:00Z")),
            ),
            wakeAt = t("2026-10-12T05:25:00Z"),
            wakeSource = WakeSource.DETECTED,
            reminderShownAt = t("2026-10-12T09:42:00Z"),
            firstRemindedAt = t("2026-10-12T09:42:00Z"),
            ringingSince = null,
            snoozedAt = t("2026-10-12T09:59:00.123456789Z"),
            snoozesUsed = 1,
        )
        val json = CoreJson.encodeToString(DayState.serializer(), state)
        assertEquals(state, CoreJson.decodeFromString(DayState.serializer(), json))
        assertTrue(json, json.contains("\"dayId\":\"2026-10-12\""))
        assertTrue(json, json.contains("\"phase\":\"SNOOZED\""))
    }

    @Test
    fun `fresh DayState round-trips`() {
        val state = DayState(LocalDate.of(2026, 10, 12), Phase.BEFORE_WINDOW, 1, emptyList())
        val json = CoreJson.encodeToString(DayState.serializer(), state)
        assertEquals(state, CoreJson.decodeFromString(DayState.serializer(), json))
    }

    @Test
    fun `unknown keys and missing defaults are tolerated`() {
        val json = """{"dayId":"2026-10-12","phase":"DONE","nextDose":4,"doses":[],"futureField":42}"""
        val state = CoreJson.decodeFromString(DayState.serializer(), json)
        assertEquals(DayState(LocalDate.of(2026, 10, 12), Phase.DONE, 4, emptyList()), state)
    }

    @Test
    fun `Settings round-trip with ISO durations and times`() {
        val settings = Settings(
            weekdayEarliestTime = LocalTime.of(6, 45),
            doseInterval = Duration.ofMinutes(230),
            alarmSoundUri = "content://media/internal/audio/media/42",
        )
        val json = CoreJson.encodeToString(Settings.serializer(), settings)
        assertEquals(settings, CoreJson.decodeFromString(Settings.serializer(), json))
        assertTrue(json, json.contains("\"doseInterval\":\"PT3H50M\""))
        assertTrue(json, json.contains("\"weekdayEarliestTime\":\"06:45\""))
    }

    @Test
    fun `ReminderSpec round-trips`() {
        val subtitles = listOf(
            null,
            ReminderSubtitle.Snoozed(Instant.parse("2026-10-12T07:20:00Z")),
            ReminderSubtitle.AlarmStopped,
        )
        for (subtitle in subtitles) {
            val spec = ReminderSpec(LocalDate.of(2026, 10, 12), 2, 3, false, true, 2, subtitle)
            val json = CoreJson.encodeToString(ReminderSpec.serializer(), spec)
            assertEquals(spec, CoreJson.decodeFromString(ReminderSpec.serializer(), json))
        }
    }
}
