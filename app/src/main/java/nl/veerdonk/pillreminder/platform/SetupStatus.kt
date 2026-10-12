package nl.veerdonk.pillreminder.platform

/**
 * One row of the setup checklist (docs/03-technical-design.md §9). [hard] items are the ones
 * without which reminders can't work at all (notifications, exact alarms); the rest degrade
 * the app or only make it more reliable. [optional] items switch on an extra feature: they
 * never count as missing, so they don't raise the Home banner.
 */
enum class SetupItem(val hard: Boolean, val optional: Boolean = false) {
    NOTIFICATIONS(hard = true),
    EXACT_ALARMS(hard = true),
    USAGE_ACCESS(hard = false),
    FULL_SCREEN_INTENT(hard = false),
    BATTERY_OPTIMISATION(hard = false),
    MAPS_NAVIGATION(hard = false, optional = true),
    ONEPLUS_EXTRAS(hard = false),
}

/**
 * Result of one permission check: the items that are fine. All decisions about what to show
 * live here, as pure functions, so they can be unit-tested without Android.
 */
data class SetupStatus(val ok: Set<SetupItem>) {

    fun isOk(item: SetupItem): Boolean = item in ok

    /** Every item that needs attention, in checklist order. Optional items never do. */
    val missing: List<SetupItem> = SetupItem.entries.filter { it !in ok && !it.optional }

    /** The missing items that are hard requirements. */
    val missingHard: List<SetupItem> = missing.filter { it.hard }

    /** FR-6.3: the Home banner shows when anything at all is missing. */
    val showBanner: Boolean get() = missing.isNotEmpty()

    /**
     * FR-9.1 / design §10: Setup opens by itself on first launch ([setupSeen] false) and
     * while a hard requirement is missing.
     */
    fun isSetupNeeded(setupSeen: Boolean): Boolean = !setupSeen || missingHard.isNotEmpty()

    companion object {
        val ALL_OK = SetupStatus(SetupItem.entries.toSet())
    }
}
