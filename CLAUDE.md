# Instructions for implementing agents

This repo holds a single-user Android app (Kotlin, Jetpack Compose). The specification
lives in `docs/`. Read the docs in this order before writing code:

1. `docs/01-requirements.md`: what to build. The requirements are authoritative.
2. `docs/02-state-machine.md`: the core behaviour. Implement it exactly.
3. `docs/03-technical-design.md`: how to build it: package layout, interfaces and Android APIs.
4. `docs/04-implementation-plan.md`: pick a task here. Check its dependencies first.

## Working rules

- **One task per branch/PR where practical.** Put the task ID in the commit message, e.g. `T3: engine reducer`.
- **Respect the interfaces in `03-technical-design.md` §4.** Parallel tasks rely on them.
  If an interface has to change, update the doc in the same commit and say so in the PR.
- **Keep the domain layer pure.** The `:core` module must not depend on Android. All time
  comes from an injected `java.time.Clock`. This is what makes the timing logic testable.
- **Write unit tests for every row of the transition table** in `02-state-machine.md` (task T3).
- **Before pushing, run** `./gradlew :core:test lint testDebugUnitTest assembleDebug`. Lint
  must stay free of warnings.
- **Local Android SDK:** cloud containers don't ship one. Install it with:
  - download `commandlinetools-linux-*_latest.zip` from `dl.google.com/android/repository/`
    into `/opt/android-sdk/cmdline-tools/latest`
  - `yes | sdkmanager --licenses`
  - `sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"`
  - `echo sdk.dir=/opt/android-sdk > local.properties`

  If Maven Central answers `429 Too Many Requests` on the first build, re-run with
  `--max-workers=2`. Each run caches more, so it succeeds within a few attempts.
- **Don't add dependencies beyond those listed in the design** without a reason in the PR.
  We're deliberately not using Hilt or Firebase, and there is no network access at all.
- **Update the task status** in `docs/04-implementation-plan.md` (`[ ]` → `[x]`) when a task is done.
- **Report spec gaps instead of guessing.** If the spec is ambiguous, pick the option
  closest to the spirit of `01-requirements.md`, add it to the "Open points / assumptions" list there,
  and mention it in the PR.
- **Target device:** a OnePlus 8 on Android 13 (API 33). Release builds are sideloaded; the app is not on the Play Store.
