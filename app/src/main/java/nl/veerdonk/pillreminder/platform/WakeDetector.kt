package nl.veerdonk.pillreminder.platform

import nl.veerdonk.pillreminder.core.model.Settings
import java.time.Instant

/**
 * Decides whether the user is awake, from the usage log in `[from, to]`
 * (docs/03-technical-design.md §4, §6). Returns false when it can't tell, e.g. without Usage Access.
 */
interface WakeDetector {
    suspend fun isAwake(from: Instant, to: Instant, settings: Settings): Boolean
}
