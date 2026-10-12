package nl.veerdonk.pillreminder.platform

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.provider.Settings
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.ReminderSubtitle
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.platform.receivers.ActionReceiver
import nl.veerdonk.pillreminder.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// A plain Application: the notifier needs only a Context, not the app's container.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidNotifierTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val shadow: ShadowNotificationManager = shadowOf(notificationManager)
    private val notifier = AndroidNotifier(context, zone = { ZoneId.of("Europe/Amsterdam") })

    private val day = LocalDate.parse("2026-10-13")

    private fun reminder(dose: Int, subtitle: ReminderSubtitle? = null) = ReminderSpec(
        dayId = day,
        dose = dose,
        doseCount = 3,
        alarmStyle = false,
        canSnooze = false,
        snoozesLeft = 3,
        subtitle = subtitle,
    )

    private fun ring(canSnooze: Boolean, snoozesLeft: Int) =
        RingSpec(dayId = day, dose = 2, doseCount = 3, canSnooze = canSnooze, snoozesLeft = snoozesLeft)

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    private val Notification.subText get() = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
    private val Notification.actionLabels get() = actions.orEmpty().map { it.title.toString() }

    private fun posted(): Notification = shadow.allNotifications.single().also {
        assertNotNull(shadow.getNotification(AndroidNotifier.NOTIFICATION_ID))
    }

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.ensureChannels()
    }

    @Test
    fun `reminder channel is high importance with the default sound and vibration`() {
        val channel = notificationManager.getNotificationChannel(AndroidNotifier.CHANNEL_REMINDERS)
        assertEquals("Pill reminders", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, channel.sound)
        assertTrue(channel.shouldVibrate())
    }

    @Test
    fun `alarm channel is high importance without sound or vibration`() {
        val channel = notificationManager.getNotificationChannel(AndroidNotifier.CHANNEL_ALARM)
        assertEquals("Pill alarm", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun `ensureChannels can run again`() {
        notifier.ensureChannels()
        assertEquals(2, notificationManager.notificationChannels.size)
    }

    @Test
    fun `dose 1 reminder is ongoing with took and not today`() {
        notifier.showReminder(reminder(dose = 1))

        val n = posted()
        assertEquals(AndroidNotifier.CHANNEL_REMINDERS, n.channelId)
        assertEquals(Notification.CATEGORY_REMINDER, n.category)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals("Time for pill 1 of 3", n.title)
        assertEquals("Tap a button below once you've decided.", n.text)
        assertNull(n.subText)
        assertEquals(listOf("I took my pill", "Not today"), n.actionLabels)
        assertNull(n.fullScreenIntent)
    }

    @Test
    fun `later dose reminder offers I'm done for today`() {
        notifier.showReminder(reminder(dose = 2))

        val n = posted()
        assertEquals("Time for pill 2 of 3", n.title)
        assertEquals(listOf("I took my pill", "I'm done for today"), n.actionLabels)
    }

    @Test
    fun `reminder body opens MainActivity`() {
        notifier.showReminder(reminder(dose = 1))

        val content = shadowOf(posted().contentIntent)
        assertTrue(content.isActivity)
        assertEquals(MainActivity::class.java.name, content.savedIntent.component?.className)
    }

    @Test
    fun `snoozed subtitle shows the snooze end in the device zone`() {
        // 07:20 UTC is 09:20 in Amsterdam (CEST).
        notifier.showReminder(reminder(dose = 1, subtitle = ReminderSubtitle.Snoozed(Instant.parse("2026-10-13T07:20:00Z"))))

        val n = posted()
        assertEquals("Snoozed until 09:20", n.subText)
        assertEquals(
            "Snoozed until 09:20\nTap a button below once you've decided.",
            n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
        )
    }

    @Test
    fun `alarm stopped subtitle`() {
        notifier.showReminder(reminder(dose = 2, subtitle = ReminderSubtitle.AlarmStopped))

        assertEquals("Alarm stopped — please take your pill", posted().subText)
    }

    @Test
    fun `re-posting the same reminder keeps a single notification`() {
        notifier.showReminder(reminder(dose = 1))
        notifier.showReminder(reminder(dose = 1))

        assertEquals(1, shadow.allNotifications.size)
    }

    @Test
    fun `a new spec replaces the reminder in place`() {
        notifier.showReminder(reminder(dose = 1))
        notifier.showReminder(reminder(dose = 2, subtitle = ReminderSubtitle.AlarmStopped))

        assertEquals("Time for pill 2 of 3", posted().title)
    }

    @Test
    fun `cancelReminder removes it, and is a no-op when nothing is shown`() {
        notifier.cancelReminder()
        notifier.showReminder(reminder(dose = 1))
        notifier.cancelReminder()
        assertTrue(shadow.allNotifications.isEmpty())
        notifier.cancelReminder()
        assertTrue(shadow.allNotifications.isEmpty())
    }

    @Test
    fun `nothing is posted without the notification permission`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.showReminder(reminder(dose = 1))

        assertTrue(shadow.allNotifications.isEmpty())
    }

    @Test
    fun `alarm notification is an ongoing alarm with a full-screen intent`() {
        val n = notifier.buildAlarmNotification(ring(canSnooze = true, snoozesLeft = 2))

        assertEquals(AndroidNotifier.CHANNEL_ALARM, n.channelId)
        assertEquals(Notification.CATEGORY_ALARM, n.category)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Time for pill 2 of 3", n.title)
        val fullScreen = shadowOf(n.fullScreenIntent)
        assertTrue(fullScreen.isActivity)
        // MainActivity until T8 adds AlarmActivity.
        assertEquals(MainActivity::class.java.name, fullScreen.savedIntent.component?.className)
        assertEquals(n.fullScreenIntent, n.contentIntent)
    }

    @Test
    fun `alarm notification offers snooze with the snoozes left`() {
        val n = notifier.buildAlarmNotification(ring(canSnooze = true, snoozesLeft = 2))
        assertEquals(listOf("I took my pill", "I'm done for today", "Snooze (2 left)"), n.actionLabels)

        val one = notifier.buildAlarmNotification(ring(canSnooze = true, snoozesLeft = 1))
        assertEquals("Snooze (1 left)", one.actionLabels.last())
    }

    @Test
    fun `alarm notification hides snooze when none are left`() {
        val n = notifier.buildAlarmNotification(ring(canSnooze = false, snoozesLeft = 0))

        assertEquals(listOf("I took my pill", "I'm done for today"), n.actionLabels)
    }

    @Test
    fun `building the alarm notification posts nothing`() {
        notifier.buildAlarmNotification(ring(canSnooze = true, snoozesLeft = 2))
        assertTrue(shadow.allNotifications.isEmpty())
    }

    @Test
    fun `every action carries day, dose and source, with distinct request codes`() {
        notifier.showReminder(reminder(dose = 2))
        val reminderActions = posted().actions.toList()
        val alarmActions = notifier.buildAlarmNotification(ring(canSnooze = true, snoozesLeft = 2)).actions.toList()

        val expected = listOf(
            Triple(reminderActions[0], ActionReceiver.ACTION_TOOK, DoseSource.NOTIFICATION),
            Triple(reminderActions[1], ActionReceiver.ACTION_SKIP, DoseSource.NOTIFICATION),
            Triple(alarmActions[0], ActionReceiver.ACTION_TOOK, DoseSource.ALARM),
            Triple(alarmActions[1], ActionReceiver.ACTION_SKIP, DoseSource.ALARM),
            Triple(alarmActions[2], ActionReceiver.ACTION_SNOOZE, DoseSource.ALARM),
        )
        val requestCodes = expected.map { (action, name, source) ->
            val pi = shadowOf(action.actionIntent)
            assertTrue(pi.isBroadcast)
            assertTrue(pi.flags and PendingIntent.FLAG_IMMUTABLE != 0)
            val intent = pi.savedIntent
            assertEquals(ActionReceiver::class.java.name, intent.component?.className)
            assertEquals(name, intent.action)
            assertEquals("2026-10-13", intent.getStringExtra(ActionReceiver.EXTRA_DAY_ID))
            assertEquals(2, intent.getIntExtra(ActionReceiver.EXTRA_DOSE, -1))
            assertEquals(source.name, intent.getStringExtra(ActionReceiver.EXTRA_SOURCE))
            pi.requestCode
        }
        assertEquals(requestCodes.size, requestCodes.toSet().size)
    }
}
