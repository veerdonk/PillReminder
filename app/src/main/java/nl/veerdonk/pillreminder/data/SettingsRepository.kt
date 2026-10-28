package nl.veerdonk.pillreminder.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import nl.veerdonk.pillreminder.core.model.Settings
import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalTime

/** Persistent user settings (docs/03-technical-design.md §3.1, §4). */
interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun current(): Settings
    suspend fun update(s: Settings)
}

/**
 * [SettingsRepository] on a Preferences DataStore. Each field is its own key: durations as
 * whole minutes, times as minute-of-day, `alarmSoundUri` and `pharmacyPhone` as strings. A
 * missing key reads as the default from `Settings()`; a missing string key reads as null.
 *
 * If the stored values don't form valid settings (for example after a downgrade, or a value out
 * of range), the whole object falls back to `Settings()` defaults (assumption A12 in
 * docs/01-requirements.md §6). A file that can't be read at all also yields the defaults.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<Settings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)
        .distinctUntilChanged()

    override suspend fun current(): Settings = settings.first()

    /**
     * Persists [s].
     *
     * @throws IllegalArgumentException if `s.validate()` reports any error. Nothing is written
     *   in that case; the UI is expected to validate before calling this.
     */
    override suspend fun update(s: Settings) {
        val errors = s.validate()
        require(errors.isEmpty()) { "Invalid settings: ${errors.joinToString("; ")}" }
        dataStore.edit { p -> write(p, s) }
    }

    private fun write(p: MutablePreferences, s: Settings) {
        p[Keys.WEEKDAY_EARLIEST] = s.weekdayEarliestTime.minuteOfDay()
        p[Keys.WEEKDAY_FALLBACK] = s.weekdayFallbackTime.minuteOfDay()
        p[Keys.WEEKEND_EARLIEST] = s.weekendEarliestTime.minuteOfDay()
        p[Keys.WEEKEND_FALLBACK] = s.weekendFallbackTime.minuteOfDay()
        p[Keys.MIN_UNLOCKS] = s.minUnlocks
        p[Keys.MIN_APP_USAGE] = s.minAppUsage.wholeMinutes()
        p[Keys.DETECTION_WINDOW] = s.detectionWindow.wholeMinutes()
        p[Keys.DETECTION_POLL_INTERVAL] = s.detectionPollInterval.wholeMinutes()
        p[Keys.DOSES_PER_DAY] = s.dosesPerDay
        p[Keys.DOSE_INTERVAL] = s.doseInterval.wholeMinutes()
        p[Keys.ESCALATION_DELAY] = s.escalationDelay.wholeMinutes()
        p[Keys.SNOOZE_DURATION] = s.snoozeDuration.wholeMinutes()
        p[Keys.MAX_SNOOZES] = s.maxSnoozes
        p[Keys.ALARM_RING_DURATION] = s.alarmRingDuration.wholeMinutes()
        p[Keys.HOLD_FIRST_ALARM] = s.holdFirstAlarmUntilFallback
        p[Keys.DAY_ROLLOVER] = s.dayRolloverTime.minuteOfDay()
        p[Keys.SHOW_STATUS_NOTIFICATION] = s.showStatusNotification
        p[Keys.REMINDER_VIBRATE] = s.reminderVibrate
        p[Keys.REMINDER_CHIME] = s.reminderChime
        val uri = s.alarmSoundUri
        if (uri == null) p.remove(Keys.ALARM_SOUND_URI) else p[Keys.ALARM_SOUND_URI] = uri
        p[Keys.BOTTLE_SIZE] = s.bottleSize
        val phone = s.pharmacyPhone
        if (phone == null) p.remove(Keys.PHARMACY_PHONE) else p[Keys.PHARMACY_PHONE] = phone
    }

    private fun toSettings(p: Preferences): Settings {
        val d = DEFAULTS
        val stored = try {
            Settings(
                weekdayEarliestTime = p[Keys.WEEKDAY_EARLIEST]?.toLocalTime() ?: d.weekdayEarliestTime,
                weekdayFallbackTime = p[Keys.WEEKDAY_FALLBACK]?.toLocalTime() ?: d.weekdayFallbackTime,
                weekendEarliestTime = p[Keys.WEEKEND_EARLIEST]?.toLocalTime() ?: d.weekendEarliestTime,
                weekendFallbackTime = p[Keys.WEEKEND_FALLBACK]?.toLocalTime() ?: d.weekendFallbackTime,
                minUnlocks = p[Keys.MIN_UNLOCKS] ?: d.minUnlocks,
                minAppUsage = p[Keys.MIN_APP_USAGE]?.minutes() ?: d.minAppUsage,
                detectionWindow = p[Keys.DETECTION_WINDOW]?.minutes() ?: d.detectionWindow,
                detectionPollInterval = p[Keys.DETECTION_POLL_INTERVAL]?.minutes() ?: d.detectionPollInterval,
                dosesPerDay = p[Keys.DOSES_PER_DAY] ?: d.dosesPerDay,
                doseInterval = p[Keys.DOSE_INTERVAL]?.minutes() ?: d.doseInterval,
                escalationDelay = p[Keys.ESCALATION_DELAY]?.minutes() ?: d.escalationDelay,
                snoozeDuration = p[Keys.SNOOZE_DURATION]?.minutes() ?: d.snoozeDuration,
                maxSnoozes = p[Keys.MAX_SNOOZES] ?: d.maxSnoozes,
                alarmRingDuration = p[Keys.ALARM_RING_DURATION]?.minutes() ?: d.alarmRingDuration,
                holdFirstAlarmUntilFallback = p[Keys.HOLD_FIRST_ALARM] ?: d.holdFirstAlarmUntilFallback,
                dayRolloverTime = p[Keys.DAY_ROLLOVER]?.toLocalTime() ?: d.dayRolloverTime,
                alarmSoundUri = p[Keys.ALARM_SOUND_URI],
                showStatusNotification = p[Keys.SHOW_STATUS_NOTIFICATION] ?: d.showStatusNotification,
                reminderVibrate = p[Keys.REMINDER_VIBRATE] ?: d.reminderVibrate,
                reminderChime = p[Keys.REMINDER_CHIME] ?: d.reminderChime,
                pharmacyPhone = p[Keys.PHARMACY_PHONE],
                bottleSize = p[Keys.BOTTLE_SIZE] ?: d.bottleSize,
            )
        } catch (_: DateTimeException) {
            return d // a minute-of-day outside 0..1439
        } catch (_: ClassCastException) {
            return d // a key stored with another type by a different build
        }
        return if (stored.validate().isEmpty()) stored else d
    }

    private object Keys {
        val WEEKDAY_EARLIEST = intPreferencesKey("weekday_earliest_time")
        val WEEKDAY_FALLBACK = intPreferencesKey("weekday_fallback_time")
        val WEEKEND_EARLIEST = intPreferencesKey("weekend_earliest_time")
        val WEEKEND_FALLBACK = intPreferencesKey("weekend_fallback_time")
        val MIN_UNLOCKS = intPreferencesKey("min_unlocks")
        val MIN_APP_USAGE = intPreferencesKey("min_app_usage_minutes")
        val DETECTION_WINDOW = intPreferencesKey("detection_window_minutes")
        val DETECTION_POLL_INTERVAL = intPreferencesKey("detection_poll_interval_minutes")
        val DOSES_PER_DAY = intPreferencesKey("doses_per_day")
        val DOSE_INTERVAL = intPreferencesKey("dose_interval_minutes")
        val ESCALATION_DELAY = intPreferencesKey("escalation_delay_minutes")
        val SNOOZE_DURATION = intPreferencesKey("snooze_duration_minutes")
        val MAX_SNOOZES = intPreferencesKey("max_snoozes")
        val ALARM_RING_DURATION = intPreferencesKey("alarm_ring_duration_minutes")
        val HOLD_FIRST_ALARM = booleanPreferencesKey("hold_first_alarm_until_fallback")
        val DAY_ROLLOVER = intPreferencesKey("day_rollover_time")
        val ALARM_SOUND_URI = stringPreferencesKey("alarm_sound_uri")
        val SHOW_STATUS_NOTIFICATION = booleanPreferencesKey("show_status_notification")
        val REMINDER_VIBRATE = booleanPreferencesKey("reminder_vibrate")
        val REMINDER_CHIME = booleanPreferencesKey("reminder_chime")
        val PHARMACY_PHONE = stringPreferencesKey("pharmacy_phone")
        val BOTTLE_SIZE = intPreferencesKey("bottle_size")
    }

    companion object {
        const val FILE_NAME = "settings"

        /**
         * Creates the Preferences DataStore for [file]. A corrupt file is replaced by empty
         * preferences (that is, defaults). Create only one per file per process.
         */
        fun createDataStore(
            file: () -> File,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )

        private val DEFAULTS = Settings()

        private fun LocalTime.minuteOfDay(): Int = hour * 60 + minute
        private fun Int.toLocalTime(): LocalTime = LocalTime.ofSecondOfDay(this * 60L)
        private fun Duration.wholeMinutes(): Int = toMinutes().toInt() // validate() guarantees whole, small minutes
        private fun Int.minutes(): Duration = Duration.ofMinutes(toLong())
    }
}
