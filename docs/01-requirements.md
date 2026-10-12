# 01 — Requirements

## 1. Purpose

I take **3 pills a day**:
- the first some time after getting up,
- the second about 4 hours after the first,
- the third about 4 hours after the second.

I often forget both the first pill and the follow-ups. The app must:
1. notice when I'm awake and remind me to take the first pill,
2. remind me again after each pill I take, until the day's doses are done,
3. escalate to a snoozable alarm when I ignore a reminder.

This is a single-user app, sideloaded onto my phone. There is no account, no network
access and no Play Store release.

**Target device:** a OnePlus 8 on Android 13 (OxygenOS 13). I'm happy to change phone
settings to make the app reliable (see `06-device-setup-oneplus.md`).

## 2. Glossary

| Term | Meaning |
|------|---------|
| **Dose N** | The N-th pill of the day (1-based). `N` runs from 1 to `dosesPerDay`. |
| **Day** | The app's day runs from `dayRolloverTime` (default 03:00) to the next day's `dayRolloverTime`. It is identified by the calendar date on which it starts. |
| **Weekend day** | A day whose date falls on a Saturday or Sunday. |
| **Wake window** | From `earliestTime` to `fallbackTime` for the current day type (weekday or weekend). Wake detection only runs inside this window. |
| **Reminder** | A high-priority notification asking me to take dose N. |
| **Alarm** | A full-screen, ringing, vibrating alert that is raised when a reminder is ignored. |
| **Escalation** | When a reminder is ignored for `escalationDelay`, it turns into the alarm. |

## 3. Functional requirements

### FR-1 Wake detection (dose 1 trigger)
- **FR-1.1** Each day, wake detection starts at `earliestTime`. That is 07:00 on weekdays by
  default (`weekdayEarliestTime`) and 08:30 on weekends (`weekendEarliestTime`).
- **FR-1.2** While detection is running, the app checks Android's usage log (Usage Access)
  about every `detectionPollInterval` (default 5 min).
- **FR-1.3** I count as **awake** when, looking only at events since `earliestTime` and within
  the last `detectionWindow` (default 20 min), **either** of these holds:
  - (a) there have been at least `minUnlocks` (default **2**) unlocks, **or**
  - (b) there has been at least 1 unlock and at least `minAppUsage` (default **3 min**) of
    foreground use of apps other than this app, the launcher and System UI.
- **FR-1.4** Once I'm detected as awake, the app shows the dose 1 reminder immediately.
- **FR-1.5** **Fallback:** if I haven't been detected as awake by `fallbackTime` (default 10:30
  on weekdays, 11:30 on weekends), the app shows the dose 1 reminder anyway.
- **FR-1.6** Phone activity before `earliestTime` is ignored. For example, if I check the time
  at 06:40 and go back to sleep, that doesn't count.
- **FR-1.7** If I record dose 1 manually in the app (FR-5), detection stops for the day.

### FR-2 Reminders
- **FR-2.1** A reminder notification reads "Time for pill N of M" and has two action buttons:
  - Dose 1: **"I took my pill"** and **"Not today"**.
  - Dose 2 and later: **"I took my pill"** and **"I'm done for today"**.
- **FR-2.2** The reminder is *ongoing*: swiping it doesn't dismiss it. Tapping its body
  opens the app on the Home screen.
- **FR-2.3** A reminder uses the normal notification sound and vibration. These follow the
  phone's ringer mode as usual.

### FR-3 Escalation to alarm
- **FR-3.1** If neither reminder button is tapped within `escalationDelay` (default **15 min**)
  of the reminder appearing, the app raises the **alarm** for that dose.
- **FR-3.2** The alarm is a full-screen activity, shown over the lock screen with the screen
  turned on, plus a matching notification. Both offer:
  - **"I took my pill"**
  - **"Snooze (X left)"**, hidden once no snoozes are left
  - **"Not today"** for dose 1, or **"I'm done for today"** for later doses.
- **FR-3.3** Sound behaviour depends on the phone's ringer mode (set with the alert slider on
  the OnePlus) and on Do Not Disturb:
  | Phone state | Alarm sound | Vibration | Screen |
  |-------------|-------------|-----------|--------|
  | Ring (normal), DND off | Yes (alarm volume) | Yes | Full-screen alert |
  | Vibrate, DND off | **Yes** (rings through vibrate) | Yes | Full-screen alert |
  | Silent | **No** | **Yes** | Full-screen alert (screen turns on) |
  | Do Not Disturb on (any ringer mode) | **No** (treated as silent) | **Yes** | Full-screen alert (screen turns on) |
  The phone state is checked at the moment the alarm starts ringing. "DND on" means the
  interruption filter is anything other than "all".
- **FR-3.4** The alarm rings for `alarmRingDuration` (default **2 min**). If nothing is tapped
  in that time, it stops and behaves as if **Snooze** had been tapped. This counts as a snooze.
- **FR-3.5** **Snooze** stops the alarm. The alarm rings again after `snoozeDuration`
  (default **10 min**).
- **FR-3.6** Each dose allows at most `maxSnoozes` snoozes (default **3**). Timed-out rings count
  towards this limit. When the last allowed ring times out and no snoozes are left, the alarm
  stops ringing and the **ongoing reminder notification stays** with its two buttons. No more
  ringing happens for that dose. The dose stays pending until I act or the day ends.
- **FR-3.7** Snooze counts are per dose and reset for each new dose.

### FR-4 Dose sequence
- **FR-4.1** **"I took my pill"** on dose N records dose N as taken at the moment of the tap.
  It clears the reminder and alarm, and:
  - if N < `dosesPerDay`: it schedules the dose N+1 reminder for **taken time + `doseInterval`**
    (default **3 h 50 min**).
  - if N = `dosesPerDay`: the day is complete and the app is quiet until the next day.
- **FR-4.2** **"Not today"** on dose 1, or **"I'm done for today"** on later doses, records the
  remaining doses as *skipped*. The app is quiet until the next day's wake detection.
- **FR-4.3** Every dose (2…M) behaves exactly like dose 1 once its reminder fires: reminder,
  then escalation, then alarm, then snooze.
- **FR-4.4** If a dose is still pending when the day rolls over (`dayRolloverTime`), it and any
  later doses are recorded as *missed*. The reminder notification is removed and the new day starts.

### FR-5 Recording doses in the app ("took it earlier")
- **FR-5.1** The Home screen has a **Record dose** section. It records the current/next dose
  (dose N) as taken, either **now** or at a time I pick.
- **FR-5.2** The picked time must not be in the future. For N > 1 it must also not be earlier
  than dose N-1's recorded time. For N = 1 it must not be earlier than the start of the day.
- **FR-5.3** Recording dose N works in any state where dose N is next. That includes:
  - dose 1 before detection has fired,
  - the waiting period before dose N's reminder fires,
  - while dose N's reminder or alarm is active.
  Recording dose N clears any active reminder or alarm. The next reminder is scheduled at
  `recorded time + doseInterval`. If that time is already past, the reminder is shown immediately.
- **FR-5.4** The Home screen also offers **"I'm done for today"** / **"Not today"** (same effect
  as FR-4.2) and **"Remind me now"**. "Remind me now" shows the next dose's reminder immediately,
  skipping detection or the wait.
- **FR-5.5** After the day is complete or skipped, Home shows a **"Resume today"** action. It
  re-opens the day at the next untaken dose, as if "Remind me now" had been tapped. It is meant
  for undoing an accidental "Not today".

### FR-6 Home screen
- **FR-6.1** Shows today's status for each dose: *taken at HH:MM* / *pending* (reminder or alarm
  active) / *upcoming at ~HH:MM* / *skipped* / *missed*. Before dose 1 it also shows the detection
  state, e.g. "Waiting for you to wake up (from 07:00, fallback 10:30)".
- **FR-6.2** Shows the time of the next scheduled action. For example: "Next reminder at 14:35",
  "Alarm in 6 min", or "Snoozed until 09:20".
- **FR-6.3** Shows a warning banner when a required permission or setting is missing (see FR-9).

### FR-7 History
- **FR-7.1** A History screen lists past days, newest first. For each day it shows:
  - when wake was detected and how (*detected* / *fallback* / *manual*),
  - each dose's status and time,
  - the number of snoozes.
- **FR-7.2** Keep history for at least 365 days. Deleting older entries is optional.

### FR-8 Settings
All of these settings are editable in the app and take effect immediately. If a change affects
a timer that is already scheduled, it applies from the next scheduling decision. Already-scheduled
alarms are recomputed where the spec defines them in terms of the setting (see state machine §5).

| Key | Default | Range / validation | Notes |
|-----|---------|--------------------|-------|
| `weekdayEarliestTime` | 07:00 | time | Start of detection Mon–Fri |
| `weekdayFallbackTime` | 10:30 | > earliest | |
| `weekendEarliestTime` | 08:30 | time | Start of detection Sat–Sun |
| `weekendFallbackTime` | 11:30 | > earliest | |
| `minUnlocks` | 2 | 1–10 | FR-1.3a |
| `minAppUsage` | 3 min | 0–30 min | FR-1.3b. 0 disables rule (b). |
| `detectionWindow` | 20 min | 5–120 min | FR-1.3 |
| `detectionPollInterval` | 5 min | 1–15 min | Advanced setting |
| `dosesPerDay` | 3 | 1–6 | |
| `doseInterval` | 3 h 50 min | 1 min – 12 h | Stored in minutes |
| `escalationDelay` | 15 min | 1–120 min | |
| `snoozeDuration` | 10 min | 1–60 min | |
| `maxSnoozes` | 3 | 0–10 | 0 = no snooze button |
| `alarmRingDuration` | 2 min | 1–10 min | |
| `dayRolloverTime` | 03:00 | 00:00–06:00 and < both earliest times | Advanced setting |
| `alarmSound` | System default alarm | Ringtone picker | Nice-to-have |

- The minimum values of 1 minute are deliberate. They let me test the full flow quickly on the
  phone.
- Settings has a **"Test alarm"** button. It rings the alarm UI for 10 s without changing state.
- Settings has a **"Reset to defaults"** button.

### FR-9 Permissions and reliability
- **FR-9.1** On first launch, a setup checklist walks me through each item, with a button that
  opens the right system screen and a live ✓/✗ status for each:
  - notification permission,
  - Usage Access,
  - exact alarms,
  - full-screen intent,
  - battery optimisation exemption,
  - a link to the OnePlus-specific steps.
- **FR-9.2** The schedule must survive a phone reboot, an app update, a time change and a
  timezone change. A pending reminder must be re-shown, and the pending alarm or timer re-armed
  (or fired at once if overdue).
- **FR-9.3** The app must not run a permanent foreground service. It uses scheduled alarms only,
  plus a short-lived foreground service while the alarm is ringing.

## 4. Non-functional requirements

- **NFR-1** Kotlin, Jetpack Compose, Material 3. minSdk 33, targetSdk is the latest stable.
- **NFR-2** There is no network permission, no analytics and no crash reporting service. All
  data stays on the device.
- **NFR-3** Timing accuracy: reminders, alarms and snoozes fire within ±1 minute of their
  scheduled time. Wake detection reacts within `detectionPollInterval` + 1 min.
- **NFR-4** Battery: no wake locks except while ringing. Detection polling only runs inside the
  wake window.
- **NFR-5** UI language is English. Strings go in `strings.xml`, not hard-coded.
- **NFR-6** Supports light and dark theme. Dynamic color is optional.

## 5. Decisions log

| # | Question | Decision |
|---|----------|----------|
| D1 | Detection method | Usage Access log (unlocks plus app foreground time). "First unlock after 07:00" alone is not enough. |
| D2 | Fallback | Yes. Separate weekday and weekend times, configurable. |
| D3 | Weekend | Sat and Sun have their own earliest and fallback times. |
| D4 | Alarm style | Full-screen alarm with looping sound and vibration, like a clock app |
| D5 | Ringer modes | Rings through vibrate mode. Silent and Do Not Disturb give no sound, but still vibrate and turn the screen on (see FR-3.3). |
| D6 | Snooze limit | 3 by default, configurable. Default snooze is 10 min. |
| D7 | Ring timeout | 2 min, after which it auto-snoozes |
| D8 | Interval reference | Counted from the moment "I took my pill" is tapped (or the recorded time) |
| D9 | Backdating | Yes, through a Record-dose section in the app |
| D10 | Doses per day | Configurable, default 3 |
| D11 | Day boundary | 03:00 |
| D12 | History, home status | Yes |
| D13 | Language | English |
| D15 | Silent / DND | Light and vibration, no sound (A1, A2) |
| D14 | Stack | Kotlin, Compose, AlarmManager, DataStore, Room, GitHub Actions APK build |

## 6. Open points / assumptions

Implementing agents add to this list and the owner confirms the items.

Confirmed by the owner:
- **A1 ✓** In **silent** mode the alarm gives light (the screen turns on with the full-screen
  alert) and vibration, but no sound.
- **A2 ✓** Do Not Disturb is treated exactly like silent mode: light and vibration, no sound.
- **A3 ✓** Weekend means Saturday and Sunday, fixed. It is not configurable.
- **A4 ✓** A timed-out ring counts as a snooze (FR-3.4/3.6).
- **A5 ✓** The *reminder notification* has no snooze button. Snooze is only on the alarm.

  This matches the owner's description: the reminder has 2 buttons.
- **A8 ✓** (T3) `ResumeDay` follows FR-5.5 ("as if Remind me now had been tapped"): when it
  re-opens dose 1 and `wakeAt` is null, it sets `wakeAt = now`, `wakeSource = MANUAL`. It also
  sets `firstRemindedAt = now`, because REMINDER is entered for that dose with fresh per-dose fields.
- **A9 ✓** (T3) To keep UI text in `strings.xml` (NFR-5), `ReminderSpec.subtitle` is a typed
  `ReminderSubtitle` (`Snoozed(until: Instant)` / `AlarmStopped`) instead of English text.
  `:app` words it and formats `until` as HH:MM in the device zone (02 §5, design §7).
- **A10 ✓** (T3) Cases the spec left open:
  - A missing base timestamp (a corrupt state, e.g. REMINDER with `reminderShownAt == null`) is
    repaired on the next Tick by setting it to `now`, so that phase's timer restarts. WAITING
    with no previous dose time shows the reminder at once. Until then, `outputs` schedules a
    wake-up 1 s from now so the repair Tick happens.
  - `Tick(wakeDetected = true)` only counts if the day was already DETECTING when the event
    arrived. It is ignored in the same Tick that leaves BEFORE_WINDOW (02 §6, FR-1.6).
  - Every DONE state has its per-dose fields cleared: on the last TookDose, on SkipRest and when
    `dosesPerDay` is lowered. The rollover's finished day is also set to DONE with those fields
    cleared, so history only sees one shape of finished day.

Open:
- **A6** (T2) `dayIdOf` is defined as "the day `d` with `startOfDay(d) ≤ now < startOfDay(d+1)`".
  This equals the formula in 02 §2 except when `dayRolloverTime` falls inside a DST gap or overlap
  (e.g. a 02:30 rollover in `Europe/Amsterdam`). There, a gap moves the rollover forward to
  03:30, and in an overlap the first 02:30 starts the new day, so the day never jumps back.
  The default 03:00 rollover is not affected.
- **A7** (T2) Duration settings must be whole minutes (they are stored in minutes, design §3.1);
  `validate()` reports anything else as an error.
- **A11** (T4) Usage-log cases design §6 leaves open, resolved toward FR-1.3 ("foreground use"
  means the phone is actually being used) and FR-1.6 (no false wake-ups):
  - **APP_RESUMED twice for the same package with no pause between** (e.g. one activity resumes
    another) keeps the first start: the app is treated as in the foreground the whole time.
  - **SCREEN_OFF** ends every open foreground interval at that moment, since nobody uses an app
    with the screen off. It does not affect unlock counting.
  - "APP_PAUSED with no preceding RESUMED" means a package whose *first* event in the list is a
    pause (it was already open before the list starts). A pause after an interval has already
    closed (e.g. ACTIVITY_PAUSED then ACTIVITY_STOPPED, both mapped to APP_PAUSED) adds nothing.
  - Events with the same timestamp are processed in input order (the usage log's order).
- **A12** (T5) Stored settings that don't pass `validate()` (e.g. after a downgrade to a build
  with narrower ranges, or an out-of-range minute-of-day) are not repaired field by field: the
  whole `Settings` object falls back to the `Settings()` defaults. The stored values are left
  as they are until the next save, which overwrites them. An unreadable settings file is also
  read as defaults. `SettingsRepository.update` throws `IllegalArgumentException` for invalid
  settings and writes nothing.
- **A13 ✓** (T6) Detection and rollover in one Tick. 02 §6 runs detection when the *loaded*
  state is DETECTING. If that state is about to roll over (the phone was off past
  `dayRolloverTime`), its window belongs to the old day, and the engine ignores `wakeDetected`
  on the fresh day anyway. So the controller only treats the state as "in DETECTING" when its
  `dayId == dayIdOf(now)`. The second-tick rule then runs whenever the Tick ends in DETECTING
  and detection has not run yet (the wording of 02 §4). Without a rollover this is exactly the
  02 §6 pseudo-code; with one, detection runs once, for the new day, instead of once for the old
  day and not at all for the new one. 02 §6 is updated to match.
- **A14** (T6) The `[from, to]` passed to `WakeDetector.isAwake` is computed by the controller:
  `from = max(earliestAt(dayId), now − detectionWindow)`, `to = now` (FR-1.3, design §6). The
  detector (T9) uses it as given and doesn't recompute it.
- **A15** (T6) Stale notification actions (design §7). `ReminderController.handle(event,
  expectedDayId)` compares `expectedDayId` with the day after the rollover check, which is
  always `dayIdOf(now)`. On a mismatch it does nothing at all: no rollover is saved, no history
  is written and the platform isn't reconciled. A stale notification can only still be showing
  if the rollover wake-up was missed, and the next Tick (rollover alarm, boot, app open) cleans it up.
- **A16** (T6) Failures inside `handle` (e.g. a storage error) are logged by the app scope's
  exception handler and the event is lost. Because the wake-up is scheduled at the end of
  `apply`, a failure before that leaves the previous wake-up in place, or none. The next app
  open, boot or settings change re-runs the reconcile. To review in T12 whether a retry is needed.
- **A17** (T7) Where the reminder's subtitle goes (02 §5 only names it). The title and text
  stay as in FR-2.1/design §7, and the subtitle ("Snoozed until 09:20", "Alarm stopped — please
  take your pill") is the notification's sub-text, shown in its header. The expanded view shows
  the subtitle above the text, so the long "Alarm stopped" line isn't cut off.
- **A18** (T7) "Snoozed until HH:MM" is always 24-hour (`HH:mm`), whatever the phone's 12/24-hour
  setting, as written in 02 §5 and design §7.
- **A19** (T7) The alarm notification uses the reminder's title and text ("Time for pill N of
  M", "Tap a button below once you've decided."). Tapping its body opens the same screen as its
  full-screen intent (AlarmActivity from T8; MainActivity until then).
- **A20** (T7) The buttons also carry `EXTRA_SOURCE` (`NOTIFICATION` on the reminder, `ALARM` on
  the alarm notification), so "I took my pill" records the right `DoseSource`. `ActionReceiver`
  ignores an intent with an unknown action, a missing or unparsable dayId, a missing dose or one
  below 1, or (for "I took my pill") a missing or other source. Request codes are unique per
  action *and* source. Design §7 is updated to match.
- **A21** (T7) Without the notification permission, `showReminder` posts nothing and doesn't
  fail, and the state still advances. The setup checklist (T11) and
  the Home banner (FR-6.3) are where a missing permission is surfaced.
- **A30** (T9) The detection log is not persisted: `AndroidWakeDetector` keeps only the last
  check in memory (`StateFlow<DetectionLogEntry?>`, plus logcat tag `WakeDetect`). After a process
  restart it is empty until the next check. A check without Usage Access is logged as "Usage access
  missing" and counts as not awake.
- **A31** (T9) Usage Access is checked with `AppOpsManager.unsafeCheckOpNoThrow` on every call, and
  the default launcher is resolved on every call, so a change in either takes effect on the next
  poll. If no launcher resolves, only this app and `com.android.systemui` are excluded.
- **A40** (T11) The Home banner counts every checklist item, including the soft ones and the
  manual OnePlus box, so it stays until the owner has ticked or fixed everything. It is red when a
  hard requirement (notifications, exact alarms) is missing, and calmer otherwise.
- **A41** (T11) "Setup seen" is set when the user taps Continue on the Setup screen, not when
  the screen is first shown. Setup opens at app start (not mid-session) if it was never seen or a
  hard requirement is missing; the Home banner is the way back in at other times. Continue is
  always enabled, so a hard requirement can be skipped (the banner then stays red).
- **A42** (T11) The "I've done this" box and "setup seen" are kept in a small SharedPreferences
  file (`setup_prefs`), not in `Settings`, since they are UI state and not part of the core model.
- **A43** (T11) The manifest declares PACKAGE_USAGE_STATS (design §9 lists it; shared with T9) so the app
  shows up under Usage access. T9 reads the log. The battery-optimisation dialog is lint's
  `BatteryLife`, suppressed because the app is sideloaded.
