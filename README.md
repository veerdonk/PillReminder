# PillReminder

A personal Android app that notices when I've woken up, reminds me to take my
first pill, and then reminds me again for each following dose during the day.
If I ignore a reminder, it turns into a snoozable alarm.

**Status:** specification only. Nothing has been implemented yet.

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
