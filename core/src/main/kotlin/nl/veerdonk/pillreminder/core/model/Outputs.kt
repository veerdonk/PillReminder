@file:UseSerializers(LocalDateSerializer::class)

package nl.veerdonk.pillreminder.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
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
    val subtitle: String?,
)

@Serializable
data class RingSpec(
    val dayId: LocalDate,
    val dose: Int,
    val doseCount: Int,
    val canSnooze: Boolean,
    val snoozesLeft: Int,
)
