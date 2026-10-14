package nl.veerdonk.pillreminder.controller

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.veerdonk.pillreminder.core.engine.Engine
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.LowStock
import nl.veerdonk.pillreminder.core.model.Outputs
import nl.veerdonk.pillreminder.core.model.Phase
import nl.veerdonk.pillreminder.core.model.PillStock
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.model.StatusSpec
import nl.veerdonk.pillreminder.core.time.TimeMath
import nl.veerdonk.pillreminder.data.PillStockRepository
import nl.veerdonk.pillreminder.data.SettingsRepository
import nl.veerdonk.pillreminder.data.StateStore
import nl.veerdonk.pillreminder.data.history.HistoryRepository
import nl.veerdonk.pillreminder.platform.AlarmScheduler
import nl.veerdonk.pillreminder.platform.NavigationDetector
import nl.veerdonk.pillreminder.platform.Notifier
import nl.veerdonk.pillreminder.platform.RingController
import nl.veerdonk.pillreminder.platform.WakeDetector
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single entry point for events (docs/02-state-machine.md §6). Each call loads the state,
 * reduces the event, persists the result, writes history and then reconciles the platform
 * against the engine's outputs. Calls are serialised by a [Mutex], so every event sees the
 * state the previous one saved.
 *
 * Time comes from [clock] and the zone from [zone], read on every call (in production
 * `ZoneId.systemDefault()`, so a time zone change takes effect at the next event).
 */
class ReminderController(
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val settingsRepository: SettingsRepository,
    private val stateStore: StateStore,
    private val historyRepository: HistoryRepository,
    private val wakeDetector: WakeDetector,
    private val navigationDetector: NavigationDetector,
    private val notifier: Notifier,
    private val ringController: RingController,
    private val alarmScheduler: AlarmScheduler,
    private val pillStock: PillStockRepository,
) {
    private val mutex = Mutex()

    /**
     * Processes [event]. When [expectedDayId] is set (actions from a notification carry the day
     * they were posted for, design §7), the event is dropped if that isn't the current app-day
     * after the rollover check, i.e. `dayIdOf(now)`. A dropped event changes nothing: no state
     * is saved, no history is written and the platform is not touched.
     */
    suspend fun handle(event: Event, expectedDayId: LocalDate? = null) {
        mutex.withLock {
            val now = clock.instant()
            val zone = zone()
            val settings = settingsRepository.current()
            val today = TimeMath.dayIdOf(now, settings, zone)
            // Reduce rolls any older (or newer) state over to `today` first, so after the
            // rollover check the state's dayId is always `today`.
            if (expectedDayId != null && expectedDayId != today) return

            val state = stateStore.load() ?: Engine.fresh(today)

            // Detection only runs for a Tick that finds the day in DETECTING. A state that is
            // about to roll over is not "in DETECTING" for this purpose: its window is a past day's.
            val detectNow = event is Event.Tick && state.phase == Phase.DETECTING && state.dayId == today
            // Navigation is a cheap in-memory check, so every Tick carries it (FR-3.8).
            val navigating = event is Event.Tick && navigationDetector.isNavigating()
            val input = if (event is Event.Tick) {
                Event.Tick(detectNow && isAwake(today, settings, now, zone), navigating)
            } else {
                event
            }
            var t = Engine.reduce(state, input, settings, now, zone)
            // Second-tick rule (02 §4): the Tick moved the day into DETECTING, but the engine
            // ignores wakeDetected on that move, so detect now and tick once more. The same
            // `now` can't roll over again, so the first reduce's finishedDay stands.
            if (event is Event.Tick && !detectNow && t.state.phase == Phase.DETECTING) {
                val detected = isAwake(today, settings, now, zone)
                t = t.copy(state = Engine.reduce(t.state, Event.Tick(detected, navigating), settings, now, zone).state)
            }

            t.finishedDay?.let { historyRepository.upsert(it) }
            stateStore.save(t.state)
            historyRepository.upsert(t.state) // keep today's history row live
            // Every dose newly recorded as taken uses one pill (FR-10.2).
            val taken = PillStock.pillsTaken(state, t.state)
            if (taken > 0) pillStock.take(taken)
            val lowStock = PillStock.lowStock(pillStock.current(), settings.dosesPerDay)
            val outputs = Engine.outputs(t.state, settings, now, zone)
            apply(
                outputs.copy(
                    reminder = outputs.reminder?.copy(lowStock = lowStock, pharmacyPhone = settings.pharmacyPhone),
                    status = outputs.status ?: callPharmacyStatus(t.state, settings, lowStock),
                ),
                t.state,
            )
        }
    }

    /**
     * Once the day is done the status notification would go away (FR-2.4), but with under
     * `PillStock.CALL_PHARMACY_DAYS` days of pills left it stays up with only the "call the
     * pharmacy" warning (FR-10.6).
     */
    private fun callPharmacyStatus(state: DayState, settings: Settings, lowStock: LowStock?): StatusSpec? {
        if (!settings.showStatusNotification || state.phase != Phase.DONE) return null
        if (lowStock == null || !PillStock.shouldCallPharmacy(lowStock)) return null
        return StatusSpec.CallPharmacy(lowStock, settings.pharmacyPhone)
    }

    /** A time-based Tick. `wakeDetected` and `navigating` are filled in by [handle]. */
    suspend fun tick() = handle(Event.Tick(false))

    /**
     * Detection window (FR-1.3): events since `earliestAt` and within the last
     * `detectionWindow`, up to now.
     */
    private suspend fun isAwake(day: LocalDate, settings: Settings, now: Instant, zone: ZoneId): Boolean {
        val from = maxOf(TimeMath.earliestAt(day, settings, zone), now - settings.detectionWindow)
        return wakeDetector.isAwake(from, now, settings)
    }

    /**
     * Reconciles the platform with [outputs], in the order of design §8. Every step is
     * idempotent, so running it again with the same outputs changes nothing.
     */
    private fun apply(outputs: Outputs, state: DayState) {
        val ringing = outputs.ringing
        // The status notification is null whenever a reminder or the alarm shows. It is cancelled
        // before and shown after the others, so only one pill notification is visible (FR-2.4).
        val status = outputs.status
        if (status == null) notifier.cancelStatus()
        if (ringing == null) {
            ringController.ensureStopped()
            val reminder = outputs.reminder
            if (reminder != null) notifier.showReminder(reminder) else notifier.cancelReminder()
        } else {
            // The alarm has its own notification ID; remove the reminder so only one pill
            // notification is visible (design §7, 01 A26).
            notifier.cancelReminder()
            ringController.ensureRinging(ringing)
        }
        if (status != null) notifier.showStatus(status)
        alarmScheduler.schedule(outputs.nextWakeAt, userVisible = state.phase in USER_VISIBLE_PHASES)
    }

    private companion object {
        /**
         * Phases whose next wake-up is a deadline the user sees (design §5): `setAlarmClock`.
         * Not HELD_DRIVING: its wake-up is only a backup re-check (design §6a).
         */
        val USER_VISIBLE_PHASES = setOf(Phase.WAITING, Phase.REMINDER, Phase.RINGING, Phase.SNOOZED)
    }
}
