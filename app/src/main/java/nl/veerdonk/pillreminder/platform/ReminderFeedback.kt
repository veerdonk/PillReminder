package nl.veerdonk.pillreminder.platform

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings

/**
 * The buzz and chime when a reminder first shows (FR-2.3, assumption A66). The reminder channel
 * itself is silent, so the user's `reminderVibrate` / `reminderChime` switches decide.
 */
interface ReminderFeedback {
    fun alert(vibrate: Boolean, chime: Boolean)
}

/** What actually plays, after the phone's ringer mode and Do Not Disturb have had their say. */
internal data class ReminderAlert(val vibrate: Boolean, val chime: Boolean)

/**
 * Like a normal notification: the chime only plays with the ringer on normal, vibration also
 * in vibrate mode, and neither in silent mode or Do Not Disturb (interruption filter not "all").
 */
internal fun reminderAlert(vibrate: Boolean, chime: Boolean, ringerMode: Int, interruptionFilter: Int): ReminderAlert {
    val dndOff = interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    return ReminderAlert(
        vibrate = vibrate && dndOff && ringerMode != AudioManager.RINGER_MODE_SILENT,
        chime = chime && dndOff && ringerMode == AudioManager.RINGER_MODE_NORMAL,
    )
}

/** [ReminderFeedback] on the system vibrator and the default notification sound, played once. */
class AndroidReminderFeedback(private val context: Context) : ReminderFeedback {

    // Kept so the ringtone isn't collected while it plays, and so a new chime replaces the last.
    private var ringtone: Ringtone? = null

    override fun alert(vibrate: Boolean, chime: Boolean) {
        if (!vibrate && !chime) return
        val alert = reminderAlert(
            vibrate,
            chime,
            context.getSystemService(AudioManager::class.java).ringerMode,
            context.getSystemService(NotificationManager::class.java).currentInterruptionFilter,
        )
        if (alert.vibrate) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, -1),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION),
            )
        }
        if (alert.chime) {
            ringtone?.stop()
            ringtone = RingtoneManager.getRingtone(context, Settings.System.DEFAULT_NOTIFICATION_URI)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }
    }

    private companion object {
        /** Two short buzzes: off, on, off, on (ms). */
        val VIBRATION_PATTERN = longArrayOf(0, 300, 200, 300)
    }
}
