package nl.veerdonk.pillreminder.platform

import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderFeedbackTest {

    private fun alert(ringerMode: Int, filter: Int = INTERRUPTION_FILTER_ALL, vibrate: Boolean = true, chime: Boolean = true) =
        reminderAlert(vibrate, chime, ringerMode, filter)

    @Test fun normalModeVibratesAndChimes() {
        assertEquals(ReminderAlert(vibrate = true, chime = true), alert(RINGER_MODE_NORMAL))
    }

    @Test fun vibrateModeOnlyVibrates() {
        assertEquals(ReminderAlert(vibrate = true, chime = false), alert(RINGER_MODE_VIBRATE))
    }

    @Test fun silentModeDoesNothing() {
        assertEquals(ReminderAlert(vibrate = false, chime = false), alert(RINGER_MODE_SILENT))
    }

    @Test fun doNotDisturbDoesNothing() {
        assertEquals(ReminderAlert(vibrate = false, chime = false), alert(RINGER_MODE_NORMAL, INTERRUPTION_FILTER_PRIORITY))
    }

    @Test fun switchesOffMeanNothingPlays() {
        assertEquals(ReminderAlert(vibrate = false, chime = true), alert(RINGER_MODE_NORMAL, vibrate = false))
        assertEquals(ReminderAlert(vibrate = true, chime = false), alert(RINGER_MODE_NORMAL, chime = false))
    }
}
