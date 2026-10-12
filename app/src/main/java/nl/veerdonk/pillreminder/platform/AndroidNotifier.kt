package nl.veerdonk.pillreminder.platform

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.ReminderSubtitle
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.platform.receivers.ActionReceiver
import nl.veerdonk.pillreminder.ui.MainActivity
import nl.veerdonk.pillreminder.ui.alarm.AlarmActivity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * [Notifier] on the system notification manager (docs/03-technical-design.md §7). The reminder
 * use [NOTIFICATION_ID]; the alarm (the ring service's foreground notification) uses
 * [ALARM_NOTIFICATION_ID]. They are separate because the system removes a foreground service's
 * notification by ID, asynchronously, after the service stops, which would also remove a
 * reminder re-posted on the same ID. The controller keeps at most one visible (01 A26).
 *
 * Posting is skipped when POST_NOTIFICATIONS isn't granted; the setup checklist (T11) asks
 * for it. [zone] is read on every post, so "Snoozed until" follows a time zone change.
 */
class AndroidNotifier(
    private val context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : Notifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun ensureChannels() {
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // The default sound and vibration follow the ringer mode as usual (FR-2.3).
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
        }
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // The ring service plays the sound and vibrates itself (design §8).
            setSound(null, null)
            enableVibration(false)
        }
        // Creating an existing channel only updates its name, so this is safe on every start.
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(reminders, alarm))
    }

    override fun showReminder(spec: ReminderSpec) {
        // The controller never shows a reminder while ringing (design §8), so `alarmStyle`
        // never reaches here, and the reminder has no Snooze button (A5).
        val builder = baseBuilder(CHANNEL_REMINDERS, spec.dose, spec.doseCount)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(activityIntent(REQUEST_CONTENT, MainActivity::class.java, spec.dayId, spec.dose))
        addDecisionActions(builder, spec.dayId, spec.dose, DoseSource.NOTIFICATION)
        spec.subtitle?.let { subtitle ->
            val line = subtitleText(subtitle)
            // A subtitle only exists right after a ring (SNOOZED, PENDING_SILENT): the alarm just
            // alerted, so this re-post stays silent (01 A26). HELD_DRIVING stays silent too:
            // the reminder was already shown, and the user is driving (FR-3.8).
            builder.setSilent(true)
                .setSubText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$line\n${context.getString(R.string.notification_text)}"))
        }
        post(builder.build())
    }

    override fun cancelReminder() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun buildAlarmNotification(spec: RingSpec): Notification {
        val alarmScreen = activityIntent(REQUEST_ALARM_SCREEN, ALARM_SCREEN, spec.dayId, spec.dose)
        val builder = baseBuilder(CHANNEL_ALARM, spec.dose, spec.doseCount)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(alarmScreen)
            .setFullScreenIntent(alarmScreen, true)
        addDecisionActions(builder, spec.dayId, spec.dose, DoseSource.ALARM)
        if (spec.canSnooze) {
            builder.addAction(
                0,
                context.resources.getQuantityString(R.plurals.action_snooze, spec.snoozesLeft, spec.snoozesLeft),
                actionIntent(ActionReceiver.ACTION_SNOOZE, spec.dayId, spec.dose, DoseSource.ALARM),
            )
        }
        return builder.build()
    }

    private fun baseBuilder(channel: String, dose: Int, doseCount: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(context.getString(R.string.notification_title, dose, doseCount))
            .setContentText(context.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    /** "I took my pill", then "Not today" (dose 1) or "I'm done for today" (FR-2.1, FR-3.2). */
    private fun addDecisionActions(builder: NotificationCompat.Builder, dayId: LocalDate, dose: Int, source: DoseSource) {
        val skipLabel = if (dose == 1) R.string.action_not_today else R.string.action_done_for_today
        builder
            .addAction(0, context.getString(R.string.action_took), actionIntent(ActionReceiver.ACTION_TOOK, dayId, dose, source))
            .addAction(0, context.getString(skipLabel), actionIntent(ActionReceiver.ACTION_SKIP, dayId, dose, source))
    }

    private fun subtitleText(subtitle: ReminderSubtitle): String = when (subtitle) {
        is ReminderSubtitle.Snoozed -> context.getString(
            R.string.notification_subtitle_snoozed,
            TIME_FORMAT.format(subtitle.until.atZone(zone())),
        )
        ReminderSubtitle.AlarmStopped -> context.getString(R.string.notification_subtitle_alarm_stopped)
        ReminderSubtitle.HeldWhileDriving -> context.getString(R.string.notification_subtitle_held_driving)
    }

    private fun post(notification: Notification) {
        // Without the permission the system would drop the notification anyway; don't call it.
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // FLAG_UPDATE_CURRENT: each request code is one PendingIntent, whose extras follow the
    // latest dayId and dose.
    private fun actionIntent(action: String, dayId: LocalDate, dose: Int, source: DoseSource): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            actionRequestCode(action, source),
            ActionReceiver.intent(context, action, dayId, dose, source),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun activityIntent(requestCode: Int, activity: Class<out Activity>, dayId: LocalDate, dose: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ActionReceiver.EXTRA_DAY_ID, dayId.toString())
                .putExtra(ActionReceiver.EXTRA_DOSE, dose),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val CHANNEL_REMINDERS = "reminders_v1"
        const val CHANNEL_ALARM = "alarm_v1"

        /** The reminder notification. */
        const val NOTIFICATION_ID = 1001

        /** The alarm: the ring service's foreground notification. */
        const val ALARM_NOTIFICATION_ID = 1002

        /** Full-screen and content target of the alarm notification. */
        private val ALARM_SCREEN: Class<out Activity> = AlarmActivity::class.java

        private const val REQUEST_CONTENT = 100
        private const val REQUEST_ALARM_SCREEN = 101

        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** Distinct per action and source, so no button's PendingIntent overwrites another's. */
        internal fun actionRequestCode(action: String, source: DoseSource): Int {
            val base = when (action) {
                ActionReceiver.ACTION_TOOK -> 1
                ActionReceiver.ACTION_SKIP -> 2
                ActionReceiver.ACTION_SNOOZE -> 3
                else -> error("Unknown action $action")
            }
            return if (source == DoseSource.ALARM) base + 10 else base
        }
    }
}
