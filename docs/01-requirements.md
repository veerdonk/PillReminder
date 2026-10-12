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

Open:
- **A6** (T2) `dayIdOf` is defined as "the day `d` with `startOfDay(d) ≤ now < startOfDay(d+1)`".
  This equals the formula in 02 §2 except when `dayRolloverTime` falls inside a DST gap or overlap
  (e.g. a 02:30 rollover in `Europe/Amsterdam`). There, a gap moves the rollover forward to
  03:30, and in an overlap the first 02:30 starts the new day, so the day never jumps back.
  The default 03:00 rollover is not affected.
- **A7** (T2) Duration settings must be whole minutes (they are stored in minutes, design §3.1);
  `validate()` reports anything else as an error.
