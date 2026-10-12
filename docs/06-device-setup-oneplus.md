# 06 — OnePlus 8 (OxygenOS 13) setup

OxygenOS kills background apps aggressively, and if it kills this app the reminders won't
fire. Do these steps once after installing. Menu names vary slightly between OxygenOS builds;
if a path doesn't match, use the search box in Settings.

The in-app **Setup** screen covers steps 1–5 with buttons. Steps 6–8 are OnePlus-specific and
can only be done by hand.

1. **Notifications:** allow them when asked. Then check that both channels, "Pill reminders" and
   "Pill alarm", are on: *Settings → Apps → PillReminder → Notifications*.
2. **Usage access:** *Settings → Apps → Special app access → Usage access (Device & app history)
   → PillReminder → Allow*. Without it, only the fallback time triggers dose 1.
3. **Alarms & reminders:** *Special app access → Alarms & reminders → PillReminder → Allow*
   (normally already allowed).
4. **Full-screen notifications:** allowed by default on Android 13. Re-check this after an
   upgrade to Android 14+.
5. **Battery optimisation:** *Settings → Battery → More settings → Optimise battery use →
   PillReminder → Don't optimise* (or *Apps → PillReminder → Battery usage → Allow background
   activity*).
6. **Auto-launch:** *Settings → Apps → Auto launch* (or *App management → PillReminder → Auto
   launch*) → on. This lets the app start after a reboot.
7. **Lock the app in Recents:** open Recents, tap the ⋮ menu (or long-press) on PillReminder →
   **Lock**. This stops "clear all" from killing it.
8. **Turn off "Sleep standby optimisation"** if present: *Battery → More settings → Sleep standby
   optimisation → off*. Also turn off any "Deep optimisation" / "Advanced optimisation" for the app.
9. **Do Not Disturb:** if the alarm doesn't vibrate or show while DND is on (test M14b), go to
   *Settings → Sound & vibration → Do Not Disturb* and allow **Alarms**, or add PillReminder
   under *Apps* exceptions.

**Verify:** run Settings → **Test alarm**. Then lock the phone and wait for a 1-minute test
escalation. The screen should turn on with the alarm.

**After an OxygenOS update,** re-check steps 5–9, because updates sometimes reset them.
