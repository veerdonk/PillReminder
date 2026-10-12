package nl.veerdonk.pillreminder.platform

import android.app.Application
import nl.veerdonk.pillreminder.core.model.RingSpec
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidRingControllerTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private val controller = AndroidRingController(app)
    private val shadow = shadowOf(app)
    private val spec = RingSpec(LocalDate.parse("2026-10-13"), 2, 3, canSnooze = true, snoozesLeft = 2)

    @Before
    fun setUp() {
        RingState.current.value = null
    }

    @After
    fun tearDown() {
        RingState.current.value = null
    }

    @Test
    fun `ensureStopped while nothing rings starts and stops nothing`() {
        controller.ensureStopped()

        assertNull(shadow.nextStartedService)
        assertNull(shadow.nextStoppedService)
    }

    @Test
    fun `ensureRinging starts the service with the spec`() {
        controller.ensureRinging(spec)

        val started = shadow.nextStartedService
        assertNotNull(started)
        assertEquals(AlarmRingService::class.java.name, started.component?.className)
        assertEquals(spec, RingExtras.read(started)?.spec)
    }

    @Test
    fun `ensureRinging twice for the same dose starts once`() {
        controller.ensureRinging(spec)
        shadow.nextStartedService
        controller.ensureRinging(spec)

        assertNull(shadow.nextStartedService)
    }

    @Test
    fun `canSnooze change reaches the service`() {
        controller.ensureRinging(spec)
        shadow.nextStartedService
        controller.ensureRinging(spec.copy(canSnooze = false, snoozesLeft = 0))

        assertEquals(false, RingExtras.read(shadow.nextStartedService)?.spec?.canSnooze)
    }

    @Test
    fun `ensureStopped after ringing stops the service once`() {
        controller.ensureRinging(spec)
        controller.ensureStopped()
        controller.ensureStopped()

        assertNotNull(shadow.nextStoppedService)
        assertNull(shadow.nextStoppedService)
        assertNull(RingState.current.value)
    }

    @Test
    fun `test alarm starts in test mode and survives ensureStopped`() {
        controller.startTestAlarm()
        assertTrue(RingExtras.read(shadow.nextStartedService)!!.test)

        controller.ensureStopped()
        assertNull(shadow.nextStoppedService)

        controller.stopTestAlarm()
        assertNotNull(shadow.nextStoppedService)
    }

    @Test
    fun `test alarm never replaces a real ring`() {
        controller.ensureRinging(spec)
        shadow.nextStartedService

        controller.startTestAlarm()

        assertNull(shadow.nextStartedService)
        assertEquals(false, RingState.current.value?.test)
    }
}
