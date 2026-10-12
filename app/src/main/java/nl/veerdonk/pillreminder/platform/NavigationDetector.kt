package nl.veerdonk.pillreminder.platform

/**
 * Says whether Google Maps is navigating right now (FR-3.8, docs/03-technical-design.md §4, §6a).
 * Returns false when it can't tell, e.g. without notification access, so the alarm still rings.
 */
interface NavigationDetector {
    fun isNavigating(): Boolean
}
