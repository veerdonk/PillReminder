package nl.veerdonk.pillreminder.platform

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import nl.veerdonk.pillreminder.platform.receivers.WakeupReceiver
import nl.veerdonk.pillreminder.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant

// A plain Application: the scheduler needs only a Context, not the app's container.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AlarmSchedulerTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val shadow: ShadowAlarmManager = shadowOf(alarmManager)
    private val scheduler = AndroidAlarmScheduler(context)

    private val t1 = Instant.parse("2026-10-13T05:00:00Z")
    private val t2 = Instant.parse("2026-10-13T05:40:00Z")

    // ScheduledAlarm has no getter for the operation, only the deprecated public field.
    @Suppress("DEPRECATION")
    private val ShadowAlarmManager.ScheduledAlarm.wakeup: PendingIntent get() = operation!!

    private fun assertWakeupIntent(pi: PendingIntent) {
        val sp = shadowOf(pi)
        assertTrue(sp.isBroadcast)
        assertEquals(0, sp.requestCode)
        assertTrue(sp.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertEquals(WakeupReceiver::class.java.name, sp.savedIntent.component?.className)
        assertEquals(WakeupReceiver.ACTION_WAKEUP, sp.savedIntent.action)
    }

    @Test
    fun `background wake-up is exact and allowed while idle`() {
        scheduler.schedule(t1, userVisible = false)

        val alarm = shadow.scheduledAlarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
        assertEquals(t1.toEpochMilli(), alarm.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
        assertNull(alarm.alarmClockInfo)
        assertWakeupIntent(alarm.wakeup)
    }

    @Test
    fun `user-visible wake-up is an alarm clock that opens MainActivity`() {
        scheduler.schedule(t1, userVisible = true)

        val alarm = shadow.scheduledAlarms.single()
        val info = alarm.alarmClockInfo!!
        assertEquals(t1.toEpochMilli(), info.triggerTime)
        val show = shadowOf(info.showIntent)
        assertTrue(show.isActivity)
        assertEquals(MainActivity::class.java.name, show.savedIntent.component?.className)
        assertWakeupIntent(alarm.wakeup)
    }

    @Test
    fun `scheduling replaces the previous wake-up, whatever its type`() {
        scheduler.schedule(t1, userVisible = true)
        scheduler.schedule(t2, userVisible = false)

        val alarm = shadow.scheduledAlarms.single()
        assertEquals(t2.toEpochMilli(), alarm.triggerAtMs)
        assertNull(alarm.alarmClockInfo)

        scheduler.schedule(t1, userVisible = true)
        val again = shadow.scheduledAlarms.single()
        assertEquals(t1.toEpochMilli(), again.triggerAtMs)
        assertNotNull(again.alarmClockInfo)
    }

    @Test
    fun `cancel removes the pending wake-up`() {
        scheduler.schedule(t1, userVisible = false)
        scheduler.cancel()
        assertTrue(shadow.scheduledAlarms.isEmpty())
    }
}
