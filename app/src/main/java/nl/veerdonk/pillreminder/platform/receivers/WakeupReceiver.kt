package nl.veerdonk.pillreminder.platform.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The [nl.veerdonk.pillreminder.platform.AlarmScheduler] wake-up fired: tick. Not exported. */
class WakeupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WAKEUP) return
        tickAsync(context)
    }

    companion object {
        const val ACTION_WAKEUP = "nl.veerdonk.pillreminder.action.WAKEUP"
    }
}
