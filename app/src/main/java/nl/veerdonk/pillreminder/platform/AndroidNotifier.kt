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
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.LowStock
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.ReminderSubtitle
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.core.model.StatusSpec
import nl.veerdonk.pillreminder.platform.receivers.ActionReceiver
import nl.veerdonk.pillreminder.ui.MainActivity
import nl.veerdonk.pillreminder.ui.alarm.AlarmActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * [Notifier] on the system notification manager (docs/03-technical-design.md §7). The reminder
 * use [NOTIFICATION_ID]; the alarm (the ring service's foreground notification) uses
 * [ALARM_NOTIFICATION_ID]; the optional status notification (FR-2.4) uses
 * [STATUS_NOTIFICATION_ID]. The first two are separate because the system removes a foreground service's
 * notification by ID, asynchronously, after the service stops, which would also remove a
 * reminder re-posted on the same ID. The controller keeps at most one visible (01 A26).
 *
 * Posting is skipped when POST_NOTIFICATIONS isn't granted; the setup checklist (T11) asks
 * for it. [zone] is read on every post, so "Snoozed until" follows a time zone change.
 *
 * The reminder channel is silent: when a reminder first shows, [feedback] vibrates and chimes as
 * the settings say (FR-2.3, A66).
 */
class AndroidNotifier(
    private val context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val feedback: ReminderFeedback = AndroidReminderFeedback(context),
) : Notifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun ensureChannels() {
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // ReminderFeedback buzzes and chimes itself, as the settings say (FR-2.3, A66).
            setSound(null, null)
            enableVibration(false)
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
        // Silent and low: it's only there to glance at (FR-2.4).
        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        // Creating an existing channel only updates its name, so this is safe on every start.
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(reminders, alarm, status))
        // reminders_v1 played the default sound and vibrated itself (before A66).
        manager.deleteNotificationChannel(OLD_CHANNEL_REMINDERS)
    }

    override fun showReminder(spec: ReminderSpec) {
        // The controller never shows a reminder while ringing (design §8), so `alarmStyle`
        // never reaches here, and the reminder has no Snooze button (A5).
        val builder = baseBuilder(CHANNEL_REMINDERS, spec.dose, spec.doseCount)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(activityIntent(REQUEST_CONTENT, MainActivity::class.java, spec.dayId, spec.dose))
        addDecisionActions(builder, spec.dayId, spec.dose, DoseSource.NOTIFICATION)
        val subtitle = spec.subtitle?.let(::subtitleText)
        // Like setOnlyAlertOnce: a re-post of the reminder that's already up doesn't buzz again.
        val fresh = subtitle == null && !isShowing(NOTIFICATION_ID)
        if (subtitle != null) {
            // A subtitle only exists right after a ring (SNOOZED, PENDING_SILENT): the alarm just
            // alerted, so this re-post stays silent (01 A26). HELD_DRIVING stays silent too:
            // the reminder was already shown, and the user is driving (FR-3.8).
            builder.setSilent(true).setSubText(subtitle)
        }
        // "Running low" replaces the collapsed text, so it shows without expanding (FR-10.3). Out
        // of pills, a warning asking you to call the pharmacy replaces the title too (FR-10.6).
        val outOfPills = spec.lowStock?.pillsLeft == 0
        val lowStock = if (outOfPills) {
            context.getString(R.string.notification_out_text, spec.dose, spec.doseCount)
        } else {
            spec.lowStock?.let(::lowStockText)
        }
        if (lowStock != null) builder.setContentText(lowStock)
        if (outOfPills) {
            builder.setSmallIcon(R.drawable.ic_stat_warning).setContentTitle(context.getString(R.string.notification_out_title))
            spec.pharmacyPhone?.let { addCallPharmacyAction(builder, it) }
        }
        val lines = listOfNotNull(subtitle, lowStock, context.getString(R.string.notification_text))
        if (lines.size > 1) builder.setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
        if (post(NOTIFICATION_ID, builder.build()) && fresh) feedback.alert(spec.vibrate, spec.chime)
    }

    override fun cancelReminder() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun showStatus(spec: StatusSpec) {
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(statusContentIntent())
        when (spec) {
            is StatusSpec.NextDose -> builder
                .setContentTitle(context.getString(R.string.status_next_title, spec.dose, spec.doseCount, formatTime(spec.dueAt)))
                .setContentText(context.getString(R.string.status_next_text))
                // The system draws the countdown in the header, so no wake-ups are needed for it.
                .setWhen(spec.dueAt.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
            is StatusSpec.WaitingForWake -> builder
                .setContentTitle(context.getString(R.string.status_wake_title, spec.doseCount))
                .setContentText(context.getString(R.string.status_wake_text, formatTime(spec.fallbackAt)))
                .setShowWhen(false)
            is StatusSpec.CallPharmacy -> {
                val out = spec.lowStock.pillsLeft == 0
                builder
                    .setSmallIcon(R.drawable.ic_stat_warning)
                    .setContentTitle(context.getString(if (out) R.string.notification_out_title else R.string.status_call_title))
                    .setContentText(if (out) context.getString(R.string.status_out_text) else lowStockText(spec.lowStock))
                    .setShowWhen(false)
                spec.pharmacyPhone?.let { addCallPharmacyAction(builder, it) }
            }
        }
        post(STATUS_NOTIFICATION_ID, builder.build())
    }

    override fun cancelStatus() {
        notificationManager.cancel(STATUS_NOTIFICATION_ID)
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

    /** "Call pharmacy": opens the dialer with [phone] filled in, like the Home button (FR-10.4). */
    private fun addCallPharmacyAction(builder: NotificationCompat.Builder, phone: String) {
        val dial = PendingIntent.getActivity(
            context,
            REQUEST_CALL_PHARMACY,
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(0, context.getString(R.string.pills_call_pharmacy), dial)
    }

    private fun subtitleText(subtitle: ReminderSubtitle): String = when (subtitle) {
        is ReminderSubtitle.Snoozed -> context.getString(
            R.string.notification_subtitle_snoozed,
            formatTime(subtitle.until),
        )
        ReminderSubtitle.AlarmStopped -> context.getString(R.string.notification_subtitle_alarm_stopped)
        ReminderSubtitle.HeldWhileDriving -> context.getString(R.string.notification_subtitle_held_driving)
    }

    private fun formatTime(instant: Instant): String = TIME_FORMAT.format(instant.atZone(zone()))

    private fun lowStockText(lowStock: LowStock): String {
        val res = context.resources
        return context.getString(
            R.string.notification_low_stock,
            res.getQuantityString(R.plurals.pills_left, lowStock.pillsLeft, lowStock.pillsLeft),
            res.getQuantityString(R.plurals.pills_days, lowStock.daysLeft, lowStock.daysLeft),
        )
    }

    /** Posts [notification]; false when it wasn't posted for lack of the permission. */
    private fun post(id: Int, notification: Notification): Boolean {
        // Without the permission the system would drop the notification anyway; don't call it.
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        notificationManager.notify(id, notification)
        return true
    }

    private fun isShowing(id: Int): Boolean = notificationManager.activeNotifications.any { it.id == id }

    private fun statusContentIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_STATUS_CONTENT,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

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
        const val CHANNEL_REMINDERS = "reminders_v2"
        private const val OLD_CHANNEL_REMINDERS = "reminders_v1"
        const val CHANNEL_ALARM = "alarm_v1"
        const val CHANNEL_STATUS = "status_v1"

        /** The reminder notification. */
        const val NOTIFICATION_ID = 1001

        /** The alarm: the ring service's foreground notification. */
        const val ALARM_NOTIFICATION_ID = 1002

        /** The optional status notification between doses (FR-2.4). */
        const val STATUS_NOTIFICATION_ID = 1003

        /** Full-screen and content target of the alarm notification. */
        private val ALARM_SCREEN: Class<out Activity> = AlarmActivity::class.java

        private const val REQUEST_CONTENT = 100
        private const val REQUEST_ALARM_SCREEN = 101
        private const val REQUEST_STATUS_CONTENT = 102
        private const val REQUEST_CALL_PHARMACY = 103

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
