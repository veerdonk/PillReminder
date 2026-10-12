# Manual test results

**Device:** OnePlus 8, Android 13 (OxygenOS 13).
**Build:** debug APK from CI on branch `claude/orchestrate-t8-sonnet-wh4ms3`, with T1–T11
included (head `da59b80`, before the merge to `main`).
**Script:** `05-test-plan.md` §2, with the 1-minute test timings.

The owner ran these tests and reported the results. Per-case notes weren't recorded.

| Result | Cases |
|--------|-------|
| Pass | M2–M23 and M14b. That covers fallback, reminder buttons, escalation, the snooze limit, the dose sequence, skipping, the ringer modes and DND, backdating, reboot, live settings, stale actions, Test alarm, missed doses and timezone. |
| Pass (2026-10-12) | **M26** (status notification with countdown), owner report on the PR #12 build. |
| Pending | **M1** (wake detection by unlocking) and **M24** (overnight Doze, real-world detection). Both need a real morning; the first one is scheduled for 2026-10-13. |

Bugs found in review before the manual pass (fixed):
- The alarm and the reminder shared notification ID 1001. When the ring service stopped, the
  system's asynchronous cancel of its notification could remove the reminder that had just been
  re-posted ("Snoozed until", or the ongoing reminder after the last snooze). The alarm now uses
  ID 1002 (01 A26, design §7).

Follow-ups: none open. Anything M1/M24 turns up is fixed from `main`.
