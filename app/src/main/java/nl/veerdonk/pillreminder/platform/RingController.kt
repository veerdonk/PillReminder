package nl.veerdonk.pillreminder.platform

import nl.veerdonk.pillreminder.core.model.RingSpec

/**
 * Starts and stops the alarm ring service (docs/03-technical-design.md §4, §8). Both calls are
 * idempotent: ringing for the same dose again, or stopping while stopped, does nothing.
 */
interface RingController {
    fun ensureRinging(spec: RingSpec)
    fun ensureStopped()
}

/** Placeholder until T8 wires the ring service: never rings. */
object NoOpRingController : RingController {
    override fun ensureRinging(spec: RingSpec) = Unit
    override fun ensureStopped() = Unit
}
