# Roadmap

Milestones run in order; each ends with a build that works in game.

## Current status (2026-10-18)

| Milestone | State |
|---|---|
| M0 fork baseline | Done |
| M1 menu + HUD style | Menu shell, search, toggles, HUD style options done. Inline settings panel and background blur left |
| M2 user's modules | Titles HUD in [PR #1](https://github.com/Imperium-MC/ImperiumClient/pull/1); rest not started |
| M3 performance | Not started |
| M4 remaining modules | Not started |
| M5 ImperiumMC integration | Handshake client half in PR #1. Server half shipped in the plugin (`ImperiumClientIntegrationService`) |
| M6 launcher | Repo [ImperiumLauncher](https://github.com/Imperium-MC/ImperiumLauncher) builds; Microsoft login waits on Mojang API approval |

PR #1 had two bugs, fixed on branch `review/pr-1`: the handshake used an NBT codec while the plugin sends
raw UTF-8 (would disconnect players), and the titles HUD dropped colours and was clipped off-screen. Merge
that branch (or squash it) rather than PR #1 as submitted.

## Next tasks

**Work strictly in this order.** Each item is one or more PRs with green CI, held to #3. This list overrides
everything below it. The full backlog is the work order in #12. Don't start wave 6 features until 1–6 are done.

1. **The owner can't play:** Imperium-MC/ImperiumLauncher#4 (natives extracted to the wrong folder, so
   `lwjgl.dll` isn't found). Ship it in a new launcher release with a bumped version number.
2. **Launcher security advisory** GHSA-vggq-h9gm-26m2 (private; see its top note). Ship it in the same release
   as item 1, with neutral commit titles.
3. **Launcher download integrity:** Imperium-MC/ImperiumLauncher#6 (hashes not verified).
4. **CI on this repo** (#11): build plus `runClientGameTest` on every PR. Render tests must assert pixels, not
   just save screenshots (see #15).
5. **Plugin CI:** Imperium-MC/ImperiumMC-Plugin#12 step 0 (`gradlew` mode, stray `ares` gitlink), then the
   anticheat fixes in ImperiumMC-Plugin#13.
6. **Review fixes in #15:** the titles fix, Kotlin toolchain (#2), the features/perks dispatch, removing the
   enforcement toggle from module settings.
7. **UI v3** (#4, spec on PR #14 `docs/design/README.md`): title screen, then the shell and module cards,
   then settings pages inside the shell, then the HUD editor. In Kotlin, replacing CarvedTheme.
8. Everything else in #12, top to bottom.

## M0: Fork baseline

- Fork AxolotlClient `dev` (3.2.3-alpha.1), build only `common` + `latest` (26.3).
- Publish on GitHub under LGPL-3.0 with these plan docs.
- Rebrand: mod name, icon, menu title, config folder, so it can run next to upstream AxolotlClient
  without clashing. Keep upstream credits and license headers.

## M1: Alpine-style menu and HUD editor

**Status:** menu shell done. Right Shift (and the title/pause menu button) opens `ImperiumMenuScreen`:
category sidebar, search, module cards with toggle and gear, Edit HUD / Profiles / All Settings. Cards are
built by `MenuEntries` from every HUD entry plus every config category. Settings still open Axolotl's
config screen; an inline Alpine-style settings panel and background blur are the remaining M1 items.
Covered by the `ImperiumMenuGameTest` client gametest (`./gradlew :latest:runClientGameTest`).


- Full-screen menu on Right Shift: category sidebar, search, module cards with toggle and gear,
  per-module settings page, ImperiumMC (Roman) theme.
- Shared `HudStyle` block on every HUD module: background, brackets, corner radius, background blur,
  border, text colour and shadow, width/height, hide in F3, z-level.
- Profiles: named setting bundles with auto-switch per server, world and game version.
- HUD editor with snapping (extends Axolotl's `HudEditScreen` / `SnappingHelper`).

## M2: The user's 23 enabled modules

The **bold** rows in [alpine-parity.md](alpine-parity.md): CPS, FPS, TPS, compass, scoreboard,
boss bar, titles, info HUD, cooldowns, toggle sneak/sprint (+ status HUD), fullbright, environment,
chat settings, tab editor, nametag editor, waypoints, inventory search, scrollable tooltips,
factions, EnchantedMC boosts, EnchantedMC fishing (server-gated).

## M3: Performance layer

Built on top of Sodium/Lithium, not replacing them:

- HUD rendered at a capped rate (`hud_frame_limit`) and reused between frames.
- Frame limiter: none / minimised / unfocused / AFK, with background, AFK and long-AFK FPS caps.
  Replaces Dynamic FPS, which conflicts with a second limiter.
- Render-distance limits and hiding for holograms (armor-stand text), mobs, signs (entity and text
  separately), plus display-name optimisation.
- Cannon mode: minimal TNT/falling-block rendering, no TNT flash or expansion.
- Chest/spawner/enchant-table render simplification, lazy chunk loading with an update cap.

Each item gets a before/after FPS measurement on the same scene before it's merged.

## M4: Remaining Alpine modules

The other ~47 rows of the parity spec, in batches of simple text HUDs, render tweaks, then the large
ones (minimap, WorldEdit CUI, Spotify, emotes).

## M5: ImperiumMC integration

- Server handshake channel (`imperiumclient:hello`) declaring client version and feature flags.
- Server-gated features protocol ([server-gated-features.md](server-gated-features.md)).
- Native rendering of ImperiumMC cosmetics, rank/Denarius HUD, story-state HUD, NPC (mannequin) safe
  rendering.
- Plugin side lives in the ImperiumMC plugin repo.

## M6: Launcher

Imperium Launcher, forked from HeliosLauncher. See [launcher-plan.md](launcher-plan.md).

## Known compatibility notes

From running Alpine 1.8.0-snapshot alongside Fabulously Optimized:

- Two frame limiters (Alpine + Dynamic FPS) fight over the vanilla inactivity option.
- Screenshot mixins from two mods (Alpine + renice-shot) break `Screenshot` class transformation.
- No Chat Reports checks signing keys once at startup. A client that swaps the session later must
  re-run that check, or secure-chat servers disable chat.
- Avatar renderers also draw `ClientMannequin`; never cast an avatar's entity to a player type unchecked.
