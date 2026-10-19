# Wave 4+ roadmap — trusted-client architecture (owner spec, 2026-10-19)

Owner directive: take the good ideas, build them natively for ImperiumMC, and make the
official client the safe, sanctioned way to play. Full spec in the session log; this is the
build order.

## W4-A: Carved imperial visual identity (menu + HUD editor)

Hybrid layout: modern dashboard navigation with pixel-grid borders and real item textures.
Chiseled stone/bronze panels, engraved capital typography, structured - no clutter.
Anti-sloppy: zero RGB neon/glow, no bubbly cartoon tropes, no frosted glass, no muddy
textures, no dark-on-dark text. Extends the existing Roman menu (M1) toward the owner's
carved-stone reference.

## W4-B: Native utility set + tiered rules channel

Built-in (not gated): Fullbright (exists), Waypoints (exists), CPS/Keystrokes (exist),
Health indicator, Durability HUD (exists for armor), Auto-Fish (exists, gated).
New native utilities, wired into the tier system below: Auto-Eat, Auto-Tool (swap before
breaking), Auto-Armor, Auto-Reconnect, Auto-Walk (conditional tier).

Extend `imperiumclient:features` (protocol v2) to carry the tier table:
- `allow` (QoL always on: auto_eat, auto_tool, auto_armor, auto_reconnect)
- `limited` (rate-limited: auto_fish, auto_walk) - client self-throttles to the server's rate
- `deny` (blocked locally: movement/kill cheats loaded from third-party mods get their
  packets suppressed before the socket - see W4-C)
Menu shows restricted modules with a "DISABLED ON THIS SERVER" badge; grants reset on
disconnect; singleplayer restores everything.

## W4-C: Local rules enforcement (the interceptor)

When connected to imperiummc.net, the official client becomes the local enforcement layer:
- Movement sanitization: illegal move deltas (fly/speed patterns matching our server-side
  checks) are corrected/dropped at the client BEFORE the socket - the packet never leaves.
  A player with a third-party cheat mod loaded simply cannot cheat here; nothing to ban.
- Exploit refusal: known malicious outgoing payloads (oversized book, ReDoS sign text,
  duplicate-channel floods) are refused locally - matching the server's existing checks.
- Scope: ONLY on connections to imperiummc.net. On any other server/singleplayer the client
  does nothing to other mods' traffic.
Framing rule for all comms/docs: this is LOCAL enforcement that PREVENTS violations - the
official client guarantees a clean connection, which is why staff review isn't needed for it.

## W4-D: Rank-gated client perks (EULA-safe)

Server sends the player's rank tier via the channel; client unlocks non-competitive perks:
expanded waypoint limits, menu theme variants (carved stone / bronze / marble), priority
config slots. No combat/competitive advantage; EULA-safe convenience only. Plugin side:
extend ImperiumClientIntegrationService to send rank + perks list after hello.

## W4-E: Launcher hardening + UX

- Download progress detail per-artifact, retry-on-failure with backoff.
- Server news panel wired to imperiummc.net RSS (already in distro root).
- Self-update smoke test (dev-app-update.yml).
- Installer config: per-platform install dir, JVM args defaults (RAM per javaOptions).

## Later (owner-input / dependency-gated)

- Emote wheel + cosmetics channel (needs the plugin's cosmetics data shaped for the wire).
  W5-A update: the client shell is done — carved radial wheel, 8 config slots, dispatcher seam
  (docs/media-and-emotes.md); only the channel + animation remain.
- Spotify state HUD. Fullscreen map view. Cannon trajectory preview.
  W5-B update: NowPlayingHud shipped as a registry shell plus a best-effort Spotify desktop
  local-endpoint poller (default off, no OAuth/cloud — docs/media-and-emotes.md).
