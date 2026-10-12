package nl.veerdonk.pillreminder.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * [NavigationDetector] fed by [MapsNavigationListener] (design §6a). Calls [onNavigationChanged]
 * when navigation starts or ends, so the controller can tick at once instead of waiting for
 * the next wake-up.
 */
class AndroidNavigationDetector(private val onNavigationChanged: () -> Unit) : NavigationDetector {

    private val _status = MutableStateFlow(NavigationStatus())

    /** Live status, shown in Settings → Advanced. */
    val status: StateFlow<NavigationStatus> = _status

    override fun isNavigating(): Boolean = _status.value.navigating

    fun update(change: (NavigationStatus) -> NavigationStatus) {
        val before = _status.value.navigating
        _status.update(change)
        if (_status.value.navigating != before) onNavigationChanged()
    }
}
