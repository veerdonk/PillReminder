package nl.veerdonk.pillreminder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.AppContainer
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.Event
import java.time.LocalTime
import java.time.ZoneId

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** Re-evaluated every [REFRESH_MILLIS], so relative times ("Alarm in 6 min") stay fresh. */
    private val ticker = flow {
        while (true) {
            emit(container.clock.instant())
            delay(REFRESH_MILLIS)
        }
    }

    /** Pills on hand, or null when not tracked (FR-10). */
    val pillsLeft: StateFlow<Int?> = container.pillStock.pillsLeft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val model: StateFlow<HomeModel?> = combine(
        container.stateStore.state,
        container.settingsRepository.settings,
        ticker,
    ) { state, settings, now -> buildHomeModel(state, settings, now, ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** On the app scope, so leaving the screen doesn't cancel the event halfway. */
    private fun send(event: Event) {
        container.appScope.launch { container.reminderController.handle(event) }
    }

    fun tookDoseNow(dose: Int) = send(Event.TookDose(dose, container.clock.instant(), DoseSource.APP_NOW))

    /**
     * Validates a picked time (FR-5.2) and sends the event when it's valid. Returns the result so
     * the dialog can show the error; nothing is sent for an invalid time.
     */
    fun tookDoseAt(time: LocalTime): BackdateResult {
        val m = model.value ?: return BackdateResult.Invalid(BackdateError.IN_FUTURE, container.clock.instant())
        val dose = m.actions.recordDose ?: return BackdateResult.Invalid(BackdateError.IN_FUTURE, container.clock.instant())
        val result = resolveBackdate(time, m.state, m.settings, container.clock.instant(), ZoneId.systemDefault())
        if (result is BackdateResult.Valid) send(Event.TookDose(dose, result.at, DoseSource.APP_BACKDATED))
        return result
    }

    fun skip() {
        val dose = model.value?.state?.nextDose ?: return
        send(Event.SkipRest(dose))
    }

    fun remindNow() = send(Event.RemindNow)

    fun resumeDay() = send(Event.ResumeDay)

    /**
     * Sets the pill count, or stops counting with null, then ticks so a showing reminder gains
     * or loses its "running low" line (FR-10.3).
     */
    fun setPillsLeft(count: Int?) {
        container.appScope.launch {
            container.pillStock.set(count)
            container.reminderController.tick()
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 15_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
