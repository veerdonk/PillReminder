package nl.veerdonk.pillreminder.platform

import nl.veerdonk.pillreminder.core.model.RingSpec

/**
 * Starts and stops the alarm ring service (docs/03-technical-design.md §4, §8). Both calls are
 * idempotent: ringing for the same dose again, or stopping while stopped, does nothing.
 */
interface RingController {
    fun ensureRinging(spec: RingSpec)
    fun ensureStopped()
}

/**
 * The Settings "Test alarm" hook (design §8): rings for 10 s without touching the day state.
 * Reached as `AppContainer.testAlarm`.
 */
interface TestAlarm {
    /** Rings the alarm (sound, vibration, AlarmActivity) for 10 seconds. */
    fun startTestAlarm()

    /** Ends a running test alarm early. Does nothing when no test alarm is ringing. */
    fun stopTestAlarm()
}
