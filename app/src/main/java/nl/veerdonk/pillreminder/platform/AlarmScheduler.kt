package nl.veerdonk.pillreminder.platform

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import nl.veerdonk.pillreminder.platform.receivers.WakeupReceiver
import nl.veerdonk.pillreminder.ui.MainActivity
import java.time.Instant

/** The app's single pending wake-up (docs/03-technical-design.md §4, §5). */
interface AlarmScheduler {
    /**
     * Arms the wake-up at [at], replacing any earlier one. [userVisible] marks a deadline the
     * user cares about (WAITING, REMINDER, RINGING, SNOOZED), which must not be deferred by Doze.
     */
    fun schedule(at: Instant, userVisible: Boolean)

    fun cancel()
}

/**
 * [AlarmScheduler] on [AlarmManager]. Every wake-up uses the same [PendingIntent] to
 * [WakeupReceiver], so scheduling replaces the previous one whatever its type.
 * User-visible deadlines use `setAlarmClock` (never deferred by Doze; shows the alarm icon,
 * and tapping it opens [MainActivity]); everything else uses `setExactAndAllowWhileIdle`.
 * Exact alarms are allowed by `USE_EXACT_ALARM`, which is granted at install on API 33+.
 */
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    // Lint only knows SCHEDULE_EXACT_ALARM; the manifest's USE_EXACT_ALARM grants the same
    // on API 33+ (minSdk) and can't be revoked by the user.
    @SuppressLint("MissingPermission")
    override fun schedule(at: Instant, userVisible: Boolean) {
        val millis = at.toEpochMilli()
        if (userVisible) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent()), wakeupIntent())
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, wakeupIntent())
        }
    }

    override fun cancel() {
        alarmManager.cancel(wakeupIntent())
    }

    private fun wakeupIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WakeupReceiver::class.java).setAction(WakeupReceiver.ACTION_WAKEUP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 0
    }
}
