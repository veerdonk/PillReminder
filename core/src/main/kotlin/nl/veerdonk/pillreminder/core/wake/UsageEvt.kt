package nl.veerdonk.pillreminder.core.wake

import java.time.Instant

/** A usage-log event, mapped from `UsageStatsManager` by `UsageEventSource` (design §6). */
data class UsageEvt(val time: Instant, val type: Type, val pkg: String) {
    enum class Type { UNLOCK, SCREEN_ON, SCREEN_OFF, APP_RESUMED, APP_PAUSED }
}
