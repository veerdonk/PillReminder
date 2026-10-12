package nl.veerdonk.pillreminder.core.model

import java.time.Instant

/** Inputs to the engine (02-state-machine §4). */
sealed interface Event {
    /** Time-based transitions. [wakeDetected] is only meaningful in DETECTING. */
    data class Tick(val wakeDetected: Boolean) : Event

    data class TookDose(val dose: Int, val at: Instant, val source: DoseSource) : Event

    data class SkipRest(val dose: Int) : Event

    data class Snooze(val dose: Int) : Event

    data object RemindNow : Event

    data object ResumeDay : Event
}

/** Result of a reduce step. [finishedDay] is set when the event caused a rollover. */
data class Transition(val state: DayState, val finishedDay: DayState?)
