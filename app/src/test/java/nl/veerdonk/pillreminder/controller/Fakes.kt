package nl.veerdonk.pillreminder.controller

import android.app.Notification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.veerdonk.pillreminder.core.model.DayState
import nl.veerdonk.pillreminder.core.model.ReminderSpec
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.core.model.StatusSpec
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.model.PillStock
import nl.veerdonk.pillreminder.data.PillStockRepository
import nl.veerdonk.pillreminder.data.SettingsRepository
import nl.veerdonk.pillreminder.data.StateStore
import nl.veerdonk.pillreminder.data.history.DayWithDoses
import nl.veerdonk.pillreminder.data.history.HistoryRepository
import nl.veerdonk.pillreminder.platform.AlarmScheduler
import nl.veerdonk.pillreminder.platform.Notifier
import nl.veerdonk.pillreminder.platform.RingController
import nl.veerdonk.pillreminder.platform.NavigationDetector
import nl.veerdonk.pillreminder.platform.WakeDetector
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** One ordered log shared by all fakes, so tests can check the order of side effects. */
class CallLog {
    val calls = mutableListOf<Call>()
    fun names(): List<String> = calls.map { it.name }
}

sealed interface Call {
    val name: String get() = this::class.simpleName!!

    data class Load(val returned: DayState?) : Call
    data class Save(val state: DayState) : Call
    data class Upsert(val day: DayState) : Call
    data class IsAwake(val from: Instant, val to: Instant) : Call
    data class ShowReminder(val spec: ReminderSpec) : Call
    data object CancelReminder : Call
    data class ShowStatus(val spec: StatusSpec) : Call
    data object CancelStatus : Call
    data class EnsureRinging(val spec: RingSpec) : Call
    data object EnsureStopped : Call
    data class Schedule(val at: Instant, val userVisible: Boolean) : Call
    data object Cancel : Call
}

class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
}

class FakeStateStore(private val log: CallLog, initial: DayState?) : StateStore {
    private val flow = MutableStateFlow(initial)
    var stored: DayState? = initial
        private set

    /** When set, `load()` suspends on it before returning (for the mutex test). */
    var loadGate: (suspend () -> Unit)? = null

    override val state: Flow<DayState?> = flow
    override suspend fun load(): DayState? {
        log.calls += Call.Load(stored)
        loadGate?.invoke()
        return stored
    }

    override suspend fun save(s: DayState) {
        log.calls += Call.Save(s)
        stored = s
        flow.value = s
    }
}

class FakeSettingsRepository(var value: Settings = Settings()) : SettingsRepository {
    override val settings: Flow<Settings> get() = MutableStateFlow(value)
    override suspend fun current(): Settings = value
    override suspend fun update(s: Settings) {
        require(s.validate().isEmpty())
        value = s
    }
}

class FakeHistoryRepository(private val log: CallLog) : HistoryRepository {
    val upserts = mutableListOf<DayState>()
    override fun days(): Flow<List<DayWithDoses>> = MutableStateFlow(emptyList())
    override suspend fun upsert(day: DayState) {
        log.calls += Call.Upsert(day)
        upserts += day
    }
}

/** Answers from [answers] in order, then `false`. */
class FakeWakeDetector(private val log: CallLog, vararg answers: Boolean) : WakeDetector {
    private val answers = ArrayDeque(answers.toList())
    var calls = 0
        private set

    override suspend fun isAwake(from: Instant, to: Instant, settings: Settings): Boolean {
        calls++
        log.calls += Call.IsAwake(from, to)
        return answers.removeFirstOrNull() ?: false
    }
}

class FakeNavigationDetector(var navigating: Boolean = false) : NavigationDetector {
    override fun isNavigating(): Boolean = navigating
}

class FakeNotifier(private val log: CallLog) : Notifier {
    override fun ensureChannels() = Unit
    override fun showReminder(spec: ReminderSpec) {
        log.calls += Call.ShowReminder(spec)
    }

    override fun cancelReminder() {
        log.calls += Call.CancelReminder
    }

    override fun showStatus(spec: StatusSpec) {
        log.calls += Call.ShowStatus(spec)
    }

    override fun cancelStatus() {
        log.calls += Call.CancelStatus
    }

    override fun buildAlarmNotification(spec: RingSpec): Notification =
        throw UnsupportedOperationException("not used by the controller")
}

class FakeRingController(private val log: CallLog) : RingController {
    override fun ensureRinging(spec: RingSpec) {
        log.calls += Call.EnsureRinging(spec)
    }

    override fun ensureStopped() {
        log.calls += Call.EnsureStopped
    }
}

class FakeAlarmScheduler(private val log: CallLog) : AlarmScheduler {
    override fun schedule(at: Instant, userVisible: Boolean) {
        log.calls += Call.Schedule(at, userVisible)
    }

    override fun cancel() {
        log.calls += Call.Cancel
    }
}

class FakePillStock(initial: Int? = null) : PillStockRepository {
    private val flow = MutableStateFlow(initial)
    override val pillsLeft: Flow<Int?> = flow
    override suspend fun current(): Int? = flow.value
    override suspend fun set(count: Int?) {
        flow.value = count
    }

    override suspend fun take(count: Int) {
        flow.value = flow.value?.let { PillStock.afterTaking(it, count) }
    }
}
