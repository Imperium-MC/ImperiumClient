# Legions tooling

Imperium Client's reimagining of Alpine Client's **factions** bundle for ImperiumMC's
Legions (the owner's directive: take Alpine's good ideas, build them for OUR server).
Alpine bundles a factions status HUD, breadcrumbs, cannon_playback / cannon_view (TNT
trajectory preview), explosion_boxes (rendered blast radius) and patchcrumbs (siege
markers). What ships here:

| Piece | Where | Status |
|---|---|---|
| Legion status HUD | `LegionHud` (`imperiumclient:legionhud`) | shell — fed by `LegionStateRegistry`, invisible until the server channel exists |
| Breadcrumbs | `BreadcrumbsHud` (`imperiumclient:breadcrumbshud`) + `BreadcrumbManager` | done — keybind drop/clear, per-dimension persistence, age labels, auto-expire |
| Explosion boxes | `ExplosionBoxes` + `ExplosionBoxRendererMixin` | done — translucent blast-radius cube for the TNT fuse duration |
| Cannon playback/view | — | **out of scope this pass**: needs physics prediction (see below) |
| Patchcrumbs (siege markers) | — | not built; when sieges exist server-side they can ride the breadcrumb mechanism |

## Legion status HUD (the shell)

`io.github.axolotlclient.imperium.LegionStateRegistry` is a thread-safe singleton holding
one snapshot record:

```java
record LegionState(String name, int onlineMembers, int totalMembers, long power, int level)
```

- `set(...)` / `update(online, power)` / `clear()` are the only mutators; `null` state
  means "no legion" and the HUD entry draws nothing (the BoostsHud shell pattern).
- `LegionHud` renders `[Name] 3/12 · Power 4.2k`, each part toggleable.
- The static setter is test-friendly: `LegionsToolingGameTest` pins the semantics without
  any server attached.

## Breadcrumbs

- Keybind `key.legions.breadcrumbs_drop` (default **N**) drops a sequentially-named crumb
  at your feet; `key.legions.breadcrumbs_clear` (unbound by default) clears all.
- Persistence reuses the WaypointManager serialization pattern in a **separate** blob: a
  `breadcrumbs_data` `StringOption` inside the module config holds a JSON array of
  `{"name","x","y","z","dimension","droppedAt"}` — per-dimension, profile-aware for free,
  and waypoints files are untouched.
- HUD rows are WaypointsHud-style distance labels with age: `1 42m 5m ago`, plus the same
  rotating direction arrow.
- Auto-expire: never / 5 / 15 / 60 minutes (default 15). Expiry is enforced lazily by the
  manager's accessor, so it keeps working with the HUD hidden.

## Explosion boxes

- While a primed TNT renders within `range` blocks of the camera, a translucent cube of
  half-extent `radius` (default 4 = vanilla TNT power) is drawn around it — for the whole
  fuse, because the hook is the tail of `TntRenderer.submit`. No entity tracking, no
  timers; the box follows the TNT until it detonates.
- World-space drawing follows the block-outline fill approach: camera-relative vertices
  through `submitCustomGeometry` into a dedicated quads `RenderType` (same construction as
  `DrawUtil.QUADS`). The mixin is its own class (`ExplosionBoxRendererMixin`), registered
  in `axolotlclient.mixins.json`; `DrawUtil` / `WorldRendererMixin` are untouched.
- Options: radius (1–8), range (8–256), color, opacity (5–100%), enabled.

## The future `imperiumclient:legion` channel (plugin-side adoption hook)

The server half lives in the ImperiumMC plugin (net/imperiummc/managers/LegionManager.kt
already tracks everything needed). Proposed wire shape, following the existing
`imperiumclient:hello` / `imperiumclient:features` conventions (Bukkit plugin messaging,
bare UTF-8 JSON, no NBT framing — see `ImperiumIntegration.RAW_UTF8`):

**Channel:** `imperiumclient:legion` (clientbound)

```json
{"type":"full","name":"Spartans","onlineMembers":3,"totalMembers":12,"power":4200,"level":5}
{"type":"delta","onlineMembers":4,"power":4250}
{"type":"none"}
```

- `full` — sent on join and on legion join/leave/rename; maps 1:1 to
  `LegionStateRegistry.set(name, onlineMembers, totalMembers, power, level)`.
  - `power` = `LegionManager.getTotalWarScore(name)` (the war-score standings currency
    players see in `/legion warscore`); `totalMembers` = `Legion.memberCount`;
    `onlineMembers` = count of members currently online; `level` = `Legion.level`.
  - `displayName` (MiniMessage) exists server-side but the wire carries the plain ASCII
    `name` — the HUD renders plain text.
- `delta` — cheap per-change pushes (member logins/logouts, war-score ticks); maps to
  `LegionStateRegistry.update(onlineMembers, power)`. Absent fields keep the last value.
- `none` — player is legion-less; maps to `LegionStateRegistry.clear()`. Also clear on
  disconnect (the client already clears session state on `ClientPlayConnectionEvents`).

The client receiver (when written) should call the registry on the client thread the same
way `ImperiumIntegration` dispatches features payloads. Nothing else in the HUD needs to
change — populating the registry is the entire integration.

## Next steps

- **Cannon playback/view (TNT trajectory preview): built** (`CannonTrajectory` +
  `TntTrajectory`). The pure integrator runs the exact 26.3 `PrimedTnt` step order
  (gravity 0.04 before the move, air drag 0.98 after it, stop at the first solid block via
  a world indirection); while the player holds TNT the predicted arc of a launch along the
  look direction (configurable power, for cannon charge) is drawn with the gizmo line
  pipeline, and live primed TNT within range gets its remaining flight + a blast box at the
  predicted landing site (radius taken from `ExplosionBoxes` so the two visual systems
  merge). Still open from Alpine's cannon_playback: frame-stepped playback of recorded
  flights and falling-block recording.
- **Patchcrumbs:** once sieges are observable on the wire, siege markers can reuse
  `BreadcrumbManager` with a dedicated flag and color instead of a parallel list.
