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

/** Placeholder until T9 wires `AndroidWakeDetector`: never detects a wake-up, so only the fallback fires. */
object NoOpWakeDetector : WakeDetector {
    override suspend fun isAwake(from: Instant, to: Instant, settings: Settings): Boolean = false
}
