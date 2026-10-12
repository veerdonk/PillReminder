package nl.veerdonk.pillreminder.core.wake

import java.time.Duration

/** The result of [WakeRule.evaluate]: the inputs to the decision and the decision itself. */
data class WakeCheck(val unlocks: Int, val foregroundTime: Duration, val awake: Boolean)
