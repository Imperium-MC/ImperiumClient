# Launcher plan

The ImperiumMC launcher will be a fork of [HeliosLauncher](https://github.com/dscalzi/HeliosLauncher)
(MIT, Electron). Helios is built for server-branded launchers: it reads a *distribution index* that
lists the game version, loader, mods and files to sync, so players always run the server's exact pack.
It will live in its own repository.

## Scope

- ImperiumMC branding and a Roman theme.
- Distribution index hosted by ImperiumMC: Minecraft 26.3, Fabric loader, ImperiumMC Client, a
  Fabulously-Optimized-based performance pack and the server resource pack.
- Java 25 runtime download and management.
- Microsoft account login.
- One-click "Play ImperiumMC" that joins `play.imperium-mc.net`.
- Auto-update of the launcher itself.

## Microsoft login

New launchers need their own Azure app registration, and Mojang must approve it before it can call
the Minecraft services API. Forks can't reuse another launcher's client ID.

1. Register an Azure app (public client, device-code flow).
2. Submit it for Minecraft API access and wait for approval. **Start this early.**
3. Until approved, the launcher can't sign in.

## Avoiding Alpine's bugs

Alpine starts Minecraft with a placeholder account and swaps the real one in later, which broke
chat signing with No Chat Reports. This launcher passes the real session on the command line from
the start, so the vanilla account setup runs once with the correct account.

## Milestones

1. Fork and rebrand; build installers for Windows (macOS/Linux after).
2. Distribution index and file sync working with a test pack.
3. Microsoft login (after Azure approval).
4. Release alongside ImperiumMC Client M2.
