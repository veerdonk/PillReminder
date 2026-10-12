# 05 — Test plan

## 1. Automated tests

| Layer | Where | What |
|-------|-------|------|
| Core | `:core` unit tests | TimeMath, Settings validation, serialization (T2). Every transition and output row (T3). WakeRule (T4). |
| Controller | `:app` JVM unit tests with fakes | Orchestration rules from 02 §6 (T6) |
| Data | Robolectric or instrumented | DataStore and Room round-trips (T5) |

Engine scenario tests should use a fixed `ZoneId.of("Europe/Amsterdam")` and helper builders
such as `at("2026-10-13T07:25")`, so tests read like the worked example.

## 2. Manual test script (on the OnePlus 8)

**Preparation:**
- Install the latest CI APK and complete Setup (all ✓). Do the OnePlus steps from `06-…`.
- In Settings, apply these **test timings**:
  - dose interval 3 min
  - escalation 1 min
  - snooze 1 min
  - ring duration 1 min
  - max snoozes 2
  - poll interval 1 min
  - detection window 5 min
- Set the earliest time 3 minutes ahead of now and the fallback 10 minutes ahead (for today's
  day type).
- Turn **"First alarm waits for fallback"** off for M4–M7 and M19, so dose 1 escalates after
  1 min. Turn it back on for M4b.

Use `adb shell dumpsys alarm | grep -A3 pillreminder` to check scheduled wake-ups, where useful.

| # | Case | Steps | Expected |
|---|------|-------|----------|
| M1 | Detection | Lock the phone. After earliest has passed, unlock twice within 2 min. | The dose 1 reminder appears within about 1 min. History shows wake source "detected". |
| M2 | Fallback | Leave the phone untouched past earliest until fallback. | The dose 1 reminder appears at fallback. Wake source is "fallback". |
| M3 | Reminder buttons | Check the dose 1 notification. | It has "I took my pill" and "Not today", and can't be swiped away. |
| M4 | Escalation, locked | Lock the phone and ignore the reminder. | After 1 min the screen turns on with the full-screen alarm, sound and vibration. |
| M4b | Dose 1 waits for fallback | Switch on, fallback set more than 60 min after earliest. After M1 (reminder shown by detection), ignore it. | No alarm until the fallback time. Home shows "Alarm at HH:MM" while it's more than 60 min away, then "Alarm in N min". |
| M4c | Remind me now is not held | Switch on, fresh day, in DETECTING tap "Remind me now" and ignore the reminder. | The alarm rings after the escalation delay, not at the fallback time. |
| M5 | Escalation, in use | Use another app and ignore the reminder. | The alarm activity appears (or a heads-up plus sound), and sound plays. |
| M6 | Ring timeout → snooze | Don't touch the alarm. | It stops after 1 min. The notification says "Snoozed until …", and it rings again 1 min later. |
| M7 | Snooze limit | Let it time out or snooze twice. | On the third ring there's no Snooze button. After its timeout the ringing stops and the ongoing notification remains. Nothing rings again. |
| M8 | Took pill → dose 2 | Tap "I took my pill". | The alarm and notification clear. Home shows "Next reminder at +3 min". The dose 2 reminder appears with "I'm done for today". |
| M9 | Dose 2/3 escalation | Ignore dose 2. | Same alarm behaviour as M4–M6. |
| M10 | Last dose | Take dose 3. | Home shows "All done for today". No further alarms (only rollover is scheduled). |
| M11 | Not today | Fresh day (adjust rollover/earliest as needed). Tap "Not today" on dose 1. | Nothing more happens today. History shows all doses as skipped. "Resume today" brings back the dose 1 reminder. |
| M12 | Done for today | On dose 2, tap "I'm done for today". | Doses 2 and 3 are skipped. Quiet. |
| M13 | Ringer: vibrate | Alert slider on vibrate, let the alarm fire. | Sound plays and the phone vibrates. |
| M14 | Ringer: silent | Alert slider on silent, let the alarm fire. | Screen turns on with the full-screen alert, and it vibrates. No sound. |
| M14b | Do Not Disturb | Ringer on normal, DND on, let the alarm fire. | Same as M14: light and vibration, no sound. |
| M15 | Backdate | Before the dose 1 reminder, record "Took pill 1 at" 5 minutes ago. | Detection stops. The dose 2 reminder comes at backdated time + interval (immediately if already past). |
| M16 | Backdate validation | Try a future time, and a time before the previous dose. | Both are rejected with an error. |
| M17 | Reboot | While WAITING for dose 2, reboot the phone. | The dose 2 reminder still arrives on time, or immediately after boot if overdue. |
| M18 | Reboot while pending | Reboot while the reminder is showing. | The reminder notification is back after boot, and escalation still happens. |
| M19 | Settings live | While the reminder is showing, change escalation from 1 to 3 min. | The "next action" time moves accordingly. |
| M20 | Stale action | Get a notification, let the day roll over (change rollover time), then tap the old button if it's still visible. | Nothing happens; no crash. |
| M21 | Test alarm | Settings → Test alarm. | It rings for about 10 s; the state is unchanged. |
| M22 | Missed | Leave a dose pending through rollover. | History shows it as "missed". |
| M23 | Timezone | Change the phone's timezone while WAITING. | No crash. The reminder fires at the same absolute time. |
| M24 | Overnight Doze | Real-world check: set defaults and leave the phone overnight. | The dose 1 reminder arrives the next morning after the first real phone use after 07:00. |
| M25 | Driving hold | Grant notification access (Setup). Start Google Maps navigation, then let a reminder escalate (or tap Snooze and wait). End navigation. | No ringing while navigating; the reminder says "Alarm held while you drive". The alarm rings within seconds of ending navigation. Settings → Advanced → Maps navigation says "navigating: yes" during navigation. |
| M26 | Status notification | Settings → Doses → turn on "Always show next pill", Save. Walk a day: before dose 1, take dose 1, let dose 2 come, take the last dose. Also lock the phone for a few minutes while WAITING. | Before dose 1: "Once you're up: pill 1 of 3, by HH:MM at the latest". After taking a dose: "Pill 2 of 3 at HH:MM" with a countdown in the header that keeps ticking while locked. When the reminder is due only the reminder shows (and it still buzzes); same while ringing. After the last dose: "All done for today". None of these make a sound, and none can be swiped away. Switching it off and saving removes it. |
| M27 | Pill supply | On Home, set the pill supply to 16 (3 doses a day). Take a dose, then let the next reminder appear. | Home shows "15 pills left, enough for 5 days" and no warning after the first dose. After the second dose (14 left) the next reminder's text reads "Running low: 14 pills left, enough for 4 days", and Home shows the warning. "Not today" doesn't change the count. "Stop counting" removes the warning from the next reminder. |

Then restore the default settings.
