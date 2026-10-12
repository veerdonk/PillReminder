package nl.veerdonk.pillreminder.platform

import android.content.Context
import android.content.Intent
import android.util.Log
import nl.veerdonk.pillreminder.core.model.RingSpec

/**
 * [RingController] on [AlarmRingService] (docs/03-technical-design.md §8), and the Settings
 * "Test alarm" hook ([TestAlarm]).
 *
 * [RingState] records what was last asked for, so a repeated [ensureRinging] for the same dose
 * doesn't touch the service, and [ensureStopped] while nothing rings does nothing at all. In
 * particular it never calls `startForegroundService` just to stop, which would fail with
 * `ForegroundServiceDidNotStartInTimeException`.
 */
class AndroidRingController(private val context: Context) : RingController, TestAlarm {

    override fun ensureRinging(spec: RingSpec) = request(ActiveRing(spec))

    override fun ensureStopped() {
        // A test alarm isn't the engine's to stop: it ends itself, or from its own screen.
        val current = RingState.current.value ?: return
        if (current.test) return
        stop()
    }

    override fun startTestAlarm() {
        // Never replace a real ring with a test.
        if (RingState.current.value?.test == false) return
        request(ActiveRing.test())
    }

    override fun stopTestAlarm() {
        if (RingState.current.value?.test == true) stop()
    }

    private fun request(ring: ActiveRing) {
        if (ringChange(RingState.current.value, ring) == RingChange.NONE) return
        RingState.current.value = ring
        try {
            context.startForegroundService(AlarmRingService.intent(context, ring))
        } catch (e: RuntimeException) {
            // E.g. ForegroundServiceStartNotAllowedException: nothing is ringing then.
            Log.e(TAG, "Can't start the ring service", e)
            RingState.current.value = null
        }
    }

    private fun stop() {
        RingState.current.value = null
        context.stopService(Intent(context, AlarmRingService::class.java))
    }

    private companion object {
        const val TAG = "AndroidRingController"
    }
}
