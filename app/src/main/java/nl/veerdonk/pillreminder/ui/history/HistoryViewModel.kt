package nl.veerdonk.pillreminder.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import nl.veerdonk.pillreminder.AppContainer
import nl.veerdonk.pillreminder.core.time.TimeMath
import java.time.ZoneId

class HistoryViewModel(container: AppContainer) : ViewModel() {

    /** Null until the first load, then the rows newest first. */
    val rows: StateFlow<List<HistoryRow>?> = combine(
        container.historyRepository.days(),
        container.stateStore.state,
        container.settingsRepository.settings,
    ) { days, state, settings ->
        val today = TimeMath.dayIdOf(container.clock.instant(), settings, ZoneId.systemDefault())
        buildHistoryRows(days, today, settings.dosesPerDay, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
