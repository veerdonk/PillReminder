# PillReminder

A personal Android app that notices when I've woken up, reminds me to take my
first pill, and then reminds me again for each following dose during the day.
If I ignore a reminder, it turns into a snoozable alarm.

**Status:** project scaffold and CI are in place (task T1). The app itself is not implemented yet.

## Install on the phone

1. Open the latest successful **Android CI** run in the repo's Actions tab and download the
   `PillReminder-debug-<sha>` artifact. It's a zip containing `app-debug.apk`.
2. Copy the APK to the phone and open it. Allow "install unknown apps" for your file manager
   or browser when Android asks.
3. Later builds install over the previous one and keep your data. That works because every
   build is signed with the same key, `app/debug.keystore`, which is committed on purpose. The
   key isn't secret, which is fine for a personal sideloaded app, but don't reuse it for
   anything published.

## Build locally

Requires JDK 17+ and the Android SDK (platform 37, build-tools 37.0.0), with `ANDROID_HOME`
set or `sdk.dir` in `local.properties`.

```
./gradlew :core:test lint testDebugUnitTest assembleDebug
```

## Documentation

| Doc | What it's for |
|-----|---------------|
| [docs/01-requirements.md](docs/01-requirements.md) | Functional spec: what the app does, all settings and defaults, decisions taken |
| [docs/02-state-machine.md](docs/02-state-machine.md) | The daily reminder flow as a state machine, with a full transition table |
| [docs/03-technical-design.md](docs/03-technical-design.md) | Architecture, modules, interfaces, Android APIs, permissions |
| [docs/04-implementation-plan.md](docs/04-implementation-plan.md) | Ordered tasks with dependencies and acceptance criteria, for implementing agents |
| [docs/05-test-plan.md](docs/05-test-plan.md) | Unit-test expectations and the manual on-device test script |
| [docs/06-device-setup-oneplus.md](docs/06-device-setup-oneplus.md) | One-time phone settings for the OnePlus 8 / OxygenOS 13 so the app isn't killed |

Implementing agents: start with [CLAUDE.md](CLAUDE.md).
