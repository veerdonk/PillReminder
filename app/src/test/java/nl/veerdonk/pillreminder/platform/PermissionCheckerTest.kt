package nl.veerdonk.pillreminder.platform

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AppOpsManager
import android.os.Process
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PermissionCheckerTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val checker = AndroidPermissionChecker(context)

    @Before
    fun setUp() {
        AndroidNotifier(context).ensureChannels()
    }

    @Test
    fun `notifications are ok with both channels on`() {
        assertTrue(checker.check().isOk(SetupItem.NOTIFICATIONS))
    }

    @Test
    fun `notifications are missing when a channel is switched off`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(AndroidNotifier.CHANNEL_ALARM)
        manager.createNotificationChannel(
            NotificationChannel(AndroidNotifier.CHANNEL_ALARM, "x", NotificationManager.IMPORTANCE_NONE),
        )
        assertFalse(checker.check().isOk(SetupItem.NOTIFICATIONS))
    }

    @Test
    fun `notifications are missing when disabled for the app`() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        assertFalse(checker.check().isOk(SetupItem.NOTIFICATIONS))
    }

    @Test
    fun `exact alarms follow canScheduleExactAlarms`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(checker.check().isOk(SetupItem.EXACT_ALARMS))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(checker.check().isOk(SetupItem.EXACT_ALARMS))
    }

    @Test
    fun `usage access follows the app op`() {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED,
        )
        assertTrue(checker.check().isOk(SetupItem.USAGE_ACCESS))
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED,
        )
        assertFalse(checker.check().isOk(SetupItem.USAGE_ACCESS))
    }

    @Test
    fun `battery optimisation follows the power manager`() {
        val power = shadowOf(context.getSystemService(android.os.PowerManager::class.java))
        assertFalse(checker.check().isOk(SetupItem.BATTERY_OPTIMISATION))
        power.setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(checker.check().isOk(SetupItem.BATTERY_OPTIMISATION))
    }

    @Test
    fun `full screen intent is ok below API 34`() {
        // Robolectric runs at API 33 (robolectric.properties).
        assertTrue(checker.check().isOk(SetupItem.FULL_SCREEN_INTENT))
    }

    @Test
    fun `OnePlus extras and setup seen are persisted`() {
        assertFalse(checker.check().isOk(SetupItem.ONEPLUS_EXTRAS))
        assertFalse(checker.setupSeen)
        checker.onePlusDone = true
        checker.setupSeen = true
        val reread = AndroidPermissionChecker(context)
        assertTrue(reread.check().isOk(SetupItem.ONEPLUS_EXTRAS))
        assertTrue(reread.setupSeen)
    }

    @Test
    fun `fix intents point at the right settings screens`() {
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, checker.fixIntent(SetupItem.NOTIFICATIONS).action)
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, checker.fixIntent(SetupItem.USAGE_ACCESS).action)
        val exact = checker.fixIntent(SetupItem.EXACT_ALARMS)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, exact.action)
        assertEquals("package:${context.packageName}", exact.dataString)
        val battery = checker.fixIntent(SetupItem.BATTERY_OPTIMISATION)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, battery.action)
        assertEquals("package:${context.packageName}", battery.dataString)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, checker.fixIntent(SetupItem.ONEPLUS_EXTRAS).action)
    }
}
