package nl.veerdonk.pillreminder.platform

import android.app.usage.UsageEvents.Event
import nl.veerdonk.pillreminder.core.wake.UsageEvt.Type
import nl.veerdonk.pillreminder.core.wake.WakeCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AndroidWakeDetectorTest {

    @Test
    fun `each raw type maps per the design table`() {
        assertEquals(Type.UNLOCK, UsageEventSource.mapEventType(Event.KEYGUARD_HIDDEN))
        assertEquals(Type.SCREEN_ON, UsageEventSource.mapEventType(Event.SCREEN_INTERACTIVE))
        assertEquals(Type.SCREEN_OFF, UsageEventSource.mapEventType(Event.SCREEN_NON_INTERACTIVE))
        assertEquals(Type.APP_RESUMED, UsageEventSource.mapEventType(Event.ACTIVITY_RESUMED))
        assertEquals(Type.APP_PAUSED, UsageEventSource.mapEventType(Event.ACTIVITY_PAUSED))
    }

    @Test
    fun `activity stopped and other types are dropped`() {
        assertNull(UsageEventSource.mapEventType(Event.ACTIVITY_STOPPED))
        assertNull(UsageEventSource.mapEventType(Event.KEYGUARD_SHOWN))
        assertNull(UsageEventSource.mapEventType(Event.NONE))
        assertNull(UsageEventSource.mapEventType(Event.USER_INTERACTION))
        assertNull(UsageEventSource.mapEventType(Event.DEVICE_SHUTDOWN))
        assertNull(UsageEventSource.mapEventType(-1))
        assertNull(UsageEventSource.mapEventType(9999))
    }

    @Test
    fun `excluded packages are own, systemui and the launcher`() {
        assertEquals(
            setOf("me", "com.android.systemui", "launcher"),
            AndroidWakeDetector.excludedPackages("me", "launcher"),
        )
        assertEquals(setOf("me", "com.android.systemui"), AndroidWakeDetector.excludedPackages("me", null))
    }

    @Test
    fun `log entry describes counts and result`() {
        val from = Instant.parse("2026-10-12T05:00:00Z")
        val to = Instant.parse("2026-10-12T05:20:00Z")
        val text = DetectionLogEntry(from, to, WakeCheck(2, Duration.ofSeconds(90), true)).describe()
        assertTrue(text.contains("unlocks: 2"))
        assertTrue(text.contains("foreground: 1.5 min"))
        assertTrue(text.contains("result: awake"))
        assertTrue(text.contains(from.toString()) && text.contains(to.toString()))
    }

    @Test
    fun `log entry records missing usage access`() {
        val t = Instant.parse("2026-10-12T05:00:00Z")
        val entry = DetectionLogEntry(t, t, null)
        assertTrue(entry.usageAccessMissing)
        assertTrue(entry.describe().contains("Usage access missing"))
        assertFalse(DetectionLogEntry(t, t, WakeCheck(0, Duration.ZERO, false)).usageAccessMissing)
    }
}
