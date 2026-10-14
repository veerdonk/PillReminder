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
    val status: StatusSpec? = null, // persistent status notification (FR-2.4), or null = cancel
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
    /** Set by the controller from the pill count, not by `Engine.outputs` (FR-10.3). */
    val lowStock: LowStock? = null,
    /** Set by the controller from `Settings.pharmacyPhone`, for the out-of-pills reminder (FR-10.6). */
    val pharmacyPhone: String? = null,
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

    /** "Alarm held while you drive" (HELD_DRIVING, FR-3.8). */
    @Serializable
    @SerialName("heldWhileDriving")
    data object HeldWhileDriving : ReminderSubtitle
}

@Serializable
data class RingSpec(
    val dayId: LocalDate,
    val dose: Int,
    val doseCount: Int,
    val canSnooze: Boolean,
    val snoozesLeft: Int,
)

/**
 * The persistent status notification shown while no reminder or alarm is (FR-2.4). Only set
 * when `Settings.showStatusNotification` is on. Typed rather than text, like [ReminderSubtitle].
 */
sealed interface StatusSpec {
    /** WAITING: pill [dose] of [doseCount] is due at [dueAt]; `:app` counts down to it. */
    data class NextDose(val dose: Int, val doseCount: Int, val dueAt: Instant) : StatusSpec

    /** BEFORE_WINDOW, DETECTING: pill 1 comes once you're up, at [fallbackAt] at the latest. */
    data class WaitingForWake(val doseCount: Int, val fallbackAt: Instant) : StatusSpec

    /**
     * DONE with under `PillStock.CALL_PHARMACY_DAYS` days of pills left: only the "call the
     * pharmacy" warning (FR-10.6). Set by the controller from the pill count, not by
     * `Engine.outputs`; [pharmacyPhone] adds a "Call pharmacy" button.
     */
    data class CallPharmacy(val lowStock: LowStock, val pharmacyPhone: String?) : StatusSpec
}
