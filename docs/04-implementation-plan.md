# 04 — Implementation plan

The tasks are sized for one agent session each. Before starting a task, check that its
dependencies are done (`[x]`). Tasks in the same **wave** can run in parallel, because they
only meet through the interfaces in `03-technical-design.md` §4.

```
Wave 1:  T1 scaffold+CI
Wave 2:  T2 core model+time   →  T3 engine            (T2 then T3, same agent recommended)
         T4 wake rule (core)
Wave 3:  T5 data layer        T6 scheduling+receivers+controller
         T7 notifications     T8 alarm ringing+AlarmActivity
         T9 usage source+detector
Wave 4:  T10 UI (home, record dose, history, settings)    T11 setup/permissions UI
Wave 5:  T12 integration hardening + manual test pass
```

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| T1 | Project scaffold + CI | — | [x] |
| T2 | Core model, settings, TimeMath | T1 | [x] |
| T3 | Engine (reducer + outputs) | T2 | [x] |
| T4 | WakeRule | T2 | [x] |
| T5 | Data layer (settings, state, history) | T2 | [x] |
| T6 | Scheduler, receivers, ReminderController | T3, T5 | [x] |
| T7 | Notifications + ActionReceiver | T2 | [x] |
| T8 | Alarm ring service + AlarmActivity | T7 | [x] |
| T9 | UsageEventSource + AndroidWakeDetector | T4 | [x] |
| T10 | Main UI: Home, Record dose, History, Settings | T5, T6 | [x] |
| T11 | Setup checklist + PermissionChecker | T1 | [x] |
| T12 | Integration, hardening, manual test pass | all | [ ] |
| T13 | Hold the alarm while Maps navigates (FR-3.8) | T3, T6, T11 | [x] |
| T14 | Optional status notification with countdown (FR-2.4) | T3, T6, T7, T10 | [x] |

T6 integrates T7, T8 and T9 through their interfaces. If one of those isn't done yet,
T6 uses a no-op/fake implementation and the owning task wires the real one into `AppContainer`.

---

## T1 — Project scaffold + CI
**Do:**
- Gradle wrapper, `settings.gradle.kts` with `:core` (Kotlin JVM) and `:app` (Android application),
  and a version catalog. Package `nl.veerdonk.pillreminder`, `minSdk 33`, compile and target SDK
  at the latest stable version.
- A Compose `MainActivity` with an empty Material 3 scaffold showing "PillReminder".
  `PillReminderApp` Application class, an empty `AppContainer`.
- A committed `app/debug.keystore`, used by the debug signing config.
- `.github/workflows/android.yml` as in design §11.
- `.gitignore` for Android and Gradle.

**Accept when:**
- CI is green.
- The APK artifact installs on Android 13 and opens to the placeholder screen.

*Done 2026-10-12. CI run #1 green; the owner confirmed the APK opens to the placeholder screen on the OnePlus 8.*
- `./gradlew :core:test` runs (with a trivial test).

## T2 — Core model, settings, TimeMath
**Do:**
- In `:core`: the `Settings` model with defaults and `validate()` (ranges from FR-8, including the
  cross-field rules: fallback > earliest, and rollover < both earliest times).
- `DayState`, `DoseRecord`, all enums, `Event`, `Transition`, `Outputs`, `ReminderSpec`, `RingSpec`
  (02 §2, §4, §5), with kotlinx.serialization serializers for java.time types.
- `TimeMath` (design §4).

**Accept when** unit tests cover:
- `dayIdOf` around rollover (02:59 / 03:00)
- weekday vs weekend selection, including Sunday→Monday
- a DST spring-forward and fall-back day in `Europe/Amsterdam`
- validation errors
- a JSON round-trip of `DayState`

*Done 2026-10-12. `Event`/`Transition` live in `core.model`; `CoreJson` (in `core.serial`) is the
shared `Json` instance for persisting core types. See assumptions A6 and A7 in 01.*

## T3 — Engine
**Do:** implement `Engine.reduce`, `Engine.outputs` and `Engine.fresh` exactly as in
`02-state-machine.md`. Include the `dosesPerDay`-lowered rule.

**Accept when:**
- Every row of tables 4.1, 4.2, 4.3 and §5 has at least one test.
- Every "Valid when" condition has a negative test (an ignored event returns an identical state).
- A scenario test reproduces the worked example in 02 §7.
- A scenario test covers dose 1 with 3 timeouts → PENDING_SILENT → TookDose → WAITING.
- A rollover-while-RINGING test records the doses as MISSED and gives a fresh day.
- A backdated TookDose whose `at + interval < now` goes straight to REMINDER.
- The engine has no Android imports and all logic uses the passed-in `now`/`zone`.

*Done 2026-10-12. `Engine` lives in `core.engine`; tests are in `core/src/test/.../engine/`
(one file per table, plus scenarios). Confirmed assumptions A8–A10 in 01; A9 made
`ReminderSpec.subtitle` a typed `ReminderSubtitle` (02 §5).*

## T4 — WakeRule
**Do:** implement `WakeRule.isAwake` (design §6) in `:core`.

**Accept when** tests cover:
- 2 unlocks in the window → true; 1 unlock → false.
- 1 unlock plus 3 min of app use → true. 1 unlock plus 2 min → false.
  The same 3 min of use but in the launcher, System UI or own package → false.
- Events before `from` are ignored, but an app resumed before `from` and still open counts from `from`.
- An open interval at `to` is counted up to `to`.
- Overlapping intervals are not double-counted.
- `countScreenOnAsUnlock` behaviour.
- `minAppUsage = 0` disables rule (b).

## T5 — Data layer
**Do:**
- `SettingsRepository` (Preferences DataStore).
- `StateStore` (DataStore + JSON, tolerant of corrupt data).
- Room `HistoryDatabase` / DAO / `HistoryRepository.upsert(DayState)` and `days()` Flow
  (design §3.3), with exported Room schema JSON committed under `app/schemas`.
- Wire everything into `AppContainer`.

**Accept when:**
- Unit/Robolectric (or instrumented) tests show the settings round-trip, a state round-trip,
  that a corrupt state file yields `null`, and that history upsert replaces dose rows for the day.

*Done 2026-10-12. Implementations are `DataStoreSettingsRepository`, `DataStoreStateStore` and
`RoomHistoryRepository`, all wired in `AppContainer`. `DayWithDoses` is defined in design §4.
Settings that fail `validate()` on read fall back to defaults (A12 in 01). Tests are in
`app/src/test/.../data/` (DataStore on the plain JVM, Room under Robolectric).*

## T6 — Scheduling, receivers, ReminderController
**Do:**
- `AlarmScheduler` (design §5).
- `WakeupReceiver` and `SystemEventsReceiver` with `goAsync`, and manifest entries.
- `ReminderController` with a Mutex, implementing 02 §6 including the second-tick detection
  rule, and `apply(outputs)` in the order from design §8.
- `MainActivity.onResume` → `tick()`. Settings changes → `tick()`.

**Accept when:**
- Controller unit tests with fakes (Clock, StateStore, WakeDetector, Notifier, RingController,
  AlarmScheduler, History) verify:
  - detection is only invoked in DETECTING
  - BEFORE_WINDOW→DETECTING within a single tick runs detection
  - finishedDay is written on rollover
  - apply order
  - stale `dayId` actions are dropped
- On a device, after a reboot, the next alarm is re-armed (check with `adb shell dumpsys alarm`).

*Done 2026-10-12. `ReminderController` (controller/), `AndroidAlarmScheduler` and the receivers
(platform/) are wired in `AppContainer`, with an app-scoped `appScope`. The `WakeDetector`,
`Notifier` and `RingController` interfaces live in platform/ with no-op implementations
(`NoOpWakeDetector` (removed by T9), `NoOpNotifier`, `NoOpRingController`) that T9, T7 and T8 replace
(all three have been).
`handle(event, expectedDayId)` drops stale notification actions (design §4). Settings changes
tick via `AppContainer.tickOnSettingsChange()`. Assumptions A13–A16 in 01. The reboot check on
the device is still open for the owner.*

## T7 — Notifications + ActionReceiver
**Do:**
- `Notifier` (design §7): channels, reminder notification and alarm notification builder.
- `ActionReceiver` mapping actions to events, with dayId validation.

**Accept when:**
- On a device, the reminder for dose 1 shows "I took my pill / Not today", and for dose 2+ shows
  "I took my pill / I'm done for today".
- It can't be swiped away.
- The buttons update the state, and the notification disappears or updates accordingly.

*Done 2026-10-12. `AndroidNotifier` (channels, reminder, `buildAlarmNotification`) replaces
`NoOpNotifier`; `ActionReceiver` maps buttons to events through the pure `parseAction` and
passes the dayId as `expectedDayId`. Adds POST_NOTIFICATIONS and USE_FULL_SCREEN_INTENT. The
alarm's full-screen target is MainActivity until T8 changes `AndroidNotifier.ALARM_SCREEN` to
AlarmActivity. Assumptions A17–A21 in 01. The on-device checks above are still open for the owner.*

## T8 — Alarm ring service + AlarmActivity
**Do:**
- `AlarmRingService`, `RingController` and `AlarmActivity` (design §8), including the
  ringer-mode rules and the Settings "Test alarm" hook (exposed as a function T10 can call).

**Accept when** on the device:
- With the screen off and locked, the alarm turns the screen on and shows the full-screen
  activity with sound and vibration.
- With the alert slider on vibrate, sound still plays. On silent, and with DND on, there is no
  sound but the phone vibrates and the screen is shown.
- Snooze, Took it and Done all stop the ringing and close the activity.
- The ring times out after `alarmRingDuration`, through the engine.
- The Snooze button is hidden when no snoozes are left.

*Done 2026-10-12. `AndroidRingController` (platform/) replaces `NoOpRingController`; it starts
`AlarmRingService`, which does sound, vibration, the wake lock and the notification as in design
§8. `AlarmActivity` (ui/alarm/) is the full-screen alarm, and `AndroidNotifier.ALARM_SCREEN` now
points to it. The pure decisions are in `platform/RingLogic.kt` (`shouldPlaySound`, `ringChange`,
`RingExtras`) and are unit tested. **Test alarm hook for T10:** `AppContainer.testAlarm`
(`TestAlarm.startTestAlarm()` / `stopTestAlarm()`, implemented by `AndroidRingController`). It
rings 10 s and doesn't touch the state. Adds the FOREGROUND_SERVICE, FOREGROUND_SERVICE_SYSTEM_EXEMPTED,
WAKE_LOCK and VIBRATE permissions. Assumptions A22–A26 in 01 (A26: the alarm notification has its own ID 1002). The on-device checks above, and the
vibration-under-DND check from design §8, are still open for the owner.*

## T9 — UsageEventSource + AndroidWakeDetector
**Do:** implement design §6 (Android side). Returns false when Usage Access is missing.
Map raw events exactly as in the §6 table; `ACTIVITY_STOPPED` is dropped. Call
`WakeRule.evaluate` (not `isAwake`) so the detection log can show the counts from `WakeCheck`.

**Accept when:**
- A debug-only "Detection log" (a text section at the bottom of Settings → Advanced, or a log tag
  `WakeDetect`) shows, for the last check: unlock count, foreground minutes and the result.
- On the device, unlocking twice within the window triggers dose 1 within one poll interval.
- A unit test of the raw-event mapping (a pure function from event type to `UsageEvt.Type?`)
  shows `ACTIVITY_STOPPED` and unknown types are dropped.

*Done 2026-10-12. `UsageEventSource` (`mapEventType` is the pure mapping) and
`AndroidWakeDetector` replace `NoOpWakeDetector`, which is removed. Adds PACKAGE_USAGE_STATS and a
`<queries>` entry for the HOME intent (launcher lookup on API 30+). Each check is logged under tag
`WakeDetect` and kept as a `DetectionLogEntry` (from, to, `WakeCheck?`; null check = Usage Access
missing). **For T10:** `AppContainer.detectionLog: StateFlow<DetectionLogEntry?>` holds the last
check (null before the first); render `entry.describe()` as monospace text at the bottom of
Settings → Advanced, in debug builds only. Assumptions A30–A31 in 01. The on-device check
(two unlocks within the window triggers dose 1 within one poll) is still open for the owner.*

## T10 — Main UI
**Do:** Home (FR-6 and the Record-dose section FR-5), History (FR-7) and Settings (FR-8),
following design §10. Use ViewModels exposing `StateFlow` from the repositories, and send user
actions through `ReminderController`.

**Accept when:**
- Every Home action produces the state change from the transition table.
- Backdating rejects future times and times before the previous dose.
- Settings validation blocks invalid saves.
- Changing `escalationDelay` while a reminder is showing moves the alarm time (visible in the
  "next action" line).
- History shows today live and past days after rollover.
- The UI works in both light and dark theme.

*Done 2026-10-12. `ui/home`, `ui/history` and `ui/settings` each have a ViewModel (built by
`AppViewModelFactory` from `AppContainer`, using `lifecycle-viewmodel-compose`, already pulled in by
navigation-compose) and a screen; the pure parts are `HomeModel.kt` (dose rows, next-action line,
visible actions per phase, backdate mapping and validation), `HistoryModel.kt` and `SettingsForm.kt`,
all unit tested. User actions go through `reminderController.handle` on `appScope`. Routes: home,
history, settings, setup, oneplus_guide. "Test alarm" calls `container.testAlarm.startTestAlarm()`.
Assumptions A50-A56 in 01. On-device checks (both themes, ringtone picker, Test alarm, the
escalationDelay change moving the "Alarm in N min" line) remain for the owner. Note: since
FR-3.1a a detected dose 1 reminder before the fallback time escalates at the fallback time, so
check the escalationDelay change on dose 2, or with "First alarm waits for fallback" off.*

## T11 — Setup checklist + PermissionChecker
**Do:**
- `PermissionChecker` and the Setup screen (design §9).
- The OnePlus guide screen, with content from `06-device-setup-oneplus.md`.
- The Home warning banner.

**Accept when:**
- Each row shows live status (re-checked on resume) and its button opens the right system screen.
- The setup screen shows on first launch and when a hard requirement is missing.

*Done 2026-10-12. `PermissionChecker`/`AndroidPermissionChecker` (platform/PermissionChecker.kt)
do the checks and fix intents; the pure rules (missing items, hard vs soft, setup-needed, banner)
are in `SetupStatus` (platform/SetupStatus.kt). `SetupScreen`, `OnePlusGuideScreen` and the reusable
`PermissionBanner(status, onOpenSetup)` plus `rememberSetupStatus(checker)` live in `ui/setup/`.
T10 adds the banner at the top of Home and keeps the "setup"/"oneplus_guide" routes that
MainActivity now defines with a minimal NavHost (`Routes`). Adds navigation-compose and the
PACKAGE_USAGE_STATS and REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permissions. Assumptions A40-A43 in 01.
The on-device checks above are still open for the owner.*

## T12 — Integration, hardening, manual test pass
**Do:**
- Run the full manual test script in `05-test-plan.md` on the OnePlus 8, using short timings
  (1-minute settings).
- Fix any bugs found.
- Write results into `docs/test-results.md`, noting which device and build were used.
- Update the README with install instructions: download the APK from the latest CI run, allow
  install from unknown sources, run Setup.

**Accept when:** all manual test cases pass, or have documented follow-up issues the owner has accepted.

*In progress 2026-10-12. The owner reports every manual case passing except M1 and M24 (wake
detection), which need a real morning; results are in `docs/test-results.md`. README has the
install and Setup steps. Mark `[x]` once M1/M24 pass.*

## T13 — Hold the alarm while Maps navigates
**Do:**
- Engine: `Tick.navigating` and the HELD_DRIVING phase (02 §4.2, §5), with a test per new row.
- `NavigationDetector` + `MapsNavigationListener` (design §6a), wired into `ReminderController`.
- Optional "notification access" row in the setup checklist; a live status in Settings → Advanced.

**Accept when:**
- Unit tests cover the new transitions and the Maps notification rule.
- On the phone: with Maps navigating, a due alarm doesn't ring and the reminder says "Alarm held
  while you drive"; when navigation ends it rings within seconds.

*Done 2026-10-12 (owner request). Assumption A58 in 01. The on-device check above is open for
the owner: Settings → Advanced → "Maps navigation" should say "navigating: yes" while Maps
navigates. If it doesn't, the Maps lines there show what to match on instead.*

## T14 — Optional status notification with countdown
**Do:**
- `Settings.showStatusNotification` (default off), stored and editable in Settings → Doses.
- `Outputs.status` (02 §5) with a test per phase; `Notifier.showStatus`/`cancelStatus` on a
  low-importance channel, ID 1003, with a system-drawn countdown (design §7).

**Accept when:** unit tests cover the outputs, the controller order and the notification; M26
passes on the phone.

*Done 2026-10-12 (owner request). Assumption A59 in 01. The owner reports M26 passing.*
