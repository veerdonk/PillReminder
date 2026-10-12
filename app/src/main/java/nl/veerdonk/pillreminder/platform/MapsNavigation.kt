package nl.veerdonk.pillreminder.platform

/**
 * The parts of a posted notification that decide whether it is Google Maps' navigation
 * notification. A plain value, so the rule can be unit-tested without Android.
 */
data class NotificationFacts(
    val key: String,
    val packageName: String,
    val id: Int,
    val ongoing: Boolean,
    val category: String?,
    val channelId: String?,
) {
    fun describe(): String = "id=$id channel=${channelId ?: "-"} category=${category ?: "-"}"
}

object MapsNavigation {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    /** `Notification.CATEGORY_NAVIGATION`, spelled out so this stays free of Android types. */
    const val CATEGORY_NAVIGATION = "navigation"

    /**
     * Maps shows an ongoing notification while it navigates. It also posts other ongoing ones
     * (location sharing, for example), so the category or channel must say "navigation" (A58).
     */
    fun isNavigation(n: NotificationFacts): Boolean =
        n.packageName == MAPS_PACKAGE &&
            n.ongoing &&
            (n.category == CATEGORY_NAVIGATION || n.channelId?.contains("navigation", ignoreCase = true) == true)

    /** Ongoing Maps notifications are listed in Settings → Advanced, to check the rule on the phone. */
    fun isOngoingMaps(n: NotificationFacts): Boolean = n.packageName == MAPS_PACKAGE && n.ongoing
}

/** What the listener knows right now, for [AndroidNavigationDetector] and Settings → Advanced. */
data class NavigationStatus(
    /** The system has bound the listener: notification access is granted. */
    val connected: Boolean = false,
    /** Keys of the active Maps navigation notifications. */
    val navigationKeys: Set<String> = emptySet(),
    /** The active ongoing Maps notifications, described, keyed by notification key. */
    val ongoingMaps: Map<String, String> = emptyMap(),
) {
    val navigating: Boolean get() = connected && navigationKeys.isNotEmpty()

    fun connected(active: List<NotificationFacts>): NavigationStatus =
        active.fold(NavigationStatus(connected = true)) { status, n -> status.posted(n) }

    fun disconnected(): NavigationStatus = NavigationStatus()

    fun posted(n: NotificationFacts): NavigationStatus = copy(
        navigationKeys = if (MapsNavigation.isNavigation(n)) navigationKeys + n.key else navigationKeys - n.key,
        ongoingMaps = if (MapsNavigation.isOngoingMaps(n)) ongoingMaps + (n.key to n.describe()) else ongoingMaps - n.key,
    )

    fun removed(key: String): NavigationStatus =
        copy(navigationKeys = navigationKeys - key, ongoingMaps = ongoingMaps - key)

    /** One fact per line, for a monospace text block. */
    fun describe(): String = buildString {
        append("listener: ").append(if (connected) "connected" else "not connected").append('\n')
        append("navigating: ").append(if (navigating) "yes" else "no")
        ongoingMaps.values.sorted().forEach { append("\nmaps: ").append(it) }
    }
}
