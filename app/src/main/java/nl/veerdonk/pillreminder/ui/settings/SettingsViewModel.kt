package nl.veerdonk.pillreminder.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.AppContainer
import nl.veerdonk.pillreminder.core.model.Settings

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    /** Null until the stored settings are loaded. */
    var form: SettingsForm? by mutableStateOf(null)
        private set

    /** The stored settings the form started from (or was last saved as), to tell edits apart. */
    var saved: Settings? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            val current = container.settingsRepository.current()
            saved = current
            form = SettingsForm.from(current)
        }
    }

    fun edit(change: (SettingsForm) -> SettingsForm) {
        form = form?.let(change)
    }

    /** Saves the form; a no-op while it is invalid. Changes then tick through the container. */
    fun save() {
        val settings = form?.parse()?.settings ?: return
        saved = settings
        container.appScope.launch { container.settingsRepository.update(settings) }
    }

    /** "Reset to defaults" (after the user confirmed): saves `Settings()` at once. */
    fun resetToDefaults() {
        val defaults = Settings()
        form = SettingsForm.from(defaults)
        saved = defaults
        container.appScope.launch { container.settingsRepository.update(defaults) }
    }
}
