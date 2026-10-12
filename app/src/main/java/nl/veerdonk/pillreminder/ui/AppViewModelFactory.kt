package nl.veerdonk.pillreminder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import nl.veerdonk.pillreminder.AppContainer
import nl.veerdonk.pillreminder.ui.history.HistoryViewModel
import nl.veerdonk.pillreminder.ui.home.HomeViewModel
import nl.veerdonk.pillreminder.ui.settings.SettingsViewModel

/** The manual-DI ViewModel factory (no Hilt): builds each ViewModel from the [AppContainer]. */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(container)
        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(container)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container)
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    } as T
}
