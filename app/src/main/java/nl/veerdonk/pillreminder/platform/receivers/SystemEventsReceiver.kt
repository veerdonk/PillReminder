package nl.veerdonk.pillreminder.platform.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Boot, app update, clock change and time zone change: tick, which re-arms the wake-up and
 * restores the reminder or alarm (FR-9.2). These broadcasts are exempt from the
 * implicit-broadcast limits, so the manifest registration receives them.
 */
class SystemEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Exported, so only act on the system broadcasts it is registered for.
        if (intent.action !in HANDLED_ACTIONS) return
        tickAsync(context)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
