@file:UseSerializers(InstantSerializer::class, LocalDateSerializer::class)

package nl.veerdonk.pillreminder.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import nl.veerdonk.pillreminder.core.serial.InstantSerializer
import nl.veerdonk.pillreminder.core.serial.LocalDateSerializer
import java.time.Instant
import java.time.LocalDate

/** Phases of the daily state machine (02-state-machine §2). */
@Serializable
enum class Phase {
    BEFORE_WINDOW, // day started (after rollover), waiting for earliestTime
    DETECTING, // inside wake window, polling usage log
    WAITING, // dose N-1 taken, waiting doseInterval before dose N reminder
    REMINDER, // dose N reminder notification visible, escalation timer running
    RINGING, // dose N alarm ringing
    SNOOZED, // dose N alarm snoozed
    PENDING_SILENT, // dose N: snoozes exhausted, ongoing notification only, no more alarms
    DONE, // all doses taken, or rest of day skipped
}

@Serializable
enum class DoseStatus { TAKEN, SKIPPED, MISSED }

@Serializable
enum class DoseSource { NOTIFICATION, ALARM, APP_NOW, APP_BACKDATED }

@Serializable
enum class WakeSource { DETECTED, FALLBACK, MANUAL }

/** A finalised dose of the day. */
@Serializable
data class DoseRecord(
    val index: Int, // 1-based
    val status: DoseStatus,
    val takenAt: Instant?, // only for TAKEN
    val source: DoseSource?, // only for TAKEN
    val snoozesUsed: Int,
    val firstRemindedAt: Instant?, // when the reminder for this dose first appeared
)

/** The single persisted state of the current app-day (02-state-machine §2). */
@Serializable
data class DayState(
    val dayId: LocalDate, // calendar date on which this app-day started
    val phase: Phase,
    val nextDose: Int, // dose that is next/pending (1..dosesPerDay); irrelevant in DONE
    val doses: List<DoseRecord>, // finalised doses, ordered by index
    val wakeAt: Instant? = null,
    val wakeSource: WakeSource? = null,
    // Base timestamps for the current dose (reset when a new dose becomes current):
    val reminderShownAt: Instant? = null, // set when entering REMINDER
    val firstRemindedAt: Instant? = null, // first time REMINDER was entered for nextDose
    val ringingSince: Instant? = null, // set when entering RINGING
    val snoozedAt: Instant? = null, // set when entering SNOOZED
    val snoozesUsed: Int = 0,
)
