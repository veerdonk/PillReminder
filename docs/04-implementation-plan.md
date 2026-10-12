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
| T8 | Alarm ring service + AlarmActivity | T7 | [ ] |
| T9 | UsageEventSource + AndroidWakeDetector | T4 | [ ] |
| T10 | Main UI: Home, Record dose, History, Settings | T5, T6 | [ ] |
| T11 | Setup checklist + PermissionChecker | T1 | [ ] |
| T12 | Integration, hardening, manual test pass | all | [ ] |

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
(`NoOpWakeDetector`, `NoOpNotifier`, `NoOpRingController`) that T9, T7 and T8 replace.
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

## T11 — Setup checklist + PermissionChecker
**Do:**
- `PermissionChecker` and the Setup screen (design §9).
- The OnePlus guide screen, with content from `06-device-setup-oneplus.md`.
- The Home warning banner.

**Accept when:**
- Each row shows live status (re-checked on resume) and its button opens the right system screen.
- The setup screen shows on first launch and when a hard requirement is missing.

## T12 — Integration, hardening, manual test pass
**Do:**
- Run the full manual test script in `05-test-plan.md` on the OnePlus 8, using short timings
  (1-minute settings).
- Fix any bugs found.
- Write results into `docs/test-results.md`, noting which device and build were used.
- Update the README with install instructions: download the APK from the latest CI run, allow
  install from unknown sources, run Setup.

**Accept when:** all manual test cases pass, or have documented follow-up issues the owner has accepted.
