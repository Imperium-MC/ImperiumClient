# worldedit:cui — server-side guide

The Imperium client renders WorldEdit-style selections from the `worldedit:cui` plugin
channel. Any server feature that wants an in-world region overlay (mine regions, plot
regions, event areas, `/mine region` display…) can drive it; this is the same public
protocol WorldEdit and WorldEditCUI speak (EPL-2.0, EngineHub), so a plain
`player.sendPluginMessage` is all that is needed.

## Channel mechanics

- Channel name: `worldedit:cui` (register it with Bukkit's Messenger, both directions).
- Payload: **bare UTF-8 text**, `|`-separated, no length prefix, no NBT. Exactly the
  Bukkit plugin-messaging convention the ImperiumMC handshake uses
  (docs/dev-notes.md "Server protocol").
- The client announces itself once per session by sending `v|4` on the channel.
  **Only send selections to players that announced** — that is also how vanilla
  WorldEdit decides, and it keeps silent for stock clients.
- The client clears its selection on disconnect, on world change, and when you send a
  selection event naming a shape it does not know.
- There is no per-message ack; re-send the full selection on every change.

## Messages the client understands

Event id first. A leading `+` marks WorldEdit multi-selection events (the client renders
the last-announced region only; the trailing region UUID is ignored).

| Message | Meaning |
|---|---|
| `s|cuboid` | start a cuboid selection (points reset) |
| `s|ellipsoid` | start an ellipsoid selection (center/radii reset) |
| `s|polygon2d` / `s|cylinder` | parsed and stored, not rendered yet |
| `s|<unknown>` | clears the selection |
| `p|0|x\|y\|z\|area` | first corner (block coords; `area` optional, ignored) |
| `p|1|x\|y\|z\|area` | second corner |
| `p2|blockX\|blockY\|blockZ` | legacy second-corner update |
| `e|0|cx\|cy\|cz` | ellipsoid center block |
| `e|1|rx\|ry\|rz` | ellipsoid radii (fractional allowed) |
| `mm|minY\|maxY` | polygon y bounds |

The client also accepts the legacy one-shot cuboid line, useful for a single
fire-and-forget overlay:

```
p|<worldName>|minX|minY|minZ|maxX|maxY|maxZ|cuboid
```

`u`, `col` and `grid` events are accepted and ignored (WorldEdit sends them; other
clients use them for update ticks and colour hints).

## What it renders

- **Cuboid**: the bounding-box wireframe, a per-block grid on the three faces touching
  the minimum corner, and the two corner blocks in separate colours (pos1/pos2). Corner
  coordinates are inclusive — a corner at `10,64,-5` and one at `20,70,7` draws a
  11×7×13-block box.
- **Ellipsoid**: bounding box around center ± radii, the three principal circles, and
  the center block marker.
- Rendering rides vanilla's gizmo line pass: depth-tested against terrain, translucent
  (never hides what is behind it), no extra chunk work.
- Player options: enable toggle, grid colour, both corner colours, line opacity, grid
  on/off, line width. Grid lines are capped at 256 per axis; beyond that the face falls
  back to wireframe only.

## ImperiumMC adoption sketch (Kotlin, Paper)

```kotlin
// on enable:
val messenger = server.messenger
messenger.registerOutgoingPluginChannel(this, "worldedit:cui")
messenger.registerIncomingPluginChannel(this, "worldedit:cui") { _, player, bytes ->
    if (String(bytes, UTF_8).startsWith("v|")) cuiClients += player.uniqueId
}

// whenever a region overlay should show (or change):
fun showRegion(player: Player, min: BlockVector, max: BlockVector) {
    if (player.uniqueId !in cuiClients) return
    player.sendPluginMessage(this, "worldedit:cui", "s|cuboid".toByteArray())
    player.sendPluginMessage(this, "worldedit:cui",
        "p|0|${min.x}|${min.y}|${min.z}|0".toByteArray())
    player.sendPluginMessage(this, "worldedit:cui",
        "p|1|${max.x}|${max.y}|${max.z}|0".toByteArray())
}

// to clear: send an unknown shape id
fun clearRegion(player: Player) =
    player.sendPluginMessage(this, "worldedit:cui", "s|clear".toByteArray())
```

Rate: the client re-renders from its own state every frame, so sending on region change
(and at most a few times per second if the region moves, e.g. a live mining zone) is
plenty. Remove the player from `cuiClients` on quit.

## Compatibility notes

- Real WorldEdit / FAWE installations already speak all of the above to any client that
  sends `v|4`; Imperium's module renders those selections with no server work at all.
- The client speaks protocol version 4 (the current WorldEditCUI line) but tolerates the
  legacy v1-era one-shot cuboid format for simple server-side integrations.
- One selection at a time; multi-region overlays would need either rapid re-sends
  (visible flicker) or a future Imperium-specific extension channel.
