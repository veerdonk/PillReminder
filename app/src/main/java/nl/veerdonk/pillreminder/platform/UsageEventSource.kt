package nl.veerdonk.pillreminder.platform

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import nl.veerdonk.pillreminder.core.wake.UsageEvt
import java.time.Duration
import java.time.Instant

/**
 * Reads the usage log and maps it to [UsageEvt]s (docs/03-technical-design.md §6).
 * The caller must hold Usage Access; without it the log is empty.
 */
class UsageEventSource(context: Context) {

    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    /**
     * Events in `[from − 1 h, to]`, in the usage log's order. The extra hour lets the rule pair
     * apps that were already in the foreground at [from].
     */
    fun query(from: Instant, to: Instant): List<UsageEvt> {
        val events = usageStatsManager.queryEvents((from - LOOKBACK).toEpochMilli(), to.toEpochMilli())
            ?: return emptyList()
        val result = mutableListOf<UsageEvt>()
        val raw = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(raw)
            val type = mapEventType(raw.eventType) ?: continue
            result += UsageEvt(Instant.ofEpochMilli(raw.timeStamp), type, raw.packageName.orEmpty())
        }
        return result
    }

    companion object {
        val LOOKBACK: Duration = Duration.ofHours(1)

        /**
         * The §6 table. Everything else is dropped, in particular `ACTIVITY_STOPPED`: a late
         * STOPPED of activity A would end the app's interval while activity B is still in front.
         */
        fun mapEventType(rawType: Int): UsageEvt.Type? = when (rawType) {
            UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEvt.Type.UNLOCK
            UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEvt.Type.SCREEN_ON
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEvt.Type.SCREEN_OFF
            UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvt.Type.APP_RESUMED
            UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvt.Type.APP_PAUSED
            else -> null
        }
    }
}
