package nl.veerdonk.pillreminder.platform.receivers

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.PillReminderApp

/**
 * Runs `controller.tick()` on the app scope and keeps the broadcast alive until it is done
 * (docs/03-technical-design.md §5).
 */
internal fun BroadcastReceiver.tickAsync(context: Context) {
    val container = (context.applicationContext as PillReminderApp).container
    val pending = goAsync()
    container.appScope.launch {
        try {
            container.reminderController.tick()
        } finally {
            pending.finish()
        }
    }
}
