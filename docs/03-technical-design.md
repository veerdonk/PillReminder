# 03 — Technical design

## 1. Stack

| Concern | Choice |
|---------|--------|
| Language | Kotlin 2.x, bytecode target JVM 17. Builds on JDK 17+; CI uses 17. |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`). Use the Gradle wrapper. AGP 9 compiles Kotlin in `:app` through its built-in Kotlin support, so **don't apply `org.jetbrains.kotlin.android`**. Only the Compose compiler plugin is applied there. |
| SDK levels | `minSdk = 33`, `compileSdk`/`targetSdk` = latest stable at implementation time |
| UI | Jetpack Compose (BOM), Material 3, Navigation Compose |
| Async | Kotlin coroutines + Flow |
| Settings | Jetpack DataStore (Preferences) |
| Day state | Jetpack DataStore holding one JSON document (kotlinx.serialization) |
| History | Room (with KSP) |
| DI | Manual: `AppContainer` created in `Application`. No Hilt. |
| Scheduling | `AlarmManager`. Not WorkManager: its 15-minute minimum period and inexact timing don't fit. |
| Tests | JUnit (4 or 5), kotlinx-coroutines-test. Robolectric is optional for receivers and Room. |
| CI | GitHub Actions: lint, unit tests and debug APK as a downloadable artifact |

Application ID / package: `nl.veerdonk.pillreminder`.

## 2. Modules and package layout

There are two Gradle modules. The pure logic is in a plain JVM module, so it builds and tests
without the Android SDK.

```
:core   (kotlin("jvm") — NO Android dependencies)
  nl.veerdonk.pillreminder.core
    model/       Settings, DayState, DoseRecord, enums, Event, Transition, Outputs, ReminderSpec, ReminderSubtitle, RingSpec
    time/        TimeMath: dayIdOf, startOfDay, earliestAt, fallbackAt, nextRolloverAt, isWeekend
    engine/      Engine.reduce(), Engine.outputs()           (02-state-machine.md)
    wake/        UsageEvt, WakeRule.isAwake()                 (§6)
    serial/      kotlinx.serialization serializers for Instant/LocalDate/LocalTime/Duration (ISO-8601
                 strings), and CoreJson, the shared Json config (ignoreUnknownKeys, encodeDefaults)

:app    (com.android.application, depends on :core)
  nl.veerdonk.pillreminder
    PillReminderApp.kt          Application; creates AppContainer; ensures notification channels
    AppContainer.kt             Wires repositories + platform services + controller
    controller/
      ReminderController.kt     The single entry point for events (02 §6). Mutex-guarded.
    data/
      SettingsRepository.kt     DataStore <-> core Settings, Flow<Settings>
      StateStore.kt             DataStore JSON <-> DayState, Flow<DayState?>
      history/                  Room: HistoryDatabase, DayLogEntity, DoseLogEntity, HistoryDao, HistoryRepository
    platform/
      AlarmScheduler.kt         AlarmManager wrapper (§5)
      Notifier.kt               Channels + reminder notification (§7)
      RingController.kt         Starts/stops AlarmRingService
      AlarmRingService.kt       Foreground service: sound + vibration + wake lock (§8)
      UsageEventSource.kt       UsageStatsManager -> List<UsageEvt> (§6)
      AndroidWakeDetector.kt    UsageEventSource + WakeRule
      PermissionChecker.kt      Status of each required permission/setting (§9)
      receivers/
        WakeupReceiver.kt       AlarmManager fires -> controller.handle(Tick)
        ActionReceiver.kt       Notification buttons -> TookDose/SkipRest/Snooze
        SystemEventsReceiver.kt BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED -> Tick
    ui/
      MainActivity.kt           NavHost: Home, History, Settings, Setup
      theme/
      home/                     HomeScreen + HomeViewModel (status, record dose, actions)
      history/                  HistoryScreen + HistoryViewModel
      settings/                 SettingsScreen + SettingsViewModel
      setup/                    SetupScreen (permission checklist) + OnePlus guide
      alarm/                    AlarmActivity (full-screen, over lock screen)
```

## 3. Data model

### 3.1 Settings (`:core`)
```kotlin
@Serializable
data class Settings(
    val weekdayEarliestTime: LocalTime = LocalTime.of(7, 0),
    val weekdayFallbackTime: LocalTime = LocalTime.of(10, 30),
    val weekendEarliestTime: LocalTime = LocalTime.of(8, 30),
    val weekendFallbackTime: LocalTime = LocalTime.of(11, 30),
    val minUnlocks: Int = 2,
    val minAppUsage: Duration = Duration.ofMinutes(3),
    val detectionWindow: Duration = Duration.ofMinutes(20),
    val detectionPollInterval: Duration = Duration.ofMinutes(5),
    val dosesPerDay: Int = 3,
    val doseInterval: Duration = Duration.ofMinutes(230),
    val escalationDelay: Duration = Duration.ofMinutes(15),
    val snoozeDuration: Duration = Duration.ofMinutes(10),
    val maxSnoozes: Int = 3,
    val alarmRingDuration: Duration = Duration.ofMinutes(2),
    val dayRolloverTime: LocalTime = LocalTime.of(3, 0),
    val alarmSoundUri: String? = null,          // null = system default alarm
) {
    fun validate(): List<String>                 // human-readable errors; empty = valid (ranges in 01 §FR-8)
}
```
`SettingsRepository` stores each field as a separate Preferences key. Durations are stored as
minutes and times as minute-of-day. Invalid input is rejected in the UI and never persisted.

### 3.2 Day state
Defined in `02-state-machine.md` §2. It is persisted as JSON in its own DataStore file,
`day_state.json`. If the JSON is missing or can't be parsed, `StateStore` returns `null` (it never
throws for it) and the caller starts with `fresh(dayIdOf(now))`.

### 3.3 History (Room)
```
day_log  (day_id TEXT PRIMARY KEY  -- ISO date
          wake_at INTEGER NULL      -- epoch millis
          wake_source TEXT NULL     -- DETECTED | FALLBACK | MANUAL
          final_phase TEXT          -- phase at last write
          updated_at INTEGER)

dose_log (day_id TEXT, dose_index INTEGER,
          status TEXT               -- TAKEN | SKIPPED | MISSED
          taken_at INTEGER NULL, source TEXT NULL,
          snoozes_used INTEGER, first_reminded_at INTEGER NULL,
          PRIMARY KEY (day_id, dose_index))
```
`HistoryRepository.upsert(day: DayState)` writes the `day_log` row and *replaces* all `dose_log`
rows for that day with `day.doses`, in one transaction. The History UI shows doses that have no
row yet as "pending/upcoming", but only for today.
Times are stored as epoch millis and enums by name; `updated_at` comes from the injected `Clock`.
The exported schema JSON is committed under `app/schemas`. History is never pruned (FR-7.2).

## 4. Key interfaces (contracts for parallel work)

Agents working in parallel must code against these interfaces. Change them only together with
this document.

```kotlin
// :core
object Engine {
    fun reduce(state: DayState, event: Event, settings: Settings, now: Instant, zone: ZoneId): Transition
    fun outputs(state: DayState, settings: Settings, now: Instant, zone: ZoneId): Outputs
    fun fresh(dayId: LocalDate): DayState
}
object TimeMath {
    fun dayIdOf(now: Instant, s: Settings, zone: ZoneId): LocalDate
    fun startOfDay(day: LocalDate, s: Settings, zone: ZoneId): Instant
    fun earliestAt(day: LocalDate, s: Settings, zone: ZoneId): Instant
    fun fallbackAt(day: LocalDate, s: Settings, zone: ZoneId): Instant
    fun nextRolloverAt(day: LocalDate, s: Settings, zone: ZoneId): Instant
    fun isWeekend(day: LocalDate): Boolean
}
object WakeRule {
    fun isAwake(events: List<UsageEvt>, from: Instant, to: Instant, s: Settings,
                countScreenOnAsUnlock: Boolean, excludedPackages: Set<String>): Boolean
    // Same inputs; also returns the counts, for the T9 detection log. isAwake == evaluate(...).awake
    fun evaluate(events: List<UsageEvt>, from: Instant, to: Instant, s: Settings,
                 countScreenOnAsUnlock: Boolean, excludedPackages: Set<String>): WakeCheck
}
data class WakeCheck(val unlocks: Int, val foregroundTime: Duration, val awake: Boolean)

// :app
interface WakeDetector { suspend fun isAwake(from: Instant, to: Instant, settings: Settings): Boolean }
interface AlarmScheduler { fun schedule(at: Instant, userVisible: Boolean); fun cancel() }
interface Notifier {
    fun ensureChannels()
    fun showReminder(spec: ReminderSpec)
    fun cancelReminder()
    fun buildAlarmNotification(spec: RingSpec): android.app.Notification   // used by the ring service
}
interface RingController { fun ensureRinging(spec: RingSpec); fun ensureStopped() }
interface StateStore { val state: Flow<DayState?>; suspend fun load(): DayState?; suspend fun save(s: DayState) }
interface SettingsRepository { val settings: Flow<Settings>; suspend fun current(): Settings; suspend fun update(s: Settings) }
interface HistoryRepository { fun days(): Flow<List<DayWithDoses>>; suspend fun upsert(day: DayState) }
// days() is newest first. DayWithDoses (data/history) is a day_log row plus its dose_log rows
// as core DoseRecords, ordered by index. Instants have millisecond precision.
data class DayWithDoses(val dayId: LocalDate, val wakeAt: Instant?, val wakeSource: WakeSource?,
                        val finalPhase: Phase, val updatedAt: Instant, val doses: List<DoseRecord>)

class ReminderController(/* all of the above + Clock */) {
    suspend fun handle(event: Event)          // see 02 §6
    suspend fun tick() = handle(Event.Tick(false))   // wakeDetected filled in internally
}
```

Time comes from an injected `java.time.Clock`, and the zone is `ZoneId.systemDefault()` read
on every call. Both are injected into the controller, so tests can fix them.

## 5. Scheduling (AlarmManager)

- **One pending wake-up at a time.** It uses a single `PendingIntent` (request code 0, targeting
  `WakeupReceiver`, with `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`). Scheduling replaces the
  previous wake-up.
- **User-visible deadlines use `setAlarmClock(...)`.** These are the WAITING, REMINDER, RINGING and
  SNOOZED phases. `setAlarmClock` is never deferred by Doze and is the most reliable option. Its
  side effect is that Android shows the alarm icon and "next alarm" for these times, which is
  acceptable. Its show-intent opens `MainActivity`.
- **Everything else uses `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)`.** That covers
  BEFORE_WINDOW, DETECTING polls, PENDING_SILENT and DONE. During deep Doze these may be stretched
  to about 9 minutes, which is fine because I'm asleep then.
- **Permission:** declare `USE_EXACT_ALARM`. It is auto-granted on API 33+, and the app is
  sideloaded, so Play policy doesn't apply. Still check `canScheduleExactAlarms()` in the
  setup checklist.
- **Receivers:**
  - `WakeupReceiver.onReceive` uses `goAsync()`, launches `controller.tick()` on an app-scoped
    coroutine, and calls `finish()` when done.
  - `SystemEventsReceiver` (exported, with an intent-filter for `BOOT_COMPLETED`,
    `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`) does the same. These broadcasts are
    exempt from the implicit-broadcast restrictions.
- `MainActivity.onResume` and every settings change also call `controller.tick()`.

## 6. Wake detection

**`UsageEventSource`** queries `UsageStatsManager.queryEvents(from − 1 h, to)` and maps raw
events to:
```kotlin
data class UsageEvt(val time: Instant, val type: Type, val pkg: String) {
    enum class Type { UNLOCK, SCREEN_ON, SCREEN_OFF, APP_RESUMED, APP_PAUSED }
}
```
| Raw event | Mapped type |
|-----------|-------------|
| `KEYGUARD_HIDDEN` | UNLOCK |
| `SCREEN_INTERACTIVE` | SCREEN_ON |
| `SCREEN_NON_INTERACTIVE` | SCREEN_OFF |
| `ACTIVITY_RESUMED` | APP_RESUMED |
| `ACTIVITY_PAUSED` | APP_PAUSED |
| anything else (incl. `ACTIVITY_STOPPED`) | dropped |

Do **not** map `ACTIVITY_STOPPED`. Within one app, switching activity A → B logs
`PAUSED(A), RESUMED(B), STOPPED(A)`; a late STOPPED would end the app's interval while B is
still in front. `ACTIVITY_PAUSED` always comes before `ACTIVITY_STOPPED`, so nothing is lost.

The extra hour before `from` makes sure apps that were already in the foreground are paired
correctly.

**`AndroidWakeDetector`** calls
`WakeRule.isAwake(events, from = max(earliestAt, now − detectionWindow), to = now, ...)` with:
- `countScreenOnAsUnlock = !keyguardManager.isDeviceSecure`. Without a lock screen there are no
  KEYGUARD_HIDDEN events.
- `excludedPackages` = this app's package, `com.android.systemui`, and the default launcher
  package (resolve `Intent.ACTION_MAIN` + `CATEGORY_HOME`).
- If Usage Access is not granted, return `false`. Fallback still works, and the Home banner warns.

**`WakeRule.isAwake` (pure):**
1. `unlocks` = the number of UNLOCK events (or SCREEN_ON events when `countScreenOnAsUnlock`)
   with `time ∈ [from, to]`.
2. `fgTime` = the sum, over packages not in `excludedPackages`, of each APP_RESUMED→APP_PAUSED
   interval for the same package, clipped to `[from, to]`. An interval still open at `to` counts
   up to `to`. Treat an APP_PAUSED with no preceding RESUMED as an interval starting at `from`.
   Overlapping intervals (multi-window) are merged, so time is not double-counted.
3. Return `unlocks ≥ minUnlocks || (minAppUsage > 0 && unlocks ≥ 1 && fgTime ≥ minAppUsage)`.

Cases the steps above leave open (double RESUMED, SCREEN_OFF, a repeated PAUSED, equal timestamps)
are handled as in 01-requirements A11. `WakeCheck` always reports both `unlocks` and `fgTime`,
even when rule (a) alone decides the result.

## 7. Notifications

**Channels** are created in `Application.onCreate`. Their settings are immutable once created,
so to change them, bump the channel ID.

| ID | Name | Importance | Sound | Vibration | Use |
|----|------|-----------|-------|-----------|-----|
| `reminders_v1` | Pill reminders | HIGH | default notification sound | yes | Reminder notification |
| `alarm_v1` | Pill alarm | HIGH | **none** (service plays sound) | **off** (service vibrates) | Alarm foreground notification with full-screen intent |

**One notification ID (`1001`)** is used for both reminder and alarm, so only one pill
notification is ever visible.

**Reminder notification:**
- `setOngoing(true)`, `setOnlyAlertOnce(true)` (so re-posting an identical one doesn't re-buzz),
  category `REMINDER`.
- Content intent → `MainActivity`.
- Title "Time for pill N of M", text "Tap a button below once you've decided." with the optional
  subtitle from `ReminderSpec`. `ReminderSubtitle` is typed (A9): word it from `strings.xml`, and
  format `Snoozed.until` as HH:MM in the device's current zone.
- Actions:
  - "I took my pill" → `ActionReceiver` (`ACTION_TOOK`)
  - "Not today" (dose 1) / "I'm done for today" (later doses) → `ActionReceiver` (`ACTION_SKIP`)

**Alarm notification** (`buildAlarmNotification`):
- Channel `alarm_v1`, category `ALARM`, `setFullScreenIntent(AlarmActivity PI, true)`, ongoing.
- Same actions as the reminder, plus "Snooze (X left)" (`ACTION_SNOOZE`) when `canSnooze`.

**PendingIntent extras:** every action carries `EXTRA_DAY_ID` (ISO date) and `EXTRA_DOSE`.
Request codes must be unique per action type, so the PIs don't overwrite each other. Use
`FLAG_IMMUTABLE`. `ActionReceiver` drops the intent if `dayId` doesn't match the current state,
then forwards it as `TookDose(dose, now, NOTIFICATION or ALARM)`, `SkipRest(dose)` or `Snooze(dose)`.

## 8. Alarm ringing

**`AlarmRingService`** is a foreground service, `foregroundServiceType="systemExempted"`. Android
14+ allows this type for apps holding `USE_EXACT_ALARM` that use a foreground service to continue
alarms. Declare `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`. On Android 13 the
type isn't enforced at runtime, but `targetSdk ≥ 34` requires it in the manifest.

- **`RingController.ensureRinging(spec)`** sends `startForegroundService` with the spec extras.
  If the service is already ringing for the same `dayId`/`dose`, it does nothing. If that same
  spec's `canSnooze` changed, it only updates the notification.
- **On start:**
  1. `startForeground(1001, notifier.buildAlarmNotification(spec))`.
  2. Decide whether to play sound (FR-3.3):
     - `quiet = audioManager.ringerMode == RINGER_MODE_SILENT ||
       notificationManager.currentInterruptionFilter != INTERRUPTION_FILTER_ALL`
     - If not `quiet`, play sound. **Always vibrate**, whatever `quiet` is. The screen/full-screen
       alert is always shown.
     - **Check on the device that the vibration really happens in silent mode and under DND.**
       Vibration with `USAGE_ALARM` attributes is expected to bypass silent mode. Under DND,
       OxygenOS may suppress it, or may suppress the full-screen intent, unless "Alarms" are
       allowed in the DND settings or the app is added as a DND exception. If so, document the
       required DND setting in `06-device-setup-oneplus.md` rather than requesting DND-policy
       access.
  3. Sound: `MediaPlayer` with `AudioAttributes.USAGE_ALARM` and `CONTENT_TYPE_SONIFICATION`,
     looping. The source is `settings.alarmSoundUri`, or
     `RingtoneManager.getDefaultUri(TYPE_ALARM)`, falling back to `TYPE_RINGTONE`. The alarm stream
     ignores vibrate mode, which gives the required "rings through vibrate".
  4. Vibration: `Vibrator.vibrate(VibrationEffect.createWaveform([0, 800, 600], 0), VibrationAttributes.USAGE_ALARM)`.
  5. Acquire a `PARTIAL_WAKE_LOCK` with a timeout of `alarmRingDuration + 30 s`.
  6. Launch `AlarmActivity` explicitly as well, for when the screen is already on and a heads-up
     would show instead of the full-screen intent.
- **The ring timeout is driven by the engine,** through the scheduled `ringEndAt` wake-up. The
  service does **not** time itself out, except for a safety stop at `alarmRingDuration + 1 min`.
- **`ensureStopped()`** stops the sound, vibration and wake lock, then
  `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf()`. The controller's `apply` then
  re-posts the reminder-style notification if the outputs require one.
- **Order inside `apply(outputs)`:**
  1. If `ringing == null`, call `ringController.ensureStopped()`.
  2. Show or cancel the reminder. **Skip this step while ringing**, because the service owns ID 1001.
  3. If `ringing != null`, call `ringController.ensureRinging`.
  4. Schedule `nextWakeAt`.
- **"Test alarm"** (Settings) starts the service with a test flag. It rings for 10 s, doesn't touch
  the state, and the AlarmActivity buttons just close it.

**`AlarmActivity`:**
- `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `excludeFromRecents`,
  `launchMode="singleTask"`, and `showOnLockScreen` theme attributes.
- The UI is large: "Pill N of M", the current time, and big buttons (Took it / Snooze (X left) /
  Not today or I'm done for today).
- Buttons call `controller.handle(...)` directly.
- It observes `StateStore.state` and finishes itself as soon as the phase is no longer
  `RINGING` for that dose.
- Back press does nothing, so the alarm can only be dismissed through a decision.

## 9. Permissions and manifest

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
<!-- NO INTERNET permission -->
```

`PermissionChecker` reports these checks (each with a deep-link intent):

| Check | How | Fix intent |
|-------|-----|-----------|
| Notifications | `NotificationManagerCompat.areNotificationsEnabled()`, and both channels not `IMPORTANCE_NONE` | Runtime request; else `ACTION_APP_NOTIFICATION_SETTINGS` |
| Usage access | `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg) == MODE_ALLOWED` | `Settings.ACTION_USAGE_ACCESS_SETTINGS` |
| Exact alarms | `alarmManager.canScheduleExactAlarms()` | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| Full-screen intent | API ≥ 34: `notificationManager.canUseFullScreenIntent()`; else true | `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (34+) |
| Battery optimisation | `powerManager.isIgnoringBatteryOptimizations(pkg)` | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:` URI |
| OnePlus extras | Can't be detected; manual "I've done this" checkbox stored in prefs | Open app details settings, and show the steps from `06-device-setup-oneplus.md` |

## 10. UI overview

- **Home:**
  - status card: today's doses and the phase text (see FR-6)
  - next-action line
  - **Record dose** card: "Took pill N now" and "Took pill N at…" (Material 3 `TimePicker`
    dialog, validated per FR-5.2)
  - "Remind me now", "I'm done for today" / "Not today", and "Resume today" (only in DONE with
    skipped doses)
  - permission warning banner
  - top-bar actions: History, Settings
- **History:** a LazyColumn of days. Each row shows the date, wake time and source, and one chip
  per dose (✓ 07:52 · ✓ 11:50 · ✗ skipped · ! missed), plus the snooze count.
- **Settings:** grouped into Wake detection (weekday/weekend times and detection thresholds),
  Doses (count, interval), Alarm (escalation, snooze duration, max snoozes, ring duration, sound,
  Test alarm), and Advanced (poll interval, day rollover, Reset to defaults).
  - Durations use number fields in minutes; the interval uses an hours + minutes pair.
  - Validation errors are shown inline, and Save is disabled while any field is invalid.
- **Setup:** the checklist from §9. It is shown automatically on first launch, and also while any
  hard requirement (notifications, exact alarms) is missing.

## 11. Build and CI

- Commit the Gradle wrapper.
- Debug builds are signed with a **committed `app/debug.keystore`**, referenced from the `debug`
  `signingConfig`. That way every CI-built APK installs over the previous one without losing data.
  This is acceptable for a personal sideloaded app; note it in the README.
- Debug builds keep the `applicationId` without a suffix, so there is one install.
- `.github/workflows/android.yml`:
  - runs on push and on pull_request
  - `actions/checkout`, `actions/setup-java` (temurin 17), `gradle/actions/setup-gradle`
  - `./gradlew :core:test lint testDebugUnitTest assembleDebug`
  - uploads `app/build/outputs/apk/debug/app-debug.apk` as an artifact named `PillReminder-debug-<short sha>`
- **Local agents without an Android SDK:** the `:core` logic has no Android dependencies, but
  configuring `:app` may still fail without an SDK. Prefer installing the Android command-line
  tools (`cmdline-tools`, `platforms;android-<compileSdk>`, `build-tools`), accepting the
  licences and setting `ANDROID_HOME`. If that's impossible, run `./gradlew :core:test` (works
  if Gradle can configure the project), and otherwise rely on CI. Say in the PR which checks
  ran locally.
