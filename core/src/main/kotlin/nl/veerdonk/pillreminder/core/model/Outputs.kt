@file:UseSerializers(LocalDateSerializer::class, InstantSerializer::class)

package nl.veerdonk.pillreminder.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import nl.veerdonk.pillreminder.core.serial.InstantSerializer
import nl.veerdonk.pillreminder.core.serial.LocalDateSerializer
import java.time.Instant
import java.time.LocalDate

/** What the Android layer must make true right now (02-state-machine §5). */
data class Outputs(
    val reminder: ReminderSpec?, // ongoing notification to show, or null = cancel
    val ringing: RingSpec?, // alarm must be ringing, or null = must be stopped
    val nextWakeAt: Instant, // exact alarm to schedule
)

@Serializable
data class ReminderSpec(
    val dayId: LocalDate,
    val dose: Int,
    val doseCount: Int,
    val alarmStyle: Boolean,
    val canSnooze: Boolean,
    val snoozesLeft: Int,
    val subtitle: ReminderSubtitle?,
)

/**
 * Extra line on the reminder notification. Typed rather than text, so `:app` can word it from
 * `strings.xml` (NFR-5, assumption A9).
 */
@Serializable
sealed interface ReminderSubtitle {
    /** "Snoozed until HH:MM": [until] is the snooze end, formatted by `:app` in the device zone. */
    @Serializable
    @SerialName("snoozed")
    data class Snoozed(val until: Instant) : ReminderSubtitle

    /** "Alarm stopped — please take your pill" (snoozes exhausted). */
    @Serializable
    @SerialName("alarmStopped")
    data object AlarmStopped : ReminderSubtitle
}

@Serializable
data class RingSpec(
    val dayId: LocalDate,
    val dose: Int,
    val doseCount: Int,
    val canSnooze: Boolean,
    val snoozesLeft: Int,
)
