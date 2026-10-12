package nl.veerdonk.pillreminder.core.wake

import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.wake.UsageEvt.Type
import java.time.Duration
import java.time.Instant

/** The pure "am I awake?" rule from 01-requirements FR-1.3 and design §6. */
object WakeRule {

    /**
     * True when [events] show the user awake in `[from, to]` (both ends included):
     * at least `minUnlocks` unlocks, or (if `minAppUsage > 0`) at least 1 unlock plus
     * `minAppUsage` of merged foreground time in packages not in [excludedPackages].
     *
     * [events] may be unsorted and may contain events before [from]; the caller queries from
     * `from − 1 h` so apps already open at [from] can be paired. Gaps the spec leaves open are
     * handled as in 01-requirements A11.
     */
    fun isAwake(
        events: List<UsageEvt>,
        from: Instant,
        to: Instant,
        s: Settings,
        countScreenOnAsUnlock: Boolean,
        excludedPackages: Set<String>,
    ): Boolean {
        val sorted = events.sortedBy { it.time } // stable: ties keep the source order
        val unlocks = sorted.count { it.isUnlock(countScreenOnAsUnlock) && it.time in from..to }
        if (unlocks >= s.minUnlocks) return true
        if (s.minAppUsage.isZero || unlocks < 1) return false
        return foregroundTime(sorted, from, to, excludedPackages) >= s.minAppUsage
    }

    private fun UsageEvt.isUnlock(countScreenOn: Boolean): Boolean =
        type == Type.UNLOCK || (countScreenOn && type == Type.SCREEN_ON)

    /** Merged foreground time in `[from, to]`; [sorted] must be in time order. */
    private fun foregroundTime(
        sorted: List<UsageEvt>,
        from: Instant,
        to: Instant,
        excluded: Set<String>,
    ): Duration {
        if (!from.isBefore(to)) return Duration.ZERO

        // A package whose first event is a pause was already open before the list starts.
        val open = HashMap<String, Instant>()
        sorted.filter { it.isAppEvent() && it.pkg !in excluded }
            .distinctBy { it.pkg }
            .filter { it.type == Type.APP_PAUSED }
            .forEach { open[it.pkg] = Instant.MIN }

        val intervals = mutableListOf<Pair<Instant, Instant>>()
        for (e in sorted) {
            when (e.type) {
                Type.APP_RESUMED -> if (e.pkg !in excluded) open.putIfAbsent(e.pkg, e.time)
                Type.APP_PAUSED -> open.remove(e.pkg)?.let { intervals += it to e.time }
                Type.SCREEN_OFF -> {
                    open.values.forEach { intervals += it to e.time }
                    open.clear()
                }
                Type.UNLOCK, Type.SCREEN_ON -> Unit
            }
        }
        open.values.forEach { intervals += it to to }

        var total = Duration.ZERO
        var curStart: Instant? = null
        var curEnd: Instant? = null
        val clipped = intervals
            .map { (start, end) -> maxOf(start, from) to minOf(end, to) }
            .filter { (start, end) -> start.isBefore(end) }
            .sortedBy { it.first }
        for ((start, end) in clipped) {
            if (curEnd == null || start.isAfter(curEnd)) {
                if (curStart != null) total += Duration.between(curStart, curEnd)
                curStart = start
                curEnd = end
            } else if (end.isAfter(curEnd)) {
                curEnd = end
            }
        }
        if (curStart != null) total += Duration.between(curStart, curEnd)
        return total
    }

    private fun UsageEvt.isAppEvent(): Boolean = type == Type.APP_RESUMED || type == Type.APP_PAUSED
}
