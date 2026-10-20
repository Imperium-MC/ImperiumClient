# Roadmap

Milestones run in order; each ends with a build that works in game.

## Current status (2026-10-18)

| Milestone | State |
|---|---|
| M0 fork baseline | Done |
| M1 menu + HUD style | Menu shell, search, toggles, HUD style options done. Inline settings panel and background blur left |
| M2 user's modules | Titles HUD in [PR #1](https://github.com/Imperium-MC/ImperiumClient/pull/1); rest not started |
| M3 performance | First slice done: `performance` category with the frame limiter (limiter/background/AFK/long-AFK, replacing Dynamic FPS, deferring to vanilla's inactivity throttle when vanilla is stricter) plus `disable_tnt_flash`, `hide_lightning_bolts`, `hide_hoppers` (items + minecarts) and `hide_distant_entities` + `entity_render_distance`. Open: `hud_frame_limit`, hopper *block* hiding, lazy chunk loading, chest/spawner/enchant-table simplification, cannon-minimal mode, hologram/sign text distance limits |
| M4 remaining modules | Not started |
| M5 ImperiumMC integration | Handshake client half in PR #1. Server half shipped in the plugin (`ImperiumClientIntegrationService`) |
| M6 launcher | Repo [ImperiumLauncher](https://github.com/Imperium-MC/ImperiumLauncher) builds; Microsoft login waits on Mojang API approval |

PR #1 had two bugs, fixed on branch `review/pr-1`: the handshake used an NBT codec while the plugin sends
raw UTF-8 (would disconnect players), and the titles HUD dropped colours and was clipped off-screen. Merge
that branch (or squash it) rather than PR #1 as submitted.

## Next tasks

Pick from the top; each is one PR. Check [dev-notes.md](dev-notes.md) first.

1. Inline settings panel in the menu: the gear opens the module's settings inside the menu instead of
   Axolotl's config screen. Spec per module: [module-settings.md](module-settings.md).
2. Background blur option for HUD elements (`blur_radius`). `MenuBlur` has a blur pass to reuse.
3. M2 modules, in this order: tab editor, waypoints, inventory search, cooldowns, info HUD, environment
   (weather), chat settings (timestamps, smooth chat, compact chat), toggle sneak/sprint extras, compass
   waypoints, nametag editor, TPS colour tiers.
4. Hide config categories that aren't modules (Account Management, Authentication) from the module grid,
   or move them to a separate "Client" section.
5. Gated modules (auto fish/click) checking `ImperiumClientSession.isAllowed(...)` once PR #1 lands.


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

- HUD rendered at a capped rate (`hud_frame_limit`) and reused between frames. **Not shipped:** the
  HUD draws immediately into the per-frame GUI layer, so "keep the previous result" needs an
  offscreen-texture cache; skipping frames without it makes the HUD blink out. Skipped rather than
  shipping a frozen/blinking HUD (dynamic elements — CPS, FPS, coordinates — must not visibly
  freeze). Revisit with a render-target cache in `HudManager`.
- Frame limiter: none / minimised / unfocused / AFK, with background, AFK and long-AFK FPS caps.
  Replaces Dynamic FPS, which conflicts with a second limiter. **Shipped** as the `performance`
  category: `limiter`, `background_fps`, `afk_fps` + `afk_delay`, `long_afk_fps` + `long_afk_delay`.
  Hooks vanilla's `FramerateLimitTracker` (the same seam a limiter mod uses) so vanilla does the
  frame pacing; ours only ever lowers the limit, deferring to vanilla's inactivity throttle or the
  user's max FPS whenever that is stricter, and never applies during world loading or gametest runs.
- Cheap render-hide toggles. **Shipped:** `disable_tnt_flash` (primed TNT stays visible, only the
  white blink goes), `hide_lightning_bolts`, `hide_hoppers` (dropped hopper items + hopper
  minecarts — placed hopper blocks are chunk-meshed in 26.3 with no per-frame renderer, so the
  block form needs the chunk-compile work below), `hide_distant_entities` + `entity_render_distance`
  (horizontal distance from the camera; the camera entity and whatever the player rides are exempt).
  All gate `LevelExtractor.isEntityVisible` / `TntRenderer.submit`, so hidden entities skip render
  state extraction entirely.
- Render-distance limits and hiding for holograms (armor-stand text), signs (entity and text
  separately), plus display-name optimisation. **Open.**
- Cannon mode: minimal TNT/falling-block rendering, no TNT flash or expansion. **Open** (the flash
  half exists now via `disable_tnt_flash`; expansion/minimal mode not).
- Chest/spawner/enchant-table render simplification, lazy chunk loading with an update cap.
  **Open.**

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
