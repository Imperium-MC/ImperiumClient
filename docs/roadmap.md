# Roadmap

Milestones run in order; each ends with a build that works in game.

## Current status (2026-10-18)

| Milestone | State |
|---|---|
| M0 fork baseline | Done |
| M1 menu + HUD style | Done — menu shell, search, toggles, HUD style options, inline settings panel, background blur |
| M2 user's modules | Done — all priority modules shipped across waves 2-5 (see [alpine-parity.md](alpine-parity.md)) |
| M3 performance | Done except the chunk-level items: frame limiter slice, `hud_frame_limit` (record/replay HUD cache), `text_render_distance`, `disable_tnt_expansion`, `optimize_spawners`, `disable_enchant_table_books`, each measured by `PerformanceBenchmarkGameTest` (see M3 below). Open: hopper *block* hiding + lazy chunk loading (chunk-mesh work), chest simplification (no honest win) |
| M4 remaining modules | Done — the parity sheet shows zero real gaps (2026-10-18) |
| M5 ImperiumMC integration | Done — handshake + server-gated features live both sides: client enforcement layer and plugin `ImperiumClientIntegrationService` (v2 tier table + rank perks) |
| M6 launcher | Done — [ImperiumLauncher](https://github.com/Imperium-MC/ImperiumLauncher) v0.1.0 released: Microsoft login approved, signed Windows installer, auto-updater feed |

History note: PR #1 (titles HUD + handshake) landed on `main` together with its two review fixes
(NBT-codec handshake that would disconnect players, and the clipped titles HUD); branch
`review/pr-1` is kept for reference only — merge `main`, never that branch.

## Next tasks

The authoritative backlog is the work order in [issue #12](https://github.com/Imperium-MC/ImperiumClient/issues/12);
work strictly in its order. Status as of 2026-10-20 (wave 6 shipped the inline settings panel, HUD background
blur, the gizmo-window fix, and the first M3 performance slice — see the milestone table above):

1. **Launcher blockers, all in flight for release 0.1.2:** the launch crash (ImperiumLauncher#4),
   the renderer hardening (private advisory, neutral commits), download verification
   (ImperiumLauncher#6), and the crash screen (ImperiumLauncher#5 part 3).
2. **This repo's CI** (#11): build plus gametest on every PR; PR-gated merges from then on.
3. **Review fixes** (#15): titles colour/wrap fix, enforcement toggle removed from player settings,
   interceptor replace-policy doc — then the Kotlin toolchain and the `imperium/` package port (#2),
   with Fabric Language Kotlin added to the launcher pack.
4. **Plugin CI + anticheat** (ImperiumMC-Plugin#12 step 0, #13).
5. **UI v3** per the design spec now merged at `docs/design/` (#4): title screen, shell and module
   cards, settings pages, HUD editor — in Kotlin.
6. Everything else in #12, top to bottom: the M3 remainder is down to the chunk-level items
   (hopper *block* hiding belongs with lazy chunk loading; chests have no honest win — see M3),
   cosmetics channel design (owner input wanted), real-server enforcement spot-check,
   parity-sheet regeneration, translations.


## M0: Fork baseline

- Fork AxolotlClient `dev` (3.2.3-alpha.1), build only `common` + `latest` (26.3).
- Publish on GitHub under LGPL-3.0 with these plan docs.
- Rebrand: mod name, icon, menu title, config folder, so it can run next to upstream AxolotlClient
  without clashing. Keep upstream credits and license headers.

## M1: Alpine-style menu and HUD editor

**Status:** menu shell done. Right Shift (and the title/pause menu button) opens `ImperiumMenuScreen`:
category sidebar, search, module cards with toggle and gear, Edit HUD / Profiles / All Settings. Cards are
built by `MenuEntries` from every HUD entry plus every config category. The gear (or a right-click) opens
the module's settings inline inside the menu, as an overlay panel over the card grid. HUD elements can
blur the world behind their background (`background_blur`).
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

Built on top of Sodium/Lithium, not replacing them. Every shipped item below is measured by
`PerformanceBenchmarkGameTest` (`./gradlew :latest:runClientGameTest`): per feature it warms
100 frames, measures 300 frames with the feature OFF then ON using `Minecraft.getFrameTimeNs()`
(the debug-FPS source, via the per-frame `FrameTimeSampler` hook) with vsync off and the vanilla
framerate limit raised, and appends both phases to
`versions/latest/build/run/clientGameTest/benchmarks/performance-benchmarks.jsonl`. The numbers
are local truth from the dev machine (RTX 3060; the scenes run uncapped at ~800-2300 fps, whole
frames of 0.4-1.6 ms), not CI truth: at these frame rates run-to-run variance of roughly
±0.3 ms dominates anything small. The ON-phase asserts on per-feature counters prove each toggle
actually fires, and every phase asserts the expected frames arrived (rendering alive).

- HUD rendered at a capped rate (`hud_frame_limit`, 0-120, default 0 = off). **Shipped** as a
  record/replay cache (`HudFrameCache`) rather than an offscreen texture: the GUI elements the
  HUD submits are immutable render-state snapshots, so cacheable entries re-RECORD into the
  `GuiRenderState` at most N times per second (frame-count pacing off the game's own frame-time
  source) and the recorded elements are re-submitted unchanged between records. Entries
  reporting `HudEntry.dynamicContent()` — every text readout (FPS, CPS, coordinates, timers),
  the minimap, keystrokes, the paperdoll — always render live and are never captured, so
  nothing dynamic can visibly freeze; the cacheable set is the event-driven item HUDs (armor,
  inventory, IP, icon). Measured (those four cached + FPS/CPS live, limit 30): avg frame
  0.835 -> 0.861 ms, i.e. **within noise on this machine**; across six runs the delta ranged
  -0.06..+0.37 ms with no consistent direction. Shipped anyway because it is default-off,
  measures as cost-neutral, and the one clearly positive run (-24% frame time) came from a
  slower-clock pass of the same scene — the honest verdict is "no measurable gain locally".
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
- Render-distance limits for sign text and floating name tags. **Shipped** as
  `text_render_distance` (0-128 blocks, default 0 = vanilla): sign lettering
  (`AbstractSignRenderer.submitSignText`, distance from the block-entity pose translation) and all
  entity name tags including armour-stand holograms (`EntityRenderer.submitNameDisplay`, off
  `distanceToCameraSq`) are skipped beyond the limit; the sign wood and the entities themselves
  keep rendering. Measured (14 signs + 14 named armour stands at 22-40 blocks, limit 16): avg
  frame 1.190 -> 0.656 ms (**-45%, +684 fps**; repeat runs saved 0.35-0.65 ms). The clearest
  win of the tail.
- Cannon mode: minimal TNT rendering. **Shipped** as `disable_tnt_expansion`: the final-second
  swell scale in `TntRenderer.submit` is forced to 1 so primed TNT keeps its normal size (the
  entity stays fully visible), composing with `disable_tnt_flash`. Measured (24 long-fuse primed
  TNT): avg frame 0.710 -> 0.579 ms — within the machine noise, as expected for a visual toggle
  whose wrapped call only runs during the final fuse second; the swell-suppression counter
  proves the path fires on a short-fuse TNT.
- Spawner and enchanting-table simplification. **Shipped:** `optimize_spawners` skips the spinning
  display entity entirely — both `BaseSpawner.getOrCreateDisplayEntity` and the
  `TrialSpawnerRenderer.extractSpawnerData` extraction it feeds (vanilla monster spawners only;
  the cage is chunk-meshed and stays) — and `disable_enchant_table_books` cancels the
  enchanting-table BER submit (that BER is only the floating book; the table block itself is
  chunk-meshed). Measured: spawners avg frame 0.426 -> 0.509 ms (**no measurable gain** at 16
  spawners — the per-spawner extraction is too cheap to see locally); enchanting books avg frame
  0.945 -> 0.847 ms (-10%; an earlier run measured -24%), a real but noisy win for book-heavy
  scenes.
- Chest simplification. **Not shipped:** in 26.3 the entire chest is a per-frame BER model
  submit (no static chest mesh exists), so the only client-side "simplification" is freezing
  the lid — which still runs the BER and saves nothing — or skipping the BER, which makes chests
  invisible. There is no honest per-frame win short of a hide toggle, so `optimize_chests` was
  skipped rather than shipped as a no-op.
- Hopper *block* hiding and lazy chunk loading. **Open:** both are chunk-mesh work — 26.3 exposes
  no per-block visibility predicate or render-layer hook that avoids forcing chunk rebuilds, and
  a resource-pack model swap remeshes every loaded chunk on toggle. The `hide_hoppers` tooltip
  already documents that placed blocks are not covered.

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
