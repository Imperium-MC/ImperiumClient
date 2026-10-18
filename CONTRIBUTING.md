# Contributing to Imperium Client

## Setup

1. Install JDK 25.
2. Clone the repo and run `./gradlew :latest:build` once to fetch Minecraft and dependencies.
3. Open the project in IntelliJ IDEA (recommended) and use the generated `runClient` configuration,
   or run `./gradlew :latest:runClient`.

Only the `common` and `latest` (26.3) projects are built. `gradle.properties` controls this through
`axolotlclient.modules.*`; leave it at `latest=true`.

## Where things go

- `common/`: version-independent code (module logic, config, HUD framework).
- `versions/latest/`: 26.3-specific code, mixins and resources.
- `docs/`: plans and specs. Update [alpine-parity.md](docs/alpine-parity.md) when a module's status changes.

## Rules

- **No Alpine code or assets.** Parity features are reimplemented from behaviour and settings.
  Do not decompile, copy or trace Alpine's jar to write code.
- **License headers.** Every source file carries the LGPL header from `HEADER`. The licenser
  plugin checks it; run `./gradlew applyLicenses` to add it to new files.
- **Automation features are server-gated.** Anything that clicks, fishes, mines or moves for the
  player must follow [server-gated-features.md](docs/server-gated-features.md).
- **One logical change per PR**, with a conventional commit title (`feat:`, `fix:`, `perf:`, `docs:`).
- **Test in game.** Say in the PR what you checked in a dev client or on a server.
- Note user-facing changes in `CHANGELOG.md`.

## Reporting bugs

Open an issue with your client log (`logs/latest.log`) and crash report if there is one, plus the
list of other mods installed.
