package nl.veerdonk.pillreminder.platform

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import nl.veerdonk.pillreminder.core.model.RingSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RingLogicTest {

    private val all = NotificationManager.INTERRUPTION_FILTER_ALL
    private val day = LocalDate.parse("2026-10-13")

    private fun ring(
        dose: Int = 2,
        canSnooze: Boolean = true,
        snoozesLeft: Int = 2,
        dayId: LocalDate = day,
        test: Boolean = false,
    ) = ActiveRing(RingSpec(dayId, dose, 3, canSnooze, snoozesLeft), test)

    // FR-3.3 table

    @Test
    fun `normal ringer without DND plays sound`() {
        assertTrue(shouldPlaySound(AudioManager.RINGER_MODE_NORMAL, all))
    }

    @Test
    fun `vibrate ringer without DND still plays sound`() {
        assertTrue(shouldPlaySound(AudioManager.RINGER_MODE_VIBRATE, all))
    }

    @Test
    fun `silent ringer is quiet`() {
        assertFalse(shouldPlaySound(AudioManager.RINGER_MODE_SILENT, all))
    }

    @Test
    fun `any DND filter is quiet in every ringer mode`() {
        val filters = listOf(
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
        )
        val modes = listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT)
        for (filter in filters) for (mode in modes) assertFalse("$mode/$filter", shouldPlaySound(mode, filter))
    }

    // ringChange

    @Test
    fun `nothing ringing starts`() {
        assertEquals(RingChange.START, ringChange(null, ring()))
    }

    @Test
    fun `same spec is idempotent`() {
        assertEquals(RingChange.NONE, ringChange(ring(), ring()))
    }

    @Test
    fun `only canSnooze changed updates the notification`() {
        assertEquals(
            RingChange.UPDATE_NOTIFICATION,
            ringChange(ring(canSnooze = true), ring(canSnooze = false, snoozesLeft = 0)),
        )
    }

    @Test
    fun `another dose or day restarts`() {
        assertEquals(RingChange.START, ringChange(ring(dose = 2), ring(dose = 3)))
        assertEquals(RingChange.START, ringChange(ring(), ring(dayId = day.plusDays(1))))
    }

    @Test
    fun `test and real rings replace each other`() {
        assertEquals(RingChange.START, ringChange(ring(), ActiveRing.test()))
        assertEquals(RingChange.START, ringChange(ActiveRing.test(), ring()))
        assertEquals(RingChange.NONE, ringChange(ActiveRing.test(), ActiveRing.test()))
    }

    // Extras

    @Test
    fun `extras round-trip a ring spec`() {
        val original = ring(dose = 3, canSnooze = false, snoozesLeft = 0)
        assertEquals(original, RingExtras.read(RingExtras.put(Intent(), original)))
    }

    @Test
    fun `extras round-trip a test ring`() {
        assertEquals(ActiveRing.test(), RingExtras.read(RingExtras.put(Intent(), ActiveRing.test())))
    }

    @Test
    fun `incomplete or bad extras read as null`() {
        assertNull(RingExtras.read(null))
        assertNull(RingExtras.read(Intent()))
        assertNull(RingExtras.read(Intent().putExtra(RingExtras.DAY_ID, "nonsense").putExtra(RingExtras.DOSE, 1)))
        assertNull(RingExtras.read(Intent().putExtra(RingExtras.DAY_ID, "2026-10-13").putExtra(RingExtras.DOSE, 0)))
    }

    @Test
    fun `notification extras without ring details still read`() {
        // What AndroidNotifier puts on the alarm screen's PendingIntent.
        val read = RingExtras.read(Intent().putExtra(RingExtras.DAY_ID, "2026-10-13").putExtra(RingExtras.DOSE, 2))
        assertNotNull(read)
        assertEquals(day, read?.spec?.dayId)
        assertEquals(2, read?.spec?.dose)
        assertFalse(read!!.test)
    }
}
