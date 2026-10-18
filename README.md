# Imperium Client

An open-source Minecraft client mod for **Minecraft 26.3 (Fabric)**, built for
[ImperiumMC](https://imperium-mc.net) and usable on any server.

The goal is feature and UX parity with Alpine Client (modules, settings, menu and HUD editor),
plus a performance layer and ImperiumMC integration. It is a fork of
[AxolotlClient](https://codeberg.org/AxolotlClient/AxolotlClient-mod), which provides the
module system, HUD framework and many of the HUD modules.

> **Status:** early fork. The codebase is still AxolotlClient, narrowed to 26.3. See the
> [roadmap](docs/roadmap.md) for what's being built and in what order.

## Plan docs

| Doc | What it covers |
|---|---|
| [Roadmap](docs/roadmap.md) | Milestones from fork to 1.0 |
| [Alpine parity spec](docs/alpine-parity.md) | All 70 Alpine modules, their settings, and how each maps onto this codebase |
| [Server-gated features](docs/server-gated-features.md) | How servers enable or block automation features |
| [Launcher plan](docs/launcher-plan.md) | The HeliosLauncher-based ImperiumMC launcher |
| [Upstream README](docs/UPSTREAM-README.md) | AxolotlClient's original feature list and credits |

## Building

Requires JDK 25.

```bash
./gradlew :latest:build
```

The mod jar lands in `versions/latest/build/libs/`. Run a dev client with:

```bash
./gradlew :latest:runClient
```

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## License

LGPL-3.0, inherited from AxolotlClient. See [LICENSE](LICENSE).

```
Copyright (C) 2021-present moehreag + Contributors (AxolotlClient)
Copyright (C) 2026-present ImperiumMC + Contributors (Imperium Client changes)
```

Imperium Client is not affiliated with Alpine Client. Alpine's code and assets are not used;
parity features are reimplemented from observed behaviour.
