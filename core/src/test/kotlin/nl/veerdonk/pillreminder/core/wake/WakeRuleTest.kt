package nl.veerdonk.pillreminder.core.wake

import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.wake.UsageEvt.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WakeRuleTest {

    // Window 07:00–07:20 UTC: a 20 min detection window, defaults minUnlocks 2, minAppUsage 3 min.
    private val from = t("07:00")
    private val to = t("07:20")
    private val defaults = Settings()

    private val launcher = "com.oneplus.launcher"
    private val excluded = setOf(OWN, "com.android.systemui", launcher)

    private fun awake(
        vararg events: UsageEvt,
        s: Settings = defaults,
        screenOnIsUnlock: Boolean = false,
    ): Boolean = WakeRule.isAwake(events.toList(), from, to, s, screenOnIsUnlock, excluded)

    // --- rule (a): unlocks ---

    @Test fun `2 unlocks in the window is awake`() =
        assertTrue(awake(unlock("07:05"), unlock("07:10")))

    @Test fun `1 unlock is not awake`() =
        assertFalse(awake(unlock("07:05")))

    @Test fun `no events is not awake`() =
        assertFalse(awake())

    @Test fun `exactly minUnlocks is awake, one fewer is not`() {
        val s = defaults.copy(minUnlocks = 4)
        assertTrue(awake(unlock("07:01"), unlock("07:02"), unlock("07:03"), unlock("07:04"), s = s))
        assertFalse(awake(unlock("07:01"), unlock("07:02"), unlock("07:03"), s = s))
    }

    @Test fun `unlocks exactly at from and exactly at to count`() =
        assertTrue(awake(unlock("07:00"), unlock("07:20")))

    @Test fun `unlocks just outside the window do not count`() =
        assertFalse(awake(unlock("06:59:59"), unlock("07:10"), unlock("07:20:01")))

    @Test fun `unlocks before from are ignored`() =
        assertFalse(awake(unlock("06:40"), unlock("06:45"), unlock("06:59")))

    @Test fun `input order does not matter`() =
        assertTrue(awake(resumed("07:08", APP), unlock("07:10"), paused("07:05", APP), unlock("07:02")))

    // --- rule (b): 1 unlock + foreground time ---

    @Test fun `1 unlock plus 3 min of app use is awake`() =
        assertTrue(awake(unlock("07:05"), resumed("07:06", APP), paused("07:09", APP)))

    @Test fun `1 unlock plus 2 min of app use is not awake`() =
        assertFalse(awake(unlock("07:05"), resumed("07:06", APP), paused("07:08", APP)))

    @Test fun `exactly minAppUsage is awake, one second less is not`() {
        assertTrue(awake(unlock("07:05"), resumed("07:06", APP), paused("07:09:00", APP)))
        assertFalse(awake(unlock("07:05"), resumed("07:06", APP), paused("07:08:59", APP)))
    }

    @Test fun `app use without any unlock is not awake`() =
        assertFalse(awake(resumed("07:01", APP), paused("07:15", APP)))

    @Test fun `3 min in the launcher is not awake`() =
        assertFalse(awake(unlock("07:05"), resumed("07:06", launcher), paused("07:09", launcher)))

    @Test fun `3 min in System UI is not awake`() =
        assertFalse(
            awake(unlock("07:05"), resumed("07:06", "com.android.systemui"), paused("07:09", "com.android.systemui")),
        )

    @Test fun `3 min in this app is not awake`() =
        assertFalse(awake(unlock("07:05"), resumed("07:06", OWN), paused("07:09", OWN)))

    @Test fun `excluded time does not add to other apps`() =
        assertFalse(
            awake(
                unlock("07:05"),
                resumed("07:06", launcher), paused("07:07", launcher),
                resumed("07:07", APP), paused("07:09", APP),
            ),
        )

    @Test fun `time in different apps adds up`() =
        assertTrue(
            awake(
                unlock("07:05"),
                resumed("07:06", APP), paused("07:07", APP),
                resumed("07:10", OTHER), paused("07:12", OTHER),
            ),
        )

    // --- the window edges for foreground time ---

    @Test fun `app use entirely before from is ignored`() =
        assertFalse(awake(unlock("07:05"), resumed("06:40", APP), paused("06:55", APP)))

    @Test fun `app resumed before from and still open counts from from`() {
        // Open since 06:30; paused at 07:03 → 3 min inside the window.
        assertTrue(awake(unlock("07:01"), resumed("06:30", APP), paused("07:03", APP)))
        // Paused at 07:02 → only 2 min inside the window.
        assertFalse(awake(unlock("07:01"), resumed("06:30", APP), paused("07:02", APP)))
    }

    @Test fun `app resumed before from and never paused counts from from to to`() =
        assertTrue(awake(unlock("07:01"), resumed("06:30", APP)))

    @Test fun `interval open at to is counted up to to`() {
        assertTrue(awake(unlock("07:10"), resumed("07:17", APP)))
        assertFalse(awake(unlock("07:10"), resumed("07:17:01", APP)))
    }

    @Test fun `app resumed exactly at to adds nothing`() {
        val s = defaults.copy(minAppUsage = Duration.ofMinutes(1))
        assertFalse(awake(unlock("07:10"), resumed("07:20", APP), s = s))
    }

    @Test fun `pause exactly at from adds nothing`() {
        val s = defaults.copy(minAppUsage = Duration.ofMinutes(1))
        assertFalse(awake(unlock("07:10"), resumed("06:30", APP), paused("07:00", APP), s = s))
    }

    @Test fun `interval ending after to is clipped to to`() {
        assertTrue(awake(unlock("07:10"), resumed("07:17", APP), paused("07:30", APP)))
        assertFalse(awake(unlock("07:10"), resumed("07:18", APP), paused("07:30", APP)))
    }

    @Test fun `pause with no preceding resume is an interval starting at from`() {
        assertTrue(awake(unlock("07:01"), paused("07:03", APP)))
        assertFalse(awake(unlock("07:01"), paused("07:02", APP)))
    }

    // --- overlap ---

    @Test fun `overlapping intervals in two apps are not double-counted`() =
        // Split screen 07:05–07:07 in both apps: 2 min, not 4.
        assertFalse(
            awake(
                unlock("07:01"),
                resumed("07:05", APP), resumed("07:05", OTHER),
                paused("07:07", APP), paused("07:07", OTHER),
            ),
        )

    @Test fun `partly overlapping intervals are merged`() {
        // 07:05–07:07 and 07:06–07:08 → 3 min merged (4 if double-counted).
        val events = arrayOf(
            unlock("07:01"),
            resumed("07:05", APP), paused("07:07", APP),
            resumed("07:06", OTHER), paused("07:08", OTHER),
        )
        assertTrue(awake(*events))
        assertFalse(awake(*events, s = defaults.copy(minAppUsage = Duration.ofMinutes(4))))
    }

    @Test fun `an interval inside another adds nothing`() =
        assertFalse(
            awake(
                unlock("07:01"),
                resumed("07:05", APP), paused("07:07", APP),
                resumed("07:05:30", OTHER), paused("07:06:30", OTHER),
            ),
        )

    @Test fun `touching intervals add up`() =
        assertTrue(
            awake(
                unlock("07:01"),
                resumed("07:05", APP), paused("07:06", APP),
                resumed("07:06", OTHER), paused("07:08", OTHER),
            ),
        )

    // --- countScreenOnAsUnlock ---

    @Test fun `screen on is not an unlock by default`() =
        assertFalse(awake(screenOn("07:05"), screenOn("07:10")))

    @Test fun `screen on counts as unlock when countScreenOnAsUnlock`() =
        assertTrue(awake(screenOn("07:05"), screenOn("07:10"), screenOnIsUnlock = true))

    @Test fun `screen on and unlock both count when countScreenOnAsUnlock`() =
        assertTrue(awake(screenOn("07:05"), unlock("07:06"), screenOnIsUnlock = true))

    @Test fun `screen on enables rule b when countScreenOnAsUnlock`() {
        val events = arrayOf(screenOn("07:05"), resumed("07:06", APP), paused("07:09", APP))
        assertTrue(awake(*events, screenOnIsUnlock = true))
        assertFalse(awake(*events, screenOnIsUnlock = false))
    }

    @Test fun `screen on outside the window does not count when countScreenOnAsUnlock`() =
        assertFalse(awake(screenOn("06:59"), screenOn("07:10"), screenOnIsUnlock = true))

    // --- minAppUsage = 0 ---

    @Test fun `minAppUsage 0 disables rule b`() {
        val s = defaults.copy(minAppUsage = Duration.ZERO)
        assertFalse(awake(unlock("07:05"), resumed("07:01", APP), paused("07:19", APP), s = s))
        assertFalse(awake(unlock("07:05"), s = s))
    }

    @Test fun `minAppUsage 0 still allows rule a`() {
        val s = defaults.copy(minAppUsage = Duration.ZERO)
        assertTrue(awake(unlock("07:05"), unlock("07:06"), s = s))
    }

    // --- cases the spec left open (01-requirements A11) ---

    @Test fun `second resume without a pause keeps the first start`() {
        // Open 07:05 → 07:08 = 3 min. Restarting at 07:07 would give only 1 min.
        assertTrue(awake(unlock("07:01"), resumed("07:05", APP), resumed("07:07", APP), paused("07:08", APP)))
    }

    @Test fun `a second pause after the interval closed adds nothing`() {
        // ACTIVITY_PAUSED then ACTIVITY_STOPPED both map to APP_PAUSED: 2 min, not "from → 07:09".
        assertFalse(
            awake(unlock("07:01"), resumed("07:05", APP), paused("07:07", APP), paused("07:09", APP)),
        )
    }

    @Test fun `screen off ends open intervals`() =
        // Resumed at 07:05, screen off at 07:06, never paused: 1 min, not until `to`.
        assertFalse(awake(unlock("07:01"), resumed("07:05", APP), screenOff("07:06")))

    @Test fun `screen off ends an app that was open before the list starts`() =
        // First event for APP is a pause, but the screen went off at 07:01 before it.
        assertFalse(awake(unlock("07:00"), screenOff("07:01"), paused("07:10", APP)))

    @Test fun `use after screen on again counts`() =
        assertTrue(
            awake(
                resumed("07:02", APP), screenOff("07:03"),
                screenOn("07:10"), unlock("07:10"), resumed("07:10", APP), paused("07:12", APP),
            ),
        )

    @Test fun `screen off alone does not affect unlock counting`() =
        assertTrue(awake(unlock("07:05"), screenOff("07:06"), unlock("07:10")))

    @Test fun `from after to is never awake`() =
        assertFalse(
            WakeRule.isAwake(listOf(unlock("07:05"), unlock("07:06")), to, from, defaults, false, excluded),
        )

    // --- evaluate (counts for the T9 detection log) ---

    private fun check(vararg events: UsageEvt, s: Settings = defaults): WakeCheck =
        WakeRule.evaluate(events.toList(), from, to, s, false, excluded)

    @Test fun `evaluate reports unlocks and merged foreground time`() =
        assertEquals(
            WakeCheck(1, Duration.ofMinutes(3), awake = true),
            check(
                unlock("07:01"), unlock("06:50"),
                resumed("07:05", APP), paused("07:07", APP),
                resumed("07:06", OTHER), paused("07:08", OTHER),
                resumed("07:10", launcher), paused("07:15", launcher),
            ),
        )

    @Test fun `evaluate reports foreground time even when rule a decides`() =
        assertEquals(
            WakeCheck(2, Duration.ofMinutes(1), awake = true),
            check(unlock("07:01"), unlock("07:02"), resumed("07:05", APP), paused("07:06", APP)),
        )

    @Test fun `evaluate reports foreground time when rule b is disabled`() =
        assertEquals(
            WakeCheck(1, Duration.ofMinutes(10), awake = false),
            check(unlock("07:01"), resumed("07:05", APP), paused("07:15", APP), s = defaults.copy(minAppUsage = Duration.ZERO)),
        )

    @Test fun `isAwake matches evaluate`() {
        val events = listOf(unlock("07:01"), resumed("07:05", APP), paused("07:08", APP))
        for (minutes in 0L..5L) {
            val s = defaults.copy(minAppUsage = Duration.ofMinutes(minutes))
            assertEquals(
                WakeRule.evaluate(events, from, to, s, false, excluded).awake,
                WakeRule.isAwake(events, from, to, s, false, excluded),
            )
        }
    }

    private companion object {
        const val OWN = "nl.veerdonk.pillreminder"
        const val APP = "org.example.news"
        const val OTHER = "org.example.mail"

        fun t(hhmmss: String): Instant =
            Instant.parse("2026-10-13T${if (hhmmss.length == 5) "$hhmmss:00" else hhmmss}Z")

        fun unlock(at: String) = UsageEvt(t(at), Type.UNLOCK, "android")
        fun screenOn(at: String) = UsageEvt(t(at), Type.SCREEN_ON, "android")
        fun screenOff(at: String) = UsageEvt(t(at), Type.SCREEN_OFF, "android")
        fun resumed(at: String, pkg: String) = UsageEvt(t(at), Type.APP_RESUMED, pkg)
        fun paused(at: String, pkg: String) = UsageEvt(t(at), Type.APP_PAUSED, pkg)
    }
}
